package com.yenaly.han1meviewer.MissAV

import com.yenaly.han1meviewer.util.loadAssetAs
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MissAvTag(
    @SerialName("search_key") val searchKey: String,
    @SerialName("name") val name: String,
    @SerialName("group") val group: String? = null,
)

@Serializable
data class MissAvTags(
    @SerialName("sort")   val sort: List<MissAvTag> = emptyList(),
    @SerialName("filter") val filter: List<MissAvTag> = emptyList(),
)

enum class MissAvGroup(val jsonKey: String, val displayName: String) {
    SORT("sort", "Sort"),
    FILTER("filter", "Filter"),
    GENRE("genre", "Genre"),
}

object MissAvOptions {

    val tags: MissAvTags by lazy {
        loadAssetAs<MissAvTags>("missav_options/tags.json") ?: MissAvTags()
    }

    val genreOptions: List<MissAvTag> by lazy {
        loadAssetAs<List<MissAvTag>>("missav_options/genres.json").orEmpty()
    }

    val sortOptions: List<MissAvTag> get() = tags.sort
    val filterOptions: List<MissAvTag> get() = tags.filter

    const val DEFAULT_GENRE_KEY = "en/release"
    const val OTHER_GROUP = "Other"

    /** All group names in display order (Other is forced last). */
    val genreGroupNames: List<String> by lazy {
        val present = genreOptions.mapNotNull { it.group }.distinct()
        val ordered = present.filter { it != OTHER_GROUP }
        ordered + listOfNotNull(OTHER_GROUP.takeIf { it in present })
    }

    /** Genres inside a given group, sorted alphabetically. */
    fun genresInGroup(group: String): List<MissAvTag> =
        genreOptions.filter { it.group == group }.sortedBy { it.name }

    fun displayName(group: MissAvGroup, searchKey: String?): String? {
        if (searchKey.isNullOrBlank()) return null
        val list = when (group) {
            MissAvGroup.SORT -> sortOptions
            MissAvGroup.FILTER -> filterOptions
            MissAvGroup.GENRE -> genreOptions
        }
        return list.firstOrNull { it.searchKey == searchKey }?.name
    }

    /** "Group › Name" label for a selected genre. */
    fun genreLabel(searchKey: String?): String? {
        if (searchKey.isNullOrBlank()) return null
        val tag = genreOptions.firstOrNull { it.searchKey == searchKey } ?: return searchKey
        return tag.group?.let { "$it › ${tag.name}" } ?: tag.name
    }
}