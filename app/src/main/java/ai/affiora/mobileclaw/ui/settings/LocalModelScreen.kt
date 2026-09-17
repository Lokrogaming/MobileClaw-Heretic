package ai.affiora.mobileclaw.ui.settings

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ai.affiora.mobileclaw.agent.AiProvider
import ai.affiora.mobileclaw.agent.DeviceCapability
import ai.affiora.mobileclaw.agent.LocalModelManager
import ai.affiora.mobileclaw.agent.ModelState

@Composable
fun LocalModelPage(viewModel: SettingsViewModel) {
    val deviceCapability by viewModel.deviceCapability.collectAsState()
    val modelStates by viewModel.localModelStates.collectAsState()
    val selectedProvider by viewModel.selectedProvider.collectAsStateWithLifecycle()
    val selectedModel by viewModel.selectedModel.collectAsStateWithLifecycle()

    val customPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let { viewModel.importCustomModel(it) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Explainer
        Text(
            text = "Run AI models directly on your device. No API key, no internet, no cost.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Device capability card
        deviceCapability?.let { cap ->
            DeviceCapabilityCard(cap)
        }

        // Custom .litertlm import card (same logic as Settings → Provider page)
        CustomModelImportCard(
            viewModel = viewModel,
            selectedProvider = selectedProvider,
            selectedModel = selectedModel,
            onPickFile = { customPicker.launch(arrayOf("*/*")) },
        )

        // Model cards
        for (model in LocalModelManager.MODELS) {
            val state = modelStates[model.id] ?: ModelState.NotAvailable("Unknown")
            val isSelected = selectedProvider.isLocal && selectedModel == model.id
            ModelCard(
                modelName = model.displayName,
                fileSize = formatBytes(model.fileSizeBytes),
                ramRequired = "${model.requiredRamMb / 1000} GB RAM",
                state = state,
                isSelected = isSelected,
                onDownload = { viewModel.downloadModel(model.id) },
                onCancel = { viewModel.cancelDownload(model.id) },
                onDelete = { viewModel.deleteModel(model.id) },
                onActivate = { viewModel.selectLocalModel(model.id) },
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun CustomModelImportCard(
    viewModel: SettingsViewModel,
    selectedProvider: AiProvider,
    selectedModel: String,
    onPickFile: () -> Unit,
) {
    val modelStates by viewModel.localModelStates.collectAsState()
    val customName by viewModel.customModelDisplayName.collectAsStateWithLifecycle()
    val importError by viewModel.customImportError.collectAsStateWithLifecycle()

    val customState = modelStates[LocalModelManager.CUSTOM_MODEL_ID]
    val isActive = selectedProvider.isLocal && selectedModel == LocalModelManager.CUSTOM_MODEL_ID

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Eigenes Modell (.litertlm)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        when {
                            customState is ModelState.Downloaded && customName != null ->
                                "$customName · bereit"
                            customState is ModelState.Downloading ->
                                "Import läuft… ${(customState.progress * 100).toInt()} %"
                            customState is ModelState.Error ->
                                "Import fehlgeschlagen"
                            else -> "Eigene Datei importieren"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (customState is ModelState.Downloaded) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = if (isActive) "Aktiv" else "Bereit",
                        tint = if (isActive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (customState is ModelState.Downloading) {
                LinearProgressIndicator(
                    progress = { customState.progress },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${(customState.progress * 100).toInt()} %", style = MaterialTheme.typography.bodySmall)
                    IconButton(
                        onClick = { viewModel.cancelDownload(LocalModelManager.CUSTOM_MODEL_ID) },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Abbrechen", modifier = Modifier.size(18.dp))
                    }
                }
            }

            val errorText = importError ?: (customState as? ModelState.Error)?.message
            if (errorText != null) {
                Text(
                    text = errorText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = { viewModel.clearCustomImportError() }) {
                    Text("Fehler verwerfen")
                }
            }

            if (customState is ModelState.Downloaded) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(onClick = onPickFile, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Ersetzen…")
                    }
                    if (!isActive) {
                        Button(
                            onClick = { viewModel.selectLocalModel(LocalModelManager.CUSTOM_MODEL_ID) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Aktivieren")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { viewModel.deleteModel(LocalModelManager.CUSTOM_MODEL_ID) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Löschen")
                }
            } else if (customState !is ModelState.Downloading) {
                Button(onClick = onPickFile, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Datei wählen…")
                }
                Text(
                    "Nur .litertlm-Dateien. Tipp: große Modelle brauchen mehrere GB freien Speicher.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DeviceCapabilityCard(capability: DeviceCapability) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Device",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))

            CapabilityRow(
                icon = Icons.Filled.PhoneAndroid,
                label = "Android API ${capability.apiLevel}",
                ok = capability.apiLevelOk,
                detail = if (capability.apiLevelOk) "Supported" else "Requires API 31+",
            )
            CapabilityRow(
                icon = Icons.Filled.Memory,
                label = "RAM: ${capability.totalRamMb / 1000} GB",
                ok = capability.totalRamMb >= 6000,
                detail = if (capability.totalRamMb >= 8000) "All models"
                else if (capability.totalRamMb >= 6000) "E2B only"
                else "Insufficient",
            )
            CapabilityRow(
                icon = Icons.Filled.Storage,
                label = "Storage: ${capability.availableStorageMb / 1000} GB free",
                ok = capability.availableStorageMb >= 3600,
                detail = if (capability.availableStorageMb >= 4700) "All models"
                else if (capability.availableStorageMb >= 3600) "E2B only"
                else "Insufficient",
            )
        }
    }
}

@Composable
private fun CapabilityRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    ok: Boolean,
    detail: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall,
            color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun ModelCard(
    modelName: String,
    fileSize: String,
    ramRequired: String,
    state: ModelState,
    isSelected: Boolean = false,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onActivate: () -> Unit = {},
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = modelName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "$fileSize \u00b7 $ramRequired",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Status icon
                when (state) {
                    is ModelState.Downloaded -> Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = "Downloaded",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    is ModelState.Error -> Icon(
                        Icons.Filled.Error,
                        contentDescription = "Error",
                        tint = MaterialTheme.colorScheme.error,
                    )
                    else -> {}
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            when (state) {
                is ModelState.NotAvailable -> {
                    Text(
                        text = state.reason,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                is ModelState.NotDownloaded -> {
                    Button(
                        onClick = onDownload,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Download")
                    }
                }

                is ModelState.Downloading -> {
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${(state.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        IconButton(onClick = onCancel, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.Close, contentDescription = "Cancel", modifier = Modifier.size(18.dp))
                        }
                    }
                }

                is ModelState.Downloaded -> {
                    if (!isSelected) {
                        Button(
                            onClick = onActivate,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Als aktiv setzen")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    } else {
                        Text(
                            "Aktiv · wird für neue Chats verwendet",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    OutlinedButton(
                        onClick = onDelete,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Delete (${formatBytes(state.sizeBytes)})")
                    }
                }

                is ModelState.Error -> {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onDownload,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Retry Download")
                    }
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    val gb = bytes / 1_000_000_000.0
    return if (gb >= 1.0) "%.1f GB".format(gb)
    else "%.0f MB".format(bytes / 1_000_000.0)
}
