package app.gamenative.launch

import app.gamenative.data.GameSource
import java.io.File

/** Requested tracking / presentation mode for a launch session. */
enum class RequestedLaunchMode {
    AUTOMATIC,  // Auto-resolve best available mode (Native VR -> Modded VR -> Flat 3DoF)
    FLAT_ONLY,  // Force flat-screen presentation with 3DoF head orientation
    NATIVE_VR,  // Require native OpenXR/OpenVR 6DoF tracking
    MODDED_VR   // Require active VR mod package hook execution
}

/**
 * Immutable launch request submitted to [GameLaunchCoordinator].
 */
data class LaunchRequest(
    val launchId: String,
    val sessionId: String,
    val appId: String,
    val gameSource: GameSource,
    val exeRelativePath: String,
    val containerPath: String,
    val requestedMode: RequestedLaunchMode = RequestedLaunchMode.AUTOMATIC,
    val isOffline: Boolean = false,
    val isDiagnosticLaunch: Boolean = false,
    val customExecArgs: String? = null,
    val timestampMs: Long = System.currentTimeMillis()
) {
    init {
        require(launchId.isNotBlank()) { "launchId must not be blank" }
        require(sessionId.isNotBlank()) { "sessionId must not be blank" }
        require(appId.isNotBlank()) { "appId must not be blank" }
        require(exeRelativePath.isNotBlank()) { "exeRelativePath must not be blank" }
        require(!File(exeRelativePath).isAbsolute && !exeRelativePath.startsWith("/") && !exeRelativePath.startsWith("\\")) {
            "exeRelativePath must not be an absolute path: $exeRelativePath"
        }
        require(!exeRelativePath.contains("..")) { "exeRelativePath must not contain relative path escape ('..'): $exeRelativePath" }
    }

    /** Helper to format a redacted diagnostic summary of this request. */
    fun toDiagnosticSummary(): String {
        return "LaunchRequest(launchId='$launchId', appId='$appId', source=$gameSource, mode=$requestedMode, diag=$isDiagnosticLaunch)"
    }
}
