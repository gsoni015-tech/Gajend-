package com.example.data.cloud

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

private const val AUTH_TAG = "Auth"

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        trySend(firebaseAuth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

object GoogleDriveAuthHelper {
    const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
    private const val TOKEN_TTL_MS = 45L * 60L * 1000L

    private val lock = Any()
    private var cachedToken: String? = null
    private var cachedForUserId: String? = null
    private var tokenExpiresAtMs: Long = 0L

    fun invalidateToken() {
        synchronized(lock) {
            cachedToken = null
            cachedForUserId = null
            tokenExpiresAtMs = 0L
        }
    }

    private fun cacheToken(userId: String, token: String) {
        synchronized(lock) {
            cachedToken = token
            cachedForUserId = userId
            tokenExpiresAtMs = System.currentTimeMillis() + TOKEN_TTL_MS
        }
    }

    suspend fun getOrRequestAccessToken(
        context: Context,
        userId: String,
        forceRefresh: Boolean = false
    ): DriveTokenResult {
        if (!forceRefresh) {
            synchronized(lock) {
                val token = cachedToken
                if (!token.isNullOrBlank() &&
                    cachedForUserId == userId &&
                    System.currentTimeMillis() < tokenExpiresAtMs
                ) {
                    return DriveTokenResult.Authorized(token)
                }
            }
        } else {
            invalidateToken()
        }

        return try {
            val request = AuthorizationRequest.builder()
                .setRequestedScopes(listOf(Scope(DRIVE_FILE_SCOPE)))
                .build()
            val result = Identity.getAuthorizationClient(context.applicationContext)
                .authorize(request)
                .await()

            val pendingIntent = result.pendingIntent
            if (result.hasResolution() && pendingIntent != null) {
                DriveTokenResult.NeedsConsent(pendingIntent)
            } else {
                val accessToken = result.accessToken
                if (!accessToken.isNullOrBlank()) {
                    cacheToken(userId, accessToken)
                    DriveTokenResult.Authorized(accessToken)
                } else {
                    DriveTokenResult.Error("Google Drive authorization did not return an access token.")
                }
            }
        } catch (e: ApiException) {
            Log.e(AUTH_TAG, "Google Drive authorization failed with status ${e.statusCode}", e)
            DriveTokenResult.Error(e.localizedMessage ?: "Google Drive authorization failed (${e.statusCode}).")
        } catch (e: Exception) {
            Log.e(AUTH_TAG, "Google Drive authorization error", e)
            DriveTokenResult.Error(e.localizedMessage ?: "Failed to authorize Google Drive access.")
        }
    }

    fun handleAuthorizationIntentResult(
        context: Context,
        userId: String,
        data: Intent?
    ): DriveTokenResult {
        return try {
            val result = Identity.getAuthorizationClient(context.applicationContext)
                .getAuthorizationResultFromIntent(data)
            val accessToken = result.accessToken
            if (!accessToken.isNullOrBlank()) {
                cacheToken(userId, accessToken)
                DriveTokenResult.Authorized(accessToken)
            } else {
                DriveTokenResult.Error("Google Drive access was not granted.")
            }
        } catch (e: ApiException) {
            Log.w(AUTH_TAG, "Google Drive consent declined or failed (${e.statusCode})")
            DriveTokenResult.Error("Google Drive permission was not granted.")
        } catch (e: Exception) {
            Log.e(AUTH_TAG, "Failed to read Google Drive consent result", e)
            DriveTokenResult.Error(e.localizedMessage ?: "Failed to complete Google Drive permission.")
        }
    }
}

fun attemptAutoSignIn(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onUnauthenticated: () -> Unit,
    scope: CoroutineScope
) {
    if (Firebase.auth.currentUser != null) {
        onAuthSuccess()
        return
    }
    val clientId = resolveDefaultWebClientId(context) ?: run {
        Log.w(AUTH_TAG, "Skipping auto sign-in: default_web_client_id string resource not found")
        onUnauthenticated()
        return
    }
    val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(true)
        .setServerClientId(clientId)
        .setAutoSelectEnabled(true)
        .build()
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()

    scope.launch {
        try {
            val cred = credentialManager.getCredential(context, request).credential
            if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                Firebase.auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
                onAuthSuccess()
            } else {
                onUnauthenticated()
            }
        } catch (_: Exception) {
            onUnauthenticated()
        }
    }
}

fun onGoogleSignInClicked(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    scope: CoroutineScope,
    onAuthCancelled: () -> Unit = {}
) {
    val activity = context as? Activity
    if (activity == null) {
        onAuthError("Activity context unavailable for Google Sign-In")
        return
    }
    val clientId = resolveDefaultWebClientId(context) ?: run {
        onAuthError("Google Sign-In configuration missing: default_web_client_id not found")
        return
    }
    val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(signInOption)
        .build()

    scope.launch {
        try {
            val cred = credentialManager.getCredential(activity, request).credential
            if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                Firebase.auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
                onAuthSuccess()
            } else {
                onAuthError("Unexpected credential type returned")
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w(AUTH_TAG, "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
            onAuthCancelled()
        } catch (e: Exception) {
            Log.e(AUTH_TAG, "Google Sign-In failed", e)
            onAuthError(e.localizedMessage ?: "Google Sign-In failed")
        }
    }
}

fun signOutGoogleAccount(
    credentialManager: CredentialManager,
    scope: CoroutineScope,
    onSignOutComplete: () -> Unit = {}
) {
    GoogleDriveAuthHelper.invalidateToken()
    scope.launch {
        withContext(NonCancellable) {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w(AUTH_TAG, "Failed to clear credential state: ${e.message}")
            } finally {
                Firebase.auth.signOut()
                onSignOutComplete()
            }
        }
    }
}

@Suppress("DiscouragedApi")
internal fun resolveDefaultWebClientId(context: Context): String? {
    return try {
        val resId = context.resources.getIdentifier(
            "default_web_client_id",
            "string",
            context.packageName
        )
        if (resId == 0) {
            null
        } else {
            context.getString(resId).trim().takeIf { it.isNotEmpty() }
        }
    } catch (_: Exception) {
        null
    }
}
