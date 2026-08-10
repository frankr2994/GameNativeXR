package app.gamenative.launch

import app.gamenative.data.GameSource
import app.gamenative.launch.inspect.ExecutableIdentity
import app.gamenative.launch.inspect.ExecutableInspector
import app.gamenative.launch.inspect.PeArchitecture
import app.gamenative.launch.install.*
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
import org.junit.Test
import java.io.File

class GameLaunchCoordinatorStressTest {
    @Test
    fun transitionGraph_forbidsRunningWithoutEvidence() {
        assertEquals(20, LaunchState.values().size)
        assertFalse(LaunchState.REQUEST_RECEIVED.canTransitionTo(LaunchState.RUNNING))
        assertFalse(LaunchState.GUEST_PROCESS_STARTING.canTransitionTo(LaunchState.RUNNING))
        assertTrue(LaunchState.WINDOW_OR_XR_HANDSHAKE_WAITING.canTransitionTo(LaunchState.RUNNING))
        assertFalse(LaunchState.RUNNING.canTransitionTo(LaunchState.COMPLETED))
    }

    @Test
    fun concurrentLaunch_isRejectedInsteadOfQueued() = runTest {
        val installResolver = mockk<GameInstallResolver>()
        val inspector = mockk<ExecutableInspector>()
        val planResolver = mockk<LaunchPrecedenceResolver>()
        val backend = BlockingBackend()
        val request = request("first")
        val install = install()
        val identity = identity()
        val plan = mockk<LaunchPlan>(relaxed = true)
        every { plan.trackingMode } returns TrackingMode.FLAT_3DOF
        every { plan.executionConfig.requiredPackagedComponentIds } returns emptySet()
        coEvery { installResolver.resolve(any()) } returns install
        coEvery { inspector.inspect(any(), any()) } returns identity
        every { planResolver.resolvePlan(any(), any(), any(), any(), any(), any(), any()) } returns plan
        val coordinator = GameLaunchCoordinatorImpl(
            installResolver,
            inspector,
            planResolver,
            object : LaunchHardwareProfileProvider {
                override suspend fun resolve() = QuestHardwareProfileInput(deviceDescriptor = "QUEST_2")
            },
            backend
        )

        val first = async { coordinator.executeLaunch(request) }
        backend.started.await()
        val duplicate = coordinator.executeLaunch(request("second"))

        assertTrue(duplicate is TerminalLaunchResult.Failure)
        duplicate as TerminalLaunchResult.Failure
        assertEquals("LAUNCH_ALREADY_ACTIVE", duplicate.failure.failureCode)

        backend.finish()
        assertTrue(first.await() is TerminalLaunchResult.Success)
    }

    private fun request(id: String) = LaunchRequest(
        launchId = id,
        sessionId = "session-$id",
        appId = "220",
        gameSource = GameSource.STEAM,
        exeRelativePath = "hl2.exe",
        containerPath = "/sdcard/hl2"
    )

    private fun install(): ResolvedGameInstall {
        val root = File("/sdcard/hl2")
        val mount = GuestMountedRoot('D', root)
        return ResolvedGameInstall(
            "220",
            GameSource.STEAM,
            HostInstallRoot(root),
            mount,
            File(root, "hl2.exe"),
            "hl2.exe",
            WineExecutablePath("D:\\hl2.exe"),
            WineWorkingDirectoryPath("D:\\"),
            SelectedLaunchOption("default", "hl2.exe"),
            emptyList()
        )
    }

    private fun identity() = ExecutableIdentity(
        "/sdcard/hl2/hl2.exe", "hl2.exe", 1024, 1000,
        "hash", PeArchitecture.X64_64, false, emptyList(), false, false
    )

    private class BlockingBackend : LaunchExecutionBackend {
        val started = CompletableDeferred<Unit>()
        private val release = CompletableDeferred<Unit>()
        private lateinit var listener: LaunchBackendEventListener
        private lateinit var handle: MutableRunningLaunchHandle

        override suspend fun start(plan: LaunchPlan, listener: LaunchBackendEventListener): RunningLaunchHandle {
            this.listener = listener
            handle = MutableRunningLaunchHandle(plan.launchId, LaunchBackendIdentity.BIONIC)
            listOf(
                LaunchBackendEvent.PrefixPreparationStarted,
                LaunchBackendEvent.PrefixPreparationCompleted,
                LaunchBackendEvent.EnvironmentStarting,
                LaunchBackendEvent.EnvironmentComponentsStarted,
                LaunchBackendEvent.GuestCommandSubmitted("wine hl2.exe"),
                LaunchBackendEvent.FirstWindowObserved(1, 10)
            ).forEach(listener::onEvent)
            started.complete(Unit)
            return handle
        }

        fun finish() {
            handle.complete(LaunchTermination.Exited(0))
            listener.onEvent(LaunchBackendEvent.GuestExited(0))
            listener.onEvent(LaunchBackendEvent.CleanupCompleted)
            release.complete(Unit)
        }
    }
}
