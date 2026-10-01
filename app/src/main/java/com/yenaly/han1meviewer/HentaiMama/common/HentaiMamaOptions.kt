package com.yenaly.han1meviewer.HentaiMama.common
import com.yenaly.han1meviewer.util.loadAssetAs
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HentaiMamaOrder(
    @SerialName("name") val name: String,
    @SerialName("value") val value: String,
)

object HentaiMamaOptions {

    const val OTHER_GROUP = "Other"

    val genres: List<String> by lazy {
        loadAssetAs<List<String>>("hentaimama_options/genres.json").orEmpty()
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
}
