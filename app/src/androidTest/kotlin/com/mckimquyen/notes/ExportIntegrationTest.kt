package com.mckimquyen.notes

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.notes.model.entity.*
import com.mckimquyen.notes.ui.edit.ExportHelper
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Date

@RunWith(AndroidJUnit4::class)
class ExportIntegrationTest {

    @Test
    fun testExportPdfAndImageTextNote() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val note = Note(
            id = 1,
            type = NoteType.TEXT,
            title = "Test Text Note for Export",
            content = "This is the content line 1.\nThis is the content line 2.",
            metadata = BlankNoteMetadata,
            addedDate = Date(),
            lastModifiedDate = Date(),
            status = NoteStatus.ACTIVE,
            pinned = PinnedStatus.UNPINNED,
            reminder = null
        )
        val labels = listOf(Label(1, "Test Label"))

        val pdfFile = File(context.cacheDir, "test_export.pdf")
        val imageFile = File(context.cacheDir, "test_export.png")

        if (pdfFile.exists()) pdfFile.delete()
        if (imageFile.exists()) imageFile.delete()

        // Export PDF
        ExportHelper.exportAsPdf(context, note, labels, pdfFile)
        assertTrue("PDF file should exist after export", pdfFile.exists())
        assertTrue("PDF file size should be greater than 0", pdfFile.length() > 0)

        // Export Image
        ExportHelper.exportAsImage(context, note, labels, imageFile)
        assertTrue("Image file should exist after export", imageFile.exists())
        assertTrue("Image file size should be greater than 0", imageFile.length() > 0)

        // Cleanup
        pdfFile.delete()
        imageFile.delete()
    }

    @Test
    fun testExportPdfAndImageListNote() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val note = Note(
            id = 2,
            type = NoteType.LIST,
            title = "Test List Note for Export",
            content = "Checked Item\nUnchecked Item",
            metadata = ListNoteMetadata(listOf(true, false)),
            addedDate = Date(),
            lastModifiedDate = Date(),
            status = NoteStatus.ACTIVE,
            pinned = PinnedStatus.UNPINNED,
            reminder = null
        )
        val labels = emptyList<Label>()

        val pdfFile = File(context.cacheDir, "test_export_list.pdf")
        val imageFile = File(context.cacheDir, "test_export_list.png")

        if (pdfFile.exists()) pdfFile.delete()
        if (imageFile.exists()) imageFile.delete()

        // Export PDF
        ExportHelper.exportAsPdf(context, note, labels, pdfFile)
        assertTrue("PDF list file should exist after export", pdfFile.exists())
        assertTrue("PDF list file size should be greater than 0", pdfFile.length() > 0)

        // Export Image
        ExportHelper.exportAsImage(context, note, labels, imageFile)
        assertTrue("Image list file should exist after export", imageFile.exists())
        assertTrue("Image list file size should be greater than 0", imageFile.length() > 0)

        // Cleanup
        pdfFile.delete()
        imageFile.delete()
    }
}
