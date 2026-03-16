package com.winlator.xr

import android.content.Intent
import android.view.View
import androidx.compose.ui.platform.ComposeView
import app.gamenative.PluviaApp
import app.gamenative.events.AndroidEvent
import app.gamenative.ui.PluviaMain
import app.gamenative.utils.IntentLaunchManager
import com.winlator.XrActivity
import timber.log.Timber

object XrScreenHost {
    private const val ACTION_LAUNCH_GAME = "app.gamenative.LAUNCH_GAME"
    private const val EXTRA_APP_ID = "app_id"
    private const val EXTRA_GAME_SOURCE = "game_source"

    @JvmStatic
    fun createView(activity: XrActivity, containerId: String?): View {
        return ComposeView(activity).apply {
            setContent {
                PluviaMain()
            }
            post { emitLaunchIntentForPluviaMain(containerId) }
        }
    }

    private fun emitLaunchIntentForPluviaMain(containerId: String?) {
        if (containerId.isNullOrEmpty()) return

        try {
            val launchIntent = Intent(ACTION_LAUNCH_GAME).apply {
                putExtra(EXTRA_APP_ID, extractGameIdFromContainerId(containerId))
                putExtra(EXTRA_GAME_SOURCE, extractGameSourceFromContainerId(containerId))
            }

            val launchRequest = IntentLaunchManager.parseLaunchIntent(launchIntent)
            if (launchRequest != null) {
                PluviaApp.events.emitJava(AndroidEvent.ExternalGameLaunch(launchRequest.appId))
                Timber.d("[IntentLaunch][XR]: Emitted ExternalGameLaunch for %s", launchRequest.appId)
            }
        } catch (e: Exception) {
            Timber.e(e, "[IntentLaunch][XR]: Failed to emit launch intent for container %s", containerId)
        }
    }

    private fun extractGameIdFromContainerId(containerId: String): Int {
        val idWithoutSuffix = if (containerId.contains("(")) {
            containerId.substringBefore("(")
        } else {
            containerId
        }
        val parts = idWithoutSuffix.split("_")
        val lastPart = parts.lastOrNull() ?: throw IllegalArgumentException("Invalid container ID format: $containerId")
        return lastPart.toInt()
    }

    private fun extractGameSourceFromContainerId(containerId: String): String {
        val idWithoutSuffix = if (containerId.contains("(")) {
            containerId.substringBefore("(")
        } else {
            containerId
        }
        val separatorIndex = idWithoutSuffix.lastIndexOf('_')
        return idWithoutSuffix.substring(0, separatorIndex)
    }
}
