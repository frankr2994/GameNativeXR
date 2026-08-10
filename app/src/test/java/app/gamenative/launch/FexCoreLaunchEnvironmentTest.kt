package app.gamenative.launch

import app.gamenative.launch.vr.TrackingMode
import org.junit.Assert.assertEquals
import org.junit.Test

class FexCoreLaunchEnvironmentTest {
    @Test
    fun `quest two fex fields override preset values for this launch only`() {
        val overrides = FexCoreLaunchEnvironment.overridesFor(config(
            tso = "Fast",
            x87 = "Fast",
            multiblock = "Disabled",
        ))

        assertEquals("1", overrides["FEX_TSOENABLED"])
        assertEquals("0", overrides["FEX_VECTORTSOENABLED"])
        assertEquals("0", overrides["FEX_MEMCPYSETTSOENABLED"])
        assertEquals("1", overrides["FEX_HALFBARRIERTSOENABLED"])
        assertEquals("1", overrides["FEX_X87REDUCEDPRECISION"])
        assertEquals("0", overrides["FEX_MULTIBLOCK"])
    }

    @Test
    fun `unsupported optional labels leave the launcher preset untouched`() {
        val overrides = FexCoreLaunchEnvironment.overridesFor(config(
            tso = "unknown",
            x87 = "unknown",
            multiblock = "unknown",
        ))

        assertEquals(emptyMap<String, String>(), overrides)
    }

    private fun config(
        tso: String,
        x87: String,
        multiblock: String,
    ) = ResolvedContainerExecutionConfig(
        containerVariant = "bionic",
        wineVersion = "proton-10.0-arm64ec-2",
        wow64Mode = true,
        emulator = "FEXCore",
        fexcoreVersion = "2605",
        fexcoreTSOMode = tso,
        fexcoreX87Mode = x87,
        fexcoreMultiBlock = multiblock,
        fexcorePreset = "INTERMEDIATE",
        box86Version = "0.3.2",
        box64Version = "0.4.2",
        box86Preset = "COMPATIBILITY",
        box64Preset = "COMPATIBILITY",
        graphicsDriver = "Wrapper",
        graphicsDriverVersion = "Turnip v26.2.0 R4",
        graphicsDriverConfig = "",
        dxwrapper = "dxvk",
        dxwrapperConfig = "version=1.11.1-sarek,vkd3dVersion=2.14.1",
        environmentVariables = emptyMap(),
        dllOverrides = emptyMap(),
        requiredPackagedComponentIds = emptySet(),
        trackingMode = TrackingMode.FLAT_3DOF,
        activeModId = null,
    )
}
