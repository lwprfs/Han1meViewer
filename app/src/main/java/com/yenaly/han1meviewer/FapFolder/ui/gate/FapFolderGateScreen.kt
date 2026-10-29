package com.yenaly.han1meviewer.FapFolder.ui.gate

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver

sealed interface FapFolderGateState {

    data object Initializing : FapFolderGateState
    data object Probing : FapFolderGateState
    data object Passed : FapFolderGateState

    data class ContentError(
        val reason: String,
        val suggestSwitchSite: Boolean = false,
        val suggestWait: Boolean = false,
        val suggestRelogin: Boolean = false,
    ) : FapFolderGateState

    companion object {
        val Saver: Saver<FapFolderGateState, Any> = listSaver(
            save = { state ->
                when (state) {
                    Initializing -> listOf("I")
                    Probing -> listOf("P")
                    Passed -> listOf("OK")
                    is ContentError -> listOf(
                        "CE",
                        state.reason,
                        state.suggestSwitchSite,
                        state.suggestWait,
                        state.suggestRelogin,
                    )
                }
            },
            restore = { list ->
                when (list[0] as String) {
                    "I" -> Initializing
                    "P" -> Probing
                    "OK" -> Passed
                    "CE" -> ContentError(
                        reason = list[1] as String,
                        suggestSwitchSite = list[2] as Boolean,
                        suggestWait = list[3] as Boolean,
                        suggestRelogin = list[4] as Boolean,
                    )
                    else -> Initializing
                }
            },
        )
    }
}
