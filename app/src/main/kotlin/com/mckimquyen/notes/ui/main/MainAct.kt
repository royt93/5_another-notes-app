package com.mckimquyen.notes.ui.main

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.addCallback
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.children
import androidx.core.view.contains
import androidx.core.view.forEach
import androidx.core.view.updatePadding
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.fragment.NavHostFragment
import com.google.android.material.color.DynamicColors
import com.mckimquyen.notes.NavGraphMainDirections
import com.mckimquyen.notes.R
import com.mckimquyen.notes.RApp
import com.mckimquyen.notes.databinding.AMainBinding
import com.mckimquyen.notes.ext.TAG
import com.mckimquyen.notes.ext.navigateSafe
import com.mckimquyen.notes.model.PrefsManager
import com.mckimquyen.notes.model.converter.NoteTypeConverter
import com.mckimquyen.notes.model.entity.Note
import com.mckimquyen.notes.model.entity.NoteStatus
import com.mckimquyen.notes.model.entity.NoteType
import com.mckimquyen.notes.receiver.AlarmReceiver
import com.mckimquyen.notes.ui.SharedViewModel
import com.mckimquyen.notes.ui.main.MainVM.NewNoteData
import com.mckimquyen.notes.ui.navGraphViewModel
import com.mckimquyen.notes.ui.navigation.HomeDestination
import com.mckimquyen.notes.ui.observeEvent
import com.mckimquyen.notes.ui.viewModel
import java.io.IOException
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Provider

class MainAct : BaseAct(), NavController.OnDestinationChangedListener {

    @Inject
    lateinit var sharedViewModelProvider: Provider<SharedViewModel>
    private val sharedViewModel by navGraphViewModel(R.id.nav_graph_main) {
        sharedViewModelProvider.get()
    }

    @Inject
    lateinit var viewModelFactory: MainVM.Factory
    private val viewModel by viewModel {
        viewModelFactory.create(it)
    }

    @Inject
    lateinit var prefs: PrefsManager

    lateinit var drawerLayout: DrawerLayout

    private lateinit var navController: NavController
    private lateinit var binding: AMainBinding

    private val exitHandler = Handler(Looper.getMainLooper())
    private val resetExitRunnable = Runnable { doubleBackToExitPressedOnce = false }

    //set to false to make it work, but i do not like
    private var doubleBackToExitPressedOnce = true

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.AppTheme_DayNight)

        super.onCreate(savedInstanceState)
        (applicationContext as RApp).appComponent.inject(this)

        // Apply dynamic colors
        if (prefs.dynamicColors) {
            DynamicColors.applyToActivityIfAvailable(this)
        }

        // Can be useful when debugging after process death, debugging notification receiver, etc.
//        Debug.waitForDebugger()

        // For triggering process death during debug
//        val venom = Venom.createInstance(this)
//        venom.initialize()
//        venom.start()

        binding = AMainBinding.inflate(layoutInflater)
        drawerLayout = binding.drawerLayout
        setContentView(binding.root)

        // Allow for transparent status and navigation bars
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Apply padding to navigation drawer
        val initialPadding = resources.getDimensionPixelSize(R.dimen.navigation_drawer_bottom_padding)
        ViewCompat.setOnApplyWindowInsetsListener(binding.navView) { _, insets ->
            val sysWindow = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.navView.getHeaderView(0).updatePadding(top = sysWindow.top)
            binding.navView.children.last().updatePadding(bottom = initialPadding + sysWindow.bottom)
            // Don't draw under system bars, if it conflicts with the navigation drawer.
            // This is mainly the case if the app is used in landscape mode with traditional 3 button navigation.
            if (sysWindow.left > 0) {
                WindowCompat.setDecorFitsSystemWindows(window, true)
            }
            insets
        }

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.navHostFragment) as NavHostFragment
        navController = navHostFragment.navController
        navController.addOnDestinationChangedListener(this)

        binding.navView.setNavigationItemSelectedListener { item ->
            viewModel.navigationItemSelected(
                item = item,
                labelsMenu = binding.navView.menu.findItem(R.id.drawerLabels).subMenu!!
            )
            true
        }
        viewModel.startPopulatingDrawerWithLabels()

        onBackPressedDispatcher.addCallback(this) {
            if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                drawerLayout.closeDrawers()
            } else {
                val isLastFragment = navController.currentDestination?.id == R.id.fragment_home
//                Log.d(
//                    "",
//                    "size ${navHostFragment.navController.currentDestination?.id}, isLastFragment $isLastFragment"
//                )
                if (isLastFragment) {
                    if (doubleBackToExitPressedOnce) {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                        isEnabled = true
                    } else {
                        doubleBackToExitPressedOnce = true
                        Toast.makeText(this@MainAct, "Please click BACK again to exit", Toast.LENGTH_SHORT).show()
                        exitHandler.postDelayed(resetExitRunnable, 2000)
                    }
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        }

        setupViewModelObservers()
    }

    private fun setupViewModelObservers() {
        val menu = binding.navView.menu
        val labelSubmenu = menu.findItem(R.id.drawerLabels).subMenu!!
        var currentHomeDestination: HomeDestination = HomeDestination.Status(NoteStatus.ACTIVE)

        viewModel.currentHomeDestination.observe(this) { newHomeDestination ->
            sharedViewModel.changeHomeDestination(newHomeDestination)
            currentHomeDestination = newHomeDestination
        }

        viewModel.navDirectionsEvent.observeEvent(this) { navDirections ->
            navController.navigateSafe(navDirections)
        }

        viewModel.drawerCloseEvent.observeEvent(this) {
            drawerLayout.closeDrawers()
        }

        viewModel.clearLabelsEvent.observeEvent(this) {
            labelSubmenu.clear()
        }

        viewModel.labelsAddEvent.observeEvent(this) { labels ->
            if (labels != null) {
                for (label in labels) {
                    labelSubmenu.add(Menu.NONE, View.generateViewId(), Menu.NONE, label.name)
                        .setIcon(R.drawable.ic_label_outline).isCheckable = true
                }
            }

            // Select the current label in the navigation drawer, if it isn't already.
            if (currentHomeDestination is HomeDestination.Labels) {
                val currentLabelName = (currentHomeDestination as HomeDestination.Labels).label.name
                if (binding.navView.checkedItem != null && (
                            binding.navView.checkedItem!! !in labelSubmenu ||
                                    binding.navView.checkedItem!!.title != currentLabelName)
                    || binding.navView.checkedItem == null
                ) {
                    labelSubmenu.forEach { item: MenuItem ->
                        if (item.title == currentLabelName) {
                            binding.navView.setCheckedItem(item)
                            return@forEach
                        }
                    }
                }
            }
        }

        viewModel.manageLabelsVisibility.observe(this) { isVisible ->
            menu.findItem(R.id.drawerItemEditLabels).isVisible = isVisible
        }

        viewModel.editItemEvent.observeEvent(this) { noteId ->
            // Allow navigating to same destination, in case notification is clicked while already editing a note.
            // In this case the EditFrm will be opened multiple times.
            navController.navigateSafe(NavGraphMainDirections.actionEditNote(noteId), true)
        }

        viewModel.autoExportEvent.observeEvent(this) { uri ->
            viewModel.autoExport(
                try {
                    // write and *truncate*. Otherwise the file is not overwritten!
                    contentResolver.openOutputStream(Uri.parse(uri), "wt")
                } catch (e: Exception) {
                    Log.i(TAG, "Auto data export failed", e)
                    null
                }
            )
        }

        viewModel.createNoteEvent.observeEvent(this) { newNoteData ->
            navController.navigateSafe(
                NavGraphMainDirections.actionEditNote(
                    type = newNoteData.type.value,
                    title = newNoteData.title,
                    content = newNoteData.content
                )
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        this.intent = intent
    }

    override fun onDestinationChanged(
        controller: NavController,
        destination: NavDestination,
        arguments: Bundle?,
    ) {
        drawerLayout.setDrawerLockMode(
            if (destination.id == R.id.fragment_home) {
                DrawerLayout.LOCK_MODE_UNLOCKED
            } else {
                DrawerLayout.LOCK_MODE_LOCKED_CLOSED
            }
        )
    }

    override fun onStart() {
        super.onStart()

        // Go to label, if it has been newly created
        sharedViewModel.labelAddEventNav.observeEvent(this) { label ->
            if (navController.previousBackStackEntry?.destination?.id == R.id.fragment_home) {
                viewModel.selectLabel(label)
            }
        }

        viewModel.onStart()
    }

    override fun onResume() {
        super.onResume()
        handleIntent()
    }

    override fun onDestroy() {
        super.onDestroy()
        navController.removeOnDestinationChangedListener(this)
        exitHandler.removeCallbacks(resetExitRunnable)
    }

    private fun handleIntent() {
        val intent = intent ?: return
        if (!intent.getBooleanExtra(KEY_INTENT_HANDLED, false)) {
            when (intent.action) {
                Intent.ACTION_SEND -> {
                    // Plain text was shared to app, create new note for it
                    val noteData = createNoteFromIntent(intent)
                    if (noteData != null) {
                        viewModel.createNote(noteData)
                    }
                }

                INTENT_ACTION_CREATE -> {
                    // Intent to create a note of a certain type. Used by launcher shortcuts.
                    val type = NoteTypeConverter.toType(
                        intent.getIntExtra(EXTRA_NOTE_TYPE, 0)
                    )
                    viewModel.createNote(NewNoteData(type))
                }

                INTENT_ACTION_EDIT -> {
                    // Intent to edit a specific note. This is used by reminder notification.
                    viewModel.editNote(intent.getLongExtra(AlarmReceiver.EXTRA_NOTE_ID, Note.NO_ID))
                }

                INTENT_ACTION_SHOW_REMINDERS -> {
                    // Show reminders screen in HomeFrm. Used by launcher shortcut.
                    binding.navView.menu.findItem(R.id.drawerItemReminders).isChecked = true
                    sharedViewModel.changeHomeDestination(HomeDestination.Reminders)
                }
            }

            // Mark intent as handled or it will be handled again if activity is resumed again.
            intent.putExtra(KEY_INTENT_HANDLED, true)
        }
    }

    private fun createNoteFromIntent(intent: Intent): NewNoteData? {
        val extras = intent.extras ?: return null
        var noteData: NewNoteData? = null
        if (intent.type == "text/plain") {
            if (extras.containsKey(Intent.EXTRA_STREAM)) {
                // A file was shared
                @Suppress("DEPRECATION")
                val uri = extras.get(Intent.EXTRA_STREAM) as? Uri
                if (uri != null) {
                    try {
                        val reader = InputStreamReader(contentResolver.openInputStream(uri))
                        val title = uri.pathSegments.last()
                        val content = reader.readText()
                        noteData = NewNoteData(NoteType.TEXT, title, content)
                        reader.close()
                    } catch (e: IOException) {
                        // nothing to do (file doesn't exist, access error, etc)
                    }
                }
            } else {
                // Text was shared
                val title = extras.getString(Intent.EXTRA_TITLE)
                    ?: extras.getString(Intent.EXTRA_SUBJECT) ?: ""
                val content = extras.getString(Intent.EXTRA_TEXT) ?: ""
                noteData = NewNoteData(NoteType.TEXT, title, content)
            }
        }
        return noteData
    }

    companion object {
        private const val KEY_INTENT_HANDLED = "com.mckimquyen.notes.INTENT_HANDLED"
        const val EXTRA_NOTE_TYPE = "com.mckimquyen.notes.NOTE_TYPE"
        const val INTENT_ACTION_CREATE = "com.mckimquyen.notes.CREATE"
        const val INTENT_ACTION_EDIT = "com.mckimquyen.notes.EDIT"
        const val INTENT_ACTION_SHOW_REMINDERS = "com.mckimquyen.notes.SHOW_REMINDERS"
    }
}
