package app.gamenative.launch.vr

import app.gamenative.launch.LaunchRequest
import app.gamenative.launch.inspect.ExecutableIdentity

class TrackingModeResolverImpl : TrackingModeResolver {
    override fun resolveTrackingMode(
        request: LaunchRequest,
        executableIdentity: ExecutableIdentity,
        hasVrModManifest: Boolean,
        isNativeVrSupported: Boolean
    ): TrackingModeDecision {
        // "rejects ambiguous combinations"
        if (hasVrModManifest && isNativeVrSupported) {
            // A title cannot simultaneously use an external mod manifest AND native VR injection reliably
            // without user disambiguation.
            throw IllegalStateException("Ambiguous tracking request: both VR Mod and Native VR are supported/active.")
        }

        if (hasVrModManifest) {
            return TrackingModeDecision(
                mode = TrackingMode.MODDED_6DOF,
                rationale = "External VR mod manifest is active for this executable."
            )
        }

        if (isNativeVrSupported) {
            return TrackingModeDecision(
                mode = TrackingMode.NATIVE_OPENXR_6DOF,
                rationale = "Executable supports native VR rendering and valid runtime adapter is available."
            )
        }

        // Fallback for every title without a validated native VR adapter or compatible active VR mod
        return TrackingModeDecision(
            mode = TrackingMode.FLAT_3DOF,
            rationale = "No VR mod or native VR support detected. Defaulting to flat virtual screen.",
            isFallback = true
        )
    }
}
