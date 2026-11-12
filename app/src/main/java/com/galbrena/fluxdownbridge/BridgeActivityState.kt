package com.galbrena.fluxdownbridge

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal data class BridgeActivitySnapshot(
    val lastRequestAtMillis: Long? = null,
    val lastResult: BridgeDispatchResult? = null,
)

internal data class BridgeDispatchResult(
    val atMillis: Long,
    val outcome: BridgeDispatchOutcome,
    val sent: Int = 0,
    val total: Int = 0,
)

internal enum class BridgeDispatchOutcome {
    SENT,
    AUTH_FAILED,
    INVALID_REQUEST,
    LAUNCH_FAILED,
}

/** Latest process-local activity only; never retains URLs, tokens or launcher output. */
internal object BridgeActivityState {
    private val mutableSnapshot = MutableStateFlow(BridgeActivitySnapshot())
    val snapshot: StateFlow<BridgeActivitySnapshot> = mutableSnapshot.asStateFlow()

    fun requestReceived() {
        mutableSnapshot.update { it.copy(lastRequestAtMillis = System.currentTimeMillis()) }
    }

    /** SENT means the handoff command succeeded, not that FluxDown created a download. */
    fun dispatchFinished(outcome: BridgeDispatchOutcome, sent: Int = 0, total: Int = 0) {
        mutableSnapshot.update {
            it.copy(lastResult = BridgeDispatchResult(System.currentTimeMillis(), outcome, sent, total))
        }
    }

    fun reset() {
        mutableSnapshot.value = BridgeActivitySnapshot()
    }
}
