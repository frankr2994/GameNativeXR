package app.gamenative.launch

import kotlinx.coroutines.flow.StateFlow

/** Structured transition event emitted on state changes. */
data class LaunchEvent(
    val launchId: String,
    val previousState: LaunchState,
    val newState: LaunchState,
    val timestampMs: Long = System.currentTimeMillis(),
    val durationMs: Long = 0,
    val detail: String = "",
    val failure: LaunchFailureException? = null
)

/** Listener callback for launch events. */
fun interface LaunchEventListener {
    fun onEvent(event: LaunchEvent)
}

/** Terminal result of a launch execution session. */
sealed class TerminalLaunchResult {
    data class Success(
        val launchId: String,
        val plan: LaunchPlan,
        val processPid: Int,
        val totalDurationMs: Long
    ) : TerminalLaunchResult()

    data class Failure(
        val launchId: String,
        val failedState: LaunchState,
        val failure: LaunchFailureException,
        val totalDurationMs: Long
    ) : TerminalLaunchResult()
}

interface GameLaunchCoordinator {
    val currentState: StateFlow<LaunchState>
    val currentLaunchId: StateFlow<String?>
    val currentPlan: StateFlow<LaunchPlan?>

    fun addListener(listener: LaunchEventListener)
    fun removeListener(listener: LaunchEventListener)

    /** Initiates the 19-state launch execution sequence. */
    suspend fun executeLaunch(request: LaunchRequest): TerminalLaunchResult

    /** Requests cancellation of the current active launch session. */
    suspend fun cancelLaunch(launchId: String, reason: String)
}

/**
 * Supplies the immutable hardware execution input for a launch. The coordinator deliberately
 * depends on this narrow contract instead of an Android [android.content.Context] so that launch
 * planning remains unit-testable and the Android-specific Quest probe stays in the hardware
 * package.
 */
interface LaunchHardwareProfileProvider {
    suspend fun resolve(): QuestHardwareProfileInput
}
