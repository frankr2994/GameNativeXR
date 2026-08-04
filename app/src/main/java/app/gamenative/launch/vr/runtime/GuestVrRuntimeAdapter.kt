package app.gamenative.launch.vr.runtime

/**
 * Abstraction for Windows OpenXR and OpenVR-via-OpenComposite runtime bridging.
 */
interface GuestVrRuntimeAdapter {
    fun initializeRuntime(): Boolean
    fun shutdownRuntime()
    fun isRuntimeActive(): Boolean
}
