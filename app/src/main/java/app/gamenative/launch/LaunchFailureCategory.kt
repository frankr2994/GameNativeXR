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
    val category: LaunchFailureCategory,
    val userMessage: String,
    val technicalDetails: String,
    val remediationSuggestion: String,
    cause: Throwable? = null
) : Exception(userMessage, cause) {

    class ExecutableNotFound(path: String) : LaunchFailureException(
        category = LaunchFailureCategory.INSTALL_OR_EXECUTABLE_NOT_FOUND,
        userMessage = "Target executable file was not found.",
        technicalDetails = "Path does not exist on filesystem: $path",
        remediationSuggestion = "Verify game installation files or re-download the game."
    )

    class UnsupportedArchitecture(arch: String, path: String) : LaunchFailureException(
        category = LaunchFailureCategory.UNSUPPORTED_OR_UNKNOWN_ARCHITECTURE,
        userMessage = "Executable architecture is not supported.",
        technicalDetails = "File $path reported unsupported PE machine architecture: $arch",
        remediationSuggestion = "GameNativeXR requires 32-bit x86 or 64-bit x64 Windows executables."
    )

    class MissingComponent(componentName: String) : LaunchFailureException(
        category = LaunchFailureCategory.MISSING_TRANSLATION_COMPONENT,
        userMessage = "A required system component is missing.",
        technicalDetails = "Required runtime package not installed: $componentName",
        remediationSuggestion = "Open Container Settings and download missing component assets."
    )

    class EarlyProcessExit(exitCode: Int, stdoutTail: String) : LaunchFailureException(
        category = LaunchFailureCategory.GUEST_PROCESS_EXITED_BEFORE_FIRST_WINDOW,
        userMessage = "Game process exited before opening a window.",
        technicalDetails = "Process returned exit code $exitCode. Output tail: $stdoutTail",
        remediationSuggestion = "Try changing DirectX wrapper or Wine version in game configuration."
    )

    class XrHandshakeTimeout(timeoutMs: Long, mode: String) : LaunchFailureException(
        category = LaunchFailureCategory.VR_RUNTIME_HANDSHAKE_TIMEOUT_OR_PROTOCOL_MISMATCH,
        userMessage = "VR session handshake timed out.",
        technicalDetails = "Timed out waiting for XR host handshake after ${timeoutMs}ms in mode $mode",
        remediationSuggestion = "Verify VR mod installation or launch in Flat 3DoF mode."
    )

    class JavaCrash(cause: Throwable) : LaunchFailureException(
        category = LaunchFailureCategory.JAVA_EXCEPTION_OR_CRASH,
        userMessage = "An unexpected error occurred in GameNativeXR.",
        technicalDetails = "Unhandled Java/Kotlin exception: ${cause.localizedMessage}\n${cause.stackTraceToString()}",
        remediationSuggestion = "Restart GameNativeXR and submit a diagnostic log report.",
        cause = cause
    )

    class InvalidHardware(detected: String) : LaunchFailureException(
        category = LaunchFailureCategory.INCOMPATIBLE_HARDWARE_PROFILE,
        userMessage = "Device hardware is unsupported.",
        technicalDetails = "Detected device profile ($detected) falls below the minimum required specification (QUEST_2).",
        remediationSuggestion = "GameNativeXR requires a Meta Quest 2 or newer headset."
    )
}
