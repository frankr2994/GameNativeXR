package app.gamenative.launch.vr.mod

import app.gamenative.launch.inspect.ExecutableIdentity

/**
 * Defines a plan to inject and hook an external VR mod into a specific guest executable.
 */
data class VrHookPlan(
    val modId: String,
    val requiresPassthrough: Boolean,
    val environmentOverrides: Map<String, String>,
    val dllOverrides: Map<String, String>,
    val placementPaths: List<String>
)

/**
 * Parses manifests, matches exact game/executable facts, and creates a hook plan.
 */
interface VrModResolver {
    fun resolveModHook(modManifestUri: String, executableIdentity: ExecutableIdentity): VrHookPlan?
}
