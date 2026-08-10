package app.gamenative.launch

/**
 * 18 structured failure categories for GameNativeXR launch errors.
 */
enum class LaunchFailureCategory(
    val title: String,
    val isRecoverable: Boolean
) {
    INSTALL_OR_EXECUTABLE_NOT_FOUND("Executable File Not Found", true),
    UNSUPPORTED_OR_UNKNOWN_ARCHITECTURE("Unsupported Executable Architecture", false),
    MISSING_TRANSLATION_COMPONENT("Missing Runtime Translation Component", true),
    INCOMPATIBLE_HARDWARE_PROFILE("Incompatible Headset Hardware Profile", false),
    MISSING_GRAPHICS_DRIVER_OR_DX_COMPONENT("Graphics Driver / DirectX Component Missing", true),
    STEAM_AUTH_OR_CLIENT_FAILURE("Steam Authentication or Client Error", true),
    CONTAINER_PREPARATION_FAILURE("Wine Container Preparation Failure", true),
    GUEST_PROCESS_START_FAILURE("Guest Process Execution Failed", true),
    GUEST_PROCESS_EXITED_BEFORE_FIRST_WINDOW("Guest Process Exited Early (No Window)", true),
    FIRST_WINDOW_APPEARED_BUT_STOPPED_RESPONDING("Guest Window Frozen or Unresponsive", true),
    NATIVE_XR_HOST_INITIALIZATION_FAILURE("Native XR Host Initialization Error", true),
    VR_RUNTIME_HANDSHAKE_TIMEOUT_OR_PROTOCOL_MISMATCH("VR Runtime Handshake Timeout", true),
    MOD_HASH_OR_MANIFEST_MISMATCH("VR Mod Verification Failed", true),
    HOOK_PLACEMENT_OR_ROLLBACK_FAILURE("VR Mod Hook Deployment Failure", true),
    JAVA_EXCEPTION_OR_CRASH("Internal Android Application Exception", false),
    NATIVE_CRASH("Native Library Crash (SIGSEGV/SIGABRT)", false),
    GUEST_CRASH("Windows Guest Executable Crash", true),
    ANDROID_PROCESS_DEATH("Android OS Process Termination (OOM/ANR)", false);

    companion object {
        /** Classifies process exit codes into structured failure categories. */
        fun fromExitCode(exitCode: Int): LaunchFailureCategory {
            return when (exitCode) {
                0 -> GUEST_PROCESS_EXITED_BEFORE_FIRST_WINDOW
                137, 9 -> ANDROID_PROCESS_DEATH // SIGKILL / OOM
                139, 11 -> NATIVE_CRASH // SIGSEGV
                134, 6 -> NATIVE_CRASH // SIGABRT
                else -> GUEST_CRASH
            }
        }

        /** Classifies arbitrary Throwables into structured failure categories. */
        fun fromThrowable(t: Throwable): LaunchFailureCategory {
            return when (t) {
                is LaunchFailureException -> t.category
                is java.io.FileNotFoundException -> INSTALL_OR_EXECUTABLE_NOT_FOUND
                is SecurityException -> CONTAINER_PREPARATION_FAILURE
                else -> JAVA_EXCEPTION_OR_CRASH
            }
        }
    }
}

/**
 * Base sealed class for all structured launch failure exceptions in GameNativeXR.
 */
sealed class LaunchFailureException(
    val failureCode: String,
    val category: LaunchFailureCategory,
    val userMessage: String,
    val technicalDetails: String,
    val remediationSuggestion: String,
    cause: Throwable? = null
) : Exception(userMessage, cause) {

    class ExecutableNotFound(path: String) : LaunchFailureException(
        failureCode = "INSTALL_EXECUTABLE_NOT_FOUND",
        category = LaunchFailureCategory.INSTALL_OR_EXECUTABLE_NOT_FOUND,
        userMessage = "Target executable file was not found.",
        technicalDetails = "Path does not exist on filesystem: $path",
        remediationSuggestion = "Verify game installation files or re-download the game."
    )

    class UnsupportedArchitecture(arch: String, path: String) : LaunchFailureException(
        failureCode = "EXECUTABLE_ARCHITECTURE_UNSUPPORTED",
        category = LaunchFailureCategory.UNSUPPORTED_OR_UNKNOWN_ARCHITECTURE,
        userMessage = "Executable architecture is not supported.",
        technicalDetails = "File $path reported unsupported PE machine architecture: $arch",
        remediationSuggestion = "GameNativeXR requires 32-bit x86 or 64-bit x64 Windows executables."
    )

    class MissingComponent(componentName: String) : LaunchFailureException(
        failureCode = "RUNTIME_COMPONENT_MISSING",
        category = LaunchFailureCategory.MISSING_TRANSLATION_COMPONENT,
        userMessage = "A required system component is missing.",
        technicalDetails = "Required runtime package not installed: $componentName",
        remediationSuggestion = "Open Container Settings and download missing component assets."
    )

    class EarlyProcessExit(exitCode: Int, stdoutTail: String) : LaunchFailureException(
        failureCode = "GUEST_EXITED_BEFORE_FIRST_WINDOW",
        category = LaunchFailureCategory.GUEST_PROCESS_EXITED_BEFORE_FIRST_WINDOW,
        userMessage = "Game process exited before opening a window.",
        technicalDetails = "Process returned exit code $exitCode. Output tail: $stdoutTail",
        remediationSuggestion = "Try changing DirectX wrapper or Wine version in game configuration."
    )

    class XrHandshakeTimeout(timeoutMs: Long, mode: String) : LaunchFailureException(
        failureCode = "XR_HANDSHAKE_TIMEOUT",
        category = LaunchFailureCategory.VR_RUNTIME_HANDSHAKE_TIMEOUT_OR_PROTOCOL_MISMATCH,
        userMessage = "VR session handshake timed out.",
        technicalDetails = "Timed out waiting for XR host handshake after ${timeoutMs}ms in mode $mode",
        remediationSuggestion = "Verify VR mod installation or launch in Flat 3DoF mode."
    )

    class JavaCrash(cause: Throwable) : LaunchFailureException(
        failureCode = "INTERNAL_JAVA_EXCEPTION",
        category = LaunchFailureCategory.JAVA_EXCEPTION_OR_CRASH,
        userMessage = "An unexpected error occurred in GameNativeXR.",
        technicalDetails = "Unhandled Java/Kotlin exception: ${cause.localizedMessage}\n${cause.stackTraceToString()}",
        remediationSuggestion = "Restart GameNativeXR and submit a diagnostic log report.",
        cause = cause
    )

    class InvalidHardware(detected: String) : LaunchFailureException(
        failureCode = "HARDWARE_PROFILE_UNSUPPORTED",
        category = LaunchFailureCategory.INCOMPATIBLE_HARDWARE_PROFILE,
        userMessage = "Device hardware is unsupported.",
        technicalDetails = "Detected device profile ($detected) falls below the minimum required specification (QUEST_2).",
        remediationSuggestion = "GameNativeXR requires a Meta Quest 2 or newer headset."
    )

    class InstallDirectoryNotFound(appId: String, candidates: List<String>) : LaunchFailureException(
        failureCode = "INSTALL_DIRECTORY_NOT_FOUND",
        category = LaunchFailureCategory.INSTALL_OR_EXECUTABLE_NOT_FOUND,
        userMessage = "The installed game directory could not be found.",
        technicalDetails = "No install candidate for app $appId is an existing directory: ${candidates.joinToString()}",
        remediationSuggestion = "Verify or repair the game installation, then select the correct install folder."
    )

    class InstallNotMounted(appId: String, detail: String) : LaunchFailureException(
        failureCode = "INSTALL_GUEST_MOUNT_MISSING",
        category = LaunchFailureCategory.INSTALL_OR_EXECUTABLE_NOT_FOUND,
        userMessage = "The selected game installation is not available to Wine.",
        technicalDetails = "App $appId has no usable Wine drive mapping: $detail",
        remediationSuggestion = "Repair the game container drive mapping, then try the launch again."
    )

    class ConflictingInstallCandidates(appId: String, candidates: List<String>) : LaunchFailureException(
        failureCode = "INSTALL_CANDIDATES_CONFLICT",
        category = LaunchFailureCategory.INSTALL_OR_EXECUTABLE_NOT_FOUND,
        userMessage = "More than one valid game installation was found.",
        technicalDetails = "App $appId has conflicting valid install roots: ${candidates.joinToString()}",
        remediationSuggestion = "Choose the intended installation before launching."
    )

    class InvalidInstallPath(path: String, reason: String) : LaunchFailureException(
        failureCode = "INSTALL_PATH_INVALID",
        category = LaunchFailureCategory.INSTALL_OR_EXECUTABLE_NOT_FOUND,
        userMessage = "The selected executable path is invalid.",
        technicalDetails = "Rejected install-relative path '$path': $reason",
        remediationSuggestion = "Select an executable contained by the installed game directory."
    )

    class DuplicateLaunch(activeLaunchId: String, requestedLaunchId: String) : LaunchFailureException(
        failureCode = "LAUNCH_ALREADY_ACTIVE",
        category = LaunchFailureCategory.GUEST_PROCESS_START_FAILURE,
        userMessage = "A game launch is already in progress.",
        technicalDetails = "Rejected launch $requestedLaunchId while $activeLaunchId is active",
        remediationSuggestion = "Wait for the current launch to finish or cancel it before trying again."
    )

    class PreinstallFailed(commandId: String, exitCode: Int) : LaunchFailureException(
        failureCode = "PREINSTALL_COMMAND_FAILED",
        category = LaunchFailureCategory.CONTAINER_PREPARATION_FAILURE,
        userMessage = "A required prerequisite installer failed.",
        technicalDetails = "Preinstall command '$commandId' returned exit code $exitCode",
        remediationSuggestion = "Retry the launch or repair the selected Wine container."
    )

    class EnvironmentSetupFailed(cause: Throwable) : LaunchFailureException(
        failureCode = "ENVIRONMENT_SETUP_FAILED",
        category = LaunchFailureCategory.CONTAINER_PREPARATION_FAILURE,
        userMessage = "The game environment could not be started.",
        technicalDetails = "Environment backend failed: ${cause.message}",
        remediationSuggestion = "Verify the selected Wine, graphics, and translation components.",
        cause = cause
    )

    class CleanupFailed(cause: Throwable) : LaunchFailureException(
        failureCode = "LAUNCH_CLEANUP_FAILED",
        category = LaunchFailureCategory.CONTAINER_PREPARATION_FAILURE,
        userMessage = "The previous game session could not be cleaned up completely.",
        technicalDetails = "Launch cleanup failed: ${cause.message}",
        remediationSuggestion = "Restart GameNativeXR before starting another game.",
        cause = cause
    )
}
