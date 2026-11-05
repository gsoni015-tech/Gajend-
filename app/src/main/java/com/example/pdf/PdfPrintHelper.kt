package com.example.pdf

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.widget.Toast
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream

/**
 * Helper to print PDF documents via Android's native PrintManager framework.
 * Supports Wi-Fi printers, Wi-Fi Direct (printer hotspot), IPP, and standard
 * Android print settings (page ranges, copies, paper size, color/B&W, orientation).
 */
object PdfPrintHelper {

    fun printPdf(
        context: Context,
        file: File,
        uriString: String? = null,
        documentTitle: String? = null
    ) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
        if (printManager == null) {
            Toast.makeText(context, "Print service is not available on this device", Toast.LENGTH_SHORT).show()
            return
        }

        val printJobName = (documentTitle ?: file.nameWithoutExtension).ifBlank { "Narayani_Document" }
        val adapter = PdfPrintDocumentAdapter(context, file, uriString, printJobName)

        val printAttributes = PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
            .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
            .build()

        try {
            printManager.print(printJobName, adapter, printAttributes)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Failed to start print job: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}

class PdfPrintDocumentAdapter(
    private val context: Context,
    private val file: File,
    private val uriString: String? = null,
    private val documentName: String
) : PrintDocumentAdapter() {

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes?,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback?,
        extras: Bundle?
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback?.onLayoutCancelled()
            return
        }

        val sanitizedName = if (documentName.endsWith(".pdf", ignoreCase = true)) {
            documentName
        } else {
            "$documentName.pdf"
        }

        val info = PrintDocumentInfo.Builder(sanitizedName)
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
            .build()

        callback?.onLayoutFinished(info, newAttributes != oldAttributes)
    }

    override fun onWrite(
        pages: Array<out PageRange>?,
        destination: ParcelFileDescriptor?,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback?
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback?.onWriteCancelled()
            return
        }

        var input: InputStream? = null
        var output: FileOutputStream? = null

        try {
            input = if (file.exists() && file.canRead()) {
                FileInputStream(file)
            } else if (!uriString.isNullOrBlank()) {
                context.contentResolver.openInputStream(Uri.parse(uriString))
            } else {
                null
            }

            if (input == null || destination == null) {
                callback?.onWriteFailed("Cannot access document stream")
                return
            }

            output = FileOutputStream(destination.fileDescriptor)
            val buffer = ByteArray(16384)
            var bytesRead: Int

            while (input.read(buffer).also { bytesRead = it } >= 0) {
                if (cancellationSignal?.isCanceled == true) {
                    callback?.onWriteCancelled()
                    return
                }
                output.write(buffer, 0, bytesRead)
            }

            callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (e: Exception) {
            e.printStackTrace()
            callback?.onWriteFailed(e.message)
        } finally {
            try { input?.close() } catch (e: Exception) {}
            try { output?.close() } catch (e: Exception) {}
        }
    }
}
