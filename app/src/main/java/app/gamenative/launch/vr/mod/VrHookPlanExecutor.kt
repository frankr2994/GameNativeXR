package app.gamenative.launch.vr.mod

/**
 * Performs reversible pre-launch placement and environment changes, then monitors the mod handshake.
 */
interface VrHookPlanExecutor {
    /**
     * Executes the hook plan safely, creating backups if needed.
     */
    fun executePlan(plan: VrHookPlan): Boolean

    /**
     * Reverts all file placements and changes made by the executor.
     */
    fun revertPlan(plan: VrHookPlan)
}
