package com.yenaly.han1meviewer.MissAV

import com.yenaly.han1meviewer.util.loadAssetAs
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A single selectable option (sort / filter / genre / whatever).
 */
@Serializable
data class MissAvTag(
    @SerialName("search_key") val searchKey: String,
    @SerialName("name") val name: String,
)

/**
 * Root of assets/missav_options/tags.json.
 *
 * Add new top-level arrays to the JSON to introduce new filter groups
 * without changing this class — just extend it with the new field.
 */
@Serializable
data class MissAvTags(
    @SerialName("sort")   val sort: List<MissAvTag> = emptyList(),
    @SerialName("filter") val filter: List<MissAvTag> = emptyList(),
    @SerialName("genre")  val genre: List<MissAvTag> = emptyList(),
    // Add more groups later, e.g.:
    // @SerialName("producer") val producer: List<MissAvTag> = emptyList(),
) {
    /** Return any group by its JSON key. */
    fun group(key: MissAvGroup): List<MissAvTag> = when (key) {
        MissAvGroup.SORT -> sort
        MissAvGroup.FILTER -> filter
        MissAvGroup.GENRE -> genre
    }
}

/** Identifies a filter group. Kept as an enum so we can add groups safely. */
enum class MissAvGroup(val jsonKey: String, val displayName: String) {
    SORT("sort", "Sort"),
    FILTER("filter", "Filter"),
    GENRE("genre", "Genre"),
}

/**
 * Central access point for MissAV filter options.
 * Loaded once from assets, cached for the process lifetime.
 */
object MissAvOptions {

    val tags: MissAvTags by lazy {
        loadAssetAs<MissAvTags>("missav_options/tags.json")
            ?: MissAvTags()
    }

    val sortOptions: List<MissAvTag> get() = tags.sort
    val filterOptions: List<MissAvTag> get() = tags.filter
    val genreOptions: List<MissAvTag> get() = tags.genre

    /** The default genre when no genre is selected ("All"). */
    const val DEFAULT_GENRE_KEY = "en/release"

    /** Look up the display name for a search key. */
    fun displayName(group: MissAvGroup, searchKey: String?): String? {
        if (searchKey.isNullOrBlank()) return null
        return tags.group(group).firstOrNull { it.searchKey == searchKey }?.name
    }
}