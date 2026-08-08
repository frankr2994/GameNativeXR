package app.gamenative.hardware.quest

import android.content.Context
import android.os.Build
import com.winlator.core.GPUInformation
import com.winlator.xr.XrActivity
import com.winlator.xr.runtime.MetaQuest

object QuestDeviceDetector {
    fun collectDescriptor(context: Context): QuestDeviceDescriptor {
        val isMetaQuestRuntime = try {
            val activity = XrActivity.getInstance()
            activity != null && activity is MetaQuest
        } catch (e: Exception) {
            false
        }
        
        return QuestDeviceDescriptor(
            manufacturer = Build.MANUFACTURER ?: "",
            brand = Build.BRAND ?: "",
            model = Build.MODEL ?: "",
            device = Build.DEVICE ?: "",
            product = Build.PRODUCT ?: "",
            hardware = Build.HARDWARE ?: "",
            socManufacturer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MANUFACTURER else null,
            socModel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL else null,
            glRenderer = GPUInformation.getRenderer(context),
            glVendor = GPUInformation.getVendor(context),
            supportedAbis = Build.SUPPORTED_ABIS?.toList() ?: emptyList(),
            androidVersion = Build.VERSION.SDK_INT,
            securityPatch = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Build.VERSION.SECURITY_PATCH else "",
            isMetaQuestRuntime = isMetaQuestRuntime
        )
    }
}
