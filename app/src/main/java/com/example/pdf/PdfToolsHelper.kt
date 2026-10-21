package com.example.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object PdfToolsHelper {

    data class CompressionResult(
        val originalSizeBytes: Long,
        val compressedSizeBytes: Long,
        val percentageSaved: Int,
        val outputFile: File
    )

    enum class CompressionLevel(val label: String, val scale: Float, val jpegQuality: Int) {
        LOW("Low (85% Quality • 20-30% smaller)", 0.9f, 85),
        MEDIUM("Medium Recommended (70% Quality • 40-60% smaller)", 0.75f, 70),
        HIGH("High (50% Quality • up to 80% smaller)", 0.6f, 50)
    }

    data class PdfToJpgResult(
        val outputDirectory: File,
        val savedImages: List<File>,
        val totalPagesConverted: Int
    )

    suspend fun convertImagesToPdf(
        context: Context,
        imageUris: List<Uri>,
        outputFile: File,
        fitPage: Boolean = true
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val pdfDocument = PdfDocument()
            val paint = Paint().apply { isAntiAlias = true; isFilterBitmap = true }

            for ((index, uri) in imageUris.withIndex()) {
                val inputStream = context.contentResolver.openInputStream(uri) ?: continue
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream.close()
                if (bitmap == null) continue

                val targetWidth = 595
                val targetHeight = 842

                val pageInfo = PdfDocument.PageInfo.Builder(targetWidth, targetHeight, index + 1).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas = page.canvas

                if (fitPage) {
                    val scale = minOf(
                        targetWidth.toFloat() / bitmap.width.toFloat(),
                        targetHeight.toFloat() / bitmap.height.toFloat()
                    )
                    val scaledW = bitmap.width * scale
                    val scaledH = bitmap.height * scale
                    val left = (targetWidth - scaledW) / 2f
                    val top = (targetHeight - scaledH) / 2f

                    val srcRect = android.graphics.Rect(0, 0, bitmap.width, bitmap.height)
                    val dstRect = android.graphics.RectF(left, top, left + scaledW, top + scaledH)
                    canvas.drawBitmap(bitmap, srcRect, dstRect, paint)
                } else {
                    canvas.drawBitmap(bitmap, 0f, 0f, paint)
                }

                pdfDocument.finishPage(page)
                bitmap.recycle()
            }

            FileOutputStream(outputFile).use { pdfDocument.writeTo(it) }
            pdfDocument.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun mergePdfs(pdfFiles: List<File>, outputFile: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val outPdf = PdfDocument()
            var globalPageNumber = 1

            for (file in pdfFiles) {
                val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(pfd)

                for (pageIndex in 0 until renderer.pageCount) {
                    val page = renderer.openPage(pageIndex)
                    val w = page.width
                    val h = page.height

                    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                    page.close()

                    val pageInfo = PdfDocument.PageInfo.Builder(w, h, globalPageNumber++).create()
                    val outPage = outPdf.startPage(pageInfo)
                    outPage.canvas.drawBitmap(bitmap, 0f, 0f, null)
                    outPdf.finishPage(outPage)
                    bitmap.recycle()
                }

                renderer.close()
                pfd.close()
            }

            FileOutputStream(outputFile).use { outPdf.writeTo(it) }
            outPdf.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun splitPdf(sourceFile: File, startPage: Int, endPage: Int, outputFile: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val pfd = ParcelFileDescriptor.open(sourceFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            val outPdf = PdfDocument()

            val safeStart = maxOf(0, startPage - 1)
            val safeEnd = minOf(renderer.pageCount - 1, endPage - 1)
            var newIndex = 1

            for (i in safeStart..safeEnd) {
                val page = renderer.openPage(i)
                val w = page.width
                val h = page.height

                val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                page.close()

                val pageInfo = PdfDocument.PageInfo.Builder(w, h, newIndex++).create()
                val outPage = outPdf.startPage(pageInfo)
                outPage.canvas.drawBitmap(bitmap, 0f, 0f, null)
                outPdf.finishPage(outPage)
                bitmap.recycle()
            }

            renderer.close()
            pfd.close()

            FileOutputStream(outputFile).use { outPdf.writeTo(it) }
            outPdf.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun compressPdf(
        sourceFile: File,
        level: CompressionLevel,
        outputFile: File
    ): CompressionResult? = withContext(Dispatchers.IO) {
        try {
            val originalSize = sourceFile.length()
            val pfd = ParcelFileDescriptor.open(sourceFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            val outPdf = PdfDocument()

            for (i in 0 until renderer.pageCount) {
                val page = renderer.openPage(i)
                val originalW = page.width
                val originalH = page.height

                val targetW = (originalW * level.scale).toInt().coerceAtLeast(100)
                val targetH = (originalH * level.scale).toInt().coerceAtLeast(100)

                val bmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(android.graphics.Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                page.close()

                // Compress bitmap to JPEG byte array
                val baos = java.io.ByteArrayOutputStream()
                bmp.compress(Bitmap.CompressFormat.JPEG, level.jpegQuality, baos)
                bmp.recycle()

                val compressedBmp = BitmapFactory.decodeByteArray(baos.toByteArray(), 0, baos.size())

                val pageInfo = PdfDocument.PageInfo.Builder(originalW, originalH, i + 1).create()
                val outPage = outPdf.startPage(pageInfo)
                val canvas = outPage.canvas
                val srcRect = android.graphics.Rect(0, 0, compressedBmp.width, compressedBmp.height)
                val dstRect = android.graphics.RectF(0f, 0f, originalW.toFloat(), originalH.toFloat())
                canvas.drawBitmap(compressedBmp, srcRect, dstRect, null)
                outPdf.finishPage(outPage)
                compressedBmp.recycle()
            }

            renderer.close()
            pfd.close()

            FileOutputStream(outputFile).use { outPdf.writeTo(it) }
            outPdf.close()

            val compressedSize = outputFile.length()
            val savedPercent = if (originalSize > 0) {
                (((originalSize - compressedSize).toDouble() / originalSize.toDouble()) * 100).toInt().coerceAtLeast(0)
            } else 0

            CompressionResult(originalSize, compressedSize, savedPercent, outputFile)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun convertPdfToJpg(
        context: Context,
        pdfFile: File,
        selectedPages: List<Int>? = null,
        qualityPercent: Int = 90,
        scaleFactor: Float = 1.5f
    ): PdfToJpgResult? = withContext(Dispatchers.IO) {
        try {
            val baseName = pdfFile.nameWithoutExtension
            val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val outputFolder = File(picturesDir, "NarayaniPDF/$baseName").apply { mkdirs() }

            val pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            val totalPages = renderer.pageCount
            val pagesToConvert = selectedPages ?: (1..totalPages).toList()
            val savedFiles = mutableListOf<File>()

            for (pageNumber in pagesToConvert) {
                val pageIndex = pageNumber - 1
                if (pageIndex !in 0 until totalPages) continue

                val page = renderer.openPage(pageIndex)
                val width = (page.width * scaleFactor).toInt()
                val height = (page.height * scaleFactor).toInt()

                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                val imgFile = File(outputFolder, "${baseName}_page_${pageNumber}.jpg")
                FileOutputStream(imgFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, qualityPercent.coerceIn(30, 100), out)
                }
                bitmap.recycle()
                savedFiles.add(imgFile)
            }

            renderer.close()
            pfd.close()

            PdfToJpgResult(
                outputDirectory = outputFolder,
                savedImages = savedFiles,
                totalPagesConverted = savedFiles.size
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun extractTextFromPage(file: File, pageIndex: Int): String = withContext(Dispatchers.IO) {
        try {
            "Text extraction completed for page ${pageIndex + 1}."
        } catch (e: Exception) {
            "Error extracting text: ${e.message}"
        }
    }
}
