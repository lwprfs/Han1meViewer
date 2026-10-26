package com.yenaly.han1meviewer.OnAir.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
object OnAirGateRoute

@Serializable
object OnAirHomeRoute

@Serializable
data class OnAirSearchRoute(
    val query: String? = null,
    val field: String? = null,
    val length: String? = null,
    val date: String? = null,
    val sort: String? = null,
    val tag: String? = null,
)

@Serializable
data class OnAirVideoRoute(
    val videoId: String,
    val preferredQuality: String? = null,
)

@Serializable
data class OnAirUserRoute(
    val username: String,
)

@Serializable
data class OnAirTagRoute(
    val tagName: String,
)

@Serializable
object OnAirTagsIndexRoute

@Serializable
object OnAirPicturesRoute

@Serializable
object OnAirHistoryRoute

@Serializable
object OnAirAnnouncementsRoute

@Serializable
object OnAirChatRoute

@Serializable
object OnAirSettingsRoute

@Serializable
object OnAirCookieRoute

@Serializable
object OnAirLoginRoute

@Serializable
object OnAirRegisterRoute
