package app.gamenative.launch

import app.gamenative.data.GameSource
import app.gamenative.launch.inspect.ExecutableIdentity
import app.gamenative.launch.inspect.PeArchitecture
import com.winlator.container.ContainerData
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class LaunchPrecedenceResolverTest {

    private lateinit var resolver: LaunchPrecedenceResolverImpl
    private lateinit var mockRequest: LaunchRequest
    private lateinit var mockExeIdentity: ExecutableIdentity
    private lateinit var quest2Hardware: QuestHardwareProfileInput

    @Before
    fun setUp() {
        resolver = LaunchPrecedenceResolverImpl()
        mockRequest = LaunchRequest(
            launchId = "launch-123",
            sessionId = "session-456",
            appId = "730",
            gameSource = GameSource.STEAM,
            exeRelativePath = "csgo.exe",
            containerPath = "/sdcard/game"
        )
        mockExeIdentity = ExecutableIdentity(
            canonicalPath = "/sdcard/game/csgo.exe",
            relativePath = "csgo.exe",
            fileSize = 1024,
            lastModified = System.currentTimeMillis(),
            sha256 = "abc123hash",
            architecture = PeArchitecture.X64_64,
            isLauncher = false,
            importedLibraries = emptyList(),
            hasOpenXRImport = false,
            hasOpenVRImport = false
        )
        quest2Hardware = QuestHardwareProfileInput(
            deviceDescriptor = "QUEST_2",
            graphicsDriver = "Wrapper",
            graphicsDriverConfig = "Turnip v26.2.0 R4",
            dxwrapper = "dxvk",
            dxwrapperConfig = "1.11.1-sarek"
        )
    }

    @Test
    fun resolvePlan_appliesHardwareProfile_whenNoUserOrCompatOverridesExist() {
        val plan = resolver.resolvePlan(mockRequest, mockExeIdentity, quest2Hardware, null, null, null)

        assertEquals("QUEST_2", plan.detectedDeviceDescriptor)
        assertEquals("Wrapper", plan.graphicsDriver.value)
        assertEquals(SettingSource.HARDWARE_PROFILE, plan.graphicsDriver.source)
        assertEquals("1.11.1-sarek", plan.dxwrapperConfig.value)
        assertEquals(SettingSource.HARDWARE_PROFILE, plan.dxwrapperConfig.source)
    }

    @Test
    fun resolvePlan_protectsHardwareDriver_whenCompatibilityMatchIsFallback() {
        val fallbackCompat = CompatibilityResolutionInput(
            isExactMatch = false, // Fallback match across different GPU
            recommendedGraphicsDriver = "IncompatibleDriverOverride",
            recommendedDxwrapperConfig = "2.4.1-gplasync"
        )

        val plan = resolver.resolvePlan(mockRequest, mockExeIdentity, quest2Hardware, fallbackCompat, null, null)

        // Hardware driver must NOT be replaced by fallback match!
        assertEquals("Wrapper", plan.graphicsDriver.value)
        assertEquals(SettingSource.HARDWARE_PROFILE, plan.graphicsDriver.source)
        assertEquals("1.11.1-sarek", plan.dxwrapperConfig.value)
        assertEquals(SettingSource.HARDWARE_PROFILE, plan.dxwrapperConfig.source)
    }

    @Test
    fun resolvePlan_appliesCompatibilityDriver_whenMatchIsExact() {
        val exactCompat = CompatibilityResolutionInput(
            isExactMatch = true, // Exact title & GPU match
            recommendedGraphicsDriver = "TurnipCustom",
            recommendedDxwrapperConfig = "2.4.1-gplasync"
        )

        val plan = resolver.resolvePlan(mockRequest, mockExeIdentity, quest2Hardware, exactCompat, null, null)

        // Exact match CAN replace hardware driver
        assertEquals("TurnipCustom", plan.graphicsDriver.value)
        assertEquals(SettingSource.COMPATIBILITY_PROFILE, plan.graphicsDriver.source)
    }

    @Test
    fun resolvePlan_appliesUserOverride_overAllOtherLevels() {
        val userContainer = mockk<ContainerData>(relaxed = true)
        every { userContainer.graphicsDriver } returns "UserCustomDriver"

        val plan = resolver.resolvePlan(mockRequest, mockExeIdentity, quest2Hardware, null, null, userContainer)

        assertEquals("UserCustomDriver", plan.graphicsDriver.value)
        assertEquals(SettingSource.USER_OVERRIDE, plan.graphicsDriver.source)
    }

    @Test
    fun resolvePlan_livePersistedDefaultsDoNotShadowHardwareProfile() {
        val persistedContainer = ContainerData(
            graphicsDriver = "Turnip",
            graphicsDriverConfig = "vulkanVersion=1.3",
            dxwrapper = "dxvk",
        )
        val liveRequest = mockRequest.copy(explicitContainerOverrideFields = emptySet())

        val plan = resolver.resolvePlan(liveRequest, mockExeIdentity, quest2Hardware, null, null, persistedContainer)

        assertEquals("Wrapper", plan.graphicsDriver.value)
        assertEquals(SettingSource.HARDWARE_PROFILE, plan.graphicsDriver.source)
    }

    @Test
    fun resolvePlan_liveExplicitFieldStillOverridesHardwareProfile() {
        val persistedContainer = ContainerData(graphicsDriver = "UserCustomDriver")
        val liveRequest = mockRequest.copy(
            explicitContainerOverrideFields = setOf(ContainerExecutionOverrideDetector.GRAPHICS_DRIVER),
        )

        val plan = resolver.resolvePlan(liveRequest, mockExeIdentity, quest2Hardware, null, null, persistedContainer)

        assertEquals("UserCustomDriver", plan.graphicsDriver.value)
        assertEquals(SettingSource.USER_OVERRIDE, plan.graphicsDriver.source)
    }

    @Test
    fun resolvePlan_ignoresUnsupportedModEnvironment_andAppliesUserOverCompatibility() {
        val compatInput = CompatibilityResolutionInput(
            isExactMatch = true,
            recommendedEnvVars = mapOf("SHARED_VAR" to "compat_value", "COMPAT_VAR" to "1")
        )
        val modInput = VrModRequirementInput(
            modId = "test_mod",
            requiredEnvVars = mapOf("SHARED_VAR" to "mod_value", "MOD_VAR" to "1")
        )
        val userContainer = mockk<ContainerData>(relaxed = true)
        every { userContainer.envVars } returns "SHARED_VAR=user_value USER_VAR=1"

        val plan = resolver.resolvePlan(
            request = mockRequest,
            exeIdentity = mockExeIdentity,
            hardwareInput = quest2Hardware,
            compatibilityInput = compatInput,
            modInput = modInput,
            userContainer = userContainer
        )

        assertEquals("user_value", plan.resolvedEnvVars["SHARED_VAR"])
        assertEquals("1", plan.resolvedEnvVars["COMPAT_VAR"])
        assertEquals(null, plan.resolvedEnvVars["MOD_VAR"])
        assertEquals("1", plan.resolvedEnvVars["USER_VAR"])
    }
}
