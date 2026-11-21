package com.example.ui.viewmodel

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.cache.CacheStats
import com.example.data.cache.PdfFastCacheManager
import com.example.data.cloud.CloudDocument
import com.example.data.cloud.CloudDocumentIdentity
import com.example.data.cloud.CloudSnapshotBatch
import com.example.data.cloud.CloudSyncState
import com.example.data.cloud.CloudUiState
import com.example.data.cloud.DeletedLocalDoc
import com.example.data.cloud.DriveConsentRequiredException
import com.example.data.cloud.DriveTokenResult
import com.example.data.cloud.GoogleDriveAuthHelper
import com.example.data.cloud.GoogleDriveRepository
import com.example.data.local.DocumentType
import com.example.data.local.PdfEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.Collections

class CloudSyncViewModel(
    private val driveRepository: GoogleDriveRepository,
    context: Context,
    private val currentUserId: String
) : ViewModel() {

    private val appContext = context.applicationContext
    private val uploadMutex = Mutex()
    private val inFlightDocIds = Collections.synchronizedSet(mutableSetOf<String>())
    private val failedDocIds = Collections.synchronizedSet(mutableSetOf<String>())
    private val recentlyLocallyDeletedDocIds = Collections.synchronizedSet(mutableSetOf<String>())

    private val _cloudDocsState = MutableStateFlow<CloudUiState<List<CloudDocument>>>(CloudUiState.Loading)
    val cloudDocsState: StateFlow<CloudUiState<List<CloudDocument>>> = _cloudDocsState.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _activeSyncTitle = MutableStateFlow<String?>(null)
    val activeSyncTitle: StateFlow<String?> = _activeSyncTitle.asStateFlow()

    val pendingConsentIntent: StateFlow<PendingIntent?> = driveRepository.pendingConsentIntent
    val storageSetupNeeded: StateFlow<Boolean> = driveRepository.needsDrivePermission

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    private val _openingDocId = MutableStateFlow<String?>(null)
    val openingDocId: StateFlow<String?> = _openingDocId.asStateFlow()

    private val _remoteDeletedLocalPaths = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val remoteDeletedLocalPaths: SharedFlow<String> = _remoteDeletedLocalPaths.asSharedFlow()

    val cacheStats: StateFlow<CacheStats> = PdfFastCacheManager.cacheStats

    private var latestCloudDocIds: Set<String> = emptySet()

    init {
        PdfFastCacheManager.refreshStats(appContext)
        observeDriveDocuments()
    }

    private fun observeDriveDocuments() {
        viewModelScope.launch {
            driveRepository.observeCloudSnapshotBatches(currentUserId)
                .catch { error ->
                    if (error is CancellationException) {
                        throw error
                    }
                    val msg = error.localizedMessage
                        ?: "Failed to load Google Drive documents."
                    _cloudDocsState.value = CloudUiState.Error(msg)
                }
                .collect { batch: CloudSnapshotBatch ->
                    latestCloudDocIds = batch.documents.map { it.id }.toSet()
                    _cloudDocsState.value = CloudUiState.Success(batch.documents)

                    if (batch.removedDocIds.isNotEmpty() && !batch.isFromCache) {
                        handleRemoteAccountDeletions(batch.removedDocIds)
                    }
                }
        }
    }

    private fun handleRemoteAccountDeletions(removedIds: List<String>) {
        for (docId in removedIds) {
            if (recentlyLocallyDeletedDocIds.remove(docId)) {
                continue
            }
            PdfFastCacheManager.evictByCloudId(appContext, docId)
            val localPath = driveRepository.clearLocalTrackingForDoc(currentUserId, docId)
            if (!localPath.isNullOrBlank()) {
                _remoteDeletedLocalPaths.tryEmit(localPath)
            }
        }
    }

    fun requestDrivePermission(allDocuments: List<PdfEntity>) {
        viewModelScope.launch {
            when (val res = GoogleDriveAuthHelper.getOrRequestAccessToken(appContext, currentUserId, forceRefresh = false)) {
                is DriveTokenResult.Authorized -> {
                    driveRepository.markDrivePermissionGranted()
                    refreshAndSync(allDocuments)
                }
                is DriveTokenResult.NeedsConsent -> {
                    // Launch via UI consent observer
                    driveRepository.refreshFromDrive(currentUserId)
                }
                is DriveTokenResult.Error -> {
                    _actionMessage.value = res.message
                }
            }
        }
    }

    fun onDriveConsentResult(
        resultData: Intent?,
        allDocuments: List<PdfEntity>
    ) {
        driveRepository.clearPendingConsentIntentOnly()
        val tokenResult = GoogleDriveAuthHelper.handleAuthorizationIntentResult(
            context = appContext,
            userId = currentUserId,
            data = resultData
        )
        when (tokenResult) {
            is DriveTokenResult.Authorized -> {
                driveRepository.markDrivePermissionGranted()
                failedDocIds.clear()
                _actionMessage.value = "Google Drive connected! Syncing PDFs..."
                refreshAndSync(allDocuments)
            }
            is DriveTokenResult.NeedsConsent -> {
                // Keep needsDrivePermission true so user can retry via banner
            }
            is DriveTokenResult.Error -> {
                _actionMessage.value = tokenResult.message
            }
        }
    }

    fun onDriveConsentDismissed() {
        driveRepository.clearPendingConsentIntentOnly()
    }

    fun refreshDriveInBackground() {
        if (storageSetupNeeded.value || _isSyncing.value) return
        viewModelScope.launch(Dispatchers.IO) {
            driveRepository.refreshFromDrive(currentUserId)
        }
    }

    private fun refreshAndSync(allDocuments: List<PdfEntity>) {
        viewModelScope.launch(Dispatchers.IO) {
            driveRepository.refreshFromDrive(currentUserId)
            autoSyncLocalPdfs(allDocuments)
        }
    }

    fun autoSyncLocalPdfs(allDocuments: List<PdfEntity>) {
        val pdfs = allDocuments.filter { it.documentType == DocumentType.PDF }
        if (pdfs.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            uploadMutex.withLock {
                val trackedIds = driveRepository.getTrackedDocIds(currentUserId)
                val knownSyncedIds = latestCloudDocIds + trackedIds

                val pendingUploads = pdfs.filter { entity ->
                    val docId = CloudDocumentIdentity.buildDocId(entity)
                    docId !in knownSyncedIds &&
                        docId !in inFlightDocIds &&
                        docId !in failedDocIds
                }

                if (pendingUploads.isEmpty()) return@withLock

                _isSyncing.value = true
                try {
                    for (entity in pendingUploads) {
                        val docId = CloudDocumentIdentity.buildDocId(entity)
                        if (docId in latestCloudDocIds && driveRepository.getTrackedDocId(currentUserId, entity.path) == docId) {
                            continue
                        }
                        inFlightDocIds.add(docId)
                        _activeSyncTitle.value = entity.title
                        val result = driveRepository.uploadDocument(entity, latestCloudDocIds)
                        inFlightDocIds.remove(docId)

                        result.onFailure { err ->
                            if (err is DriveConsentRequiredException) {
                                // Stop waiting for consent rather than marking uploads as failed
                                break
                            }
                            failedDocIds.add(docId)
                        }
                    }
                } finally {
                    _activeSyncTitle.value = null
                    _isSyncing.value = false
                    PdfFastCacheManager.refreshStats(appContext)
                }
            }
        }
    }

    fun syncAllNow(allDocuments: List<PdfEntity>) {
        failedDocIds.clear()
        val pdfs = allDocuments.filter { it.documentType == DocumentType.PDF }
        viewModelScope.launch(Dispatchers.IO) {
            val refreshRes = driveRepository.refreshFromDrive(currentUserId)
            refreshRes.onFailure { err ->
                if (err is DriveConsentRequiredException) {
                    return@launch
                }
            }
            if (pdfs.isEmpty()) {
                _actionMessage.value = "Google Drive refreshed. No local PDFs found to upload."
                return@launch
            }
            uploadMutex.withLock {
                _isSyncing.value = true
                var uploadedCount = 0
                try {
                    for (entity in pdfs) {
                        val docId = CloudDocumentIdentity.buildDocId(entity)
                        inFlightDocIds.add(docId)
                        _activeSyncTitle.value = entity.title
                        val result = driveRepository.uploadDocument(entity, latestCloudDocIds)
                        inFlightDocIds.remove(docId)
                        result.onSuccess {
                            uploadedCount++
                        }.onFailure { err ->
                            if (err is DriveConsentRequiredException) {
                                return@withLock
                            }
                            failedDocIds.add(docId)
                        }
                    }
                    _actionMessage.value = if (uploadedCount > 0) {
                        "Synced $uploadedCount PDF(s) to Google Drive & warmed fast cache."
                    } else {
                        "Google Drive sync completed."
                    }
                } finally {
                    _activeSyncTitle.value = null
                    _isSyncing.value = false
                    PdfFastCacheManager.refreshStats(appContext)
                }
            }
        }
    }

    fun handleLocalDeletions(deletedDocs: List<DeletedLocalDoc>) {
        if (deletedDocs.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            deletedDocs.forEach { doc ->
                val trackedId = driveRepository.getTrackedDocId(currentUserId, doc.path)
                if (!trackedId.isNullOrBlank()) {
                    recentlyLocallyDeletedDocIds.add(trackedId)
                } else if (doc.title.isNotBlank() && doc.sizeBytes > 0L) {
                    recentlyLocallyDeletedDocIds.add(
                        CloudDocumentIdentity.buildDocId(doc.title, doc.sizeBytes, doc.path)
                    )
                }
            }
            driveRepository.deleteByLocalDocsFromCloud(deletedDocs)
            PdfFastCacheManager.refreshStats(appContext)
        }
    }

    fun deleteFromCloudAccount(
        doc: CloudDocument,
        alsoDeleteLocal: Boolean,
        onDeleteLocalPath: (String) -> Unit
    ) {
        viewModelScope.launch {
            recentlyLocallyDeletedDocIds.add(doc.id)
            val resolvedLocalPath = doc.localPath.ifBlank {
                driveRepository.getTrackedLocalPath(currentUserId, doc.id).orEmpty()
            }
            val result = driveRepository.deleteFromCloud(
                docId = doc.id,
                storagePath = doc.storagePath,
                localPath = resolvedLocalPath.ifBlank { null }
            )
            result.onSuccess {
                if (alsoDeleteLocal && resolvedLocalPath.isNotBlank()) {
                    onDeleteLocalPath(resolvedLocalPath)
                }
                _actionMessage.value = if (alsoDeleteLocal) {
                    "Deleted '${doc.title}' from Google Drive, Cache & Device."
                } else {
                    "Deleted '${doc.title}' from Google Drive & Cache."
                }
            }.onFailure { err ->
                recentlyLocallyDeletedDocIds.remove(doc.id)
                _actionMessage.value = err.localizedMessage ?: "Failed to delete from Google Drive."
            }
        }
    }

    fun openCloudDocument(
        doc: CloudDocument,
        onOpenEntity: (PdfEntity) -> Unit
    ) {
        if (_openingDocId.value != null) return
        viewModelScope.launch {
            _openingDocId.value = doc.id
            try {
                // Check fast cache or local readable path first for instant open
                val localFile = doc.localPath.takeIf { it.isNotBlank() }?.let { File(it) }
                if (localFile != null && localFile.exists() && localFile.canRead() && localFile.length() > 0L) {
                    val entity = PdfEntity(
                        path = localFile.canonicalPath,
                        title = doc.title.ifBlank { localFile.name },
                        uriString = Uri.fromFile(localFile).toString(),
                        sizeBytes = localFile.length(),
                        lastModifiedTimestamp = localFile.lastModified(),
                        folderName = doc.folderName.ifBlank { "Google Drive" }
                    )
                    PdfFastCacheManager.warmCacheAsync(appContext, entity)
                    onOpenEntity(entity)
                    return@launch
                }

                val result = driveRepository.downloadToFastCache(doc)
                result.onSuccess { cachedFile ->
                    val entity = PdfEntity(
                        path = cachedFile.canonicalPath,
                        title = doc.title.ifBlank { cachedFile.name },
                        uriString = Uri.fromFile(cachedFile).toString(),
                        sizeBytes = cachedFile.length(),
                        lastModifiedTimestamp = cachedFile.lastModified(),
                        folderName = doc.folderName.ifBlank { "Google Drive" }
                    )
                    onOpenEntity(entity)
                }.onFailure { err ->
                    _actionMessage.value = err.localizedMessage ?: "File not available offline or in Google Drive."
                }
            } finally {
                _openingDocId.value = null
                PdfFastCacheManager.refreshStats(appContext)
            }
        }
    }

    fun clearMobileCache() {
        viewModelScope.launch(Dispatchers.IO) {
            PdfFastCacheManager.clearAllCache(appContext)
            _actionMessage.value = "Mobile fast cache cleared."
        }
    }

    fun clearActionMessage() {
        _actionMessage.value = null
    }
}
