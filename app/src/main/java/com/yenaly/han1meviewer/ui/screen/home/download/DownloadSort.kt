package com.yenaly.han1meviewer.ui.screen.home.download

import androidx.annotation.StringRes
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.logic.entity.download.VideoWithCategories
import java.text.Collator
import java.util.Locale

enum class DownloadSort(@StringRes val labelRes: Int) {

    DATE_DESC(R.string.sort_by_date_descending),

    DATE_ASC(R.string.sort_by_date_ascending),

    FIRST_LETTER_ASC(R.string.sort_by_alphabet_ascending),

    FIRST_LETTER_DESC(R.string.sort_by_alphabet_descending),
}

fun downloadSortComparator(sort: DownloadSort): Comparator<VideoWithCategories> = when (sort) {
    DownloadSort.DATE_DESC -> compareByDescending { it.video.addDate }
    DownloadSort.DATE_ASC -> compareBy { it.video.addDate }
    DownloadSort.FIRST_LETTER_ASC -> firstLetterComparator()
    DownloadSort.FIRST_LETTER_DESC -> firstLetterComparator().reversed()
}

private fun firstLetterComparator(): Comparator<VideoWithCategories> {
    val collator = Collator.getInstance(Locale.CHINESE).apply {
        strength = Collator.PRIMARY
    }
    return Comparator { a, b ->
        val compared = collator.compare(sortKey(a.video.title), sortKey(b.video.title))
        if (compared != 0) compared else a.video.title.compareTo(b.video.title)
    }
}

private fun sortKey(title: String): String {
    val sb = StringBuilder(title.length)
    for (ch in title) {
        when {
            ch == '\u3000' -> sb.append(' ')
            ch in '\uFF01'..'\uFF5E' -> sb.append((ch.code - 0xFEE0).toChar())
            else -> sb.append(KANA_TO_ROMAJI[ch] ?: ch)
        }
    }
    return sb.toString()
}

private val KANA_TO_ROMAJI: Map<Char, String> = buildMap {
    val hiraganaToRomaji = mapOf(
        'あ' to "a", 'い' to "i", 'う' to "u", 'え' to "e", 'お' to "o",
        'か' to "ka", 'き' to "ki", 'く' to "ku", 'け' to "ke", 'こ' to "ko",
        'さ' to "sa", 'し' to "shi", 'す' to "su", 'せ' to "se", 'そ' to "so",
        'た' to "ta", 'ち' to "chi", 'つ' to "tsu", 'て' to "te", 'と' to "to",
        'な' to "na", 'に' to "ni", 'ぬ' to "nu", 'ね' to "ne", 'の' to "no",
        'は' to "ha", 'ひ' to "hi", 'ふ' to "fu", 'へ' to "he", 'ほ' to "ho",
        'ま' to "ma", 'み' to "mi", 'む' to "mu", 'め' to "me", 'も' to "mo",
        'や' to "ya", 'ゆ' to "yu", 'よ' to "yo",
        'ら' to "ra", 'り' to "ri", 'る' to "ru", 'れ' to "re", 'ろ' to "ro",
        'わ' to "wa", 'を' to "wo", 'ん' to "n",

        'が' to "ga", 'ぎ' to "gi", 'ぐ' to "gu", 'げ' to "ge", 'ご' to "go",
        'ざ' to "za", 'じ' to "ji", 'ず' to "zu", 'ぜ' to "ze", 'ぞ' to "zo",
        'だ' to "da", 'ぢ' to "di", 'づ' to "du", 'で' to "de", 'ど' to "do",
        'ば' to "ba", 'び' to "bi", 'ぶ' to "bu", 'べ' to "be", 'ぼ' to "bo",

        'ぱ' to "pa", 'ぴ' to "pi", 'ぷ' to "pu", 'ぺ' to "pe", 'ぽ' to "po",

        'ぁ' to "a", 'ぃ' to "i", 'ぅ' to "u", 'ぇ' to "e", 'ぉ' to "o",
        'っ' to "tsu", 'ゃ' to "ya", 'ゅ' to "yu", 'ょ' to "yo", 'ゎ' to "wa",

        'ゔ' to "vu",
    )
    for ((hiragana, romaji) in hiraganaToRomaji) {
        put(hiragana, romaji)
        put((hiragana.code + 0x60).toChar(), romaji)
    }

    put('ヵ', "ka")
    put('ヶ', "ke")

    put('ー', "")
}
