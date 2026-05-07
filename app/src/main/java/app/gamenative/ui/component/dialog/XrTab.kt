package app.gamenative.ui.component.dialog

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.gamenative.R
import app.gamenative.ui.component.settings.SettingsListDropdown
import app.gamenative.ui.theme.settingsTileColors
import app.gamenative.ui.theme.settingsTileColorsAlt
import com.alorma.compose.settings.ui.SettingsGroup
import com.alorma.compose.settings.ui.SettingsSwitch

@Composable
fun XrTabContent(state: ContainerConfigState) {
    val config = state.config.value

    SettingsGroup() {
        // Injectors
        SettingsSwitch(
            colors = settingsTileColorsAlt(),
            title = { Text(text = stringResource(R.string.use_reshade)) },
            subtitle = { Text(text = stringResource(R.string.use_reshade_desc)) },
            state = config.xrUseReshade,
            onCheckedChange = { state.config.value = config.copy(xrUseReshade = it) },
        )
        SettingsSwitch(
            colors = settingsTileColorsAlt(),
            title = { Text(text = stringResource(R.string.force_dxgi)) },
            subtitle = { Text(text = stringResource(R.string.force_dxgi_desc)) },
            enabled = config.xrUseReshade,
            state = config.xrForceDCGI,
            onCheckedChange = { state.config.value = config.copy(xrForceDCGI = it) },
        )
        SettingsSwitch(
            colors = settingsTileColorsAlt(),
            title = { Text(text = stringResource(R.string.use_trackir)) },
            subtitle = { Text(text = stringResource(R.string.use_trackir_desc)) },
            state = config.xrUseTrackIR,
            onCheckedChange = { state.config.value = config.copy(xrUseTrackIR = it) },
        )

        // Performance
        val levelsValues = listOf(0, 25, 50, 75)
        val levelsLabels = listOf("Power saving", "Low", "High", "Boost")
        SettingsListDropdown(
            colors = settingsTileColors(),
            title = { Text(text = stringResource(R.string.cpu_level)) },
            value = 0.coerceAtLeast(levelsValues.indexOf(config.xrCPULevel)),
            items = levelsLabels,
            onItemSelected = { idx -> state.config.value = config.copy(xrCPULevel = levelsValues[idx]) },
        )
        SettingsListDropdown(
            colors = settingsTileColors(),
            title = { Text(text = stringResource(R.string.gpu_level)) },
            value = 0.coerceAtLeast(levelsValues.indexOf(config.xrGPULevel)),
            items = levelsLabels,
            onItemSelected = { idx -> state.config.value = config.copy(xrGPULevel = levelsValues[idx]) },
        )
        val refreshValues = listOf(60, 72, 90)
        val refreshLabels = listOf("60Hz (Quest 2 only)", "72Hz", "90Hz")
        SettingsListDropdown(
            colors = settingsTileColors(),
            title = { Text(text = stringResource(R.string.refresh_rate)) },
            value = 0.coerceAtLeast(refreshValues.indexOf(config.xrRefreshRate)),
            items = refreshLabels,
            onItemSelected = { idx -> state.config.value = config.copy(xrRefreshRate = refreshValues[idx]) },
        )
    }
}
