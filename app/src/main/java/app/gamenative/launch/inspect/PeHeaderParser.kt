package app.gamenative.launch.inspect

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

enum class PeArchitecture {
    X86_32,
    X64_64,
    ARM64,
    UNKNOWN
}

data class PeInspectionResult(
    val architecture: PeArchitecture,
    val importedDlls: List<String>,
    val hasOpenXRImport: Boolean,
    val hasOpenVRImport: Boolean
)

object PeHeaderParser {

    private const val DOS_MAGIC = 0x5A4D // "MZ"
    private const val PE_MAGIC = 0x00004550 // "PE\0\0"

    private const val MACHINE_I386 = 0x014C
    private const val MACHINE_AMD64 = 0x8664
    private const val MACHINE_ARM64 = 0xAA64

    private data class SectionHeader(
        val virtualAddress: Long,
        val virtualSize: Long,
        val pointerToRawData: Long,
        val sizeOfRawData: Long
    )

    /**
     * Parses PE header of executable at [file] and returns machine architecture and imported DLLs.
     */
    fun parse(file: File): PeInspectionResult {
        if (!file.exists() || file.length() < 128) {
            return PeInspectionResult(PeArchitecture.UNKNOWN, emptyList(), hasOpenXRImport = false, hasOpenVRImport = false)
        }

        try {
            RandomAccessFile(file, "r").use { raf ->
                val headerSize = Math.min(raf.length(), 8192L).toInt()
                val headerBuffer = ByteArray(headerSize)
                val bytesRead = raf.read(headerBuffer)
                if (bytesRead < 64) {
                    return PeInspectionResult(PeArchitecture.UNKNOWN, emptyList(), hasOpenXRImport = false, hasOpenVRImport = false)
                }

                val buf = ByteBuffer.wrap(headerBuffer).order(ByteOrder.LITTLE_ENDIAN)

                // 1. Verify DOS Header "MZ"
                val dosMagic = buf.getShort(0).toInt() and 0xFFFF
                if (dosMagic != DOS_MAGIC) {
                    return PeInspectionResult(PeArchitecture.UNKNOWN, emptyList(), hasOpenXRImport = false, hasOpenVRImport = false)
                }

                // 2. Get PE Header offset from e_lfanew at 0x3C
                val peOffset = buf.getInt(0x3C)
                if (peOffset < 0 || peOffset > bytesRead - 24) {
                    return PeInspectionResult(PeArchitecture.UNKNOWN, emptyList(), hasOpenXRImport = false, hasOpenVRImport = false)
                }

                // 3. Verify PE Signature "PE\0\0"
                val peMagic = buf.getInt(peOffset)
                if (peMagic != PE_MAGIC) {
                    return PeInspectionResult(PeArchitecture.UNKNOWN, emptyList(), hasOpenXRImport = false, hasOpenVRImport = false)
                }

                // 4. Read File Header Machine Type at peOffset + 4
                val machine = buf.getShort(peOffset + 4).toInt() and 0xFFFF
                val numberOfSections = buf.getShort(peOffset + 6).toInt() and 0xFFFF
                val sizeOfOptionalHeader = buf.getShort(peOffset + 20).toInt() and 0xFFFF

                val architecture = when (machine) {
                    MACHINE_I386 -> PeArchitecture.X86_32
                    MACHINE_AMD64 -> PeArchitecture.X64_64
                    MACHINE_ARM64 -> PeArchitecture.ARM64
                    else -> PeArchitecture.UNKNOWN
                }

                val importedDllSet = mutableSetOf<String>()

                // 5. Parse Section Headers & Import Directory Table RVA dynamically from DataDirectory[1]
                if (sizeOfOptionalHeader > 0 && peOffset + 24 + sizeOfOptionalHeader <= bytesRead) {
                    val optionalHeaderMagic = buf.getShort(peOffset + 24).toInt() and 0xFFFF
                    val dataDirOffset = when (optionalHeaderMagic) {
                        0x010B -> peOffset + 24 + 96  // PE32
                        0x020B -> peOffset + 24 + 112 // PE32+
                        else -> -1
                    }

                    if (dataDirOffset > 0 && dataDirOffset + 16 <= bytesRead) {
                        val importRva = buf.getInt(dataDirOffset + 8).toLong() and 0xFFFFFFFFL

                        // Parse section headers
                        val sectionHeadersOffset = peOffset + 24 + sizeOfOptionalHeader
                        val sections = mutableListOf<SectionHeader>()
                        for (i in 0 until numberOfSections) {
                            val secOff = sectionHeadersOffset + i * 40
                            if (secOff + 40 > bytesRead) break
                            val vSize = buf.getInt(secOff + 8).toLong() and 0xFFFFFFFFL
                            val vAddr = buf.getInt(secOff + 12).toLong() and 0xFFFFFFFFL
                            val rawSize = buf.getInt(secOff + 16).toLong() and 0xFFFFFFFFL
                            val rawPtr = buf.getInt(secOff + 20).toLong() and 0xFFFFFFFFL
                            sections.add(SectionHeader(vAddr, vSize, rawPtr, rawSize))
                        }

                        if (importRva > 0 && sections.isNotEmpty()) {
                            val importFileOffset = rvaToFileOffset(importRva, sections)
                            if (importFileOffset >= 0 && importFileOffset < raf.length()) {
                                parseImportTable(raf, importFileOffset, sections, importedDllSet)
                            }
                        }
                    }
                }

                // 6. Fallback string scan across headerBuffer for VR loader names
                scanVRStrings(headerBuffer, bytesRead, importedDllSet)

                val importedDlls = importedDllSet.toList()
                val hasOpenXR = importedDlls.any { it.equals("openxr_loader.dll", ignoreCase = true) }
                val hasOpenVR = importedDlls.any { it.equals("openvr_api.dll", ignoreCase = true) }

                return PeInspectionResult(architecture, importedDlls, hasOpenXRImport = hasOpenXR, hasOpenVRImport = hasOpenVR)
            }
        } catch (e: Exception) {
            return PeInspectionResult(PeArchitecture.UNKNOWN, emptyList(), hasOpenXRImport = false, hasOpenVRImport = false)
        }
    }

    private fun rvaToFileOffset(rva: Long, sections: List<SectionHeader>): Long {
        for (sec in sections) {
            val size = if (sec.virtualSize > 0) sec.virtualSize else sec.sizeOfRawData
            if (rva >= sec.virtualAddress && rva < sec.virtualAddress + size) {
                return rva - sec.virtualAddress + sec.pointerToRawData
            }
        }
        return -1L
    }

    private fun parseImportTable(
        raf: RandomAccessFile,
        importFileOffset: Long,
        sections: List<SectionHeader>,
        outSet: MutableSet<String>
    ) {
        var curOffset = importFileOffset
        val fileLength = raf.length()
        var count = 0
        val descBuf = ByteArray(20)

        while (curOffset + 20 <= fileLength && count < 500) {
            count++
            raf.seek(curOffset)
            val readBytes = raf.read(descBuf)
            if (readBytes < 20) break

            val bb = ByteBuffer.wrap(descBuf).order(ByteOrder.LITTLE_ENDIAN)
            val nameRva = bb.getInt(12).toLong() and 0xFFFFFFFFL
            val firstThunk = bb.getInt(16).toLong() and 0xFFFFFFFFL

            if (nameRva == 0L && firstThunk == 0L) {
                break // Null descriptor marks end of Import Directory Table
            }

            if (nameRva > 0L) {
                val nameFileOffset = rvaToFileOffset(nameRva, sections)
                if (nameFileOffset >= 0 && nameFileOffset < fileLength) {
                    val savedPos = raf.filePointer
                    raf.seek(nameFileOffset)
                    val name = readNullTerminatedAscii(raf)
                    if (name.isNotBlank()) {
                        outSet.add(name)
                    }
                    raf.seek(savedPos)
                }
            }
            curOffset += 20
        }
    }

    private fun readNullTerminatedAscii(raf: RandomAccessFile): String {
        val sb = StringBuilder()
        var bytesRead = 0
        while (bytesRead < 256) {
            val b = raf.read()
            if (b <= 0) break
            sb.append(b.toChar())
            bytesRead++
        }
        return sb.toString()
    }

    private fun scanVRStrings(buffer: ByteArray, length: Int, outSet: MutableSet<String>) {
        val str = String(buffer, 0, length, Charsets.ISO_8859_1)
        if (str.contains("openxr_loader.dll", ignoreCase = true)) {
            outSet.add("openxr_loader.dll")
        }
        if (str.contains("openvr_api.dll", ignoreCase = true)) {
            outSet.add("openvr_api.dll")
        }
    }
}
