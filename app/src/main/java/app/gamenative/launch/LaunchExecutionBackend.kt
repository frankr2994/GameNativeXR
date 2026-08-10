package app.gamenative.launch

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

enum class LaunchBackendIdentity {
    BIONIC,
    GLIBC,
    UNKNOWN
}

sealed interface LaunchBackendEvent {
    data object PrefixPreparationStarted : LaunchBackendEvent
    data object PrefixPreparationCompleted : LaunchBackendEvent
    data object EnvironmentStarting : LaunchBackendEvent
    data object EnvironmentComponentsStarted : LaunchBackendEvent
    data class PreinstallStarted(val commandId: String) : LaunchBackendEvent
    data class PreinstallCompleted(val commandId: String, val exitCode: Int) : LaunchBackendEvent
    data class GuestCommandSubmitted(val redactedCommand: String) : LaunchBackendEvent
    data class GuestPidObserved(val pid: Int) : LaunchBackendEvent
    data class ChildPidObserved(val pid: Int) : LaunchBackendEvent
    data class FirstWindowObserved(val windowId: Int, val processId: Int?) : LaunchBackendEvent
    data class XrHandshakeObserved(val protocolVersion: String) : LaunchBackendEvent
    data class GuestExited(val exitCode: Int) : LaunchBackendEvent
    data class TimedOut(val reason: String) : LaunchBackendEvent
    data class Cancelled(val reason: String) : LaunchBackendEvent
    data object CleanupCompleted : LaunchBackendEvent
    data class CleanupFailed(val cause: Throwable) : LaunchBackendEvent
}

fun interface LaunchBackendEventListener {
    fun onEvent(event: LaunchBackendEvent)
}

sealed interface LaunchTermination {
    data class Exited(val exitCode: Int) : LaunchTermination
    data class Cancelled(val reason: String) : LaunchTermination
    data class Failed(val cause: Throwable) : LaunchTermination
}

interface RunningLaunchHandle {
    val launchId: String
    val backendIdentity: LaunchBackendIdentity
    val rootPid: StateFlow<Int?>
    val observedChildPids: StateFlow<Set<Int>>
    val termination: Deferred<LaunchTermination>

    suspend fun cancel(reason: String)
}

/** Thread-safe handle implementation shared by the X-server adapter and backend tests. */
class MutableRunningLaunchHandle(
    override val launchId: String,
    override val backendIdentity: LaunchBackendIdentity,
    private val cancelAction: suspend (String) -> Unit = {}
) : RunningLaunchHandle {
    private val cancellationRequested = AtomicBoolean(false)
    private val mutableRootPid = MutableStateFlow<Int?>(null)
    override val rootPid: StateFlow<Int?> = mutableRootPid.asStateFlow()

    private val mutableChildPids = MutableStateFlow<Set<Int>>(emptySet())
    override val observedChildPids: StateFlow<Set<Int>> = mutableChildPids.asStateFlow()

    private val mutableTermination = CompletableDeferred<LaunchTermination>()
    override val termination: Deferred<LaunchTermination> = mutableTermination

    fun observeRootPid(pid: Int) {
        if (pid > 0) mutableRootPid.value = pid
    }

    fun observeChildPid(pid: Int) {
        if (pid > 0) mutableChildPids.value = mutableChildPids.value + pid
    }

    fun complete(termination: LaunchTermination) {
        mutableTermination.complete(termination)
    }

    override suspend fun cancel(reason: String) {
        if (mutableTermination.isCompleted) return
        if (cancellationRequested.compareAndSet(false, true)) {
            cancelAction(reason)
        }
    }
}

interface LaunchExecutionBackend {
    suspend fun start(plan: LaunchPlan, listener: LaunchBackendEventListener): RunningLaunchHandle

    /**
     * Gives a real backend a chance to publish its cancellation handle before expensive setup
     * starts. Legacy/test backends retain the two-argument implementation and publish it once
     * their start call completes.
     */
    suspend fun start(
        plan: LaunchPlan,
        listener: LaunchBackendEventListener,
        onHandleReady: (RunningLaunchHandle) -> Unit,
    ): RunningLaunchHandle {
        val handle = start(plan, listener)
        onHandleReady(handle)
        return handle
    }
}

interface RuntimeComponentValidator {
    suspend fun validate(requiredComponentIds: Set<String>)
}

object NoOpRuntimeComponentValidator : RuntimeComponentValidator {
    override suspend fun validate(requiredComponentIds: Set<String>) = Unit
}
