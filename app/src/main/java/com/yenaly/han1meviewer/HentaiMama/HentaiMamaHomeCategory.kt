package com.yenaly.han1meviewer.HentaiMama

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.yenaly.han1meviewer.Preferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class HentaiMamaHomeCategory(
    @SerialName("key") val key: String,
    @SerialName("title") val title: String,
    @SerialName("genrePath") val genrePath: String,
    @SerialName("sort") val sort: String? = null,
    @SerialName("hidden") val hidden: Boolean = false,
)

@Serializable
private data class HentaiMamaHomeCategoriesFile(
    @SerialName("version") val version: Int = 1,
    @SerialName("categories") val categories: List<HentaiMamaHomeCategory> = emptyList(),
)

object HentaiMamaHomeCategoryRepo {

    private const val TAG = "HentaiMamaHomeCatRepo"

    private const val ASSET_PATH = "hentaimama_options/home_categories.json"

    private const val PREF_OVERRIDE = "hentaimama_home_categories_override"
    private const val PREF_OVERRIDE_VERSION = "hentaimama_home_categories_override_version"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    suspend fun load(context: Context): List<HentaiMamaHomeCategory> =
        withContext(Dispatchers.IO) {
            val (assetVersion, assetCategories) = readSeed(context)

            val override = Preferences.preferenceSp.getString(PREF_OVERRIDE, null)
            val overrideVersion = Preferences.preferenceSp.getInt(PREF_OVERRIDE_VERSION, -1)

            if (!override.isNullOrBlank() && overrideVersion == assetVersion) {
                val parsed = runCatching {
                    json.decodeFromString<List<HentaiMamaHomeCategory>>(override)
                }.onFailure {
                    Log.w(TAG, "Failed to parse override, falling back to asset", it)
                }.getOrNull()

                if (parsed != null) return@withContext parsed
            }

            if (!override.isNullOrBlank() && overrideVersion != assetVersion) {
                Log.d(
                    TAG,
                    "Override version ($overrideVersion) != asset version ($assetVersion); discarding stale override"
                )
            }

            Preferences.preferenceSp.edit {
                remove(PREF_OVERRIDE)
                putInt(PREF_OVERRIDE_VERSION, assetVersion)
            }

            assetCategories
        }

    suspend fun save(
        context: Context,
        categories: List<HentaiMamaHomeCategory>,
    ) = withContext(Dispatchers.IO) {
        val (assetVersion, _) = readSeed(context)
        Preferences.preferenceSp.edit {
            putString(PREF_OVERRIDE, json.encodeToString(categories))
            putInt(PREF_OVERRIDE_VERSION, assetVersion)
        }
    }

    suspend fun resetToDefaults() = withContext(Dispatchers.IO) {
        Preferences.preferenceSp.edit {
            remove(PREF_OVERRIDE)
            remove(PREF_OVERRIDE_VERSION)
        }
    }

    fun hasOverride(): Boolean =
        !Preferences.preferenceSp.getString(PREF_OVERRIDE, null).isNullOrBlank()

    private suspend fun readSeed(context: Context): Pair<Int, List<HentaiMamaHomeCategory>> =
        withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open(ASSET_PATH).use { stream ->
                    val file = json.decodeFromString<HentaiMamaHomeCategoriesFile>(
                        stream.bufferedReader().readText()
                    )
                    file.version to file.categories
                }
            }.onFailure {
                Log.e(TAG, "Failed to read $ASSET_PATH", it)
            }.getOrDefault(1 to emptyList())
        }
}
