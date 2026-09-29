package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.MovieCreation
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.local.VideoGenerationEntity
import com.example.ui.components.CinemaMonitorCard
import com.example.ui.components.DialogueComposerSection
import com.example.ui.theme.EmeraldReady
import com.example.ui.theme.TorchAmber
import com.example.ui.viewmodel.StudioTab
import com.example.ui.viewmodel.StudioUiState
import com.example.ui.viewmodel.StudioViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VeoStudioScreen(
    viewModel: StudioViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val savedGenerations by viewModel.savedGenerations.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.MovieCreation,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(7.dp)
                                    .size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = stringResource(R.string.studio_subtitle),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                NavigationBarItem(
                    selected = uiState.currentTab == StudioTab.DIRECTOR,
                    onClick = { viewModel.selectTab(StudioTab.DIRECTOR) },
                    icon = {
                        Icon(
                            imageVector = if (uiState.currentTab == StudioTab.DIRECTOR) {
                                Icons.Filled.MovieCreation
                            } else {
                                Icons.Outlined.MovieCreation
                            },
                            contentDescription = stringResource(R.string.tab_director)
                        )
                    },
                    label = { Text(stringResource(R.string.tab_director)) },
                    modifier = Modifier.testTag("nav_tab_director")
                )

                NavigationBarItem(
                    selected = uiState.currentTab == StudioTab.VAULT,
                    onClick = { viewModel.selectTab(StudioTab.VAULT) },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (savedGenerations.isNotEmpty()) {
                                    Badge { Text(savedGenerations.size.toString()) }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (uiState.currentTab == StudioTab.VAULT) {
                                    Icons.Filled.VideoLibrary
                                } else {
                                    Icons.Outlined.VideoLibrary
                                },
                                contentDescription = stringResource(R.string.tab_vault)
                            )
                        }
                    },
                    label = { Text(stringResource(R.string.tab_vault)) },
                    modifier = Modifier.testTag("nav_tab_vault")
                )

                NavigationBarItem(
                    selected = uiState.currentTab == StudioTab.SDK_TELEMETRY,
                    onClick = { viewModel.selectTab(StudioTab.SDK_TELEMETRY) },
                    icon = {
                        Icon(
                            imageVector = if (uiState.currentTab == StudioTab.SDK_TELEMETRY) {
                                Icons.Filled.Terminal
                            } else {
                                Icons.Outlined.Terminal
                            },
                            contentDescription = stringResource(R.string.tab_code)
                        )
                    },
                    label = { Text(stringResource(R.string.tab_code)) },
                    modifier = Modifier.testTag("nav_tab_telemetry")
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Feedback / Error Banner
            AnimatedVisibility(visible = uiState.bannerMessage != null || uiState.errorBanner != null) {
                val isError = uiState.errorBanner != null
                val text = uiState.errorBanner ?: uiState.bannerMessage.orEmpty()
                Surface(
                    color = if (isError) {
                        MaterialTheme.colorScheme.tertiaryContainer
                    } else {
                        EmeraldReady.copy(alpha = 0.18f)
                    },
                    border = BorderStroke(
                        1.dp,
                        if (isError) MaterialTheme.colorScheme.tertiary else EmeraldReady
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .testTag("status_feedback_banner")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (isError) Icons.Default.ErrorOutline else Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = if (isError) MaterialTheme.colorScheme.tertiary else EmeraldReady,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = text,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(
                            onClick = { viewModel.dismissBanners() },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss notification",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            when (uiState.currentTab) {
                StudioTab.DIRECTOR -> {
                    DirectorStudioContent(
                        uiState = uiState,
                        viewModel = viewModel,
                        onExportTake = { viewModel.exportTakeToMovies(it) }
                    )
                }

                StudioTab.VAULT -> {
                    TakesVaultScreen(
                        takes = savedGenerations,
                        onPlayTake = { viewModel.selectTakeForPlayback(it) },
                        onLoadPrompt = { viewModel.loadTakePromptIntoEditor(it) },
                        onExportTake = { viewModel.exportTakeToMovies(it) },
                        onDeleteTake = { viewModel.deleteTake(it) },
                        onNavigateBackToStudio = { viewModel.selectTab(StudioTab.DIRECTOR) }
                    )
                }

                StudioTab.SDK_TELEMETRY -> {
                    SdkTelemetryScreen(
                        uiState = uiState,
                        onClearLogs = { viewModel.clearLogs() },
                        onNavigateBackToStudio = { viewModel.selectTab(StudioTab.DIRECTOR) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DirectorStudioContent(
    uiState: StudioUiState,
    viewModel: StudioViewModel,
    onExportTake: (VideoGenerationEntity) -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isExpanded = maxWidth >= 760.dp

        if (isExpanded) {
            // Adaptive Two-Pane Layout for Tablets / Foldables / Landscape
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                LazyColumn(
                    modifier = Modifier.weight(0.48f),
                    contentPadding = PaddingValues(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        CinemaMonitorCard(
                            selectedTake = uiState.selectedTake,
                            isGenerating = uiState.isGenerating,
                            pollCount = uiState.pollCount,
                            secondsUntilNextPoll = uiState.secondsUntilNextPoll,
                            elapsedSeconds = uiState.elapsedSeconds,
                            activeOperationName = uiState.activeOperationName,
                            currentModel = uiState.selectedModel,
                            currentAspectRatio = uiState.aspectRatio,
                            currentResolution = uiState.resolution,
                            currentDownloadPath = uiState.downloadPath,
                            onStartGeneration = { viewModel.generateVideo() },
                            onCancelGeneration = { viewModel.cancelGeneration() },
                            onExportTake = onExportTake
                        )
                    }
                    item {
                        CompactTelemetryStrip(
                            uiState = uiState,
                            onOpenFullConsole = { viewModel.selectTab(StudioTab.SDK_TELEMETRY) }
                        )
                    }
                    item {
                        PrototypeSecurityNotice()
                    }
                }

                LazyColumn(
                    modifier = Modifier.weight(0.52f),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    item {
                        DialogueComposerSection(
                            uiState = uiState,
                            onSelectPreset = { viewModel.applyPreset(it) },
                            onRestoreDefault = { viewModel.restoreDefaultCrypticWallPrompt() },
                            onPromptChange = { viewModel.updatePrompt(it) },
                            onPolishPrompt = { viewModel.polishPromptWithAi() },
                            onAddDialogueCue = { s, d, l -> viewModel.addDialogueCue(s, d, l) },
                            onRemoveDialogueCue = { viewModel.removeDialogueCue(it) },
                            onAppendCameraKeyword = { viewModel.appendCameraKeyword(it) },
                            onModelChange = { viewModel.updateModel(it) },
                            onAspectRatioChange = { viewModel.updateAspectRatio(it) },
                            onResolutionChange = { viewModel.updateResolution(it) },
                            onDownloadPathChange = { viewModel.updateDownloadPath(it) },
                            onGenerateClick = { viewModel.generateVideo() }
                        )
                    }
                }
            }
        } else {
            // Mobile Single-Column Layout
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("director_studio_scroll"),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                item {
                    CinemaMonitorCard(
                        selectedTake = uiState.selectedTake,
                        isGenerating = uiState.isGenerating,
                        pollCount = uiState.pollCount,
                        secondsUntilNextPoll = uiState.secondsUntilNextPoll,
                        elapsedSeconds = uiState.elapsedSeconds,
                        activeOperationName = uiState.activeOperationName,
                        currentModel = uiState.selectedModel,
                        currentAspectRatio = uiState.aspectRatio,
                        currentResolution = uiState.resolution,
                        currentDownloadPath = uiState.downloadPath,
                        onStartGeneration = { viewModel.generateVideo() },
                        onCancelGeneration = { viewModel.cancelGeneration() },
                        onExportTake = onExportTake,
                        modifier = Modifier.widthIn(max = 640.dp)
                    )
                }

                item {
                    CompactTelemetryStrip(
                        uiState = uiState,
                        onOpenFullConsole = { viewModel.selectTab(StudioTab.SDK_TELEMETRY) },
                        modifier = Modifier.widthIn(max = 640.dp)
                    )
                }

                item {
                    DialogueComposerSection(
                        uiState = uiState,
                        onSelectPreset = { viewModel.applyPreset(it) },
                        onRestoreDefault = { viewModel.restoreDefaultCrypticWallPrompt() },
                        onPromptChange = { viewModel.updatePrompt(it) },
                        onPolishPrompt = { viewModel.polishPromptWithAi() },
                        onAddDialogueCue = { s, d, l -> viewModel.addDialogueCue(s, d, l) },
                        onRemoveDialogueCue = { viewModel.removeDialogueCue(it) },
                        onAppendCameraKeyword = { viewModel.appendCameraKeyword(it) },
                        onModelChange = { viewModel.updateModel(it) },
                        onAspectRatioChange = { viewModel.updateAspectRatio(it) },
                        onResolutionChange = { viewModel.updateResolution(it) },
                        onDownloadPathChange = { viewModel.updateDownloadPath(it) },
                        onGenerateClick = { viewModel.generateVideo() },
                        modifier = Modifier.widthIn(max = 640.dp)
                    )
                }

                item {
                    PrototypeSecurityNotice(modifier = Modifier.widthIn(max = 640.dp))
                }
            }
        }
    }
}

@Composable
private fun CompactTelemetryStrip(
    uiState: StudioUiState,
    onOpenFullConsole: () -> Unit,
    modifier: Modifier = Modifier
) {
    val latestLog = uiState.consoleLogs.lastOrNull()
    Surface(
        onClick = onOpenFullConsole,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("compact_telemetry_strip")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Terminal,
                    contentDescription = null,
                    tint = TorchAmber,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = latestLog?.let { "[${it.timestamp}] ${it.message}" }
                        ?: "Console ready",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "SDK Logs ->",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun PrototypeSecurityNotice(modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = stringResource(R.string.security_prototype_notice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
