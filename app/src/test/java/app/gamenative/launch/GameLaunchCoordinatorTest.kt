package app.gamenative.launch

import app.gamenative.data.GameSource
import app.gamenative.launch.inspect.ExecutableIdentity
import app.gamenative.launch.inspect.ExecutableInspector
import app.gamenative.launch.inspect.PeArchitecture
import app.gamenative.launch.install.GameInstallResolver
import app.gamenative.launch.install.GuestMountedRoot
import app.gamenative.launch.install.HostInstallRoot
import app.gamenative.launch.install.ResolvedGameInstall
import app.gamenative.launch.install.SelectedLaunchOption
import app.gamenative.launch.install.WineExecutablePath
import app.gamenative.launch.install.WineWorkingDirectoryPath
import app.gamenative.launch.vr.TrackingMode
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class GameLaunchCoordinatorTest {
    private lateinit var installResolver: GameInstallResolver
    private lateinit var inspector: ExecutableInspector
    private lateinit var planResolver: LaunchPrecedenceResolver
    private lateinit var request: LaunchRequest
    private lateinit var install: ResolvedGameInstall
    private lateinit var identity: ExecutableIdentity
    private lateinit var plan: LaunchPlan

    @Before
    fun setUp() {
        installResolver = mockk()
        inspector = mockk()
        planResolver = mockk()
        request = LaunchRequest(
            launchId = "test-launch-1",
            sessionId = "test-session-1",
            appId = "480",
            gameSource = GameSource.STEAM,
            exeRelativePath = "SpaceWar.exe",
            containerPath = "/sdcard/spacewar"
        )
        val root = File("/sdcard/spacewar")
        install = ResolvedGameInstall(
            appId = "480",
            gameSource = GameSource.STEAM,
            hostInstallRoot = HostInstallRoot(root),
            guestMountedRoot = GuestMountedRoot('D', root),
            selectedExecutable = File(root, "SpaceWar.exe"),
            executableRelativePath = "SpaceWar.exe",
            wineExecutablePath = WineExecutablePath("D:\\SpaceWar.exe"),
            wineWorkingDirectoryPath = WineWorkingDirectoryPath("D:\\"),
            selectedLaunchOption = SelectedLaunchOption("default", "SpaceWar.exe"),
            evidence = emptyList()
        )
        identity = ExecutableIdentity(
            canonicalPath = "/sdcard/spacewar/SpaceWar.exe",
            relativePath = "SpaceWar.exe",
            fileSize = 2048,
            lastModified = 1000L,
            sha256 = "hash123",
            architecture = PeArchitecture.X64_64,
            isLauncher = false,
            importedLibraries = emptyList(),
            hasOpenXRImport = false,
            hasOpenVRImport = false
        )
        plan = mockk(relaxed = true)
        every { plan.trackingMode } returns TrackingMode.FLAT_3DOF
        every { plan.executionConfig.requiredPackagedComponentIds } returns emptySet()
        coEvery { installResolver.resolve(any()) } returns install
        coEvery { inspector.inspect(any(), any()) } returns identity
        every { planResolver.resolvePlan(any(), any(), any(), any(), any(), any(), any()) } returns plan
    }

    @Test
    fun callbackTimeline_requiresFirstWindowBeforeRunning_andReturnsRealHandle() = runTest {
        val backend = ScriptedBackend(
            listOf(
                LaunchBackendEvent.PrefixPreparationStarted,
                LaunchBackendEvent.PrefixPreparationCompleted,
                LaunchBackendEvent.EnvironmentStarting,
                LaunchBackendEvent.EnvironmentComponentsStarted,
                LaunchBackendEvent.GuestCommandSubmitted("wine game.exe"),
                LaunchBackendEvent.GuestPidObserved(4321),
                LaunchBackendEvent.FirstWindowObserved(7, 4321),
                LaunchBackendEvent.GuestExited(0),
                LaunchBackendEvent.CleanupCompleted
            )
        )
        val coordinator = coordinator(backend)
        val states = mutableListOf<LaunchState>()
        coordinator.addListener { states += it.newState }

        val result = coordinator.executeLaunch(request)

        assertTrue(result is TerminalLaunchResult.Success)
        result as TerminalLaunchResult.Success
        assertEquals(4321, result.handle.rootPid.value)
        assertEquals(LaunchState.COMPLETED, coordinator.currentState.value)
        assertTrue(states.indexOf(LaunchState.WINDOW_OR_XR_HANDSHAKE_WAITING) < states.indexOf(LaunchState.RUNNING))
        assertEquals(1, backend.cleanupEvents)
    }

    @Test
    fun guestExitBeforeFirstWindow_isClassifiedAndNeverRuns() = runTest {
        val backend = ScriptedBackend(
            listOf(
                LaunchBackendEvent.PrefixPreparationStarted,
                LaunchBackendEvent.PrefixPreparationCompleted,
                LaunchBackendEvent.EnvironmentStarting,
                LaunchBackendEvent.EnvironmentComponentsStarted,
                LaunchBackendEvent.GuestCommandSubmitted("wine game.exe"),
                LaunchBackendEvent.GuestExited(5),
                LaunchBackendEvent.CleanupCompleted
            )
        )
        val coordinator = coordinator(backend)
        val states = mutableListOf<LaunchState>()
        coordinator.addListener { states += it.newState }

        val result = coordinator.executeLaunch(request)

        assertTrue(result is TerminalLaunchResult.Failure)
        result as TerminalLaunchResult.Failure
        assertEquals(LaunchFailureCategory.GUEST_PROCESS_EXITED_BEFORE_FIRST_WINDOW, result.failure.category)
        assertFalse(states.contains(LaunchState.RUNNING))
        assertEquals(1, backend.cleanupEvents)
    }

    @Test
    fun preinstallFailure_isNotMarkedComplete() = runTest {
        val backend = ScriptedBackend(
            listOf(
                LaunchBackendEvent.PrefixPreparationStarted,
                LaunchBackendEvent.PrefixPreparationCompleted,
                LaunchBackendEvent.EnvironmentStarting,
                LaunchBackendEvent.EnvironmentComponentsStarted,
                LaunchBackendEvent.PreinstallStarted("vcredist"),
                LaunchBackendEvent.PreinstallCompleted("vcredist", 1603),
                LaunchBackendEvent.GuestExited(1603),
                LaunchBackendEvent.CleanupCompleted
            )
        )

        val result = coordinator(backend).executeLaunch(request) as TerminalLaunchResult.Failure

        assertEquals("PREINSTALL_COMMAND_FAILED", result.failure.failureCode)
    }

    @Test
    fun environmentSetupException_isClassified() = runTest {
        val backend = object : LaunchExecutionBackend {
            override suspend fun start(plan: LaunchPlan, listener: LaunchBackendEventListener): RunningLaunchHandle {
                throw IllegalStateException("X server failed")
            }
        }

        val result = coordinator(backend).executeLaunch(request) as TerminalLaunchResult.Failure

        assertEquals("ENVIRONMENT_SETUP_FAILED", result.failure.failureCode)
    }

    @Test
    fun cancellationWhileRunning_isIdempotentAndCleansUpOnce() = runTest {
        val backend = CancellableBackend()
        val coordinator = coordinator(backend)
        val execution = async { coordinator.executeLaunch(request) }
        backend.started.await()

        coordinator.cancelLaunch(request.launchId, "user")
        coordinator.cancelLaunch(request.launchId, "duplicate")
        val result = execution.await()

        assertTrue(result is TerminalLaunchResult.Success)
        assertEquals(1, backend.cancelCalls)
        assertEquals(1, backend.cleanupEvents)
        assertEquals(LaunchState.COMPLETED, coordinator.currentState.value)
    }

    @Test
    fun cancellationDuringSetup_usesThePublishedRealHandle() = runTest {
        val backend = SetupCancellableBackend()
        val coordinator = coordinator(backend)
        val execution = async { coordinator.executeLaunch(request) }
        backend.handleReady.await()

        coordinator.cancelLaunch(request.launchId, "cancel during setup")
        val result = execution.await() as TerminalLaunchResult.Success

        assertEquals(1, backend.cancelCalls)
        assertEquals(1, backend.cleanupEvents)
        assertEquals(LaunchTermination.Cancelled("cancel during setup"), result.termination)
        assertEquals(LaunchState.COMPLETED, coordinator.currentState.value)
    }

    @Test
    fun listenerExceptions_areIsolated() = runTest {
        val backend = ScriptedBackend(successEvents())
        val coordinator = coordinator(backend)
        val observed = mutableListOf<LaunchState>()
        coordinator.addListener { throw IllegalStateException("listener") }
        coordinator.addListener { observed += it.newState }

        val result = coordinator.executeLaunch(request)

        assertTrue(result is TerminalLaunchResult.Success)
        assertTrue(observed.contains(LaunchState.RUNNING))
    }

    private fun coordinator(backend: LaunchExecutionBackend) = GameLaunchCoordinatorImpl(
        installResolver = installResolver,
        inspector = inspector,
        precedenceResolver = planResolver,
        hardwareProfileProvider = object : LaunchHardwareProfileProvider {
            override suspend fun resolve() = QuestHardwareProfileInput(deviceDescriptor = "QUEST_2")
        },
        executionBackend = backend
    )

    private fun successEvents() = listOf(
        LaunchBackendEvent.PrefixPreparationStarted,
        LaunchBackendEvent.PrefixPreparationCompleted,
        LaunchBackendEvent.EnvironmentStarting,
        LaunchBackendEvent.EnvironmentComponentsStarted,
        LaunchBackendEvent.GuestCommandSubmitted("wine game.exe"),
        LaunchBackendEvent.FirstWindowObserved(1, 42),
        LaunchBackendEvent.GuestExited(0),
        LaunchBackendEvent.CleanupCompleted
    )

    private class ScriptedBackend(private val events: List<LaunchBackendEvent>) : LaunchExecutionBackend {
        var cleanupEvents = 0
            private set

        override suspend fun start(plan: LaunchPlan, listener: LaunchBackendEventListener): RunningLaunchHandle {
            val handle = MutableRunningLaunchHandle(plan.launchId, LaunchBackendIdentity.BIONIC)
            events.forEach { event ->
                when (event) {
                    is LaunchBackendEvent.GuestPidObserved -> handle.observeRootPid(event.pid)
                    is LaunchBackendEvent.ChildPidObserved -> handle.observeChildPid(event.pid)
                    is LaunchBackendEvent.GuestExited -> handle.complete(LaunchTermination.Exited(event.exitCode))
                    LaunchBackendEvent.CleanupCompleted -> cleanupEvents++
                    else -> Unit
                }
                listener.onEvent(event)
            }
            return handle
        }
    }

    private class CancellableBackend : LaunchExecutionBackend {
        val started = kotlinx.coroutines.CompletableDeferred<Unit>()
        var cancelCalls = 0
        var cleanupEvents = 0
        private lateinit var listener: LaunchBackendEventListener

        override suspend fun start(plan: LaunchPlan, listener: LaunchBackendEventListener): RunningLaunchHandle {
            this.listener = listener
            lateinit var handle: MutableRunningLaunchHandle
            handle = MutableRunningLaunchHandle(plan.launchId, LaunchBackendIdentity.BIONIC) { reason ->
                cancelCalls++
                listener.onEvent(LaunchBackendEvent.Cancelled(reason))
                cleanupEvents++
                listener.onEvent(LaunchBackendEvent.CleanupCompleted)
                handle.complete(LaunchTermination.Cancelled(reason))
            }
            listOf(
                LaunchBackendEvent.PrefixPreparationStarted,
                LaunchBackendEvent.PrefixPreparationCompleted,
                LaunchBackendEvent.EnvironmentStarting,
                LaunchBackendEvent.EnvironmentComponentsStarted,
                LaunchBackendEvent.GuestCommandSubmitted("wine game.exe"),
                LaunchBackendEvent.FirstWindowObserved(1, 42)
            ).forEach(listener::onEvent)
            started.complete(Unit)
            return handle
        }
    }

    private class SetupCancellableBackend : LaunchExecutionBackend {
        val handleReady = CompletableDeferred<Unit>()
        private val allowStartToReturn = CompletableDeferred<Unit>()
        var cancelCalls = 0
        var cleanupEvents = 0

        override suspend fun start(plan: LaunchPlan, listener: LaunchBackendEventListener): RunningLaunchHandle {
            error("The coordinator should use the early-handle backend overload")
        }

        override suspend fun start(
            plan: LaunchPlan,
            listener: LaunchBackendEventListener,
            onHandleReady: (RunningLaunchHandle) -> Unit,
        ): RunningLaunchHandle {
            lateinit var handle: MutableRunningLaunchHandle
            handle = MutableRunningLaunchHandle(plan.launchId, LaunchBackendIdentity.BIONIC) { reason ->
                cancelCalls++
                listener.onEvent(LaunchBackendEvent.Cancelled(reason))
                cleanupEvents++
                listener.onEvent(LaunchBackendEvent.CleanupCompleted)
                handle.complete(LaunchTermination.Cancelled(reason))
                allowStartToReturn.complete(Unit)
            }
            onHandleReady(handle)
            handleReady.complete(Unit)
            allowStartToReturn.await()
            return handle
        }
    }
}
