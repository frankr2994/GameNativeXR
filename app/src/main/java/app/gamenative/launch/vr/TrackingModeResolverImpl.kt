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
        // We currently do not have a guest OpenXR/OpenVR runtime bridge implemented.
        // Therefore, even if the executable imports OpenXR or requests a VR mod,
        // we cannot actually provide 6DOF tracking yet.
        // We must safely default to FLAT_3DOF and defer the decision rather than
        // returning an unsupported 6DOF mode or crashing with IllegalStateException.

        val rationale = buildString {
            append("No guest OpenXR/OpenVR runtime bridge is available in this environment. ")
            if (hasVrModManifest) append("VR mod manifest was requested but cannot be fulfilled. ")
            if (isNativeVrSupported) append("Executable imports OpenXR/OpenVR but native injection is unsupported. ")
            append("Defaulting to flat virtual screen.")
        }.trim()

        return TrackingModeDecision(
            mode = TrackingMode.FLAT_3DOF,
            rationale = rationale,
            isFallback = true
        )
    }
}
