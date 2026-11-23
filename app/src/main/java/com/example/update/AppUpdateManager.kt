package com.example.update

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.annotation.Keep
import androidx.core.content.FileProvider
import com.example.BuildConfig
import com.example.R
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.lang.ref.WeakReference
import java.net.URI
import java.util.concurrent.TimeUnit

@Keep
@IgnoreExtraProperties
data class AppReleaseConfig(
    val latestVersionCode: Long = 0L,
    val latestVersionName: String = "",
    val apkUrl: String = "",
    val releaseNotes: String = "",
    val forceUpdate: Boolean = false
)

sealed class UpdateCheckState {
    object Idle : UpdateCheckState()
    object Checking : UpdateCheckState()
    data class UpdateAvailable(
        val release: AppReleaseConfig,
        val currentVersionCode: Long,
        val currentVersionName: String
    ) : UpdateCheckState()

    data class UpToDate(
        val currentVersionCode: Long,
        val currentVersionName: String,
        val hasRemoteRelease: Boolean = false
    ) : UpdateCheckState()

    data class Downloading(
        val progressPercent: Int,
        val release: AppReleaseConfig
    ) : UpdateCheckState()

    data class ReadyToInstall(
        val apkFile: File,
        val release: AppReleaseConfig,
        val needsInstallPermission: Boolean
    ) : UpdateCheckState()

    data class Error(
        val message: String,
        val fallbackApkUrl: String? = null
    ) : UpdateCheckState()
}

object AppUpdateManager {
    private const val TAG = "AppUpdateManager"
    private const val COLLECTION_APP_CONFIG = "app_config"
    private const val DOC_LATEST_RELEASE = "latest_release"
    private const val UPDATES_SUBDIR = "app_updates"
    private const val UPDATE_APK_FILENAME = "narayani_update.apk"
    private const val UPDATE_CHECK_TIMEOUT_MS = 12_000L

    private val managerScope by lazy {
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }
    private var checkJob: Job? = null
    private var downloadJob: Job? = null
    private var activityRef: WeakReference<Activity>? = null

    private val _state = MutableStateFlow<UpdateCheckState>(UpdateCheckState.Idle)
    val state: StateFlow<UpdateCheckState> = _state.asStateFlow()

    private val _dialogVisible = MutableStateFlow(false)
    val dialogVisible: StateFlow<Boolean> = _dialogVisible.asStateFlow()

    private var hasRunLaunchCheck = false

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    fun bindActivity(activity: Activity) {
        if (!activity.isFinishing && !activity.isDestroyed) {
            activityRef = WeakReference(activity)
        }
    }

    private fun resolveActivity(context: Context): Activity? {
        var current: Context? = context
        while (current is ContextWrapper) {
            if (current is Activity) {
                return current.takeIf { !it.isFinishing && !it.isDestroyed }
            }
            current = current.baseContext
        }
        return activityRef?.get()?.takeIf { !it.isFinishing && !it.isDestroyed }
    }

    fun isUpdateAvailable(currentVersionCode: Long, remoteVersionCode: Long): Boolean {
        return remoteVersionCode > currentVersionCode
    }

    fun isValidHttpsApkUrl(rawUrl: String): Boolean {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) return false
        return try {
            val uri = URI(trimmed)
            if (!uri.scheme.equals("https", ignoreCase = true)) return false
            val host = uri.host?.lowercase()?.trim() ?: return false
            if (host.isEmpty()) return false
            if (isPrivateOrLocalHost(host)) return false
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun isPrivateOrLocalHost(host: String): Boolean {
        if (host == "localhost" || host == "localhost.localdomain" || host == "::1" || host == "[::1]") {
            return true
        }
        if (host.endsWith(".local") || host.endsWith(".internal")) return true
        val parts = host.split('.')
        if (parts.size == 4 && parts.all { it.toIntOrNull() in 0..255 }) {
            val first = parts[0].toInt()
            val second = parts[1].toInt()
            return first == 127 ||
                first == 10 ||
                first == 0 ||
                (first == 169 && second == 254) ||
                (first == 192 && second == 168) ||
                (first == 172 && second in 16..31)
        }
        return false
    }

    fun isValidApkHeader(headerBytes: ByteArray, length: Int): Boolean {
        if (length < 4 || headerBytes.size < 4) return false
        return headerBytes[0] == 0x50.toByte() &&
            headerBytes[1] == 0x4B.toByte() &&
            headerBytes[2] == 0x03.toByte() &&
            headerBytes[3] == 0x04.toByte()
    }

    fun parseReleaseMap(data: Map<String, Any?>?): AppReleaseConfig? {
        if (data == null) return null
        val rawVersionCode = data["latestVersionCode"] ?: data["latest_version_code"]
        val versionCode = when (rawVersionCode) {
            is Number -> rawVersionCode.toLong()
            is String -> rawVersionCode.trim().toLongOrNull() ?: 0L
            else -> 0L
        }
        val versionName = ((data["latestVersionName"] ?: data["latest_version_name"]) as? String)?.trim().orEmpty()
        val apkUrl = ((data["apkUrl"] ?: data["apk_url"]) as? String)?.trim().orEmpty()
        val releaseNotes = ((data["releaseNotes"] ?: data["release_notes"]) as? String)?.trim().orEmpty()
        val rawForceUpdate = data["forceUpdate"] ?: data["force_update"]
        val forceUpdate = when (rawForceUpdate) {
            is Boolean -> rawForceUpdate
            is String -> rawForceUpdate.equals("true", ignoreCase = true)
            else -> false
        }
        if (versionCode <= 0L || versionName.isEmpty() || !isValidHttpsApkUrl(apkUrl)) {
            return null
        }
        return AppReleaseConfig(
            latestVersionCode = versionCode,
            latestVersionName = versionName,
            apkUrl = apkUrl,
            releaseNotes = releaseNotes,
            forceUpdate = forceUpdate
        )
    }

    fun buildPlayStoreWebUrl(packageName: String): String {
        return "https://play.google.com/store/apps/details?id=${packageName.trim()}"
    }

    fun formatUpdateErrorMessage(error: Throwable): String {
        return when (error) {
            is TimeoutCancellationException ->
                "Update check timed out. Please check your internet connection or check Google Play Store."
            is FirebaseFirestoreException -> when (error.code) {
                FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                    "Update server access denied. Please try updating from Google Play Store."
                FirebaseFirestoreException.Code.UNAVAILABLE,
                FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ->
                    "Unable to reach update server. Please check your internet connection."
                else ->
                    "Unable to check for updates right now (${error.code.name})."
            }
            else ->
                "Unable to check for updates right now. Please check your internet connection."
        }
    }

    fun canRequestPackageInstalls(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.applicationContext.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun startCheckForUpdates(
        context: Context,
        isManual: Boolean,
        currentVersionCode: Long = BuildConfig.VERSION_CODE.toLong(),
        currentVersionName: String = BuildConfig.VERSION_NAME
    ) {
        resolveActivity(context)?.let { bindActivity(it) }
        if (downloadJob?.isActive == true) {
            if (isManual) _dialogVisible.value = true
            return
        }
        if (checkJob?.isActive == true) {
            if (isManual) _dialogVisible.value = true
            return
        }
        val appContext = context.applicationContext
        checkJob = managerScope.launch {
            checkForUpdates(
                context = appContext,
                isManual = isManual,
                currentVersionCode = currentVersionCode,
                currentVersionName = currentVersionName
            )
        }
    }

    fun startDownloadAndInstall(context: Context, release: AppReleaseConfig) {
        resolveActivity(context)?.let { bindActivity(it) }
        if (downloadJob?.isActive == true) {
            _dialogVisible.value = true
            return
        }
        val appContext = context.applicationContext
        downloadJob = managerScope.launch {
            downloadAndInstallApk(appContext, release)
        }
    }

    suspend fun checkForUpdates(
        context: Context,
        isManual: Boolean,
        currentVersionCode: Long = BuildConfig.VERSION_CODE.toLong(),
        currentVersionName: String = BuildConfig.VERSION_NAME
    ) {
        resolveActivity(context)?.let { bindActivity(it) }
        if (!isManual && hasRunLaunchCheck) return
        if (!isManual) hasRunLaunchCheck = true

        if (isManual) {
            _state.value = UpdateCheckState.Checking
            _dialogVisible.value = true
        }

        val appContext = context.applicationContext

        try {
            val dbId = appContext.getString(R.string.firestore_database_id)
            val db = FirebaseFirestore.getInstance(dbId)
            val source = if (isManual) Source.SERVER else Source.DEFAULT
            val snapshot = withTimeout(UPDATE_CHECK_TIMEOUT_MS) {
                db.collection(COLLECTION_APP_CONFIG)
                    .document(DOC_LATEST_RELEASE)
                    .get(source)
                    .await()
            }

            if (!snapshot.exists()) {
                val upToDate = UpdateCheckState.UpToDate(
                    currentVersionCode = currentVersionCode,
                    currentVersionName = currentVersionName,
                    hasRemoteRelease = false
                )
                _state.value = upToDate
                if (!isManual) {
                    _dialogVisible.value = false
                }
                return
            }

            val release = parseReleaseMap(snapshot.data)
            if (release == null) {
                val upToDate = UpdateCheckState.UpToDate(
                    currentVersionCode = currentVersionCode,
                    currentVersionName = currentVersionName,
                    hasRemoteRelease = false
                )
                _state.value = upToDate
                if (!isManual) {
                    _dialogVisible.value = false
                }
                return
            }

            if (isUpdateAvailable(currentVersionCode, release.latestVersionCode)) {
                _state.value = UpdateCheckState.UpdateAvailable(
                    release = release,
                    currentVersionCode = currentVersionCode,
                    currentVersionName = currentVersionName
                )
                _dialogVisible.value = true
            } else {
                _state.value = UpdateCheckState.UpToDate(
                    currentVersionCode = currentVersionCode,
                    currentVersionName = currentVersionName,
                    hasRemoteRelease = true
                )
                if (!isManual) {
                    _dialogVisible.value = false
                }
            }
        } catch (te: TimeoutCancellationException) {
            Log.w(TAG, "Update check timed out", te)
            if (isManual) {
                _state.value = UpdateCheckState.Error(message = formatUpdateErrorMessage(te))
                _dialogVisible.value = true
            } else if (_state.value is UpdateCheckState.Checking) {
                _state.value = UpdateCheckState.Idle
            }
        } catch (ce: CancellationException) {
            if (_state.value is UpdateCheckState.Checking) {
                _state.value = UpdateCheckState.Idle
            }
            throw ce
        } catch (e: Exception) {
            Log.w(TAG, "Update check failed: ${e.message}", e)
            if (isManual) {
                _state.value = UpdateCheckState.Error(
                    message = formatUpdateErrorMessage(e)
                )
                _dialogVisible.value = true
            } else if (_state.value is UpdateCheckState.Checking) {
                _state.value = UpdateCheckState.Idle
            }
        }
    }

    suspend fun downloadAndInstallApk(context: Context, release: AppReleaseConfig) {
        resolveActivity(context)?.let { bindActivity(it) }
        if (!isValidHttpsApkUrl(release.apkUrl)) {
            _state.value = UpdateCheckState.Error("Invalid APK download URL. Only HTTPS links are allowed.")
            _dialogVisible.value = true
            return
        }

        val appContext = context.applicationContext
        _state.value = UpdateCheckState.Downloading(progressPercent = 0, release = release)
        _dialogVisible.value = true

        try {
            val apkFile = withContext(Dispatchers.IO) {
                val updatesDir = File(appContext.cacheDir, UPDATES_SUBDIR).apply { mkdirs() }
                val canonicalDir = updatesDir.canonicalPath
                val targetFile = File(updatesDir, UPDATE_APK_FILENAME).canonicalFile
                if (!targetFile.path.startsWith(canonicalDir + File.separator)) {
                    throw SecurityException("Invalid update cache destination")
                }

                val tempFile = File(updatesDir, "$UPDATE_APK_FILENAME.tmp").canonicalFile
                if (!tempFile.path.startsWith(canonicalDir + File.separator)) {
                    throw SecurityException("Invalid temporary update path")
                }
                if (tempFile.exists()) tempFile.delete()

                val request = Request.Builder()
                    .url(release.apkUrl.trim())
                    .get()
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IllegalStateException("Download failed (HTTP ${response.code})")
                    }
                    val contentType = response.header("Content-Type").orEmpty()
                    if (contentType.contains("text/html", ignoreCase = true)) {
                        throw IllegalStateException("Update link returned a webpage instead of a direct APK file.")
                    }
                    val body = response.body ?: throw IllegalStateException("Empty download response")
                    val totalBytes = body.contentLength()
                    var downloadedBytes = 0L
                    var lastProgress = -1

                    body.byteStream().use { input ->
                        FileOutputStream(tempFile).use { output ->
                            val buffer = ByteArray(16 * 1024)
                            while (true) {
                                val read = input.read(buffer)
                                if (read == -1) break
                                output.write(buffer, 0, read)
                                downloadedBytes += read
                                if (totalBytes > 0L) {
                                    val percent = ((downloadedBytes * 100L) / totalBytes).toInt().coerceIn(0, 100)
                                    if (percent != lastProgress) {
                                        lastProgress = percent
                                        _state.value = UpdateCheckState.Downloading(percent, release)
                                    }
                                }
                            }
                            output.flush()
                        }
                    }
                }

                if (!tempFile.exists() || tempFile.length() < 1024L) {
                    tempFile.delete()
                    throw IllegalStateException("Downloaded file is not a valid APK package.")
                }

                val header = ByteArray(4)
                val bytesRead = tempFile.inputStream().use { it.read(header) }
                if (!isValidApkHeader(header, bytesRead)) {
                    tempFile.delete()
                    throw IllegalStateException("URL returned a webpage instead of a direct APK file.")
                }

                if (targetFile.exists()) targetFile.delete()
                if (!tempFile.renameTo(targetFile)) {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }

                val archiveInfo = appContext.packageManager.getPackageArchiveInfo(targetFile.absolutePath, 0)
                if (archiveInfo != null && archiveInfo.packageName != appContext.packageName) {
                    targetFile.delete()
                    throw IllegalStateException(
                        "Downloaded APK (${archiveInfo.packageName}) does not match this app (${appContext.packageName})."
                    )
                }

                targetFile
            }

            val needsPerm = !canRequestPackageInstalls(appContext)
            _state.value = UpdateCheckState.ReadyToInstall(
                apkFile = apkFile,
                release = release,
                needsInstallPermission = needsPerm
            )
            _dialogVisible.value = true
            if (!needsPerm) {
                launchApkInstaller(resolveActivity(context) ?: appContext, apkFile)
            }
        } catch (ce: CancellationException) {
            if (_state.value is UpdateCheckState.Downloading) {
                _state.value = UpdateCheckState.UpdateAvailable(
                    release = release,
                    currentVersionCode = BuildConfig.VERSION_CODE.toLong(),
                    currentVersionName = BuildConfig.VERSION_NAME
                )
            }
            throw ce
        } catch (e: Exception) {
            Log.e(TAG, "APK download failed: ${e.message}", e)
            _state.value = UpdateCheckState.Error(
                message = e.message ?: "Failed to download update APK.",
                fallbackApkUrl = release.apkUrl
            )
            _dialogVisible.value = true
        }
    }

    fun refreshInstallPermissionState(context: Context) {
        resolveActivity(context)?.let { bindActivity(it) }
        val current = _state.value
        if (current is UpdateCheckState.ReadyToInstall) {
            val needsPerm = !canRequestPackageInstalls(context)
            if (current.needsInstallPermission != needsPerm) {
                _state.value = current.copy(needsInstallPermission = needsPerm)
                if (!needsPerm && current.apkFile.exists()) {
                    _dialogVisible.value = true
                    launchApkInstaller(resolveActivity(context) ?: context, current.apkFile)
                }
            }
        }
    }

    fun showReadyToInstallPrompt() {
        if (_state.value is UpdateCheckState.ReadyToInstall) {
            _dialogVisible.value = true
        }
    }

    fun launchApkInstaller(context: Context, apkFile: File): Boolean {
        val appContext = context.applicationContext
        val currentReleaseUrl = (_state.value as? UpdateCheckState.ReadyToInstall)?.release?.apkUrl
        if (!apkFile.exists()) {
            _state.value = UpdateCheckState.Error(
                message = "Downloaded APK file is missing. Please try updating again.",
                fallbackApkUrl = currentReleaseUrl
            )
            _dialogVisible.value = true
            return false
        }
        if (!canRequestPackageInstalls(appContext)) {
            val current = _state.value
            if (current is UpdateCheckState.ReadyToInstall && !current.needsInstallPermission) {
                _state.value = current.copy(needsInstallPermission = true)
            }
            openUnknownSourcesSettings(context)
            return false
        }

        val launchTarget = resolveActivity(context) ?: appContext
        val needsNewTask = launchTarget !is Activity
        val apkUri: Uri = FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.provider",
            apkFile
        )

        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (needsNewTask) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            launchTarget.startActivity(viewIntent)
            return true
        } catch (e: Exception) {
            Log.w(TAG, "ACTION_VIEW APK install failed, trying fallback: ${e.message}", e)
        }

        @Suppress("DEPRECATION")
        val fallbackIntent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            data = apkUri
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            if (needsNewTask) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            launchTarget.startActivity(fallbackIntent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch installer: ${e.message}", e)
            _state.value = UpdateCheckState.Error(
                message = "Could not open the Android installer on this device. Try opening the update link in your browser.",
                fallbackApkUrl = currentReleaseUrl
            )
            _dialogVisible.value = true
            false
        }
    }

    fun openUnknownSourcesSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val launchTarget = resolveActivity(context) ?: context.applicationContext
            try {
                val intent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    if (launchTarget !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                launchTarget.startActivity(intent)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to open unknown sources settings: ${e.message}", e)
                _state.value = UpdateCheckState.Error(
                    message = "Please enable \"Install unknown apps\" for Narayani PDF Reader in Android Settings.",
                    fallbackApkUrl = (_state.value as? UpdateCheckState.ReadyToInstall)?.release?.apkUrl
                )
                _dialogVisible.value = true
            }
        }
    }

    fun openPlayStoreForUpdate(context: Context): Boolean {
        val launchTarget = resolveActivity(context) ?: context.applicationContext
        val needsNewTask = launchTarget !is Activity
        val packageName = context.packageName
        val marketIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("market://details?id=$packageName")
        ).apply {
            if (needsNewTask) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            launchTarget.startActivity(marketIntent)
            return true
        } catch (_: Exception) {
            // Fall back to HTTPS Play Store link in browser
        }

        val webIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse(buildPlayStoreWebUrl(packageName))
        ).apply {
            if (needsNewTask) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            launchTarget.startActivity(webIntent)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to open Play Store: ${e.message}", e)
            false
        }
    }

    fun openDownloadInBrowser(context: Context, rawUrl: String) {
        if (!isValidHttpsApkUrl(rawUrl)) return
        val launchTarget = resolveActivity(context) ?: context.applicationContext
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(rawUrl.trim())).apply {
                if (launchTarget !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            launchTarget.startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to open browser URL: ${e.message}", e)
        }
    }

    fun dismissDialog() {
        val current = _state.value
        if (current is UpdateCheckState.UpdateAvailable && current.release.forceUpdate) {
            return
        }
        if (current is UpdateCheckState.Downloading) {
            return
        }
        _dialogVisible.value = false
    }

    fun resetForTesting() {
        checkJob?.cancel()
        downloadJob?.cancel()
        checkJob = null
        downloadJob = null
        activityRef = null
        hasRunLaunchCheck = false
        _state.value = UpdateCheckState.Idle
        _dialogVisible.value = false
    }
}
