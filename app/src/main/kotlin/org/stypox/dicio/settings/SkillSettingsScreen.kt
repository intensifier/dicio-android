package org.stypox.dicio.settings

import android.app.Application
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.dicio.skill.skill.SkillInfo
import org.stypox.dicio.R
import org.stypox.dicio.di.SkillContextImpl
import org.stypox.dicio.settings.datastore.UserSettingsModule.Companion.newDataStoreForPreviews
import org.stypox.dicio.eval.SkillHandler
import org.stypox.dicio.skills.lyrics.LyricsInfo
import org.stypox.dicio.skills.search.SearchInfo
import org.stypox.dicio.skills.weather.WeatherInfo
import org.stypox.dicio.skills.weather.WeatherSkill
import org.stypox.dicio.ui.theme.AppTheme
import org.stypox.dicio.ui.util.SkillInfoPreviews
import org.stypox.dicio.util.PermissionUtils
import org.stypox.dicio.util.ShareUtils

const val DICIO_NUMBERS_LINK = "https://github.com/Stypox/dicio-numbers"

@Composable
fun SkillSettingsScreen(
    navigationIcon: @Composable () -> Unit,
    viewModel: SkillSettingsViewModel = hiltViewModel(),
) {
    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { Text(stringResource(R.string.pref_skills_title)) },
                navigationIcon = navigationIcon
            )
        }
    ) {
        SkillSettingsScreen(viewModel = viewModel, modifier = Modifier.padding(it))
    }
}

@Composable
fun SkillSettingsScreen(
    viewModel: SkillSettingsViewModel,
    modifier: Modifier = Modifier,
) {
    val skills = viewModel.skills
    val enabledSkills by viewModel.enabledSkills.collectAsState()

    LazyColumn(
        contentPadding = PaddingValues(top = 4.dp, bottom = 4.dp),
        modifier = modifier,
    ) {
        if (viewModel.numberLibraryNotAvailable) {
            item {
                val context = LocalContext.current
                Card(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    onClick = { ShareUtils.openUrlInBrowser(context, DICIO_NUMBERS_LINK) },
                ) {
                    Text(
                        text = stringResource(R.string.pref_skill_number_library_not_available),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    )
                }
            }
        }
        items(skills) { skill ->
            SkillSettingsItem(
                skill = skill,
                isAvailable = skill.isAvailable(viewModel.skillContext),
                enabled = enabledSkills.getOrDefault(skill.id, true),
                setEnabled = { enabled -> viewModel.setSkillEnabled(skill.id, enabled) }
            )
        }
    }
}

@Composable
fun SkillSettingsItem(
    skill: SkillInfo,
    isAvailable: Boolean,
    enabled: Boolean,
    setEnabled: (Boolean) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    SkillSettingsItem(
        skill = skill,
        isAvailable = isAvailable,
        enabled = enabled,
        setEnabled = setEnabled,
        expanded = expanded,
        toggleExpanded = { expanded = !expanded },
    )
}

@Composable
fun SkillSettingsItem(
    skill: SkillInfo,
    isAvailable: Boolean,
    enabled: Boolean,
    setEnabled: (Boolean) -> Unit,
    expanded: Boolean,
    toggleExpanded: () -> Unit,
) {
    val canExpand = isAvailable &&
            (skill.renderSettings != null || skill.neededPermissions.isNotEmpty())

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .animateContentSize()
    ) {
        SkillSettingsItemHeader(
            expanded = expanded,
            toggleExpanded = if (canExpand) toggleExpanded else null,
            skill = skill,
            enabled = enabled && isAvailable,
            setEnabled = setEnabled,
            isAvailable = isAvailable,
        )

        if (!isAvailable) {
            Text(
                text = stringResource(R.string.pref_skill_not_available),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            )

        } else if (expanded) {
            if (skill.neededPermissions.isNotEmpty()) {
                SkillSettingsItemPermissionLine(skill)
            }

            skill.renderSettings?.invoke()
        }
    }
}

@Composable
private fun SkillSettingsItemHeader(
    expanded: Boolean,
    toggleExpanded: (() -> Unit)?,
    skill: SkillInfo,
    enabled: Boolean,
    setEnabled: (Boolean) -> Unit,
    isAvailable: Boolean,
) {
    val expandedAnimation by animateFloatAsState(
        label = "skill ${skill.id} card expanded",
        targetValue = if (expanded) 180f else 0f
    )

    Row(
        modifier = Modifier
            .let { if (toggleExpanded != null) it.clickable(onClick = toggleExpanded) else it }
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val maybeDisabledColor = if (isAvailable) {
            LocalContentColor.current
        } else {
            LocalContentColor.current.copy(alpha = 0.8f)
        }
        Icon(
            painter = skill.icon(),
            contentDescription = skill.name(LocalContext.current),
            modifier = Modifier
                .padding(start = 12.dp)
                .size(24.dp),
            tint = maybeDisabledColor,
        )
        Checkbox(
            checked = enabled,
            onCheckedChange = setEnabled,
            enabled = isAvailable,
        )
        Text(
            text = skill.name(LocalContext.current),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            modifier = Modifier.weight(1.0f),
            color = maybeDisabledColor,
        )
        if (toggleExpanded != null) {
            IconButton(
                onClick = toggleExpanded,
                modifier = Modifier.testTag("expand_skill_settings_handle"),
            ) {
                Icon(
                    modifier = Modifier.rotate(expandedAnimation),
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = stringResource(
                        if (expanded) R.string.reduce else R.string.expand
                    )
                )
            }
        }
    }
}

@Preview
@Composable
private fun SkillSettingsItemPermissionLine(@PreviewParameter(SkillInfoPreviews::class) skill: SkillInfo) {
    var allPermissionsGranted by remember { mutableStateOf(true) }
    val context = LocalContext.current
    LaunchedEffect(skill.neededPermissions) {
        allPermissionsGranted = PermissionUtils
            .checkPermissions(context, *skill.neededPermissions.toTypedArray())
    }

    val needingPermissionsString = if (LocalInspectionMode.current) {
        // getCommaJoinedPermissions doesn't work inside @Preview
        "Requires these permissions: directly call contacts, whatever 123, test"
    } else {
        stringResource(
            R.string.pref_skill_missing_permissions,
            PermissionUtils.getCommaJoinedPermissions(LocalContext.current, skill)
        )
    }

    if (allPermissionsGranted) {
        Text(
            text = needingPermissionsString,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        )

    } else {
        val launcher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { isGranted ->
            allPermissionsGranted = isGranted.values.all { it }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
        ) {
            Text(
                text = needingPermissionsString,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.0f)
                    .padding(end = 8.dp),
            )

            ElevatedButton(
                onClick = { launcher.launch(skill.neededPermissions.toTypedArray()) },
            ) {
                Text(text = stringResource(R.string.pref_skill_grant_permissions))
            }
        }
    }
}

@Preview
@Composable
private fun SkillSettingsItemPreview(@PreviewParameter(SkillInfoPreviews::class) skill: SkillInfo) {
    SkillSettingsItem(
        skill = skill,
        isAvailable = true,
        enabled = false,
        setEnabled = {},
    )
}

// this preview is useful to take screenshots
@Preview
@Composable
private fun ThreeSkillSettingsItemsPreview() {
    AppTheme {
        LazyColumn(
            contentPadding = PaddingValues(top = 4.dp, bottom = 4.dp),
        ) {
            item {
                SkillSettingsItem(
                    skill = WeatherInfo,
                    isAvailable = true,
                    enabled = true,
                    setEnabled = {},
                    expanded = true,
                    toggleExpanded = {},
                )
            }
            item {
                SkillSettingsItem(
                    skill = SearchInfo,
                    isAvailable = true,
                    enabled = false,
                    setEnabled = {},
                    expanded = false,
                    toggleExpanded = {},
                )
            }
            item {
                SkillSettingsItem(
                    skill = LyricsInfo,
                    isAvailable = false,
                    enabled = true,
                    setEnabled = {},
                    expanded = false,
                    toggleExpanded = {},
                )
            }
        }
    }
}

@Preview
@Composable
private fun SkillSettingsScreenPreview() {
    AppTheme {
        Surface(
            color = MaterialTheme.colorScheme.background
        ) {
            SkillSettingsScreen(
                SkillSettingsViewModel(
                    application = Application(),
                    dataStore = newDataStoreForPreviews(),
                    skillContext = SkillContextImpl.newForPreviews(LocalContext.current),
                    skillHandler = SkillHandler.newForPreviews(LocalContext.current),
                )
            )
        }
    }
}

@Preview
@Composable
private fun SkillSettingsScreenWithTopBarPreview() {
    AppTheme {
        Surface(
            color = MaterialTheme.colorScheme.background
        ) {
            SkillSettingsScreen(
                navigationIcon = {
                    IconButton(onClick = {}) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                        )
                    }
                },
                SkillSettingsViewModel(
                    application = Application(),
                    dataStore = newDataStoreForPreviews(),
                    skillContext = SkillContextImpl.newForPreviews(LocalContext.current),
                    skillHandler = SkillHandler.newForPreviews(LocalContext.current),
                )
            )
        }
    }
}

