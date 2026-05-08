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
import com.winlator.xserver.XKeycode


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

        // Controller mapping
        val values = XKeycode.entries.toTypedArray()
        val keyLabels = ArrayList<String>()
        val keyValues = ArrayList<Int>()
        for (value in values) {
            keyLabels.add(value.name)
            keyValues.add(value.id.toInt())
        }
        SettingsListDropdown(
            colors = settingsTileColors(),
            title = { Text(text = stringResource(R.string.button_a)) },
            value = 0.coerceAtLeast(keyValues.indexOf(config.xrButtonA)),
            items = keyLabels,
            onItemSelected = { idx -> state.config.value = config.copy(xrButtonA = keyValues[idx]) },
        )
        SettingsListDropdown(
            colors = settingsTileColors(),
            title = { Text(text = stringResource(R.string.button_b)) },
            value = 0.coerceAtLeast(keyValues.indexOf(config.xrButtonB)),
            items = keyLabels,
            onItemSelected = { idx -> state.config.value = config.copy(xrButtonB = keyValues[idx]) },
        )
        SettingsListDropdown(
            colors = settingsTileColors(),
            title = { Text(text = stringResource(R.string.button_x)) },
            value = 0.coerceAtLeast(keyValues.indexOf(config.xrButtonX)),
            items = keyLabels,
            onItemSelected = { idx -> state.config.value = config.copy(xrButtonX = keyValues[idx]) },
        )
        SettingsListDropdown(
            colors = settingsTileColors(),
            title = { Text(text = stringResource(R.string.button_y)) },
            value = 0.coerceAtLeast(keyValues.indexOf(config.xrButtonY)),
            items = keyLabels,
            onItemSelected = { idx -> state.config.value = config.copy(xrButtonY = keyValues[idx]) },
        )
        SettingsListDropdown(
            colors = settingsTileColors(),
            title = { Text(text = stringResource(R.string.button_grip)) },
            value = 0.coerceAtLeast(keyValues.indexOf(config.xrButtonGrip)),
            items = keyLabels,
            onItemSelected = { idx -> state.config.value = config.copy(xrButtonGrip = keyValues[idx]) },
        )
        SettingsListDropdown(
            colors = settingsTileColors(),
            title = { Text(text = stringResource(R.string.button_trigger)) },
            value = 0.coerceAtLeast(keyValues.indexOf(config.xrButtonTrigger)),
            items = keyLabels,
            onItemSelected = { idx -> state.config.value = config.copy(xrButtonTrigger = keyValues[idx]) },
        )
        SettingsListDropdown(
            colors = settingsTileColors(),
            title = { Text(text = stringResource(R.string.thumbstick_up)) },
            value = 0.coerceAtLeast(keyValues.indexOf(config.xrThumbstickUp)),
            items = keyLabels,
            onItemSelected = { idx -> state.config.value = config.copy(xrThumbstickUp = keyValues[idx]) },
        )
        SettingsListDropdown(
            colors = settingsTileColors(),
            title = { Text(text = stringResource(R.string.thumbstick_down)) },
            value = 0.coerceAtLeast(keyValues.indexOf(config.xrThumbstickDown)),
            items = keyLabels,
            onItemSelected = { idx -> state.config.value = config.copy(xrThumbstickDown = keyValues[idx]) },
        )
        SettingsListDropdown(
            colors = settingsTileColors(),
            title = { Text(text = stringResource(R.string.thumbstick_left)) },
            value = 0.coerceAtLeast(keyValues.indexOf(config.xrThumbstickLeft)),
            items = keyLabels,
            onItemSelected = { idx -> state.config.value = config.copy(xrThumbstickLeft = keyValues[idx]) },
        )
        SettingsListDropdown(
            colors = settingsTileColors(),
            title = { Text(text = stringResource(R.string.thumbstick_right)) },
            value = 0.coerceAtLeast(keyValues.indexOf(config.xrThumbstickRight)),
            items = keyLabels,
            onItemSelected = { idx -> state.config.value = config.copy(xrThumbstickRight = keyValues[idx]) },
        )
    }
}
