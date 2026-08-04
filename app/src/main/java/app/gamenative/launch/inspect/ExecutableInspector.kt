package app.gamenative.launch.inspect

import app.gamenative.launch.LaunchFailureException
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/**
 * Metadata result produced by [ExecutableInspector].
 */
data class ExecutableIdentity(
    val canonicalPath: String,
    val relativePath: String,
    val fileSize: Long,
    val lastModified: Long,
    val sha256: String,
    val architecture: PeArchitecture,
    val isLauncher: Boolean,
    val importedLibraries: List<String>,
    val hasOpenXRImport: Boolean,
    val hasOpenVRImport: Boolean
)

interface ExecutableInspector {
    suspend fun inspect(gameRoot: File, exeRelativePath: String): ExecutableIdentity
}

class ExecutableInspectorImpl : ExecutableInspector {

    override suspend fun inspect(gameRoot: File, exeRelativePath: String): ExecutableIdentity {
        val gameRootCanonical = gameRoot.canonicalFile
        val targetFile = File(gameRoot, exeRelativePath).canonicalFile
        require(targetFile.toPath().startsWith(gameRootCanonical.toPath())) {
            "Target file path escapes game root containment: $exeRelativePath"
        }
        if (!targetFile.exists() || !targetFile.isFile) {
            throw LaunchFailureException.ExecutableNotFound(targetFile.absolutePath)
        }

        // 1. Compute SHA-256 Digest
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(targetFile).use { fis ->
            val buf = ByteArray(8192)
            var bytesRead: Int
            while (fis.read(buf).also { bytesRead = it } != -1) {
                digest.update(buf, 0, bytesRead)
            }
        }
        val sha256Hash = digest.digest().joinToString("") { "%02x".format(it) }

        // 2. Parse PE Header & Machine Architecture
        val peResult = PeHeaderParser.parse(targetFile)

        // 3. Heuristic Launcher Detection
        val fileNameLower = targetFile.name.lowercase()
        val isLauncher = fileNameLower.contains("launcher") || fileNameLower.contains("unins") ||
                fileNameLower.contains("setup") || fileNameLower.contains("crashreport")

        return ExecutableIdentity(
            canonicalPath = targetFile.absolutePath,
            relativePath = exeRelativePath,
            fileSize = targetFile.length(),
            lastModified = targetFile.lastModified(),
            sha256 = sha256Hash,
            architecture = peResult.architecture,
            isLauncher = isLauncher,
            importedLibraries = peResult.importedDlls,
            hasOpenXRImport = peResult.hasOpenXRImport,
            hasOpenVRImport = peResult.hasOpenVRImport
        )
    }
}
