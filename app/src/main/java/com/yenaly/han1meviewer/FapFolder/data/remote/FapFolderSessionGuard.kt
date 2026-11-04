package com.yenaly.han1meviewer.FapFolder.data.remote

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.yenaly.han1meviewer.FapFolder.common.FapFolderSecurePrefs
import com.yenaly.han1meviewer.FapFolder.data.local.FapFolderSessionCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object FapFolderCredentialStore {

    private const val KEY_USER = "fapfolder_auto_user"
    private const val KEY_PASS = "fapfolder_auto_pass"
    private const val KEY_REJECTED = "fapfolder_auto_rejected"

    fun canStore(): Boolean = FapFolderSecurePrefs.isEncrypted

    fun save(username: String, password: String): Boolean {
        if (username.isBlank() || password.isEmpty()) return false
        if (!canStore()) {
            clear()
            return false
        }
        FapFolderSecurePrefs.instance.edit(commit = true) {
            putString(KEY_USER, username.trim())
            putString(KEY_PASS, password)
            remove(KEY_REJECTED)
        }
        return true
    }

    fun load(): Pair<String, String>? {
        if (!canStore()) return null
        val prefs = FapFolderSecurePrefs.instance
        if (prefs.getBoolean(KEY_REJECTED, false)) return null
        val user = prefs.getString(KEY_USER, null)?.takeIf { it.isNotBlank() } ?: return null
        val pass = prefs.getString(KEY_PASS, null)?.takeIf { it.isNotEmpty() } ?: return null
        return user to pass
    }

    fun hasSaved(): Boolean = load() != null

    internal fun markRejected() {
        if (!canStore()) return
        FapFolderSecurePrefs.instance.edit(commit = true) { putBoolean(KEY_REJECTED, true) }
    }

    fun clear() {
        runCatching {
            FapFolderSecurePrefs.instance.edit(commit = true) {
                remove(KEY_USER)
                remove(KEY_PASS)
                remove(KEY_REJECTED)
            }
        }
    }
}

sealed interface FapFolderGuardResult {
    val probe: FapFolderProbeResult

    data class Valid(
        override val probe: FapFolderProbeResult,
        val reSignedIn: Boolean = false,
    ) : FapFolderGuardResult

    data class Transient(override val probe: FapFolderProbeResult) : FapFolderGuardResult

    data class NeedsManualLogin(
        val message: String,
        override val probe: FapFolderProbeResult = FapFolderProbeResult(
            ok = false,
            failure = FapFolderProbeFailure.SessionExpired,
            userMessage = message,
            suggestRelogin = true,
        ),
    ) : FapFolderGuardResult
}

object FapFolderSessionGuard {

    private const val TAG = "FapFolderSessionGuard"

    private const val RECENT_RESIGNIN_WINDOW_MS = 20_000L
    private const val FAILED_RESIGNIN_BACKOFF_MS = 60_000L

    private val mutex = Mutex()

    @Volatile
    private var lastReSignInOkAt = 0L

    @Volatile
    private var lastReSignInFailAt = 0L

    suspend fun validateOrSignIn(
        context: Context,
        allowAutoLogin: Boolean = true,
    ): FapFolderGuardResult = withContext(Dispatchers.IO) {
        mutex.withLock { validateLocked(context, allowAutoLogin) }
    }

    suspend fun recoverAfterDeadSession(): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {

            if (System.currentTimeMillis() - lastReSignInOkAt < RECENT_RESIGNIN_WINDOW_MS &&
                FapFolderNetwork.hasAuthCookies()
            ) {
                return@withLock true
            }
            val result = runCatching {
                validateLocked(FapFolderNetwork.context(), allowAutoLogin = true)
            }.getOrNull()
            result is FapFolderGuardResult.Valid && result.reSignedIn
        }
    }

    private suspend fun validateLocked(
        context: Context,
        allowAutoLogin: Boolean,
    ): FapFolderGuardResult {
        val first = FapFolderAuthRepo.probe(context).first()
        if (first.ok) return FapFolderGuardResult.Valid(first)

        when (first.failure) {
            FapFolderProbeFailure.NoCookies, FapFolderProbeFailure.SessionExpired -> Unit
            else -> return FapFolderGuardResult.Transient(first)
        }

        val deadMessage = first.userMessage ?: "Session expired. Sign in again."

        if (first.failure == FapFolderProbeFailure.SessionExpired) {
            Log.d(TAG, "session dead, clearing auth cookies (Cloudflare cookies kept)")
            FapFolderNetwork.clearAuthCookies()
            FapFolderSessionCache.clear()
        }

        if (!allowAutoLogin) return FapFolderGuardResult.NeedsManualLogin(deadMessage)

        val credentials = FapFolderCredentialStore.load()
            ?: return FapFolderGuardResult.NeedsManualLogin(deadMessage)

        if (System.currentTimeMillis() - lastReSignInFailAt < FAILED_RESIGNIN_BACKOFF_MS) {
            return FapFolderGuardResult.NeedsManualLogin(deadMessage)
        }

        val login = FapFolderAuthRepo.login(credentials.first, credentials.second, remember = true)
            .toList()
            .firstOrNull()
            ?: FapFolderAuthResult.NetworkError(IllegalStateException("No login response"))

        return when (login) {
            is FapFolderAuthResult.Success -> {
                val second = FapFolderAuthRepo.probe(context).first()
                when {
                    second.ok -> {
                        lastReSignInOkAt = System.currentTimeMillis()
                        Log.d(TAG, "re-signed in with saved credentials")
                        FapFolderGuardResult.Valid(second, reSignedIn = true)
                    }
                    second.failure == FapFolderProbeFailure.SessionExpired ||
                            second.failure == FapFolderProbeFailure.NoCookies -> {
                        lastReSignInFailAt = System.currentTimeMillis()
                        FapFolderNetwork.clearAuthCookies()
                        FapFolderSessionCache.clear()
                        FapFolderGuardResult.NeedsManualLogin(deadMessage)
                    }
                    else -> FapFolderGuardResult.Transient(second)
                }
            }

            is FapFolderAuthResult.InvalidCredentials,
            is FapFolderAuthResult.FormError -> {
                lastReSignInFailAt = System.currentTimeMillis()
                FapFolderCredentialStore.markRejected()
                FapFolderGuardResult.NeedsManualLogin(
                    "Saved sign-in was rejected. Please sign in again."
                )
            }

            is FapFolderAuthResult.SessionExpired -> {
                lastReSignInFailAt = System.currentTimeMillis()
                FapFolderGuardResult.NeedsManualLogin(deadMessage)
            }

            is FapFolderAuthResult.CloudflareChallenge -> FapFolderGuardResult.Transient(
                FapFolderProbeResult(
                    ok = false,
                    failure = FapFolderProbeFailure.CloudflareChallenge,
                    userMessage = "Cloudflare challenge. Wait a moment and try again.",
                    suggestWait = true,
                )
            )

            is FapFolderAuthResult.NetworkError -> FapFolderGuardResult.Transient(
                FapFolderProbeResult(
                    ok = false,
                    failure = FapFolderProbeFailure.Network,
                    userMessage = "Could not reach FapFolder: " +
                            (login.throwable.message ?: login.throwable::class.simpleName),
                    suggestWait = true,
                )
            )
        }
    }
}
