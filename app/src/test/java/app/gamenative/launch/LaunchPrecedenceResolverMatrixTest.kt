package app.gamenative.launch

import app.gamenative.data.GameSource
import app.gamenative.launch.inspect.ExecutableIdentity
import app.gamenative.launch.inspect.PeArchitecture
import com.winlator.container.ContainerData
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LaunchPrecedenceResolverMatrixTest {

    private lateinit var resolver: LaunchPrecedenceResolverImpl
    private lateinit var mockRequest: LaunchRequest
    private lateinit var mockExeIdentity: ExecutableIdentity
    private lateinit var questHardware: QuestHardwareProfileInput

    @Before
    fun setUp() {
        resolver = LaunchPrecedenceResolverImpl()
        mockRequest = LaunchRequest(
            launchId = "matrix-launch-1",
            sessionId = "matrix-session-1",
            appId = "550",
            gameSource = GameSource.STEAM,
            exeRelativePath = "left4dead2.exe",
            containerPath = "/sdcard/l4d2"
        )
        mockExeIdentity = ExecutableIdentity(
            canonicalPath = "/sdcard/l4d2/left4dead2.exe",
            relativePath = "left4dead2.exe",
            fileSize = 4096,
            lastModified = System.currentTimeMillis(),
            sha256 = "deadbeef12345678",
            architecture = PeArchitecture.X64_64,
            isLauncher = false,
            importedLibraries = listOf("vulkan-1.dll"),
            hasOpenXRImport = false,
            hasOpenVRImport = false
        )
        questHardware = QuestHardwareProfileInput(
            deviceDescriptor = "QUEST_3",
            containerVariant = "bionic",
            wineVersion = "proton-10.0-arm64ec-2",
            graphicsDriver = "Wrapper",
            graphicsDriverConfig = "Turnip v26.2.0 R4",
            dxwrapper = "dxvk",
            dxwrapperConfig = "2.1-custom"
        )
    }

    @Test
    fun resolvePlan_full5LevelPrecedenceMatrix() {
        // Level 3 Compatibility Profile (Exact match)
        val compatInput = CompatibilityResolutionInput(
            isExactMatch = true,
            recommendedVariant = "glibc",
            recommendedWineVersion = "wine-9.0-staging",
            recommendedGraphicsDriver = "TurnipCompat",
            recommendedGraphicsDriverConfig = "CompatConfig",
            recommendedDxwrapper = "vkd3d",
            recommendedDxwrapperConfig = "2.8-compat",
            recommendedEnvVars = mapOf("COMPAT_ENV" to "1", "SHARED_ENV" to "compat")
        )

        // Level 4 VR Mod Requirement
        val modInput = VrModRequirementInput(
            modId = "l4d2_vr_mod",
            requiredDllOverrides = mapOf("openvr_api" to "n,b", "dxgi" to "n"),
            requiredEnvVars = mapOf("MOD_ENV" to "1", "SHARED_ENV" to "mod"),
            targetTrackingMode = "MODDED_6DOF"
        )

        // Level 5 User Override
        val userContainer = mockk<ContainerData>(relaxed = true)
        every { userContainer.containerVariant } returns "bionic_user"
        every { userContainer.wineVersion } returns "" // Blank -> Should fall back to lower level
        every { userContainer.graphicsDriver } returns "UserDriver"
        every { userContainer.graphicsDriverConfig } returns "" // Blank -> Should fall back to lower level

        val plan = resolver.resolvePlan(
            request = mockRequest,
            exeIdentity = mockExeIdentity,
            hardwareInput = questHardware,
            compatibilityInput = compatInput,
            modInput = modInput,
            userContainer = userContainer
        )

        // Level 5 User Override takes precedence when non-blank
        assertEquals("bionic_user", plan.containerVariant.value)
        assertEquals(SettingSource.USER_OVERRIDE, plan.containerVariant.source)

        assertEquals("UserDriver", plan.graphicsDriver.value)
        assertEquals(SettingSource.USER_OVERRIDE, plan.graphicsDriver.source)

        // Blank Level 5 User Override falls back to Level 3 Compatibility (since exact match)
        assertEquals("wine-9.0-staging", plan.wineVersion.value)
        assertEquals(SettingSource.COMPATIBILITY_PROFILE, plan.wineVersion.source)

        assertEquals("CompatConfig", plan.graphicsDriverConfig.value)
        assertEquals(SettingSource.COMPATIBILITY_PROFILE, plan.graphicsDriverConfig.source)

        // Level 4 VR Mod Tracking Mode & DLL Overrides
        assertEquals(app.gamenative.launch.vr.TrackingMode.MODDED_6DOF, plan.trackingMode)
        assertEquals("l4d2_vr_mod", plan.activeModId)
        assertEquals("n,b", plan.resolvedDllOverrides["openvr_api"])
        assertEquals("n", plan.resolvedDllOverrides["dxgi"])

        // Environment variables merged with Mod (Level 4) overriding Compat (Level 3) for duplicate keys
        assertEquals("1", plan.resolvedEnvVars["COMPAT_ENV"])
        assertEquals("1", plan.resolvedEnvVars["MOD_ENV"])
        assertEquals("mod", plan.resolvedEnvVars["SHARED_ENV"])
    }

    @Test
    fun resolvePlan_verifiesTrackingModeFallbackChain() {
        // Case 1: Mod present -> MODDED_6DOF
        val modInput = VrModRequirementInput(modId = "some_mod")
        val planMod = resolver.resolvePlan(mockRequest, mockExeIdentity, questHardware, null, modInput, null)
        assertEquals(app.gamenative.launch.vr.TrackingMode.MODDED_6DOF, planMod.trackingMode)

        // Case 2: OpenXR import present -> NATIVE_OPENXR_6DOF
        val openXrExe = mockExeIdentity.copy(hasOpenXRImport = true)
        val planOpenXR = resolver.resolvePlan(mockRequest, openXrExe, questHardware, null, null, null)
        assertEquals(app.gamenative.launch.vr.TrackingMode.NATIVE_OPENXR_6DOF, planOpenXR.trackingMode)

        // Case 3: OpenVR import present -> NATIVE_OPENXR_6DOF
        val openVrExe = mockExeIdentity.copy(hasOpenVRImport = true)
        val planOpenVR = resolver.resolvePlan(mockRequest, openVrExe, questHardware, null, null, null)
        assertEquals(app.gamenative.launch.vr.TrackingMode.NATIVE_OPENXR_6DOF, planOpenVR.trackingMode)

        // Case 4: No VR imports, no mod -> FLAT_3DOF
        val planFlat = resolver.resolvePlan(mockRequest, mockExeIdentity, questHardware, null, null, null)
        assertEquals(app.gamenative.launch.vr.TrackingMode.FLAT_3DOF, planFlat.trackingMode)
    }
}
