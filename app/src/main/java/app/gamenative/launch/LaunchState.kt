package app.gamenative.launch

/**
 * Explicit 19-state launch lifecycle enum for GameNativeXR.
 * Represents every stage from initial launch request through resolution,
 * materialization, environment startup, execution, and cleanup.
 */
enum class LaunchState(
    val description: String,
    val stage: LaunchStage
) {
    // 1. Initial Request
    REQUEST_RECEIVED("Launch request received and validated", LaunchStage.PRE_LAUNCH),

    // 2-7. Inspection & Resolution
    INSTALL_RESOLVING("Resolving game installation directory and executable path", LaunchStage.RESOLUTION),
    EXECUTABLE_INSPECTING("Inspecting executable PE headers, architecture, SHA-256, and imported APIs", LaunchStage.RESOLUTION),
    DEVICE_DETECTING("Detecting Quest hardware characteristics and GPU features", LaunchStage.RESOLUTION),
    HARDWARE_PROFILE_RESOLVING("Resolving hardware execution profile for detected device", LaunchStage.RESOLUTION),
    COMPATIBILITY_RESOLVING("Evaluating game-specific compatibility profiles and overrides", LaunchStage.RESOLUTION),
    VR_CAPABILITY_RESOLVING("Determining target VR tracking mode and runtime capabilities", LaunchStage.RESOLUTION),

    // 8-10. Validation & File Preparation
    RUNTIME_VALIDATING("Validating required translation components, Wine runtime, and drivers", LaunchStage.VALIDATION),
    MOD_PLAN_VALIDATING("Validating VR mod manifests, hash matches, and hook strategy", LaunchStage.VALIDATION),
    FILES_MATERIALIZING("Deploying mod files, DLL overrides, and configuration assets", LaunchStage.MATERIALIZATION),

    // 11-14. Environment & Process Startup
    STEAM_PREPARING("Configuring Steam client state, auth tokens, and app launch commands", LaunchStage.PREPARATION),
    CONTAINER_PREPARING("Preparing Wine container, system files, registry entries, and environment variables", LaunchStage.PREPARATION),
    ENVIRONMENT_STARTING("Starting XServer, audio, shared memory, and launcher components", LaunchStage.EXECUTION_START),
    GUEST_PROCESS_STARTING("Spawning guest Wine/Proton program launcher process", LaunchStage.EXECUTION_START),

    // 15-16. Execution & Monitoring
    WINDOW_OR_XR_HANDSHAKE_WAITING("Waiting for first guest X11 window or XR host handshake", LaunchStage.ACTIVE_EXECUTION),
    RUNNING("Game process running and active", LaunchStage.ACTIVE_EXECUTION),

    // 17-20. Teardown & Terminal States
    STOPPING("Graceful shutdown or cancellation requested", LaunchStage.TEARDOWN),
    CLEANUP("Releasing process resources, temporary files, and restoring input state", LaunchStage.TEARDOWN),
    COMPLETED("Launch session completed successfully and process exited cleanly", LaunchStage.TERMINAL),
    FAILED("Launch failed or terminated with error", LaunchStage.TERMINAL);

    /** Stage category grouping for UI rendering and diagnostic summaries. */
    enum class LaunchStage {
        PRE_LAUNCH,
        RESOLUTION,
        VALIDATION,
        MATERIALIZATION,
        PREPARATION,
        EXECUTION_START,
        ACTIVE_EXECUTION,
        TEARDOWN,
        TERMINAL
    }

    /** Returns true if this state is a terminal end state (COMPLETED or FAILED). */
    fun isTerminal(): Boolean = this == COMPLETED || this == FAILED

    /** Returns true if the game process is running or actively initializing windows/XR. */
    fun isRunningOrActive(): Boolean = this == RUNNING || this == WINDOW_OR_XR_HANDSHAKE_WAITING

    /** Returns true if the state is prior to environment process startup. */
    fun isPreExecution(): Boolean = stage == LaunchStage.PRE_LAUNCH || stage == LaunchStage.RESOLUTION ||
            stage == LaunchStage.VALIDATION || stage == LaunchStage.MATERIALIZATION || stage == LaunchStage.PREPARATION

    /** Checks whether transitioning from this state to [targetState] is valid. */
    fun canTransitionTo(targetState: LaunchState): Boolean {
        if (this == targetState) return true
        if (isTerminal()) return false // No transitions out of terminal states
        if (targetState == FAILED || targetState == STOPPING) return true // Failure/stop allowed from any non-terminal state

        return when (this) {
            REQUEST_RECEIVED -> targetState == INSTALL_RESOLVING
            INSTALL_RESOLVING -> targetState == EXECUTABLE_INSPECTING
            EXECUTABLE_INSPECTING -> targetState == DEVICE_DETECTING
            DEVICE_DETECTING -> targetState == HARDWARE_PROFILE_RESOLVING
            HARDWARE_PROFILE_RESOLVING -> targetState == COMPATIBILITY_RESOLVING
            COMPATIBILITY_RESOLVING -> targetState == VR_CAPABILITY_RESOLVING
            VR_CAPABILITY_RESOLVING -> targetState == RUNTIME_VALIDATING
            RUNTIME_VALIDATING -> targetState == MOD_PLAN_VALIDATING
            MOD_PLAN_VALIDATING -> targetState == FILES_MATERIALIZING || targetState == STEAM_PREPARING
            FILES_MATERIALIZING -> targetState == STEAM_PREPARING
            STEAM_PREPARING -> targetState == CONTAINER_PREPARING
            CONTAINER_PREPARING -> targetState == ENVIRONMENT_STARTING
            ENVIRONMENT_STARTING -> targetState == GUEST_PROCESS_STARTING
            GUEST_PROCESS_STARTING -> targetState == WINDOW_OR_XR_HANDSHAKE_WAITING
            WINDOW_OR_XR_HANDSHAKE_WAITING -> targetState == RUNNING
            RUNNING -> targetState == STOPPING || targetState == CLEANUP
            STOPPING -> targetState == CLEANUP
            CLEANUP -> targetState == COMPLETED || targetState == FAILED
            COMPLETED, FAILED -> false
        }
    }
}
