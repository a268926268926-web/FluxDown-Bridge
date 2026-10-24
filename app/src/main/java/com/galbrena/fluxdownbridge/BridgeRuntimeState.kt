package com.galbrena.fluxdownbridge

internal sealed interface BridgeRuntimeState {
    data object Stopped : BridgeRuntimeState
    data object Starting : BridgeRuntimeState
    data object Running : BridgeRuntimeState
    data class Failed(val message: String) : BridgeRuntimeState

    val active: Boolean
        get() = this == Starting || this == Running
}
