package app.gamenative.hardware

import android.content.Context

/**
 * Reads the versioned, packaged hardware-profile assets. Only a conservative profile identifier
 * format is accepted so callers cannot use this provider to address arbitrary Android assets.
 */
class AndroidAssetProfileProvider(context: Context) : ProfileAssetProvider {
    private val appContext = context.applicationContext

    override fun getProfileJson(profileId: String): String? {
        if (!PROFILE_ID.matches(profileId)) return null

        return runCatching {
            appContext.assets.open("profiles/$profileId.json")
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
        }.getOrNull()
    }

    private companion object {
        val PROFILE_ID = Regex("[a-z0-9_]+")
    }
}
