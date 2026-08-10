package app.gamenative.launch

import app.gamenative.launch.inspect.ExecutableInspector
import app.gamenative.launch.install.GameInstallResolver
import app.gamenative.launch.vr.TrackingMode
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.CopyOnWriteArrayList

class GameLaunchCoordinatorImpl(
    private val installResolver: GameInstallResolver,
    private val inspector: ExecutableInspector,
    private val precedenceResolver: LaunchPrecedenceResolver,
    private val hardwareProfileProvider: LaunchHardwareProfileProvider,
    private val executionBackend: LaunchExecutionBackend,
    private val componentValidator: RuntimeComponentValidator = NoOpRuntimeComponentValidator
) : GameLaunchCoordinator {

    private val _currentState = MutableStateFlow(LaunchState.REQUEST_RECEIVED)
    override val currentState: StateFlow<LaunchState> = _currentState.asStateFlow()

    private val _currentLaunchId = MutableStateFlow<String?>(null)
    override val currentLaunchId: StateFlow<String?> = _currentLaunchId.asStateFlow()

    private val _currentPlan = MutableStateFlow<LaunchPlan?>(null)
    override val currentPlan: StateFlow<LaunchPlan?> = _currentPlan.asStateFlow()

    private val _currentHandle = MutableStateFlow<RunningLaunchHandle?>(null)
    override val currentHandle: StateFlow<RunningLaunchHandle?> = _currentHandle.asStateFlow()

    private val listeners = CopyOnWriteArrayList<LaunchEventListener>()
    private val sessionMutex = Mutex()
    private val transitionLock = Any()
    private var activeLaunchId: String? = null
    private var stateStartTimeMs: Long = System.currentTimeMillis()

    override fun addListener(listener: LaunchEventListener) {
        listeners.addIfAbsent(listener)
    }

    override fun removeListener(listener: LaunchEventListener) {
        listeners.remove(listener)
    }

    private fun transitionTo(
        newState: LaunchState,
        detail: String = "",
        failure: LaunchFailureException? = null
    ) = synchronized(transitionLock) {
        val previousState = _currentState.value
        check(previousState.canTransitionTo(newState)) {
            "Invalid state transition from $previousState to $newState"
        }
        val now = System.currentTimeMillis()
        val duration = now - stateStartTimeMs
        stateStartTimeMs = now
        _currentState.value = newState
        publish(
            LaunchEvent(
                launchId = _currentLaunchId.value ?: "unknown",
                previousState = previousState,
                newState = newState,
                timestampMs = now,
                durationMs = duration,
                detail = detail,
                failure = failure
            )
        )
    }

    private fun publish(event: LaunchEvent) {
        listeners.forEach { listener ->
            try {
                listener.onEvent(event)
            } catch (_: Exception) {
                // A diagnostic/UI listener cannot break the launch state machine.
            }
        }
    }

    override suspend fun executeLaunch(request: LaunchRequest): TerminalLaunchResult {
        val startTimeMs = System.currentTimeMillis()
        val duplicate = sessionMutex.withLock {
            activeLaunchId?.let { LaunchFailureException.DuplicateLaunch(it, request.launchId) }
                ?: run {
                    activeLaunchId = request.launchId
                    null
                }
        }
        if (duplicate != null) {
            return TerminalLaunchResult.Failure(
                launchId = request.launchId,
                failedState = _currentState.value,
                failure = duplicate,
                totalDurationMs = System.currentTimeMillis() - startTimeMs
            )
        }

        _currentLaunchId.value = request.launchId
        _currentPlan.value = null
        _currentHandle.value = null
        _currentState.value = LaunchState.REQUEST_RECEIVED
        stateStartTimeMs = startTimeMs
        publish(
            LaunchEvent(
                launchId = request.launchId,
                previousState = LaunchState.REQUEST_RECEIVED,
                newState = LaunchState.REQUEST_RECEIVED,
                timestampMs = startTimeMs,
                detail = "Request validated for appId=${request.appId}"
            )
        )

        var failedState = LaunchState.REQUEST_RECEIVED
        try {
            transitionTo(LaunchState.INSTALL_RESOLVING, "Resolving authoritative game install")
            val install = installResolver.resolve(request.requireInstallResolution())

            transitionTo(LaunchState.EXECUTABLE_INSPECTING, "Inspecting ${install.executableRelativePath}")
            val executableIdentity = inspector.inspect(
                install.hostInstallRoot.file,
                install.executableRelativePath
            ).copy(isLauncher = install.selectedLaunchOption.isLauncher)

            transitionTo(LaunchState.DEVICE_DETECTING, "Detecting headset once for this launch")
            val hardwareInput = hardwareProfileProvider.resolve()
            if (hardwareInput.deviceDescriptor != "QUEST_2" && hardwareInput.deviceDescriptor != "QUEST_3") {
                throw LaunchFailureException.InvalidHardware(hardwareInput.deviceDescriptor)
            }

            transitionTo(LaunchState.HARDWARE_PROFILE_RESOLVING, "Resolved profile ${hardwareInput.profileId}")
            transitionTo(LaunchState.COMPATIBILITY_RESOLVING, "Resolving compatibility and user precedence")
            transitionTo(LaunchState.VR_CAPABILITY_RESOLVING, "Resolving safe tracking mode")

            val plan = precedenceResolver.resolvePlan(
                request = request,
                resolvedInstall = install,
                exeIdentity = executableIdentity,
                hardwareInput = hardwareInput,
                compatibilityInput = null,
                modInput = null,
                userContainer = request.userContainerConfig
            )
            _currentPlan.value = plan

            transitionTo(LaunchState.RUNTIME_VALIDATING, "Validating packaged runtime components")
            componentValidator.validate(plan.executionConfig.requiredPackagedComponentIds)
            transitionTo(LaunchState.MOD_PLAN_VALIDATING, "No active VR mod plan")
            transitionTo(LaunchState.STEAM_PREPARING, "Backend may prepare the selected Steam mode")

            val eventChannel = Channel<LaunchBackendEvent>(Channel.UNLIMITED)
            val handle = try {
                executionBackend.start(
                    plan = plan,
                    listener = { event -> eventChannel.trySend(event) },
                    onHandleReady = { startedHandle -> _currentHandle.value = startedHandle },
                )
            } catch (failure: Throwable) {
                throw LaunchFailureException.EnvironmentSetupFailed(failure)
            }
            _currentHandle.value = handle

            var pendingFailure: LaunchFailureException? = null
            var terminal: LaunchTermination? = null
            while (true) {
                when (val event = eventChannel.receive()) {
                    LaunchBackendEvent.PrefixPreparationStarted ->
                        transitionTo(LaunchState.CONTAINER_PREPARING, "Wine prefix preparation started")
                    LaunchBackendEvent.PrefixPreparationCompleted ->
                        transitionTo(LaunchState.CONTAINER_PREPARING, "Wine prefix preparation completed")
                    LaunchBackendEvent.EnvironmentStarting ->
                        transitionTo(LaunchState.ENVIRONMENT_STARTING, "Environment component startup requested")
                    LaunchBackendEvent.EnvironmentComponentsStarted -> {
                        if (_currentState.value == LaunchState.ENVIRONMENT_STARTING) {
                            transitionTo(LaunchState.GUEST_PROCESS_STARTING, "Environment components started")
                        } else {
                            transitionTo(_currentState.value, "Environment components started")
                        }
                    }
                    is LaunchBackendEvent.PreinstallStarted -> {
                        if (_currentState.value == LaunchState.ENVIRONMENT_STARTING) {
                            transitionTo(LaunchState.GUEST_PROCESS_STARTING, "Preinstall started: ${event.commandId}")
                        } else {
                            transitionTo(_currentState.value, "Preinstall started: ${event.commandId}")
                        }
                    }
                    is LaunchBackendEvent.PreinstallCompleted -> {
                        transitionTo(LaunchState.GUEST_PROCESS_STARTING, "Preinstall completed: ${event.commandId} (${event.exitCode})")
                        if (event.exitCode != 0 && pendingFailure == null) {
                            pendingFailure = LaunchFailureException.PreinstallFailed(event.commandId, event.exitCode)
                        }
                    }
                    is LaunchBackendEvent.GuestCommandSubmitted -> {
                        if (_currentState.value == LaunchState.ENVIRONMENT_STARTING) {
                            transitionTo(LaunchState.GUEST_PROCESS_STARTING, "Guest launcher component ready")
                        }
                        if (_currentState.value == LaunchState.GUEST_PROCESS_STARTING) {
                            transitionTo(
                                LaunchState.WINDOW_OR_XR_HANDSHAKE_WAITING,
                                "Guest command submitted: ${event.redactedCommand}",
                            )
                        } else {
                            transitionTo(_currentState.value, "Guest command submitted: ${event.redactedCommand}")
                        }
                    }
                    is LaunchBackendEvent.GuestPidObserved ->
                        transitionTo(_currentState.value, "Observed backend root PID ${event.pid}")
                    is LaunchBackendEvent.ChildPidObserved ->
                        transitionTo(_currentState.value, "Observed guest child PID ${event.pid}")
                    is LaunchBackendEvent.FirstWindowObserved -> {
                        if (plan.trackingMode == TrackingMode.FLAT_3DOF && _currentState.value != LaunchState.RUNNING) {
                            transitionTo(LaunchState.RUNNING, "First X-server application window observed")
                        }
                    }
                    is LaunchBackendEvent.XrHandshakeObserved -> {
                        if (plan.trackingMode != TrackingMode.FLAT_3DOF && _currentState.value != LaunchState.RUNNING) {
                            transitionTo(LaunchState.RUNNING, "XR handshake ${event.protocolVersion} observed")
                        }
                    }
                    is LaunchBackendEvent.GuestExited -> {
                        terminal = LaunchTermination.Exited(event.exitCode)
                        if (_currentState.value != LaunchState.RUNNING && pendingFailure == null) {
                            pendingFailure = LaunchFailureException.EarlyProcessExit(event.exitCode, "")
                        } else if (event.exitCode != 0 && pendingFailure == null) {
                            pendingFailure = LaunchFailureException.EarlyProcessExit(event.exitCode, "")
                        }
                        if (_currentState.value != LaunchState.RUNNING && _currentState.value != LaunchState.CLEANUP) {
                            transitionTo(LaunchState.STOPPING, "Guest exited before running evidence")
                        }
                        if (_currentState.value != LaunchState.CLEANUP) {
                            transitionTo(LaunchState.CLEANUP, "Guest exited with status ${event.exitCode}")
                        }
                    }
                    is LaunchBackendEvent.TimedOut -> {
                        terminal = LaunchTermination.Failed(IllegalStateException(event.reason))
                        pendingFailure = LaunchFailureException.XrHandshakeTimeout(0, plan.trackingMode.name)
                        transitionTo(LaunchState.STOPPING, "Backend timeout: ${event.reason}")
                        transitionTo(LaunchState.CLEANUP, "Cleanup after timeout")
                    }
                    is LaunchBackendEvent.Cancelled -> {
                        terminal = LaunchTermination.Cancelled(event.reason)
                        transitionTo(LaunchState.STOPPING, "Cancellation acknowledged: ${event.reason}")
                        transitionTo(LaunchState.CLEANUP, "Cleanup after cancellation")
                    }
                    LaunchBackendEvent.CleanupCompleted -> {
                        val totalDuration = System.currentTimeMillis() - startTimeMs
                        if (pendingFailure != null) {
                            failedState = _currentState.value
                            transitionTo(LaunchState.FAILED, pendingFailure.technicalDetails, pendingFailure)
                            return TerminalLaunchResult.Failure(request.launchId, failedState, pendingFailure, totalDuration)
                        }
                        transitionTo(LaunchState.COMPLETED, "Backend cleanup completed")
                        return TerminalLaunchResult.Success(
                            launchId = request.launchId,
                            plan = plan,
                            handle = handle,
                            termination = terminal ?: handle.termination.await(),
                            totalDurationMs = totalDuration
                        )
                    }
                    is LaunchBackendEvent.CleanupFailed -> throw LaunchFailureException.CleanupFailed(event.cause)
                }
            }
        } catch (failure: Throwable) {
            val classified = when (failure) {
                is LaunchFailureException -> failure
                else -> LaunchFailureException.JavaCrash(failure)
            }
            failedState = _currentState.value
            if (!_currentState.value.isTerminal()) {
                transitionTo(LaunchState.FAILED, classified.technicalDetails, classified)
            }
            return TerminalLaunchResult.Failure(
                launchId = request.launchId,
                failedState = failedState,
                failure = classified,
                totalDurationMs = System.currentTimeMillis() - startTimeMs
            )
        } finally {
            sessionMutex.withLock {
                if (activeLaunchId == request.launchId) activeLaunchId = null
            }
        }
    }

    override suspend fun cancelLaunch(launchId: String, reason: String) {
        val handle = sessionMutex.withLock {
            if (activeLaunchId == launchId) _currentHandle.value else null
        }
        handle?.cancel(reason)
    }
}
