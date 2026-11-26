package com.pennywiseai.tracker.presentation.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.runtime.Composable
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.ui.components.cards.ListItemPosition
import com.pennywiseai.tracker.ui.theme.grey_dark
import com.pennywiseai.tracker.ui.theme.grey_light

@Composable
internal fun DeveloperPage(settingsViewModel: SettingsViewModel = hiltViewModel()) {
    val isDeveloperModeEnabled by settingsViewModel.isDeveloperModeEnabled.collectAsStateWithLifecycle(initialValue = false)

    SettingsGroup {
        SettingsSwitchRow(
            icon = Icons.Default.Code,
            iconBgColor = grey_light,
            iconTint = grey_dark,
            title = stringResource(R.string.settings_developer_mode_title),
            subtitle = stringResource(R.string.settings_developer_mode_subtitle),
            checked = isDeveloperModeEnabled,
            onCheckedChange = { settingsViewModel.toggleDeveloperMode(it) },
            position = ListItemPosition.Single
        )
    }
}
