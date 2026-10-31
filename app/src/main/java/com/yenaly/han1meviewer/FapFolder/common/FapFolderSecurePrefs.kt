package com.yenaly.han1meviewer.FapFolder.common

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.yenaly.yenaly_libs.utils.applicationContext

object FapFolderSecurePrefs {

    private const val FILE_NAME = "fapfolder_secure"
    private const val FALLBACK_FILE_NAME = "fapfolder_secure_fallback"

    val instance: SharedPreferences by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        createEncryptedWithRetry() ?: applicationContext.getSharedPreferences(
            FALLBACK_FILE_NAME,
            Context.MODE_PRIVATE,
        )
    }

    private fun createEncryptedWithRetry(): SharedPreferences? {
        repeat(3) { attempt ->
            val created = runCatching {
                EncryptedSharedPreferences.create(
                    applicationContext,
                    FILE_NAME,
                    MasterKey.Builder(applicationContext)
                        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                        .build(),
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )
            }.getOrNull()
            if (created != null) return created
            if (attempt < 2) {
                try {
                    Thread.sleep(150L * (attempt + 1))
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return null
                }
            }
        }
        return null
    }

    fun readFallback(key: String): String? = runCatching {
        applicationContext
            .getSharedPreferences(FALLBACK_FILE_NAME, Context.MODE_PRIVATE)
            .getString(key, null)
    }.getOrNull()
}
