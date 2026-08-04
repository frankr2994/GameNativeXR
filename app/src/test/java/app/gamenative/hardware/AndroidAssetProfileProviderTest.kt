package app.gamenative.hardware

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AndroidAssetProfileProviderTest {

    @Test
    fun getProfileJson_readsPackagedQuest2Profile() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val json = AndroidAssetProfileProvider(context).getProfileJson("quest_2")

        assertNotNull(json)
        assertTrue(json!!.contains("\"profileId\": \"quest_2\""))
    }

    @Test
    fun getProfileJson_rejectsPathTraversalAndUnsupportedIdentifiers() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val provider = AndroidAssetProfileProvider(context)

        assertNull(provider.getProfileJson("../quest_2"))
        assertNull(provider.getProfileJson("quest-2"))
    }
}
