package com.mckimquyen.notes.ui.edit

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.mckimquyen.notes.model.entity.Label
import com.mckimquyen.notes.model.entity.Note
import com.mckimquyen.notes.model.entity.NoteType
import java.io.File
import java.io.FileOutputStream
import java.text.DateFormat

object ExportHelper {

    private fun createStaticLayout(text: CharSequence, paint: TextPaint, width: Int): StaticLayout {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.15f)
                .setIncludePad(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            StaticLayout(
                text, paint, width,
                Layout.Alignment.ALIGN_NORMAL, 1.15f, 0f, true
            )
        }
    }

    fun exportAsPdf(context: Context, note: Note, labels: List<Label>, file: File) {
        val pdfDocument = PdfDocument()
        
        // A4 page size at 72 dpi: 595 x 842
        val pageWidth = 595
        val pageHeight = 842
        val marginLeft = 40f
        val marginRight = 40f
        val marginTop = 50f
        val marginBottom = 50f
        val contentWidth = pageWidth - marginLeft.toInt() - marginRight.toInt()

        // Page info
        var pageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas

        // Paints
        val titlePaint = TextPaint().apply {
            isAntiAlias = true
            textSize = 22f
            color = Color.BLACK
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val metaPaint = TextPaint().apply {
            isAntiAlias = true
            textSize = 11f
            color = Color.GRAY
        }
        val contentPaint = TextPaint().apply {
            isAntiAlias = true
            textSize = 13f
            color = Color.BLACK
        }
        val checkboxPaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
            color = Color.DKGRAY
        }
        val checkmarkPaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = Color.DKGRAY
        }

        var currentY = marginTop

        // 1. Draw Title
        if (note.title.isNotEmpty()) {
            val titleLayout = createStaticLayout(note.title, titlePaint, contentWidth)
            // Every other block below (meta, paragraphs, items) wraps its draw() call in
            // save/translate(marginLeft, currentY)/restore — this one didn't, so the title
            // always painted at the canvas origin (0,0) regardless of margins. FIX-M14.
            canvas.save()
            canvas.translate(marginLeft, currentY)
            titleLayout.draw(canvas)
            canvas.restore()
            currentY += titleLayout.height + 15
        }

        // 2. Draw Date & Labels
        val dateText = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(note.lastModifiedDate)
        val labelsText = if (labels.isNotEmpty()) {
            "  |  Labels: " + labels.joinToString(", ") { it.name }
        } else {
            ""
        }
        val metaText = dateText + labelsText
        val metaLayout = createStaticLayout(metaText, metaPaint, contentWidth)
        canvas.save()
        canvas.translate(marginLeft, currentY)
        metaLayout.draw(canvas)
        canvas.restore()
        currentY += metaLayout.height + 25

        // Divider line
        canvas.drawLine(marginLeft, currentY, pageWidth - marginRight, currentY, checkboxPaint)
        currentY += 20

        // Helper to check and start new page
        fun ensureSpace(heightNeeded: Float) {
            if (currentY + heightNeeded > pageHeight - marginBottom) {
                pdfDocument.finishPage(page)
                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas
                currentY = marginTop
            }
        }

        // 3. Draw Content
        if (note.type == NoteType.TEXT) {
            val paragraphs = note.content.split('\n')
            for (para in paragraphs) {
                val paraLayout = createStaticLayout(para, contentPaint, contentWidth)
                val height = paraLayout.height.toFloat() + 10f
                ensureSpace(height)
                
                canvas.save()
                canvas.translate(marginLeft, currentY)
                paraLayout.draw(canvas)
                canvas.restore()
                currentY += height
            }
        } else {
            val items = note.listItems
            val checkboxSize = 14f
            val textWidth = contentWidth - checkboxSize.toInt() - 10
            for (item in items) {
                val itemLayout = createStaticLayout(item.content, contentPaint, textWidth)
                val height = Math.max(checkboxSize, itemLayout.height.toFloat()) + 12f
                ensureSpace(height)

                // Draw Checkbox
                val checkboxLeft = marginLeft
                val checkboxTop = currentY + 2
                canvas.drawRect(
                    checkboxLeft,
                    checkboxTop,
                    checkboxLeft + checkboxSize,
                    checkboxTop + checkboxSize,
                    checkboxPaint
                )

                if (item.checked) {
                    // Draw Checkmark
                    val checkPath = Path().apply {
                        moveTo(checkboxLeft + 3f, checkboxTop + 7f)
                        lineTo(checkboxLeft + 6f, checkboxTop + 10f)
                        lineTo(checkboxLeft + 11f, checkboxTop + 3f)
                    }
                    canvas.drawPath(checkPath, checkmarkPaint)
                }

                // Draw Text
                canvas.save()
                canvas.translate(marginLeft + checkboxSize + 10, currentY)
                itemLayout.draw(canvas)
                canvas.restore()

                currentY += height
            }
        }

        pdfDocument.finishPage(page)

        FileOutputStream(file).use { out ->
            pdfDocument.writeTo(out)
        }
        pdfDocument.close()
    }

    fun exportAsImage(context: Context, note: Note, labels: List<Label>, file: File) {
        val width = 720
        val marginLeft = 48f
        val marginRight = 48f
        val marginTop = 60f
        val marginBottom = 60f
        val contentWidth = width - marginLeft.toInt() - marginRight.toInt()

        // 1. Calculate required height
        val titlePaint = TextPaint().apply {
            isAntiAlias = true
            textSize = 32f
            color = Color.BLACK
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val metaPaint = TextPaint().apply {
            isAntiAlias = true
            textSize = 16f
            color = Color.GRAY
        }
        val contentPaint = TextPaint().apply {
            isAntiAlias = true
            textSize = 20f
            color = Color.BLACK
        }
        val checkboxPaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
            color = Color.DKGRAY
        }
        val checkmarkPaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = Color.DKGRAY
        }

        var totalHeight = marginTop + marginBottom

        val titleLayout = if (note.title.isNotEmpty()) createStaticLayout(note.title, titlePaint, contentWidth) else null
        if (titleLayout != null) {
            totalHeight += titleLayout.height + 20
        }

        val dateText = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(note.lastModifiedDate)
        val labelsText = if (labels.isNotEmpty()) {
            "  |  Labels: " + labels.joinToString(", ") { it.name }
        } else {
            ""
        }
        val metaText = dateText + labelsText
        val metaLayout = createStaticLayout(metaText, metaPaint, contentWidth)
        totalHeight += metaLayout.height + 35 // date layout + space for divider

        if (note.type == NoteType.TEXT) {
            val paragraphs = note.content.split('\n')
            for (para in paragraphs) {
                val layout = createStaticLayout(para, contentPaint, contentWidth)
                totalHeight += layout.height + 15
            }
        } else {
            val items = note.listItems
            val checkboxSize = 22f
            val textWidth = contentWidth - checkboxSize.toInt() - 15
            for (item in items) {
                val layout = createStaticLayout(item.content, contentPaint, textWidth)
                totalHeight += Math.max(checkboxSize, layout.height.toFloat()) + 18
            }
        }

        // Ensure a reasonable minimum height
        val finalHeight = Math.max(400, totalHeight.toInt())

        // 2. Draw to Bitmap
        val bitmap = Bitmap.createBitmap(width, finalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        var currentY = marginTop

        // Draw Title
        if (titleLayout != null) {
            canvas.save()
            canvas.translate(marginLeft, currentY)
            titleLayout.draw(canvas)
            canvas.restore()
            currentY += titleLayout.height + 20
        }

        // Draw Meta
        canvas.save()
        canvas.translate(marginLeft, currentY)
        metaLayout.draw(canvas)
        canvas.restore()
        currentY += metaLayout.height + 25

        // Draw Divider
        canvas.drawLine(marginLeft, currentY, width - marginRight, currentY, checkboxPaint)
        currentY += 25

        // Draw Content
        if (note.type == NoteType.TEXT) {
            val paragraphs = note.content.split('\n')
            for (para in paragraphs) {
                val layout = createStaticLayout(para, contentPaint, contentWidth)
                canvas.save()
                canvas.translate(marginLeft, currentY)
                layout.draw(canvas)
                canvas.restore()
                currentY += layout.height + 15
            }
        } else {
            val items = note.listItems
            val checkboxSize = 22f
            val textWidth = contentWidth - checkboxSize.toInt() - 15
            for (item in items) {
                val layout = createStaticLayout(item.content, contentPaint, textWidth)
                val height = Math.max(checkboxSize, layout.height.toFloat())

                // Draw Checkbox
                val checkboxLeft = marginLeft
                val checkboxTop = currentY + 4
                canvas.drawRect(
                    checkboxLeft,
                    checkboxTop,
                    checkboxLeft + checkboxSize,
                    checkboxTop + checkboxSize,
                    checkboxPaint
                )

                if (item.checked) {
                    // Draw Checkmark
                    val checkPath = Path().apply {
                        moveTo(checkboxLeft + 4f, checkboxTop + 11f)
                        lineTo(checkboxLeft + 9f, checkboxTop + 16f)
                        lineTo(checkboxLeft + 18f, checkboxTop + 5f)
                    }
                    canvas.drawPath(checkPath, checkmarkPaint)
                }

                // Draw Text
                canvas.save()
                canvas.translate(marginLeft + checkboxSize + 15, currentY)
                layout.draw(canvas)
                canvas.restore()

                currentY += height + 18
            }
        }

        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }
}
