package com.yenaly.han1meviewer.logic.state

import androidx.annotation.IntDef
import kotlinx.serialization.Serializable

@Serializable
enum class DownloadState(@param:Mask val mask: Int) {

    Unknown(Mask.UNKNOWN),
    Queued(Mask.QUEUED),
    Downloading(Mask.DOWNLOADING),
    Paused(Mask.PAUSED),
    Finished(Mask.FINISHED),
    Failed(Mask.FAILED);

    companion object {
        const val STATE = "state"

        fun from(@Mask mask: Int): DownloadState = when (mask) {
            Mask.QUEUED -> Queued
            Mask.DOWNLOADING -> Downloading
            Mask.PAUSED -> Paused
            Mask.FINISHED -> Finished
            Mask.FAILED -> Failed
            else -> Unknown
        }
    }

    @IntDef(
        Mask.UNKNOWN,
        Mask.QUEUED,
        Mask.DOWNLOADING,
        Mask.PAUSED,
        Mask.FINISHED,
        Mask.FAILED
    )
    annotation class Mask {
        companion object {
            const val UNKNOWN = 0
            const val QUEUED = 1
            const val DOWNLOADING = 2
            const val PAUSED = 3
            const val FINISHED = 4
            const val FAILED = 5
        }
    }
}
