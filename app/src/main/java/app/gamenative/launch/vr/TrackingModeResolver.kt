package app.gamenative.launch.vr

import app.gamenative.launch.LaunchRequest
import app.gamenative.launch.inspect.ExecutableIdentity

data class TrackingModeDecision(
    val mode: TrackingMode,
    val rationale: String,
    val isFallback: Boolean = false,
    val requiresPassthrough: Boolean = false
)

interface TrackingModeResolver {
    /**
     * Selects FLAT_3DOF, NATIVE_OPENXR_6DOF, or MODDED_6DOF and rejects ambiguous combinations.
     * If an expected 6DoF handshake fails or is invalid, returns to a clearly reported 
     * flat 3DoF launch only when the user/game profile permits fallback; otherwise fails.
     */
    fun resolveTrackingMode(
        request: LaunchRequest,
        executableIdentity: ExecutableIdentity,
        hasVrModManifest: Boolean,
        isNativeVrSupported: Boolean
    ): TrackingModeDecision
}
