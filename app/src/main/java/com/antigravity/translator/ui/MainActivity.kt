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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.antigravity.translator.service.ScreenCaptureService
import com.antigravity.translator.ui.theme.ScreenTranslatorTheme
import com.antigravity.translator.ui.theme.SuccessGreen
import com.antigravity.translator.ui.theme.WarningAmber

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

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    TranslatorMainScreen(
                        uiState = uiState,
                        availableLanguages = viewModel.availableLanguages,
                        onApiKeyChanged = viewModel::onApiKeyChanged,
                        onSourceSelected = viewModel::onSourceLanguageSelected,
                        onTargetSelected = viewModel::onTargetLanguageSelected,
                        onModeToggled = viewModel::onModeToggled,
                        onRequestOverlayPermission = { requestOverlayPermission() },
                        onRequestNotificationPermission = { requestNotificationPermission() },
                        onStartServiceClicked = { requestScreenCapture() },
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
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjectionLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    private fun startTranslationService(resultCode: Int, data: Intent) {
        val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
            putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode)
            putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, data)
        }
        ContextCompat.startForegroundService(this, serviceIntent)
        viewModel.setServiceRunning(true)
        Toast.makeText(this, "Miku_AI iniciado. Usa el botón flotante en pantalla.", Toast.LENGTH_LONG).show()
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
    availableLanguages: List<Pair<String, String>>,
    onApiKeyChanged: (String) -> Unit,
    onSourceSelected: (String) -> Unit,
    onTargetSelected: (String) -> Unit,
    onModeToggled: (Boolean) -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onStartServiceClicked: () -> Unit,
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
