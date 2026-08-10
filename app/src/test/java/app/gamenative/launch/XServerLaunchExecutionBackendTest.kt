package app.gamenative.launch

import app.gamenative.launch.backend.XServerLaunchExecutionBackend
import app.gamenative.launch.backend.XServerLaunchSessionRegistry
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XServerLaunchExecutionBackendTest {
    @After
    fun tearDown() {
        XServerLaunchSessionRegistry.current()?.cleanupCompleted()
    }

    @Test
    fun `real callbacks update handle and preserve lifecycle ordering`() = runBlocking {
        val events = mutableListOf<LaunchBackendEvent>()
        val plan = mockk<LaunchPlan>(relaxed = true) {
            io.mockk.every { launchId } returns "launch-live"
        }
        val backend = XServerLaunchExecutionBackend(
            backendIdentity = LaunchBackendIdentity.BIONIC,
            launchAction = { _, lifecycle ->
                lifecycle.prefixPreparationStarted()
                lifecycle.prefixPreparationCompleted()
                lifecycle.environmentStarting()
                lifecycle.guestCommandSubmitted("<resolved-game-command>")
                lifecycle.guestPidObserved(4321)
                lifecycle.environmentComponentsStarted()
            },
            stopAction = {},
        )

        val handle = backend.start(plan) { events += it }
        XServerLaunchSessionRegistry.firstWindowObserved(17, 4321)
        XServerLaunchSessionRegistry.current()!!.guestExited(0)
        XServerLaunchSessionRegistry.cleanupCompleted()

        assertEquals(4321, handle.rootPid.value)
        assertEquals(LaunchTermination.Exited(0), handle.termination.await())
        assertEquals(1, events.count { it is LaunchBackendEvent.FirstWindowObserved })
        assertTrue(events.last() is LaunchBackendEvent.CleanupCompleted)
        assertNull(XServerLaunchSessionRegistry.current())
    }

    @Test
    fun `cancellation stops and cleans up exactly once`() = runBlocking {
        val events = mutableListOf<LaunchBackendEvent>()
        var stopCount = 0
        var cleanupCount = 0
        val plan = mockk<LaunchPlan>(relaxed = true) {
            io.mockk.every { launchId } returns "launch-cancel"
        }
        val backend = XServerLaunchExecutionBackend(
            backendIdentity = LaunchBackendIdentity.GLIBC,
            launchAction = { _, _ -> },
            stopAction = { stopCount++ },
            cleanupAction = { cleanupCount++ },
        )

        val handle = backend.start(plan) { events += it }
        handle.cancel("user")
        handle.cancel("duplicate")

        assertEquals(1, stopCount)
        assertEquals(1, cleanupCount)
        assertEquals(1, events.count { it is LaunchBackendEvent.Cancelled })
        assertEquals(1, events.count { it is LaunchBackendEvent.CleanupCompleted })
        assertEquals(LaunchTermination.Cancelled("user"), handle.termination.await())
        assertNull(XServerLaunchSessionRegistry.current())
    }

    @Test
    fun `cancellation during setup defers overlay cleanup until setup returns`() = runBlocking {
        val events = mutableListOf<LaunchBackendEvent>()
        val setupStarted = CompletableDeferred<Unit>()
        val allowSetupToReturn = CompletableDeferred<Unit>()
        var stopCount = 0
        var cleanupCount = 0
        lateinit var handle: RunningLaunchHandle
        val plan = mockk<LaunchPlan>(relaxed = true) {
            io.mockk.every { launchId } returns "launch-setup-cancel"
        }
        val backend = XServerLaunchExecutionBackend(
            backendIdentity = LaunchBackendIdentity.BIONIC,
            launchAction = { _, _ ->
                setupStarted.complete(Unit)
                allowSetupToReturn.await()
            },
            stopAction = { stopCount++ },
            cleanupAction = { cleanupCount++ },
        )

        val start = async {
            backend.start(plan, { events += it }) { startedHandle -> handle = startedHandle }
        }
        setupStarted.await()
        handle.cancel("user")

        assertEquals(1, stopCount)
        assertEquals(0, cleanupCount)
        allowSetupToReturn.complete(Unit)
        start.await()

        assertEquals(2, stopCount)
        assertEquals(1, cleanupCount)
        assertEquals(1, events.count { it is LaunchBackendEvent.Cancelled })
        assertEquals(1, events.count { it is LaunchBackendEvent.CleanupCompleted })
        assertNull(XServerLaunchSessionRegistry.current())
    }

    @Test(expected = IllegalStateException::class)
    fun `second live session is rejected across backend instances`() {
        runBlocking {
            val firstPlan = mockk<LaunchPlan>(relaxed = true) {
                io.mockk.every { launchId } returns "launch-one"
            }
            val secondPlan = mockk<LaunchPlan>(relaxed = true) {
                io.mockk.every { launchId } returns "launch-two"
            }
            val first = XServerLaunchExecutionBackend(LaunchBackendIdentity.BIONIC, { _, _ -> }, {})
            val second = XServerLaunchExecutionBackend(LaunchBackendIdentity.BIONIC, { _, _ -> }, {})

            first.start(firstPlan) { }
            second.start(secondPlan) { }
        }
    }
}
