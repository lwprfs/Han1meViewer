package com.yenaly.han1meviewer.MissAV.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.ui.component.SiteBackButton
import com.yenaly.han1meviewer.ui.component.SiteTopAppBar

data class MissAvSubtitleSourceOption(
    val value: String,
    val label: String,
    val description: String,
)

data class MissAvSubtitleLanguage(val code: String, val label: String)

object MissAvSubtitleSettingsRepo {
    const val SOURCE_SUBTITLECAT = "subtitlecat"
    const val SOURCE_SUBDOG = "subdog"

    val sources = listOf(
        MissAvSubtitleSourceOption(
            SOURCE_SUBTITLECAT,
            "SubtitleCat (English)",
            "Search subtitlecat.com and download the English subtitle",
        ),
        MissAvSubtitleSourceOption(
            SOURCE_SUBDOG,
            "Sub-Dog (Chinese)",
            "Chinese subtitles from the Sub-Dog (字幕狗) library",
        ),
    )

    val subtitleCatLanguages = listOf(
        MissAvSubtitleLanguage("en", "English"),
        MissAvSubtitleLanguage("zh-CN", "Chinese (Simplified)"),
        MissAvSubtitleLanguage("zh-TW", "Chinese (Traditional)"),
        MissAvSubtitleLanguage("ja", "Japanese"),
        MissAvSubtitleLanguage("ko", "Korean"),
        MissAvSubtitleLanguage("es", "Spanish"),
        MissAvSubtitleLanguage("fr", "French"),
        MissAvSubtitleLanguage("de", "German"),
        MissAvSubtitleLanguage("ru", "Russian"),
        MissAvSubtitleLanguage("pt-BR", "Portuguese (Brazil)"),
        MissAvSubtitleLanguage("ar", "Arabic"),
        MissAvSubtitleLanguage("id", "Indonesian"),
        MissAvSubtitleLanguage("th", "Thai"),
        MissAvSubtitleLanguage("vi", "Vietnamese"),
        MissAvSubtitleLanguage("tr", "Turkish"),
        MissAvSubtitleLanguage("hi", "Hindi"),
        MissAvSubtitleLanguage("af", "Afrikaans"),
        MissAvSubtitleLanguage("ak", "Akan"),
        MissAvSubtitleLanguage("sq", "Albanian"),
        MissAvSubtitleLanguage("am", "Amharic"),
        MissAvSubtitleLanguage("hy", "Armenian"),
        MissAvSubtitleLanguage("az", "Azerbaijani"),
        MissAvSubtitleLanguage("eu", "Basque"),
        MissAvSubtitleLanguage("be", "Belarusian"),
        MissAvSubtitleLanguage("bem", "Bemba"),
        MissAvSubtitleLanguage("bn", "Bengali"),
        MissAvSubtitleLanguage("bh", "Bihari"),
        MissAvSubtitleLanguage("bs", "Bosnian"),
        MissAvSubtitleLanguage("br", "Breton"),
        MissAvSubtitleLanguage("bg", "Bulgarian"),
        MissAvSubtitleLanguage("km", "Cambodian"),
        MissAvSubtitleLanguage("ca", "Catalan"),
        MissAvSubtitleLanguage("ceb", "Cebuano"),
        MissAvSubtitleLanguage("chr", "Cherokee"),
        MissAvSubtitleLanguage("ny", "Chichewa"),
        MissAvSubtitleLanguage("co", "Corsican"),
        MissAvSubtitleLanguage("hr", "Croatian"),
        MissAvSubtitleLanguage("cs", "Czech"),
        MissAvSubtitleLanguage("da", "Danish"),
        MissAvSubtitleLanguage("nl", "Dutch"),
        MissAvSubtitleLanguage("eo", "Esperanto"),
        MissAvSubtitleLanguage("et", "Estonian"),
        MissAvSubtitleLanguage("ee", "Ewe"),
        MissAvSubtitleLanguage("fo", "Faroese"),
        MissAvSubtitleLanguage("tl", "Filipino"),
        MissAvSubtitleLanguage("fi", "Finnish"),
        MissAvSubtitleLanguage("fy", "Frisian"),
        MissAvSubtitleLanguage("gaa", "Ga"),
        MissAvSubtitleLanguage("gl", "Galician"),
        MissAvSubtitleLanguage("ka", "Georgian"),
        MissAvSubtitleLanguage("el", "Greek"),
        MissAvSubtitleLanguage("gn", "Guarani"),
        MissAvSubtitleLanguage("gu", "Gujarati"),
        MissAvSubtitleLanguage("ht", "Haitian Creole"),
        MissAvSubtitleLanguage("ha", "Hausa"),
        MissAvSubtitleLanguage("haw", "Hawaiian"),
        MissAvSubtitleLanguage("iw", "Hebrew"),
        MissAvSubtitleLanguage("hmn", "Hmong"),
        MissAvSubtitleLanguage("hu", "Hungarian"),
        MissAvSubtitleLanguage("is", "Icelandic"),
        MissAvSubtitleLanguage("ig", "Igbo"),
        MissAvSubtitleLanguage("ia", "Interlingua"),
        MissAvSubtitleLanguage("ga", "Irish"),
        MissAvSubtitleLanguage("it", "Italian"),
        MissAvSubtitleLanguage("jw", "Javanese"),
        MissAvSubtitleLanguage("kn", "Kannada"),
        MissAvSubtitleLanguage("kk", "Kazakh"),
        MissAvSubtitleLanguage("rw", "Kinyarwanda"),
        MissAvSubtitleLanguage("rn", "Kirundi"),
        MissAvSubtitleLanguage("kg", "Kongo"),
        MissAvSubtitleLanguage("kri", "Krio (Sierra Leone)"),
        MissAvSubtitleLanguage("ku", "Kurdish"),
        MissAvSubtitleLanguage("ckb", "Kurdish (Soranî)"),
        MissAvSubtitleLanguage("ky", "Kyrgyz"),
        MissAvSubtitleLanguage("lo", "Laothian"),
        MissAvSubtitleLanguage("la", "Latin"),
        MissAvSubtitleLanguage("lv", "Latvian"),
        MissAvSubtitleLanguage("ln", "Lingala"),
        MissAvSubtitleLanguage("lt", "Lithuanian"),
        MissAvSubtitleLanguage("loz", "Lozi"),
        MissAvSubtitleLanguage("lg", "Luganda"),
        MissAvSubtitleLanguage("ach", "Luo"),
        MissAvSubtitleLanguage("lb", "Luxembourgish"),
        MissAvSubtitleLanguage("mk", "Macedonian"),
        MissAvSubtitleLanguage("mg", "Malagasy"),
        MissAvSubtitleLanguage("ms", "Malay"),
        MissAvSubtitleLanguage("ml", "Malayalam"),
        MissAvSubtitleLanguage("mt", "Maltese"),
        MissAvSubtitleLanguage("mi", "Maori"),
        MissAvSubtitleLanguage("mr", "Marathi"),
        MissAvSubtitleLanguage("mfe", "Mauritian Creole"),
        MissAvSubtitleLanguage("mo", "Moldavian"),
        MissAvSubtitleLanguage("mn", "Mongolian"),
        MissAvSubtitleLanguage("my", "Myanmar (Burmese)"),
        MissAvSubtitleLanguage("sr-ME", "Montenegrin"),
        MissAvSubtitleLanguage("ne", "Nepali"),
        MissAvSubtitleLanguage("pcm", "Nigerian Pidgin"),
        MissAvSubtitleLanguage("nso", "Northern Sotho"),
        MissAvSubtitleLanguage("no", "Norwegian"),
        MissAvSubtitleLanguage("nn", "Norwegian (Nynorsk)"),
        MissAvSubtitleLanguage("oc", "Occitan"),
        MissAvSubtitleLanguage("or", "Oriya"),
        MissAvSubtitleLanguage("om", "Oromo"),
        MissAvSubtitleLanguage("ps", "Pashto"),
        MissAvSubtitleLanguage("fa", "Persian"),
        MissAvSubtitleLanguage("pl", "Polish"),
        MissAvSubtitleLanguage("pt", "Portuguese (Portugal)"),
        MissAvSubtitleLanguage("pa", "Punjabi"),
        MissAvSubtitleLanguage("qu", "Quechua"),
        MissAvSubtitleLanguage("ro", "Romanian"),
        MissAvSubtitleLanguage("rm", "Romansh"),
        MissAvSubtitleLanguage("nyn", "Runyakitara"),
        MissAvSubtitleLanguage("sm", "Samoan"),
        MissAvSubtitleLanguage("gd", "Scots Gaelic"),
        MissAvSubtitleLanguage("sr", "Serbian"),
        MissAvSubtitleLanguage("sh", "Serbo-Croatian"),
        MissAvSubtitleLanguage("st", "Sesotho"),
        MissAvSubtitleLanguage("tn", "Setswana"),
        MissAvSubtitleLanguage("crs", "Seychellois Creole"),
        MissAvSubtitleLanguage("sn", "Shona"),
        MissAvSubtitleLanguage("sd", "Sindhi"),
        MissAvSubtitleLanguage("si", "Sinhalese"),
        MissAvSubtitleLanguage("sk", "Slovak"),
        MissAvSubtitleLanguage("sl", "Slovenian"),
        MissAvSubtitleLanguage("so", "Somali"),
        MissAvSubtitleLanguage("es-419", "Spanish (Latin American)"),
        MissAvSubtitleLanguage("su", "Sundanese"),
        MissAvSubtitleLanguage("sw", "Swahili"),
        MissAvSubtitleLanguage("sv", "Swedish"),
        MissAvSubtitleLanguage("tg", "Tajik"),
        MissAvSubtitleLanguage("ta", "Tamil"),
        MissAvSubtitleLanguage("tt", "Tatar"),
        MissAvSubtitleLanguage("te", "Telugu"),
        MissAvSubtitleLanguage("ti", "Tigrinya"),
        MissAvSubtitleLanguage("to", "Tonga"),
        MissAvSubtitleLanguage("lua", "Tshiluba"),
        MissAvSubtitleLanguage("tum", "Tumbuka"),
        MissAvSubtitleLanguage("tk", "Turkmen"),
        MissAvSubtitleLanguage("tw", "Twi"),
        MissAvSubtitleLanguage("ug", "Uighur"),
        MissAvSubtitleLanguage("uk", "Ukrainian"),
        MissAvSubtitleLanguage("ur", "Urdu"),
        MissAvSubtitleLanguage("uz", "Uzbek"),
        MissAvSubtitleLanguage("cy", "Welsh"),
        MissAvSubtitleLanguage("wo", "Wolof"),
        MissAvSubtitleLanguage("xh", "Xhosa"),
        MissAvSubtitleLanguage("yi", "Yiddish"),
        MissAvSubtitleLanguage("yo", "Yoruba"),
        MissAvSubtitleLanguage("zu", "Zulu"),
    )

    fun subtitleCatLanguage(): String =
        subtitleCatLanguages.firstOrNull { it.code == Preferences.missAvSubtitleCatLanguage }?.code
            ?: "en"

    fun languageName(code: String): String =
        subtitleCatLanguages.firstOrNull { it.code == code }?.label ?: code

    fun source(): String =
        sources.firstOrNull { it.value == Preferences.missAvSubtitleSource }?.value
            ?: SOURCE_SUBTITLECAT
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MissAvSubtitleSettingsScreen(onBack: () -> Unit) {
    var source by remember { mutableStateOf(MissAvSubtitleSettingsRepo.source()) }
    var catLanguage by remember { mutableStateOf(MissAvSubtitleSettingsRepo.subtitleCatLanguage()) }
    var translate by remember { mutableStateOf(Preferences.missAvSubtitleCatTranslate) }

    Scaffold(
        topBar = {
            SiteTopAppBar(
                title = { Text("Video Subtitle") },
                navigationIcon = { SiteBackButton(onClick = onBack) },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionLabel("Subtitle source")
            MissAvSubtitleSettingsRepo.sources.forEach { option ->
                OptionRow(
                    title = option.label,
                    description = option.description,
                    selected = source == option.value,
                    onClick = {
                        source = option.value
                        Preferences.missAvSubtitleSource = option.value
                    },
                )
            }

            if (source == MissAvSubtitleSettingsRepo.SOURCE_SUBTITLECAT) {
                SectionLabel("SubtitleCat language")
                SwitchRow(
                    title = "Translate if not available",
                    description = "When SubtitleCat has no subtitle in this language, translate it " +
                        "with Google Translate from the original subtitle",
                    checked = translate,
                    onCheckedChange = {
                        translate = it
                        Preferences.missAvSubtitleCatTranslate = it
                    },
                )
                MissAvSubtitleSettingsRepo.subtitleCatLanguages.forEach { lang ->
                    OptionRow(
                        title = lang.label,
                        description = null,
                        selected = catLanguage == lang.code,
                        onClick = {
                            catLanguage = lang.code
                            Preferences.missAvSubtitleCatLanguage = lang.code
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun OptionRow(
    title: String,
    description: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (selected) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
