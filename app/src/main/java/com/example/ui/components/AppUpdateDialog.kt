package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.ui.theme.BrandPrimary
import com.example.ui.theme.BrandPrimaryContainer
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WhiteMixBorder
import com.example.ui.theme.WhiteMixCard
import com.example.update.AppReleaseConfig
import com.example.update.UpdateCheckState

@Composable
fun AppUpdateDialog(
    state: UpdateCheckState,
    visible: Boolean,
    onStartDownload: (AppReleaseConfig) -> Unit,
    onInstallApk: (UpdateCheckState.ReadyToInstall) -> Unit,
    onOpenInstallPermission: () -> Unit,
    onOpenInBrowser: (String) -> Unit,
    onDismiss: () -> Unit
) {
    if (!visible || state is UpdateCheckState.Idle) return

    when (state) {
        UpdateCheckState.Idle -> Unit
        is UpdateCheckState.Checking -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                modifier = Modifier.testTag("app_update_dialog"),
                icon = {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 2.5.dp,
                        color = BrandPrimary
                    )
                },
                title = {
                    Text(
                        text = "Checking for Updates",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                },
                text = {
                    Text(
                        text = "Looking for a newer version of Narayani PDF Reader...",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                },
                confirmButton = {
                    TextButton(onClick = onDismiss) {
                        Text("Close")
                    }
                }
            )
        }

        is UpdateCheckState.UpToDate -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                modifier = Modifier.testTag("app_update_dialog"),
                icon = {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF16A34A),
                        modifier = Modifier.size(32.dp)
                    )
                },
                title = {
                    Text(
                        text = "App is Up to Date",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                },
                text = {
                    Text(
                        text = "You are using the latest version (v${state.currentVersionName} • Build ${state.currentVersionCode}).",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                },
                confirmButton = {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                        modifier = Modifier.testTag("app_update_ok_button")
                    ) {
                        Text("OK")
                    }
                }
            )
        }

        is UpdateCheckState.UpdateAvailable -> {
            val release = state.release
            AlertDialog(
                onDismissRequest = {
                    if (!release.forceUpdate) onDismiss()
                },
                modifier = Modifier.testTag("app_update_dialog"),
                icon = {
                    Icon(
                        imageVector = Icons.Default.SystemUpdate,
                        contentDescription = null,
                        tint = BrandPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                },
                title = {
                    Text(
                        text = "Update Available (v${release.latestVersionName})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                },
                text = {
                    Column {
                        Text(
                            text = "Installed: v${state.currentVersionName} (Build ${state.currentVersionCode}) → New: v${release.latestVersionName} (Build ${release.latestVersionCode})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = BrandPrimary
                        )
                        if (release.releaseNotes.isNotBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "What's new:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = release.releaseNotes,
                                fontSize = 13.sp,
                                color = TextSecondary
                            )
                        } else {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Download and install the latest APK directly to update your app without losing saved files.",
                                fontSize = 13.sp,
                                color = TextSecondary
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { onStartDownload(release) },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("app_update_now_button")
                    ) {
                        Text("Update Now")
                    }
                },
                dismissButton = {
                    if (!release.forceUpdate) {
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("app_update_later_button")
                        ) {
                            Text("Later")
                        }
                    }
                }
            )
        }

        is UpdateCheckState.Downloading -> {
            AlertDialog(
                onDismissRequest = {},
                modifier = Modifier.testTag("app_update_dialog"),
                icon = {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = null,
                        tint = BrandPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                },
                title = {
                    Text(
                        text = "Downloading Update v${state.release.latestVersionName}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (state.progressPercent > 0) {
                            LinearProgressIndicator(
                                progress = { state.progressPercent / 100f },
                                modifier = Modifier.fillMaxWidth(),
                                color = BrandPrimary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "${state.progressPercent}% downloaded...",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                                color = BrandPrimary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Downloading APK package...",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                    }
                },
                confirmButton = {}
            )
        }

        is UpdateCheckState.ReadyToInstall -> {
            AlertDialog(
                onDismissRequest = {
                    if (!state.release.forceUpdate) onDismiss()
                },
                modifier = Modifier.testTag("app_update_dialog"),
                icon = {
                    Icon(
                        imageVector = Icons.Default.SystemUpdate,
                        contentDescription = null,
                        tint = Color(0xFF16A34A),
                        modifier = Modifier.size(32.dp)
                    )
                },
                title = {
                    Text(
                        text = if (state.needsInstallPermission) "Allow App Install Permission" else "Ready to Install Update",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                },
                text = {
                    Text(
                        text = if (state.needsInstallPermission) {
                            "Update v${state.release.latestVersionName} is downloaded. Tap \"Allow Permission\" and turn on \"Allow from this source\", then return here to install."
                        } else {
                            "Update v${state.release.latestVersionName} is ready. Tap Install to update over your existing app."
                        },
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                },
                confirmButton = {
                    if (state.needsInstallPermission) {
                        Button(
                            onClick = onOpenInstallPermission,
                            colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                            modifier = Modifier.testTag("app_update_permission_button")
                        ) {
                            Text("Allow Permission")
                        }
                    } else {
                        Button(
                            onClick = { onInstallApk(state) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                            modifier = Modifier.testTag("app_update_install_button")
                        ) {
                            Text("Install Update")
                        }
                    }
                },
                dismissButton = {
                    if (!state.release.forceUpdate) {
                        TextButton(onClick = onDismiss) {
                            Text("Later")
                        }
                    }
                }
            )
        }

        is UpdateCheckState.Error -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                modifier = Modifier.testTag("app_update_dialog"),
                title = {
                    Text(
                        text = "Update Check",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                },
                text = {
                    Text(
                        text = state.message,
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                },
                confirmButton = {
                    if (!state.fallbackApkUrl.isNullOrBlank()) {
                        Button(
                            onClick = { onOpenInBrowser(state.fallbackApkUrl) },
                            colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                            modifier = Modifier.testTag("app_update_browser_button")
                        ) {
                            Text("Open in Browser")
                        }
                    } else {
                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary)
                        ) {
                            Text("OK")
                        }
                    }
                },
                dismissButton = {
                    if (!state.fallbackApkUrl.isNullOrBlank()) {
                        TextButton(onClick = onDismiss) {
                            Text("Close")
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun AppUpdateToolsCard(
    state: UpdateCheckState,
    onCheckUpdate: () -> Unit,
    onUpdateNow: (AppReleaseConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    val isChecking = state is UpdateCheckState.Checking
    val availableRelease = (state as? UpdateCheckState.UpdateAvailable)?.release
    val subtitleText = when (state) {
        is UpdateCheckState.UpdateAvailable -> "New update v${state.release.latestVersionName} available!"
        is UpdateCheckState.UpToDate -> "You are on the latest version"
        is UpdateCheckState.Checking -> "Checking Firebase for updates..."
        else -> "Direct APK update check (non-Play Store)"
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("tools_app_update_card"),
        shape = RoundedCornerShape(14.dp),
        color = WhiteMixCard,
        border = BorderStroke(1.dp, if (availableRelease != null) BrandPrimary.copy(alpha = 0.45f) else WhiteMixBorder),
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(BrandPrimaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SystemUpdate,
                        contentDescription = "App Updates",
                        tint = BrandPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = "App Version v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitleText,
                        fontSize = 12.sp,
                        color = if (availableRelease != null) BrandPrimary else TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (availableRelease != null) {
                Button(
                    onClick = { onUpdateNow(availableRelease) },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("tools_check_update_button")
                ) {
                    Text("Update", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            } else {
                OutlinedButton(
                    onClick = onCheckUpdate,
                    enabled = !isChecking,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("tools_check_update_button")
                ) {
                    if (isChecking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = BrandPrimary
                        )
                    } else {
                        Text("Check Update", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
