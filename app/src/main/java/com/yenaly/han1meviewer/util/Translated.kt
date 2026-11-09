package com.yenaly.han1meviewer.util

import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.RouVideo.common.RouVideoTranslation

fun translatedTitle(text: String): String = RouVideoTranslation.title(text, Preferences.siteType)

fun translatedDescription(text: String): String =
    if (text.contains("http")) text else RouVideoTranslation.description(text, Preferences.siteType)

fun translatedComment(text: String): String = RouVideoTranslation.comment(text, Preferences.siteType)
