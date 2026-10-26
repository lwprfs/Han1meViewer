package com.yenaly.han1meviewer.OnAir.ui.gate

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import com.yenaly.han1meviewer.OnAir.data.remote.OnAirProbeFailure

sealed interface OnAirGateState {

    data object Initializing : OnAirGateState
    data object CheckingTor : OnAirGateState
    data object Probing : OnAirGateState
    data object Passed : OnAirGateState

    data class TorBlocked(
        val reason: String,
        val torFailure: TorFailureKind? = null,
    ) : OnAirGateState

    data class ContentError(
        val reason: String,
        val suggestSwitchSite: Boolean = false,
        val suggestWait: Boolean = false,
        val suggestRelogin: Boolean = false,
        val probeFailure: OnAirProbeFailure? = null,
    ) : OnAirGateState

    enum class TorFailureKind {
        Timeout,
        NetworkUnreachable,
        NonTorExit,
        Unknown,
    }

    companion object {
        val Saver: Saver<OnAirGateState, Any> = listSaver(
            save = { state ->
                when (state) {
                    Initializing -> listOf("I")
                    CheckingTor -> listOf("T")
                    Probing -> listOf("P")
                    Passed -> listOf("OK")
                    is TorBlocked -> listOf(
                        "TB",
                        state.reason,
                        state.torFailure?.name ?: "",
                    )
                    is ContentError -> listOf(
                        "CE",
                        state.reason,
                        state.suggestSwitchSite,
                        state.suggestWait,
                        state.suggestRelogin,
                        state.probeFailure?.name ?: "",
                    )
                }
            },
            restore = { list ->
                when (list[0] as String) {
                    "I" -> Initializing
                    "T" -> CheckingTor
                    "P" -> Probing
                    "OK" -> Passed
                    "TB" -> TorBlocked(
                        reason = list[1] as String,
                        torFailure = (list[2] as String).takeIf { it.isNotBlank() }
                            ?.let { runCatching { TorFailureKind.valueOf(it) }.getOrNull() },
                    )
                    "CE" -> ContentError(
                        reason = list[1] as String,
                        suggestSwitchSite = list[2] as Boolean,
                        suggestWait = list[3] as Boolean,
                        suggestRelogin = list[4] as Boolean,
                        probeFailure = (list[5] as String).takeIf { it.isNotBlank() }
                            ?.let { runCatching { OnAirProbeFailure.valueOf(it) }.getOrNull() },
                    )
                    else -> Initializing
                }
            },
        )
    }
}
