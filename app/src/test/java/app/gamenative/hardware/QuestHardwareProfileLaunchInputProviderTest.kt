package app.gamenative.hardware

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class QuestHardwareProfileLaunchInputProviderTest {

    @Test
    fun resolve_convertsQualifiedProfileIntoLaunchInputWithoutMutatingContainerState() =
        kotlinx.coroutines.test.runTest {
            val descriptor = QuestDeviceDescriptor(
                buildManufacturer = "Oculus",
                buildModel = "Quest 2",
                buildDevice = "hollywood",
                buildProduct = "hollywood",
                socManufacturer = "Qualcomm",
                socModel = "SM8250",
                gpuRenderer = "Adreno (TM) 650",
                isMetaXrRuntime = true,
                classifiedDevice = ClassifiedQuestDevice.QUEST_2,
                matchedRuleId = QuestDeviceDetectorImpl.RULE_QUEST_2_EXACT,
                confidence = 1.0
            )
            val profile = HardwareExecutionProfile(
                profileId = "quest_2",
                schemaVersion = HardwareExecutionProfile.CURRENT_SCHEMA_VERSION,
                acceptedRuleIds = listOf(QuestDeviceDetectorImpl.RULE_QUEST_2_EXACT),
                containerVariant = "bionic",
                wineVersion = "proton-10.0-arm64ec-2",
                wow64Mode = true,
                emulator = "FEXCore",
                fexcoreVersion = "2605",
                fexcoreTSOMode = "Fast",
                fexcoreX87Mode = "Fast",
                fexcoreMultiBlock = "Disabled",
                fexcorePreset = "INTERMEDIATE",
                box86Version = "0.3.2",
                box64Version = "0.4.2",
                box86Preset = "COMPATIBILITY",
                box64Preset = "COMPATIBILITY",
                graphicsDriver = "Wrapper",
                graphicsDriverVersion = "Turnip v26.2.0 R4",
                graphicsDriverConfig = "",
                dxwrapper = "dxvk",
                dxwrapperConfig = "version=1.11.1-sarek,vkd3dVersion=2.14.1",
                requiredPackagedComponents = listOf(RequiredComponentSpec("fexcore_2605"))
            )
            val resolution = HardwareProfileResolutionResult(
                descriptor = descriptor,
                profile = profile,
                resolutionSource = ProfileResolutionSource.EXACT_MATCH,
                fieldDecisions = emptyMap()
            )
            val detector = object : QuestDeviceDetector {
                override fun detectDevice(context: Context) = descriptor
                override fun classifyFacts(facts: QuestRawHardwareFacts) = descriptor
            }
            val resolver = object : HardwareExecutionProfileResolver {
                override fun resolveProfile(descriptor: QuestDeviceDescriptor) = resolution

                override fun applyToContainerData(
                    base: com.winlator.container.ContainerData,
                    result: HardwareProfileResolutionResult
                ) = base
            }

            val context = ApplicationProvider.getApplicationContext<Context>()
            val input = QuestHardwareProfileLaunchInputProvider(context, detector, resolver).resolve()

            assertEquals("quest_2", input.profileId)
            assertEquals("QUEST_2", input.deviceDescriptor)
            assertEquals(QuestDeviceDetectorImpl.RULE_QUEST_2_EXACT, input.matchedRuleId)
            assertEquals("Turnip v26.2.0 R4", input.graphicsDriverVersion)
            assertEquals(listOf("fexcore_2605"), input.requiredComponentIds)
        }
}
