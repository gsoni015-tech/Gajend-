package com.example.pdf

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream

object SamplePdfGenerator {

    fun generateInitialDocuments(context: Context): List<File> {
        val docsDir = File(context.filesDir, "NarayaniDocuments").apply { mkdirs() }
        val generated = mutableListOf<File>()

        val guidePdf = File(docsDir, "Narayani_User_Guide.pdf")
        if (!guidePdf.exists()) {
            createGuidePdf(guidePdf)
            generated.add(guidePdf)
        } else {
            generated.add(guidePdf)
        }

        val agreementPdf = File(docsDir, "Service_Agreement_Sample.pdf")
        if (!agreementPdf.exists()) {
            createAgreementPdf(agreementPdf)
            generated.add(agreementPdf)
        } else {
            generated.add(agreementPdf)
        }

        val financialPdf = File(docsDir, "Monthly_Financial_Summary.pdf")
        if (!financialPdf.exists()) {
            createFinancialPdf(financialPdf)
            generated.add(financialPdf)
        } else {
            generated.add(financialPdf)
        }

        return generated
    }

    private fun createGuidePdf(outputFile: File) {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas

        val paint = Paint().apply { isAntiAlias = true }

        // Header Background
        paint.color = Color.rgb(30, 58, 138) // Deep Blue
        canvas.drawRect(0f, 0f, 595f, 90f, paint)

        // Header Title
        paint.color = Color.WHITE
        paint.textSize = 24f
        paint.isFakeBoldText = true
        canvas.drawText("Narayani PDF Reader & Editor", 40f, 45f, paint)

        paint.textSize = 13f
        paint.isFakeBoldText = false
        canvas.drawText("Complete Guide & Feature Showcase • गाइड एवं उपयोग निर्देश", 40f, 70f, paint)

        // Body text
        paint.color = Color.rgb(17, 24, 39)
        paint.textSize = 15f
        paint.isFakeBoldText = true
        canvas.drawText("1. Key Features • मुख्य विशेषताएं", 40f, 130f, paint)

        paint.textSize = 12f
        paint.isFakeBoldText = false
        val items = listOf(
            "• Continuous Vertical List View: Smooth, uninterrupted document reading.",
            "• Advanced PDF Tools: PDF to JPG conversion, Image to PDF, Merge & Split, Compression.",
            "• Full PDF Editor: Draw annotations, highlight text, insert signatures & official stamps.",
            "• Hindi & English Font Support: Noto Sans Devanagari, Mukta, Hind, Poppins, Roboto, KrutiDev.",
            "• Word & Excel Viewer: Direct viewing of DOCX and XLSX documents with card/table modes.",
            "• Text-to-Speech (TTS): Listen to your documents in natural voices.",
            "• PIN Protection & Privacy: Secure your sensitive files with password protection."
        )

        var yPos = 160f
        for (item in items) {
            canvas.drawText(item, 50f, yPos, paint)
            yPos += 24f
        }

        yPos += 20f
        paint.textSize = 15f
        paint.isFakeBoldText = true
        canvas.drawText("2. PDF to JPG Conversion • पीडीएफ से फोटो में बदलें", 40f, yPos, paint)

        yPos += 30f
        paint.textSize = 12f
        paint.isFakeBoldText = false
        val jpgFeatures = listOf(
            "1. Open 'PDF Tools' (टूल्स) tab from bottom navigation.",
            "2. Select 'PDF to JPG (Pdf से jpg)' tool.",
            "3. Choose any PDF document, select quality and pages.",
            "4. Images are instantly saved to your Pictures/Gallery in high resolution."
        )
        for (f in jpgFeatures) {
            canvas.drawText(f, 50f, yPos, paint)
            yPos += 22f
        }

        // Footer
        paint.color = Color.rgb(156, 163, 175)
        paint.textSize = 10f
        canvas.drawText("Narayani PDF Suite • Clean, fast and ad-free experience", 40f, 800f, paint)

        document.finishPage(page)
        FileOutputStream(outputFile).use { document.writeTo(it) }
        document.close()
    }

    private fun createAgreementPdf(outputFile: File) {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas

        val paint = Paint().apply { isAntiAlias = true }

        // Banner
        paint.color = Color.rgb(220, 38, 38) // Crimson
        canvas.drawRect(0f, 0f, 595f, 60f, paint)

        paint.color = Color.WHITE
        paint.textSize = 20f
        paint.isFakeBoldText = true
        canvas.drawText("STANDARD SERVICE CONTRACT • सेवा अनुबंध", 40f, 38f, paint)

        paint.color = Color.rgb(31, 41, 55)
        paint.textSize = 13f
        paint.isFakeBoldText = true
        canvas.drawText("Contract ID: NAR-2026-8891", 40f, 100f, paint)
        canvas.drawText("Effective Date: October 2026", 360f, 100f, paint)

        paint.isFakeBoldText = false
        paint.textSize = 11f
        var y = 140f
        val lines = listOf(
            "This document certifies mutual understanding between Party A (Provider) and Party B (Client).",
            "Scope of Work includes document digitization, PDF formatting, indexing, and high-security archival.",
            "All terms and conditions remain binding upon digital signature and stamp verification.",
            "Confidentiality & Privacy: Data processed through Narayani PDF Reader remains 100% on-device."
        )
        for (line in lines) {
            canvas.drawText(line, 40f, y, paint)
            y += 24f
        }

        // Stamp and Signature placeholder box
        paint.color = Color.rgb(243, 244, 246)
        canvas.drawRect(40f, 600f, 260f, 720f, paint)
        canvas.drawRect(320f, 600f, 540f, 720f, paint)

        paint.color = Color.rgb(107, 114, 128)
        paint.textSize = 11f
        canvas.drawText("[ Tap Edit to Sign Here ]", 70f, 665f, paint)
        canvas.drawText("[ Tap Edit for Official Stamp ]", 345f, 665f, paint)

        document.finishPage(page)
        FileOutputStream(outputFile).use { document.writeTo(it) }
        document.close()
    }

    private fun createFinancialPdf(outputFile: File) {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas

        val paint = Paint().apply { isAntiAlias = true }

        paint.color = Color.rgb(16, 185, 129) // Emerald
        canvas.drawRect(0f, 0f, 595f, 70f, paint)

        paint.color = Color.WHITE
        paint.textSize = 22f
        paint.isFakeBoldText = true
        canvas.drawText("FINANCIAL STATEMENT • वित्तीय विवरण", 40f, 42f, paint)

        paint.color = Color.rgb(17, 24, 39)
        paint.textSize = 13f
        paint.isFakeBoldText = true
        canvas.drawText("Quarterly Performance Review (Q3 2026)", 40f, 110f, paint)

        // Draw a sample table
        paint.color = Color.rgb(229, 231, 235)
        canvas.drawRect(40f, 140f, 555f, 175f, paint)

        paint.color = Color.rgb(31, 41, 55)
        paint.textSize = 11f
        paint.isFakeBoldText = true
        canvas.drawText("Category • श्रेणी", 50f, 162f, paint)
        canvas.drawText("Projected (₹)", 260f, 162f, paint)
        canvas.drawText("Actual (₹)", 380f, 162f, paint)
        canvas.drawText("Variance", 480f, 162f, paint)

        val rows = listOf(
            Triple("Document Operations", "₹ 2,50,000", "₹ 2,42,000"),
            Triple("Software Licensing", "₹ 1,20,000", "₹ 1,18,500"),
            Triple("Storage & Cloud Sync", "₹ 80,000", "₹ 75,000"),
            Triple("Digital Signatures", "₹ 60,000", "₹ 62,000")
        )

        paint.isFakeBoldText = false
        var rowY = 205f
        for ((cat, proj, act) in rows) {
            canvas.drawText(cat, 50f, rowY, paint)
            canvas.drawText(proj, 260f, rowY, paint)
            canvas.drawText(act, 380f, rowY, paint)
            canvas.drawText("+3.2%", 480f, rowY, paint)
            rowY += 30f
        }

        document.finishPage(page)
        FileOutputStream(outputFile).use { document.writeTo(it) }
        document.close()
    }
}
