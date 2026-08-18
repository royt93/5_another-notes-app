package com.mckimquyen.notes.ui.setting

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.navigation.fragment.findNavController
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.google.android.material.color.DynamicColors
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.transition.MaterialElevationScale
import com.google.android.material.transition.MaterialSharedAxis
import com.mckimquyen.notes.ui.common.SelectorBottomSheet
import com.mckimquyen.notes.R
import com.mckimquyen.notes.RApp
import com.mckimquyen.notes.databinding.FSettingsBinding
import com.mckimquyen.notes.ext.TAG
import com.mckimquyen.notes.ext.moreApp
import com.mckimquyen.notes.ext.navigateSafe
import com.mckimquyen.notes.ext.openBrowserPolicy
import com.mckimquyen.notes.ext.rateApp
import com.mckimquyen.notes.ext.shareApp
import com.mckimquyen.notes.model.PrefsManager
import com.mckimquyen.notes.ui.AppTheme
import com.roy.sdkadbmob.AdManager
import com.mckimquyen.notes.ui.common.ConfirmDlg
import com.mckimquyen.notes.ui.main.MainAct
import com.mckimquyen.notes.ui.observeEvent
import com.mckimquyen.notes.ui.viewModel
import java.text.DateFormat
import javax.inject.Inject
import com.google.android.material.R as RMaterial

class SettingsFrm : PreferenceFragmentCompat(), ConfirmDlg.Callback, ExportPasswordDlg.Callback,
    ImportPasswordDlg.Callback {

    companion object {
        private const val RESTART_DIALOG_TAG = "restart_dialog"
        private const val CLEAR_DATA_DIALOG_TAG = "clear_data_dialog"
        private const val AUTOMATIC_EXPORT_DIALOG_TAG = "automatic_export_dialog"

        // Preference keys whose default ListPreference dialog is replaced by SelectorBottomSheet.
        private val CUPERTINO_SELECTOR_KEYS = setOf(
            "app_language",
            PrefsManager.THEME,
            PrefsManager.SHOWN_DATE,
            PrefsManager.SWIPE_ACTION_LEFT,
            PrefsManager.SWIPE_ACTION_RIGHT,
        )
        private const val SELECTOR_RESULT_PREFIX = "selector_result_"
    }

    @Inject
    lateinit var viewModelFactory: SettingsVM.Factory
    val viewModel by viewModel {
        viewModelFactory.create(it)
    }

    private var exportDataLauncher: ActivityResultLauncher<Intent>? = null
    private var autoExportLauncher: ActivityResultLauncher<Intent>? = null
    private var importDataLauncher: ActivityResultLauncher<Intent>? = null

    private var binding: FSettingsBinding? = null

    // SDK manages banner lifecycle via ActivityLifecycleCallbacks (autoManageLifecycle=true).
    // Keep the View ref only so we can explicitly destroy in onDestroyView.
    private var adView: View? = null

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val context = requireContext()

        (context.applicationContext as RApp?)?.appComponent?.inject(this)

        exportDataLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val uri = result.data?.data
            if (result.resultCode == Activity.RESULT_OK && uri != null) {
                val output = try {
                    // write and *truncate*. Otherwise the file is not overwritten!
                    context.contentResolver.openOutputStream(uri, "wt")
                } catch (e: Exception) {
                    Log.i(TAG, "Data export failed", e)
                    null
                }
                if (output != null) {
                    viewModel.exportData(output)
                } else {
                    showMessage(R.string.export_fail)
                }
            }
        }

        autoExportLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val uri = result.data?.data
            if (result.resultCode == Activity.RESULT_OK && uri != null) {
                val output = try {
                    val cr = context.contentResolver
                    cr.takePersistableUriPermission(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    cr.openOutputStream(uri)
                } catch (e: Exception) {
//                    Log.i(TAG, "Data export failed", e)
                    e.printStackTrace()
                    null
                }
                if (output != null) {
                    viewModel.setupAutoExport(output = output, uri = uri.toString())
                } else {
                    // takePersistableUriPermission() above already succeeded even though
                    // openOutputStream() then failed — release it via disableAutoExport()
                    // instead of only flipping the switch, or the permission grant leaks
                    // permanently. FIX-M11.
                    showMessage(R.string.export_fail)
                    autoExportPref.isChecked = false
                    viewModel.disableAutoExport()
                }
            }
        }

        importDataLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val uri = result.data?.data
            if (result.resultCode == Activity.RESULT_OK && uri != null) {
                val input = try {
                    context.contentResolver.openInputStream(uri)
                } catch (e: Exception) {
//                    Log.i(TAG, "Data import failed", e)
                    e.printStackTrace()
                    null
                }
                if (input != null) {
                    viewModel.importData(input)
                } else {
                    showMessage(R.string.import_bad_input)
                }
            }
        }

        enterTransition = MaterialElevationScale(false).apply {
            duration = resources.getInteger(RMaterial.integer.material_motion_duration_short_2).toLong()
        }
        exitTransition = MaterialElevationScale(true).apply {
            duration = resources.getInteger(RMaterial.integer.material_motion_duration_short_2).toLong()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding = FSettingsBinding.bind(view)

        binding?.toolbar?.setNavigationOnClickListener {
            findNavController().popBackStack()
        }

        setupViewModelObservers()
        binding?.layoutAdBanner?.bannerContainer?.let { bannerContainer ->
            binding?.layoutAdBanner?.tvLabelAd?.let { tvLabelAd ->
                adView = AdManager.loadBanner(
                    context = requireContext(),
                    container = bannerContainer,
                    tvLabelAd = tvLabelAd,
                    adSize = AdManager.getAdaptiveBannerSize(requireActivity()),
                )
            }
        }
    }

    override fun onDestroyView() {
        AdManager.bannerDestroy(adView)
        adView = null
        super.onDestroyView()
        binding = null
    }

    private fun setupViewModelObservers() {
        viewModel.messageEvent.observeEvent(viewLifecycleOwner, ::showMessage)
        viewModel.lastAutoExport.observe(viewLifecycleOwner) { date ->
            updateAutoExportSummary(autoExportPref.isChecked, date)
        }
        viewModel.releasePersistableUriEvent.observeEvent(viewLifecycleOwner) { uri ->
            try {
                requireContext().contentResolver.releasePersistableUriPermission(
                    Uri.parse(uri),
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (e: Exception) {
                // Permission was revoked? will probably happen sometimes
                Log.i(TAG, "Failed to release persistable URI permission", e)
            }
        }
        viewModel.showImportPasswordDialogEvent.observeEvent(viewLifecycleOwner) {
            ImportPasswordDlg.newInstance()
                .show(childFragmentManager, null)
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val context = requireContext()
        setPreferencesFromResource(R.xml.prefs, rootKey)

        // Wire 1 listener per Cupertino selector key — bottom sheet returns picked value
        // here; we forward it to the ListPreference so onPreferenceChangeListener still fires.
        CUPERTINO_SELECTOR_KEYS.forEach { key ->
            childFragmentManager.setFragmentResultListener(
                "$SELECTOR_RESULT_PREFIX$key", this
            ) { _, bundle ->
                val newValue = bundle.getString(SelectorBottomSheet.RESULT_KEY).orEmpty()
                applySelectorValue(key, newValue)
            }
        }

        requirePreference<Preference>("privacy_settings").setOnPreferenceClickListener {
            openPrivacySettings()
            true
        }

        requirePreference<ListPreference>("app_language").apply {
            // Reflect the current per-app locale (Android 13+ persists this; AppCompat backports it).
            value = AppCompatDelegate.getApplicationLocales().toLanguageTags().substringBefore('-')
            setOnPreferenceChangeListener { _, newValue ->
                val tag = newValue as String
                val locales = if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList()
                              else LocaleListCompat.forLanguageTags(tag)
                AppCompatDelegate.setApplicationLocales(locales)
                true
            }
        }

        requirePreference<ListPreference>(PrefsManager.THEME).setOnPreferenceChangeListener { _, theme ->
            (context.applicationContext as RApp).updateTheme(AppTheme.fromValue(theme as String))
            true
        }

        requirePreference<Preference>(PrefsManager.DYNAMIC_COLORS).apply {
            if (DynamicColors.isDynamicColorAvailable()) {
                setOnPreferenceClickListener {
                    ConfirmDlg.newInstance(
                        title = R.string.pref_restart_dialog_title,
                        message = R.string.pref_restart_dialog_description,
                        btnPositive = R.string.action_ok
                    ).show(childFragmentManager, RESTART_DIALOG_TAG)
                    true
                }
            } else {
                // Hide dynamic color / material you preference on unsupported Android versions
                isVisible = false
            }
        }

        requirePreference<Preference>(PrefsManager.PREVIEW_LINES).setOnPreferenceClickListener {
            exitTransition = MaterialSharedAxis(MaterialSharedAxis.Z, /* forward = */ true).apply {
                duration = resources.getInteger(RMaterial.integer.material_motion_duration_medium_1).toLong()
            }
            reenterTransition = MaterialSharedAxis(MaterialSharedAxis.Z, /* forward = */ false).apply {
                duration = resources.getInteger(RMaterial.integer.material_motion_duration_medium_1).toLong()
            }
            findNavController().navigateSafe(
                SettingsFrmDirections.actionNestedSettings(
                    R.xml.prefs_preview_lines, R.string.pref_preview_lines
                )
            )
            true
        }

        requirePreference<Preference>(PrefsManager.EXPORT_DATA).setOnPreferenceClickListener {
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
                .setType("application/json").addCategory(Intent.CATEGORY_OPENABLE)
            exportDataLauncher?.launch(intent)
            true
        }

        val encryptedExportPref: SwitchPreferenceCompat = requirePreference(PrefsManager.ENCRYPTED_EXPORT)
        // Older versions don't support PBKDF2withHmacSHA512
        if (Build.VERSION.SDK_INT < 26) {
            encryptedExportPref.isVisible = false
        }

        encryptedExportPref.setOnPreferenceChangeListener { _, newValue ->
            if (newValue == true) {
                ExportPasswordDlg.newInstance()
                    .show(childFragmentManager, null)
            } else {
                viewModel.deleteExportKey()
            }
            true
        }

        autoExportPref.setOnPreferenceChangeListener { _, newValue ->
            if (newValue == true) {
                ConfirmDlg.newInstance(
                    title = R.string.pref_data_auto_export,
                    message = R.string.auto_export_message,
                    btnPositive = R.string.action_ok
                ).show(childFragmentManager, AUTOMATIC_EXPORT_DIALOG_TAG)
            } else {
                updateAutoExportSummary(false)
                viewModel.disableAutoExport()
            }
            true
        }

        requirePreference<Preference>(PrefsManager.IMPORT_DATA).setOnPreferenceClickListener {
            // note: explicit mimetype fails for some devices, see #11
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
                .setType("*/*").addCategory(Intent.CATEGORY_OPENABLE)
            importDataLauncher?.launch(intent)
            true
        }

        requirePreference<Preference>(PrefsManager.CLEAR_DATA).setOnPreferenceClickListener {
            ConfirmDlg.newInstance(
                title = R.string.pref_data_clear,
                message = R.string.pref_data_clear_confirm_message,
                btnPositive = R.string.action_clear
            ).show(childFragmentManager, CLEAR_DATA_DIALOG_TAG)
            true
        }

//        requirePreference<Preference>(PrefsManager.VIEW_LICENSES).setOnPreferenceClickListener {
//            findNavController().navigate(R.id.action_about_libraries, bundleOf(
//                // Navigation component safe args seem to fail for cross module navigation.
//                // So pass the customization argument the old way.
//                "data" to LibsBuilder().apply {
//                    aboutShowIcon = false
//                    aboutShowVersion = false
//                }
//            ))
//            true
//        }

        requirePreference<Preference>("shareApp").setOnPreferenceClickListener {
            activity?.shareApp()
            true
        }
        requirePreference<Preference>("rateApp").setOnPreferenceClickListener {
            activity?.rateApp(activity?.packageName ?: "")
            true
        }
        requirePreference<Preference>("moreApp").setOnPreferenceClickListener {
            activity?.moreApp()
            true
        }
        requirePreference<Preference>("policy").setOnPreferenceClickListener {
            activity?.openBrowserPolicy()
            true
        }

        // Set version name as summary text for version preference
        requirePreference<Preference>(PrefsManager.VERSION).summary = com.mckimquyen.notes.BuildConfig.VERSION_NAME

        requirePreference<Preference>("exact_alarm_permission").apply {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                // SCHEDULE_EXACT_ALARM doesn't exist below API 31 — exact alarms are always
                // allowed there, nothing for the user to grant.
                isVisible = false
            } else {
                setOnPreferenceClickListener {
                    startActivity(
                        Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                            .setData(Uri.parse("package:${context.packageName}"))
                    )
                    true
                }
            }
        }
    }

    // Tracked across onResume() calls so we can tell "user just granted it in Settings" (worth
    // rescheduling already-set inexact alarms) apart from "already granted last time too".
    private var wasExactAlarmGranted: Boolean? = null

    override fun onResume() {
        super.onResume()
        updateExactAlarmPrefSummary()
    }

    private fun updateExactAlarmPrefSummary() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val alarmManager = requireContext().getSystemService(android.app.AlarmManager::class.java)
        val granted = alarmManager.canScheduleExactAlarms()
        requirePreference<Preference>("exact_alarm_permission").summary = getString(
            if (granted) R.string.pref_exact_alarm_summary_granted else R.string.pref_exact_alarm_summary_denied
        )
        if (granted && wasExactAlarmGranted == false) {
            viewModel.rescheduleAllAlarms()
        }
        wasExactAlarmGranted = granted
    }

    override fun onDestroy() {
        super.onDestroy()
        exportDataLauncher = null
        importDataLauncher = null
        autoExportLauncher = null
    }

    /**
     * Intercept the default AlertDialog for ListPreference and show our Cupertino-style
     * bottom sheet instead. Falls through to default behavior for other preferences.
     */
    override fun onDisplayPreferenceDialog(preference: Preference) {
        if (preference is ListPreference && preference.key in CUPERTINO_SELECTOR_KEYS) {
            SelectorBottomSheet.newInstance(
                requestKey = "$SELECTOR_RESULT_PREFIX${preference.key}",
                title = preference.title?.toString().orEmpty(),
                entries = preference.entries.map { it.toString() }.toTypedArray(),
                values = preference.entryValues.map { it.toString() }.toTypedArray(),
                selectedValue = preference.value.orEmpty(),
            ).show(childFragmentManager, "selector_${preference.key}")
            return
        }
        super.onDisplayPreferenceDialog(preference)
    }

    /** Apply the bottom-sheet result the same way the default dialog would (listener + persist). */
    private fun applySelectorValue(prefKey: String, newValue: String) {
        val pref = findPreference<ListPreference>(prefKey) ?: return
        if (pref.value == newValue) return
        // Mirror PreferenceFragmentCompat: if the listener vetoes (returns false), don't persist.
        val listener = pref.onPreferenceChangeListener
        val accepted = listener?.onPreferenceChange(pref, newValue) ?: true
        if (accepted) pref.value = newValue
    }

    /**
     * UMP: privacy options form only exists for users in regions where consent is required (EEA / UK / CH).
     * For everyone else `showConsentFormIfAvailable` no-ops — give the user a clear message instead of
     * silently doing nothing on tap.
     */
    private fun openPrivacySettings() {
        val info = com.google.android.ump.UserMessagingPlatform.getConsentInformation(requireContext())
        val required = info.privacyOptionsRequirementStatus ==
            com.google.android.ump.ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
        if (required) {
            AdManager.showConsentFormIfAvailable(requireActivity())
        } else {
            showMessage(R.string.msg_consent_not_required)
        }
    }


    private fun showMessage(@StringRes messageId: Int) {
        val snackbar = Snackbar.make(requireView(), messageId, Snackbar.LENGTH_SHORT)
            .setGestureInsetBottomIgnored(true)
        snackbar.view.findViewById<androidx.appcompat.widget.AppCompatTextView>(com.google.android.material.R.id.snackbar_text).maxLines =
            5
        snackbar.show()
    }

    private fun <T : Preference> requirePreference(key: CharSequence) =
        checkNotNull(findPreference<T>(key)) { "Could not find preference with key '$key'." }

    private val autoExportPref: SwitchPreferenceCompat
        get() = requirePreference(PrefsManager.AUTO_EXPORT)

    private fun updateAutoExportSummary(enabled: Boolean, date: Long = 0) {
        if (enabled) {
            autoExportPref.summary = buildString {
                appendLine(getString(R.string.pref_data_auto_export_summary))
                append(
                    getString(
                        R.string.pref_data_auto_export_date,
                        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(date)
                    )
                )
            }
        } else {
            autoExportPref.summary = getString(R.string.pref_data_auto_export_summary)
        }
    }

    override fun onDialogPositiveButtonClicked(tag: String?) {
        when (tag) {
            RESTART_DIALOG_TAG -> {
                // Reload MainAct to apply theming changes
                requireActivity().finish()
                startActivity(Intent(requireContext(), MainAct::class.java))
            }

            CLEAR_DATA_DIALOG_TAG -> {
                viewModel.clearData()
            }

            AUTOMATIC_EXPORT_DIALOG_TAG -> {
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
                    .setType("application/json")
                    .addCategory(Intent.CATEGORY_OPENABLE)
                autoExportLauncher?.launch(intent)
            }
        }
    }

    override fun onDialogNegativeButtonClicked(tag: String?) {
        if (tag == AUTOMATIC_EXPORT_DIALOG_TAG) {
            // No file chosen for auto export, disable it.
            autoExportPref.isChecked = false
        }
    }

    override fun onDialogCancelled(tag: String?) {
        if (tag == AUTOMATIC_EXPORT_DIALOG_TAG) {
            // No file chosen for auto export, disable it.
            autoExportPref.isChecked = false
        }
    }

    private val exportEncryptionPref: SwitchPreferenceCompat
        get() = requirePreference(PrefsManager.ENCRYPTED_EXPORT)

    override fun onExportPasswordDialogPositiveButtonClicked(password: String) {
        viewModel.generateExportKeyFromPassword(password)
    }

    override fun onExportPasswordDialogNegativeButtonClicked() {
        exportEncryptionPref.isChecked = false
    }

    override fun onExportPasswordDialogCancelled() {
        exportEncryptionPref.isChecked = false
    }

    override fun onImportPasswordDialogPositiveButtonClicked(password: String) {
        viewModel.importSavedEncryptedJsonData(password)
    }
}
