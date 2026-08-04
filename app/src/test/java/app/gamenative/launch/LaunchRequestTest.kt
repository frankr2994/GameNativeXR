package app.gamenative.launch

import app.gamenative.data.GameSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class LaunchRequestTest {

    @Test
    fun launchRequest_acceptsValidRelativePath() {
        val request = LaunchRequest(
            launchId = "launch-1",
            sessionId = "session-1",
            appId = "730",
            gameSource = GameSource.STEAM,
            exeRelativePath = "bin/csgo.exe",
            containerPath = "/sdcard/game"
        )
        assertEquals("bin/csgo.exe", request.exeRelativePath)
    }

    @Test
    fun launchRequest_rejectsAbsolutePath_slash() {
        try {
            LaunchRequest(
                launchId = "launch-1",
                sessionId = "session-1",
                appId = "730",
                gameSource = GameSource.STEAM,
                exeRelativePath = "/etc/passwd",
                containerPath = "/sdcard/game"
            )
            fail("Expected IllegalArgumentException for absolute path starting with slash")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("must not be an absolute path") == true)
        }
    }

    @Test
    fun launchRequest_rejectsAbsolutePath_windowsDrive() {
        try {
            LaunchRequest(
                launchId = "launch-1",
                sessionId = "session-1",
                appId = "730",
                gameSource = GameSource.STEAM,
                exeRelativePath = "C:\\Windows\\System32\\cmd.exe",
                containerPath = "/sdcard/game"
            )
            fail("Expected IllegalArgumentException for absolute path with Windows drive letter")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("must not be an absolute path") == true)
        }
    }

    @Test
    fun launchRequest_rejectsAbsolutePath_backslash() {
        try {
            LaunchRequest(
                launchId = "launch-1",
                sessionId = "session-1",
                appId = "730",
                gameSource = GameSource.STEAM,
                exeRelativePath = "\\Windows\\System32\\cmd.exe",
                containerPath = "/sdcard/game"
            )
            fail("Expected IllegalArgumentException for absolute path starting with backslash")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("must not be an absolute path") == true)
        }
    }

    @Test
    fun launchRequest_rejectsRelativePathEscape() {
        try {
            LaunchRequest(
                launchId = "launch-1",
                sessionId = "session-1",
                appId = "730",
                gameSource = GameSource.STEAM,
                exeRelativePath = "../escape.exe",
                containerPath = "/sdcard/game"
            )
            fail("Expected IllegalArgumentException for relative path escape '..'")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("relative path escape") == true)
        }
    }
}
