package com.example.pdf

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream

object SamplePdfGenerator {

    fun generateSamplePdfsIfNeeded(context: Context): List<File> {
        val docsDir = File(context.filesDir, "Documents")
        if (!docsDir.exists()) {
            docsDir.mkdirs()
        }

        val generatedFiles = mutableListOf<File>()

        val file1 = File(docsDir, "Kotlin_Compose_Architecture_Guide.pdf")
        if (!file1.exists()) {
            createGuidePdf(file1)
            generatedFiles.add(file1)
        } else {
            generatedFiles.add(file1)
        }

        val file2 = File(docsDir, "Quarterly_Financial_Report_2026.pdf")
        if (!file2.exists()) {
            createFinancialPdf(file2)
            generatedFiles.add(file2)
        } else {
            generatedFiles.add(file2)
        }

        val file3 = File(docsDir, "Software_Services_Agreement_NDA.pdf")
        if (!file3.exists()) {
            createAgreementPdf(file3)
            generatedFiles.add(file3)
        } else {
            generatedFiles.add(file3)
        }

        return generatedFiles
    }

    private fun createGuidePdf(outputFile: File) {
        val document = PdfDocument()
        val pageWidth = 595 // A4 standard pt at 72dpi
        val pageHeight = 842

        // Page 1
        run {
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas
            drawHeader(canvas, "Android Modern Architecture", "Jetpack Compose & Kotlin", 1, 3, Color.rgb(211, 47, 47))

            val paint = Paint().apply { isAntiAlias = true }
            paint.color = Color.rgb(30, 41, 59)
            paint.textSize = 14f

            var y = 140f
            y = drawSectionTitle(canvas, "1. Declarative UI Principles", y, Color.rgb(211, 47, 47))
            y = drawParagraph(
                canvas,
                "Jetpack Compose accelerates Android UI development with less code, powerful Kotlin APIs, and intuitive declarative reactive components. State flows downward via parameters, and events flow upward via callbacks.",
                y, pageWidth - 100
            )

            y += 20f
            y = drawSectionTitle(canvas, "2. Unidirectional Data Flow (UDF)", y, Color.rgb(211, 47, 47))
            y = drawParagraph(
                canvas,
                "In UDF architecture, ViewModel exposes StateFlow<UiState> observed via collectAsStateWithLifecycle. This pattern guarantees predictable UI transitions, simplifies unit testing, and prevents common concurrency race conditions.",
                y, pageWidth - 100
            )

            // Code box
            y += 20f
            drawCodeBox(
                canvas,
                listOf(
                    "@Composable",
                    "fun PdfViewer(viewModel: PdfViewModel = viewModel()) {",
                    "    val state by viewModel.uiState.collectAsStateWithLifecycle()",
                    "    Scaffold(topBar = { ViewerTopBar(state.title) }) { padding ->",
                    "        PdfCanvas(modifier = Modifier.padding(padding))",
                    "    }",
                    "}"
                ),
                y, pageWidth
            )

            document.finishPage(page)
        }

        // Page 2
        run {
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 2).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas
            drawHeader(canvas, "Android Modern Architecture", "Jetpack Compose & Kotlin", 2, 3, Color.rgb(211, 47, 47))

            var y = 140f
            y = drawSectionTitle(canvas, "3. High Performance PDF Rendering", y, Color.rgb(211, 47, 47))
            y = drawParagraph(
                canvas,
                "Android native android.graphics.pdf.PdfRenderer allows hardware-accelerated bitmap rasterization directly from memory-mapped ParcelFileDescriptor. Paired with LRU memory caching and Coroutines dispatchers, 60fps rendering is achieved effortlessly.",
                y, pageWidth - 100
            )

            y += 25f
            y = drawSectionTitle(canvas, "4. Key Engineering Checklist", y, Color.rgb(211, 47, 47))
            val items = listOf(
                "• Strict Edge-to-Edge with WindowInsets handling",
                "• Room Database for favorites and bookmarks persistence",
                "• ActivityResultContracts for modern Zero-Permission photo picking",
                "• Smooth Pinch-to-Zoom transform gestures with clamp boundaries",
                "• Dynamic Night Mode reader with color matrix filters"
            )
            for (item in items) {
                val p = Paint().apply {
                    isAntiAlias = true
                    textSize = 13f
                    color = Color.rgb(51, 65, 85)
                }
                canvas.drawText(item, 50f, y, p)
                y += 28f
            }

            document.finishPage(page)
        }

        // Page 3
        run {
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 3).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas
            drawHeader(canvas, "Android Modern Architecture", "Jetpack Compose & Kotlin", 3, 3, Color.rgb(211, 47, 47))

            var y = 140f
            y = drawSectionTitle(canvas, "5. Summary & Deployment Notes", y, Color.rgb(211, 47, 47))
            y = drawParagraph(
                canvas,
                "This document confirms full compilation readiness under Android 14/15/16. All modules, including Auto-fetcher, complete reader viewer, and PDF tools suite adhere strictly to Google Play policy and Material 3 design guidelines.",
                y, pageWidth - 100
            )

            document.finishPage(page)
        }

        FileOutputStream(outputFile).use { out ->
            document.writeTo(out)
        }
        document.close()
    }

    private fun createFinancialPdf(outputFile: File) {
        val document = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842

        // Page 1
        run {
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas
            drawHeader(canvas, "Global Financial Services", "Quarterly Report & Analytics Q3", 1, 2, Color.rgb(15, 118, 110))

            var y = 140f
            y = drawSectionTitle(canvas, "Executive Summary", y, Color.rgb(15, 118, 110))
            y = drawParagraph(
                canvas,
                "Total gross merchandise volume reached $14.2M in the current fiscal period, representing a 28% year-over-year surge driven by cloud software integrations and automated document workflows.",
                y, pageWidth - 100
            )

            y += 25f
            y = drawSectionTitle(canvas, "Revenue Breakdown by Category", y, Color.rgb(15, 118, 110))

            // Draw Table
            val headers = listOf("Division", "Target ($)", "Actual ($)", "Growth")
            val rows = listOf(
                listOf("Enterprise SaaS", "4,500,000", "5,230,000", "+16.2%"),
                listOf("Mobile Utilities", "2,800,000", "3,410,000", "+21.8%"),
                listOf("Cloud Security", "3,200,000", "3,950,000", "+23.4%"),
                listOf("Professional Consulting", "1,500,000", "1,620,000", "+8.0%")
            )

            y = drawTable(canvas, headers, rows, y, pageWidth)

            document.finishPage(page)
        }

        // Page 2
        run {
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 2).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas
            drawHeader(canvas, "Global Financial Services", "Quarterly Report & Analytics Q3", 2, 2, Color.rgb(15, 118, 110))

            var y = 140f
            y = drawSectionTitle(canvas, "Strategic Outlook & Growth Projections", y, Color.rgb(15, 118, 110))
            y = drawParagraph(
                canvas,
                "Management maintains an optimistic projection for the upcoming quarter with an expected 35% compound customer retention across multi-device document processing and security vault capabilities.",
                y, pageWidth - 100
            )

            document.finishPage(page)
        }

        FileOutputStream(outputFile).use { out ->
            document.writeTo(out)
        }
        document.close()
    }

    private fun createAgreementPdf(outputFile: File) {
        val document = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842

        // Page 1
        run {
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas
            drawHeader(canvas, "Mutual Non-Disclosure Agreement", "Standard Confidentiality Contract", 1, 2, Color.rgb(30, 58, 138))

            var y = 140f
            y = drawSectionTitle(canvas, "1. Parties and Recitals", y, Color.rgb(30, 58, 138))
            y = drawParagraph(
                canvas,
                "This Non-Disclosure Agreement ('Agreement') is entered into as of October 2026 by and between the Disclosing Party and Receiving Party. Both parties agree to safeguard proprietary source assets, algorithms, and documentation.",
                y, pageWidth - 100
            )

            y += 25f
            y = drawSectionTitle(canvas, "2. Obligations of Non-Disclosure", y, Color.rgb(30, 58, 138))
            y = drawParagraph(
                canvas,
                "The Receiving Party agrees to treat all technical materials, encryption keys, and business trade secrets with reasonable care, preventing unauthorized disclosure, reproduction, or distribution to external third parties.",
                y, pageWidth - 100
            )

            document.finishPage(page)
        }

        // Page 2
        run {
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 2).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas
            drawHeader(canvas, "Mutual Non-Disclosure Agreement", "Standard Confidentiality Contract", 2, 2, Color.rgb(30, 58, 138))

            var y = 140f
            y = drawSectionTitle(canvas, "3. Term & Governing Law", y, Color.rgb(30, 58, 138))
            y = drawParagraph(
                canvas,
                "This Agreement shall remain in full force and effect for three (3) calendar years from the execution date. This document shall be governed and construed in accordance with standard legal jurisdictions.",
                y, pageWidth - 100
            )

            y += 40f
            val sigPaint = Paint().apply {
                isAntiAlias = true
                textSize = 12f
                color = Color.DKGRAY
            }
            canvas.drawText("Authorized Signature: _______________________", 50f, y, sigPaint)
            y += 30f
            canvas.drawText("Title / Capacity: Managing Director", 50f, y, sigPaint)

            document.finishPage(page)
        }

        FileOutputStream(outputFile).use { out ->
            document.writeTo(out)
        }
        document.close()
    }

    private fun drawHeader(
        canvas: Canvas,
        superTitle: String,
        title: String,
        pageNum: Int,
        totalPages: Int,
        themeColor: Int
    ) {
        val bannerPaint = Paint().apply {
            color = themeColor
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, 595f, 18f, bannerPaint)

        val titlePaint = Paint().apply {
            isAntiAlias = true
            textSize = 20f
            isFakeBoldText = true
            color = Color.rgb(15, 23, 42)
        }
        canvas.drawText(title, 50f, 65f, titlePaint)

        val subPaint = Paint().apply {
            isAntiAlias = true
            textSize = 11f
            color = Color.rgb(100, 116, 139)
        }
        canvas.drawText(superTitle.uppercase(), 50f, 85f, subPaint)

        // Divider
        val linePaint = Paint().apply {
            color = Color.rgb(226, 232, 240)
            strokeWidth = 1.5f
        }
        canvas.drawLine(50f, 100f, 545f, 100f, linePaint)

        // Page footer
        canvas.drawLine(50f, 800f, 545f, 800f, linePaint)
        val footerPaint = Paint().apply {
            isAntiAlias = true
            textSize = 10f
            color = Color.rgb(148, 163, 184)
        }
        canvas.drawText("Narayani PDF Reader Document System", 50f, 818f, footerPaint)
        val pageStr = "Page $pageNum of $totalPages"
        val textWidth = footerPaint.measureText(pageStr)
        canvas.drawText(pageStr, 545f - textWidth, 818f, footerPaint)
    }

    private fun drawSectionTitle(canvas: Canvas, title: String, y: Float, color: Int): Float {
        val paint = Paint().apply {
            isAntiAlias = true
            textSize = 15f
            isFakeBoldText = true
            this.color = color
        }
        canvas.drawText(title, 50f, y, paint)
        return y + 20f
    }

    private fun drawParagraph(canvas: Canvas, text: String, startY: Float, maxWidth: Int): Float {
        val paint = Paint().apply {
            isAntiAlias = true
            textSize = 12.5f
            color = Color.rgb(51, 65, 85)
        }
        var y = startY
        val words = text.split(" ")
        var line = StringBuilder()

        for (word in words) {
            val testLine = if (line.isEmpty()) word else "$line $word"
            if (paint.measureText(testLine) > maxWidth) {
                canvas.drawText(line.toString(), 50f, y, paint)
                y += 20f
                line = StringBuilder(word)
            } else {
                line = StringBuilder(testLine)
            }
        }
        if (line.isNotEmpty()) {
            canvas.drawText(line.toString(), 50f, y, paint)
            y += 20f
        }
        return y
    }

    private fun drawCodeBox(canvas: Canvas, lines: List<String>, startY: Float, pageWidth: Int): Float {
        val boxHeight = lines.size * 22f + 25f
        val bgPaint = Paint().apply {
            color = Color.rgb(241, 245, 249)
            style = Paint.Style.FILL
        }
        val borderPaint = Paint().apply {
            color = Color.rgb(203, 213, 225)
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        val rect = RectF(50f, startY, (pageWidth - 50).toFloat(), startY + boxHeight)
        canvas.drawRoundRect(rect, 8f, 8f, bgPaint)
        canvas.drawRoundRect(rect, 8f, 8f, borderPaint)

        val codePaint = Paint().apply {
            isAntiAlias = true
            textSize = 11.5f
            color = Color.rgb(15, 23, 42)
        }
        var y = startY + 22f
        for (l in lines) {
            canvas.drawText(l, 65f, y, codePaint)
            y += 20f
        }
        return startY + boxHeight + 15f
    }

    private fun drawTable(
        canvas: Canvas,
        headers: List<String>,
        rows: List<List<String>>,
        startY: Float,
        pageWidth: Int
    ): Float {
        val colWidth = (pageWidth - 100) / headers.size.toFloat()
        var y = startY

        // Header Background
        val headerBg = Paint().apply {
            color = Color.rgb(238, 242, 246)
            style = Paint.Style.FILL
        }
        canvas.drawRect(50f, y - 15f, 545f, y + 15f, headerBg)

        val headerPaint = Paint().apply {
            isAntiAlias = true
            textSize = 11.5f
            isFakeBoldText = true
            color = Color.rgb(30, 41, 59)
        }
        for (i in headers.indices) {
            canvas.drawText(headers[i], 55f + i * colWidth, y + 5f, headerPaint)
        }
        y += 25f

        val cellPaint = Paint().apply {
            isAntiAlias = true
            textSize = 11f
            color = Color.rgb(51, 65, 85)
        }
        val linePaint = Paint().apply {
            color = Color.rgb(226, 232, 240)
            strokeWidth = 0.8f
        }

        for (row in rows) {
            canvas.drawLine(50f, y - 5f, 545f, y - 5f, linePaint)
            for (i in row.indices) {
                canvas.drawText(row[i], 55f + i * colWidth, y + 12f, cellPaint)
            }
            y += 24f
        }
        return y + 10f
    }
}
