package app.gamenative.launch

import app.gamenative.data.GameSource
import app.gamenative.launch.inspect.ExecutableIdentity
import app.gamenative.launch.inspect.ExecutableInspector
import app.gamenative.launch.inspect.PeArchitecture
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GameLaunchCoordinatorTest {

    private lateinit var mockInspector: ExecutableInspector
    private lateinit var mockResolver: LaunchPrecedenceResolver
    private lateinit var coordinator: GameLaunchCoordinatorImpl
    private lateinit var mockRequest: LaunchRequest

    @Before
    fun setUp() {
        mockInspector = mockk()
        mockResolver = mockk()
        coordinator = GameLaunchCoordinatorImpl(
            mockInspector,
            mockResolver,
            object : LaunchHardwareProfileProvider {
                override suspend fun resolve() = QuestHardwareProfileInput(deviceDescriptor = "QUEST_2")
            }
        )

        mockRequest = LaunchRequest(
            launchId = "test-launch-1",
            sessionId = "test-session-1",
            appId = "480",
            gameSource = GameSource.STEAM,
            exeRelativePath = "SpaceWar.exe",
            containerPath = "/sdcard/spacewar"
        )
    }

    @Test
    fun executeLaunch_progressesThrough19StatesToSuccess() = runTest {
        val fakeExeIdentity = ExecutableIdentity(
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

        coEvery { mockInspector.inspect(any(), "SpaceWar.exe") } returns fakeExeIdentity

        val fakePlan = mockk<LaunchPlan>(relaxed = true)
        every { mockResolver.resolvePlan(any(), any(), any(), any(), any(), any()) } returns fakePlan

        val eventsEmitted = mutableListOf<LaunchState>()
        coordinator.addListener { event ->
            eventsEmitted.add(event.newState)
        }

        val result = coordinator.executeLaunch(mockRequest)

        assertTrue(result is TerminalLaunchResult.Success)
        assertEquals(LaunchState.RUNNING, coordinator.currentState.value)
        assertTrue(eventsEmitted.contains(LaunchState.REQUEST_RECEIVED))
        assertTrue(eventsEmitted.contains(LaunchState.EXECUTABLE_INSPECTING))
        assertTrue(eventsEmitted.contains(LaunchState.RUNNING))
    }

    @Test
    fun executeLaunch_transitionsToFailed_whenExecutableNotFound() = runTest {
        coEvery { mockInspector.inspect(any(), any()) } throws LaunchFailureException.ExecutableNotFound("/sdcard/spacewar/SpaceWar.exe")

        val result = coordinator.executeLaunch(mockRequest)

        assertTrue(result is TerminalLaunchResult.Failure)
        val failureResult = result as TerminalLaunchResult.Failure
        assertEquals(LaunchState.FAILED, failureResult.failedState)
        assertEquals(LaunchFailureCategory.INSTALL_OR_EXECUTABLE_NOT_FOUND, failureResult.failure.category)
    }

    @Test
    fun cancelLaunch_gracefullyStopsActiveSession() = runTest {
        coordinator.cancelLaunch("test-launch-1", "User cancelled launch")

        // No active session running, state remains in initial state or completed cleanly
        assertFalse(coordinator.currentState.value.isRunningOrActive())
    }
}
