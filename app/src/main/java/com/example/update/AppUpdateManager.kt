package com.example.update

import android.content.Context
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
import com.google.firebase.firestore.IgnoreExtraProperties
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
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
        val currentVersionName: String
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
        val versionCode = (data["latestVersionCode"] as? Number)?.toLong() ?: 0L
        val versionName = (data["latestVersionName"] as? String)?.trim().orEmpty()
        val apkUrl = (data["apkUrl"] as? String)?.trim().orEmpty()
        val releaseNotes = (data["releaseNotes"] as? String)?.trim().orEmpty()
        val forceUpdate = data["forceUpdate"] as? Boolean ?: false
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

    fun canRequestPackageInstalls(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    suspend fun checkForUpdates(
        context: Context,
        isManual: Boolean,
        currentVersionCode: Long = BuildConfig.VERSION_CODE.toLong(),
        currentVersionName: String = BuildConfig.VERSION_NAME
    ) {
        if (!isManual && hasRunLaunchCheck) return
        if (!isManual) hasRunLaunchCheck = true

        if (isManual) {
            _state.value = UpdateCheckState.Checking
            _dialogVisible.value = true
        }

        try {
            val dbId = context.getString(R.string.firestore_database_id)
            val db = FirebaseFirestore.getInstance(dbId)
            val snapshot = db.collection(COLLECTION_APP_CONFIG)
                .document(DOC_LATEST_RELEASE)
                .get()
                .await()

            if (!snapshot.exists()) {
                val upToDate = UpdateCheckState.UpToDate(currentVersionCode, currentVersionName)
                _state.value = upToDate
                if (!isManual) {
                    _dialogVisible.value = false
                }
                return
            }

            val release = parseReleaseMap(snapshot.data)
            if (release == null) {
                val upToDate = UpdateCheckState.UpToDate(currentVersionCode, currentVersionName)
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
                _state.value = UpdateCheckState.UpToDate(currentVersionCode, currentVersionName)
                if (!isManual) {
                    _dialogVisible.value = false
                }
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Log.w(TAG, "Update check failed: ${e.message}", e)
            if (isManual) {
                _state.value = UpdateCheckState.Error(
                    message = "Unable to check for updates right now. Please check your internet connection."
                )
                _dialogVisible.value = true
            }
        }
    }

    suspend fun downloadAndInstallApk(context: Context, release: AppReleaseConfig) {
        if (!isValidHttpsApkUrl(release.apkUrl)) {
            _state.value = UpdateCheckState.Error("Invalid APK download URL. Only HTTPS links are allowed.")
            return
        }

        _state.value = UpdateCheckState.Downloading(progressPercent = 0, release = release)
        _dialogVisible.value = true

        try {
            val apkFile = withContext(Dispatchers.IO) {
                val updatesDir = File(context.cacheDir, UPDATES_SUBDIR).apply { mkdirs() }
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
                    .url(release.apkUrl)
                    .get()
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IllegalStateException("Download failed (HTTP ${response.code})")
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
                targetFile
            }

            val needsPerm = !canRequestPackageInstalls(context)
            _state.value = UpdateCheckState.ReadyToInstall(
                apkFile = apkFile,
                release = release,
                needsInstallPermission = needsPerm
            )
            if (!needsPerm) {
                launchApkInstaller(context, apkFile)
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Log.e(TAG, "APK download failed: ${e.message}", e)
            _state.value = UpdateCheckState.Error(
                message = e.message ?: "Failed to download update APK.",
                fallbackApkUrl = release.apkUrl
            )
        }
    }

    fun refreshInstallPermissionState(context: Context) {
        val current = _state.value
        if (current is UpdateCheckState.ReadyToInstall) {
            val needsPerm = !canRequestPackageInstalls(context)
            if (current.needsInstallPermission != needsPerm) {
                _state.value = current.copy(needsInstallPermission = needsPerm)
            }
        }
    }

    fun launchApkInstaller(context: Context, apkFile: File): Boolean {
        if (!apkFile.exists()) return false
        if (!canRequestPackageInstalls(context)) {
            openUnknownSourcesSettings(context)
            return false
        }
        return try {
            val apkUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                apkFile
            )
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch installer: ${e.message}", e)
            false
        }
    }

    fun openUnknownSourcesSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val intent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to open unknown sources settings: ${e.message}", e)
            }
        }
    }

    fun openDownloadInBrowser(context: Context, rawUrl: String) {
        if (!isValidHttpsApkUrl(rawUrl)) return
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(rawUrl.trim())).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
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
        hasRunLaunchCheck = false
        _state.value = UpdateCheckState.Idle
        _dialogVisible.value = false
    }
}
