package com.yenaly.han1meviewer.util

import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.RouVideo.common.RouVideoTranslation

/**
 * Shortcuts for the cross-site translation (English app language only). They return the
 * original text unless translation is enabled for the current site and that kind of text,
 * and the cached translation once it is available.
 */
fun translatedTitle(text: String): String = RouVideoTranslation.title(text, Preferences.siteType)

fun translatedDescription(text: String): String =
    if (text.contains("http")) text else RouVideoTranslation.description(text, Preferences.siteType)

fun translatedComment(text: String): String = RouVideoTranslation.comment(text, Preferences.siteType)
