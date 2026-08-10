package app.gamenative.launch.install

import app.gamenative.data.GameSource
import app.gamenative.launch.LaunchFailureException
import java.io.File
import java.nio.file.Path

@JvmInline
value class HostInstallRoot(val file: File) {
    init {
        require(file.path.isNotBlank()) { "Host install root must not be blank" }
    }
}

data class GuestMountedRoot(
    val driveLetter: Char,
    val hostBindingRoot: File
) {
    init {
        require(driveLetter.isLetter()) { "Guest drive letter must be alphabetic" }
    }

    val wineRoot: String = "${driveLetter.uppercaseChar()}:\\"

    fun resolveWinePath(relativePath: String): String =
        wineRoot + relativePath.replace('/', '\\').trimStart('\\')
}

@JvmInline
value class WineExecutablePath(val value: String) {
    init {
        require(WINE_ABSOLUTE_PATH.matches(value)) { "Wine executable path must be drive-absolute: $value" }
    }

    private companion object {
        val WINE_ABSOLUTE_PATH = Regex("^[A-Za-z]:\\\\.*")
    }
}

/** A Wine-visible working directory derived from the same verified guest mount as the executable. */
@JvmInline
value class WineWorkingDirectoryPath(val value: String) {
    init {
        require(WINE_ABSOLUTE_PATH.matches(value)) { "Wine working directory must be drive-absolute: $value" }
    }

    private companion object {
        val WINE_ABSOLUTE_PATH = Regex("^[A-Za-z]:\\\\.*")
    }
}

enum class InstallCandidateSource {
    STEAM_IMPORTED_INSTALL,
    STEAM_COMPLETED_INSTALL,
    STEAM_METADATA,
    /**
     * A bounded fallback derived from the container's existing Wine drive bindings when the
     * secondary XR process has not loaded Steam's in-memory app metadata yet.
     */
    CONTAINER_MAPPED_EXECUTABLE,
    SAVED_CONTAINER_PATH,
    STORE_METADATA,
    CUSTOM_GAME_FOLDER
}

data class GameInstallCandidate(
    /** The host directory containing this game's files; never a Wine container/prefix root. */
    val root: File,
    val source: InstallCandidateSource,
    /**
     * The Wine drive that exposes [guestMountRoot]. A null value is retained as evidence so
     * resolution can report an unmapped install before any Wine environment starts.
     */
    val guestDriveLetter: Char? = null,
    /**
     * The host root mounted at [guestDriveLetter]. It may be the game root itself or an
     * ancestor such as a Steam library. When omitted, legacy callers map [root] directly.
     */
    val guestMountRoot: File? = null,
    val description: String = source.name
)

data class SelectedLaunchOption(
    val optionId: String,
    val executableRelativePath: String,
    val workingDirectoryRelativePath: String? = null,
    val description: String = "",
    val isLauncher: Boolean = false
)

data class GameInstallResolutionRequest(
    val appId: String,
    val gameSource: GameSource,
    val selectedLaunchOption: SelectedLaunchOption,
    val candidates: List<GameInstallCandidate>
) {
    init {
        require(appId.isNotBlank()) { "appId must not be blank" }
        require(candidates.isNotEmpty()) { "At least one install candidate is required" }
    }
}

enum class InstallCandidateStatus {
    SELECTED,
    DUPLICATE_OF_SELECTED,
    DIRECTORY_MISSING,
    NOT_A_DIRECTORY,
    EXECUTABLE_MISSING,
    PATH_ESCAPE,
    GUEST_MOUNT_UNAVAILABLE,
    CONFLICTING_VALID_INSTALL
}

data class InstallResolutionEvidence(
    val source: InstallCandidateSource,
    val submittedPath: String,
    val canonicalPath: String?,
    val status: InstallCandidateStatus,
    val detail: String
)

data class ResolvedGameInstall(
    val appId: String,
    val gameSource: GameSource,
    val hostInstallRoot: HostInstallRoot,
    val guestMountedRoot: GuestMountedRoot,
    val selectedExecutable: File,
    val executableRelativePath: String,
    val wineExecutablePath: WineExecutablePath,
    val wineWorkingDirectoryPath: WineWorkingDirectoryPath,
    val selectedLaunchOption: SelectedLaunchOption,
    val evidence: List<InstallResolutionEvidence>
)

interface GameInstallResolver {
    suspend fun resolve(request: GameInstallResolutionRequest): ResolvedGameInstall
}

class GameInstallResolverImpl : GameInstallResolver {
    override suspend fun resolve(request: GameInstallResolutionRequest): ResolvedGameInstall {
        val relativeExecutable = normalizeRelativePath(
            request.selectedLaunchOption.executableRelativePath,
            "executable"
        )
        val relativeWorkingDirectory = request.selectedLaunchOption.workingDirectoryRelativePath
            ?.takeIf { it.isNotBlank() }
            ?.let { normalizeRelativePath(it, "working directory", allowCurrentDirectory = true) }

        val evaluations = request.candidates.map { evaluateCandidate(it, relativeExecutable) }
        val validByCanonicalRoot = evaluations
            .filter { it.executable != null }
            .groupBy { it.canonicalRoot!!.toPath().normalizedAbsolute() }

        if (validByCanonicalRoot.isEmpty()) {
            val escapes = evaluations.filter { it.status == InstallCandidateStatus.PATH_ESCAPE }
            if (escapes.isNotEmpty()) {
                throw LaunchFailureException.InvalidInstallPath(
                    request.selectedLaunchOption.executableRelativePath,
                    escapes.joinToString(separator = "; ") { evaluation ->
                        "${evaluation.candidate.root.path}: ${evaluation.detail}"
                    },
                )
            }
            val unmapped = evaluations.filter { it.status == InstallCandidateStatus.GUEST_MOUNT_UNAVAILABLE }
            if (unmapped.isNotEmpty()) {
                throw LaunchFailureException.InstallNotMounted(
                    request.appId,
                    unmapped.joinToString(separator = "; ") { evaluation ->
                        "${evaluation.candidate.root.path}: ${evaluation.detail}"
                    },
                )
            }
            val hasExistingDirectory = evaluations.any { it.canonicalRoot?.isDirectory == true }
            if (hasExistingDirectory) {
                val searched = evaluations.mapNotNull { it.canonicalRoot }
                    .joinToString { File(it, relativeExecutable).path }
                throw LaunchFailureException.ExecutableNotFound(searched)
            }
            throw LaunchFailureException.InstallDirectoryNotFound(
                request.appId,
                request.candidates.map { it.root.path }
            )
        }

        if (validByCanonicalRoot.size > 1) {
            throw LaunchFailureException.ConflictingInstallCandidates(
                request.appId,
                validByCanonicalRoot.keys.map(Path::toString)
            )
        }

        val selectedGroup = validByCanonicalRoot.values.single()
        val selected = selectedGroup.minBy { candidatePriority(it.candidate.source) }
        val selectedRoot = selected.canonicalRoot!!
        val selectedMountRoot = selected.canonicalMountRoot!!
        val selectedExecutable = selected.executable!!
        val selectedRootPath = selectedRoot.toPath().normalizedAbsolute()
        val mount = GuestMountedRoot(
            driveLetter = selected.candidate.guestDriveLetter!!,
            hostBindingRoot = selectedMountRoot,
        )
        val workingDirectory = resolveWorkingDirectory(
            installRoot = selectedRoot,
            relativeExecutable = relativeExecutable,
            requestedRelativeWorkingDirectory = relativeWorkingDirectory,
        )

        val evidence = evaluations.map { evaluation ->
            val canonical = evaluation.canonicalRoot?.toPath()?.normalizedAbsolute()
            val status = when {
                evaluation.executable == null -> evaluation.status
                canonical == selectedRootPath && evaluation === selected -> InstallCandidateStatus.SELECTED
                canonical == selectedRootPath -> InstallCandidateStatus.DUPLICATE_OF_SELECTED
                else -> InstallCandidateStatus.CONFLICTING_VALID_INSTALL
            }
            InstallResolutionEvidence(
                source = evaluation.candidate.source,
                submittedPath = evaluation.candidate.root.path,
                canonicalPath = evaluation.canonicalRoot?.path,
                status = status,
                detail = evaluation.detail
            )
        }

        return ResolvedGameInstall(
            appId = request.appId,
            gameSource = request.gameSource,
            hostInstallRoot = HostInstallRoot(selectedRoot),
            guestMountedRoot = mount,
            selectedExecutable = selectedExecutable,
            executableRelativePath = relativeExecutable,
            wineExecutablePath = WineExecutablePath(
                mount.resolveWinePath(relativePathFromMount(selectedMountRoot, selectedExecutable)),
            ),
            wineWorkingDirectoryPath = WineWorkingDirectoryPath(
                mount.resolveWinePath(relativePathFromMount(selectedMountRoot, workingDirectory)),
            ),
            selectedLaunchOption = request.selectedLaunchOption.copy(
                executableRelativePath = relativeExecutable,
                workingDirectoryRelativePath = relativeWorkingDirectory,
            ),
            evidence = evidence
        )
    }

    private fun evaluateCandidate(
        candidate: GameInstallCandidate,
        relativeExecutable: String
    ): CandidateEvaluation {
        if (!candidate.root.exists()) {
            return CandidateEvaluation(candidate, null, null, null, InstallCandidateStatus.DIRECTORY_MISSING, "Directory does not exist")
        }
        if (!candidate.root.isDirectory) {
            return CandidateEvaluation(candidate, null, null, null, InstallCandidateStatus.NOT_A_DIRECTORY, "Candidate is not a directory")
        }

        val canonicalRoot = runCatching { candidate.root.canonicalFile }
            .getOrElse {
                return CandidateEvaluation(candidate, null, null, null, InstallCandidateStatus.PATH_ESCAPE, "Could not canonicalize install root: ${it.message}")
            }
        val driveLetter = candidate.guestDriveLetter
            ?: return CandidateEvaluation(
                candidate,
                canonicalRoot,
                null,
                null,
                InstallCandidateStatus.GUEST_MOUNT_UNAVAILABLE,
                "No Wine drive is mapped for the candidate install root",
            )
        val submittedMountRoot = candidate.guestMountRoot ?: candidate.root
        if (!submittedMountRoot.exists() || !submittedMountRoot.isDirectory) {
            return CandidateEvaluation(
                candidate,
                canonicalRoot,
                null,
                null,
                InstallCandidateStatus.GUEST_MOUNT_UNAVAILABLE,
                "Wine drive ${driveLetter.uppercaseChar()}: does not map to an existing directory",
            )
        }
        val canonicalMountRoot = runCatching { submittedMountRoot.canonicalFile }
            .getOrElse {
                return CandidateEvaluation(
                    candidate,
                    canonicalRoot,
                    null,
                    null,
                    InstallCandidateStatus.GUEST_MOUNT_UNAVAILABLE,
                    "Could not canonicalize Wine drive mount: ${it.message}",
                )
            }
        if (!canonicalRoot.toPath().normalizedAbsolute().startsWith(canonicalMountRoot.toPath().normalizedAbsolute())) {
            return CandidateEvaluation(
                candidate,
                canonicalRoot,
                canonicalMountRoot,
                null,
                InstallCandidateStatus.GUEST_MOUNT_UNAVAILABLE,
                "Game install root is outside the mapped Wine drive ${driveLetter.uppercaseChar()}:",
            )
        }
        val executable = runCatching { File(canonicalRoot, relativeExecutable).canonicalFile }
            .getOrElse {
                return CandidateEvaluation(candidate, canonicalRoot, canonicalMountRoot, null, InstallCandidateStatus.PATH_ESCAPE, "Could not canonicalize executable: ${it.message}")
            }

        val rootPath = canonicalRoot.toPath().normalizedAbsolute()
        val executablePath = executable.toPath().normalizedAbsolute()
        if (!executablePath.startsWith(rootPath) || executablePath == rootPath) {
            return CandidateEvaluation(candidate, canonicalRoot, canonicalMountRoot, null, InstallCandidateStatus.PATH_ESCAPE, "Executable escapes the canonical install root")
        }
        if (!executable.isFile) {
            return CandidateEvaluation(candidate, canonicalRoot, canonicalMountRoot, null, InstallCandidateStatus.EXECUTABLE_MISSING, "Executable is missing under this candidate")
        }

        return CandidateEvaluation(
            candidate,
            canonicalRoot,
            canonicalMountRoot,
            executable,
            InstallCandidateStatus.SELECTED,
            "Candidate contains the selected executable",
        )
    }

    private fun resolveWorkingDirectory(
        installRoot: File,
        relativeExecutable: String,
        requestedRelativeWorkingDirectory: String?,
    ): File {
        val defaultRelativeDirectory = relativeExecutable.substringBeforeLast('/', missingDelimiterValue = ".")
        val relativeDirectory = requestedRelativeWorkingDirectory ?: defaultRelativeDirectory.ifBlank { "." }
        val workingDirectory = if (relativeDirectory == ".") {
            installRoot
        } else {
            File(installRoot, relativeDirectory).canonicalFile
        }
        val installRootPath = installRoot.toPath().normalizedAbsolute()
        val workingDirectoryPath = workingDirectory.toPath().normalizedAbsolute()
        if (!workingDirectoryPath.startsWith(installRootPath)) {
            throw LaunchFailureException.InvalidInstallPath(
                relativeDirectory,
                "Working directory escapes the canonical install root",
            )
        }
        if (!workingDirectory.isDirectory) {
            throw LaunchFailureException.InvalidInstallPath(
                relativeDirectory,
                "Working directory does not exist under the selected install root",
            )
        }
        return workingDirectory
    }

    private fun relativePathFromMount(mountRoot: File, path: File): String {
        val relative = mountRoot.toPath().normalizedAbsolute()
            .relativize(path.toPath().normalizedAbsolute())
            .toString()
            .replace('\\', '/')
        require(!relative.startsWith("../")) {
            "Guest path must remain inside the mapped Wine drive"
        }
        return relative.takeUnless { it == "." }.orEmpty()
    }

    private fun normalizeRelativePath(
        path: String,
        label: String,
        allowCurrentDirectory: Boolean = false,
    ): String {
        val normalizedSeparators = path.trim().replace('\\', '/')
        if (normalizedSeparators.isBlank() || normalizedSeparators.startsWith('/') || WINDOWS_DRIVE_PREFIX.containsMatchIn(normalizedSeparators)) {
            throw LaunchFailureException.InvalidInstallPath(path, "$label path must be relative")
        }
        if (allowCurrentDirectory && normalizedSeparators == ".") return "."
        val components = normalizedSeparators.split('/')
        if (components.any { it.isBlank() || it == "." || it == ".." }) {
            throw LaunchFailureException.InvalidInstallPath(path, "$label path contains an empty, current, or parent segment")
        }
        return components.joinToString("/")
    }

    private fun candidatePriority(source: InstallCandidateSource): Int = when (source) {
        InstallCandidateSource.STEAM_IMPORTED_INSTALL -> 0
        InstallCandidateSource.STEAM_COMPLETED_INSTALL -> 1
        InstallCandidateSource.STEAM_METADATA -> 2
        InstallCandidateSource.CONTAINER_MAPPED_EXECUTABLE -> 3
        InstallCandidateSource.STORE_METADATA -> 4
        InstallCandidateSource.CUSTOM_GAME_FOLDER -> 5
        InstallCandidateSource.SAVED_CONTAINER_PATH -> 6
    }

    private data class CandidateEvaluation(
        val candidate: GameInstallCandidate,
        val canonicalRoot: File?,
        val canonicalMountRoot: File?,
        val executable: File?,
        val status: InstallCandidateStatus,
        val detail: String
    )

    private fun Path.normalizedAbsolute(): Path = toAbsolutePath().normalize()

    private companion object {
        val WINDOWS_DRIVE_PREFIX = Regex("^[A-Za-z]:")
    }
}
