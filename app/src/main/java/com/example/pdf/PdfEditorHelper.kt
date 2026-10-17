package com.example.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

// Data models for all PDF editing capabilities

data class DrawingPoint(val x: Float, val y: Float)

data class DrawingStroke(
    val points: List<DrawingPoint>,
    val color: Long,
    val strokeWidth: Float = 3f,
    val isHighlighter: Boolean = false
)

data class TextAnnotation(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val xPercent: Float, // 0.0 .. 1.0 relative to page width
    val yPercent: Float, // 0.0 .. 1.0 relative to page height
    val fontSizePt: Float = 15f,
    val color: Long = 0xFF0F172A,
    val backgroundColor: Long? = null,
    val isBold: Boolean = false,
    val isItalic: Boolean = false
)

data class SignatureAnnotation(
    val id: String = UUID.randomUUID().toString(),
    val strokes: List<DrawingStroke>,
    val xPercent: Float,
    val yPercent: Float,
    val scale: Float = 1.0f
)

enum class StampType(val title: String, val color: Long) {
    APPROVED("APPROVED", 0xFF16A34A),
    REJECTED("REJECTED", 0xFFDC2626),
    CONFIDENTIAL("CONFIDENTIAL", 0xFF991B1B),
    DRAFT("DRAFT", 0xFFEA580C),
    PAID("PAID", 0xFF059669),
    SIGN_HERE("SIGN HERE ✍️", 0xFF2563EB),
    COMPLETED("COMPLETED", 0xFF0D9488),
    URGENT("URGENT ⚠️", 0xFFB91C1C)
}

data class StampAnnotation(
    val id: String = UUID.randomUUID().toString(),
    val stampType: StampType,
    val customText: String? = null,
    val xPercent: Float,
    val yPercent: Float,
    val rotationAngle: Float = -12f
)

data class WatermarkConfig(
    val text: String = "CONFIDENTIAL",
    val opacity: Float = 0.22f,
    val angle: Float = -45f,
    val color: Long = 0xFFDC2626,
    val applyToAllPages: Boolean = true
)

data class PageEditState(
    val pageId: String = UUID.randomUUID().toString(),
    val originalPageIndex: Int, // -1 if newly created blank page
    val rotationDegrees: Int = 0, // 0, 90, 180, 270
    val strokes: List<DrawingStroke> = emptyList(),
    val textAnnotations: List<TextAnnotation> = emptyList(),
    val signatureAnnotations: List<SignatureAnnotation> = emptyList(),
    val stampAnnotations: List<StampAnnotation> = emptyList(),
    val watermark: WatermarkConfig? = null
)

object PdfEditorHelper {

    suspend fun saveEditedPdf(
        context: Context,
        sourceFile: File,
        pageStates: List<PageEditState>,
        outputFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        val document = PdfDocument()
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null

        try {
            if (sourceFile.exists()) {
                pfd = ParcelFileDescriptor.open(sourceFile, ParcelFileDescriptor.MODE_READ_ONLY)
                renderer = PdfRenderer(pfd)
            }

            for ((outPageIndex, state) in pageStates.withIndex()) {
                var baseW = 595
                var baseH = 842

                var sourceBitmap: Bitmap? = null

                if (state.originalPageIndex >= 0 && renderer != null && state.originalPageIndex < renderer.pageCount) {
                    val page = renderer.openPage(state.originalPageIndex)
                    baseW = page.width
                    baseH = page.height

                    // Render at high resolution for clean vector-like appearance
                    val scaleFactor = 2f
                    val renderW = (baseW * scaleFactor).toInt().coerceAtLeast(1)
                    val renderH = (baseH * scaleFactor).toInt().coerceAtLeast(1)

                    val bmp = Bitmap.createBitmap(renderW, renderH, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                    page.close()
                    sourceBitmap = bmp
                }

                // Determine final page dimensions considering 90 / 270 rotation
                val rot = (state.rotationDegrees % 360 + 360) % 360
                val (pageW, pageH) = if (rot == 90 || rot == 270) {
                    Pair(baseH, baseW)
                } else {
                    Pair(baseW, baseH)
                }

                val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, outPageIndex + 1).create()
                val docPage = document.startPage(pageInfo)
                val canvas = docPage.canvas

                // Fill clean white background
                canvas.drawColor(Color.WHITE)

                // Draw source PDF page bitmap with rotation if available
                if (sourceBitmap != null) {
                    canvas.save()
                    if (rot != 0) {
                        canvas.translate(pageW / 2f, pageH / 2f)
                        canvas.rotate(rot.toFloat())
                        canvas.translate(-baseW / 2f, -baseH / 2f)
                    }
                    val paint = Paint().apply { isFilterBitmap = true }
                    canvas.drawBitmap(sourceBitmap, null, Rect(0, 0, baseW, baseH), paint)
                    canvas.restore()
                    sourceBitmap.recycle()
                }

                // Draw Watermark (Behind text/drawings or semi-transparent overlay)
                state.watermark?.let { wm ->
                    drawWatermarkOnCanvas(canvas, pageW, pageH, wm)
                }

                // Draw Freehand strokes
                for (stroke in state.strokes) {
                    drawStrokeOnCanvas(canvas, pageW, pageH, stroke)
                }

                // Draw Text Annotations
                for (textAnn in state.textAnnotations) {
                    drawTextAnnotationOnCanvas(canvas, pageW, pageH, textAnn)
                }

                // Draw Signatures
                for (sig in state.signatureAnnotations) {
                    drawSignatureOnCanvas(canvas, pageW, pageH, sig)
                }

                // Draw Stamps
                for (stamp in state.stampAnnotations) {
                    drawStampOnCanvas(canvas, pageW, pageH, stamp)
                }

                document.finishPage(docPage)
            }

            FileOutputStream(outputFile).use { out ->
                document.writeTo(out)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            try { renderer?.close() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
            document.close()
        }
    }

    private fun drawWatermarkOnCanvas(
        canvas: Canvas,
        pageW: Int,
        pageH: Int,
        wm: WatermarkConfig
    ) {
        if (wm.text.isBlank()) return
        canvas.save()
        canvas.translate(pageW / 2f, pageH / 2f)
        canvas.rotate(wm.angle)

        val alphaInt = (wm.opacity * 255).toInt().coerceIn(10, 255)
        val paint = Paint().apply {
            isAntiAlias = true
            textSize = (pageW * 0.12f).coerceIn(36f, 96f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = (wm.color.toInt() and 0x00FFFFFF) or (alphaInt shl 24)
            textAlign = Paint.Align.CENTER
        }

        canvas.drawText(wm.text.uppercase(), 0f, paint.textSize / 3f, paint)
        canvas.restore()
    }

    private fun drawStrokeOnCanvas(
        canvas: Canvas,
        pageW: Int,
        pageH: Int,
        stroke: DrawingStroke
    ) {
        if (stroke.points.size < 2) return

        val paint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = stroke.strokeWidth
            if (stroke.isHighlighter) {
                // Semi-transparent highlighter with bright tint
                val base = stroke.color.toInt()
                color = (base and 0x00FFFFFF) or (0x70 shl 24)
                strokeWidth = stroke.strokeWidth * 3.5f
            } else {
                color = stroke.color.toInt()
            }
        }

        val path = Path()
        val first = stroke.points.first()
        path.moveTo(first.x * pageW, first.y * pageH)

        for (i in 1 until stroke.points.size) {
            val pt = stroke.points[i]
            path.lineTo(pt.x * pageW, pt.y * pageH)
        }

        canvas.drawPath(path, paint)
    }

    private fun drawTextAnnotationOnCanvas(
        canvas: Canvas,
        pageW: Int,
        pageH: Int,
        textAnn: TextAnnotation
    ) {
        if (textAnn.text.isBlank()) return

        val x = textAnn.xPercent * pageW
        val y = textAnn.yPercent * pageH

        val textPaint = Paint().apply {
            isAntiAlias = true
            textSize = textAnn.fontSizePt
            color = textAnn.color.toInt()
            typeface = when {
                textAnn.isBold && textAnn.isItalic -> Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC)
                textAnn.isBold -> Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAnn.isItalic -> Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                else -> Typeface.DEFAULT
            }
        }

        val lines = textAnn.text.split("\n")
        var maxLineWidth = 0f
        for (line in lines) {
            val w = textPaint.measureText(line)
            if (w > maxLineWidth) maxLineWidth = w
        }

        val lineHeight = textPaint.fontSpacing
        val totalHeight = lineHeight * lines.size

        // Draw background if specified
        textAnn.backgroundColor?.let { bgCol ->
            val bgPaint = Paint().apply {
                color = bgCol.toInt()
                style = Paint.Style.FILL
            }
            val borderPaint = Paint().apply {
                color = (textAnn.color.toInt() and 0x00FFFFFF) or (0x60 shl 24)
                style = Paint.Style.STROKE
                strokeWidth = 1f
            }
            val padding = 8f
            val rect = RectF(
                x - padding,
                y - lineHeight + (lineHeight * 0.25f) - padding,
                x + maxLineWidth + padding,
                y + totalHeight - lineHeight + padding
            )
            canvas.drawRoundRect(rect, 4f, 4f, bgPaint)
            canvas.drawRoundRect(rect, 4f, 4f, borderPaint)
        }

        var currentY = y
        for (line in lines) {
            canvas.drawText(line, x, currentY, textPaint)
            currentY += lineHeight
        }
    }

    private fun drawSignatureOnCanvas(
        canvas: Canvas,
        pageW: Int,
        pageH: Int,
        sig: SignatureAnnotation
    ) {
        val startX = sig.xPercent * pageW
        val startY = sig.yPercent * pageH

        val scale = sig.scale
        for (stroke in sig.strokes) {
            if (stroke.points.size < 2) continue
            val paint = Paint().apply {
                isAntiAlias = true
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                color = stroke.color.toInt()
                strokeWidth = stroke.strokeWidth * scale
            }

            val path = Path()
            val first = stroke.points.first()
            path.moveTo(startX + first.x * scale, startY + first.y * scale)

            for (i in 1 until stroke.points.size) {
                val pt = stroke.points[i]
                path.lineTo(startX + pt.x * scale, startY + pt.y * scale)
            }

            canvas.drawPath(path, paint)
        }
    }

    private fun drawStampOnCanvas(
        canvas: Canvas,
        pageW: Int,
        pageH: Int,
        stamp: StampAnnotation
    ) {
        val title = stamp.customText?.takeIf { it.isNotBlank() } ?: stamp.stampType.title
        val x = stamp.xPercent * pageW
        val y = stamp.yPercent * pageH

        canvas.save()
        canvas.translate(x, y)
        canvas.rotate(stamp.rotationAngle)

        val stampColor = stamp.stampType.color.toInt()

        val textPaint = Paint().apply {
            isAntiAlias = true
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = stampColor
            textAlign = Paint.Align.CENTER
        }

        val textWidth = textPaint.measureText(title)
        val boxWidth = textWidth + 36f
        val boxHeight = 44f

        val rect = RectF(-boxWidth / 2f, -boxHeight / 2f, boxWidth / 2f, boxHeight / 2f)

        // Semi-transparent fill
        val fillPaint = Paint().apply {
            color = (stampColor and 0x00FFFFFF) or (0x22 shl 24)
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(rect, 8f, 8f, fillPaint)

        // Outer border
        val borderPaint = Paint().apply {
            color = stampColor
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
        }
        canvas.drawRoundRect(rect, 8f, 8f, borderPaint)

        // Inner dotted border
        val innerRect = RectF(rect.left + 3f, rect.top + 3f, rect.right - 3f, rect.bottom - 3f)
        val innerPaint = Paint().apply {
            color = stampColor
            style = Paint.Style.STROKE
            strokeWidth = 1f
            pathEffect = DashPathEffect(floatArrayOf(4f, 4f), 0f)
        }
        canvas.drawRoundRect(innerRect, 5f, 5f, innerPaint)

        canvas.drawText(title, 0f, 7f, textPaint)

        canvas.restore()
    }
}
