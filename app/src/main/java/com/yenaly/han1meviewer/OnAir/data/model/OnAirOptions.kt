package com.yenaly.han1meviewer.OnAir.data.model

data class OnAirSelectOption(
    val value: String,
    val label: String,
    val isSelected: Boolean = false,
)

object OnAirOptions {

    val searchFields: List<OnAirSelectOption> = listOf(
        OnAirSelectOption("title", "Title"),
        OnAirSelectOption("uploader", "Uploader"),
        OnAirSelectOption("tag", "Tag"),
    )

    val lengths: List<OnAirSelectOption> = listOf(
        OnAirSelectOption("any", "Any length"),
        OnAirSelectOption("xshort", "Under 1 min"),
        OnAirSelectOption("short", "1-4 min"),
        OnAirSelectOption("medium", "4-20 min"),
        OnAirSelectOption("long", "20-60 min"),
        OnAirSelectOption("xlong", "Over 1 hour"),
    )

    val dates: List<OnAirSelectOption> = listOf(
        OnAirSelectOption("any", "Any time"),
        OnAirSelectOption("day", "Past 24 hours"),
        OnAirSelectOption("week", "Past week"),
        OnAirSelectOption("month", "Past month"),
        OnAirSelectOption("year", "Past year"),
    )

    val sorts: List<OnAirSelectOption> = listOf(
        OnAirSelectOption("newest", "Newest"),
        OnAirSelectOption("oldest", "Oldest"),
        OnAirSelectOption("most_viewed", "Most viewed"),
        OnAirSelectOption("least_viewed", "Least viewed"),
        OnAirSelectOption("longest", "Longest"),
        OnAirSelectOption("shortest", "Shortest"),
    )

    val qualities: List<String> = listOf("original", "480", "360", "240", "144")

    fun qualityLabel(quality: String): String = when (quality) {
        "original" -> "Original"
        else -> "${quality}p"
    }
}
