package com.maltaisn.notes.ui.home

import com.maltaisn.notes.DebugUtils
import com.mckimquyen.notes.BuildConfig
import com.mckimquyen.notes.model.LabelsRepository
import com.mckimquyen.notes.model.NotesRepository
import com.mckimquyen.notes.model.entity.LabelRef
import com.mckimquyen.notes.model.entity.NoteStatus
import com.mckimquyen.notes.ui.home.BuildTypeBehavior
import com.mckimquyen.notes.ui.home.HomeVM
import com.mckimquyen.notes.ui.navigation.HomeDestination
import javax.inject.Inject

class DebugBuildTypeBehavior @Inject constructor(
    private val notesRepository: NotesRepository,
    private val labelsRepository: LabelsRepository,
) : BuildTypeBehavior {

    override suspend fun doExtraAction(viewModel: HomeVM) {
        // This class only compiles into debug build types now (ENH-A01) — the
        // ENABLE_DEBUG_FEATURES guard stays as defense-in-depth for the
        // taking_screenshots=true devDebug mode, where the menu item that triggers this
        // is hidden but this class is still the one wired in via Dagger.
        if (!BuildConfig.ENABLE_DEBUG_FEATURES) return

        // Add a few random notes of the currently selected status.
        val destination = viewModel.currentDestination
        if (destination is HomeDestination.Status) {
            repeat(3) {
                notesRepository.insertNote(DebugUtils.getRandomNote(destination.status))
            }

            // For performance testing
//            val labels = (1L..100L).map { Label(it, UUID.randomUUID().toString().substring(0, 9)) }
//            for (label in labels) {
//                labelsRepository.insertLabel(label)
//            }
//            for (status in NoteStatus.values()) {
//                repeat(300) {
//                    val id = notesRepository.insertNote(DebugUtils.getRandomNote(status))
//                    val noteLabels = labels.shuffled().subList(0, Random.nextInt(10))
//                    labelsRepository.insertLabelRefs(noteLabels.map { LabelRef(id, it.id) })
//                }
//            }
        } else if (destination is HomeDestination.Labels) {
            repeat(3) {
                val id = notesRepository.insertNote(DebugUtils.getRandomNote(NoteStatus.ACTIVE))
                labelsRepository.insertLabelRefs(listOf(LabelRef(id, destination.label.id)))
            }
        }
    }
}
