package app.gamenative.launch

import app.gamenative.data.GameSource
import app.gamenative.launch.install.GameInstallCandidate
import app.gamenative.launch.install.GameInstallResolutionRequest
import app.gamenative.launch.install.GameInstallResolverImpl
import app.gamenative.launch.install.InstallCandidateSource
import app.gamenative.launch.install.InstallCandidateStatus
import app.gamenative.launch.install.SelectedLaunchOption
import app.gamenative.launch.install.SteamGameInstallCandidateFactory
import app.gamenative.launch.install.SteamInstallRootCandidate
import app.gamenative.launch.install.WineDriveMapping
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeNoException
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files

class GameInstallResolverTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val resolver = GameInstallResolverImpl()

    @Test
    fun resolve_selectsValidSteamInstallAndBuildsWinePath() = runTest {
        val root = gameRoot("SpaceWar", "bin/SpaceWar.exe")

        val result = resolver.resolve(request(listOf(candidate(root)), "bin/SpaceWar.exe"))

        assertEquals(root.canonicalFile, result.hostInstallRoot.file)
        assertEquals(root.canonicalFile, result.guestMountedRoot.hostBindingRoot)
        assertEquals("D:\\bin\\SpaceWar.exe", result.wineExecutablePath.value)
        assertEquals(InstallCandidateStatus.SELECTED, result.evidence.single().status)
    }

    @Test
    fun resolve_preservesGameRootSeparatelyFromAncestorWineMount() = runTest {
        val library = temporaryFolder.newFolder("SteamLibrary")
        val root = File(library, "steamapps/common/SpaceWar").apply { mkdirs() }
        File(root, "bin/SpaceWar.exe").apply {
            parentFile?.mkdirs()
            writeText("fixture")
        }

        val result = resolver.resolve(
            request(
                listOf(
                    GameInstallCandidate(
                        root = root,
                        source = InstallCandidateSource.STEAM_COMPLETED_INSTALL,
                        guestDriveLetter = 'E',
                        guestMountRoot = library,
                    ),
                ),
                "bin/SpaceWar.exe",
            ),
        )

        assertEquals(root.canonicalFile, result.hostInstallRoot.file)
        assertEquals(library.canonicalFile, result.guestMountedRoot.hostBindingRoot)
        assertEquals("E:\\steamapps\\common\\SpaceWar\\bin\\SpaceWar.exe", result.wineExecutablePath.value)
        assertEquals("E:\\steamapps\\common\\SpaceWar\\bin", result.wineWorkingDirectoryPath.value)
    }

    @Test
    fun resolve_reportsCandidateNotMappedToWineBeforeInspection() = runTest {
        val root = gameRoot("Unmapped", "game.exe")

        val failure = expectFailure<LaunchFailureException.InstallNotMounted> {
            resolver.resolve(
                request(
                    listOf(
                        GameInstallCandidate(
                            root = root,
                            source = InstallCandidateSource.STEAM_METADATA,
                            guestDriveLetter = null,
                        ),
                    ),
                    "game.exe",
                ),
            )
        }

        assertEquals("INSTALL_GUEST_MOUNT_MISSING", failure.failureCode)
    }

    @Test
    fun steamCandidateFactory_prefersTheMostSpecificContainingWineDrive() {
        val library = temporaryFolder.newFolder("Library")
        val gameRoot = File(library, "steamapps/common/SpaceWar").apply { mkdirs() }

        val candidate = SteamGameInstallCandidateFactory.create(
            installRoots = listOf(
                SteamInstallRootCandidate(gameRoot, InstallCandidateSource.STEAM_METADATA, "Steam metadata"),
            ),
            driveMappings = listOf(
                WineDriveMapping('D', library),
                WineDriveMapping('E', gameRoot),
            ),
        ).single()

        assertEquals('E', candidate.guestDriveLetter)
        assertEquals(gameRoot, candidate.guestMountRoot)
    }

    @Test
    fun steamCandidateFactory_discoversMappedGameDirectoryWhenSteamMetadataIsUnavailable() = runTest {
        val steamCommon = temporaryFolder.newFolder("Steam", "steamapps", "common")
        val gameRoot = File(steamCommon, "Doom 3").apply { mkdirs() }
        File(gameRoot, "Doom3.exe").writeText("fixture")
        val mappings = listOf(WineDriveMapping('A', steamCommon))

        val discovered = SteamGameInstallCandidateFactory.discoverMappedExecutableCandidates(
            executableRelativePath = "Doom3.exe",
            driveMappings = mappings,
        )
        val candidates = SteamGameInstallCandidateFactory.create(discovered, mappings)

        val result = resolver.resolve(request(candidates, "Doom3.exe"))

        assertEquals(gameRoot.canonicalFile, result.hostInstallRoot.file)
        assertEquals(InstallCandidateSource.CONTAINER_MAPPED_EXECUTABLE, result.evidence.single().source)
        assertEquals("A:\\Doom 3\\Doom3.exe", result.wineExecutablePath.value)
    }

    @Test
    fun steamCandidateFactory_doesNotProbeTraversingExecutablePaths() {
        val steamCommon = temporaryFolder.newFolder("Steam", "steamapps", "common")
        val gameRoot = File(steamCommon, "Doom 3").apply { mkdirs() }
        File(gameRoot, "Doom3.exe").writeText("fixture")

        val discovered = SteamGameInstallCandidateFactory.discoverMappedExecutableCandidates(
            executableRelativePath = "../Doom3.exe",
            driveMappings = listOf(WineDriveMapping('A', steamCommon)),
        )

        assertTrue(discovered.isEmpty())
    }

    @Test
    fun resolve_rejectsMissingInstallDirectory() = runTest {
        val missing = File(temporaryFolder.root, "missing")

        val failure = expectFailure<LaunchFailureException.InstallDirectoryNotFound> {
            resolver.resolve(request(listOf(candidate(missing)), "game.exe"))
        }

        assertEquals("INSTALL_DIRECTORY_NOT_FOUND", failure.failureCode)
    }

    @Test
    fun resolve_rejectsMissingExecutableBeforeInspection() = runTest {
        val root = temporaryFolder.newFolder("MissingExe")

        val failure = expectFailure<LaunchFailureException.ExecutableNotFound> {
            resolver.resolve(request(listOf(candidate(root)), "missing.exe"))
        }

        assertEquals("INSTALL_EXECUTABLE_NOT_FOUND", failure.failureCode)
    }

    @Test
    fun resolve_ignoresStaleSavedPathWhenMetadataCandidateIsValid() = runTest {
        val stale = temporaryFolder.newFolder("Stale")
        val valid = gameRoot("Valid", "game.exe")

        val result = resolver.resolve(
            request(
                listOf(
                    candidate(stale, InstallCandidateSource.SAVED_CONTAINER_PATH),
                    candidate(valid, InstallCandidateSource.STEAM_METADATA)
                ),
                "game.exe"
            )
        )

        assertEquals(valid.canonicalFile, result.hostInstallRoot.file)
        assertTrue(result.evidence.any { it.source == InstallCandidateSource.SAVED_CONTAINER_PATH && it.status == InstallCandidateStatus.EXECUTABLE_MISSING })
    }

    @Test
    fun resolve_rejectsConflictingValidCandidates() = runTest {
        val first = gameRoot("First", "game.exe")
        val second = gameRoot("Second", "game.exe")

        val failure = expectFailure<LaunchFailureException.ConflictingInstallCandidates> {
            resolver.resolve(request(listOf(candidate(first), candidate(second)), "game.exe"))
        }

        assertEquals("INSTALL_CANDIDATES_CONFLICT", failure.failureCode)
        assertTrue(failure.technicalDetails.contains(first.canonicalPath))
        assertTrue(failure.technicalDetails.contains(second.canonicalPath))
    }

    @Test
    fun resolve_collapsesDuplicateEvidenceForTheSameCanonicalRoot() = runTest {
        val root = gameRoot("Duplicate", "game.exe")

        val result = resolver.resolve(
            request(
                listOf(
                    candidate(root, InstallCandidateSource.STEAM_COMPLETED_INSTALL),
                    candidate(File(root, "."), InstallCandidateSource.SAVED_CONTAINER_PATH)
                ),
                "game.exe"
            )
        )

        assertEquals(1, result.evidence.count { it.status == InstallCandidateStatus.SELECTED })
        assertEquals(1, result.evidence.count { it.status == InstallCandidateStatus.DUPLICATE_OF_SELECTED })
    }

    @Test
    fun resolve_rejectsTraversalAndAbsolutePaths() = runTest {
        val root = gameRoot("Traversal", "game.exe")

        listOf("../game.exe", "bin/../game.exe", "/game.exe", "C:\\game.exe").forEach { path ->
            val failure = expectFailure<LaunchFailureException.InvalidInstallPath> {
                resolver.resolve(request(listOf(candidate(root)), path))
            }
            assertEquals("INSTALL_PATH_INVALID", failure.failureCode)
        }
    }

    @Test
    fun resolve_rejectsSymlinkThatEscapesInstallRoot() = runTest {
        val root = temporaryFolder.newFolder("SymlinkRoot")
        val outside = temporaryFolder.newFile("outside.exe").apply { writeText("outside") }
        val link = File(root, "linked.exe").toPath()
        try {
            Files.createSymbolicLink(link, outside.toPath())
        } catch (failure: Exception) {
            assumeNoException("Symbolic links are unavailable on this test host", failure)
        }

        val failure = expectFailure<LaunchFailureException.InvalidInstallPath> {
            resolver.resolve(request(listOf(candidate(root)), "linked.exe"))
        }

        assertEquals("INSTALL_PATH_INVALID", failure.failureCode)
    }

    @Test
    fun resolve_preservesRootWorkingDirectory() = runTest {
        val root = gameRoot("WorkingDirectory", "bin/game.exe")
        val option = SelectedLaunchOption(
            optionId = "steam:default",
            executableRelativePath = "bin/game.exe",
            workingDirectoryRelativePath = ".",
        )

        val result = resolver.resolve(
            GameInstallResolutionRequest("480", GameSource.STEAM, option, listOf(candidate(root)))
        )

        assertEquals(".", result.selectedLaunchOption.workingDirectoryRelativePath)
    }

    @Test
    fun resolve_rejectsWorkingDirectoryTraversal() = runTest {
        val root = gameRoot("WorkingDirectoryTraversal", "bin/game.exe")
        val option = SelectedLaunchOption(
            optionId = "steam:default",
            executableRelativePath = "bin/game.exe",
            workingDirectoryRelativePath = "../outside",
        )

        val failure = expectFailure<LaunchFailureException.InvalidInstallPath> {
            resolver.resolve(GameInstallResolutionRequest("480", GameSource.STEAM, option, listOf(candidate(root))))
        }

        assertEquals("INSTALL_PATH_INVALID", failure.failureCode)
    }

    @Test
    fun resolve_preservesLauncherClassificationFromSelectedOption() = runTest {
        val root = gameRoot("Launcher", "Launcher.exe")
        val option = SelectedLaunchOption(
            optionId = "steam:launcher",
            executableRelativePath = "Launcher.exe",
            description = "Configuration launcher",
            isLauncher = true
        )

        val result = resolver.resolve(
            GameInstallResolutionRequest("480", GameSource.STEAM, option, listOf(candidate(root)))
        )

        assertTrue(result.selectedLaunchOption.isLauncher)
        assertEquals("steam:launcher", result.selectedLaunchOption.optionId)
    }

    private fun request(candidates: List<GameInstallCandidate>, executable: String) =
        GameInstallResolutionRequest(
            appId = "480",
            gameSource = GameSource.STEAM,
            selectedLaunchOption = SelectedLaunchOption("steam:default", executable),
            candidates = candidates
        )

    private fun candidate(
        root: File,
        source: InstallCandidateSource = InstallCandidateSource.STEAM_METADATA
    ) = GameInstallCandidate(root, source, 'D')

    private fun gameRoot(name: String, executable: String): File {
        val root = temporaryFolder.newFolder(name)
        File(root, executable).apply {
            parentFile?.mkdirs()
            writeText("fixture")
        }
        return root
    }

    private suspend inline fun <reified T : Throwable> expectFailure(crossinline block: suspend () -> Unit): T {
        try {
            block()
            fail("Expected ${T::class.java.simpleName}")
        } catch (failure: Throwable) {
            if (failure is T) return failure
            throw failure
        }
        error("unreachable")
    }
}
