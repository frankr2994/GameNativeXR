package app.gamenative.launch

import java.util.Locale

/**
 * Converts the three legacy FEX tuning fields into the documented FEX environment variables.
 * BionicProgramLauncherComponent applies its preset first and then merges the supplied launch
 * variables, so these resolved values intentionally win without changing global preferences.
 */
object FexCoreLaunchEnvironment {
    fun overridesFor(config: ResolvedContainerExecutionConfig): Map<String, String> = buildMap {
        when (config.fexcoreTSOMode.normalized()) {
            "fastest" -> putTso(enabled = false, vector = false, memcpySet = false, halfBarrier = false)
            "fast" -> putTso(enabled = true, vector = false, memcpySet = false, halfBarrier = true)
            "slow", "slowest" -> putTso(enabled = true, vector = true, memcpySet = true, halfBarrier = true)
        }
        when (config.fexcoreX87Mode.normalized()) {
            "fast" -> put("FEX_X87REDUCEDPRECISION", "1")
            "slow" -> put("FEX_X87REDUCEDPRECISION", "0")
        }
        when (config.fexcoreMultiBlock.normalized()) {
            "enabled" -> put("FEX_MULTIBLOCK", "1")
            "disabled" -> put("FEX_MULTIBLOCK", "0")
        }
    }

    private fun MutableMap<String, String>.putTso(
        enabled: Boolean,
        vector: Boolean,
        memcpySet: Boolean,
        halfBarrier: Boolean,
    ) {
        put("FEX_TSOENABLED", enabled.asFexFlag())
        put("FEX_VECTORTSOENABLED", vector.asFexFlag())
        put("FEX_MEMCPYSETTSOENABLED", memcpySet.asFexFlag())
        put("FEX_HALFBARRIERTSOENABLED", halfBarrier.asFexFlag())
    }

    private fun Boolean.asFexFlag(): String = if (this) "1" else "0"

    private fun String.normalized(): String = trim().lowercase(Locale.ROOT)
}
