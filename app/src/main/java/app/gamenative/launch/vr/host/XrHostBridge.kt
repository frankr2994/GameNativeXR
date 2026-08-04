package app.gamenative.launch.vr.host

/**
 * Versioned Java/native host endpoint for tracking, actions, swapchain/frame submission, 
 * passthrough state, and health events.
 */
interface XrHostBridge {
    fun initializeHost(version: Int): Boolean
    fun shutdownHost()
    
    // Tracking and Passthrough
    fun updateTrackingData()
    fun enablePassthrough(enabled: Boolean)
    
    // Frame submission
    fun submitFrame()
}
