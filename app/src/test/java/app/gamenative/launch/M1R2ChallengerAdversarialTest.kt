package app.gamenative.launch

import app.gamenative.data.GameSource
import app.gamenative.launch.inspect.ExecutableInspectorImpl
import app.gamenative.launch.inspect.PeArchitecture
import app.gamenative.launch.inspect.PeHeaderParser
import com.winlator.container.ContainerData
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class M1R2ChallengerAdversarialTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var gameRoot: File
    private lateinit var inspector: ExecutableInspectorImpl
    private lateinit var resolver: LaunchPrecedenceResolverImpl

    @Before
    fun setUp() {
        gameRoot = tempFolder.newFolder("ChallengerGameRoot")
        inspector = ExecutableInspectorImpl()
        resolver = LaunchPrecedenceResolverImpl()
    }

    private fun createPeFile(fileName: String, bytes: ByteArray): File {
        val file = File(gameRoot, fileName)
        file.writeBytes(bytes)
        return file
    }

    // =========================================================================
    // 1. PE HEADER PARSER ADVERSARIAL STRESS TESTS
    // =========================================================================

    @Test
    fun peHeaderParser_handlesIntegerOverflowPeOffset_0x7FFFFFFF() {
        val file = File(gameRoot, "overflow_max_int.exe")
        val buf = ByteBuffer.allocate(256).order(ByteOrder.LITTLE_ENDIAN)
        buf.putShort(0, 0x5A4D.toShort()) // MZ
        buf.putInt(0x3C, 0x7FFFFFFF) // Max 32-bit positive Int
        file.writeBytes(buf.array())

        val result = PeHeaderParser.parse(file)
        assertEquals(PeArchitecture.UNKNOWN, result.architecture)
        assertFalse(result.hasOpenXRImport)
        assertFalse(result.hasOpenVRImport)
        assertTrue(result.importedDlls.isEmpty())
    }

    @Test
    fun peHeaderParser_handlesIntegerOverflowPeOffset_negativeOffset() {
        val file = File(gameRoot, "overflow_neg.exe")
        val buf = ByteBuffer.allocate(256).order(ByteOrder.LITTLE_ENDIAN)
        buf.putShort(0, 0x5A4D.toShort()) // MZ
        buf.putInt(0x3C, -100) // Negative PE offset
        file.writeBytes(buf.array())

        val result = PeHeaderParser.parse(file)
        assertEquals(PeArchitecture.UNKNOWN, result.architecture)
    }

    @Test
    fun peHeaderParser_handlesExactBoundaryPeOffset() {
        val file = File(gameRoot, "boundary_pe.exe")
        val buf = ByteBuffer.allocate(128).order(ByteOrder.LITTLE_ENDIAN)
        buf.putShort(0, 0x5A4D.toShort()) // MZ
        // bytesRead = 128. bytesRead - 24 = 104.
        buf.putInt(0x3C, 105) // peOffset > 104 -> out of bounds by 1 byte

        file.writeBytes(buf.array())

        val result = PeHeaderParser.parse(file)
        assertEquals(PeArchitecture.UNKNOWN, result.architecture)
    }

    @Test
    fun peHeaderParser_handlesCorruptNumberOfSections() {
        val file = File(gameRoot, "corrupt_sections.exe")
        val buf = ByteBuffer.allocate(512).order(ByteOrder.LITTLE_ENDIAN)
        buf.putShort(0, 0x5A4D.toShort()) // MZ
        buf.putInt(0x3C, 0x80) // peOffset = 128
        buf.putInt(0x80, 0x00004550) // PE\0\0
        buf.putShort(0x84, 0x8664.toShort()) // AMD64
        buf.putShort(0x86, 0xFFFF.toShort()) // 65535 sections!
        buf.putShort(0x94, 240.toShort()) // Optional header size

        file.writeBytes(buf.array())

        val result = PeHeaderParser.parse(file)
        // Should parse architecture correctly without crashing on section iteration
        assertEquals(PeArchitecture.X64_64, result.architecture)
    }

    @Test
    fun peHeaderParser_parsesCaseInsensitiveOpenXRAndOpenVR() {
        val file = File(gameRoot, "case_vr.exe")
        val buf = ByteBuffer.allocate(1024).order(ByteOrder.LITTLE_ENDIAN)
        buf.putShort(0, 0x5A4D.toShort()) // MZ
        buf.putInt(0x3C, 0x80)
        buf.putInt(0x80, 0x00004550)
        buf.putShort(0x84, 0x8664.toShort()) // AMD64

        // Add uppercase strings in header payload
        val openxrStr = "OPENXR_LOADER.DLL".toByteArray(Charsets.ISO_8859_1)
        val openvrStr = "OpenVR_Api.Dll".toByteArray(Charsets.ISO_8859_1)
        System.arraycopy(openxrStr, 0, buf.array(), 0x200, openxrStr.size)
        System.arraycopy(openvrStr, 0, buf.array(), 0x300, openvrStr.size)

        file.writeBytes(buf.array())

        val result = PeHeaderParser.parse(file)
        assertEquals(PeArchitecture.X64_64, result.architecture)
        assertTrue(result.hasOpenXRImport)
        assertTrue(result.hasOpenVRImport)
    }

    // =========================================================================
    // 2. LAUNCH REQUEST PATH VALIDATION ADVERSARIAL TESTS
    // =========================================================================

    @Test
    fun launchRequest_acceptsValidSubdirectoryPath() {
        val req = LaunchRequest(
            launchId = "l1", sessionId = "s1", appId = "10",
            gameSource = GameSource.STEAM, exeRelativePath = "bin/x64/game.exe", containerPath = "/c"
        )
        assertEquals("bin/x64/game.exe", req.exeRelativePath)
    }

    @Test
    fun launchRequest_rejectsTraversalEscape_dotdot() {
        try {
            LaunchRequest(
                launchId = "l1", sessionId = "s1", appId = "10",
                gameSource = GameSource.STEAM, exeRelativePath = "bin/../../etc/passwd", containerPath = "/c"
            )
            fail("Expected rejection of '..'")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("relative path escape") == true)
        }
    }

    @Test
    fun launchRequest_rejectsAbsolutePath_slash() {
        try {
            LaunchRequest(
                launchId = "l1", sessionId = "s1", appId = "10",
                gameSource = GameSource.STEAM, exeRelativePath = "/system/bin/sh", containerPath = "/c"
            )
            fail("Expected rejection of leading slash")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("must not be an absolute path") == true)
        }
    }

    @Test
    fun launchRequest_rejectsAbsolutePath_windowsDrive() {
        try {
            LaunchRequest(
                launchId = "l1", sessionId = "s1", appId = "10",
                gameSource = GameSource.STEAM, exeRelativePath = "C:\\Windows\\notepad.exe", containerPath = "/c"
            )
            fail("Expected rejection of Windows drive absolute path")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("must not be an absolute path") == true)
        }
    }

    // =========================================================================
    // 3. EXECUTABLE INSPECTOR STRICT CONTAINMENT TESTS
    // =========================================================================

    @Test
    fun executableInspector_rejectsSymlinkOrTraversalEscapeOutsideGameRoot() = runTest {
        val outsideDir = tempFolder.newFolder("OutsideDir")
        val outsideExe = File(outsideDir, "secret.exe")
        outsideExe.writeBytes(ByteArray(256) { 0 })

        try {
            inspector.inspect(gameRoot, "../OutsideDir/secret.exe")
            fail("Expected ExecutableInspector to reject path escaping game root")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("escapes game root containment") == true)
        }
    }

    @Test
    fun executableInspector_throwsExecutableNotFound_whenFileIsDirectory() = runTest {
        val subDir = File(gameRoot, "subfolder")
        subDir.mkdirs()

        try {
            inspector.inspect(gameRoot, "subfolder")
            fail("Expected ExecutableNotFound when target path is a directory")
        } catch (e: LaunchFailureException.ExecutableNotFound) {
            assertTrue(e.technicalDetails.contains("subfolder"))
        }
    }

    // =========================================================================
    // 4. LAUNCH PRECEDENCE RESOLVER 5-LEVEL DETERMINISM TESTS
    // =========================================================================

    @Test
    fun precedenceResolver_userEnvVarsOverrideLevel3AndLevel4Vars() {
        val req = LaunchRequest(
            launchId = "l1", sessionId = "s1", appId = "10",
            gameSource = GameSource.STEAM, exeRelativePath = "game.exe", containerPath = gameRoot.absolutePath
        )
        val exeIdent = mockk<app.gamenative.launch.inspect.ExecutableIdentity>(relaxed = true)
        val hwInput = QuestHardwareProfileInput(deviceDescriptor = "QUEST_3")

        val compatInput = CompatibilityResolutionInput(
            isExactMatch = true,
            recommendedEnvVars = mapOf("VAR1" to "compat1", "VAR2" to "compat2", "VAR3" to "compat3")
        )
        val modInput = VrModRequirementInput(
            modId = "uevr",
            requiredEnvVars = mapOf("VAR2" to "mod2", "VAR3" to "mod3")
        )

        val container = mockk<ContainerData>(relaxed = true)
        every { container.envVars } returns "VAR3=user3 VAR4=user4"

        val plan = resolver.resolvePlan(req, exeIdent, hwInput, compatInput, modInput, container)

        // VAR1 comes from compat (Level 3)
        assertEquals("compat1", plan.resolvedEnvVars["VAR1"])
        // The mod is unsupported in flat fallback, so Level 3 remains effective.
        assertEquals("compat2", plan.resolvedEnvVars["VAR2"])
        // VAR3 comes from user container (Level 5 over Level 4 & Level 3)
        assertEquals("user3", plan.resolvedEnvVars["VAR3"])
        // VAR4 comes from user container (Level 5)
        assertEquals("user4", plan.resolvedEnvVars["VAR4"])
    }

    @Test
    fun precedenceResolver_fallbackCompatDoesNotOverrideHardwareDriver() {
        val req = LaunchRequest(
            launchId = "l1", sessionId = "s1", appId = "10",
            gameSource = GameSource.STEAM, exeRelativePath = "game.exe", containerPath = gameRoot.absolutePath
        )
        val exeIdent = mockk<app.gamenative.launch.inspect.ExecutableIdentity>(relaxed = true)
        val hwInput = QuestHardwareProfileInput(
            deviceDescriptor = "QUEST_3",
            graphicsDriver = "Wrapper",
            graphicsDriverConfig = "Turnip v26.2.0 R4"
        )

        val fallbackCompatInput = CompatibilityResolutionInput(
            isExactMatch = false, // Not exact match (e.g. general game fallback)
            recommendedGraphicsDriver = "IncompatibleDriver",
            recommendedGraphicsDriverConfig = "IncompatibleConfig"
        )

        val plan = resolver.resolvePlan(req, exeIdent, hwInput, fallbackCompatInput, null, null)

        // Fallback compatibility profile MUST NOT override hardware critical driver settings!
        assertEquals("Wrapper", plan.graphicsDriver.value)
        assertEquals(SettingSource.HARDWARE_PROFILE, plan.graphicsDriver.source)
        assertEquals("Turnip v26.2.0 R4", plan.graphicsDriverConfig.value)
        assertEquals(SettingSource.HARDWARE_PROFILE, plan.graphicsDriverConfig.source)
    }

    @Test
    fun precedenceResolver_exactCompatOverridesHardwareDriver() {
        val req = LaunchRequest(
            launchId = "l1", sessionId = "s1", appId = "10",
            gameSource = GameSource.STEAM, exeRelativePath = "game.exe", containerPath = gameRoot.absolutePath
        )
        val exeIdent = mockk<app.gamenative.launch.inspect.ExecutableIdentity>(relaxed = true)
        val hwInput = QuestHardwareProfileInput(
            deviceDescriptor = "QUEST_3",
            graphicsDriver = "Wrapper",
            graphicsDriverConfig = "Turnip v26.2.0 R4"
        )

        val exactCompatInput = CompatibilityResolutionInput(
            isExactMatch = true, // Exact match
            recommendedGraphicsDriver = "ExactDriver",
            recommendedGraphicsDriverConfig = "ExactConfig"
        )

        val plan = resolver.resolvePlan(req, exeIdent, hwInput, exactCompatInput, null, null)

        // Exact match SHOULD override hardware driver settings
        assertEquals("ExactDriver", plan.graphicsDriver.value)
        assertEquals(SettingSource.COMPATIBILITY_PROFILE, plan.graphicsDriver.source)
        assertEquals("ExactConfig", plan.graphicsDriverConfig.value)
        assertEquals(SettingSource.COMPATIBILITY_PROFILE, plan.graphicsDriverConfig.source)
    }
}
