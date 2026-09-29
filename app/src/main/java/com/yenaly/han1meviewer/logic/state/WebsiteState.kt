package com.yenaly.han1meviewer.logic.state

sealed class WebsiteState<out T> {
    data class Success<out T>(val info: T) : WebsiteState<T>()
    data object Loading : WebsiteState<Nothing>()
    data class Error(val throwable: Throwable) : WebsiteState<Nothing>()
}
