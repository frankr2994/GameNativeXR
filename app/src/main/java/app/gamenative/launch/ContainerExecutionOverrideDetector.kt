package app.gamenative.launch

import com.winlator.container.Container
import com.winlator.container.ContainerData

/**
 * Separates persisted container defaults from settings that differ from the current GameNative
 * baseline. Legacy callers can omit this information and retain their previous full-override
 * behavior; live launches provide the sparse result so a hardware profile is not shadowed by
 * every value serialized in a container file.
 */
object ContainerExecutionOverrideDetector {
    const val CONTAINER_VARIANT = "containerVariant"
    const val WINE_VERSION = "wineVersion"
    const val WOW64_MODE = "wow64Mode"
    const val EMULATOR = "emulator"
    const val BOX86_VERSION = "box86Version"
    const val BOX64_VERSION = "box64Version"
    const val BOX86_PRESET = "box86Preset"
    const val BOX64_PRESET = "box64Preset"
    const val FEXCORE_VERSION = "fexcoreVersion"
    const val FEXCORE_TSO_MODE = "fexcoreTSOMode"
    const val FEXCORE_X87_MODE = "fexcoreX87Mode"
    const val FEXCORE_MULTI_BLOCK = "fexcoreMultiBlock"
    const val FEXCORE_PRESET = "fexcorePreset"
    const val GRAPHICS_DRIVER = "graphicsDriver"
    const val GRAPHICS_DRIVER_VERSION = "graphicsDriverVersion"
    const val GRAPHICS_DRIVER_CONFIG = "graphicsDriverConfig"
    const val DXWRAPPER = "dxwrapper"
    const val DXWRAPPER_CONFIG = "dxwrapperConfig"

    /**
     * A value equal to a freshly created Container is baseline configuration, not an explicit
     * per-game choice. An intentional selection equal to the default cannot be recovered from
     * historical container files; future UI edits should record explicit field provenance.
     */
    fun detect(containerData: ContainerData): Set<String> {
        val defaults = Container("launch-defaults")
        return buildSet {
            if (containerData.containerVariant != defaults.containerVariant) add(CONTAINER_VARIANT)
            if (containerData.wineVersion != defaults.wineVersion) add(WINE_VERSION)
            if (containerData.wow64Mode != defaults.isWoW64Mode) add(WOW64_MODE)
            if (containerData.emulator != defaults.emulator) add(EMULATOR)
            if (containerData.box86Version != defaults.box86Version) add(BOX86_VERSION)
            if (containerData.box64Version != defaults.box64Version) add(BOX64_VERSION)
            if (containerData.box86Preset != defaults.box86Preset) add(BOX86_PRESET)
            if (containerData.box64Preset != defaults.box64Preset) add(BOX64_PRESET)
            if (containerData.fexcoreVersion != defaults.fexCoreVersion) add(FEXCORE_VERSION)
            if (containerData.fexcorePreset != defaults.fexCorePreset) add(FEXCORE_PRESET)
            if (containerData.graphicsDriver != defaults.graphicsDriver) add(GRAPHICS_DRIVER)
            if (containerData.graphicsDriverVersion != defaults.graphicsDriverVersion) add(GRAPHICS_DRIVER_VERSION)
            if (containerData.graphicsDriverConfig != defaults.graphicsDriverConfig) add(GRAPHICS_DRIVER_CONFIG)
            if (containerData.dxwrapper != defaults.dxWrapper) add(DXWRAPPER)
            if (containerData.dxwrapperConfig != defaults.dxWrapperConfig) add(DXWRAPPER_CONFIG)

            // These values are not yet persisted on Container itself. Their ContainerData
            // defaults are the only reliable baseline until that migration is complete.
            if (containerData.fexcoreTSOMode != ContainerData().fexcoreTSOMode) add(FEXCORE_TSO_MODE)
            if (containerData.fexcoreX87Mode != ContainerData().fexcoreX87Mode) add(FEXCORE_X87_MODE)
            if (containerData.fexcoreMultiBlock != ContainerData().fexcoreMultiBlock) add(FEXCORE_MULTI_BLOCK)
        }
    }
}
