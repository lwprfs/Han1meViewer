package com.yenaly.han1meviewer

import kotlinx.serialization.Serializable
import okhttp3.MediaType.Companion.toMediaTypeOrNull

typealias ResolutionLinkMap = LinkedHashMap<String, HanimeLink>

class HanimeResolution {

    private val resArray = arrayOfNulls<Pair<String, HanimeLink>>(5)

    companion object {

        const val RES_1080P = "1080P"
        const val RES_720P = "720P"
        const val RES_480P = "480P"
        const val RES_240P = "240P"
        const val RES_UNKNOWN = "Unknown"
    }

    fun parseResolution(resString: String?, resLink: String, type: String? = null) {
        val mediaType = type?.toMediaTypeOrNull()?.takeIf {
            it.type.equals("video", ignoreCase = true)
        }
        val link = HanimeLink(resLink, mediaType?.subtype)
        when (resString) {
            RES_1080P -> resArray[0] = RES_1080P to link
            RES_720P -> resArray[1] = RES_720P to link
            RES_480P -> resArray[2] = RES_480P to link
            RES_240P -> resArray[3] = RES_240P to link
            null -> resArray[4] = RES_UNKNOWN to link
        }
    }

    fun toResolutionLinkMap(): ResolutionLinkMap {
        return resArray.filterNotNull().toMap(linkedMapOf())
    }
}

@Serializable
data class HanimeLink(
    val link: String,
    val subtype: String?,
) {
    val suffix: String
        get() = when (subtype?.lowercase()) {
            "mp4" -> "mp4"
            "mpeg" -> "mpeg"
            "x-msvideo" -> "avi"
            "3gpp" -> "3gp"
            "3gpp2" -> "3g2"
            "ogg" -> "ogv"
            "mp2t" -> "ts"
            "webm" -> "webm"
            else -> HFileManager.DEF_VIDEO_TYPE
        }
}
