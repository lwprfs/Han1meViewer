package com.yenaly.han1meviewer.HentaiMama.common

import com.yenaly.han1meviewer.HentaiMama.data.remote.HentaiMamaNetwork
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object HentaiMamaUrlUtils {

    private val EPISODE_SLUG_REGEX =
        Regex(""".+-episode-\d+(-[\w-]+)?/?$""", RegexOption.IGNORE_CASE)

    private val KNOWN_HOSTS = arrayOf(
        "hentaimama.io",
        "hentaimama.com",
        "hentaimama.net",
    )

    fun normalizeEpisodeUrl(path: String): String {
        if (path.isBlank()) return path

        val base = HentaiMamaNetwork.baseUrl.trimEnd('/')

        if (path.startsWith("http://") || path.startsWith("https://")) {
            val uri = path.toHttpUrlOrNull() ?: return path
            if (KNOWN_HOSTS.none { uri.host.equals(it, ignoreCase = true) || uri.host.endsWith(".$it", ignoreCase = true) }) {
                return path
            }
            val segments = uri.pathSegments.filter { it.isNotBlank() }
            return when {
                segments.firstOrNull() == "episodes" -> {
                    val slug = segments.getOrNull(1) ?: return path
                    "$base/episodes/$slug/"
                }

                segments.firstOrNull() == "tvshows" -> {
                    val slug = segments.getOrNull(1) ?: return path
                    "$base/tvshows/$slug/"
                }

                EPISODE_SLUG_REGEX.matches(segments.lastOrNull().orEmpty()) -> {
                    "$base/episodes/${segments.last()}/"
                }

                else -> path
            }
        }

        return when {
            path.startsWith("/episodes/") -> "$base$path"
            path.startsWith("/tvshows/") -> "$base$path"
            path.startsWith("/") -> "$base$path"
            path.contains("/episodes/") -> HentaiMamaNetwork.normalizeUrl("/$path")
            EPISODE_SLUG_REGEX.matches(path) -> "$base/episodes/$path/"
            else -> "$base/episodes/$path/"
        }
    }

    fun normalizeSeriesUrl(path: String): String {
        if (path.isBlank()) return path

        val base = HentaiMamaNetwork.baseUrl.trimEnd('/')

        if (path.startsWith("http://") || path.startsWith("https://")) {
            val uri = path.toHttpUrlOrNull() ?: return path
            if (KNOWN_HOSTS.none { uri.host.equals(it, ignoreCase = true) || uri.host.endsWith(".$it", ignoreCase = true) }) {
                return path
            }
            val segments = uri.pathSegments.filter { it.isNotBlank() }
            return when {
                segments.firstOrNull() == "tvshows" -> {
                    val slug = segments.getOrNull(1) ?: return path
                    "$base/tvshows/$slug/"
                }

                segments.firstOrNull() == "episodes" -> {
                    val slug = segments.getOrNull(1)?.substringBeforeLast("-episode-")
                        ?: return path
                    if (slug.isBlank()) return path
                    "$base/tvshows/$slug/"
                }

                else -> path
            }
        }

        return when {
            path.startsWith("/tvshows/") -> "$base$path"
            path.startsWith("/episodes/") -> {
                val slug = path.removePrefix("/episodes/")
                    .trim('/')
                    .substringBeforeLast("-episode-")
                if (slug.isBlank()) "$base$path" else "$base/tvshows/$slug/"
            }

            path.startsWith("/") -> "$base$path"
            else -> "$base/tvshows/$path/"
        }
    }
}
