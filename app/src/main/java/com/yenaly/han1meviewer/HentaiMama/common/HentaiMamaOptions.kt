package com.yenaly.han1meviewer.HentaiMama.common

import com.yenaly.han1meviewer.util.loadAssetAs
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class HentaiMamaOrder(
    @SerialName("name") val name: String,
    @SerialName("value") val value: String,
)

object HentaiMamaOptions {

    const val OTHER_GROUP = "Other"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    val genreGroups: LinkedHashMap<String, List<String>> by lazy {
        loadGenreGroups()
    }

    val genres: List<String> by lazy {
        genreGroups.values.flatten().distinct()
    }

    val producers: List<String> by lazy {
        loadAssetAs<List<String>>("hentaimama_options/producer.json").orEmpty()
    }

    val orders: List<HentaiMamaOrder> by lazy {
        loadAssetAs<List<HentaiMamaOrder>>("hentaimama_options/order.json").orEmpty()
    }

    val years: List<String> by lazy {
        val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        (currentYear downTo 1987).map { it.toString() }
    }

    val defaultOrder: String
        get() = orders.firstOrNull()?.value ?: "weekly"

    fun genresInGroup(group: String): List<String> =
        genreGroups[group].orEmpty()

    fun groupOf(genre: String): String? =
        genreGroups.entries.firstOrNull { genre in it.value }?.key

    private fun loadGenreGroups(): LinkedHashMap<String, List<String>> {
        val raw = runCatching {
            com.yenaly.han1meviewer.util.applicationContext.assets
                .open("hentaimama_options/genres.json")
                .use { it.bufferedReader().readText() }
        }.getOrNull() ?: return LinkedHashMap()

        val root: JsonElement = runCatching { json.parseToJsonElement(raw) }.getOrNull()
            ?: return LinkedHashMap()

        val obj = root as? kotlinx.serialization.json.JsonObject
            ?: return LinkedHashMap()

        val result = LinkedHashMap<String, List<String>>()
        obj.entries.forEach { (group, element) ->
            val arr = (element as? kotlinx.serialization.json.JsonArray) ?: return@forEach
            val values = arr.mapNotNull { it.jsonPrimitive.contentOrNullOrBlank() }
            if (values.isNotEmpty()) {
                result[group] = values
            }
        }
        return result
    }

    private fun kotlinx.serialization.json.JsonPrimitive.contentOrNullOrBlank(): String? {
        val c = contentOrNull() ?: return null
        return c.takeIf { it.isNotBlank() }
    }

    private fun kotlinx.serialization.json.JsonPrimitive.contentOrNull(): String? =
        runCatching { content }.getOrNull()
}
