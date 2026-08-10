package app.gamenative.launch

import app.gamenative.data.GameSource
import app.gamenative.launch.install.GameInstallResolutionRequest
import app.gamenative.launch.install.GameInstallCandidate
import app.gamenative.launch.install.InstallCandidateSource
import app.gamenative.launch.install.SelectedLaunchOption
import com.winlator.container.ContainerData
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
    @Deprecated("Use installResolution; container roots are not game install roots")
    val exeRelativePath: String = "",
    @Deprecated("Use typed install candidates; this legacy field is retained for migration tests")
    val containerPath: String = "",
    val installResolution: GameInstallResolutionRequest? = null,
    val userContainerConfig: ContainerData? = null,
    /**
     * Sparse per-game settings the user actually changed. Null preserves the legacy behavior of
     * treating [userContainerConfig] as a full override; an empty set means persisted defaults
     * must not shadow a resolved hardware profile.
     */
    val explicitContainerOverrideFields: Set<String>? = null,
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
        val selectedExecutable = installResolution?.selectedLaunchOption?.executableRelativePath ?: exeRelativePath
        require(selectedExecutable.isNotBlank()) { "installResolution executable path must not be blank" }
        require(!File(selectedExecutable).isAbsolute && !selectedExecutable.startsWith("/") && !selectedExecutable.startsWith("\\")) {
            "installResolution executable path must not be an absolute path: $selectedExecutable"
        }
        require(!selectedExecutable.contains("..")) { "installResolution executable path must not contain relative path escape ('..'): $selectedExecutable" }
        installResolution?.let {
            require(it.appId == appId) { "installResolution appId must match request appId" }
            require(it.gameSource == gameSource) { "installResolution gameSource must match request gameSource" }
        }
    }

    /** Helper to format a redacted diagnostic summary of this request. */
    fun toDiagnosticSummary(): String {
        return "LaunchRequest(launchId='$launchId', appId='$appId', source=$gameSource, mode=$requestedMode, explicitOverrideCount=${explicitContainerOverrideFields?.size ?: -1}, diag=$isDiagnosticLaunch)"
    }

    fun requireInstallResolution(): GameInstallResolutionRequest {
        installResolution?.let { return it }
        require(containerPath.isNotBlank()) { "A typed installResolution is required" }
        return GameInstallResolutionRequest(
            appId = appId,
            gameSource = gameSource,
            selectedLaunchOption = SelectedLaunchOption("legacy", exeRelativePath),
            candidates = listOf(
                GameInstallCandidate(
                    root = File(containerPath),
                    source = InstallCandidateSource.SAVED_CONTAINER_PATH,
                    guestDriveLetter = 'D',
                    description = "Legacy request migration candidate"
                )
            )
        )
    }
}
