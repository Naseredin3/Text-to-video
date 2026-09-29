package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.EmeraldReady
import com.example.ui.theme.TorchAmber
import com.example.ui.viewmodel.StudioUiState

@Composable
fun SdkTelemetryScreen(
    uiState: StudioUiState,
    onClearLogs: () -> Unit,
    onNavigateBackToStudio: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onNavigateBackToStudio)

    val context = LocalContext.current
    var showKotlinCode by rememberSaveable { mutableStateOf(false) }
    var copiedFeedback by remember { mutableStateOf(false) }

    val jsCodeSnippet = remember(uiState.prompt, uiState.selectedModel, uiState.downloadPath) {
        """
import { GoogleGenAI } from "@google/genai";

const ai = new GoogleGenAI({});

const prompt = `${uiState.prompt}`;

let operation = await ai.models.generateVideos({
    model: "${uiState.selectedModel}",
    prompt: prompt,
});

// Poll the operation status until the video is ready.
while (!operation.done) {
    console.log("Waiting for video generation to complete...")
    await new Promise((resolve) => setTimeout(resolve, 10000));
    operation = await ai.operations.getVideosOperation({
        operation: operation,
    });
}

// Download the generated video.
ai.files.download({
    file: operation.response.generatedVideos[0].video,
    downloadPath: "${uiState.downloadPath}",
});
console.log(`Generated video saved to ${uiState.downloadPath}`);
        """.trimIndent()
    }

    val kotlinCodeSnippet = remember(uiState.prompt, uiState.selectedModel, uiState.downloadPath, uiState.aspectRatio, uiState.resolution) {
        """
// Android Kotlin + Retrofit Veo 3.1 Implementation
val request = GenerateVideosRequest(
    prompt = ""${'"'}${uiState.prompt}""${'"'},
    config = VeoConfig(
        numberOfVideos = 1,
        resolution = "${uiState.resolution}",
        aspectRatio = "${uiState.aspectRatio}"
    )
)

var operation = VeoOperationParser.parse(
    VeoRetrofitClient.service.generateVideos(
        model = "${uiState.selectedModel}",
        apiKey = BuildConfig.GEMINI_API_KEY,
        request = request
    )
)

// Poll every 10,000ms until operation.done == true
while (!operation.done) {
    Log.i("VeoStudio", "Waiting for video generation to complete...")
    delay(10_000L)
    val polled = VeoRetrofitClient.service.getVideosOperation(
        operationName = operation.name,
        apiKey = BuildConfig.GEMINI_API_KEY
    )
    operation = VeoOperationParser.parse(polled)
}

// Download MP4 to local storage
val targetFile = File(filesDir, "${uiState.downloadPath}")
VeoRetrofitClient.downloadGeneratedVideo(operation, BuildConfig.GEMINI_API_KEY, targetFile)
        """.trimIndent()
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("sdk_telemetry_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Live Operation Telemetry Console Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Terminal,
                                contentDescription = null,
                                tint = TorchAmber
                            )
                            Column {
                                Text(
                                    text = "Live Operation Polling Console",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "10,000ms interval getVideosOperation logs",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = onClearLogs,
                            modifier = Modifier.testTag("clear_logs_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Clear")
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.background,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            uiState.consoleLogs.forEach { entry ->
                                val textColor = when {
                                    entry.isError -> MaterialTheme.colorScheme.tertiary
                                    entry.isSuccess -> EmeraldReady
                                    else -> MaterialTheme.colorScheme.onBackground
                                }
                                Text(
                                    text = "[${entry.timestamp}] ${entry.message}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = textColor
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. Live Code Inspector Card (@google/genai JS & Android Kotlin)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Code,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                text = "Live Veo 3.1 Code Sync",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        FilledTonalButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                val activeCode = if (showKotlinCode) kotlinCodeSnippet else jsCodeSnippet
                                clipboard?.setPrimaryClip(ClipData.newPlainText("Veo 3.1 Code", activeCode))
                                copiedFeedback = true
                            },
                            modifier = Modifier.testTag("copy_code_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (copiedFeedback) "Copied!" else "Copy Code")
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !showKotlinCode,
                            onClick = {
                                showKotlinCode = false
                                copiedFeedback = false
                            },
                            label = { Text("@google/genai (TypeScript)") },
                            modifier = Modifier.testTag("code_tab_js")
                        )
                        FilterChip(
                            selected = showKotlinCode,
                            onClick = {
                                showKotlinCode = true
                                copiedFeedback = false
                            },
                            label = { Text("Android Retrofit (Kotlin)") },
                            modifier = Modifier.testTag("code_tab_kotlin")
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.background,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (showKotlinCode) kotlinCodeSnippet else jsCodeSnippet,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(14.dp)
                        )
                    }
                }
            }
        }
    }
}
