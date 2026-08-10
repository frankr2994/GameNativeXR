package app.gamenative.launch

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class AndroidRuntimeComponentValidatorTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val driverDirectory = File(
        context.filesDir,
        "contents/adrenotools/Turnip v26.2.0 R4"
    )

    @After
    fun removeInstalledDriverFixture() {
        driverDirectory.deleteRecursively()
    }

    @Test
    fun validate_acceptsInstalledQuest2TurnipDriver() = runTest {
        installTurnipDriver()

        AndroidRuntimeComponentValidator(context).validate(setOf("turnip_v26_2_0_r4"))
    }

    @Test
    fun validate_rejectsInstalledDriverWithMismatchedMetadata() = runTest {
        installTurnipDriver(name = "Turnip v26.0.0 R4")

        try {
            AndroidRuntimeComponentValidator(context).validate(setOf("turnip_v26_2_0_r4"))
            fail("Expected a missing-component failure")
        } catch (failure: LaunchFailureException.MissingComponent) {
            assertEquals("RUNTIME_COMPONENT_MISSING", failure.failureCode)
        }
    }

    private fun installTurnipDriver(name: String = "Turnip v26.2.0 R4") {
        driverDirectory.mkdirs()
        File(driverDirectory, "libvulkan_freedreno.so").writeBytes(byteArrayOf(1))
        File(driverDirectory, "meta.json").writeText(
            """
            {
              "schemaVersion": 1,
              "name": "$name",
              "vendor": "Mesa",
              "libraryName": "libvulkan_freedreno.so",
              "minApi": 28
            }
            """.trimIndent()
        )
    }
}
