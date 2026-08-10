package app.gamenative.launch

import android.content.Context
import android.os.Build
import app.gamenative.utils.downloader.GraphicsDriverDownloader
import org.json.JSONObject
import java.io.File

/** Validates the concrete archives/content declarations referenced by packaged Quest profiles. */
class AndroidRuntimeComponentValidator(context: Context) : RuntimeComponentValidator {
    private val appContext = context.applicationContext

    override suspend fun validate(requiredComponentIds: Set<String>) {
        val missing = requiredComponentIds.filterNot(::isAvailable)
        if (missing.isNotEmpty()) {
            throw LaunchFailureException.MissingComponent(missing.sorted().joinToString(", "))
        }
    }

    private fun isAvailable(componentId: String): Boolean = when (componentId) {
        "wine_proton_10_arm64ec" -> runtimeDefaultDeclared("proton-10.0-arm64ec-2")
        "fexcore_2605" -> assetExists("fexcore/fexcore-2605.tzst")
        "turnip_v26_2_0_r4" -> installedTurnipV26_2_0R4Available() ||
            graphicsDriverAvailable("adrenotools-turnip26.2.0_R4")
        else -> false
    }

    private fun runtimeDefaultDeclared(runtimeId: String): Boolean = runCatching {
        val root = JSONObject(readAsset("runtime-component-provenance.json"))
        val components = root.getJSONArray("components")
        (0 until components.length()).any { index ->
            val component = components.getJSONObject(index)
            component.optString("id") == runtimeId &&
                component.optString("status") == "runtime_default"
        }
    }.getOrDefault(false)

    private fun graphicsDriverAvailable(driverId: String): Boolean = runCatching {
        val root = JSONObject(readAsset(GraphicsDriverDownloader.GRAPHICS_DRIVER_MANIFEST_FILE))
        val components = root.getJSONArray("components")
        val component = (0 until components.length())
            .asSequence()
            .map(components::getJSONObject)
            .firstOrNull { it.optString("id") == driverId }
            ?: return@runCatching false
        val fileName = component.optString("name")
        assetExists("graphics_driver/$fileName") ||
            File(appContext.filesDir, "${GraphicsDriverDownloader.GRAPHICS_DRIVER_CACHE_DIR}/$fileName")
                .let { it.isFile && it.length() > 0 }
    }.getOrDefault(false)

    /**
     * GameNative installs downloaded AdrenoTools drivers as unpacked content rather than
     * graphics-driver cache archives. The Quest 2 profile refers to this exact installed
     * Turnip release, so validate the library and its metadata before launching Wine.
     */
    private fun installedTurnipV26_2_0R4Available(): Boolean = runCatching {
        val driverDirectory = File(appContext.filesDir, "contents/adrenotools/Turnip v26.2.0 R4")
        val library = File(driverDirectory, "libvulkan_freedreno.so")
        val metadata = JSONObject(File(driverDirectory, "meta.json").readText())
        library.isFile &&
            library.length() > 0 &&
            metadata.optInt("schemaVersion", -1) == 1 &&
            metadata.optString("name") == "Turnip v26.2.0 R4" &&
            metadata.optString("vendor") == "Mesa" &&
            metadata.optString("libraryName") == "libvulkan_freedreno.so" &&
            metadata.optInt("minApi", Int.MAX_VALUE) <= Build.VERSION.SDK_INT
    }.getOrDefault(false)

    private fun assetExists(path: String): Boolean = runCatching {
        appContext.assets.open(path).use { true }
    }.getOrDefault(false)

    private fun readAsset(path: String): String =
        appContext.assets.open(path).bufferedReader().use { it.readText() }
}
