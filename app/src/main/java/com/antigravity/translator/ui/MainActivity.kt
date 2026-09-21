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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.FilterCenterFocus
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.antigravity.translator.R
import com.antigravity.translator.service.ScreenCaptureService
import com.antigravity.translator.ui.theme.*

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
            .background(MikuDarkBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Cyberpunk Header Row with Miku Avatar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .border(2.dp, MikuPrimary, CircleShape)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_launcher),
                    contentDescription = "Miku_AI Icon",
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(MikuPrimary)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "HUD ACTIVE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MikuPrimary,
                        letterSpacing = 1.sp
                    )
                }
                Text(
                    text = "Miku_AI",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MikuTextPrimary
                )
                Text(
                    text = "Screen Translator • Hatsune Miku Edition",
                    fontSize = 11.5.sp,
                    color = MikuTextSecondary
                )
            }
        }

        // 2. Minimalist Status Sub-Bar (Miku Core Banner)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MikuSurfaceContainer,
            border = BorderStroke(1.dp, MikuBorderSubtle)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (uiState.isServiceRunning) MikuPrimary else MikuTextSecondary)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "MIKU CORE v2.4",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MikuPrimary,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = if (uiState.isServiceRunning) "Servicio Activo • Captura en Vivo" else "Servicio Standby • Esperando Inicio",
                            fontSize = 10.sp,
                            color = MikuTextSecondary
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MikuSurfaceHighest
                ) {
                    Text(
                        text = "ON-DEVICE",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MikuPrimary
                    )
                }
            }
        }

        // 3. Hero Floating Service Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MikuSurface,
            border = BorderStroke(1.dp, MikuBorderSubtle)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.size(40.dp),
                            shape = CircleShape,
                            color = MikuSurfaceHighest
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Layers,
                                    contentDescription = null,
                                    tint = MikuPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "OVERLAY ENGINE",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MikuTextSecondary,
                                letterSpacing = 0.8.sp
                            )
                            Text(
                                text = "Servicio Flotante",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MikuTextPrimary
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (uiState.isServiceRunning) MikuPrimary.copy(alpha = 0.15f) else MikuSurfaceHighest
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(if (uiState.isServiceRunning) MikuPrimary else MikuTextSecondary)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (uiState.isServiceRunning) "ACTIVO" else "LISTO",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (uiState.isServiceRunning) MikuPrimary else MikuTextSecondary
                            )
                        }
                    }
                }

                // Main Service CTA Button (Pill shape)
                val canStart = uiState.isOverlayPermissionGranted && uiState.apiKey.trim().isNotEmpty()

                if (!uiState.isServiceRunning) {
                    Button(
                        onClick = onStartServiceClicked,
                        enabled = canStart,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MikuPrimary,
                            contentColor = MikuOnPrimary,
                            disabledContainerColor = MikuSurfaceHighest,
                            disabledContentColor = MikuTextDisabled
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Iniciar Servicio Flotante",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Button(
                        onClick = onStopServiceClicked,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MikuError.copy(alpha = 0.15f),
                            contentColor = MikuError
                        ),
                        border = BorderStroke(1.5.dp, MikuError)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Detener Servicio Flotante",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // System Readiness Matrix
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Superposición (Overlay)
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable(enabled = !uiState.isOverlayPermissionGranted) { onRequestOverlayPermission() },
                        shape = RoundedCornerShape(10.dp),
                        color = MikuSurfaceContainer,
                        border = BorderStroke(
                            1.dp,
                            if (uiState.isOverlayPermissionGranted) MikuBorderSubtle else MikuWarning.copy(alpha = 0.5f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (uiState.isOverlayPermissionGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = if (uiState.isOverlayPermissionGranted) MikuPrimary else MikuWarning,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Superposición",
                                    fontSize = 11.5.sp,
                                    color = MikuTextPrimary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Text(
                                text = if (uiState.isOverlayPermissionGranted) "OK" else "DAR",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (uiState.isOverlayPermissionGranted) MikuPrimary else MikuWarning
                            )
                        }
                    }

                    // MediaProject / Screen Capture
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        color = MikuSurfaceContainer,
                        border = BorderStroke(1.dp, MikuBorderSubtle)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MikuPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "MediaProject",
                                    fontSize = 11.5.sp,
                                    color = MikuTextPrimary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Text(
                                text = "LISTO",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MikuPrimary
                            )
                        }
                    }
                }

                // Notification Permission for Android 13+
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !uiState.isNotificationPermissionGranted) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onRequestNotificationPermission() },
                        shape = RoundedCornerShape(10.dp),
                        color = MikuSurfaceContainer,
                        border = BorderStroke(1.dp, MikuWarning.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MikuWarning,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Permiso de Notificaciones (Android 13+)",
                                    fontSize = 11.5.sp,
                                    color = MikuTextPrimary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Text(
                                text = "CONCEDER",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MikuWarning
                            )
                        }
                    }
                }
            }
        }

        // 4. Translation Route & Language Pair Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MikuSurface,
            border = BorderStroke(1.dp, MikuBorderSubtle)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "RUTA DE TRADUCCIÓN",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MikuTextSecondary,
                        letterSpacing = 0.8.sp
                    )
                    Text(
                        text = "⚡ Neural v3",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MikuAccentMagenta
                    )
                }

                // Dual Language Selection with Interactive Swap Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Source Dropdown Box
                    Box(modifier = Modifier.weight(1f)) {
                        ExposedDropdownMenuBox(
                            expanded = sourceDropdownExpanded,
                            onExpandedChange = { sourceDropdownExpanded = !sourceDropdownExpanded }
                        ) {
                            val currentSourceName = availableLanguages.firstOrNull { it.first == uiState.sourceLanguage }?.second ?: "Detectar Auto"
                            Surface(
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = MikuSurfaceContainer,
                                border = BorderStroke(1.dp, MikuBorderGlass)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("ORIGEN", fontSize = 10.sp, color = MikuTextSecondary, fontWeight = FontWeight.SemiBold)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = currentSourceName,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MikuPrimary,
                                        maxLines = 1
                                    )
                                }
                            }
                            ExposedDropdownMenu(
                                expanded = sourceDropdownExpanded,
                                onDismissRequest = { sourceDropdownExpanded = false }
                            ) {
                                availableLanguages.forEach { (code, name) ->
                                    DropdownMenuItem(
                                        text = { Text(name, color = MikuTextPrimary) },
                                        onClick = {
                                            onSourceSelected(code)
                                            sourceDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Circular Swap Button in Miku Style
                    Surface(
                        modifier = Modifier.size(38.dp),
                        shape = CircleShape,
                        color = MikuSurfaceHighest,
                        border = BorderStroke(1.dp, MikuBorderSubtle)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.SwapHoriz,
                                contentDescription = "Intercambiar",
                                tint = MikuPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Target Dropdown Box
                    Box(modifier = Modifier.weight(1f)) {
                        ExposedDropdownMenuBox(
                            expanded = targetDropdownExpanded,
                            onExpandedChange = { targetDropdownExpanded = !targetDropdownExpanded }
                        ) {
                            val currentTargetName = availableLanguages.filter { it.first.isNotEmpty() }
                                .firstOrNull { it.first == uiState.targetLanguage }?.second ?: "Español"
                            Surface(
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = MikuSurfaceContainer,
                                border = BorderStroke(1.dp, MikuBorderGlass)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("DESTINO", fontSize = 10.sp, color = MikuTextSecondary, fontWeight = FontWeight.SemiBold)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = currentTargetName,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MikuTextPrimary,
                                        maxLines = 1
                                    )
                                }
                            }
                            ExposedDropdownMenu(
                                expanded = targetDropdownExpanded,
                                onDismissRequest = { targetDropdownExpanded = false }
                            ) {
                                availableLanguages.filter { it.first.isNotEmpty() }.forEach { (code, name) ->
                                    DropdownMenuItem(
                                        text = { Text(name, color = MikuTextPrimary) },
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
            }
        }

        // 5. Operational Mode & Capture Mode Selector Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MikuSurface,
            border = BorderStroke(1.dp, MikuBorderSubtle)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "MODO DE TRADUCCIÓN",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MikuTextSecondary,
                        letterSpacing = 0.8.sp
                    )
                    Text(
                        text = if (uiState.isManualMode) "MANUAL" else "AUTO",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (uiState.isManualMode) MikuPrimary else MikuAccentMagenta
                    )
                }

                // 3 Mode Preview Cards (Completa / Recorte / Lupa)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Completa
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        color = MikuSurfaceContainer,
                        border = BorderStroke(1.dp, MikuBorderGlass)
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.Fullscreen, contentDescription = null, tint = MikuTextSecondary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Completa", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MikuTextPrimary)
                            Text("Scan global", fontSize = 9.sp, color = MikuTextSecondary)
                        }
                    }

                    // Recorte (Snip Tool - Highlighted)
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        color = MikuSurfaceContainer,
                        border = BorderStroke(1.5.dp, MikuPrimary)
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.Crop, contentDescription = null, tint = MikuPrimary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Recorte", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MikuPrimary)
                            Text("Snip tool", fontSize = 9.sp, color = MikuTextSecondary)
                        }
                    }

                    // Lupa (Magnifier)
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        color = MikuSurfaceContainer,
                        border = BorderStroke(1.dp, MikuBorderGlass)
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.FilterCenterFocus, contentDescription = null, tint = MikuAccentMagenta, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Lupa", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MikuTextPrimary)
                            Text("Hover reticle", fontSize = 9.sp, color = MikuTextSecondary)
                        }
                    }
                }

                // Switch Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (uiState.isManualMode) "Bajo Demanda (Recomendado)" else "Automático (Tiempo Real)",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.5.sp,
                            color = if (uiState.isManualMode) MikuPrimary else MikuAccentMagenta
                        )
                        Text(
                            text = if (uiState.isManualMode)
                                "Traduce con un toque en la burbuja flotante. Ahorra caracteres DeepL y no interfiere."
                            else
                                "Traduce continuamente en tiempo real cada segundo al detectar cambios.",
                            fontSize = 11.sp,
                            color = MikuTextSecondary
                        )
                    }
                    Switch(
                        checked = !uiState.isManualMode,
                        onCheckedChange = { isChecked ->
                            onModeToggled(!isChecked)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MikuOnPrimary,
                            checkedTrackColor = MikuPrimary,
                            uncheckedThumbColor = MikuTextSecondary,
                            uncheckedTrackColor = MikuSurfaceContainer
                        )
                    )
                }
            }
        }

        // 6. DeepL Configuration Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MikuSurface,
            border = BorderStroke(1.dp, MikuBorderSubtle)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "DeepL Pro API",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MikuTextPrimary
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (uiState.isProAccount) MikuPrimary.copy(alpha = 0.2f) else MikuAccentMagenta.copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, if (uiState.isProAccount) MikuPrimary else MikuAccentMagenta)
                    ) {
                        Text(
                            text = if (uiState.isProAccount) "Plan PRO" else "Plan FREE",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (uiState.isProAccount) MikuPrimary else MikuAccentMagenta
                        )
                    }
                }

                OutlinedTextField(
                    value = uiState.apiKey,
                    onValueChange = onApiKeyChanged,
                    label = { Text("DeepL Authentication Key") },
                    placeholder = { Text("ej. xxxxxxxx-xxxx-...:fx", color = MikuTextDisabled) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = MikuTextPrimary
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MikuPrimary,
                        unfocusedBorderColor = MikuBorderGlass,
                        focusedLabelColor = MikuPrimary,
                        unfocusedLabelColor = MikuTextSecondary,
                        cursorColor = MikuPrimary,
                        focusedContainerColor = MikuSurfaceContainer,
                        unfocusedContainerColor = MikuSurfaceContainer
                    ),
                    visualTransformation = if (isApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        TextButton(onClick = { isApiKeyVisible = !isApiKeyVisible }) {
                            Text(
                                text = if (isApiKeyVisible) "Ocultar" else "Ver",
                                fontSize = 11.sp,
                                color = MikuPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                )

                if (uiState.apiKey.trim().isEmpty()) {
                    Text(
                        text = "* Ingresa tu API Key de DeepL para comenzar a traducir.",
                        color = MikuWarning,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

