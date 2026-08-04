package app.gamenative.launch.vr

/**
 * Defines the tracking and rendering contract exposed to the Windows guest environment.
 */
enum class TrackingMode(val description: String) {
    /** 
     * HMD orientation only; no HMD/controller position is injected into the game. 
     * Existing virtual-screen XR presentation. Default for every title without a validated 
     * native VR adapter or compatible active VR mod.
     */
    FLAT_3DOF("HMD orientation only; no HMD/controller position is injected into the game"),
    
    /** 
     * Full HMD and controller position/orientation/actions. 
     * Uses Guest OpenXR/OpenVR adapter to the Android OpenXR host. 
     * Only when the title/runtime adapter validates architecture and required APIs.
     */
    NATIVE_OPENXR_6DOF("Full HMD and controller position/orientation/actions"),
    
    /** 
     * Full pose/action contract requested by a matched external mod. 
     * Uses manifest-selected hook plus versioned XR host bridge. 
     * Only after exact manifest, hash, architecture, materialization, and handshake validation.
     */
    MODDED_6DOF("Full pose/action contract requested by a matched external mod")
}
