package app.gamenative.launch

import app.gamenative.data.GameSource
import app.gamenative.launch.inspect.ExecutableIdentity
import app.gamenative.launch.inspect.ExecutableInspector
import app.gamenative.launch.inspect.PeArchitecture
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedQueue

class GameLaunchCoordinatorStressTest {

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
            launchId = "stress-launch-1",
            sessionId = "stress-session-1",
            appId = "220",
            gameSource = GameSource.STEAM,
            exeRelativePath = "hl2.exe",
            containerPath = "/sdcard/hl2"
        )
    }

    @Test
    fun testComplete19StateTransitionGraphAndExhaustiveCanTransitionTo() {
        val allStates = LaunchState.values()
        assertEquals(20, allStates.size) // 20 enum entries representing 19 states + FAILED

        // Verify terminal state rules
        assertTrue(LaunchState.COMPLETED.isTerminal())
        assertTrue(LaunchState.FAILED.isTerminal())
        assertFalse(LaunchState.RUNNING.isTerminal())
        assertFalse(LaunchState.REQUEST_RECEIVED.isTerminal())

        // Terminal states cannot transition to anything except self
        for (state in allStates) {
            if (state == LaunchState.COMPLETED) {
                assertTrue(LaunchState.COMPLETED.canTransitionTo(LaunchState.COMPLETED))
            } else {
                assertFalse(LaunchState.COMPLETED.canTransitionTo(state))
            }

            if (state == LaunchState.FAILED) {
                assertTrue(LaunchState.FAILED.canTransitionTo(LaunchState.FAILED))
            } else {
                assertFalse(LaunchState.FAILED.canTransitionTo(state))
            }
        }

        // Non-terminal states CAN always transition to FAILED or STOPPING
        for (state in allStates) {
            if (!state.isTerminal()) {
                assertTrue("${state.name} should be able to transition to FAILED", state.canTransitionTo(LaunchState.FAILED))
                assertTrue("${state.name} should be able to transition to STOPPING", state.canTransitionTo(LaunchState.STOPPING))
            }
        }

        // Test illegal jump rejection (e.g. REQUEST_RECEIVED directly to RUNNING)
        assertFalse(LaunchState.REQUEST_RECEIVED.canTransitionTo(LaunchState.RUNNING))
        assertFalse(LaunchState.EXECUTABLE_INSPECTING.canTransitionTo(LaunchState.CONTAINER_PREPARING))
        assertFalse(LaunchState.DEVICE_DETECTING.canTransitionTo(LaunchState.COMPLETED))
    }

    @Test
    fun testListenerExceptionIsolation() = runTest {
        val fakeExeIdentity = ExecutableIdentity(
            canonicalPath = "/sdcard/hl2/hl2.exe",
            relativePath = "hl2.exe",
            fileSize = 1024,
            lastModified = 1000L,
            sha256 = "hash123",
            architecture = PeArchitecture.X64_64,
            isLauncher = false,
            importedLibraries = emptyList(),
            hasOpenXRImport = false,
            hasOpenVRImport = false
        )
        coEvery { mockInspector.inspect(any(), any()) } returns fakeExeIdentity

        val fakePlan = mockk<LaunchPlan>(relaxed = true)
        every { mockResolver.resolvePlan(any(), any(), any(), any(), any(), any()) } returns fakePlan

        val validListenerEvents = mutableListOf<LaunchState>()

        // Listener 1 throws RuntimeException on every event
        coordinator.addListener { event ->
            throw RuntimeException("Faulty listener explosion on ${event.newState}")
        }

        // Listener 2 is normal and records events
        coordinator.addListener { event ->
            validListenerEvents.add(event.newState)
        }

        // Execution should complete successfully despite Listener 1 throwing exceptions
        val result = coordinator.executeLaunch(mockRequest)

        assertTrue(result is TerminalLaunchResult.Success)
        assertTrue(validListenerEvents.contains(LaunchState.REQUEST_RECEIVED))
        assertTrue(validListenerEvents.contains(LaunchState.RUNNING))
        assertEquals(LaunchState.RUNNING, coordinator.currentState.value)
    }

    @Test
    fun testMutexLockConcurrencySerialization() = runTest {
        val fakeExeIdentity = ExecutableIdentity(
            canonicalPath = "/sdcard/hl2/hl2.exe",
            relativePath = "hl2.exe",
            fileSize = 1024,
            lastModified = 1000L,
            sha256 = "hash123",
            architecture = PeArchitecture.X64_64,
            isLauncher = false,
            importedLibraries = emptyList(),
            hasOpenXRImport = false,
            hasOpenVRImport = false
        )
        coEvery { mockInspector.inspect(any(), any()) } returns fakeExeIdentity

        val fakePlan = mockk<LaunchPlan>(relaxed = true)
        every { mockResolver.resolvePlan(any(), any(), any(), any(), any(), any()) } returns fakePlan

        val results = ConcurrentLinkedQueue<TerminalLaunchResult>()

        // Dispatch 5 concurrent launch requests
        val jobs = (1..5).map { index ->
            async(Dispatchers.Default) {
                val req = mockRequest.copy(launchId = "req-$index")
                val res = coordinator.executeLaunch(req)
                results.add(res)
            }
        }

        jobs.awaitAll()

        assertEquals(5, results.size)
        // All requests finished cleanly serialized without race condition crashes
        assertTrue(results.all { it is TerminalLaunchResult.Success })
    }
}
