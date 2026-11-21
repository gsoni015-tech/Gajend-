package com.example.data.cloud

import android.app.PendingIntent
import android.content.Context
import android.util.Log
import com.example.data.cache.PdfFastCacheManager
import com.example.data.local.DocumentType
import com.example.data.local.PdfEntity
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class GoogleDriveRepository(
    context: Context,
    private val auth: FirebaseAuth = Firebase.auth,
    private val httpClient: OkHttpClient = defaultDriveHttpClient()
) {
    companion object {
        private const val TAG = "GoogleDriveRepo"
        private const val DRIVE_FILES_BASE_URL = "https://www.googleapis.com/drive/v3/files"
        private const val DRIVE_UPLOAD_BASE_URL = "https://www.googleapis.com/upload/drive/v3/files"
        private const val DRIVE_APP_FOLDER_NAME = "Narayani PDF Reader"
        private const val FOLDER_MIME_TYPE = "application/vnd.google-apps.folder"
        private const val POLL_INTERVAL_MS = 20_000L

        private fun defaultDriveHttpClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(25, TimeUnit.SECONDS)
                .readTimeout(90, TimeUnit.SECONDS)
                .writeTimeout(120, TimeUnit.SECONDS)
                .build()
        }

        fun parseDriveIsoTimestamp(iso: String?): Long {
            if (iso.isNullOrBlank()) return 0L
            val patterns = listOf(
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                "yyyy-MM-dd'T'HH:mm:ss'Z'"
            )
            for (pattern in patterns) {
                try {
                    val sdf = SimpleDateFormat(pattern, Locale.US).apply {
                        timeZone = TimeZone.getTimeZone("UTC")
                    }
                    sdf.parse(iso)?.time?.let { return it }
                } catch (_: Exception) {}
            }
            return 0L
        }

        fun inferDocumentTypeFromTitle(title: String): DocumentType {
            val lower = title.lowercase(Locale.US)
            return when {
                lower.endsWith(".pdf") -> DocumentType.PDF
                lower.endsWith(".doc") || lower.endsWith(".docx") -> DocumentType.WORD
                lower.endsWith(".xls") || lower.endsWith(".xlsx") || lower.endsWith(".csv") -> DocumentType.EXCEL
                else -> DocumentType.PDF
            }
        }

        fun mimeTypeForTitle(title: String): String {
            val lower = title.lowercase(Locale.US)
            return when {
                lower.endsWith(".pdf") -> "application/pdf"
                lower.endsWith(".docx") -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                lower.endsWith(".doc") -> "application/msword"
                lower.endsWith(".xlsx") -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                lower.endsWith(".xls") -> "application/vnd.ms-excel"
                lower.endsWith(".csv") -> "text/csv"
                else -> "application/pdf"
            }
        }
    }

    private val appContext = context.applicationContext
    private val driveSyncMutex = Mutex()
    private val docsStateLock = Any()

    private var currentDocuments: List<CloudDocument> = emptyList()
    private var hasCompletedInitialDriveFetch = false
    private var lastKnownRemoteDocIds: Set<String> = emptySet()

    private val _snapshotUpdates = MutableSharedFlow<CloudSnapshotBatch>(extraBufferCapacity = 32)

    private val _pendingConsentIntent = MutableStateFlow<PendingIntent?>(null)
    val pendingConsentIntent: StateFlow<PendingIntent?> = _pendingConsentIntent.asStateFlow()

    private val _needsDrivePermission = MutableStateFlow(false)
    val needsDrivePermission: StateFlow<Boolean> = _needsDrivePermission.asStateFlow()

    private fun requireUserId(): String {
        return auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("User must be signed in with Google before accessing Google Drive.")
    }

    private fun registryPrefs(uid: String) =
        appContext.getSharedPreferences("drive_sync_registry_$uid", Context.MODE_PRIVATE)

    fun getTrackedLocalPath(uid: String, docId: String): String? {
        return registryPrefs(uid).getString("doc_to_path_$docId", null)
    }

    fun getTrackedDocId(uid: String, localPath: String): String? {
        return registryPrefs(uid).getString("path_to_doc_$localPath", null)
    }

    fun getTrackedDriveFileId(uid: String, docId: String): String? {
        return registryPrefs(uid).getString("doc_to_drive_id_$docId", null)
    }

    fun getTrackedDocIds(uid: String): Set<String> {
        return registryPrefs(uid).getStringSet("synced_doc_ids", emptySet())?.toSet() ?: emptySet()
    }

    private fun recordSyncedMapping(
        uid: String,
        docId: String,
        localPath: String,
        driveFileId: String
    ) {
        val prefs = registryPrefs(uid)
        val updated = (prefs.getStringSet("synced_doc_ids", emptySet()) ?: emptySet()).toMutableSet()
        updated.add(docId)
        val editor = prefs.edit().putStringSet("synced_doc_ids", updated)
        if (localPath.isNotBlank()) {
            editor.putString("doc_to_path_$docId", localPath)
            editor.putString("path_to_doc_$localPath", docId)
        }
        if (driveFileId.isNotBlank()) {
            editor.putString("doc_to_drive_id_$docId", driveFileId)
        }
        editor.apply()
    }

    private fun removeSyncedMapping(uid: String, docId: String, localPath: String? = null) {
        val prefs = registryPrefs(uid)
        val updated = (prefs.getStringSet("synced_doc_ids", emptySet()) ?: emptySet()).toMutableSet()
        updated.remove(docId)
        val existingPath = localPath ?: prefs.getString("doc_to_path_$docId", null)
        val editor = prefs.edit()
            .putStringSet("synced_doc_ids", updated)
            .remove("doc_to_path_$docId")
            .remove("doc_to_drive_id_$docId")
        if (!existingPath.isNullOrBlank()) {
            editor.remove("path_to_doc_$existingPath")
        }
        editor.apply()
    }

    fun markDrivePermissionGranted() {
        _pendingConsentIntent.value = null
        _needsDrivePermission.value = false
    }

    fun clearPendingConsentIntentOnly() {
        _pendingConsentIntent.value = null
    }

    private suspend fun requireDriveAccessToken(uid: String, forceRefresh: Boolean = false): String {
        return when (val tokenRes = GoogleDriveAuthHelper.getOrRequestAccessToken(appContext, uid, forceRefresh)) {
            is DriveTokenResult.Authorized -> {
                _pendingConsentIntent.value = null
                _needsDrivePermission.value = false
                tokenRes.accessToken
            }
            is DriveTokenResult.NeedsConsent -> {
                _pendingConsentIntent.value = tokenRes.pendingIntent
                _needsDrivePermission.value = true
                throw DriveConsentRequiredException(tokenRes.pendingIntent)
            }
            is DriveTokenResult.Error -> {
                throw IllegalStateException(tokenRes.message)
            }
        }
    }

    private suspend fun <T> withDriveApiToken(
        uid: String,
        block: (accessToken: String) -> T
    ): T {
        val firstToken = requireDriveAccessToken(uid, forceRefresh = false)
        return try {
            block(firstToken)
        } catch (apiEx: DriveApiException) {
            if (apiEx.statusCode == 401) {
                val refreshedToken = requireDriveAccessToken(uid, forceRefresh = true)
                block(refreshedToken)
            } else {
                throw apiEx
            }
        }
    }

    private fun ensureDriveFolderId(uid: String, accessToken: String): String {
        val prefs = registryPrefs(uid)
        val cachedFolderId = prefs.getString("drive_folder_id", null)
        if (!cachedFolderId.isNullOrBlank()) {
            val checkReq = Request.Builder()
                .url("$DRIVE_FILES_BASE_URL/$cachedFolderId?fields=id,trashed")
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()
            httpClient.newCall(checkReq).execute().use { resp ->
                if (resp.code == 401) {
                    throw DriveApiException(401, "Google Drive access token expired.")
                }
                if (resp.isSuccessful) {
                    val bodyStr = resp.body?.string().orEmpty()
                    val json = JSONObject(bodyStr)
                    if (!json.optBoolean("trashed", false)) {
                        return cachedFolderId
                    }
                }
            }
        }

        val query = "name = '$DRIVE_APP_FOLDER_NAME' and mimeType = '$FOLDER_MIME_TYPE' and trashed = false"
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val searchUrl = "$DRIVE_FILES_BASE_URL?q=$encodedQuery&spaces=drive&pageSize=10&fields=files(id,name)"
        val searchReq = Request.Builder()
            .url(searchUrl)
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()

        httpClient.newCall(searchReq).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (resp.code == 401) {
                throw DriveApiException(401, "Google Drive access token expired.")
            }
            if (!resp.isSuccessful) {
                throw DriveApiException(resp.code, parseDriveErrorMessage(raw, resp.code))
            }
            val files = JSONObject(raw).optJSONArray("files")
            if (files != null && files.length() > 0) {
                val existingId = files.getJSONObject(0).getString("id")
                prefs.edit().putString("drive_folder_id", existingId).apply()
                return existingId
            }
        }

        val createJson = JSONObject().apply {
            put("name", DRIVE_APP_FOLDER_NAME)
            put("mimeType", FOLDER_MIME_TYPE)
        }
        val createReq = Request.Builder()
            .url("$DRIVE_FILES_BASE_URL?fields=id")
            .header("Authorization", "Bearer $accessToken")
            .post(createJson.toString().toRequestBody("application/json; charset=UTF-8".toMediaType()))
            .build()

        httpClient.newCall(createReq).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (resp.code == 401) {
                throw DriveApiException(401, "Google Drive access token expired.")
            }
            if (!resp.isSuccessful) {
                throw DriveApiException(resp.code, parseDriveErrorMessage(raw, resp.code))
            }
            val newFolderId = JSONObject(raw).getString("id")
            prefs.edit().putString("drive_folder_id", newFolderId).apply()
            return newFolderId
        }
    }

    suspend fun refreshFromDrive(userId: String): Result<List<CloudDocument>> = withContext(Dispatchers.IO) {
        val uid = userId.trim()
        if (uid.isBlank() || auth.currentUser?.uid != uid) {
            return@withContext Result.success(emptyList())
        }
        try {
            val batch = driveSyncMutex.withLock {
                val docs = withDriveApiToken(uid) { accessToken ->
                    val folderId = ensureDriveFolderId(uid, accessToken)
                    fetchAllFilesInFolder(uid, folderId, accessToken)
                }
                val remoteIds = docs.map { it.id }.toSet()
                val previouslyKnownIds = if (hasCompletedInitialDriveFetch) {
                    lastKnownRemoteDocIds
                } else {
                    getTrackedDocIds(uid)
                }
                val removedIds = previouslyKnownIds.filter { it !in remoteIds }

                docs.forEach { doc ->
                    val localPath = getTrackedLocalPath(uid, doc.id).orEmpty()
                    recordSyncedMapping(uid, doc.id, localPath, doc.resolvedDriveFileId)
                }

                hasCompletedInitialDriveFetch = true
                lastKnownRemoteDocIds = remoteIds
                synchronized(docsStateLock) {
                    currentDocuments = docs
                }

                CloudSnapshotBatch(
                    documents = docs,
                    removedDocIds = removedIds,
                    isFromCache = false
                )
            }
            _snapshotUpdates.tryEmit(batch)
            Result.success(batch.documents)
        } catch (ce: CancellationException) {
            throw ce
        } catch (consentEx: DriveConsentRequiredException) {
            Result.failure(consentEx)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to refresh Google Drive documents: ${e.message}")
            Result.failure(e)
        }
    }

    private fun fetchAllFilesInFolder(
        uid: String,
        folderId: String,
        accessToken: String
    ): List<CloudDocument> {
        val allDocs = mutableListOf<CloudDocument>()
        var pageToken: String? = null
        val query = "'$folderId' in parents and trashed = false and mimeType != '$FOLDER_MIME_TYPE'"
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val fields = URLEncoder.encode(
            "nextPageToken,files(id,name,mimeType,size,modifiedTime,webViewLink,appProperties)",
            "UTF-8"
        )

        do {
            val urlBuilder = StringBuilder("$DRIVE_FILES_BASE_URL?q=$encodedQuery&orderBy=modifiedTime%20desc&pageSize=200&fields=$fields")
            if (!pageToken.isNullOrBlank()) {
                urlBuilder.append("&pageToken=").append(URLEncoder.encode(pageToken, "UTF-8"))
            }
            val request = Request.Builder()
                .url(urlBuilder.toString())
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()

            httpClient.newCall(request).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (resp.code == 401) {
                    throw DriveApiException(401, "Google Drive access token expired.")
                }
                if (!resp.isSuccessful) {
                    throw DriveApiException(resp.code, parseDriveErrorMessage(raw, resp.code))
                }
                val root = JSONObject(raw)
                val filesArray = root.optJSONArray("files") ?: JSONArray()
                for (i in 0 until filesArray.length()) {
                    val fileObj = filesArray.optJSONObject(i) ?: continue
                    parseCloudDocumentFromDriveJson(uid, fileObj)?.let { allDocs.add(it) }
                }
                pageToken = root.optString("nextPageToken").takeIf { it.isNotBlank() }
            }
        } while (!pageToken.isNullOrBlank())

        return allDocs.sortedByDescending { it.updatedAtMillis }
    }

    private fun parseCloudDocumentFromDriveJson(uid: String, fileObj: JSONObject): CloudDocument? {
        val driveFileId = fileObj.optString("id").trim()
        if (driveFileId.isBlank()) return null
        val title = fileObj.optString("name", "Document.pdf").ifBlank { "Document.pdf" }
        val sizeBytes = fileObj.optString("size").toLongOrNull() ?: 0L
        val appProps = fileObj.optJSONObject("appProperties")
        val propDocId = appProps?.optString("docId")?.trim().orEmpty()
        val docId = propDocId.ifBlank {
            CloudDocumentIdentity.buildDocId(title = title, sizeBytes = sizeBytes, path = "drive://$driveFileId")
        }
        val docType = appProps?.optString("documentType")
            ?.takeIf { it.isNotBlank() }
            ?: inferDocumentTypeFromTitle(title).name
        val folderName = appProps?.optString("folderName")
            ?.takeIf { it.isNotBlank() }
            ?: "Google Drive"
        val trackedLocalPath = getTrackedLocalPath(uid, docId).orEmpty()
        val updatedAtMillis = parseDriveIsoTimestamp(fileObj.optString("modifiedTime"))
        val webViewLink = fileObj.optString("webViewLink").orEmpty()

        return CloudDocument(
            id = docId,
            userId = uid,
            title = title,
            documentType = docType,
            sizeBytes = sizeBytes,
            contentHash = docId.removePrefix("doc_"),
            storagePath = "drive://$driveFileId",
            syncState = CloudSyncState.SYNCED.name,
            localPath = trackedLocalPath,
            folderName = folderName,
            driveFileId = driveFileId,
            webViewLink = webViewLink,
            updatedAtMillis = updatedAtMillis
        )
    }

    fun observeCloudSnapshotBatches(userId: String): Flow<CloudSnapshotBatch> = channelFlow {
        val uid = userId.trim()
        if (uid.isBlank() || auth.currentUser?.uid != uid) {
            send(CloudSnapshotBatch(emptyList(), emptyList(), isFromCache = true))
            return@channelFlow
        }

        val collectorJob = launch {
            _snapshotUpdates.collect { batch ->
                send(batch)
            }
        }

        val initialResult = refreshFromDrive(uid)
        initialResult.onFailure { err ->
            if (err is DriveConsentRequiredException) {
                send(CloudSnapshotBatch(emptyList(), emptyList(), isFromCache = false))
            } else {
                collectorJob.cancel()
                throw err
            }
        }

        while (true) {
            delay(POLL_INTERVAL_MS)
            if (auth.currentUser?.uid != uid) break
            if (!_needsDrivePermission.value) {
                refreshFromDrive(uid)
            }
        }
    }

    fun observeCloudDocuments(userId: String): Flow<List<CloudDocument>> =
        observeCloudSnapshotBatches(userId).map { it.documents }

    data class UploadResult(
        val document: CloudDocument,
        val storageUnavailable: Boolean = false
    )

    suspend fun uploadDocument(
        entity: PdfEntity,
        alreadySyncedDocIds: Set<String> = emptySet()
    ): Result<UploadResult> = withContext(Dispatchers.IO) {
        try {
            val uid = requireUserId()
            val docId = CloudDocumentIdentity.buildDocId(entity)

            val existingLocal = synchronized(docsStateLock) {
                currentDocuments.firstOrNull {
                    it.id == docId || (it.title.equals(entity.title, ignoreCase = true) && it.sizeBytes == entity.sizeBytes && entity.sizeBytes > 0L)
                }
            }

            if (existingLocal != null || (docId in alreadySyncedDocIds && getTrackedDocId(uid, entity.path) == docId && !getTrackedDriveFileId(uid, docId).isNullOrBlank())) {
                PdfFastCacheManager.warmCacheAsync(appContext, entity)
                val driveId = existingLocal?.resolvedDriveFileId
                    ?: getTrackedDriveFileId(uid, docId).orEmpty()
                if (driveId.isNotBlank()) {
                    recordSyncedMapping(uid, docId, entity.path, driveId)
                }
                val cachedDoc = existingLocal?.copy(localPath = entity.path) ?: CloudDocument(
                    id = docId,
                    userId = uid,
                    title = entity.title,
                    documentType = entity.documentType.name,
                    sizeBytes = entity.sizeBytes,
                    contentHash = docId.removePrefix("doc_"),
                    storagePath = if (driveId.isNotBlank()) "drive://$driveId" else "",
                    syncState = CloudSyncState.SYNCED.name,
                    localPath = entity.path,
                    folderName = entity.folderName,
                    driveFileId = driveId
                )
                return@withContext Result.success(UploadResult(cachedDoc))
            }

            val cachedOrLocalFile = PdfFastCacheManager.getOrWarmFastFile(appContext, entity)
            if (cachedOrLocalFile == null || !cachedOrLocalFile.exists() || cachedOrLocalFile.length() <= 0L) {
                return@withContext Result.failure(IllegalStateException("Local file unavailable for Drive upload: ${entity.title}"))
            }

            val uploadedDoc = driveSyncMutex.withLock {
                withDriveApiToken(uid) { accessToken ->
                    val folderId = ensureDriveFolderId(uid, accessToken)

                    // Check Drive folder list once before uploading if we haven't fetched yet
                    if (!hasCompletedInitialDriveFetch) {
                        val remoteDocs = fetchAllFilesInFolder(uid, folderId, accessToken)
                        lastKnownRemoteDocIds = remoteDocs.map { it.id }.toSet()
                        hasCompletedInitialDriveFetch = true
                        synchronized(docsStateLock) {
                            currentDocuments = remoteDocs
                        }
                        val remoteMatch = remoteDocs.firstOrNull {
                            it.id == docId || (it.title.equals(entity.title, ignoreCase = true) && it.sizeBytes == entity.sizeBytes && entity.sizeBytes > 0L)
                        }
                        if (remoteMatch != null) {
                            recordSyncedMapping(uid, docId, entity.path, remoteMatch.resolvedDriveFileId)
                            return@withDriveApiToken remoteMatch.copy(localPath = entity.path)
                        }
                    }

                    uploadFileMultipart(
                        uid = uid,
                        folderId = folderId,
                        accessToken = accessToken,
                        docId = docId,
                        entity = entity,
                        file = cachedOrLocalFile
                    )
                }
            }

            recordSyncedMapping(uid, docId, entity.path, uploadedDoc.resolvedDriveFileId)
            val updatedDocs = synchronized(docsStateLock) {
                val filtered = currentDocuments.filterNot { it.id == uploadedDoc.id }
                val next = listOf(uploadedDoc) + filtered
                currentDocuments = next
                lastKnownRemoteDocIds = next.map { it.id }.toSet()
                next
            }
            _snapshotUpdates.tryEmit(
                CloudSnapshotBatch(
                    documents = updatedDocs,
                    removedDocIds = emptyList(),
                    isFromCache = false
                )
            )
            Result.success(UploadResult(uploadedDoc, storageUnavailable = false))
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Log.w(TAG, "Drive upload failed for ${entity.title}: ${e.message}")
            Result.failure(e)
        }
    }

    private fun uploadFileMultipart(
        uid: String,
        folderId: String,
        accessToken: String,
        docId: String,
        entity: PdfEntity,
        file: File
    ): CloudDocument {
        val safeTitle = entity.title.ifBlank { file.name }.take(255)
        val mimeType = mimeTypeForTitle(safeTitle)
        val appProperties = JSONObject().apply {
            put("docId", CloudDocumentIdentity.truncateUtf8ForAppProperty(docId, 80))
            put("documentType", CloudDocumentIdentity.truncateUtf8ForAppProperty(entity.documentType.name, 20))
            if (entity.folderName.isNotBlank()) {
                put("folderName", CloudDocumentIdentity.truncateUtf8ForAppProperty(entity.folderName, 80))
            }
        }
        val metadataJson = JSONObject().apply {
            put("name", safeTitle)
            put("parents", JSONArray().put(folderId))
            put("appProperties", appProperties)
        }

        val multipartBody = MultipartBody.Builder()
            .setType("multipart/related".toMediaType())
            .addPart(
                Headers.headersOf("Content-Type", "application/json; charset=UTF-8"),
                metadataJson.toString().toRequestBody("application/json; charset=UTF-8".toMediaType())
            )
            .addPart(
                Headers.headersOf("Content-Type", mimeType),
                file.asRequestBody(mimeType.toMediaType())
            )
            .build()

        val fields = URLEncoder.encode("id,name,mimeType,size,modifiedTime,webViewLink,appProperties", "UTF-8")
        val request = Request.Builder()
            .url("$DRIVE_UPLOAD_BASE_URL?uploadType=multipart&fields=$fields")
            .header("Authorization", "Bearer $accessToken")
            .post(multipartBody)
            .build()

        httpClient.newCall(request).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (resp.code == 401) {
                throw DriveApiException(401, "Google Drive access token expired.")
            }
            if (!resp.isSuccessful) {
                throw DriveApiException(resp.code, parseDriveErrorMessage(raw, resp.code))
            }
            val parsed = parseCloudDocumentFromDriveJson(uid, JSONObject(raw))
                ?: throw IllegalStateException("Google Drive returned invalid file metadata after upload.")
            return parsed.copy(
                localPath = entity.path,
                folderName = entity.folderName.ifBlank { parsed.folderName },
                sizeBytes = if (parsed.sizeBytes > 0L) parsed.sizeBytes else file.length(),
                updatedAtMillis = if (parsed.updatedAtMillis > 0L) parsed.updatedAtMillis else System.currentTimeMillis()
            )
        }
    }

    suspend fun deleteFromCloud(
        docId: String,
        storagePath: String = "",
        localPath: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val uid = requireUserId()
            require(docId.isNotBlank()) { "Document ID must not be blank for delete" }

            val trackedPath = localPath ?: getTrackedLocalPath(uid, docId)
            PdfFastCacheManager.evict(appContext, cloudId = docId, localPath = trackedPath)

            val resolvedDriveId = storagePath.removePrefix("drive://").takeIf {
                it.isNotBlank() && !it.startsWith("users/")
            } ?: getTrackedDriveFileId(uid, docId)
                ?: synchronized(docsStateLock) {
                    currentDocuments.firstOrNull { it.id == docId }?.resolvedDriveFileId
                }

            if (!resolvedDriveId.isNullOrBlank()) {
                driveSyncMutex.withLock {
                    withDriveApiToken(uid) { accessToken ->
                        val request = Request.Builder()
                            .url("$DRIVE_FILES_BASE_URL/$resolvedDriveId")
                            .header("Authorization", "Bearer $accessToken")
                            .delete()
                            .build()
                        httpClient.newCall(request).execute().use { resp ->
                            if (resp.code == 401) {
                                throw DriveApiException(401, "Google Drive access token expired.")
                            }
                            if (!resp.isSuccessful && resp.code != 404) {
                                val raw = resp.body?.string().orEmpty()
                                throw DriveApiException(resp.code, parseDriveErrorMessage(raw, resp.code))
                            }
                        }
                    }
                }
            }

            removeSyncedMapping(uid, docId, trackedPath)
            val updatedDocs = synchronized(docsStateLock) {
                val next = currentDocuments.filterNot { it.id == docId }
                currentDocuments = next
                lastKnownRemoteDocIds = next.map { it.id }.toSet()
                next
            }
            _snapshotUpdates.tryEmit(
                CloudSnapshotBatch(
                    documents = updatedDocs,
                    removedDocIds = emptyList(),
                    isFromCache = false
                )
            )
            Result.success(Unit)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Log.w(TAG, "Failed to delete from Google Drive: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun deleteByLocalDocsFromCloud(deletedDocs: List<DeletedLocalDoc>): List<String> =
        withContext(Dispatchers.IO) {
            val uid = auth.currentUser?.uid?.takeIf { it.isNotBlank() } ?: return@withContext emptyList()
            val deletedCloudIds = mutableListOf<String>()
            for (doc in deletedDocs) {
                val trackedDocId = getTrackedDocId(uid, doc.path)
                val resolvedId = trackedDocId ?: if (doc.title.isNotBlank() && doc.sizeBytes > 0L) {
                    CloudDocumentIdentity.buildDocId(title = doc.title, sizeBytes = doc.sizeBytes, path = doc.path)
                } else {
                    null
                } ?: continue
                PdfFastCacheManager.evict(appContext, cloudId = resolvedId, localPath = doc.path)
                val trackedDriveId = getTrackedDriveFileId(uid, resolvedId)
                    ?: synchronized(docsStateLock) {
                        currentDocuments.firstOrNull { it.id == resolvedId }?.resolvedDriveFileId
                    }.orEmpty()

                val res = deleteFromCloud(
                    docId = resolvedId,
                    storagePath = if (trackedDriveId.isNotBlank()) "drive://$trackedDriveId" else "",
                    localPath = doc.path
                )
                if (res.isSuccess) {
                    deletedCloudIds.add(resolvedId)
                }
            }
            deletedCloudIds
        }

    fun clearLocalTrackingForDoc(userId: String, docId: String): String? {
        val uid = userId.trim()
        if (uid.isBlank()) return null
        val trackedPath = getTrackedLocalPath(uid, docId)
        removeSyncedMapping(uid, docId, trackedPath)
        return trackedPath
    }

    suspend fun downloadToFastCache(doc: CloudDocument): Result<File> = withContext(Dispatchers.IO) {
        try {
            val existingCached = PdfFastCacheManager.getCachedFileByCloudId(appContext, doc.id, doc.title)
            if (existingCached != null && existingCached.exists() && existingCached.length() > 0L) {
                PdfFastCacheManager.registerCloudCachedFile(appContext, doc.id, existingCached, doc.localPath)
                return@withContext Result.success(existingCached)
            }

            if (doc.localPath.isNotBlank()) {
                val localFile = File(doc.localPath)
                if (localFile.exists() && localFile.canRead() && localFile.length() > 0L) {
                    val tempTarget = PdfFastCacheManager.createCacheTargetFile(appContext, doc.id, doc.title)
                    localFile.copyTo(tempTarget, overwrite = true)
                    PdfFastCacheManager.registerCloudCachedFile(appContext, doc.id, tempTarget, doc.localPath)
                    return@withContext Result.success(tempTarget)
                }
            }

            val uid = requireUserId()
            val driveId = doc.resolvedDriveFileId.ifBlank {
                getTrackedDriveFileId(uid, doc.id).orEmpty()
            }
            if (driveId.isBlank()) {
                return@withContext Result.failure(IllegalStateException("Google Drive file ID is missing for ${doc.title}."))
            }

            val targetFile = PdfFastCacheManager.createCacheTargetFile(appContext, doc.id, doc.title)
            val tempFile = File(targetFile.parentFile, "${targetFile.name}.download")

            withDriveApiToken(uid) { accessToken ->
                val request = Request.Builder()
                    .url("$DRIVE_FILES_BASE_URL/$driveId?alt=media")
                    .header("Authorization", "Bearer $accessToken")
                    .get()
                    .build()

                httpClient.newCall(request).execute().use { resp ->
                    if (resp.code == 401) {
                        throw DriveApiException(401, "Google Drive access token expired.")
                    }
                    if (!resp.isSuccessful) {
                        val raw = resp.body?.string().orEmpty()
                        throw DriveApiException(resp.code, parseDriveErrorMessage(raw, resp.code))
                    }
                    val body = resp.body ?: throw IllegalStateException("Empty Google Drive download response.")
                    body.byteStream().use { input ->
                        FileOutputStream(tempFile).use { output ->
                            input.copyTo(output, bufferSize = 65_536)
                            output.flush()
                        }
                    }
                }
            }

            if (tempFile.exists() && tempFile.length() > 0L) {
                if (targetFile.exists()) targetFile.delete()
                if (!tempFile.renameTo(targetFile)) {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }
                PdfFastCacheManager.registerCloudCachedFile(appContext, doc.id, targetFile, doc.localPath)
                return@withContext Result.success(targetFile)
            }
            Result.failure(IllegalStateException("Downloaded Google Drive file was empty."))
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseDriveErrorMessage(rawBody: String, statusCode: Int): String {
        return try {
            val errObj = JSONObject(rawBody).optJSONObject("error")
            val msg = errObj?.optString("message").orEmpty()
            msg.ifBlank { "Google Drive request failed (HTTP $statusCode)." }
        } catch (_: Exception) {
            "Google Drive request failed (HTTP $statusCode)."
        }
    }
}
