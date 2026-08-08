package app.gamenative.launch.vr

import app.gamenative.launch.LaunchRequest
import app.gamenative.launch.RequestedLaunchMode
import app.gamenative.launch.inspect.ExecutableIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

class TrackingModeResolverTest {

    private val resolver = TrackingModeResolverImpl()

    private fun createMockRequest(): LaunchRequest {
        return LaunchRequest(
            launchId = "test-launch",
            sessionId = "test-session",
            appId = "test-app",
            gameSource = app.gamenative.data.GameSource.STEAM,
            exeRelativePath = "game.exe",
            containerPath = "/test/path"
        )
    }

    private fun createMockIdentity(): ExecutableIdentity {
        return mock(ExecutableIdentity::class.java)
    }

    @Test
    fun testOpenXrImportWithoutValidatedRuntimeReturnsFlat3dof() {
        val decision = resolver.resolveTrackingMode(
            request = createMockRequest(),
            executableIdentity = createMockIdentity(),
            hasVrModManifest = false,
            isNativeVrSupported = true // Has OpenXR import but no validated runtime
        )

        assertEquals(TrackingMode.FLAT_3DOF, decision.mode)
        assertTrue(decision.isFallback)
        assertTrue(decision.rationale.contains("Executable imports OpenXR/OpenVR but native injection is unsupported"))
    }

    @Test
    fun testModRequestWithoutValidationReturnsFlat3dof() {
        val decision = resolver.resolveTrackingMode(
            request = createMockRequest(),
            executableIdentity = createMockIdentity(),
            hasVrModManifest = true,
            isNativeVrSupported = false
        )

        assertEquals(TrackingMode.FLAT_3DOF, decision.mode)
        assertTrue(decision.isFallback)
        assertTrue(decision.rationale.contains("VR mod manifest was requested but cannot be fulfilled"))
    }

    @Test
    fun testNoVrEvidenceReturnsFlat3dof() {
        val decision = resolver.resolveTrackingMode(
            request = createMockRequest(),
            executableIdentity = createMockIdentity(),
            hasVrModManifest = false,
            isNativeVrSupported = false
        )

        assertEquals(TrackingMode.FLAT_3DOF, decision.mode)
        assertTrue(decision.isFallback)
        assertTrue(decision.rationale.contains("No guest OpenXR/OpenVR runtime bridge is available"))
    }
}
