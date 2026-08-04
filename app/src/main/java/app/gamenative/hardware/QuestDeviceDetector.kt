package app.gamenative.hardware

import android.content.Context

interface QuestDeviceDetector {
    /**
     * Probes the system environment (Context, Build, GLES) and returns a classified QuestDeviceDescriptor.
     */
    fun detectDevice(context: Context): QuestDeviceDescriptor

    /**
     * Pure classification method operating directly on pre-assembled raw facts.
     * Enables direct, context-free unit testing.
     */
    fun classifyFacts(facts: QuestRawHardwareFacts): QuestDeviceDescriptor
}
