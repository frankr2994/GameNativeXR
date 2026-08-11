package app.gamenative.utils

import app.gamenative.launch.inspect.PeArchitecture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SteamUtilsColdClientIniTest {

    private fun generate(
        gameName: String = "Batman Arkham Asylum GOTY",
        executablePath: String = "Binaries\\BmLauncher.exe",
        exeCommandLine: String = "",
        steamAppId: Int = 35140,
        workingDir: String? = null,
        exeRunDirOverride: String? = null,
        exePathOverride: String? = null,
    ) = SteamUtils.generateColdClientIni(
        gameName = gameName,
        executablePath = executablePath,
        exeCommandLine = exeCommandLine,
        steamAppId = steamAppId,
        workingDir = workingDir,
        exeRunDirOverride = exeRunDirOverride,
        exePathOverride = exePathOverride,
    )

    private fun String.iniValue(key: String): String =
        lines().first { it.startsWith("$key=") }.removePrefix("$key=")

    @Test
    fun `ExeRunDir is exe directory when no workingDir`() {
        val ini = generate(gameName = "New Star GP", executablePath = "release/NSGP.exe", workingDir = null)
        assertEquals("steamapps\\common\\New Star GP\\release", ini.iniValue("ExeRunDir"))
    }

    @Test
    fun `ExeRunDir is blank when workingDir is set`() {
        // workingDir set: leave blank (legacy behaviour, same as master)
        val ini = generate(workingDir = "Binaries")
        assertEquals("", ini.iniValue("ExeRunDir"))
    }

    @Test
    fun `ExeRunDir is exe directory when workingDir is empty string`() {
        val ini = generate(gameName = "New Star GP", executablePath = "release/NSGP.exe", workingDir = "")
        assertEquals("steamapps\\common\\New Star GP\\release", ini.iniValue("ExeRunDir"))
    }

    @Test
    fun `resolved Wine paths override unavailable Steam metadata`() {
        val ini = generate(
            gameName = "",
            executablePath = "Doom3.exe",
            exePathOverride = "A:\\Doom 3\\Doom3.exe",
            exeRunDirOverride = "A:\\Doom 3",
        )

        assertEquals("A:\\Doom 3\\Doom3.exe", ini.iniValue("Exe"))
        assertEquals("A:\\Doom 3", ini.iniValue("ExeRunDir"))
    }

    @Test
    fun `32 bit executable selects 32 bit cold client loader`() {
        assertEquals(
            "steamclient_loader_x32.exe",
            SteamUtils.coldClientLoaderExecutable(PeArchitecture.X86_32),
        )
    }

    @Test
    fun `64 bit and unknown executable retain 64 bit cold client loader`() {
        assertEquals(
            "steamclient_loader_x64.exe",
            SteamUtils.coldClientLoaderExecutable(PeArchitecture.X64_64),
        )
        assertEquals(
            "steamclient_loader_x64.exe",
            SteamUtils.coldClientLoaderExecutable(null),
        )
    }

    @Test
    fun `persisted Steam account ID is used before live service is ready`() {
        assertEquals(12345L, SteamUtils.resolveSteam3AccountId(null, 12345))
        assertEquals(12345L, SteamUtils.resolveSteam3AccountId(0L, 12345))
        assertEquals(67890L, SteamUtils.resolveSteam3AccountId(67890L, 12345))
        assertNull(SteamUtils.resolveSteam3AccountId(null, 0))
    }

}
