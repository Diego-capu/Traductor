package com.antigravity.translator.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.antigravity.translator.domain.model.ReadingProfile
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.antigravity.translator.R
import com.antigravity.translator.data.model.TelemetryData
import com.antigravity.translator.service.ScreenCaptureService
import com.antigravity.translator.ui.theme.ScreenTranslatorTheme
import com.antigravity.translator.ui.theme.SuccessGreen
import com.antigravity.translator.ui.theme.WarningAmber
import java.text.NumberFormat
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    // 1. Overlay Permission Launcher
    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        checkPermissions()
    }

    // 2. Notification Permission Launcher (Android 13+)
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        viewModel.setNotificationPermissionGranted(isGranted)
    }

    // 3. MediaProjection Screen Capture Launcher
    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            startTranslationService(result.resultCode, result.data!!)
        } else {
            Toast.makeText(this, "Permiso de captura de pantalla cancelado", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ScreenTranslatorTheme {
                val uiState by viewModel.uiState.collectAsState()
                val telemetry by viewModel.telemetryState.collectAsState()
                val isRefreshingUsage by viewModel.isRefreshingUsage.collectAsState()

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    TranslatorMainScreen(
                        uiState = uiState,
                        telemetry = telemetry,
                        isRefreshingUsage = isRefreshingUsage,
                        availableLanguages = viewModel.availableLanguages,
                        onApiKeyChanged = viewModel::onApiKeyChanged,
                        onRefreshUsageClicked = viewModel::refreshUsage,
                        onSourceSelected = viewModel::onSourceLanguageSelected,
                        onTargetSelected = viewModel::onTargetLanguageSelected,
                        onReadingProfileSelected = viewModel::onReadingProfileChanged,
                        onModeToggled = viewModel::onModeToggled,
                        onRequestOverlayPermission = { requestOverlayPermission() },
                        onRequestNotificationPermission = { requestNotificationPermission() },
                        onStartServiceClicked = { requestScreenCapture() },
                        onAdjustCropClicked = { adjustCropRegion() },
                        onStopServiceClicked = { stopTranslationService() }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkPermissions()
    }

    private fun checkPermissions() {
        val canDrawOverlays = Settings.canDrawOverlays(this)
        viewModel.setOverlayPermissionGranted(canDrawOverlays)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasNotification = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            viewModel.setNotificationPermissionGranted(hasNotification)
        } else {
            viewModel.setNotificationPermissionGranted(true)
        }
    }

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        overlayPermissionLauncher.launch(intent)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun requestScreenCapture() {
        val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjectionLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
    }

    private fun startTranslationService(resultCode: Int, data: Intent) {
        val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
            putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode)
            putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, data)
        }
        ContextCompat.startForegroundService(this, serviceIntent)
        viewModel.setServiceRunning(true)
        moveTaskToBack(true)
        Toast.makeText(this, "Ajusta el área de recorte que deseas traducir", Toast.LENGTH_SHORT).show()
    }

    private fun adjustCropRegion() {
        val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
            action = ScreenCaptureService.ACTION_SHOW_CROP_SELECTOR
        }
        startService(serviceIntent)
        moveTaskToBack(true)
    }

    private fun stopTranslationService() {
        val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
            action = ScreenCaptureService.ACTION_STOP
        }
        startService(serviceIntent)
        viewModel.setServiceRunning(false)
        Toast.makeText(this, "Servicio detenido", Toast.LENGTH_SHORT).show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslatorMainScreen(
    uiState: MainUiState,
    telemetry: TelemetryData,
    isRefreshingUsage: Boolean,
    availableLanguages: List<Pair<String, String>>,
    onApiKeyChanged: (String) -> Unit,
    onRefreshUsageClicked: () -> Unit,
    onSourceSelected: (String) -> Unit,
    onTargetSelected: (String) -> Unit,
    onReadingProfileSelected: (ReadingProfile) -> Unit = {},
    onModeToggled: (Boolean) -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onStartServiceClicked: () -> Unit,
    onAdjustCropClicked: () -> Unit = {},
    onStopServiceClicked: () -> Unit
) {
    var isApiKeyVisible by remember { mutableStateOf(false) }
    var sourceDropdownExpanded by remember { mutableStateOf(false) }
    var targetDropdownExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // App Header with Miku Icon
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_launcher),
                contentDescription = "Miku_AI Icon",
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = "Miku_AI",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Traductor de Pantalla para Manga, Manhwa y Cómics",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Permissions Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Permisos Requeridos",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Overlay Permission Row
                PermissionRow(
                    title = "Ventana Flotante (Overlay)",
                    description = "Necesario para mostrar los textos traducidos",
                    isGranted = uiState.isOverlayPermissionGranted,
                    onRequest = onRequestOverlayPermission
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Notification Permission Row
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    PermissionRow(
                        title = "Notificaciones",
                        description = "Requerido por Android 14 para servicio en primer plano",
                        isGranted = uiState.isNotificationPermissionGranted,
                        onRequest = onRequestNotificationPermission
                    )
                }
            }
        }

        // Reading Format Selector Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Formato de Lectura",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Badge(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = uiState.readingProfile.title,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Ajusta el OCR, orientación espacial y concatenación léxica según el origen de la obra.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Grid of Profiles (5 options: Manga JA, Manga EN, Manhwa, Manhua, Cómic)
                val profiles = ReadingProfile.entries
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (rowChunk in profiles.chunked(2)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            for (profile in rowChunk) {
                                val isSelected = uiState.readingProfile == profile
                                Card(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { onReadingProfileSelected(profile) },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) {
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                        } else {
                                            MaterialTheme.colorScheme.surface
                                        }
                                    ),
                                    border = BorderStroke(
                                        width = if (isSelected) 1.5.dp else 1.dp,
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                        }
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = profile.title,
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = when (profile) {
                                                ReadingProfile.MANGA_JA -> "Vertical RTL (JA)"
                                                ReadingProfile.MANGA_EN -> "Horizontal LTR (EN)"
                                                ReadingProfile.MANHWA -> "Horizontal LTR (KO)"
                                                ReadingProfile.MANHUA -> "Horizontal CJK (ZH)"
                                                ReadingProfile.COMIC -> "Occidental LTR (EN)"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            if (rowChunk.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        // DeepL Configuration Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Configuración DeepL",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Badge(
                        containerColor = if (uiState.isProAccount) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                    ) {
                        Text(
                            text = if (uiState.isProAccount) "Plan PRO" else "Plan FREE",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontSize = 11.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = uiState.apiKey,
                    onValueChange = onApiKeyChanged,
                    label = { Text("DeepL Authentication Key") },
                    placeholder = { Text("ej. xxxxxxxx-xxxx-...:fx") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (isApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        TextButton(onClick = { isApiKeyVisible = !isApiKeyVisible }) {
                            Text(if (isApiKeyVisible) "Ocultar" else "Ver", fontSize = 12.sp)
                        }
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Source Language Selector
                ExposedDropdownMenuBox(
                    expanded = sourceDropdownExpanded,
                    onExpandedChange = { sourceDropdownExpanded = !sourceDropdownExpanded }
                ) {
                    val currentSourceName = availableLanguages.firstOrNull { it.first == uiState.sourceLanguage }?.second ?: "Detectar automáticamente"
                    OutlinedTextField(
                        readOnly = true,
                        value = currentSourceName,
                        onValueChange = {},
                        label = { Text("Idioma Origen") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sourceDropdownExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = sourceDropdownExpanded,
                        onDismissRequest = { sourceDropdownExpanded = false }
                    ) {
                        availableLanguages.forEach { (code, name) ->
                            DropdownMenuItem(
                                text = { Text(name) },
                                onClick = {
                                    onSourceSelected(code)
                                    sourceDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Target Language Selector
                ExposedDropdownMenuBox(
                    expanded = targetDropdownExpanded,
                    onExpandedChange = { targetDropdownExpanded = !targetDropdownExpanded }
                ) {
                    val currentTargetName = availableLanguages.filter { it.first.isNotEmpty() }
                        .firstOrNull { it.first == uiState.targetLanguage }?.second ?: "Español"
                    OutlinedTextField(
                        readOnly = true,
                        value = currentTargetName,
                        onValueChange = {},
                        label = { Text("Idioma Destino") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = targetDropdownExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = targetDropdownExpanded,
                        onDismissRequest = { targetDropdownExpanded = false }
                    ) {
                        availableLanguages.filter { it.first.isNotEmpty() }.forEach { (code, name) ->
                            DropdownMenuItem(
                                text = { Text(name) },
                                onClick = {
                                    onTargetSelected(code)
                                    targetDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }

        // Consumo & Telemetría API Card (Miku Cyberpunk Palette #39C5BB & #E040FB)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Consumo & Telemetría API",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    // Remaining percentage chip (safely validated > 0L)
                    val percentRemaining = if (telemetry.serverCharacterLimit > 0L) {
                        ((telemetry.serverCharacterLimit - telemetry.serverUsedCharacters).coerceAtLeast(0L) * 100L / telemetry.serverCharacterLimit).toInt().coerceIn(0, 100)
                    } else {
                        0
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFE040FB).copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE040FB))
                    ) {
                        Text(
                            text = if (telemetry.isOfflineQuota && telemetry.serverCharacterLimit > 0L) {
                                "$percentRemaining% libre (Offline)"
                            } else {
                                "$percentRemaining% libre"
                            },
                            color = Color(0xFFE040FB),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Number formatting using user Locale
                val numberFormat = remember { NumberFormat.getNumberInstance(Locale.getDefault()) }
                val usedStr = numberFormat.format(telemetry.serverUsedCharacters)
                val limitStr = numberFormat.format(telemetry.serverCharacterLimit)
                val sessionSentStr = numberFormat.format(telemetry.sessionCharactersSent)
                val sessionSavedStr = numberFormat.format(telemetry.sessionCharactersSavedByCache)

                // Usage Progress Bar with safe division (serverCharacterLimit > 0L)
                val progress = if (telemetry.serverCharacterLimit > 0L) {
                    (telemetry.serverUsedCharacters.toFloat() / telemetry.serverCharacterLimit.toFloat()).coerceIn(0f, 1f)
                } else {
                    0f
                }

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp)),
                    color = Color(0xFF39C5BB),
                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Consumo en servidor:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "$usedStr / $limitStr caracteres",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF39C5BB)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Status chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Session sent chip
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF39C5BB).copy(alpha = 0.12f)
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Enviados sesión",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = sessionSentStr,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF39C5BB),
                                fontSize = 13.sp
                            )
                        }
                    }

                    // Cache saved chip
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF10B981).copy(alpha = 0.12f)
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Ahorro caché",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = sessionSavedStr,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF10B981),
                                fontSize = 13.sp
                            )
                        }
                    }

                    // Requests total / failed chip
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Peticiones",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${telemetry.totalRequests} (${telemetry.failedRequests} err)",
                                fontWeight = FontWeight.Bold,
                                color = if (telemetry.failedRequests > 0) Color(0xFFE11D48) else MaterialTheme.colorScheme.onSurface,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Actualizar Cuota button with loading indicator
                OutlinedButton(
                    onClick = onRefreshUsageClicked,
                    enabled = !isRefreshingUsage && uiState.apiKey.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF39C5BB)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF39C5BB))
                ) {
                    if (isRefreshingUsage) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Color(0xFF39C5BB)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Consultando /v2/usage...", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    } else {
                        Text("Actualizar Cuota DeepL", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // Mode Card: Manual vs Auto
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Modo de Traducción",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (uiState.isManualMode) "Bajo Demanda (Recomendado)" else "Automático (Tiempo Real)",
                            fontWeight = FontWeight.Medium,
                            fontSize = 15.sp,
                            color = if (uiState.isManualMode) SuccessGreen else WarningAmber
                        )
                        Text(
                            text = if (uiState.isManualMode)
                                "Traduce solo cuando presionas 'TRADUCIR' en la burbuja. Ahorra caracteres y no interfiere con los clicks."
                            else
                                "Traduce continuamente en tiempo real cada segundo al detectar cambios en la pantalla.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                    Switch(
                        checked = !uiState.isManualMode,
                        onCheckedChange = { isChecked ->
                            onModeToggled(!isChecked)
                        }
                    )
                }
            }
        }

        // Action Control Buttons
        val canStart = uiState.isOverlayPermissionGranted &&
                uiState.apiKey.trim().isNotEmpty()

        Button(
            onClick = onStartServiceClicked,
            enabled = canStart && !uiState.isServiceRunning,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = if (uiState.isServiceRunning) "Traductor en Ejecución" else "Iniciar Traducción en Pantalla",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        if (uiState.isServiceRunning) {
            OutlinedButton(
                onClick = onAdjustCropClicked,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF39C5BB)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF39C5BB))
            ) {
                Text("Reajustar Área de Recorte", fontWeight = FontWeight.SemiBold)
            }

            OutlinedButton(
                onClick = onStopServiceClicked,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Detener Traductor", fontWeight = FontWeight.SemiBold)
            }
        }

        if (uiState.apiKey.trim().isEmpty()) {
            Text(
                text = "* Ingresa tu API Key de DeepL para comenzar a traducir.",
                color = WarningAmber,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
fun PermissionRow(
    title: String,
    description: String,
    isGranted: Boolean,
    onRequest: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = if (isGranted) SuccessGreen else WarningAmber,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(text = title, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                Text(text = description, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        }

        if (!isGranted) {
            TextButton(onClick = onRequest) {
                Text("Conceder")
            }
        }
    }
}
