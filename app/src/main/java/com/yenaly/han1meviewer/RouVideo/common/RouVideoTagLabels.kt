package com.yenaly.han1meviewer.RouVideo.common

import com.yenaly.han1meviewer.RouVideo.common.RouVideoTranslation.Kind

object RouVideoTagLabels {

    private val STATIC: Map<String, String> = mapOf(

        "國產AV" to "Chinese AV",
        "探花" to "Tanhua",
        "自拍流出" to "Leaked Selfies",
        "OnlyFans" to "OnlyFans",
        "日本" to "Japan",
        "麻豆傳媒" to "Madou Media",
        "AI短劇" to "AI Short Drama",
        "中文字幕" to "Chinese Subtitles",
        "中文字幕版" to "Chinese Subtitles",

        "無碼" to "Uncensored",
        "有碼" to "Censored",
        "巨乳" to "Big Breasts",
        "熟女" to "Mature",
        "人妻" to "Housewife",
        "制服" to "Uniform",
        "偷拍" to "Hidden Camera",
        "素人" to "Amateur",
        "網紅" to "Influencer",
        "主播" to "Streamer",
        "直播" to "Live Stream",
        "劇情" to "Drama",
        "角色扮演" to "Cosplay",
        "自慰" to "Masturbation",
        "潮吹" to "Squirting",
        "中出" to "Creampie",
        "內射" to "Creampie",
        "顏射" to "Facial",
        "口交" to "Oral",
        "口爆" to "Oral Finish",
        "肛交" to "Anal",
        "多P" to "Group",
        "3P" to "Threesome",
        "群交" to "Orgy",
        "絲襪" to "Stockings",
        "美腿" to "Beautiful Legs",
        "美乳" to "Beautiful Breasts",
        "按摩" to "Massage",
        "空姐" to "Flight Attendant",
        "護士" to "Nurse",
        "老師" to "Teacher",
        "秘書" to "Secretary",
        "OL" to "Office Lady",
        "女僕" to "Maid",
        "旗袍" to "Cheongsam",
        "比基尼" to "Bikini",
        "泳裝" to "Swimsuit",
        "情侶" to "Couple",
        "夫妻" to "Married Couple",
        "約炮" to "Hookup",
        "酒店" to "Hotel",
        "外送" to "Escort",
        "足交" to "Footjob",
        "乳交" to "Titjob",
        "調教" to "BDSM",
        "捆綁" to "Bondage",
        "強制" to "Forced",
        "SM" to "SM",
        "高潮" to "Orgasm",
        "呻吟" to "Moaning",
        "對白" to "Dialogue",
        "國語對白" to "Mandarin Dialogue",
        "國語" to "Mandarin",
        "歐美" to "Western",
        "韓國" to "Korea",
        "台灣" to "Taiwan",
        "大陸" to "Mainland China",
        "香港" to "Hong Kong",
        "泰國" to "Thailand",
        "動漫" to "Anime",
        "里番" to "Hentai Anime",
        "短劇" to "Short Drama",
        "全部" to "All",
        "最新" to "Latest",
        "熱門" to "Popular",
        "推薦" to "Recommended",
    )

    fun label(tag: String): String {
        if (tag.isBlank() || !RouVideoTranslation.isActive(Kind.TAG)) return tag
        STATIC[tag]?.let { return it }
        return RouVideoTranslation.translate(Kind.TAG, tag)
    }

    fun matches(tag: String, needle: String): Boolean =
        tag.contains(needle, ignoreCase = true) ||
            label(tag).contains(needle, ignoreCase = true)
}
