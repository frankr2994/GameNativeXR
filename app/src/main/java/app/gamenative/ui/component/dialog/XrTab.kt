package app.gamenative.ui.component.dialog

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.gamenative.R
import app.gamenative.ui.theme.settingsTileColorsAlt
import com.alorma.compose.settings.ui.SettingsGroup
import com.alorma.compose.settings.ui.SettingsSwitch

@Composable
fun XrTabContent(state: ContainerConfigState) {
    val config = state.config.value

    SettingsGroup() {
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
    }
}
