package app.gamenative.launch.backend

import app.gamenative.launch.LaunchBackendEvent
import app.gamenative.launch.LaunchBackendEventListener
import app.gamenative.launch.LaunchBackendIdentity
import app.gamenative.launch.LaunchExecutionBackend
import app.gamenative.launch.LaunchPlan
import app.gamenative.launch.LaunchTermination
import app.gamenative.launch.MutableRunningLaunchHandle
import app.gamenative.launch.RunningLaunchHandle
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Narrow adapter around the existing XServerScreen/Wine environment launch path.
 * The supplied action still performs the real setup; this class only translates its
 * observable callbacks into the coordinator lifecycle contract.
 */
class XServerLaunchExecutionBackend(
    private val backendIdentity: LaunchBackendIdentity,
    private val launchAction: suspend (LaunchPlan, XServerLaunchLifecycle) -> Unit,
    private val stopAction: suspend () -> Unit,
    private val cleanupAction: () -> Unit = {},
    private val backendIdentityForPlan: (LaunchPlan) -> LaunchBackendIdentity = { backendIdentity },
) : LaunchExecutionBackend {
    override suspend fun start(
        plan: LaunchPlan,
        listener: LaunchBackendEventListener
    ): RunningLaunchHandle = start(plan, listener) {}

    override suspend fun start(
        plan: LaunchPlan,
        listener: LaunchBackendEventListener,
        onHandleReady: (RunningLaunchHandle) -> Unit,
    ): RunningLaunchHandle {
        lateinit var lifecycle: XServerLaunchLifecycle
        val startupInProgress = AtomicBoolean(true)
        val cancellationOccurredDuringStartup = AtomicBoolean(false)
        val handle = MutableRunningLaunchHandle(
            launchId = plan.launchId,
            backendIdentity = backendIdentityForPlan(plan),
            cancelAction = { reason ->
                lifecycle.cancelled(reason)
                val duringStartup = startupInProgress.get()
                if (duringStartup) cancellationOccurredDuringStartup.set(true)
                try {
                    stopAction()
                    if (!duringStartup) lifecycle.cleanupCompleted()
                } catch (failure: Throwable) {
                    if (!duringStartup) lifecycle.cleanupFailed(failure)
                }
            }
        )
        lifecycle = XServerLaunchLifecycle(plan.launchId, handle, listener, cleanupAction)
        XServerLaunchSessionRegistry.attach(lifecycle)
        try {
            onHandleReady(handle)
            launchAction(plan, lifecycle)
        } catch (failure: Throwable) {
            startupInProgress.set(false)
            if (lifecycle.isCancellationRequested()) {
                finishCancelledStartup(lifecycle, stopAction)
                return handle
            }
            XServerLaunchSessionRegistry.detach(lifecycle)
            runCatching(cleanupAction)
            handle.complete(LaunchTermination.Failed(failure))
            throw failure
        }
        startupInProgress.set(false)
        if (cancellationOccurredDuringStartup.get()) {
            finishCancelledStartup(lifecycle, stopAction)
        }
        return handle
    }

    private suspend fun finishCancelledStartup(
        lifecycle: XServerLaunchLifecycle,
        stopAction: suspend () -> Unit,
    ) {
        try {
            // The first stop request may have happened before setup created an environment.
            // Run it once more before releasing the launch-only overlay.
            stopAction()
            lifecycle.cleanupCompleted()
        } catch (failure: Throwable) {
            lifecycle.cleanupFailed(failure)
        }
    }
}

class XServerLaunchLifecycle internal constructor(
    val launchId: String,
    private val handle: MutableRunningLaunchHandle,
    private val listener: LaunchBackendEventListener,
    private val cleanupAction: () -> Unit,
) {
    private val terminalObserved = AtomicBoolean(false)
    private val cancellationRequested = AtomicBoolean(false)
    private val cleanupObserved = AtomicBoolean(false)
    private val firstWindowObserved = AtomicBoolean(false)

    fun prefixPreparationStarted() = emit(LaunchBackendEvent.PrefixPreparationStarted)

    fun prefixPreparationCompleted() = emit(LaunchBackendEvent.PrefixPreparationCompleted)

    fun environmentStarting() = emit(LaunchBackendEvent.EnvironmentStarting)

    fun environmentComponentsStarted() = emit(LaunchBackendEvent.EnvironmentComponentsStarted)

    fun preinstallStarted(commandId: String) = emit(LaunchBackendEvent.PreinstallStarted(commandId))

    fun preinstallCompleted(commandId: String, exitCode: Int) =
        emit(LaunchBackendEvent.PreinstallCompleted(commandId, exitCode))

    fun guestCommandSubmitted(redactedCommand: String) =
        emit(LaunchBackendEvent.GuestCommandSubmitted(redactedCommand))

    fun guestPidObserved(pid: Int) {
        if (pid <= 0) return
        handle.observeRootPid(pid)
        emit(LaunchBackendEvent.GuestPidObserved(pid))
    }

    fun childPidObserved(pid: Int) {
        if (pid <= 0) return
        handle.observeChildPid(pid)
        emit(LaunchBackendEvent.ChildPidObserved(pid))
    }

    fun firstWindowObserved(windowId: Int, processId: Int?) {
        if (firstWindowObserved.compareAndSet(false, true)) {
            emit(LaunchBackendEvent.FirstWindowObserved(windowId, processId?.takeIf { it > 0 }))
        }
    }

    fun xrHandshakeObserved(protocolVersion: String) =
        emit(LaunchBackendEvent.XrHandshakeObserved(protocolVersion))

    fun guestExited(exitCode: Int) {
        if (terminalObserved.compareAndSet(false, true)) {
            handle.complete(LaunchTermination.Exited(exitCode))
            emit(LaunchBackendEvent.GuestExited(exitCode))
        }
    }

    fun cancelled(reason: String) {
        cancellationRequested.set(true)
        if (terminalObserved.compareAndSet(false, true)) {
            handle.complete(LaunchTermination.Cancelled(reason))
            emit(LaunchBackendEvent.Cancelled(reason))
        }
    }

    fun isCancellationRequested(): Boolean = cancellationRequested.get()

    fun timedOut(reason: String) {
        if (terminalObserved.compareAndSet(false, true)) {
            val failure = IllegalStateException(reason)
            handle.complete(LaunchTermination.Failed(failure))
            emit(LaunchBackendEvent.TimedOut(reason))
        }
    }

    fun cleanupCompleted() {
        if (cleanupObserved.compareAndSet(false, true)) {
            try {
                cleanupAction()
                emit(LaunchBackendEvent.CleanupCompleted)
            } catch (failure: Throwable) {
                emit(LaunchBackendEvent.CleanupFailed(failure))
            } finally {
                XServerLaunchSessionRegistry.detach(this)
            }
        }
    }

    fun cleanupFailed(cause: Throwable) {
        if (cleanupObserved.compareAndSet(false, true)) {
            val failure = runCatching(cleanupAction).exceptionOrNull() ?: cause
            emit(LaunchBackendEvent.CleanupFailed(failure))
            XServerLaunchSessionRegistry.detach(this)
        }
    }

    private fun emit(event: LaunchBackendEvent) {
        listener.onEvent(event)
    }
}

/** Survives activity recreation and rejects a second environment launch while one is active. */
object XServerLaunchSessionRegistry {
    private val active = AtomicReference<XServerLaunchLifecycle?>(null)

    fun attach(session: XServerLaunchLifecycle) {
        check(active.compareAndSet(null, session)) {
            "An X-server launch session is already active (${active.get()?.launchId})"
        }
    }

    fun current(): XServerLaunchLifecycle? = active.get()

    fun firstWindowObserved(windowId: Int, processId: Int?) {
        active.get()?.firstWindowObserved(windowId, processId)
    }

    fun cleanupCompleted() {
        active.get()?.cleanupCompleted()
    }

    fun cleanupFailed(cause: Throwable) {
        active.get()?.cleanupFailed(cause)
    }

    internal fun detach(session: XServerLaunchLifecycle) {
        active.compareAndSet(session, null)
    }
}
