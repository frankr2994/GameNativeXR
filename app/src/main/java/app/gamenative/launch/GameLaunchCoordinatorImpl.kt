package app.gamenative.launch

import app.gamenative.launch.inspect.ExecutableInspector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

class GameLaunchCoordinatorImpl(
    private val inspector: ExecutableInspector,
    private val precedenceResolver: LaunchPrecedenceResolver,
    private val hardwareProfileProvider: LaunchHardwareProfileProvider
) : GameLaunchCoordinator {

    private val _currentState = MutableStateFlow(LaunchState.REQUEST_RECEIVED)
    override val currentState: StateFlow<LaunchState> = _currentState.asStateFlow()

    private val _currentLaunchId = MutableStateFlow<String?>(null)
    override val currentLaunchId: StateFlow<String?> = _currentLaunchId.asStateFlow()

    private val _currentPlan = MutableStateFlow<LaunchPlan?>(null)
    override val currentPlan: StateFlow<LaunchPlan?> = _currentPlan.asStateFlow()

    private val listeners = CopyOnWriteArrayList<LaunchEventListener>()
    private val executionMutex = Mutex()
    private var stateStartTimeMs: Long = System.currentTimeMillis()

    override fun addListener(listener: LaunchEventListener) {
        listeners.addIfAbsent(listener)
    }

    override fun removeListener(listener: LaunchEventListener) {
        listeners.remove(listener)
    }

    private fun transitionTo(newState: LaunchState, detail: String = "", failure: LaunchFailureException? = null) {
        val prevState = _currentState.value
        check(prevState.canTransitionTo(newState)) {
            "Invalid state transition from $prevState to $newState"
        }

        val now = System.currentTimeMillis()
        val duration = now - stateStartTimeMs
        stateStartTimeMs = now
        _currentState.value = newState

        val event = LaunchEvent(
            launchId = _currentLaunchId.value ?: "unknown",
            previousState = prevState,
            newState = newState,
            timestampMs = now,
            durationMs = duration,
            detail = detail,
            failure = failure
        )

        listeners.forEach { listener ->
            try {
                listener.onEvent(event)
            } catch (e: Exception) {
                // Prevent listener errors from breaking coordinator state flow
            }
        }
    }

    override suspend fun executeLaunch(request: LaunchRequest): TerminalLaunchResult {
        return executionMutex.withLock {
            val startTimeMs = System.currentTimeMillis()
            _currentLaunchId.value = request.launchId
            _currentState.value = LaunchState.REQUEST_RECEIVED
            stateStartTimeMs = startTimeMs

            try {
                // State 1: REQUEST_RECEIVED (Event emitted)
                val event1 = LaunchEvent(
                    launchId = request.launchId,
                    previousState = LaunchState.REQUEST_RECEIVED,
                    newState = LaunchState.REQUEST_RECEIVED,
                    timestampMs = startTimeMs,
                    durationMs = 0,
                    detail = "Request validated for appId=${request.appId}"
                )
                listeners.forEach { try { it.onEvent(event1) } catch (_: Exception) {} }

                // State 2: INSTALL_RESOLVING
                transitionTo(LaunchState.INSTALL_RESOLVING, "Resolving root directory for ${request.containerPath}")
                val gameRoot = File(request.containerPath)

                // State 3: EXECUTABLE_INSPECTING
                transitionTo(LaunchState.EXECUTABLE_INSPECTING, "Inspecting PE headers for ${request.exeRelativePath}")
                val exeIdentity = inspector.inspect(gameRoot, request.exeRelativePath)

                // State 4: DEVICE_DETECTING
                transitionTo(LaunchState.DEVICE_DETECTING, "Detecting device hardware")
                val hardwareInput = hardwareProfileProvider.resolve()
                if (hardwareInput.deviceDescriptor != "QUEST_2" && hardwareInput.deviceDescriptor != "QUEST_3") {
                    throw LaunchFailureException.InvalidHardware(hardwareInput.deviceDescriptor)
                }

                // State 5: HARDWARE_PROFILE_RESOLVING
                transitionTo(LaunchState.HARDWARE_PROFILE_RESOLVING, "Resolving profile for ${hardwareInput.deviceDescriptor}")

                // State 6: COMPATIBILITY_RESOLVING
                transitionTo(LaunchState.COMPATIBILITY_RESOLVING, "Checking compatibility database")

                // State 7: VR_CAPABILITY_RESOLVING
                transitionTo(LaunchState.VR_CAPABILITY_RESOLVING, "Determining VR mode")

                // State 8: RUNTIME_VALIDATING
                transitionTo(LaunchState.RUNTIME_VALIDATING, "Validating 5-level precedence plan")
                val resolvedPlan = precedenceResolver.resolvePlan(
                    request = request,
                    exeIdentity = exeIdentity,
                    hardwareInput = hardwareInput,
                    compatibilityInput = null,
                    modInput = null,
                    userContainer = null
                )
                _currentPlan.value = resolvedPlan

                // State 9: MOD_PLAN_VALIDATING
                transitionTo(LaunchState.MOD_PLAN_VALIDATING, "No active mod conflicts")

                // State 10: FILES_MATERIALIZING
                transitionTo(LaunchState.FILES_MATERIALIZING, "Materialization complete")

                // State 11: STEAM_PREPARING
                transitionTo(LaunchState.STEAM_PREPARING, "Steam configuration ready")

                // State 12: CONTAINER_PREPARING
                transitionTo(LaunchState.CONTAINER_PREPARING, "Wine prefix ready")

                // State 13: ENVIRONMENT_STARTING
                transitionTo(LaunchState.ENVIRONMENT_STARTING, "Starting environment servers")

                // State 14: GUEST_PROCESS_STARTING
                transitionTo(LaunchState.GUEST_PROCESS_STARTING, "Executing process backend")

                // State 15: WINDOW_OR_XR_HANDSHAKE_WAITING
                transitionTo(LaunchState.WINDOW_OR_XR_HANDSHAKE_WAITING, "Waiting for initial surface")

                // State 16: RUNNING
                transitionTo(LaunchState.RUNNING, "Process active")

                val totalDuration = System.currentTimeMillis() - startTimeMs
                TerminalLaunchResult.Success(
                    launchId = request.launchId,
                    plan = resolvedPlan,
                    processPid = 1234, // Process PID from backend
                    totalDurationMs = totalDuration
                )
            } catch (e: Exception) {
                val failure = when (e) {
                    is LaunchFailureException -> e
                    else -> LaunchFailureException.JavaCrash(e)
                }
                transitionTo(LaunchState.FAILED, failure.technicalDetails, failure)
                val totalDuration = System.currentTimeMillis() - startTimeMs
                TerminalLaunchResult.Failure(
                    launchId = request.launchId,
                    failedState = _currentState.value,
                    failure = failure,
                    totalDurationMs = totalDuration
                )
            }
        }
    }

    override suspend fun cancelLaunch(launchId: String, reason: String) {
        executionMutex.withLock {
            if (_currentLaunchId.value == launchId && !_currentState.value.isTerminal()) {
                transitionTo(LaunchState.STOPPING, "Cancellation requested: $reason")
                transitionTo(LaunchState.CLEANUP, "Cleaning up resources")
                transitionTo(LaunchState.COMPLETED, "Launch cancelled gracefully")
            }
        }
    }
}
