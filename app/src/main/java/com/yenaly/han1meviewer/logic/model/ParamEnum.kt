package com.yenaly.han1meviewer.logic.model

import com.yenaly.han1meviewer.EMPTY_STRING

enum class MyListType(val value: String) {
    FAV_VIDEO("likes"),
    WATCH_LATER("saves"),
    SUBSCRIPTION("SL")
}

enum class FavStatus(val value: String) {
    ADD_FAV(EMPTY_STRING),
    CANCEL_FAV("1")
}

enum class CommentPlace(val value: String) {
    COMMENT("comment"),
    CHILD_COMMENT("reply")
}
