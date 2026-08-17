

package com.mckimquyen.debug.notes.ui.home

import com.mckimquyen.debug.notes.DebugUtils
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
        // This class is wired in for every build type (see FIX-H01 in doc/task/todo/FIX.md) —
        // guard at runtime so a release build never inserts placeholder notes into a real DB.
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
