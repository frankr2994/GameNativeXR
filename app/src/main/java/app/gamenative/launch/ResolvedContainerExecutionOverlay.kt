package app.gamenative.launch

import com.winlator.container.Container
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Temporarily applies an immutable [ResolvedContainerExecutionConfig] to the existing launch
 * container. The persisted container file is protected while the overlay is active and every
 * changed field is restored during backend cleanup.
 */
class ResolvedContainerExecutionOverlay private constructor(
    private val container: Container,
    private val snapshot: Snapshot,
) : AutoCloseable {
    private val closed = AtomicBoolean(false)

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        container.setContainerVariant(snapshot.containerVariant)
        container.setWineVersion(snapshot.wineVersion)
        container.setWoW64Mode(snapshot.wow64Mode)
        container.setEmulator(snapshot.emulator)
        container.setFEXCoreVersion(snapshot.fexcoreVersion)
        container.setFEXCorePreset(snapshot.fexcorePreset)
        container.setBox86Version(snapshot.box86Version)
        container.setBox64Version(snapshot.box64Version)
        container.setBox86Preset(snapshot.box86Preset)
        container.setBox64Preset(snapshot.box64Preset)
        container.setGraphicsDriver(snapshot.graphicsDriver)
        container.setGraphicsDriverVersion(snapshot.graphicsDriverVersion)
        container.setGraphicsDriverConfig(snapshot.graphicsDriverConfig)
        container.setDXWrapper(snapshot.dxwrapper)
        container.setDXWrapperConfig(snapshot.dxwrapperConfig)
        container.setLaunchExecutionPersistenceOverrides(snapshot.previousPersistenceOverrides)
    }

    private data class Snapshot(
        val previousPersistenceOverrides: JSONObject?,
        val containerVariant: String,
        val wineVersion: String,
        val wow64Mode: Boolean,
        val emulator: String,
        val fexcoreVersion: String,
        val fexcorePreset: String,
        val box86Version: String,
        val box64Version: String,
        val box86Preset: String,
        val box64Preset: String,
        val graphicsDriver: String,
        val graphicsDriverVersion: String,
        val graphicsDriverConfig: String,
        val dxwrapper: String,
        val dxwrapperConfig: String,
    ) {
        fun executionPersistenceOverrides(): JSONObject = JSONObject().apply {
            put("containerVariant", containerVariant)
            put("wineVersion", wineVersion)
            put("wow64Mode", wow64Mode)
            put("emulator", emulator)
            put("fexcoreVersion", fexcoreVersion)
            put("fexcorePreset", fexcorePreset)
            put("box86Version", box86Version)
            put("box64Version", box64Version)
            put("box86Preset", box86Preset)
            put("box64Preset", box64Preset)
            put("graphicsDriver", graphicsDriver)
            put("graphicsDriverVersion", graphicsDriverVersion)
            put("graphicsDriverConfig", graphicsDriverConfig)
            put("dxwrapper", dxwrapper)
            put("dxwrapperConfig", dxwrapperConfig)
        }
    }

    companion object {
        fun apply(container: Container, config: ResolvedContainerExecutionConfig): ResolvedContainerExecutionOverlay {
            val snapshot = Snapshot(
                previousPersistenceOverrides = container.launchExecutionPersistenceOverrides,
                containerVariant = container.getContainerVariant(),
                wineVersion = container.getWineVersion(),
                wow64Mode = container.isWoW64Mode(),
                emulator = container.getEmulator(),
                fexcoreVersion = container.getFEXCoreVersion(),
                fexcorePreset = container.getFEXCorePreset(),
                box86Version = container.getBox86Version(),
                box64Version = container.getBox64Version(),
                box86Preset = container.getBox86Preset(),
                box64Preset = container.getBox64Preset(),
                graphicsDriver = container.getGraphicsDriver(),
                graphicsDriverVersion = container.getGraphicsDriverVersion(),
                graphicsDriverConfig = container.getGraphicsDriverConfig(),
                dxwrapper = container.getDXWrapper(),
                dxwrapperConfig = container.getDXWrapperConfig(),
            )
            container.setLaunchExecutionPersistenceOverrides(snapshot.executionPersistenceOverrides())
            container.setContainerVariant(config.containerVariant)
            container.setWineVersion(config.wineVersion)
            container.setWoW64Mode(config.wow64Mode)
            container.setEmulator(config.emulator)
            container.setFEXCoreVersion(config.fexcoreVersion)
            container.setFEXCorePreset(config.fexcorePreset)
            container.setBox86Version(config.box86Version)
            container.setBox64Version(config.box64Version)
            container.setBox86Preset(config.box86Preset)
            container.setBox64Preset(config.box64Preset)
            container.setGraphicsDriver(config.graphicsDriver)
            container.setGraphicsDriverVersion(config.graphicsDriverVersion)
            container.setGraphicsDriverConfig(config.graphicsDriverConfig)
            container.setDXWrapper(config.dxwrapper)
            container.setDXWrapperConfig(config.dxwrapperConfig)
            return ResolvedContainerExecutionOverlay(container, snapshot)
        }
    }
}
