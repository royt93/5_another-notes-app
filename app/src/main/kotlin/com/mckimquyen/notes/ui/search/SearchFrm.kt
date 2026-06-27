package com.mckimquyen.notes.ui.search

import android.os.Bundle
import android.view.View
import androidx.appcompat.widget.SearchView
import androidx.core.view.isVisible
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DefaultItemAnimator
import com.google.android.material.transition.MaterialElevationScale
import com.mckimquyen.notes.RApp
import com.mckimquyen.notes.R
import com.mckimquyen.notes.ext.hideKeyboard
import com.mckimquyen.notes.ext.showKeyboard
import com.mckimquyen.notes.ui.note.NoteFrm
import com.mckimquyen.notes.ui.viewModel
import javax.inject.Inject
import com.google.android.material.R as RMaterial

class SearchFrm : NoteFrm() {

    @Inject
    lateinit var viewModelFactory: SearchVM.Factory

    override val viewModel by viewModel {
        viewModelFactory.create(it)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (requireContext().applicationContext as RApp).appComponent.inject(this)

        enterTransition = MaterialElevationScale(false).apply {
            duration = resources.getInteger(RMaterial.integer.material_motion_duration_short_2).toLong()
        }
        exitTransition = MaterialElevationScale(true).apply {
            duration = resources.getInteger(RMaterial.integer.material_motion_duration_short_2).toLong()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val navController = findNavController()

        // Toolbar
        val toolbar = binding.toolbar
        toolbar.apply {
            inflateMenu(R.menu.menu_toolbar_search)
            setNavigationIcon(R.drawable.ic_arrow_start)
            setNavigationContentDescription(R.string.content_descrp_back)
            setNavigationOnClickListener {
                view.hideKeyboard()
                navController.popBackStack()
            }
        }

        binding.fab.isVisible = false

        // Recycler view
        val rcv = binding.recyclerView
        rcv.itemAnimator = DefaultItemAnimator().apply {
            // Short crossfade so highlight changes animate smoothly without jarring blink.
            changeDuration = 120
        }

        // Search view
        val searchView = toolbar.menu.findItem(R.id.itemSearchEdt).actionView as SearchView
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                view.hideKeyboard()
                return true
            }

            override fun onQueryTextChange(query: String): Boolean {
                viewModel.searchNotes(query)
                return false
            }
        })

        // Disable lift on scroll so that the toolbar is always a different color than the background.
        binding.toolbarLayout.isLiftOnScroll = false

        // Focus search view when search fragment is shown.
        searchView.setOnQueryTextFocusChangeListener { editText, hasFocus ->
            if (hasFocus) {
                editText.showKeyboard()
            }
        }
        searchView.requestFocus()
    }
}
