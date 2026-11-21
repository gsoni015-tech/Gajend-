package com.example.data.cloud

import android.app.PendingIntent
import androidx.annotation.Keep
import com.example.data.local.DocumentType
import com.example.data.local.PdfEntity
import java.security.MessageDigest

enum class CloudSyncState {
    SYNCED,
    METADATA_ONLY,
    ERROR;

    companion object {
        fun fromRaw(raw: String?): CloudSyncState {
            return entries.firstOrNull { it.name == raw } ?: METADATA_ONLY
        }
    }
}

@Keep
data class CloudDocument(
    val id: String = "",
    val userId: String = "",
    val title: String = "",
    val documentType: String = DocumentType.PDF.name,
    val sizeBytes: Long = 0L,
    val contentHash: String = "",
    val storagePath: String = "",
    val syncState: String = CloudSyncState.SYNCED.name,
    val localPath: String = "",
    val folderName: String = "",
    val driveFileId: String = "",
    val webViewLink: String = "",
    val updatedAtMillis: Long = 0L
) {
    val resolvedSyncState: CloudSyncState
        get() = CloudSyncState.fromRaw(syncState)

    val resolvedDocumentType: DocumentType
        get() = DocumentType.entries.firstOrNull { it.name == documentType } ?: DocumentType.OTHER

    val resolvedDriveFileId: String
        get() = driveFileId.ifBlank { storagePath.removePrefix("drive://") }
}

sealed interface CloudUiState<out T> {
    data object Loading : CloudUiState<Nothing>
    data class Success<T>(val data: T) : CloudUiState<T>
    data class Error(val message: String) : CloudUiState<Nothing>
}

data class DeletedLocalDoc(
    val path: String,
    val title: String,
    val sizeBytes: Long
)

data class CloudSnapshotBatch(
    val documents: List<CloudDocument>,
    val removedDocIds: List<String>,
    val isFromCache: Boolean
)

sealed interface DriveTokenResult {
    data class Authorized(val accessToken: String) : DriveTokenResult
    data class NeedsConsent(val pendingIntent: PendingIntent) : DriveTokenResult
    data class Error(val message: String) : DriveTokenResult
}

class DriveConsentRequiredException(
    val pendingIntent: PendingIntent,
    message: String = "Google Drive permission is required to sync your PDFs."
) : Exception(message)

class DriveApiException(
    val statusCode: Int,
    message: String
) : Exception(message)

object CloudDocumentIdentity {
    fun buildDocId(entity: PdfEntity): String {
        return buildDocId(title = entity.title, sizeBytes = entity.sizeBytes, path = entity.path)
    }

    fun buildDocId(title: String, sizeBytes: Long, path: String = ""): String {
        val key = if (title.isNotBlank() && sizeBytes > 0L) {
            "${title.trim().lowercase()}|$sizeBytes"
        } else {
            "${title.trim().lowercase()}|$sizeBytes|$path"
        }
        return "doc_${sha256Hex(key).take(40)}"
    }

    fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Google Drive `appProperties` restricts (key + value) to 124 UTF-8 bytes per entry.
     * Truncates values safely on character boundaries so multi-byte Unicode names never break uploads.
     */
    fun truncateUtf8ForAppProperty(value: String, maxBytes: Int = 90): String {
        if (value.isEmpty() || maxBytes <= 0) return ""
        val sb = StringBuilder()
        var currentBytes = 0
        for (ch in value) {
            val charBytes = ch.toString().toByteArray(Charsets.UTF_8).size
            if (currentBytes + charBytes > maxBytes) break
            sb.append(ch)
            currentBytes += charBytes
        }
        return sb.toString()
    }
}
