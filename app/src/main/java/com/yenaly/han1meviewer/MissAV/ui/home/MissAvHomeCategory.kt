package com.yenaly.han1meviewer.MissAV.ui.home
import android.content.Context
import android.util.Log
import com.yenaly.han1meviewer.Preferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class MissAvHomeCategory(
    @SerialName("key") val key: String,
    @SerialName("title") val title: String,
    @SerialName("genrePath") val genrePath: String,
    @SerialName("sort") val sort: String? = null,
    @SerialName("hidden") val hidden: Boolean = false,
)

@Serializable
private data class MissAvHomeCategoriesFile(
    @SerialName("version") val version: Int = 1,
    @SerialName("categories") val categories: List<MissAvHomeCategory> = emptyList(),
)

object MissAvHomeCategoryRepo {

    private const val TAG = "MissAvHomeCategoryRepo"

    private const val ASSET_PATH = "missav_options/home_categories.json"

    private const val PREF_OVERRIDE = "missav_home_categories_override"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    suspend fun load(context: Context): List<MissAvHomeCategory> =
        withContext(Dispatchers.IO) {
            val override = Preferences.preferenceSp.getString(PREF_OVERRIDE, null)
            if (!override.isNullOrBlank()) {
                runCatching {
                    json.decodeFromString<List<MissAvHomeCategory>>(override)
                }.onSuccess { return@withContext it }
                    .onFailure { Log.w(TAG, "Failed to read override, falling back to asset", it) }
            }
            readSeed(context)
        }

    suspend fun save(list: List<MissAvHomeCategory>) = withContext(Dispatchers.IO) {
        Preferences.preferenceSp.edit()
            .putString(PREF_OVERRIDE, json.encodeToString(list))
            .apply()
    }

    suspend fun resetToDefaults() = withContext(Dispatchers.IO) {
        Preferences.preferenceSp.edit().remove(PREF_OVERRIDE).apply()
    }

    fun hasOverride(): Boolean =
        !Preferences.preferenceSp.getString(PREF_OVERRIDE, null).isNullOrBlank()

    private fun readSeed(context: Context): List<MissAvHomeCategory> = runCatching {
        context.assets.open(ASSET_PATH).use { stream ->
            val file = json.decodeFromString<MissAvHomeCategoriesFile>(
                stream.bufferedReader().readText()
            )
            file.categories
        }
    }.onFailure {
        Log.e(TAG, "Failed to read $ASSET_PATH", it)
    }.getOrDefault(emptyList())
}