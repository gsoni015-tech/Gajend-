package com.example.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.example.ui.theme.AppFonts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object PdfEditorHelper {

    suspend fun saveEditedPdf(
        context: Context,
        originalFile: File,
        pagesEditState: Map<Int, PageEditState>,
        outputFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        val newDocument = PdfDocument()

        try {
            pfd = ParcelFileDescriptor.open(originalFile, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            val pageCount = renderer.pageCount

            for (pageIndex in 0 until pageCount) {
                val page = renderer.openPage(pageIndex)
                val originalW = page.width
                val originalH = page.height

                val state = pagesEditState[pageIndex] ?: PageEditState(
                    pageId = "page_$pageIndex",
                    originalPageIndex = pageIndex
                )

                // Page dimensions considering rotation
                val isRotated90or270 = (state.rotationDegrees % 180) != 0
                val pageW = if (isRotated90or270) originalH else originalW
                val pageH = if (isRotated90or270) originalW else originalH

                val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, pageIndex + 1).create()
                val pdfPage = newDocument.startPage(pageInfo)
                val canvas = pdfPage.canvas

                // 1. Draw Watermark in the BACK (if configured)
                state.watermark?.let { wm ->
                    drawWatermarkOnCanvas(canvas, pageW, pageH, wm)
                }

                // 2. Render Original Page Bitmap
                val pageBitmap = Bitmap.createBitmap(originalW, originalH, Bitmap.Config.ARGB_8888)
                pageBitmap.eraseColor(Color.WHITE)
                page.render(pageBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                page.close()

                canvas.save()
                if (state.rotationDegrees != 0) {
                    canvas.rotate(state.rotationDegrees.toFloat(), pageW / 2f, pageH / 2f)
                }

                // Draw with MULTIPLY so background watermark stays visible behind text
                val pagePaint = Paint().apply {
                    isAntiAlias = true
                    xfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
                }
                val srcRect = Rect(0, 0, originalW, originalH)
                val dstRect = RectF(0f, 0f, originalW.toFloat(), originalH.toFloat())
                canvas.drawBitmap(pageBitmap, srcRect, dstRect, pagePaint)
                canvas.restore()
                pageBitmap.recycle()

                // 3. Draw Strokes & Highlighters
                for (stroke in state.strokes) {
                    drawStrokeOnCanvas(canvas, pageW, pageH, stroke)
                }

                // 4. Draw Text Annotations
                for (textAnn in state.textAnnotations) {
                    drawTextAnnotationOnCanvas(context, canvas, pageW, pageH, textAnn)
                }

                // 5. Draw Signatures
                for (sig in state.signatureAnnotations) {
                    drawSignatureOnCanvas(canvas, pageW, pageH, sig)
                }

                // 6. Draw Stamps
                for (stamp in state.stampAnnotations) {
                    drawStampOnCanvas(canvas, pageW, pageH, stamp)
                }

                newDocument.finishPage(pdfPage)
            }

            FileOutputStream(outputFile).use { newDocument.writeTo(it) }
            newDocument.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            try { renderer?.close() } catch (e: Exception) {}
            try { pfd?.close() } catch (e: Exception) {}
        }
    }

    private fun drawWatermarkOnCanvas(canvas: Canvas, pageW: Int, pageH: Int, wm: WatermarkConfig) {
        val paint = Paint().apply {
            isAntiAlias = true
            color = wm.color.toInt()
            alpha = (wm.opacity * 255).toInt().coerceIn(0, 255)
            textSize = pageW * 0.08f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        canvas.save()
        canvas.rotate(wm.angle, pageW / 2f, pageH / 2f)
        canvas.drawText(wm.text, pageW / 2f, pageH / 2f, paint)
        canvas.restore()
    }

    private fun drawStrokeOnCanvas(canvas: Canvas, pageW: Int, pageH: Int, stroke: DrawingStroke) {
        if (stroke.points.size < 2) return
        val paint = Paint().apply {
            isAntiAlias = true
            color = stroke.color.toInt()
            strokeWidth = stroke.strokeWidth
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            if (stroke.isHighlighter) {
                alpha = 110
                xfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
            }
        }
        val path = Path()
        val first = stroke.points.first()
        path.moveTo(first.x * pageW, first.y * pageH)
        for (i in 1 until stroke.points.size) {
            val p = stroke.points[i]
            path.lineTo(p.x * pageW, p.y * pageH)
        }
        canvas.drawPath(path, paint)
    }

    private fun drawTextAnnotationOnCanvas(
        context: Context,
        canvas: Canvas,
        pageW: Int,
        pageH: Int,
        ann: TextAnnotation
    ) {
        val x = ann.xPercent * pageW
        val y = ann.yPercent * pageH

        val selectedFont = AppFonts.SupportedFont.fromId(ann.fontFamilyId)
        val typeface = selectedFont.getAndroidTypeface(context)

        val textPaint = Paint().apply {
            isAntiAlias = true
            color = ann.color.toInt()
            textSize = ann.fontSizePt * 1.33f
            isFakeBoldText = ann.isBold
            if (ann.isItalic) textSkewX = -0.25f
            this.typeface = typeface
        }

        ann.backgroundColor?.let { bg ->
            val bounds = Rect()
            textPaint.getTextBounds(ann.text, 0, ann.text.length, bounds)
            val bgPaint = Paint().apply {
                isAntiAlias = true
                color = bg.toInt()
                style = Paint.Style.FILL
            }
            val pad = 8f
            val bgRect = RectF(
                x - pad,
                y - bounds.height() - pad,
                x + bounds.width() + pad,
                y + pad
            )
            canvas.drawRoundRect(bgRect, 4f, 4f, bgPaint)
        }

        canvas.drawText(ann.text, x, y, textPaint)
    }

    private fun drawSignatureOnCanvas(canvas: Canvas, pageW: Int, pageH: Int, sig: SignatureAnnotation) {
        val originX = sig.xPercent * pageW
        val originY = sig.yPercent * pageH
        canvas.save()
        canvas.translate(originX, originY)
        canvas.scale(sig.scale, sig.scale)

        for (stroke in sig.strokes) {
            drawStrokeOnCanvas(canvas, pageW, pageH, stroke)
        }
        canvas.restore()
    }

    private fun drawStampOnCanvas(canvas: Canvas, pageW: Int, pageH: Int, stamp: StampAnnotation) {
        val x = stamp.xPercent * pageW
        val y = stamp.yPercent * pageH
        val stampWidth = pageW * 0.35f
        val stampHeight = stampWidth * 0.45f

        canvas.save()
        canvas.rotate(stamp.rotationAngle, x + stampWidth / 2f, y + stampHeight / 2f)

        val borderPaint = Paint().apply {
            isAntiAlias = true
            color = stamp.stampType.color.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        val stampRect = RectF(x, y, x + stampWidth, y + stampHeight)
        canvas.drawRoundRect(stampRect, 8f, 8f, borderPaint)

        val textPaint = Paint().apply {
            isAntiAlias = true
            color = stamp.stampType.color.toInt()
            textSize = stampHeight * 0.35f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }

        val textToDraw = if (stamp.customText.isNotBlank()) stamp.customText else stamp.stampType.title
        canvas.drawText(textToDraw, x + stampWidth / 2f, y + stampHeight * 0.6f, textPaint)
        canvas.restore()
    }
}
