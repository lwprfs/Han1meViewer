@file:Suppress("UnstableApiUsage")

import org.gradle.api.Project
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object Config {

    val Project.isRelease: Boolean
        get() = gradle.startParameter.taskNames.any { it.contains("Release") }

    object Version {

        const val DEBUG = "debug"
        const val RELEASE = "release"

        fun Project.createVersion(
            major: Int, minor: Int, patch: Int
        ): Pair<Int, String> {
            val source = this.source
            val versionCode: Int
            val versionName: String
            when (source) {
                DEBUG -> {
                    versionCode = 1
                    versionName = "$DEBUG+$versionCode"
                }

                else -> {
                    versionCode = LocalDateTime.now(Clock.systemUTC()).format(
                        DateTimeFormatter.ofPattern("yyMMddHH")
                    ).toInt()
                    versionName = "${major}.${minor}.${patch}-$source+$versionCode"
                }
            }
            return versionCode to versionName
        }

        val Project.source: String
            get() = System.getenv("HA1_VERSION_SOURCE") ?: kotlin.run {
                return if (isRelease) RELEASE else DEBUG
            }
    }

    val Project.lastCommitSha: String
        get() = providers.exec {
            commandLine = "git rev-parse --short=7 HEAD".split(' ')
        }.standardOutput.asText.get().trim()

    val thisYear: Int
        get() = LocalDateTime.now(Clock.system(ZoneId.of("UTC+8"))).year
}
