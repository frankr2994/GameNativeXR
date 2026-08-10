package app.gamenative.launch

import app.gamenative.launch.vr.TrackingMode
import com.winlator.container.Container
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ResolvedContainerExecutionOverlayTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `launch overlay restores live settings and keeps persisted settings while runtime state saves`() {
        val container = Container("overlay-test")
        container.rootDir = temporaryFolder.newFolder("container")
        configurePersistedContainer(container)
        container.putExtra("baseline", "present")
        container.saveData()

        val overlay = ResolvedContainerExecutionOverlay.apply(container, resolvedLaunchConfig())
        assertEquals("launch-variant", container.containerVariant)
        assertEquals("launch-wine", container.wineVersion)
        assertEquals("launch-driver", container.graphicsDriver)
        assertEquals("launch-wrapper", container.dxWrapper)

        // Preinstall markers and equivalent runtime state must still be durable during a launch.
        container.putExtra("prerequisite", "completed")
        container.saveData()

        val persisted = JSONObject(container.configFile.readText())
        assertEquals("persisted-variant", persisted.getString("containerVariant"))
        assertEquals("persisted-wine", persisted.getString("wineVersion"))
        assertEquals("persisted-driver", persisted.getString("graphicsDriver"))
        assertEquals("persisted-wrapper", persisted.getString("dxwrapper"))
        assertEquals("completed", persisted.getJSONObject("extraData").getString("prerequisite"))

        overlay.close()
        overlay.close()

        assertEquals("persisted-variant", container.containerVariant)
        assertEquals("persisted-wine", container.wineVersion)
        assertEquals("persisted-driver", container.graphicsDriver)
        assertEquals("persisted-wrapper", container.dxWrapper)
        assertNull(container.launchExecutionPersistenceOverrides)
    }

    private fun configurePersistedContainer(container: Container) {
        container.setContainerVariant("persisted-variant")
        container.setWineVersion("persisted-wine")
        container.setWoW64Mode(true)
        container.setEmulator("persisted-emulator")
        container.setFEXCoreVersion("persisted-fexcore")
        container.setFEXCorePreset("persisted-fex-preset")
        container.setBox86Version("persisted-box86")
        container.setBox64Version("persisted-box64")
        container.setBox86Preset("persisted-box86-preset")
        container.setBox64Preset("persisted-box64-preset")
        container.setGraphicsDriver("persisted-driver")
        container.setGraphicsDriverVersion("persisted-driver-version")
        container.setGraphicsDriverConfig("persisted-driver-config")
        container.setDXWrapper("persisted-wrapper")
        container.setDXWrapperConfig("persisted-wrapper-config")
    }

    private fun resolvedLaunchConfig() = ResolvedContainerExecutionConfig(
        containerVariant = "launch-variant",
        wineVersion = "launch-wine",
        wow64Mode = false,
        emulator = "launch-emulator",
        fexcoreVersion = "launch-fexcore",
        fexcoreTSOMode = "enabled",
        fexcoreX87Mode = "fast",
        fexcoreMultiBlock = "enabled",
        fexcorePreset = "launch-fex-preset",
        box86Version = "launch-box86",
        box64Version = "launch-box64",
        box86Preset = "launch-box86-preset",
        box64Preset = "launch-box64-preset",
        graphicsDriver = "launch-driver",
        graphicsDriverVersion = "launch-driver-version",
        graphicsDriverConfig = "launch-driver-config",
        dxwrapper = "launch-wrapper",
        dxwrapperConfig = "launch-wrapper-config",
        environmentVariables = emptyMap(),
        dllOverrides = emptyMap(),
        requiredPackagedComponentIds = emptySet(),
        trackingMode = TrackingMode.FLAT_3DOF,
        activeModId = null,
    )
}
