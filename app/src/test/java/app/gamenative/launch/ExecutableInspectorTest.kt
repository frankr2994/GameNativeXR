package app.gamenative.launch

import app.gamenative.launch.inspect.ExecutableInspectorImpl
import app.gamenative.launch.inspect.PeArchitecture
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

class ExecutableInspectorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var inspector: ExecutableInspectorImpl
    private lateinit var gameRoot: File

    @Before
    fun setUp() {
        inspector = ExecutableInspectorImpl()
        gameRoot = tempFolder.newFolder("TestGame")
    }

    /** Helper to generate synthetic PE binary files with specific machine headers and string imports. */
    private fun createSyntheticPeFile(fileName: String, machine: Short, imports: List<String>): File {
        val file = File(gameRoot, fileName)
        val buffer = ByteBuffer.allocate(2048).order(ByteOrder.LITTLE_ENDIAN)

        // 1. DOS Header: "MZ" at offset 0, e_lfanew = 0x80 at offset 0x3C
        buffer.putShort(0, 0x5A4D.toShort())
        buffer.putInt(0x3C, 0x80)

        // 2. PE Header: "PE\0\0" at offset 0x80
        buffer.putInt(0x80, 0x00004550)

        // 3. File Header Machine Type at offset 0x84
        buffer.putShort(0x84, machine)

        // 4. Optional Header Magic (PE32) at offset 0x98
        buffer.putShort(0x98, 0x010B.toShort())

        // 5. Append import strings into payload section
        var pos = 0x100
        imports.forEach { importStr ->
            val bytes = importStr.toByteArray(Charsets.ISO_8859_1)
            System.arraycopy(bytes, 0, buffer.array(), pos, bytes.size)
            pos += bytes.size + 1
        }

        file.writeBytes(buffer.array())
        return file
    }

    @Test
    fun inspect_parses32BitX86ExecutableCorrectly() = runTest {
        createSyntheticPeFile("game32.exe", 0x014C.toShort(), emptyList())
        val identity = inspector.inspect(gameRoot, "game32.exe")

        assertEquals(PeArchitecture.X86_32, identity.architecture)
        assertFalse(identity.isLauncher)
        assertFalse(identity.hasOpenXRImport)
        assertFalse(identity.hasOpenVRImport)
        assertEquals(64, identity.sha256.length) // Hex string SHA-256 length
    }

    @Test
    fun inspect_parses64BitX64ExecutableWithOpenXRImport() = runTest {
        createSyntheticPeFile("game64.exe", 0x8664.toShort(), listOf("openxr_loader.dll", "vulkan-1.dll"))
        val identity = inspector.inspect(gameRoot, "game64.exe")

        assertEquals(PeArchitecture.X64_64, identity.architecture)
        assertTrue(identity.hasOpenXRImport)
        assertFalse(identity.hasOpenVRImport)
    }

    @Test
    fun inspect_parsesArm64ExecutableCorrectly() = runTest {
        createSyntheticPeFile("game_arm64.exe", 0xAA64.toShort(), emptyList())
        val identity = inspector.inspect(gameRoot, "game_arm64.exe")

        assertEquals(PeArchitecture.ARM64, identity.architecture)
        assertFalse(identity.hasOpenXRImport)
        assertFalse(identity.hasOpenVRImport)
    }

    @Test
    fun inspect_parsesUnknownMachineTypeAsUnknown() = runTest {
        createSyntheticPeFile("game_unknown.exe", 0x01C0.toShort(), emptyList())
        val identity = inspector.inspect(gameRoot, "game_unknown.exe")

        assertEquals(PeArchitecture.UNKNOWN, identity.architecture)
    }

    @Test
    fun inspect_handlesZeroByteFileGracefully() = runTest {
        val file = File(gameRoot, "zero_byte.exe")
        file.writeBytes(ByteArray(0))
        val identity = inspector.inspect(gameRoot, "zero_byte.exe")

        assertEquals(PeArchitecture.UNKNOWN, identity.architecture)
        assertEquals(0L, identity.fileSize)
        assertFalse(identity.hasOpenXRImport)
        assertFalse(identity.hasOpenVRImport)
        assertTrue(identity.importedLibraries.isEmpty())
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", identity.sha256)
    }

    @Test
    fun inspect_handlesFileSmallerThan128BytesGracefully() = runTest {
        val file = File(gameRoot, "small_file.exe")
        file.writeBytes(ByteArray(80) { 0x41 })
        val identity = inspector.inspect(gameRoot, "small_file.exe")

        assertEquals(PeArchitecture.UNKNOWN, identity.architecture)
        assertEquals(80L, identity.fileSize)
        assertFalse(identity.hasOpenXRImport)
        assertFalse(identity.hasOpenVRImport)
    }

    @Test
    fun inspect_handlesNonPeBinaryGracefully() = runTest {
        val file = File(gameRoot, "not_a_pe.bin")
        file.writeBytes(ByteArray(512) { (it % 256).toByte() })
        val identity = inspector.inspect(gameRoot, "not_a_pe.bin")

        assertEquals(PeArchitecture.UNKNOWN, identity.architecture)
        assertFalse(identity.hasOpenXRImport)
        assertFalse(identity.hasOpenVRImport)
    }

    @Test
    fun inspect_handlesCorruptPeHeader_invalidPeOffset() = runTest {
        val file = File(gameRoot, "corrupt_pe_offset.exe")
        val buffer = ByteBuffer.allocate(256).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putShort(0, 0x5A4D.toShort()) // MZ
        buffer.putInt(0x3C, -1) // Invalid negative peOffset
        file.writeBytes(buffer.array())

        val identity = inspector.inspect(gameRoot, "corrupt_pe_offset.exe")
        assertEquals(PeArchitecture.UNKNOWN, identity.architecture)
    }

    @Test
    fun inspect_handlesIntegerOverflowPeOffset_gracefully() = runTest {
        val file = File(gameRoot, "overflow_pe_offset.exe")
        val buffer = ByteBuffer.allocate(256).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putShort(0, 0x5A4D.toShort()) // MZ
        buffer.putInt(0x3C, 0x7FFFFFF8) // Integer overflow vulnerable offset (2147483640)
        file.writeBytes(buffer.array())

        val identity = inspector.inspect(gameRoot, "overflow_pe_offset.exe")
        assertEquals(PeArchitecture.UNKNOWN, identity.architecture)
        assertFalse(identity.hasOpenXRImport)
        assertFalse(identity.hasOpenVRImport)
    }

    @Test
    fun inspect_rejectsCanonicalPathTraversalEscape() = runTest {
        val outsideFile = tempFolder.newFile("outside_game.exe")
        outsideFile.writeBytes(ByteArray(256) { 0 })

        val relativeEscapePath = "../${outsideFile.name}"
        try {
            inspector.inspect(gameRoot, relativeEscapePath)
            fail("Expected IllegalArgumentException for canonical path containment violation")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("escapes game root containment") == true)
        }
    }

    @Test
    fun inspect_parsesDynamicRvaImportDirectory() = runTest {
        val file = File(gameRoot, "dynamic_rva_game.exe")
        val buffer = ByteBuffer.allocate(2048).order(ByteOrder.LITTLE_ENDIAN)

        // 1. DOS Header: MZ at 0, e_lfanew = 0x80 at 0x3C
        buffer.putShort(0, 0x5A4D.toShort())
        buffer.putInt(0x3C, 0x80)

        // 2. PE Header: PE\0\0 at 0x80
        buffer.putInt(0x80, 0x00004550)

        // 3. File Header: Machine = AMD64 (0x8664), NumberOfSections = 1, SizeOfOptionalHeader = 240 (0xF0)
        buffer.putShort(0x84, 0x8664.toShort())
        buffer.putShort(0x86, 1.toShort())
        buffer.putShort(0x94, 240.toShort())

        // 4. Optional Header at 0x98: Magic = PE32+ (0x020B)
        buffer.putShort(0x98, 0x020B.toShort())

        // DataDirectory[1] Import Table RVA at 0x98 + 112 + 8 = 0x110
        // RVA = 0x2000, Size = 0x40
        buffer.putInt(0x110, 0x2000)
        buffer.putInt(0x114, 0x40)

        // 5. Section Header 1 at 0x80 + 24 + 240 = 0x188 (40 bytes)
        // Name = ".rdata"
        val secName = ".rdata".toByteArray(Charsets.ISO_8859_1)
        System.arraycopy(secName, 0, buffer.array(), 0x188, secName.size)
        buffer.putInt(0x190, 0x1000) // VirtualSize
        buffer.putInt(0x194, 0x2000) // VirtualAddress RVA
        buffer.putInt(0x198, 0x400)  // SizeOfRawData
        buffer.putInt(0x19C, 0x400)  // PointerToRawData (File offset 0x400)

        // 6. Import Directory Table at File Offset 0x400 (RVA 0x2000)
        // Descriptor 1: NameRVA = 0x2050 (File offset 0x450), FirstThunk = 0x2080
        buffer.putInt(0x40C, 0x2050)
        buffer.putInt(0x410, 0x2080)
        // Descriptor 2 (all 0s) at 0x414 marks end of import table

        // 7. DLL Name string at File Offset 0x450 (RVA 0x2050)
        val dllName = "openxr_loader.dll".toByteArray(Charsets.ISO_8859_1)
        System.arraycopy(dllName, 0, buffer.array(), 0x450, dllName.size)
        buffer.array()[0x450 + dllName.size] = 0 // Null termination

        file.writeBytes(buffer.array())

        val identity = inspector.inspect(gameRoot, "dynamic_rva_game.exe")

        assertEquals(PeArchitecture.X64_64, identity.architecture)
        assertTrue(identity.hasOpenXRImport)
        assertTrue(identity.importedLibraries.contains("openxr_loader.dll"))
    }

    @Test
    fun inspect_handlesCorruptPeHeader_invalidPeSignature() = runTest {
        val file = File(gameRoot, "corrupt_pe_sig.exe")
        val buffer = ByteBuffer.allocate(256).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putShort(0, 0x5A4D.toShort()) // MZ
        buffer.putInt(0x3C, 0x80) // peOffset
        buffer.putInt(0x80, 0x58585858) // "XXXX" instead of "PE\0\0"
        file.writeBytes(buffer.array())

        val identity = inspector.inspect(gameRoot, "corrupt_pe_sig.exe")
        assertEquals(PeArchitecture.UNKNOWN, identity.architecture)
    }

    @Test
    fun inspect_detectsOpenVRImportCorrectly() = runTest {
        createSyntheticPeFile("openvr_game.exe", 0x8664.toShort(), listOf("openvr_api.dll"))
        val identity = inspector.inspect(gameRoot, "openvr_game.exe")

        assertEquals(PeArchitecture.X64_64, identity.architecture)
        assertFalse(identity.hasOpenXRImport)
        assertTrue(identity.hasOpenVRImport)
        assertTrue(identity.importedLibraries.contains("openvr_api.dll"))
    }

    @Test
    fun inspect_detectsBothOpenXRAndOpenVRImports() = runTest {
        createSyntheticPeFile("dual_vr.exe", 0x8664.toShort(), listOf("openxr_loader.dll", "openvr_api.dll"))
        val identity = inspector.inspect(gameRoot, "dual_vr.exe")

        assertTrue(identity.hasOpenXRImport)
        assertTrue(identity.hasOpenVRImport)
        assertEquals(2, identity.importedLibraries.size)
    }

    @Test
    fun inspect_handlesCaseInsensitiveVrImports() = runTest {
        createSyntheticPeFile("upper_vr.exe", 0x8664.toShort(), listOf("OPENXR_LOADER.DLL", "OpenVR_Api.Dll"))
        val identity = inspector.inspect(gameRoot, "upper_vr.exe")

        assertTrue(identity.hasOpenXRImport)
        assertTrue(identity.hasOpenVRImport)
    }

    @Test
    fun inspect_detectsLauncherHeuristicByFileName() = runTest {
        createSyntheticPeFile("CrashReporterLauncher.exe", 0x8664.toShort(), emptyList())
        val identity = inspector.inspect(gameRoot, "CrashReporterLauncher.exe")

        assertTrue(identity.isLauncher)
    }

    @Test
    fun inspect_throwsExecutableNotFound_whenFileDoesNotExist() = runTest {
        try {
            inspector.inspect(gameRoot, "non_existent.exe")
            fail("Expected ExecutableNotFound exception")
        } catch (e: LaunchFailureException.ExecutableNotFound) {
            assertTrue(e.technicalDetails.contains("non_existent.exe"))
        }
    }
}
