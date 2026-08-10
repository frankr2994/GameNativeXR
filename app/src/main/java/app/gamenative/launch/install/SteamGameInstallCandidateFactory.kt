package app.gamenative.launch.install

import java.io.File
import java.nio.file.Path

/** A candidate install root reported by one Steam or saved-container metadata source. */
data class SteamInstallRootCandidate(
    val root: File,
    val source: InstallCandidateSource,
    val description: String,
)

/** A persisted Wine drive binding. Its [hostRoot] is not necessarily the game root. */
data class WineDriveMapping(
    val driveLetter: Char,
    val hostRoot: File,
)

/**
 * Produces resolution evidence without conflating a Steam game directory with the container or
 * the Wine drive root. A game under a library-wide mapping keeps that library as its guest mount,
 * so the resulting Wine path includes the game-relative path below the library.
 */
object SteamGameInstallCandidateFactory {
    fun create(
        installRoots: Iterable<SteamInstallRootCandidate>,
        driveMappings: Iterable<WineDriveMapping>,
    ): List<GameInstallCandidate> {
        val mappings = driveMappings
            .filter { it.driveLetter.isLetter() && it.hostRoot.path.isNotBlank() }
            .map { mapping -> mapping to canonicalOrAbsolute(mapping.hostRoot) }

        return installRoots
            .filter { it.root.path.isNotBlank() }
            .map { candidate ->
                val root = candidate.root
                val canonicalRoot = canonicalOrAbsolute(root)
                val mapping = mappings
                    .asSequence()
                    .filter { (_, mountRoot) -> canonicalRoot.toPath().normalizedAbsolute().startsWith(mountRoot.toPath().normalizedAbsolute()) }
                    .maxByOrNull { (_, mountRoot) -> mountRoot.toPath().normalizedAbsolute().nameCount }
                    ?.first
                GameInstallCandidate(
                    root = root,
                    source = candidate.source,
                    guestDriveLetter = mapping?.driveLetter,
                    guestMountRoot = mapping?.hostRoot,
                    description = candidate.description,
                )
            }
            .toList()
    }

    /**
     * Finds a game root only when the saved Wine mappings already prove that the selected
     * executable exists there. This is needed in the isolated XR process, where Steam's
     * database-backed app metadata can be unavailable even though the container itself retains
     * a valid Steam-library drive binding.
     *
     * The probe is deliberately bounded to a drive root and its immediate child directories. It
     * never accepts an absolute or traversing executable path, never recurses, and rejects a
     * symlink that escapes the mapped root. Ambiguous discoveries remain separate candidates so
     * [GameInstallResolverImpl] can return a classified conflict instead of choosing arbitrarily.
     */
    fun discoverMappedExecutableCandidates(
        executableRelativePath: String,
        driveMappings: Iterable<WineDriveMapping>,
    ): List<SteamInstallRootCandidate> {
        val relativeExecutable = normalizeRelativeExecutable(executableRelativePath) ?: return emptyList()
        val discoveredRoots = mutableSetOf<String>()

        return driveMappings
            .asSequence()
            .filter { it.driveLetter.isLetter() && it.hostRoot.path.isNotBlank() }
            .flatMap { mapping ->
                val canonicalMount = canonicalOrAbsolute(mapping.hostRoot)
                sequence {
                    yield(canonicalMount)
                    for (child in canonicalMount.listFiles().orEmpty()) {
                        if (child.isDirectory) yield(child)
                    }
                }.map { root -> mapping to (canonicalMount to root) }
            }
            .mapNotNull { (mapping, roots) ->
                val (canonicalMount, submittedRoot) = roots
                val canonicalRoot = canonicalOrAbsolute(submittedRoot)
                val mountPath = canonicalMount.toPath().normalizedAbsolute()
                val rootPath = canonicalRoot.toPath().normalizedAbsolute()
                if (!canonicalRoot.isDirectory || !rootPath.startsWith(mountPath)) return@mapNotNull null

                val executable = runCatching { File(canonicalRoot, relativeExecutable).canonicalFile }
                    .getOrNull() ?: return@mapNotNull null
                val executablePath = executable.toPath().normalizedAbsolute()
                if (!executablePath.startsWith(rootPath) || !executable.isFile) return@mapNotNull null

                if (!discoveredRoots.add(rootPath.toString())) return@mapNotNull null
                SteamInstallRootCandidate(
                    root = canonicalRoot,
                    source = InstallCandidateSource.CONTAINER_MAPPED_EXECUTABLE,
                    description = "Container drive ${mapping.driveLetter.uppercaseChar()} executable discovery",
                )
            }
            .toList()
    }

    private fun canonicalOrAbsolute(file: File): File =
        runCatching { file.canonicalFile }.getOrElse { file.absoluteFile }

    private fun normalizeRelativeExecutable(path: String): String? {
        val normalized = path.trim().replace('\\', '/')
        if (normalized.isBlank() || normalized.startsWith('/') || WINDOWS_DRIVE_PREFIX.containsMatchIn(normalized)) {
            return null
        }
        val components = normalized.split('/')
        if (components.any { it.isBlank() || it == "." || it == ".." }) return null
        return components.joinToString("/")
    }

    private fun Path.normalizedAbsolute(): Path = toAbsolutePath().normalize()

    private val WINDOWS_DRIVE_PREFIX = Regex("^[A-Za-z]:")
}
