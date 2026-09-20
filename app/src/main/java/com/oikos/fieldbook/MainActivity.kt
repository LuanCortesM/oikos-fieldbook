package com.oikos.fieldbook

import android.Manifest
import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.BitmapDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.oikos.fieldbook.data.FieldConfigEntity
import com.oikos.fieldbook.data.GpsTrackEntity
import com.oikos.fieldbook.data.GpsTrackPointEntity
import com.oikos.fieldbook.data.MediaEntity
import com.oikos.fieldbook.data.OccurrenceEntity
import com.oikos.fieldbook.data.ProjectEntity
import com.oikos.fieldbook.data.SessionEntity
import com.oikos.fieldbook.dwc.DarwinCoreCatalog
import com.oikos.fieldbook.export.ExportContent
import com.oikos.fieldbook.export.ExportFormat
import com.oikos.fieldbook.services.CapturedLocation
import com.oikos.fieldbook.services.PureMp3AudioRecorder
import com.oikos.fieldbook.services.WavAudioRecorder
import com.oikos.fieldbook.ui.theme.OikosTheme
import com.oikos.fieldbook.util.CoordinateUtils
import com.oikos.fieldbook.util.UtmHemisphere
import com.oikos.fieldbook.util.flatJson
import com.oikos.fieldbook.util.parseFlatJsonMap
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import java.util.Calendar
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        applySystemBars(darkTheme = true)
        val languagePrefs = getSharedPreferences("oikos_app_settings", Context.MODE_PRIVATE)
        val deviceLanguage = currentDeviceUiLanguage()
        Configuration.getInstance().userAgentValue = packageName
        setContent {
            var darkTheme by remember { mutableStateOf(true) }
            var simpleMode by remember { mutableStateOf(true) }
            var languageMode by remember { mutableStateOf(languagePrefs.getString("app_language", "auto") ?: "auto") }
            var showLaunch by remember { mutableStateOf(true) }
            val uiLanguage = when (languageMode) {
                "pt" -> OikosLanguage.Portuguese
                "en" -> OikosLanguage.English
                else -> deviceLanguage
            }
            LaunchedEffect(Unit) {
                delay(1_150)
                showLaunch = false
            }
            LaunchedEffect(darkTheme) {
                applySystemBars(darkTheme)
            }
            CompositionLocalProvider(LocalOikosLanguage provides uiLanguage) {
                OikosTheme(darkTheme = darkTheme) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        if (showLaunch) {
                            OikosLaunchScreen()
                        } else {
                            OikosApp(
                                viewModel,
                                darkTheme,
                                simpleMode,
                                languageMode,
                                onThemeChange = { darkTheme = it },
                                onSimpleModeChange = { simpleMode = it },
                                onLanguageModeChange = {
                                    languageMode = it
                                    languagePrefs.edit().putString("app_language", it).apply()
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun currentDeviceUiLanguage(): OikosLanguage {
        val locale = resources.configuration.locales[0]
        return if (locale.language.equals("pt", ignoreCase = true)) OikosLanguage.Portuguese else OikosLanguage.English
    }

    private fun applySystemBars(darkTheme: Boolean) {
        val transparent = android.graphics.Color.TRANSPARENT
        val statusBarStyle = if (darkTheme) {
            SystemBarStyle.dark(transparent)
        } else {
            SystemBarStyle.light(transparent, transparent)
        }
        val navigationBarStyle = if (darkTheme) {
            SystemBarStyle.dark(transparent)
        } else {
            SystemBarStyle.light(transparent, transparent)
        }
        enableEdgeToEdge(
            statusBarStyle = statusBarStyle,
            navigationBarStyle = navigationBarStyle
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
    }
}

private enum class OikosLanguage { Portuguese, English }

private val LocalOikosLanguage = staticCompositionLocalOf { OikosLanguage.Portuguese }

@Composable
private fun OikosLaunchScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0B0D)),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.oikos_brand_logo),
            contentDescription = "Oikos FieldBook logo",
            modifier = Modifier
                .fillMaxWidth(0.84f)
                .height(210.dp),
            contentScale = ContentScale.Fit
        )
    }
}

private sealed interface Screen {
    data object Projects : Screen
    data class ProjectEdit(val projectId: Long? = null) : Screen
    data class ProjectDetail(val projectId: Long) : Screen
    data class FieldConfig(val projectId: Long) : Screen
    data class SessionEdit(val projectId: Long, val sessionId: Long? = null) : Screen
    data class SessionDetail(val projectId: Long, val sessionId: Long) : Screen
    data class OccurrenceEdit(val projectId: Long, val sessionId: Long, val occurrenceId: Long? = null) : Screen
    data class Media(val projectId: Long, val sessionId: Long, val occurrenceId: Long? = null) : Screen
    data class Export(val projectId: Long, val sessionId: Long? = null) : Screen
    data object Settings : Screen
    data object About : Screen
}

@Composable
private fun OikosApp(
    viewModel: AppViewModel,
    darkTheme: Boolean,
    simpleMode: Boolean,
    languageMode: String,
    onThemeChange: (Boolean) -> Unit,
    onSimpleModeChange: (Boolean) -> Unit,
    onLanguageModeChange: (String) -> Unit
) {
    var screen by remember { mutableStateOf<Screen>(Screen.Projects) }
    val snackbarHostState = remember { SnackbarHostState() }
    val message by viewModel.message.collectAsState()
    val context = LocalContext.current
    val shareChooserTitle = ui("Compartilhar, salvar ou enviar exportação")
    var pendingLocation by remember { mutableStateOf<((CapturedLocation?) -> Unit)?>(null) }
    val scope = rememberCoroutineScope()
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions.values.any { it }) {
            viewModel.captureLocation { pendingLocation?.invoke(it) }
        } else {
            pendingLocation?.invoke(null)
        }
    }
    val requestLocation: (((CapturedLocation?) -> Unit) -> Unit) = { callback ->
        pendingLocation = callback
        val permissions = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        }.toTypedArray()
        val hasLocationPermission = hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ||
            hasPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (hasLocationPermission) {
            viewModel.captureLocation { callback(it) }
        } else {
            permissionLauncher.launch(permissions)
        }
    }

    BackHandler(enabled = true) {
        if (screen is Screen.Projects) {
            (context as? ComponentActivity)?.moveTaskToBack(true)
        } else {
            screen = screen.backDestination()
        }
    }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        when (val current = screen) {
            Screen.Projects -> ProjectListScreen(
                viewModel = viewModel,
                modifier = Modifier.padding(padding),
                onCreate = { screen = Screen.ProjectEdit() },
                onOpen = { screen = Screen.ProjectDetail(it) },
                onDeleteProject = { project -> viewModel.deleteProject(project) { } },
                onStorage = { screen = Screen.Settings },
                onAbout = { screen = Screen.About }
            )
            is Screen.ProjectEdit -> ProjectEditScreen(viewModel, current.projectId, Modifier.padding(padding), { screen = Screen.Projects }) {
                screen = Screen.ProjectDetail(it)
            }
            is Screen.ProjectDetail -> ProjectDetailScreen(
                viewModel,
                current.projectId,
                Modifier.padding(padding),
                onBack = { screen = Screen.Projects },
                onEdit = { screen = Screen.ProjectEdit(current.projectId) },
                onFields = { screen = Screen.FieldConfig(current.projectId) },
                onNewSession = { screen = Screen.SessionEdit(current.projectId) },
                onOpenSession = { screen = Screen.SessionDetail(current.projectId, it) },
                onExport = { screen = Screen.Export(current.projectId) },
                onDeleted = { screen = Screen.Projects }
            )
            is Screen.FieldConfig -> FieldConfigScreen(viewModel, current.projectId, Modifier.padding(padding)) {
                screen = Screen.ProjectDetail(current.projectId)
            }
            is Screen.SessionEdit -> SessionEditScreen(viewModel, current.projectId, current.sessionId, Modifier.padding(padding), requestLocation, { screen = Screen.ProjectDetail(current.projectId) }) {
                screen = Screen.SessionDetail(current.projectId, it)
            }
            is Screen.SessionDetail -> SessionDetailScreen(
                viewModel,
                current.projectId,
                current.sessionId,
                Modifier.padding(padding),
                onBack = { screen = Screen.ProjectDetail(current.projectId) },
                onEdit = { screen = Screen.SessionEdit(current.projectId, current.sessionId) },
                onNewOccurrence = { screen = Screen.OccurrenceEdit(current.projectId, current.sessionId) },
                onOpenOccurrence = { screen = Screen.OccurrenceEdit(current.projectId, current.sessionId, it) },
                onMedia = { screen = Screen.Media(current.projectId, current.sessionId, null) },
                onExport = { screen = Screen.Export(current.projectId, current.sessionId) },
                onDeleted = { screen = Screen.ProjectDetail(current.projectId) },
                requestLocation = requestLocation
            )
            is Screen.OccurrenceEdit -> OccurrenceEditScreen(viewModel, current.projectId, current.sessionId, current.occurrenceId, simpleMode, Modifier.padding(padding), requestLocation, { screen = Screen.SessionDetail(current.projectId, current.sessionId) }, onDeleted = { screen = Screen.SessionDetail(current.projectId, current.sessionId) }) {
                screen = Screen.Media(current.projectId, current.sessionId, it)
            }
            is Screen.Media -> MediaScreen(viewModel, current.projectId, current.sessionId, current.occurrenceId, Modifier.padding(padding)) {
                screen = if (current.occurrenceId == null) Screen.SessionDetail(current.projectId, current.sessionId) else Screen.OccurrenceEdit(current.projectId, current.sessionId, current.occurrenceId)
            }
            is Screen.Export -> ExportScreen(viewModel, current.projectId, current.sessionId, Modifier.padding(padding), onBack = {
                screen = current.sessionId?.let { Screen.SessionDetail(current.projectId, it) } ?: Screen.ProjectDetail(current.projectId)
            }) { intent, warning ->
                warning?.let { scope.launch { snackbarHostState.showSnackbar(it) } }
                context.startActivity(Intent.createChooser(intent, shareChooserTitle))
            }
            Screen.Settings -> SettingsScreen(viewModel, darkTheme, simpleMode, languageMode, onThemeChange, onSimpleModeChange, onLanguageModeChange, Modifier.padding(padding)) { screen = Screen.Projects }
            Screen.About -> AboutScreen(Modifier.padding(padding)) { screen = Screen.Projects }
        }
    }
}

private fun Screen.backDestination(): Screen = when (this) {
    Screen.Projects -> Screen.Projects
    is Screen.ProjectEdit -> projectId?.let { Screen.ProjectDetail(it) } ?: Screen.Projects
    is Screen.ProjectDetail -> Screen.Projects
    is Screen.FieldConfig -> Screen.ProjectDetail(projectId)
    is Screen.SessionEdit -> sessionId?.let { Screen.SessionDetail(projectId, it) } ?: Screen.ProjectDetail(projectId)
    is Screen.SessionDetail -> Screen.ProjectDetail(projectId)
    is Screen.OccurrenceEdit -> Screen.SessionDetail(projectId, sessionId)
    is Screen.Media -> if (occurrenceId == null) Screen.SessionDetail(projectId, sessionId) else Screen.OccurrenceEdit(projectId, sessionId, occurrenceId)
    is Screen.Export -> sessionId?.let { Screen.SessionDetail(projectId, it) } ?: Screen.ProjectDetail(projectId)
    Screen.Settings -> Screen.Projects
    Screen.About -> Screen.Projects
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OikosTopBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable () -> Unit = {}) {
    CenterAlignedTopAppBar(
        title = { Text(ui(title)) },
        navigationIcon = {
            if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, ui("Voltar")) }
        },
        actions = { actions() }
    )
}

@Composable
private fun ProjectListScreen(
    viewModel: AppViewModel,
    modifier: Modifier,
    onCreate: () -> Unit,
    onOpen: (Long) -> Unit,
    onDeleteProject: (ProjectEntity) -> Unit,
    onStorage: () -> Unit,
    onAbout: () -> Unit
) {
    val projects by viewModel.projects.collectAsState(initial = emptyList())
    var projectToDelete by remember { mutableStateOf<ProjectEntity?>(null) }
    projectToDelete?.let { project ->
        ConfirmDeleteDialog(
            title = "Apagar projeto?",
            text = "Isso remove o projeto, suas expedições, ocorrências, trilhas e mídias salvas neste aparelho.",
            confirmText = "Apagar projeto",
            onDismiss = { projectToDelete = null },
            onConfirm = {
                projectToDelete = null
                onDeleteProject(project)
            }
        )
    }
    Scaffold(
        modifier = modifier,
        topBar = {
            OikosTopBar("Oikos FieldBook", actions = {
                IconButton(onClick = onStorage) { Icon(Icons.Default.Settings, ui("Configurações")) }
                TextButton(onClick = onAbout) { Text(ui("Sobre")) }
            })
        },
        floatingActionButton = { FloatingActionButton(onClick = onCreate) { Icon(Icons.Default.Add, ui("Novo projeto")) } }
    ) { padding ->
        LazyColumn(contentPadding = padding.plus(PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 96.dp)), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                AdaptiveContent {
                    Column(
                        Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val brandLogo = if (MaterialTheme.colorScheme.background.luminance() > 0.5f) {
                            R.drawable.oikos_brand_logo_light
                        } else {
                            R.drawable.oikos_brand_logo
                        }
                        Image(
                            painter = painterResource(id = brandLogo),
                            contentDescription = "Oikos FieldBook logo",
                            modifier = Modifier
                                .fillMaxWidth(0.86f)
                                .height(170.dp)
                                .align(Alignment.CenterHorizontally),
                            contentScale = ContentScale.Fit
                        )
                        Column(
                            Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                ui("Caderno de Campo Digital"),
                                style = MaterialTheme.typography.headlineSmall,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                ui("Registre expedições, ocorrências, GPS, fotos e áudios com exportação Darwin Core."),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.large)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), MaterialTheme.shapes.large)
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(onClick = onCreate, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                                Icon(Icons.Default.Add, null)
                                Spacer(Modifier.width(8.dp))
                                Text(ui("Criar projeto"))
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                StatusBadge("Offline", true)
                                StatusBadge("Darwin Core", true)
                                StatusBadge("GPS", true)
                            }
                        }
                    }
                }
            }
            item { Text(ui("Abrir projeto"), style = MaterialTheme.typography.titleLarge) }
            if (projects.isEmpty()) {
                item {
                    EmptyState("Nenhum projeto ainda.", "Crie um projeto de pesquisa para começar a coletar offline.")
                }
            }
            items(projects) { project ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpen(project.id) }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(project.name, style = MaterialTheme.typography.titleMedium)
                            if (project.description.isNotBlank()) Text(project.description, style = MaterialTheme.typography.bodyMedium)
                            Text("${project.defaultDatum} - ${project.coordinateFormat}", style = MaterialTheme.typography.labelMedium)
                        }
                        IconButton(onClick = { projectToDelete = project }) {
                            Icon(Icons.Default.Delete, ui("Apagar projeto"))
                        }
                    }
                }
            }
            item { DeveloperSignature(Modifier.fillMaxWidth().padding(top = 16.dp)) }
        }
    }
}

@Composable
private fun ProjectEditScreen(viewModel: AppViewModel, projectId: Long?, modifier: Modifier, onBack: () -> Unit, onSaved: (Long) -> Unit) {
    val defaultProjectLanguage = if (LocalOikosLanguage.current == OikosLanguage.Portuguese) "pt" else "en"
    val existing by (projectId?.let { viewModel.observeProject(it) } ?: kotlinx.coroutines.flow.flowOf(null)).collectAsState(initial = null)
    val savedFields by (projectId?.let { viewModel.observeFields(it) } ?: kotlinx.coroutines.flow.flowOf(emptyList())).collectAsState(initial = emptyList())
    val draftFields = remember {
        mutableStateListOf<FieldConfigEntity>().also { list ->
            list.addAll(
                DarwinCoreCatalog.allFields.map {
                    FieldConfigEntity(
                        projectId = 0,
                        dwcTerm = it.term,
                        labelPt = it.labelPt,
                        labelEn = it.labelEn,
                        enabled = it.enabled,
                        required = it.required,
                        fieldType = it.type,
                        groupName = it.group,
                        displayOrder = it.order
                    )
                }
            )
        }
    }
    val fields = if (projectId == null) draftFields else savedFields
    var name by remember(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }
    var description by remember(existing?.id) { mutableStateOf(existing?.description.orEmpty()) }
    var institution by remember(existing?.id) { mutableStateOf(existing?.institution.orEmpty()) }
    var pi by remember(existing?.id) { mutableStateOf(existing?.principalInvestigator.orEmpty()) }
    var datum by remember(existing?.id) { mutableStateOf(existing?.defaultDatum ?: "SIRGAS 2000") }
    var coordFormat by remember(existing?.id) { mutableStateOf(existing?.coordinateFormat ?: "DD") }
    var photoQuality by remember(existing?.id) { mutableStateOf(existing?.photoQuality ?: "medium") }
    var audioFormat by remember(existing?.id) { mutableStateOf(existing?.audioFormat ?: "wav") }
    var enableFauna by remember(existing?.id) { mutableStateOf(existing?.enableFauna ?: true) }
    var enableBotany by remember(existing?.id) { mutableStateOf(existing?.enableBotany ?: false) }
    var enableFungi by remember(existing?.id) { mutableStateOf(existing?.enableFungi ?: false) }
    var openFieldGroup by remember(existing?.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(projectId) {
        projectId?.let { viewModel.ensureFieldCatalog(it) }
    }

    fun updateProjectField(field: FieldConfigEntity) {
        if (projectId == null) {
            val index = draftFields.indexOfFirst { it.dwcTerm == field.dwcTerm }
            if (index >= 0) draftFields[index] = field
        } else {
            viewModel.updateField(field)
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = { OikosTopBar(if (projectId == null) "Novo projeto" else "Editar projeto", onBack) },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                if (name.isNotBlank()) {
                    viewModel.saveProject(
                        ProjectEntity(
                            id = existing?.id ?: 0L,
                            name = name.trim(),
                            description = description,
                            institution = institution,
                            principalInvestigator = pi,
                            language = existing?.language ?: defaultProjectLanguage,
                            defaultDatum = datum,
                            coordinateFormat = coordFormat,
                            photoQuality = photoQuality,
                            audioFormat = audioFormat,
                            enableFauna = enableFauna,
                            enableBotany = enableBotany,
                            enableFungi = enableFungi,
                            createdAt = existing?.createdAt ?: System.currentTimeMillis()
                        ),
                    ) { savedId ->
                        if (projectId == null) {
                            viewModel.saveFieldConfiguration(savedId, draftFields.map { it.copy(projectId = savedId) })
                        }
                        onSaved(savedId)
                    }
                }
            }) { Icon(Icons.Default.Save, ui("Salvar")) }
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextInput("Nome do projeto", name) { name = it }
            TextInput("Descrição", description, minLines = 3) { description = it }
            TextInput("Instituição", institution) { institution = it }
            TextInput("Responsáveis", pi) { pi = it }
            ChipRow("Datum padrão", listOf("SIRGAS 2000" to "SIRGAS 2000", "WGS84" to "WGS84"), datum) { datum = it }
            ChipRow("Formato de coordenadas", listOf("DD" to "Graus Decimais", "UTM" to "UTM", "DMS" to "DMS", "DMM" to "DMM"), coordFormat) { coordFormat = it }
            ChipRow("Qualidade da foto", listOf("low" to "Baixa", "medium" to "Media", "high" to "Alta"), photoQuality) { photoQuality = it }
            ChipRow("Formato do áudio", listOf("wav" to "WAV", "mp3" to "MP3"), audioFormat) { audioFormat = it }
            Text(ui("Módulos do projeto"), style = MaterialTheme.typography.titleMedium)
            Text(ui("Escolha os grupos que este projeto pode registrar. Na ocorrência, o app mostra primeiro o grupo e depois apenas o questionário específico."), color = MaterialTheme.colorScheme.onSurfaceVariant)
            ProjectModuleFieldCard("Fauna", ui("Animais observados, capturados, ouvidos, vestígios e armadilhas fotográficas."), enableFauna, fields, openFieldGroup == "Fauna", onToggle = { enableFauna = it }, onOpen = { openFieldGroup = if (openFieldGroup == "Fauna") null else "Fauna" }, onFieldChange = ::updateProjectField)
            ProjectModuleFieldCard("Botanica", ui("Flora, partes de plantas, flor, fruto, caule, folha e dados botânicos."), enableBotany, fields, openFieldGroup == "Botanica", onToggle = { enableBotany = it }, onOpen = { openFieldGroup = if (openFieldGroup == "Botanica") null else "Botanica" }, onFieldChange = ::updateProjectField)
            ProjectModuleFieldCard("Fungos", ui("Funga, substrato, corpo de frutificação, associações e observações micológicas."), enableFungi, fields, openFieldGroup == "Fungos", onToggle = { enableFungi = it }, onOpen = { openFieldGroup = if (openFieldGroup == "Fungos") null else "Fungos" }, onFieldChange = ::updateProjectField)
        }
    }
}

@Composable
private fun ProjectModuleFieldCard(
    title: String,
    description: String,
    checked: Boolean,
    fields: List<FieldConfigEntity>,
    expanded: Boolean,
    onToggle: (Boolean) -> Unit,
    onOpen: () -> Unit,
    onFieldChange: (FieldConfigEntity) -> Unit
) {
    val groupFields = fields.filter { it.groupName == title }.sortedBy { it.displayOrder }
    val displayTitle = when (title) {
        "Botanica" -> "Flora"
        "Fungos" -> "Funga"
        else -> title
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).clickable(enabled = checked && groupFields.isNotEmpty(), onClick = onOpen), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(displayTitle, style = MaterialTheme.typography.titleMedium)
                    Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (checked && groupFields.isNotEmpty()) {
                        Text(if (expanded) ui("Toque para recolher campos") else ui("Toque para escolher campos deste módulo"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Switch(checked = checked, onCheckedChange = onToggle)
            }
            AnimatedVisibility(visible = checked && expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (groupFields.isEmpty()) {
                        Text(ui("Salve o projeto para gerar estes campos."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    groupFields.forEach { field ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(fieldDisplayLabel(field), style = MaterialTheme.typography.bodyMedium)
                                Text(field.dwcTerm, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = field.enabled, onCheckedChange = { onFieldChange(field.copy(enabled = it, required = field.required && it)) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectDetailScreen(
    viewModel: AppViewModel,
    projectId: Long,
    modifier: Modifier,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onFields: () -> Unit,
    onNewSession: () -> Unit,
    onOpenSession: (Long) -> Unit,
    onExport: () -> Unit,
    onDeleted: () -> Unit
) {
    val project by viewModel.observeProject(projectId).collectAsState(initial = null)
    val sessions by viewModel.observeSessions(projectId).collectAsState(initial = emptyList())
    var deleteProject by remember { mutableStateOf(false) }
    var sessionToDelete by remember { mutableStateOf<SessionEntity?>(null) }
    if (deleteProject) {
        val currentProject = project
        ConfirmDeleteDialog(
            title = "Apagar projeto?",
            text = "Isso remove o projeto inteiro, incluindo expedições, ocorrências, trilhas e mídias locais.",
            confirmText = "Apagar projeto",
            onDismiss = { deleteProject = false },
            onConfirm = {
                deleteProject = false
                currentProject?.let { viewModel.deleteProject(it, onDeleted) }
            }
        )
    }
    sessionToDelete?.let { session ->
        ConfirmDeleteDialog(
            title = "Apagar expedição?",
            text = "Isso remove a expedição, suas ocorrências, trilhas e mídias vinculadas.",
            confirmText = "Apagar expedição",
            onDismiss = { sessionToDelete = null },
            onConfirm = {
                sessionToDelete = null
                viewModel.deleteSession(session) { }
            }
        )
    }
    Scaffold(
        modifier = modifier,
        topBar = {
            OikosTopBar(project?.name ?: "Projeto", onBack) {
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, ui("Editar")) }
                IconButton(onClick = onFields) { Icon(Icons.Default.Settings, ui("Campos")) }
                IconButton(onClick = onExport) { Icon(Icons.Default.Share, ui("Exportar")) }
                IconButton(onClick = { deleteProject = true }) { Icon(Icons.Default.Delete, ui("Apagar projeto")) }
            }
        },
        floatingActionButton = { FloatingActionButton(onClick = onNewSession) { Icon(Icons.Default.Add, ui("Nova expedição")) } }
    ) { padding ->
        LazyColumn(contentPadding = padding.plus(PaddingValues(16.dp)), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text(ui("Expedições"), style = MaterialTheme.typography.titleLarge)
                Text("${sessions.size} ${ui("expedições")} - Datum ${project?.defaultDatum ?: "SIRGAS 2000"}")
            }
            if (sessions.isEmpty()) item { EmptyState("Sem expedições.", "Crie uma expedição para agrupar as ocorrências de campo.") }
            items(sessions) { session ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpenSession(session.id) }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(session.name, style = MaterialTheme.typography.titleMedium)
                            Text(listOf(session.eventDate, session.locality, session.recordedBy).filter { it.isNotBlank() }.joinToString(" - "))
                            if (session.latitude != null && session.longitude != null) Text("${session.latitude}, ${session.longitude} +/-${session.coordinateUncertaintyInMeters ?: 0.0}m")
                        }
                        IconButton(onClick = { sessionToDelete = session }) {
                            Icon(Icons.Default.Delete, ui("Apagar expedição"))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FieldConfigScreen(viewModel: AppViewModel, projectId: Long, modifier: Modifier, onBack: () -> Unit) {
    val fields by viewModel.observeFields(projectId).collectAsState(initial = emptyList())
    LaunchedEffect(projectId) {
        viewModel.ensureFieldCatalog(projectId)
    }
    Scaffold(modifier = modifier, topBar = { OikosTopBar("Campos Darwin Core", onBack) }) { padding ->
        LazyColumn(contentPadding = padding.plus(PaddingValues(16.dp)), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            fields.groupBy { it.groupName }.forEach { (group, groupFields) ->
                item { Text(ui(fieldGroupDisplayName(group)), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
                items(groupFields.sortedBy { it.displayOrder }) { field ->
                    Card {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(fieldDisplayLabel(field), style = MaterialTheme.typography.bodyLarge)
                                Text("${field.labelEn} -> ${field.dwcTerm}", style = MaterialTheme.typography.labelMedium)
                            }
                            Text(ui("Obrig."))
                            Switch(checked = field.required, onCheckedChange = { viewModel.updateField(field.copy(required = it, enabled = field.enabled || it)) })
                            Spacer(Modifier.width(8.dp))
                            Switch(checked = field.enabled, onCheckedChange = { viewModel.updateField(field.copy(enabled = it, required = field.required && it)) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionEditScreen(
    viewModel: AppViewModel,
    projectId: Long,
    sessionId: Long?,
    modifier: Modifier,
    requestLocation: ((CapturedLocation?) -> Unit) -> Unit,
    onBack: () -> Unit,
    onSaved: (Long) -> Unit
) {
    val project by viewModel.observeProject(projectId).collectAsState(initial = null)
    val existing by (sessionId?.let { viewModel.observeSession(it) } ?: kotlinx.coroutines.flow.flowOf(null)).collectAsState(initial = null)
    var name by remember(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }
    var eventId by remember(existing?.id) { mutableStateOf(existing?.eventId ?: AppViewModel.newEventId()) }
    var date by remember(existing?.id) { mutableStateOf(existing?.eventDate ?: LocalDate.now().toString()) }
    var startTime by remember(existing?.id) { mutableStateOf(existing?.startTime ?: LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))) }
    var endTime by remember(existing?.id) { mutableStateOf(existing?.endTime.orEmpty()) }
    var locality by remember(existing?.id) { mutableStateOf(existing?.locality.orEmpty()) }
    var municipality by remember(existing?.id) { mutableStateOf(existing?.municipality.orEmpty()) }
    var stateProvince by remember(existing?.id) { mutableStateOf(existing?.stateProvince.orEmpty()) }
    var country by remember(existing?.id) { mutableStateOf(existing?.country?.ifBlank { "Brasil" } ?: "Brasil") }
    var habitat by remember(existing?.id) { mutableStateOf(existing?.habitat.orEmpty()) }
    var environmentType by remember(existing?.id) { mutableStateOf(existing?.environmentType.orEmpty()) }
    var protocol by remember(existing?.id) { mutableStateOf(existing?.samplingProtocol.orEmpty()) }
    var effort by remember(existing?.id) { mutableStateOf(existing?.samplingEffort.orEmpty()) }
    var recordedBy by remember(existing?.id) { mutableStateOf(existing?.recordedBy.orEmpty()) }
    var fieldTeam by remember(existing?.id) { mutableStateOf(existing?.fieldTeam?.ifBlank { project?.principalInvestigator.orEmpty() } ?: project?.principalInvestigator.orEmpty()) }
    var trackEnabled by remember(existing?.id) { mutableStateOf(existing?.gpsTrackEnabled ?: false) }
    var lat by remember(existing?.id) { mutableStateOf(existing?.latitude?.toString().orEmpty()) }
    var lon by remember(existing?.id) { mutableStateOf(existing?.longitude?.toString().orEmpty()) }
    var alt by remember(existing?.id) { mutableStateOf(existing?.altitude?.toString().orEmpty()) }
    var accuracy by remember(existing?.id) { mutableStateOf(existing?.coordinateUncertaintyInMeters?.toString().orEmpty()) }
    var notes by remember(existing?.id) { mutableStateOf(existing?.notes.orEmpty()) }

    LaunchedEffect(sessionId) {
        if (sessionId == null && lat.isBlank() && lon.isBlank()) {
            requestLocation { location ->
                location?.let {
                    lat = it.latitude.toString()
                    lon = it.longitude.toString()
                    alt = it.altitude?.toString().orEmpty()
                    accuracy = it.accuracyMeters?.toString().orEmpty()
                    if (municipality.isBlank()) municipality = it.municipality
                    if (stateProvince.isBlank()) stateProvince = it.stateProvince
                    if (country.isBlank() || country == "Brasil") country = it.country.ifBlank { country }
                }
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = { OikosTopBar(if (sessionId == null) "Nova expedição" else "Editar expedição", onBack) },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                viewModel.saveSession(
                    SessionEntity(
                        id = existing?.id ?: 0L,
                        projectId = projectId,
                        eventId = eventId,
                        name = name.ifBlank { "Expedição $date" },
                        eventDate = date,
                        startTime = startTime,
                        endTime = endTime,
                        locality = locality,
                        municipality = municipality,
                        stateProvince = stateProvince,
                        country = country,
                        habitat = habitat,
                        environmentType = environmentType,
                        samplingProtocol = protocol,
                        samplingEffort = effort,
                        recordedBy = recordedBy,
                        fieldTeam = fieldTeam,
                        gpsTrackEnabled = trackEnabled,
                        latitude = lat.toDoubleOrNull(),
                        longitude = lon.toDoubleOrNull(),
                        altitude = alt.toDoubleOrNull(),
                        coordinateUncertaintyInMeters = accuracy.toDoubleOrNull(),
                        geodeticDatum = project?.defaultDatum ?: existing?.geodeticDatum ?: "SIRGAS 2000",
                        notes = notes,
                        createdAt = existing?.createdAt ?: System.currentTimeMillis()
                    ),
                    onSaved
                )
            }) { Icon(Icons.Default.Save, ui("Salvar")) }
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            HelpCard("Expedição", "Expedição é uma saída de campo, dia de coleta ou campanha. Internamente ela continua como Darwin Core Event.")
            TextInput("Nome da expedição", name) { name = it }
            TextInput("eventID", eventId) { eventId = it }
            DateInput("Data", date) { date = it }
            TimeInput("Hora de início", startTime) { startTime = it }
            TimeInput("Hora de término", endTime) { endTime = it }
            TextInput("Localidade", locality) { locality = it }
            TextInput("Município", municipality) { municipality = it }
            TextInput("Estado", stateProvince) { stateProvince = it }
            TextInput("País", country) { country = it }
            TextInput("Habitat geral", habitat) { habitat = it }
            TextInput("Tipo de ambiente", environmentType) { environmentType = it }
            TextInput("Método de amostragem", protocol) { protocol = it }
            TextInput("Esforço amostral", effort, help = "Tempo, distância ou método usado na coleta. Exemplo: 2 horas de busca ativa.") { effort = it }
            TextInput("Equipe de campo", fieldTeam) { fieldTeam = it }
            TextInput("Coletor principal", recordedBy) { recordedBy = it }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(ui("Registrar trilha da expedição"), style = MaterialTheme.typography.titleMedium)
                    Text(ui("Salva o percurso geral da expedição para exportar como LineString."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = trackEnabled, onCheckedChange = { trackEnabled = it })
            }
            LocationFields(project?.coordinateFormat ?: "DD", lat, lon, alt, accuracy, { lat = it }, { lon = it }, { alt = it }, { accuracy = it }) {
                requestLocation { location ->
                    location?.let {
                        lat = it.latitude.toString()
                        lon = it.longitude.toString()
                        alt = it.altitude?.toString().orEmpty()
                        accuracy = it.accuracyMeters?.toString().orEmpty()
                        if (municipality.isBlank()) municipality = it.municipality
                        if (stateProvince.isBlank()) stateProvince = it.stateProvince
                        if (country.isBlank() || country == "Brasil") country = it.country.ifBlank { country }
                    }
                }
            }
            TextInput("Observações gerais", notes, minLines = 3) { notes = it }
        }
    }
}

@Composable
private fun SessionDetailScreen(
    viewModel: AppViewModel,
    projectId: Long,
    sessionId: Long,
    modifier: Modifier,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onNewOccurrence: () -> Unit,
    onOpenOccurrence: (Long) -> Unit,
    onMedia: () -> Unit,
    onExport: () -> Unit,
    onDeleted: () -> Unit,
    requestLocation: ((CapturedLocation?) -> Unit) -> Unit
) {
    val session by viewModel.observeSession(sessionId).collectAsState(initial = null)
    val occurrences by viewModel.observeOccurrences(sessionId).collectAsState(initial = emptyList())
    val media by viewModel.observeSessionMedia(sessionId).collectAsState(initial = emptyList())
    val tracks by viewModel.observeTracks(sessionId).collectAsState(initial = emptyList())
    val activeTrack = tracks.firstOrNull { it.active }
    val displayTrack = activeTrack ?: tracks.firstOrNull()
    val trackPoints by (displayTrack?.let { viewModel.observeTrackPoints(it.id) } ?: kotlinx.coroutines.flow.flowOf(emptyList())).collectAsState(initial = emptyList())
    val context = LocalContext.current
    var deleteSession by remember { mutableStateOf(false) }
    var occurrenceToDelete by remember { mutableStateOf<OccurrenceEntity?>(null) }
    var currentLocation by remember { mutableStateOf<CapturedLocation?>(null) }
    val importTrackLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val currentSession = session
        if (uri != null && currentSession != null) {
            val imported = parseImportedTrack(context, uri)
            viewModel.importTrack(currentSession, imported)
        }
    }
    LaunchedEffect(sessionId) {
        requestLocation { location -> currentLocation = location }
    }
    if (deleteSession) {
        val currentSession = session
        ConfirmDeleteDialog(
            title = "Apagar expedição?",
            text = "Isso remove a expedição atual, suas ocorrências, trilhas e mídias vinculadas.",
            confirmText = "Apagar expedição",
            onDismiss = { deleteSession = false },
            onConfirm = {
                deleteSession = false
                currentSession?.let { viewModel.deleteSession(it, onDeleted) }
            }
        )
    }
    occurrenceToDelete?.let { occurrence ->
        ConfirmDeleteDialog(
            title = "Apagar ocorrência?",
            text = "Isso remove o registro e as fotos/áudios vinculados a ele.",
            confirmText = "Apagar ocorrência",
            onDismiss = { occurrenceToDelete = null },
            onConfirm = {
                occurrenceToDelete = null
                viewModel.deleteOccurrence(occurrence) { }
            }
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            OikosTopBar(session?.name ?: "Expedição", onBack) {
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Editar") }
                IconButton(onClick = onMedia) { Icon(Icons.Default.Photo, "Mídias") }
                IconButton(onClick = onExport) { Icon(Icons.Default.Share, "Exportar expedição") }
                IconButton(onClick = { deleteSession = true }) { Icon(Icons.Default.Delete, ui("Apagar expedição")) }
            }
        },
        floatingActionButton = { FloatingActionButton(onClick = onNewOccurrence) { Icon(Icons.Default.Add, "Nova ocorrência") } }
    ) { padding ->
        LazyColumn(contentPadding = padding.plus(PaddingValues(16.dp)), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text(ui("Ocorrências"), style = MaterialTheme.typography.titleLarge)
                Text("${occurrences.size} ${ui("registros nesta expedição")}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onNewOccurrence, modifier = Modifier.weight(1f).height(50.dp)) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(6.dp))
                        Text(ui("Registro"))
                    }
                    OutlinedButton(onClick = onMedia, modifier = Modifier.weight(1f).height(50.dp)) {
                        Icon(Icons.Default.Photo, null)
                        Spacer(Modifier.width(6.dp))
                        Text(ui("Mídias"))
                    }
                }
            }
            item {
                TrackPanel(
                    track = displayTrack,
                    points = trackPoints,
                    occurrences = occurrences,
                    currentLocation = currentLocation,
                    isActive = activeTrack != null,
                    onLocate = {
                        requestLocation { location -> currentLocation = location }
                    },
                    onStart = {
                        session?.let { currentSession ->
                            requestLocation { location ->
                                if (location != null) {
                                    viewModel.startTrack(currentSession) { trackId ->
                                        viewModel.addTrackPoint(trackId, location)
                                    }
                                }
                            }
                        }
                    },
                    onAddPoint = {
                        val track = activeTrack
                        if (track != null) {
                            requestLocation { location -> location?.let { viewModel.addTrackPoint(track.id, it) } }
                        } else {
                            session?.let { currentSession ->
                                requestLocation { location ->
                                    if (location != null) {
                                        viewModel.startTrack(currentSession) { trackId ->
                                            viewModel.addTrackPoint(trackId, location)
                                        }
                                    }
                                }
                            }
                        }
                    },
                    onStop = {
                        activeTrack?.let { track ->
                            requestLocation { location -> viewModel.stopTrack(track, location) }
                        }
                    },
                    onImport = {
                        importTrackLauncher.launch(arrayOf("application/gpx+xml", "application/geo+json", "application/json", "text/*", "*/*"))
                    },
                    onExport = onExport
                )
            }
            if (occurrences.isEmpty()) item { EmptyState("Sem ocorrências.", "Adicione espécies, coordenadas, fotos e áudios nesta expedição.") }
            items(occurrences) { occurrence ->
                val hasGps = occurrence.latitude != null && occurrence.longitude != null
                val complete = hasGps && occurrence.occurrenceId.isNotBlank()
                val occurrenceMedia = media.filter { it.occurrenceId == occurrence.id }
                val photos = occurrenceMedia.count { it.mediaType == "photo" }
                val audios = occurrenceMedia.count { it.mediaType == "audio" }
                Card(
                    Modifier.fillMaxWidth().animateContentSize(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpenOccurrence(occurrence.id) }
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.Top) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(occurrence.scientificName.ifBlank { "Ocorrência sem nome científico" }, style = MaterialTheme.typography.titleMedium)
                                Text(listOf(occurrence.vernacularName, occurrence.recordedBy, occurrence.occurrenceStatus).filter { it.isNotBlank() }.joinToString(" - "))
                            }
                            IconButton(onClick = { occurrenceToDelete = occurrence }) {
                                Icon(Icons.Default.Delete, ui("Apagar ocorrência"))
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                            StatusBadge(if (complete) "Completo" else "Incompleto", complete)
                            StatusBadge(if (hasGps) "GPS ok" else "Sem GPS", hasGps)
                            StatusBadge("$photos fotos", photos > 0)
                            StatusBadge("$audios áudios", audios > 0)
                        }
                        PhotoThumbnailStrip(occurrenceMedia.filter { it.mediaType == "photo" })
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackPanel(
    track: GpsTrackEntity?,
    points: List<GpsTrackPointEntity>,
    occurrences: List<OccurrenceEntity>,
    currentLocation: CapturedLocation?,
    isActive: Boolean,
    onLocate: () -> Unit,
    onStart: () -> Unit,
    onAddPoint: () -> Unit,
    onStop: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit
) {
    var showLargeMap by remember { mutableStateOf(false) }
    var recenterToken by remember { mutableStateOf(0) }
    var showTrackDisclosure by remember { mutableStateOf(false) }
    val bearing = rememberCompassBearing()
    LaunchedEffect(Unit) {
        onLocate()
    }
    LaunchedEffect(showLargeMap) {
        while (showLargeMap) {
            onLocate()
            delay(3_000)
        }
    }
    if (showLargeMap) {
        TrackMapDialog(
            points = points,
            occurrences = occurrences,
            currentLocation = currentLocation,
            bearing = bearing,
            onLocate = {
                onLocate()
                recenterToken++
            },
            onDismiss = { showLargeMap = false }
        )
    }
    if (showTrackDisclosure) {
        AlertDialog(
            onDismissRequest = { showTrackDisclosure = false },
            title = { Text(ui("Uso de localização na trilha")) },
            text = {
                Text(
                    ui("Enquanto a trilha estiver ativa, o Oikos FieldBook usa sua localização para registrar o percurso da expedição, mesmo com a tela bloqueada ou o app em segundo plano. Uma notificação permanente será exibida. A localização é salva apenas no aparelho e nas exportações que você gerar.")
                )
            },
            confirmButton = {
                Button(onClick = {
                    showTrackDisclosure = false
                    onStart()
                }) {
                    Text(ui("Continuar e iniciar trilha"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showTrackDisclosure = false }) {
                    Text(ui("Cancelar"))
                }
            }
        )
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(ui("Trilha GPS da expedição"), style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (isActive) "Registrando em segundo plano. A linha laranja é a trilha base; pontos das ocorrências aparecem por cima." else "Inicie a trilha para acompanhar o percurso contínuo da expedição.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusBadge(if (isActive) "Ativa" else "Parada", isActive)
            }
            TrackMapPreview(
                points = points,
                occurrences = occurrences,
                currentLocation = currentLocation,
                bearing = bearing,
                recenterToken = recenterToken,
                modifier = Modifier.fillMaxWidth().height(240.dp)
            )
            val startButtonAction: () -> Unit = if (isActive) onAddPoint else ({ showTrackDisclosure = true })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                Button(onClick = startButtonAction) {
                    Icon(Icons.Default.GpsFixed, null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (isActive) "Atualizar agora" else "Iniciar trilha")
                }
                OutlinedButton(onClick = onStop, enabled = isActive) {
                    Text(ui("Parar"))
                }
                OutlinedButton(onClick = {
                    onLocate()
                    recenterToken++
                }) {
                    Text(ui("Centralizar"))
                }
                OutlinedButton(onClick = { showLargeMap = true }) {
                    Text(ui("Ver mapa"))
                }
                OutlinedButton(onClick = onImport) {
                    Text(ui("Importar trilha"))
                }
                OutlinedButton(onClick = onExport) {
                    Icon(Icons.Default.Share, null)
                    Spacer(Modifier.width(6.dp))
                    Text(ui("Exportar"))
                }
            }
            val distance = track?.totalDistance ?: 0.0
            val occurrencePoints = occurrences.count { it.latitude != null && it.longitude != null }
            val lastPoint = points.lastOrNull()
            Text(
                "${points.size} pontos da trilha - $occurrencePoints ocorrências com GPS - ${humanDistance(distance)}.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                if (lastPoint != null) {
                    "Último ponto: ${formatClock(lastPoint.timestamp)} - precisão ${lastPoint.accuracy?.let { "±${it.toInt()} m" } ?: "não informada"}. Atualização automática aproximada: 3 s ou 1,5 m."
                } else {
                    "Ao iniciar, o app grava o ponto de saída e atualiza a linha automaticamente a cada poucos segundos."
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TrackMapPreview(
    points: List<GpsTrackPointEntity>,
    occurrences: List<OccurrenceEntity>,
    currentLocation: CapturedLocation?,
    bearing: Float,
    recenterToken: Int,
    modifier: Modifier = Modifier
) {
    val border = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    val context = LocalContext.current
    val occurrencePoints = occurrences.filter { it.latitude != null && it.longitude != null }
    val userArrow = remember(context) { oikosArrowMarkerDrawable(context) }
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var mapOrientation by remember { mutableStateOf(0f) }
    LaunchedEffect(mapViewRef) {
        while (mapViewRef != null) {
            mapOrientation = mapViewRef?.mapOrientation ?: 0f
            delay(120)
        }
    }
    Box(
        modifier
            .clip(MaterialTheme.shapes.large)
            .background(Color(0xFF101114))
            .border(1.dp, border, MaterialTheme.shapes.large)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                MapView(context).apply {
                    mapViewRef = this
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    overlays.add(RotationGestureOverlay(this).apply { isEnabled = true })
                    minZoomLevel = 3.0
                    maxZoomLevel = 20.0
                    controller.setZoom(16.0)
                }
            },
            update = { mapView ->
                mapViewRef = mapView
                mapView.overlays.clear()
                mapView.overlays.add(RotationGestureOverlay(mapView).apply { isEnabled = true })
                val trackGeo = points.map { GeoPoint(it.latitude, it.longitude) }
                val occurrenceGeo = occurrencePoints.mapNotNull {
                    val lat = it.latitude
                    val lon = it.longitude
                    if (lat != null && lon != null) GeoPoint(lat, lon) to it else null
                }
                val userGeo = currentLocation?.let { GeoPoint(it.latitude, it.longitude) }
                if (trackGeo.size >= 2) {
                    val line = Polyline().apply {
                        setPoints(trackGeo)
                        outlinePaint.color = android.graphics.Color.rgb(255, 106, 0)
                        outlinePaint.strokeWidth = 10f
                        outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                        outlinePaint.strokeJoin = android.graphics.Paint.Join.ROUND
                    }
                    mapView.overlays.add(line)
                }
                userGeo?.let { point ->
                    mapView.overlays.add(
                        Marker(mapView).apply {
                            position = point
                            title = "Você está aqui"
                            icon = userArrow
                            rotation = bearing
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        }
                    )
                }
                occurrenceGeo.forEach { (point, occurrence) ->
                    mapView.overlays.add(
                        Marker(mapView).apply {
                            position = point
                            title = occurrence.scientificName.ifBlank { occurrence.occurrenceId }
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        }
                    )
                }
                val center = trackGeo.lastOrNull()
                    ?: userGeo
                    ?: occurrenceGeo.firstOrNull()?.first
                    ?: GeoPoint(-22.739521, -45.591234)
                if (mapView.tag != recenterToken) {
                    mapView.mapOrientation = 0f
                    mapView.controller.setZoom(17.0)
                    mapView.controller.setCenter(center)
                    mapView.tag = recenterToken
                }
                mapView.invalidate()
            }
        )
        MapCompass(
            rotationDegrees = -mapOrientation,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
        )
        Surface(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
            shape = MaterialTheme.shapes.small
        ) {
            Text(
                "© OpenStreetMap",
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TrackMapDialog(
    points: List<GpsTrackPointEntity>,
    occurrences: List<OccurrenceEntity>,
    currentLocation: CapturedLocation?,
    bearing: Float,
    onLocate: () -> Unit,
    onDismiss: () -> Unit
) {
    var recenterToken by remember { mutableStateOf(1) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            color = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(ui("Mapa da expedição"), style = MaterialTheme.typography.titleMedium)
                        Text(ui("Seta laranja: direção do usuário. A bússola N mapa aponta o norte do mapa; gire com dois dedos para orientar a visualização."), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = onDismiss) { Text(ui("Fechar")) }
                }
                TrackMapPreview(
                    points = points,
                    occurrences = occurrences,
                    currentLocation = currentLocation,
                    bearing = bearing,
                    recenterToken = recenterToken,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(520.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    Button(onClick = {
                        onLocate()
                        recenterToken++
                    }) {
                        Icon(Icons.Default.GpsFixed, null)
                        Spacer(Modifier.width(6.dp))
                        Text(ui("Centralizar usuário"))
                    }
                    OutlinedButton(onClick = onDismiss) { Text(ui("Fechar mapa")) }
                }
            }
        }
    }
}

@Composable
private fun MapCompass(rotationDegrees: Float, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
        shape = MaterialTheme.shapes.small
    ) {
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(32.dp)) {
                rotate(rotationDegrees) {
                    val arrow = androidx.compose.ui.graphics.Path().apply {
                        moveTo(size.width / 2f, 0f)
                        lineTo(size.width * 0.78f, size.height * 0.78f)
                        lineTo(size.width / 2f, size.height * 0.58f)
                        lineTo(size.width * 0.22f, size.height * 0.78f)
                        close()
                    }
                    drawPath(arrow, Color(0xFFFF6A00))
                }
            }
            Text(
                "N",
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun rememberCompassBearing(): Float {
    val context = LocalContext.current
    var bearing by remember { mutableStateOf(0f) }
    DisposableEffect(context) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)
        if (sensorManager == null || sensor == null) {
            onDispose { }
        } else {
            val rotationMatrix = FloatArray(9)
            val orientation = FloatArray(3)
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                    SensorManager.getOrientation(rotationMatrix, orientation)
                    val degrees = Math.toDegrees(orientation[0].toDouble()).toFloat()
                    bearing = (degrees + 360f) % 360f
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
            sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
            onDispose { sensorManager.unregisterListener(listener) }
        }
    }
    return bearing
}

private fun oikosArrowMarkerDrawable(context: Context): BitmapDrawable {
    val size = 96
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(255, 106, 0)
        style = Paint.Style.FILL
    }
    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(95, 0, 0, 0)
        style = Paint.Style.FILL
    }
    val arrow = Path().apply {
        moveTo(size / 2f, 8f)
        lineTo(size * 0.78f, size * 0.82f)
        lineTo(size / 2f, size * 0.64f)
        lineTo(size * 0.22f, size * 0.82f)
        close()
    }
    canvas.save()
    canvas.translate(3f, 5f)
    canvas.drawPath(arrow, shadowPaint)
    canvas.restore()
    canvas.drawPath(arrow, paint)
    return BitmapDrawable(context.resources, bitmap)
}

private fun googleMapsUri(points: List<GpsTrackPointEntity>): Uri? {
    val last = points.lastOrNull() ?: return null
    val first = points.firstOrNull()
    if (first == null || points.size < 2) {
        return Uri.parse("geo:${last.latitude},${last.longitude}?q=${last.latitude},${last.longitude}(Oikos+FieldBook)")
    }
    val waypoints = points.drop(1).dropLast(1)
        .filterIndexed { index, _ -> index % ((points.size / 8).coerceAtLeast(1)) == 0 }
        .take(8)
        .joinToString("|") { "${it.latitude},${it.longitude}" }
    val base = "https://www.google.com/maps/dir/?api=1&travelmode=walking" +
        "&origin=${first.latitude},${first.longitude}" +
        "&destination=${last.latitude},${last.longitude}"
    return Uri.parse(if (waypoints.isBlank()) base else "$base&waypoints=$waypoints")
}

private fun parseImportedTrack(context: Context, uri: Uri): List<ImportedTrackPoint> {
    val text = runCatching {
        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
    }.getOrDefault("")
    if (text.isBlank()) return emptyList()
    return when {
        text.contains("<gpx", ignoreCase = true) -> parseGpxTrack(text)
        text.contains("<kml", ignoreCase = true) || text.contains("<coordinates", ignoreCase = true) -> parseKmlTrack(text)
        text.trimStart().startsWith("{") -> parseGeoJsonTrack(text)
        else -> parseCsvTrack(text)
    }
}

private fun parseGpxTrack(text: String): List<ImportedTrackPoint> {
    val pointRegex = Regex("""<(?:trkpt|rtept|wpt)\b[^>]*\blat=["']([-0-9.]+)["'][^>]*\blon=["']([-0-9.]+)["'][^>]*>(.*?)</(?:trkpt|rtept|wpt)>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    val elevationRegex = Regex("""<ele>([-0-9.]+)</ele>""", RegexOption.IGNORE_CASE)
    return pointRegex.findAll(text).mapNotNull { match ->
        val lat = match.groupValues[1].toDoubleOrNull()
        val lon = match.groupValues[2].toDoubleOrNull()
        val ele = elevationRegex.find(match.groupValues[3])?.groupValues?.getOrNull(1)?.toDoubleOrNull()
        if (lat != null && lon != null) ImportedTrackPoint(lat, lon, altitude = ele) else null
    }.toList()
}

private fun parseKmlTrack(text: String): List<ImportedTrackPoint> {
    val coordinateBlocks = Regex("""<coordinates[^>]*>(.*?)</coordinates>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        .findAll(text)
        .map { it.groupValues[1] }
        .toList()
    return coordinateBlocks.flatMap { block ->
        block.trim().split(Regex("\\s+")).mapNotNull { token ->
            val parts = token.split(",")
            val lon = parts.getOrNull(0)?.toDoubleOrNull()
            val lat = parts.getOrNull(1)?.toDoubleOrNull()
            val alt = parts.getOrNull(2)?.toDoubleOrNull()
            if (lat != null && lon != null) ImportedTrackPoint(lat, lon, altitude = alt) else null
        }
    }
}

private fun parseGeoJsonTrack(text: String): List<ImportedTrackPoint> = runCatching {
    val points = mutableListOf<ImportedTrackPoint>()
    fun readCoordinates(array: JSONArray) {
        if (array.length() >= 2 && array.opt(0) is Number && array.opt(1) is Number) {
            val lon = array.optDouble(0)
            val lat = array.optDouble(1)
            val alt = if (array.length() > 2 && array.opt(2) is Number) array.optDouble(2) else null
            points += ImportedTrackPoint(lat, lon, altitude = alt)
            return
        }
        for (i in 0 until array.length()) {
            (array.opt(i) as? JSONArray)?.let { readCoordinates(it) }
        }
    }
    fun readObject(obj: JSONObject) {
        obj.optJSONObject("geometry")?.optJSONArray("coordinates")?.let { readCoordinates(it) }
        obj.optJSONArray("coordinates")?.let { readCoordinates(it) }
        obj.optJSONArray("features")?.let { features ->
            for (i in 0 until features.length()) {
                (features.opt(i) as? JSONObject)?.let { readObject(it) }
            }
        }
    }
    readObject(JSONObject(text))
    points
}.getOrDefault(emptyList())

private fun parseCsvTrack(text: String): List<ImportedTrackPoint> {
    val lines = text.lines().filter { it.isNotBlank() }
    if (lines.isEmpty()) return emptyList()
    val separator = if (lines.first().count { it == ';' } > lines.first().count { it == ',' }) ';' else ','
    val header = lines.first().split(separator).map { it.trim().lowercase(Locale.US) }
    val latIndex = header.indexOfFirst { it in setOf("lat", "latitude", "decimallatitude", "decimal_latitude") }
    val lonIndex = header.indexOfFirst { it in setOf("lon", "lng", "longitude", "decimallongitude", "decimal_longitude") }
    if (latIndex >= 0 && lonIndex >= 0) {
        return lines.drop(1).mapNotNull { line ->
            val cells = line.split(separator).map { it.trim().replace(",", ".") }
            val lat = cells.getOrNull(latIndex)?.toDoubleOrNull()
            val lon = cells.getOrNull(lonIndex)?.toDoubleOrNull()
            if (lat != null && lon != null) ImportedTrackPoint(lat, lon) else null
        }
    }
    val pairRegex = Regex("""(-?\d{1,2}\.\d+)\s*[,;]\s*(-?\d{1,3}\.\d+)""")
    return pairRegex.findAll(text).mapNotNull {
        val a = it.groupValues[1].toDoubleOrNull()
        val b = it.groupValues[2].toDoubleOrNull()
        if (a != null && b != null && a in -90.0..90.0 && b in -180.0..180.0) ImportedTrackPoint(a, b) else null
    }.toList()
}

private fun hasPermission(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

private fun formatClock(timestamp: Long): String =
    SimpleDateFormat("HH:mm:ss", Locale("pt", "BR")).format(Date(timestamp))

@Composable
private fun OccurrenceEditScreen(
    viewModel: AppViewModel,
    projectId: Long,
    sessionId: Long,
    occurrenceId: Long?,
    simpleMode: Boolean,
    modifier: Modifier,
    requestLocation: ((CapturedLocation?) -> Unit) -> Unit,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    onMedia: (Long) -> Unit
) {
    val project by viewModel.observeProject(projectId).collectAsState(initial = null)
    val session by viewModel.observeSession(sessionId).collectAsState(initial = null)
    val occurrence by (occurrenceId?.let { viewModel.observeOccurrence(it) } ?: kotlinx.coroutines.flow.flowOf(null)).collectAsState(initial = null)
    val existing by (occurrenceId?.let { viewModel.observeOccurrence(it) } ?: kotlinx.coroutines.flow.flowOf(null)).collectAsState(initial = null)
    val sessionOccurrences by viewModel.observeOccurrences(sessionId).collectAsState(initial = emptyList())
    val fields by viewModel.observeFields(projectId).collectAsState(initial = emptyList())
    val enabledFields = fields.filter { it.enabled }
    val dynamicValues = remember(existing?.id, fields.size) { mutableStateMapOf<String, String>().also { it.putAll(parseFlatJsonMap(existing?.dynamicFieldsJson ?: "{}")) } }
    val pendingMedia = remember(existing?.id) { mutableStateListOf<MediaEntity>() }
    val moduleOptions = project?.enabledOccurrenceModules().orEmpty().ifEmpty { listOf("Fauna") }

    var occurrenceID by remember(existing?.id) { mutableStateOf(existing?.occurrenceId.orEmpty()) }
    var formType by remember(existing?.id) { mutableStateOf(existing?.formType?.ifBlank { moduleOptions.first() } ?: moduleOptions.first()) }
    var occurrenceType by remember(existing?.id) { mutableStateOf(existing?.occurrenceType?.ifBlank { occurrenceTypesFor(formType).first() } ?: occurrenceTypesFor(formType).first()) }
    var occurrenceDate by remember(existing?.id, session?.eventDate) {
        val savedDate = dynamicValues["eventDate"].orEmpty().ifBlank { session?.eventDate ?: LocalDate.now().toString() }
        mutableStateOf(normalizeDateInput(savedDate) ?: savedDate)
    }
    var occurrenceTime by remember(existing?.id) {
        mutableStateOf(dynamicValues["eventTime"].orEmpty().ifBlank { LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")) })
    }
    var recordNumber by remember(existing?.id) { mutableStateOf(existing?.recordNumber.orEmpty()) }
    var scientificName by remember(existing?.id) { mutableStateOf(existing?.scientificName.orEmpty()) }
    var vernacularName by remember(existing?.id) { mutableStateOf(existing?.vernacularName.orEmpty()) }
    var basis by remember(existing?.id) { mutableStateOf(existing?.basisOfRecord ?: "HumanObservation") }
    var status by remember(existing?.id) { mutableStateOf(existing?.occurrenceStatus ?: "present") }
    var count by remember(existing?.id) { mutableStateOf(existing?.individualCount?.toString().orEmpty()) }
    var sex by remember(existing?.id) { mutableStateOf(existing?.sex.orEmpty()) }
    var lifeStage by remember(existing?.id) { mutableStateOf(existing?.lifeStage.orEmpty()) }
    var behavior by remember(existing?.id) { mutableStateOf(existing?.behavior.orEmpty()) }
    var reproductive by remember(existing?.id) { mutableStateOf(existing?.reproductiveCondition.orEmpty()) }
    var recordedBy by remember(existing?.id) { mutableStateOf(existing?.recordedBy.orEmpty()) }
    var identifiedBy by remember(existing?.id) { mutableStateOf(existing?.identifiedBy.orEmpty()) }
    var dateIdentified by remember(existing?.id) { mutableStateOf(normalizeDateInput(existing?.dateIdentified.orEmpty()) ?: existing?.dateIdentified.orEmpty()) }
    var lat by remember(existing?.id) { mutableStateOf(existing?.latitude?.toString().orEmpty()) }
    var lon by remember(existing?.id) { mutableStateOf(existing?.longitude?.toString().orEmpty()) }
    var alt by remember(existing?.id) { mutableStateOf(existing?.altitude?.toString().orEmpty()) }
    var accuracy by remember(existing?.id) { mutableStateOf(existing?.coordinateUncertaintyInMeters?.toString().orEmpty()) }
    var remarks by remember(existing?.id) { mutableStateOf(existing?.occurrenceRemarks.orEmpty()) }
    var fieldNotes by remember(existing?.id) { mutableStateOf(existing?.fieldNotes.orEmpty()) }
    var showAdvanced by remember(simpleMode) { mutableStateOf(true) }
    var deleteOccurrence by remember { mutableStateOf(false) }
    if (deleteOccurrence) {
        val currentOccurrence = occurrence
        ConfirmDeleteDialog(
            title = "Apagar ocorrência?",
            text = "Isso remove este registro e as fotos/áudios vinculados a ele.",
            confirmText = "Apagar ocorrência",
            onDismiss = { deleteOccurrence = false },
            onConfirm = {
                deleteOccurrence = false
                currentOccurrence?.let { viewModel.deleteOccurrence(it, onDeleted) }
            }
        )
    }

    LaunchedEffect(projectId) {
        viewModel.ensureFieldCatalog(projectId)
    }

    LaunchedEffect(project?.id, session?.id, existing?.id, sessionOccurrences.size) {
        if (occurrenceId == null && occurrenceID.isBlank()) {
            occurrenceID = semanticOccurrenceId(project, session, sessionOccurrences.size + 1)
        }
    }

    LaunchedEffect(moduleOptions.joinToString("|")) {
        if (formType !in moduleOptions) {
            formType = moduleOptions.first()
            occurrenceType = occurrenceTypesFor(formType).first()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            OikosTopBar(if (occurrenceId == null) "Nova ocorrência" else "Editar ocorrência", onBack) {
                if (occurrenceId != null) IconButton(onClick = { onMedia(occurrenceId) }) { Icon(Icons.Default.Photo, "Mídias") }
                if (occurrenceId != null) IconButton(onClick = { deleteOccurrence = true }) { Icon(Icons.Default.Delete, ui("Apagar ocorrência")) }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                dynamicValues["eventDate"] = occurrenceDate
                dynamicValues["eventTime"] = occurrenceTime
                val occurrenceToSave =
                    OccurrenceEntity(
                        id = existing?.id ?: 0L,
                        projectId = projectId,
                        sessionId = sessionId,
                        occurrenceId = occurrenceID,
                        occurrenceType = occurrenceType,
                        formType = formType,
                        recordNumber = recordNumber,
                        scientificName = scientificName,
                        vernacularName = vernacularName,
                        basisOfRecord = basis,
                        occurrenceStatus = status,
                        individualCount = count.toIntOrNull(),
                        sex = sex,
                        lifeStage = lifeStage,
                        behavior = behavior,
                        reproductiveCondition = reproductive,
                        recordedBy = recordedBy,
                        identifiedBy = identifiedBy,
                        dateIdentified = dateIdentified,
                        latitude = lat.toDoubleOrNull(),
                        longitude = lon.toDoubleOrNull(),
                        altitude = alt.toDoubleOrNull(),
                        coordinateUncertaintyInMeters = accuracy.toDoubleOrNull(),
                        geodeticDatum = project?.defaultDatum ?: existing?.geodeticDatum ?: "SIRGAS 2000",
                        occurrenceRemarks = remarks,
                        fieldNotes = fieldNotes,
                        dynamicFieldsJson = flatJson(dynamicValues),
                        createdAt = existing?.createdAt ?: System.currentTimeMillis()
                    )
                viewModel.saveOccurrenceWithMedia(occurrenceToSave, pendingMedia) {
                    pendingMedia.clear()
                    onBack()
                }
            }) { Icon(Icons.Default.Save, ui("Salvar")) }
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            HelpCard("Registro de ocorrência", "Registro é cada espécie, indivíduo, som, foto, vestígio ou observação feita dentro da expedição.")
            ChipRow("Grupo do registro", moduleOptions.map { it to moduleDisplayName(it) }, formType) {
                formType = it
                occurrenceType = occurrenceTypesFor(it).first()
            }
            ChipRow("Tipo de ocorrência", occurrenceTypesFor(formType).map { it to it }, occurrenceType) { occurrenceType = it }
            DateInput("Data do registro", occurrenceDate) { occurrenceDate = it }
            TimeInput("Hora do registro", occurrenceTime) { occurrenceTime = it }
            TextInput("Registro científico", occurrenceID, help = "Identificador Darwin Core occurrenceID. Ele é gerado automaticamente e pode ficar oculto no uso comum.") { occurrenceID = it }
            TextInput("Registro de campo", recordNumber, help = "Código amigável do ponto ou registro. Exemplo: P03, T1-P03 ou Registro #0003.") { recordNumber = it }
            if (enabled("scientificName", enabledFields)) TextInput(label("scientificName", enabledFields, "Nome da espécie"), scientificName, help = "Use o nome científico da espécie, se souber. Exemplo: Boana faber.") { scientificName = it }
            if (enabled("vernacularName", enabledFields)) TextInput(label("vernacularName", enabledFields, "Nome popular"), vernacularName) { vernacularName = it }
            TextInput("basisOfRecord", basis) { basis = it }
            if (enabled("occurrenceStatus", enabledFields)) TextInput(label("occurrenceStatus", enabledFields, "Status da ocorrência"), status) { status = it }
            if (enabled("individualCount", enabledFields)) TextInput(label("individualCount", enabledFields, "Número de indivíduos"), count, KeyboardType.Number) { count = it }
            if (enabled("sex", enabledFields)) TextInput(label("sex", enabledFields, "Sexo"), sex) { sex = it }
            if (enabled("lifeStage", enabledFields)) TextInput(label("lifeStage", enabledFields, "Fase de vida"), lifeStage) { lifeStage = it }
            if (enabled("behavior", enabledFields)) TextInput(label("behavior", enabledFields, "Comportamento"), behavior) { behavior = it }
            if (enabled("reproductiveCondition", enabledFields)) TextInput(label("reproductiveCondition", enabledFields, "Condição reprodutiva"), reproductive) { reproductive = it }
            if (enabled("recordedBy", enabledFields)) TextInput(label("recordedBy", enabledFields, "Quem coletou"), recordedBy) { recordedBy = it }
            if (enabled("identifiedBy", enabledFields)) TextInput(label("identifiedBy", enabledFields, "Quem identificou"), identifiedBy) { identifiedBy = it }
            if (enabled("dateIdentified", enabledFields)) DateInput(label("dateIdentified", enabledFields, "Data da identificação"), dateIdentified) { dateIdentified = it }
            LocationFields(project?.coordinateFormat ?: "DD", lat, lon, alt, accuracy, { lat = it }, { lon = it }, { alt = it }, { accuracy = it }) {
                requestLocation { location ->
                    location?.let {
                        lat = it.latitude.toString()
                        lon = it.longitude.toString()
                        alt = it.altitude?.toString().orEmpty()
                        accuracy = it.accuracyMeters?.toString().orEmpty()
                    }
                }
            }
            if (enabled("occurrenceRemarks", enabledFields)) TextInput(label("occurrenceRemarks", enabledFields, "Observações da ocorrência"), remarks, minLines = 3) { remarks = it }
            TextInput("Anotações de campo", fieldNotes, minLines = 5, help = "Use para anotações rápidas, descrição detalhada, dúvidas de identificação, observações de laboratório ou coleção.") { fieldNotes = it }
            OutlinedButton(onClick = { showAdvanced = !showAdvanced }) {
                Text(if (showAdvanced) "Ocultar campos avançados" else "Mostrar campos avançados")
            }
            AnimatedVisibility(visible = showAdvanced) {
                AdvancedOccurrenceSections(formType, dynamicValues, enabledFields)
            }
            Text(ui("Campos Darwin Core extras"), style = MaterialTheme.typography.titleMedium)
            enabledFields
                .filterNot { it.dwcTerm in fixedOccurrenceTerms }
                .filterNot { it.dwcTerm in advancedOccurrenceTerms }
                .forEach { field ->
                    val fieldLabel = "${field.labelPt} (${field.dwcTerm})"
                    when {
                        field.fieldType == "date" || field.dwcTerm.contains("Date", ignoreCase = true) ->
                            DateInput(fieldLabel, dynamicValues[field.dwcTerm].orEmpty()) { dynamicValues[field.dwcTerm] = it }
                        field.fieldType == "time" || field.dwcTerm.contains("Time", ignoreCase = true) ->
                            TimeInput(fieldLabel, dynamicValues[field.dwcTerm].orEmpty()) { dynamicValues[field.dwcTerm] = it }
                        else ->
                            TextInput(fieldLabel, dynamicValues[field.dwcTerm].orEmpty()) { dynamicValues[field.dwcTerm] = it }
                    }
                }
            Text(ui("Mídias da ocorrência"), style = MaterialTheme.typography.titleMedium)
            InlineMediaCapture(
                viewModel = viewModel,
                projectId = projectId,
                sessionId = sessionId,
                occurrenceId = occurrenceId,
                occurrenceForName = occurrenceToName(
                    occurrenceID = occurrenceID,
                    scientificName = scientificName,
                    recordNumber = recordNumber
                ),
                modifier = Modifier.fillMaxWidth(),
                pendingMedia = pendingMedia,
                onPendingMediaAdded = { pendingMedia.add(it) },
                onPendingMediaDeleted = {
                    pendingMedia.remove(it)
                    viewModel.mediaFiles.deleteIfManaged(it.filePath)
                },
                onPhotoMetadata = { metadata ->
                    metadata.capturedAt?.let {
                        occurrenceDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(it))
                        occurrenceTime = SimpleDateFormat("HH:mm", Locale.US).format(Date(it))
                    }
                    if (lat.isBlank()) metadata.latitude?.let { lat = it.toString() }
                    if (lon.isBlank()) metadata.longitude?.let { lon = it.toString() }
                }
            )
        }
    }
}

@Composable
private fun MediaScreen(viewModel: AppViewModel, projectId: Long, sessionId: Long, occurrenceId: Long?, modifier: Modifier, onBack: () -> Unit) {
    val project by viewModel.observeProject(projectId).collectAsState(initial = null)
    val session by viewModel.observeSession(sessionId).collectAsState(initial = null)
    val occurrence by (occurrenceId?.let { viewModel.observeOccurrence(it) } ?: kotlinx.coroutines.flow.flowOf(null)).collectAsState(initial = null)
    val media by (occurrenceId?.let { viewModel.observeOccurrenceMedia(it) } ?: viewModel.observeSessionMedia(sessionId)).collectAsState(initial = emptyList())
    var pendingPhoto by remember { mutableStateOf<File?>(null) }
    var recordingFile by remember { mutableStateOf<File?>(null) }
    var recordingFormat by remember { mutableStateOf<String?>(null) }
    var mediaSubject by remember { mutableStateOf("") }
    var mediaDescription by remember { mutableStateOf("") }
    val wavRecorder = remember { WavAudioRecorder() }
    val mp3Recorder = remember { PureMp3AudioRecorder() }
    val context = LocalContext.current

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val file = pendingPhoto
        val currentProject = project
        if (success && file != null && currentProject != null && session != null) {
            viewModel.mediaFiles.compressImage(file, currentProject.photoQuality)
            viewModel.addMedia(mediaEntity(projectId, sessionId, occurrenceId, "photo", file, "image/jpeg", mediaSubject, mediaDescription, readPhotoMetadata(file)))
        }
    }
    val importPhoto = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val currentProject = project
        val currentSession = session
        if (uri != null && currentProject != null && currentSession != null) {
            val baseName = mediaBaseName(currentProject, currentSession, occurrence, mediaSubject, "FOTO")
            val file = viewModel.mediaFiles.importUri(uri, currentProject.name, currentSession.name, "photo", "image/jpeg", baseName)
            viewModel.addMedia(mediaEntity(projectId, sessionId, occurrenceId, "photo", file, "image/jpeg", mediaSubject, mediaDescription, readPhotoMetadata(file)))
        }
    }
    val importAudio = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val currentProject = project
        val currentSession = session
        if (uri != null && currentProject != null && currentSession != null) {
            val mime = viewModel.getApplication<OikosApplication>().contentResolver.getType(uri) ?: "audio/wav"
            val baseName = mediaBaseName(currentProject, currentSession, occurrence, mediaSubject, "AUDIO")
            val file = viewModel.mediaFiles.importUri(uri, currentProject.name, currentSession.name, "audio", mime, baseName)
            viewModel.addMedia(mediaEntity(projectId, sessionId, occurrenceId, "audio", file, mime, mediaSubject, mediaDescription))
        }
    }
    fun launchCameraCapture() {
        val baseName = mediaBaseName(project, session, occurrence, mediaSubject, "FOTO")
        val file = viewModel.mediaFiles.createImageFile(project?.name ?: "Projeto", session?.name ?: "Expedição", baseName)
        pendingPhoto = file
        takePicture.launch(viewModel.mediaFiles.uriFor(file))
    }
    fun startAudioRecording() {
        if (!hasPermission(context, Manifest.permission.RECORD_AUDIO)) {
            viewModel.showMessage("Permissão de áudio negada.")
            return
        }
        val audioFormat = project?.audioFormat ?: "wav"
        val baseName = mediaBaseName(project, session, occurrence, mediaSubject, "AUDIO")
        val file = viewModel.mediaFiles.createAudioFile(project?.name ?: "Projeto", session?.name ?: "Expedição", audioFormat, baseName)
        val started = runCatching {
            startAudioRecorder(audioFormat, file, mp3Recorder, wavRecorder)
        }.onFailure {
            file.delete()
            viewModel.showMessage(it.message ?: "Não foi possível iniciar a gravação de áudio.")
        }.isSuccess
        if (started) {
            recordingFormat = audioFormat
            recordingFile = file
        }
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCameraCapture() else viewModel.showMessage("Permissão de câmera negada.")
    }
    val audioPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startAudioRecording() else viewModel.showMessage("Permissão de áudio negada.")
    }

    Scaffold(
        modifier = modifier,
        topBar = { OikosTopBar(if (occurrenceId == null) "Mídias da expedição" else "Mídias da ocorrência", onBack) }
    ) { padding ->
        LazyColumn(
            contentPadding = padding.plus(PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp)),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                if (occurrenceId == null) {
                    HelpCard(
                        "Mídias da expedição",
                        "Esta galeria reúne fotos e áudios da expedição, incluindo mídias vinculadas aos registros. Toque em uma mídia para editar o que ela mostra e sua descrição individual."
                    )
                } else {
                    HelpCard("Mídias vinculadas", "Use mídias para registrar fotos e áudios ligados a esta ocorrência. A exportação preserva occurrenceID/eventID e associatedMedia.")
                }
            }
            if (occurrenceId != null) {
                item {
                    TextInput("O que esta mídia mostra ou registra?", mediaSubject, help = "Exemplos: indivíduo inteiro, folha, flor, microhabitat, canto reprodutivo, chamado territorial, observação narrada.") { mediaSubject = it }
                }
                item {
                    TextInput("Descrição da mídia", mediaDescription, minLines = 2) { mediaDescription = it }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    Button(onClick = {
                        if (hasPermission(context, Manifest.permission.CAMERA)) launchCameraCapture()
                        else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }) { Icon(Icons.Default.CameraAlt, null); Spacer(Modifier.width(6.dp)); Text(ui("Foto")) }
                    OutlinedButton(onClick = { importPhoto.launch("image/*") }) { Icon(Icons.Default.Photo, null); Spacer(Modifier.width(6.dp)); Text(ui("Anexar foto")) }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    Button(onClick = {
                        if (recordingFormat == null) {
                            if (hasPermission(context, Manifest.permission.RECORD_AUDIO)) startAudioRecording()
                            else audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        } else {
                            if (recordingFormat == "mp3") mp3Recorder.stop() else wavRecorder.stop()
                            val finishedFormat = recordingFormat
                            recordingFormat = null
                            recordingFile?.let {
                                val mime = if (finishedFormat == "mp3") "audio/mpeg" else "audio/wav"
                                viewModel.addMedia(mediaEntity(projectId, sessionId, occurrenceId, "audio", it, mime, mediaSubject, mediaDescription))
                            }
                            recordingFile = null
                        }
                    }) { Icon(Icons.Default.Mic, null); Spacer(Modifier.width(6.dp)); Text(if (recordingFormat == null) "Gravar ${project?.audioFormat?.uppercase() ?: "WAV"}" else "Parar") }
                    OutlinedButton(onClick = { importAudio.launch("audio/*") }) { Icon(Icons.Default.FileUpload, null); Spacer(Modifier.width(6.dp)); Text(ui("Anexar áudio")) }
                }
            }
            if (media.isEmpty()) {
                item { EmptyState("Sem mídias.", "Fotos e áudios ficam em arquivos locais; o banco guarda só metadados.") }
            } else {
                items(media) { MediaListCard(it, onUpdate = { updated -> viewModel.updateMedia(updated) }, onDelete = { viewModel.deleteMedia(it) }) }
            }
        }
    }
}

@Composable
private fun ExportScreen(viewModel: AppViewModel, projectId: Long, sessionId: Long?, modifier: Modifier, onBack: () -> Unit, onShare: (Intent, String?) -> Unit) {
    var selectedContent by remember { mutableStateOf(ExportContent.SpreadsheetsAndAllMedia) }
    var selectedFormat by remember { mutableStateOf(ExportFormat.Zip) }
    Scaffold(modifier = modifier, topBar = { OikosTopBar(if (sessionId == null) "Exportar projeto" else "Exportar expedição", onBack) }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(ui("Exportar dados"), style = MaterialTheme.typography.titleLarge)
            Text(if (sessionId == null) ui("Escopo: Projeto inteiro / planilha mãe consolidada") else ui("Escopo: Expedição atual"))
            Text(ui("Para não perder o vínculo entre fotos, áudios e registros, recomendamos exportar como ZIP completo."))
            HelpCard(
                "ZIP organizado",
                "O ZIP cria 01_planilha_principal, 02_tabelas_de_apoio, 03_sig_qgis e 04_midias. A planilha principal reúne as ocorrências do escopo escolhido com os campos Darwin Core e os campos configurados no projeto."
            )
            HelpCard("Trilhas GPS", "O ZIP completo inclui trilha_gps_pontos.csv e o GeoJSON inclui a trilha da expedição como LineString. No QGIS, estilize featureType=expeditionTrack em laranja para ver o percurso.")
            Text(ui("Conteúdo"), style = MaterialTheme.typography.titleMedium)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExportContent.entries.forEach { content ->
                    FilterChip(
                        selected = selectedContent == content,
                        onClick = { selectedContent = content },
                        label = {
                            Text(
                                when (content) {
                                    ExportContent.MediaOnly -> ui("Somente mídias")
                                    ExportContent.SpreadsheetsOnly -> ui("Apenas planilhas")
                                    ExportContent.SpreadsheetsAndPhotos -> ui("Planilhas + fotos")
                                    ExportContent.SpreadsheetsAndAudios -> ui("Planilhas + áudios")
                                    ExportContent.SpreadsheetsAndAllMedia -> ui("Planilhas + fotos + áudios")
                                }
                            )
                        }
                    )
                }
            }
            Text(ui("Formatos"), style = MaterialTheme.typography.titleMedium)
            val availableFormats = if (selectedContent == ExportContent.MediaOnly) {
                listOf(ExportFormat.Csv, ExportFormat.Xlsx, ExportFormat.Json, ExportFormat.Zip)
            } else {
                ExportFormat.entries
            }
            if (selectedFormat !in availableFormats) selectedFormat = ExportFormat.Zip
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                availableFormats.forEach { format ->
                    FilterChip(
                        selected = selectedFormat == format,
                        onClick = { selectedFormat = format },
                        label = { Text(if (format == ExportFormat.Zip) ui("ZIP completo recomendado") else format.name.uppercase()) }
                    )
                }
            }
            Button(
                modifier = Modifier.fillMaxWidth().height(56.dp),
                onClick = {
                    if (sessionId == null) viewModel.exportProject(projectId, selectedFormat, selectedContent, onShare)
                    else viewModel.exportSession(projectId, sessionId, selectedFormat, selectedContent, onShare)
                }
            ) {
                Icon(Icons.Default.Share, null)
                Spacer(Modifier.width(8.dp))
                Text(ui("Exportar e compartilhar/salvar"))
            }
            Text(
                ui("Depois de tocar no botão, escolha WhatsApp, Google Drive, Files/Arquivos ou outro app na tela de compartilhamento do Android."),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun InlineMediaCapture(
    viewModel: AppViewModel,
    projectId: Long,
    sessionId: Long,
    occurrenceId: Long?,
    modifier: Modifier = Modifier,
    occurrenceForName: MediaOccurrenceName? = null,
    pendingMedia: List<MediaEntity> = emptyList(),
    onPendingMediaAdded: (MediaEntity) -> Unit = {},
    onPendingMediaDeleted: (MediaEntity) -> Unit = {},
    onPhotoMetadata: (PhotoMetadata) -> Unit = {}
) {
    val project by viewModel.observeProject(projectId).collectAsState(initial = null)
    val session by viewModel.observeSession(sessionId).collectAsState(initial = null)
    val savedMedia by (occurrenceId?.let { viewModel.observeOccurrenceMedia(it) } ?: kotlinx.coroutines.flow.flowOf(emptyList())).collectAsState(initial = emptyList())
    val savedOccurrence by (occurrenceId?.let { viewModel.observeOccurrence(it) } ?: kotlinx.coroutines.flow.flowOf(null)).collectAsState(initial = null)
    val nameOccurrence = occurrenceForName ?: savedOccurrence?.toMediaOccurrenceName()
    val media = if (occurrenceId == null) pendingMedia else savedMedia
    var pendingPhoto by remember { mutableStateOf<File?>(null) }
    var recordingFile by remember { mutableStateOf<File?>(null) }
    var recordingFormat by remember { mutableStateOf<String?>(null) }
    var mediaSubject by remember { mutableStateOf("") }
    var mediaDescription by remember { mutableStateOf("") }
    val wavRecorder = remember { WavAudioRecorder() }
    val mp3Recorder = remember { PureMp3AudioRecorder() }
    val context = LocalContext.current

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val file = pendingPhoto
        val currentProject = project
        if (success && file != null && currentProject != null && session != null) {
            viewModel.mediaFiles.compressImage(file, currentProject.photoQuality)
            val metadata = readPhotoMetadata(file)
            onPhotoMetadata(metadata)
            val entity = mediaEntity(projectId, sessionId, occurrenceId, "photo", file, "image/jpeg", mediaSubject, mediaDescription, metadata)
            if (occurrenceId == null) onPendingMediaAdded(entity) else viewModel.addMedia(entity)
        }
    }
    val importPhoto = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val currentProject = project
        val currentSession = session
        if (uri != null && currentProject != null && currentSession != null) {
            val baseName = mediaBaseName(currentProject, currentSession, nameOccurrence, mediaSubject, "FOTO")
            val file = viewModel.mediaFiles.importUri(uri, currentProject.name, currentSession.name, "photo", "image/jpeg", baseName)
            val metadata = readPhotoMetadata(file)
            onPhotoMetadata(metadata)
            val entity = mediaEntity(projectId, sessionId, occurrenceId, "photo", file, "image/jpeg", mediaSubject, mediaDescription, metadata)
            if (occurrenceId == null) onPendingMediaAdded(entity) else viewModel.addMedia(entity)
        }
    }
    val importAudio = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val currentProject = project
        val currentSession = session
        if (uri != null && currentProject != null && currentSession != null) {
            val mime = viewModel.getApplication<OikosApplication>().contentResolver.getType(uri) ?: "audio/wav"
            val baseName = mediaBaseName(currentProject, currentSession, nameOccurrence, mediaSubject, "AUDIO")
            val file = viewModel.mediaFiles.importUri(uri, currentProject.name, currentSession.name, "audio", mime, baseName)
            val entity = mediaEntity(projectId, sessionId, occurrenceId, "audio", file, mime, mediaSubject, mediaDescription)
            if (occurrenceId == null) onPendingMediaAdded(entity) else viewModel.addMedia(entity)
        }
    }
    fun launchCameraCapture() {
        val baseName = mediaBaseName(project, session, nameOccurrence, mediaSubject, "FOTO")
        val file = viewModel.mediaFiles.createImageFile(project?.name ?: "Projeto", session?.name ?: "Expedição", baseName)
        pendingPhoto = file
        takePicture.launch(viewModel.mediaFiles.uriFor(file))
    }
    fun startAudioRecording() {
        if (!hasPermission(context, Manifest.permission.RECORD_AUDIO)) {
            viewModel.showMessage("Permissão de áudio negada.")
            return
        }
        val audioFormat = project?.audioFormat ?: "wav"
        val baseName = mediaBaseName(project, session, nameOccurrence, mediaSubject, "AUDIO")
        val file = viewModel.mediaFiles.createAudioFile(project?.name ?: "Projeto", session?.name ?: "Expedição", audioFormat, baseName)
        val started = runCatching {
            startAudioRecorder(audioFormat, file, mp3Recorder, wavRecorder)
        }.onFailure {
            file.delete()
            viewModel.showMessage(it.message ?: "Não foi possível iniciar a gravação de áudio.")
        }.isSuccess
        if (started) {
            recordingFormat = audioFormat
            recordingFile = file
        }
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCameraCapture() else viewModel.showMessage("Permissão de câmera negada.")
    }
    val audioPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startAudioRecording() else viewModel.showMessage("Permissão de áudio negada.")
    }

    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            HelpCard("Mídias vinculadas", "Fotos e áudios ficam ligados ao projeto, expedição e occurrenceID/eventID. A exportação preserva associatedMedia.")
            TextInput("O que esta mídia mostra ou registra?", mediaSubject, help = "Exemplos: indivíduo inteiro, folha, flor, microhabitat, canto reprodutivo, chamado territorial, observação narrada.") { mediaSubject = it }
            TextInput("Descrição da mídia", mediaDescription, minLines = 2) { mediaDescription = it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                Button(onClick = {
                    if (hasPermission(context, Manifest.permission.CAMERA)) launchCameraCapture()
                    else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }) { Icon(Icons.Default.CameraAlt, null); Spacer(Modifier.width(6.dp)); Text(ui("Foto")) }
                OutlinedButton(onClick = { importPhoto.launch("image/*") }) { Icon(Icons.Default.Photo, null); Spacer(Modifier.width(6.dp)); Text(ui("Anexar foto")) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                Button(onClick = {
                    if (recordingFormat == null) {
                        if (hasPermission(context, Manifest.permission.RECORD_AUDIO)) startAudioRecording()
                        else audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    } else {
                        if (recordingFormat == "mp3") mp3Recorder.stop() else wavRecorder.stop()
                        val finishedFormat = recordingFormat
                        recordingFormat = null
                        recordingFile?.let {
                            val mime = if (finishedFormat == "mp3") "audio/mpeg" else "audio/wav"
                            val entity = mediaEntity(projectId, sessionId, occurrenceId, "audio", it, mime, mediaSubject, mediaDescription)
                            if (occurrenceId == null) onPendingMediaAdded(entity) else viewModel.addMedia(entity)
                        }
                        recordingFile = null
                    }
                }) { Icon(Icons.Default.Mic, null); Spacer(Modifier.width(6.dp)); Text(if (recordingFormat == null) "Gravar ${project?.audioFormat?.uppercase() ?: "WAV"}" else "Parar") }
                OutlinedButton(onClick = { importAudio.launch("audio/*") }) { Icon(Icons.Default.FileUpload, null); Spacer(Modifier.width(6.dp)); Text(ui("Anexar áudio")) }
            }
            if (media.isEmpty()) {
                EmptyState("Sem mídias.", "Fotos e áudios ficam em arquivos locais; o banco guarda apenas metadados.")
            } else {
                media.forEach {
                    MediaListCard(
                        it,
                        onUpdate = { updated ->
                            if (occurrenceId == null) {
                                onPendingMediaDeleted(it)
                                onPendingMediaAdded(updated)
                            } else {
                                viewModel.updateMedia(updated)
                            }
                        },
                        onDelete = { if (occurrenceId == null) onPendingMediaDeleted(it) else viewModel.deleteMedia(it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MediaListCard(media: MediaEntity, onUpdate: (MediaEntity) -> Unit = {}, onDelete: () -> Unit) {
    var editing by remember(media.id, media.filePath) { mutableStateOf(false) }
    if (editing) {
        EditMediaDialog(
            media = media,
            onDismiss = { editing = false },
            onSave = {
                editing = false
                onUpdate(it)
            }
        )
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { editing = true }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (media.mediaType == "photo") {
                val bitmap = remember(media.filePath, media.createdAt) { decodeThumbnail(media.filePath, 180) }
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f), MaterialTheme.shapes.small),
                    contentAlignment = Alignment.Center
                ) {
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = ui("Miniatura da foto"),
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(Icons.Default.Photo, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Mic, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            Column(Modifier.weight(1f)) {
                Text("${media.mediaType}: ${media.fileName}", style = MaterialTheme.typography.titleSmall)
                if (media.caption.isNotBlank()) Text(media.caption, style = MaterialTheme.typography.bodySmall)
                Text(
                    media.mediaSubject.ifBlank { ui("Toque para descrever esta mídia.") },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
                Text(
                    if (media.occurrenceId == null) ui("Mídia geral da expedição") else ui("Mídia vinculada a uma ocorrência"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { editing = true }) {
                Icon(Icons.Default.Edit, ui("Editar mídia"))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, ui("Remover mídia"))
            }
        }
    }
}

@Composable
private fun EditMediaDialog(media: MediaEntity, onDismiss: () -> Unit, onSave: (MediaEntity) -> Unit) {
    var subject by remember(media.id, media.filePath) { mutableStateOf(media.mediaSubject) }
    var description by remember(media.id, media.filePath) { mutableStateOf(media.caption) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(ui("Editar mídia")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(media.fileName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextInput(
                    "O que esta mídia mostra ou registra?",
                    subject,
                    help = "Exemplos: indivíduo inteiro, construção/vestígio, folha, flor, microhabitat, canto reprodutivo, observação narrada."
                ) { subject = it }
                TextInput("Descrição da mídia", description, minLines = 2) { description = it }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(media.copy(mediaSubject = subject.trim(), caption = description.trim(), notes = description.trim()))
            }) { Text(ui("Salvar")) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(ui("Cancelar")) }
        }
    )
}

@Composable
private fun SettingsScreen(
    viewModel: AppViewModel,
    darkTheme: Boolean,
    simpleMode: Boolean,
    languageMode: String,
    onThemeChange: (Boolean) -> Unit,
    onSimpleModeChange: (Boolean) -> Unit,
    onLanguageModeChange: (String) -> Unit,
    modifier: Modifier,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val mapPrefs = remember { context.getSharedPreferences("oikos_map_settings", Context.MODE_PRIVATE) }
    var cache by remember { mutableLongStateOf(viewModel.mediaFiles.cacheSizeBytes()) }
    var mapCache by remember { mutableLongStateOf(viewModel.mediaFiles.mapCacheSizeBytes()) }
    var files by remember { mutableLongStateOf(viewModel.mediaFiles.projectFilesSizeBytes()) }
    var db by remember { mutableLongStateOf(viewModel.mediaFiles.databaseSizeBytes()) }
    var confirmClearCache by remember { mutableStateOf(false) }
    var confirmClearMapCache by remember { mutableStateOf(false) }
    var offlineMapCache by remember { mutableStateOf(mapPrefs.getBoolean("offline_map_cache", false)) }
    var offlineMapArea by remember { mutableStateOf(mapPrefs.getString("offline_map_area", "20") ?: "20") }
    var offlineMapDetail by remember { mutableStateOf(mapPrefs.getString("offline_map_detail", "field") ?: "field") }
    if (confirmClearCache) {
        ConfirmDeleteDialog(
            title = "Limpar cache?",
            text = "Isso apaga exportações temporárias e arquivos de apoio. Projetos, expedições, ocorrências, fotos e áudios não serão removidos.",
            confirmText = "Limpar cache",
            onDismiss = { confirmClearCache = false },
            onConfirm = {
                confirmClearCache = false
                val removed = viewModel.mediaFiles.clearCache()
                cache = viewModel.mediaFiles.cacheSizeBytes()
                mapCache = viewModel.mediaFiles.mapCacheSizeBytes()
                files = viewModel.mediaFiles.projectFilesSizeBytes()
                db = viewModel.mediaFiles.databaseSizeBytes()
                viewModel.showMessage(
                    if (removed > 0) "Cache limpo: ${humanSize(removed)} removidos." else "Cache já estava limpo."
                )
            }
        )
    }
    if (confirmClearMapCache) {
        ConfirmDeleteDialog(
            title = "Apagar cache do mapa?",
            text = "Isso remove apenas os mapas salvos em cache. Projetos, expedições, trilhas, ocorrências, fotos, áudios e exportações não serão removidos.",
            confirmText = "Apagar cache do mapa",
            onDismiss = { confirmClearMapCache = false },
            onConfirm = {
                confirmClearMapCache = false
                val removed = viewModel.mediaFiles.clearMapCache()
                mapCache = viewModel.mediaFiles.mapCacheSizeBytes()
                cache = viewModel.mediaFiles.cacheSizeBytes()
                viewModel.showMessage(
                    if (removed > 0) "Cache do mapa apagado: ${humanSize(removed)} removidos." else "Cache do mapa já estava vazio."
                )
            }
        )
    }
    Scaffold(modifier = modifier, topBar = { OikosTopBar("Configurações", onBack) }) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), elevation = CardDefaults.cardElevation(2.dp)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Crossfade(targetState = darkTheme, label = "themeLabel") { isDark ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(if (isDark) Icons.Default.DarkMode else Icons.Default.LightMode, null, tint = MaterialTheme.colorScheme.primary)
                                Text(if (isDark) ui("Escuro") else ui("Claro"), style = MaterialTheme.typography.titleMedium)
                            }
                        }
                        Text(ui("Tema de alto contraste para campo, com alternância sempre visível."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = darkTheme, onCheckedChange = onThemeChange)
                }
            }
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), elevation = CardDefaults.cardElevation(2.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(ui("Idioma do aplicativo"), style = MaterialTheme.typography.titleMedium)
                    Text(ui("Use automático para seguir o idioma do celular. Você também pode forçar português ou inglês manualmente."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ChipRow(
                        "Idioma",
                        listOf("auto" to "Automático", "pt" to "Português", "en" to "English"),
                        languageMode,
                        onLanguageModeChange
                    )
                }
            }
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), elevation = CardDefaults.cardElevation(2.dp)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(ui("Modo simples / coleta rápida"), style = MaterialTheme.typography.titleMedium)
                        Text(ui("Menos campos visíveis por padrão; ideal para sol forte, pressa e uso prolongado."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = simpleMode, onCheckedChange = onSimpleModeChange)
                }
            }
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), elevation = CardDefaults.cardElevation(2.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(ui("Mapas offline"), style = MaterialTheme.typography.titleMedium)
                            Text(ui("A trilha GPS continua funcionando sem internet. O mapa de fundo usa cache local quando a área já foi preparada ou visualizada."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = offlineMapCache,
                            onCheckedChange = {
                                offlineMapCache = it
                                mapPrefs.edit().putBoolean("offline_map_cache", it).apply()
                            }
                        )
                    }
                    AnimatedVisibility(visible = offlineMapCache) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            ChipRow(
                                "Área para manter em cache",
                                listOf("2" to "2 km²", "5" to "5 km²", "10" to "10 km²", "20" to "20 km²"),
                                offlineMapArea
                            ) {
                                offlineMapArea = it
                                mapPrefs.edit().putString("offline_map_area", it).apply()
                            }
                            ChipRow(
                                "Detalhe do mapa",
                                listOf("light" to "Leve", "field" to "Campo", "detailed" to "Detalhado"),
                                offlineMapDetail
                            ) {
                                offlineMapDetail = it
                                mapPrefs.edit().putString("offline_map_detail", it).apply()
                            }
                            Text(
                                ui("Use enquanto ainda houver internet para preparar a última área de trabalho. Por política do OpenStreetMap, o app evita downloads massivos do servidor público; para 100% offline em áreas grandes, use pacote offline importado em uma atualização futura."),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text("${ui("Cache do mapa")}: ${humanSize(mapCache)}")
                    OutlinedButton(onClick = { confirmClearMapCache = true }) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(ui("Apagar cache do mapa"))
                    }
                }
            }
            Text("${ui("Arquivos de projetos")}: ${humanSize(files)}")
            Text("${ui("Banco local")}: ${humanSize(db)}")
            Text("${ui("Cache/exportações temporárias")}: ${humanSize(cache)}")
            Button(onClick = { confirmClearCache = true }) { Text(ui("Limpar cache")) }
            DeveloperSignature(Modifier.fillMaxWidth().padding(top = 20.dp))
        }
    }
}

@Composable
private fun AboutScreen(modifier: Modifier, onBack: () -> Unit) {
    Scaffold(modifier = modifier, topBar = { OikosTopBar("Sobre", onBack) }) { padding ->
        LazyColumn(
            contentPadding = padding.plus(PaddingValues(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 32.dp)),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
            val brandLogo = if (MaterialTheme.colorScheme.background.luminance() > 0.5f) {
                R.drawable.oikos_brand_logo_light
            } else {
                R.drawable.oikos_brand_logo
            }
            Image(
                painter = painterResource(id = brandLogo),
                contentDescription = "Oikos FieldBook logo",
                modifier = Modifier.fillMaxWidth().height(160.dp),
                contentScale = ContentScale.Fit
            )
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Oikos FieldBook", style = MaterialTheme.typography.headlineSmall)
            Text(ui("Sobre o aplicativo"), style = MaterialTheme.typography.titleMedium)
                    Text(
                        ui("Produto desenvolvido para facilitar a rotina do pesquisador em campo, reunindo catalogação de ocorrências, fotos, áudios, coordenadas e trilhas GPS em um só lugar."),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        ui("Enquanto você coleta, o Oikos organiza os dados em Darwin Core e prepara exportações em CSV, XLSX, JSON, GeoJSON e ZIP para uso em Excel, QGIS, R, Python e acervos científicos. De pesquisador para pesquisador."),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(ui("Créditos"), style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Produto científico desenvolvido por Luan da Silva Cortes Maciel (Maciel, L. S. C.), mestrando no Instituto de Pesquisa Jardim Botânico do Rio de Janeiro, sob orientação de Leandro Freitas e coorientação de Matheus de Toledo Moroti.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(ui("Aplicativo vinculado à pesquisa:"), style = MaterialTheme.typography.labelLarge)
                    Text(
                        "\"Levantamento e Interações Ecológicas entre Anuros e Bromélias na RPPN Gigante do Itaguaré\".",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        ui("Mapas por © OpenStreetMap contributors."),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            }
            item {
            DeveloperSignature(Modifier.fillMaxWidth().padding(top = 20.dp))
            }
        }
    }
}

@Composable
private fun ui(portuguese: String): String =
    if (LocalOikosLanguage.current == OikosLanguage.Portuguese) portuguese else englishUiText(portuguese)

@Composable
private fun fieldDisplayLabel(field: FieldConfigEntity): String =
    if (LocalOikosLanguage.current == OikosLanguage.Portuguese) {
        prettyPortugueseText(field.labelPt)
    } else {
        field.labelEn.ifBlank { englishUiText(prettyPortugueseText(field.labelPt)) }
    }

private fun englishUiText(portuguese: String): String = englishText[portuguese] ?: portuguese

private val englishText = mapOf(
    "Voltar" to "Back",
    "Configurações" to "Settings",
    "Sobre" to "About",
    "Novo projeto" to "New project",
    "Editar projeto" to "Edit project",
    "Salvar" to "Save",
    "Idioma" to "Language",
    "Automático" to "Automatic",
    "Português" to "Portuguese",
    "Idioma do aplicativo" to "App language",
    "Use automático para seguir o idioma do celular. Você também pode forçar português ou inglês manualmente." to "Use automatic to follow the phone language. You can also force Portuguese or English manually.",
    "Caderno de Campo Digital" to "Digital Field Notebook",
    "Registre expedições, ocorrências, GPS, fotos e áudios com exportação Darwin Core." to "Record expeditions, occurrences, GPS, photos and audio with Darwin Core export.",
    "Criar projeto" to "Create project",
    "Abrir projeto" to "Open project",
    "Nenhum projeto ainda." to "No projects yet.",
    "Crie um projeto de pesquisa para começar a coletar offline." to "Create a research project to start collecting offline.",
    "Apagar projeto" to "Delete project",
    "Apagar projeto?" to "Delete project?",
    "Isso remove o projeto, suas expedições, ocorrências, trilhas e mídias salvas neste aparelho." to "This removes the project, its expeditions, occurrences, tracks and media saved on this device.",
    "Isso remove o projeto inteiro, incluindo expedições, ocorrências, trilhas e mídias locais." to "This removes the entire project, including expeditions, occurrences, tracks and local media.",
    "Nome do projeto" to "Project name",
    "Descrição" to "Description",
    "Instituição" to "Institution",
    "Responsáveis" to "Researchers",
    "Idioma" to "Language",
    "Português" to "Portuguese",
    "Datum padrão" to "Default datum",
    "Formato de coordenadas" to "Coordinate format",
    "Graus Decimais" to "Decimal Degrees",
    "Qualidade da foto" to "Photo quality",
    "Baixa" to "Low",
    "Media" to "Medium",
    "Alta" to "High",
    "Formato do áudio" to "Audio format",
    "Módulos do projeto" to "Project modules",
    "Escolha os grupos que este projeto pode registrar. Na ocorrência, o app mostra primeiro o grupo e depois apenas o questionário específico." to "Choose which groups this project can record. In an occurrence, the app first asks the group and then shows only that specific form.",
    "Animais observados, capturados, ouvidos, vestígios e armadilhas fotográficas." to "Observed, captured or heard animals, traces and camera traps.",
    "Flora, partes de plantas, flor, fruto, caule, folha e dados botânicos." to "Flora, plant parts, flowers, fruits, stems, leaves and botanical data.",
    "Funga, substrato, corpo de frutificação, associações e observações micológicas." to "Funga, substrate, fruiting bodies, associations and mycological notes.",
    "Fauna" to "Fauna",
    "Flora" to "Flora",
    "Funga" to "Funga",
    "Toque para recolher campos" to "Tap to collapse fields",
    "Toque para escolher campos deste módulo" to "Tap to choose fields for this module",
    "Salve o projeto para gerar estes campos." to "Save the project to generate these fields.",
    "Projeto" to "Project",
    "Editar" to "Edit",
    "Campos" to "Fields",
    "Exportar" to "Export",
    "Nova expedição" to "New expedition",
    "Expedições" to "Expeditions",
    "expedições" to "expeditions",
    "Sem expedições." to "No expeditions.",
    "Crie uma expedição para agrupar as ocorrências de campo." to "Create an expedition to group field occurrences.",
    "Apagar expedição" to "Delete expedition",
    "Apagar expedição?" to "Delete expedition?",
    "Isso remove a expedição, suas ocorrências, trilhas e mídias vinculadas." to "This removes the expedition, its occurrences, tracks and linked media.",
    "Isso remove a expedição atual, suas ocorrências, trilhas e mídias vinculadas." to "This removes the current expedition, its occurrences, tracks and linked media.",
    "Campos Darwin Core" to "Darwin Core Fields",
    "Obrig." to "Req.",
    "Nova ocorrência" to "New occurrence",
    "Editar ocorrência" to "Edit occurrence",
    "Nova expedição" to "New expedition",
    "Editar expedição" to "Edit expedition",
    "Nome da expedição" to "Expedition name",
    "Data" to "Date",
    "Selecionar data" to "Select date",
    "Selecionar hora" to "Select time",
    "Salvo no padrão científico AAAA-MM-DD para evitar confusão entre datas brasileiras e norte-americanas." to "Saved in the scientific YYYY-MM-DD standard to avoid confusion between Brazilian and North American date formats.",
    "Hora de início" to "Start time",
    "Hora de término" to "End time",
    "Localidade" to "Locality",
    "Município" to "Municipality",
    "Estado" to "State",
    "País" to "Country",
    "Habitat geral" to "General habitat",
    "Tipo de ambiente" to "Environment type",
    "Método de amostragem" to "Sampling method",
    "Esforço amostral" to "Sampling effort",
    "Tempo, distância ou método usado na coleta. Exemplo: 2 horas de busca ativa." to "Time, distance or method used in sampling. Example: 2 hours of active search.",
    "Equipe de campo" to "Field team",
    "Coletor principal" to "Main collector",
    "Registrar trilha da expedição" to "Record expedition track",
    "Salva o percurso geral da expedição para exportar como LineString." to "Saves the expedition route for export as a LineString.",
    "Observações gerais" to "General notes",
    "Expedição é uma saída de campo, dia de coleta ou campanha. Internamente, ela é exportada como Darwin Core Event." to "An expedition is a field trip, sampling day or campaign. Internally, it is exported as a Darwin Core Event.",
    "Expedição" to "Expedition",
    "Mídias" to "Media",
    "Exportar expedição" to "Export expedition",
    "Ocorrências" to "Occurrences",
    "registros nesta expedição" to "records in this expedition",
    "Registro" to "Record",
    "Sem ocorrências." to "No occurrences.",
    "Adicione espécies, coordenadas, fotos e áudios nesta expedição." to "Add species, coordinates, photos and audio to this expedition.",
    "Apagar ocorrência" to "Delete occurrence",
    "Apagar ocorrência?" to "Delete occurrence?",
    "Isso remove o registro e as fotos/áudios vinculados a ele." to "This removes the record and its linked photos/audio.",
    "Isso remove este registro e as fotos/áudios vinculados a ele." to "This removes this record and its linked photos/audio.",
    "Ocorrência sem nome científico" to "Occurrence without scientific name",
    "Trilha GPS da expedição" to "Expedition GPS track",
    "Uso de localização na trilha" to "Track location use",
    "Enquanto a trilha estiver ativa, o Oikos FieldBook usa sua localização para registrar o percurso da expedição, mesmo com a tela bloqueada ou o app em segundo plano. Uma notificação permanente será exibida. A localização é salva apenas no aparelho e nas exportações que você gerar." to "While the track is active, Oikos FieldBook uses your location to record the expedition route, even with the screen locked or the app in the background. A persistent notification will be shown. Location data is saved only on the device and in exports you create.",
    "Continuar e iniciar trilha" to "Continue and start track",
    "Atualizar agora" to "Update now",
    "Iniciar trilha" to "Start track",
    "Parar" to "Stop",
    "Abrir Maps" to "Open Maps",
    "Centralizar" to "Center",
    "Centralizar usuário" to "Center user",
    "Ver mapa" to "View map",
    "Fechar" to "Close",
    "Fechar mapa" to "Close map",
    "Importar trilha" to "Import track",
    "Mapa da expedição" to "Expedition map",
    "N do mapa" to "Map N",
    "norte do mapa" to "map north",
    "N verdadeiro" to "True N",
    "bússola do celular" to "phone compass",
    "Seta laranja: direção do usuário. N do mapa: topo do mapa. N verdadeiro: norte pela bússola do celular." to "Orange arrow: user heading. Map N: top of the map. True N: north from the phone compass.",
    "Seta laranja: direção do usuário. A bússola N mapa aponta o norte do mapa; gire com dois dedos para orientar a visualização." to "Orange arrow: user heading. The Map N compass points to map north; rotate with two fingers to orient the view.",
    "Mapa OpenStreetMap" to "OpenStreetMap map",
    "A trilha laranja aparece quando houver pontos de GPS." to "The orange track appears when GPS points are available.",
    "Grupo do registro" to "Record group",
    "Tipo de ocorrência" to "Occurrence type",
    "Data do registro" to "Record date",
    "Hora do registro" to "Record time",
    "Registro científico" to "Scientific record",
    "Identificador Darwin Core occurrenceID. Ele é gerado automaticamente e pode ficar oculto no uso comum." to "Darwin Core occurrenceID. It is generated automatically and can stay hidden during normal use.",
    "Registro de campo" to "Field record",
    "Código amigável do ponto ou registro. Exemplo: P03, T1-P03 ou Registro #0003." to "Friendly point or record code. Example: P03, T1-P03 or Record #0003.",
    "Nome da espécie" to "Species name",
    "Use o nome científico da espécie, se souber. Exemplo: Boana faber." to "Use the species scientific name, if known. Example: Boana faber.",
    "Nome popular" to "Common name",
    "Status da ocorrência" to "Occurrence status",
    "Número de indivíduos" to "Number of individuals",
    "Sexo" to "Sex",
    "Fase de vida" to "Life stage",
    "Comportamento" to "Behavior",
    "Condição reprodutiva" to "Reproductive condition",
    "Quem coletou" to "Recorded by",
    "Quem identificou" to "Identified by",
    "Data da identificação" to "Identification date",
    "Observações da ocorrência" to "Occurrence notes",
    "Anotações de campo" to "Field notes",
    "Use para anotações rápidas, descrição detalhada, dúvidas de identificação, observações de laboratório ou coleção." to "Use for quick notes, detailed description, identification questions, lab or collection notes.",
    "Ocultar campos avançados" to "Hide advanced fields",
    "Mostrar campos avançados" to "Show advanced fields",
    "Campos Darwin Core extras" to "Extra Darwin Core fields",
    "Mídias da ocorrência" to "Occurrence media",
    "Miniatura da foto" to "Photo thumbnail",
    "Registro de ocorrência" to "Occurrence record",
    "Registro é cada espécie, indivíduo, som, foto, vestígio ou observação feita em campo." to "A record is each species, individual, sound, photo, trace or observation made in the field.",
    "Registro é cada espécie, indivíduo, som, foto, vestígio ou observação feita dentro da expedição." to "A record is each species, individual, sound, photo, trace or observation made within the expedition.",
    "Mídias vinculadas" to "Linked media",
    "Use mídias para registrar fotos e áudios ligados a esta ocorrência antes de salvar." to "Use media to record photos and audio linked to this occurrence before saving.",
    "Use mídias para registrar fotos e áudios ligados a esta ocorrência. A exportação preserva occurrenceID/eventID e associatedMedia." to "Use media to record photos and audio linked to this occurrence. Export preserves occurrenceID/eventID and associatedMedia.",
    "Use mídias para registrar fotos e áudios ligados a esta ocorrência ou à expedição. A exportação preserva occurrenceID/eventID e associatedMedia." to "Use media to record photos and audio linked to this occurrence or expedition. Export preserves occurrenceID/eventID and associatedMedia.",
    "Esta galeria reúne fotos e áudios da expedição, incluindo mídias vinculadas aos registros. Toque em uma mídia para editar o que ela mostra e sua descrição individual." to "This gallery brings together photos and audio from the expedition, including media linked to records. Tap a media item to edit what it shows and its individual description.",
    "Fotos e áudios ficam ligados ao projeto, expedição e occurrenceID quando houver ocorrência." to "Photos and audio stay linked to the project, expedition and occurrenceID when an occurrence exists.",
    "Fotos e áudios ficam ligados ao projeto, expedição e occurrenceID/eventID. A exportação preserva associatedMedia." to "Photos and audio stay linked to the project, expedition and occurrenceID/eventID. Export preserves associatedMedia.",
    "Não foi possível iniciar a gravação de áudio." to "Could not start audio recording.",
    "Permissão de câmera negada." to "Camera permission denied.",
    "Permissão de áudio negada." to "Audio permission denied.",
    "Mídias da expedição" to "Expedition media",
    "O que esta mídia mostra ou registra?" to "What does this media show or record?",
    "Exemplos: indivíduo inteiro, folha, flor, microhabitat, canto reprodutivo, chamado territorial, observação narrada." to "Examples: whole individual, leaf, flower, microhabitat, reproductive call, territorial call, narrated observation.",
    "Exemplos: indivíduo inteiro, construção/vestígio, folha, flor, microhabitat, canto reprodutivo, observação narrada." to "Examples: whole individual, construction/trace, leaf, flower, microhabitat, reproductive call, narrated observation.",
    "Descrição da mídia" to "Media description",
    "Foto" to "Photo",
    "Anexar foto" to "Attach photo",
    "Anexar áudio" to "Attach audio",
    "Sem mídias." to "No media.",
    "Fotos e áudios ficam em arquivos locais; o banco guarda só metadados." to "Photos and audio stay as local files; the database stores only metadata.",
    "Fotos e áudios ficam em arquivos locais; o banco guarda apenas metadados." to "Photos and audio stay as local files; the database stores only metadata.",
    "Toque para descrever esta mídia." to "Tap to describe this media.",
    "Mídia geral da expedição" to "General expedition media",
    "Mídia vinculada a uma ocorrência" to "Media linked to an occurrence",
    "Editar mídia" to "Edit media",
    "Remover mídia" to "Remove media",
    "Exportar projeto" to "Export project",
    "Exportar dados" to "Export data",
    "Escopo: Projeto inteiro / planilha mãe consolidada" to "Scope: whole project / consolidated master sheet",
    "Escopo: Expedição atual" to "Scope: current expedition",
    "Para não perder o vínculo entre fotos, áudios e registros, recomendamos exportar como ZIP completo." to "To keep links between photos, audio and records, we recommend exporting as a complete ZIP.",
    "ZIP organizado" to "Organized ZIP",
    "O ZIP cria 01_planilha_principal, 02_tabelas_de_apoio, 03_sig_qgis e 04_midias. A planilha principal reúne as ocorrências do escopo escolhido com os campos Darwin Core e os campos configurados no projeto." to "The ZIP creates 01_planilha_principal, 02_tabelas_de_apoio, 03_sig_qgis and 04_midias. The main spreadsheet gathers occurrences from the selected scope with Darwin Core fields and the fields configured in the project.",
    "Trilhas GPS" to "GPS tracks",
    "O ZIP completo inclui trilha_gps_pontos.csv e o GeoJSON inclui a trilha da expedição como LineString. No QGIS, estilize featureType=expeditionTrack em laranja para ver o percurso." to "The complete ZIP includes trilha_gps_pontos.csv and the GeoJSON includes the expedition track as a LineString. In QGIS, style featureType=expeditionTrack in orange to see the route.",
    "Conteúdo" to "Content",
    "Somente mídias" to "Media only",
    "Apenas planilhas" to "Spreadsheets only",
    "Planilhas + fotos" to "Spreadsheets + photos",
    "Planilhas + áudios" to "Spreadsheets + audio",
    "Planilhas + fotos + áudios" to "Spreadsheets + photos + audio",
    "Formatos" to "Formats",
    "ZIP completo recomendado" to "Complete ZIP recommended",
    "Exportar e compartilhar/salvar" to "Export and share/save",
    "Depois de tocar no botão, escolha WhatsApp, Google Drive, Files/Arquivos ou outro app na tela de compartilhamento do Android." to "After tapping the button, choose WhatsApp, Google Drive, Files or another app in the Android share sheet.",
    "Compartilhar, salvar ou enviar exportação" to "Share, save or send export",
    "Escuro" to "Dark",
    "Claro" to "Light",
    "Tema de alto contraste para campo, com alternância sempre visível." to "High-contrast field theme with an always-visible toggle.",
    "Tema de alto contraste para campo, com alternancia sempre visivel." to "High-contrast field theme with an always-visible toggle.",
    "Modo simples / coleta rápida" to "Simple mode / quick collection",
    "Modo simples / coleta rapida" to "Simple mode / quick collection",
    "Menos campos visíveis por padrão; ideal para sol forte, pressa e uso prolongado." to "Fewer fields visible by default; ideal for strong sun, urgency and long field use.",
    "Mapas offline" to "Offline maps",
    "A trilha GPS continua funcionando sem internet. O mapa de fundo usa cache local quando a área já foi preparada ou visualizada." to "The GPS track keeps working without internet. The base map uses local cache when the area has already been prepared or viewed.",
    "Área para manter em cache" to "Area to keep cached",
    "Detalhe do mapa" to "Map detail",
    "Leve" to "Light",
    "Campo" to "Field",
    "Detalhado" to "Detailed",
    "Use enquanto ainda houver internet para preparar a última área de trabalho. Por política do OpenStreetMap, o app evita downloads massivos do servidor público; para 100% offline em áreas grandes, use pacote offline importado em uma atualização futura." to "Use this while internet is still available to prepare the latest work area. Due to OpenStreetMap policy, the app avoids mass downloads from the public server; for 100% offline use in large areas, use an imported offline package in a future update.",
    "Cache do mapa" to "Map cache",
    "Apagar cache do mapa" to "Clear map cache",
    "Apagar cache do mapa?" to "Clear map cache?",
    "Isso remove apenas os mapas salvos em cache. Projetos, expedições, trilhas, ocorrências, fotos, áudios e exportações não serão removidos." to "This removes only cached maps. Projects, expeditions, tracks, occurrences, photos, audio and exports will not be removed.",
    "Arquivos de projetos" to "Project files",
    "Banco local" to "Local database",
    "Cache/exportações temporárias" to "Cache/temporary exports",
    "Cache/exportacoes temporarias" to "Cache/temporary exports",
    "Limpar cache" to "Clear cache",
    "Limpar cache?" to "Clear cache?",
    "Isso apaga exportações temporárias e arquivos de apoio. Projetos, expedições, ocorrências, fotos e áudios não serão removidos." to "This deletes temporary exports and support files. Projects, expeditions, occurrences, photos and audio will not be removed.",
    "Cancelar" to "Cancel",
    "Sobre o aplicativo" to "About the app",
    "Produto desenvolvido para facilitar a rotina do pesquisador em campo, reunindo catalogação de ocorrências, fotos, áudios, coordenadas e trilhas GPS em um só lugar." to "A product designed to make field research easier by bringing occurrence cataloging, photos, audio, coordinates and GPS tracks together in one place.",
    "Enquanto você coleta, o Oikos organiza os dados em Darwin Core e prepara exportações em CSV, XLSX, JSON, GeoJSON e ZIP para uso em Excel, QGIS, R, Python e acervos científicos. De pesquisador para pesquisador." to "While you collect, Oikos organizes data in Darwin Core and prepares CSV, XLSX, JSON, GeoJSON and ZIP exports for Excel, QGIS, R, Python and scientific collections. From researcher to researcher.",
    "Créditos" to "Credits",
    "Aplicativo vinculado à pesquisa:" to "App linked to the research:",
    "Mapas por © OpenStreetMap contributors." to "Maps by © OpenStreetMap contributors.",
    "Ajuda" to "Help",
    "Entendi" to "Got it",
    "Coordenadas" to "Coordinates",
    "Precisão GPS" to "GPS accuracy",
    "Mostra o erro aproximado da localização em metros. Quanto menor, melhor. As exportações usam Graus Decimais." to "Shows the approximate location error in meters. Lower is better. Exports use Decimal Degrees.",
    "Preencha Zona, Hemisfério, Leste (Easting) e Norte (Northing). O app converte e salva internamente como latitude/longitude em Graus Decimais para Darwin Core e GeoJSON." to "Fill Zone, Hemisphere, Easting and Northing. The app converts and stores latitude/longitude internally as Decimal Degrees for Darwin Core and GeoJSON.",
    "Coordenada UTM inválida." to "Invalid UTM coordinate.",
    "Zona UTM" to "UTM zone",
    "Hemisfério" to "Hemisphere",
    "Sul" to "South",
    "Norte" to "North",
    "Leste (Easting)" to "Easting",
    "Norte (Northing)" to "Northing",
    "Latitude DD" to "Latitude DD",
    "Longitude DD" to "Longitude DD",
    "Precisão (m)" to "Accuracy (m)",
    "Erro aproximado da localização em metros. Quanto menor, melhor." to "Approximate location error in meters. Lower is better.",
    "Capturar localização atual" to "Capture current location",
    "Seções avançadas" to "Advanced sections",
    "Modo avançado opcional" to "Optional advanced mode",
    "Preencha apenas o que fizer sentido. Campos morfológicos, ambientais e medidas podem sair como MeasurementOrFact nas exportações futuras." to "Fill only what makes sense. Morphological, environmental and measurement fields can be exported as MeasurementOrFact in future exports.",
    "Use esta área para dados do animal observado, ouvido, capturado ou vestígio." to "Use this area for observed, heard, captured animal data or traces.",
    "Use esta área para detalhes botânicos, como folha, caule, flor e fruto." to "Use this area for botanical details such as leaf, stem, flower and fruit.",
    "Use esta área para pegadas, fezes, tocas, ninhos, penas, pelos, carcaças e rastros." to "Use this area for footprints, feces, burrows, nests, feathers, hair, carcasses and tracks.",
    "Use esta área para substrato, corpo de frutificação, associações, microhabitat e observações micológicas." to "Use this area for substrate, fruiting body, associations, microhabitat and mycological notes.",
    "Registre condições ambientais, ecologia, comportamento e observações para laboratório ou coleção." to "Record environmental conditions, ecology, behavior and lab or collection notes.",
    "Ambiente e anotações" to "Environment and notes",
    "Nenhum campo deste grupo está ativo. Ative campos em Configurar campos do projeto." to "No fields from this group are active. Enable fields in project field settings.",
    "DAP em centímetros. Ao preencher, o app calcula CAP = DAP x pi." to "DBH in centimeters. When filled, the app calculates CBH = DBH x pi.",
    "CAP em centímetros. Ao preencher, o app calcula DAP = CAP / pi." to "CBH in centimeters. When filled, the app calculates DBH = CBH / pi.",
    "Básicos" to "Basics",
    "Localização" to "Location",
    "Ocorrência" to "Occurrence",
    "Identificação" to "Identification",
    "Anotações" to "Notes",
    "Vestígio" to "Trace",
    "Animal observado" to "Animal observed",
    "Animal capturado" to "Animal captured",
    "Animal ouvido" to "Animal heard",
    "Vestígio animal" to "Animal trace",
    "Armadilha fotográfica" to "Camera trap",
    "Registro sonoro" to "Sound record",
    "Planta" to "Plant",
    "Parte de planta" to "Plant part",
    "Amostra botânica" to "Botanical sample",
    "Registro fotográfico botânico" to "Botanical photo record",
    "Vegetação/parcela" to "Vegetation/plot",
    "Corpo de frutificação" to "Fruiting body",
    "Micélio" to "Mycelium",
    "Amostra fúngica" to "Fungal sample",
    "Registro fotográfico de funga" to "Funga photo record",
    "Outro" to "Other"
)

@Composable
private fun AdaptiveContent(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.fillMaxWidth().widthIn(max = 760.dp)) {
            content()
        }
    }
}

@Composable
private fun DeveloperSignature(modifier: Modifier = Modifier) {
    Text(
        "Developed by: MACIEL, L. S. C.",
        modifier = modifier,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun HelpCard(title: String, text: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(ui(title), style = MaterialTheme.typography.titleMedium)
            Text(ui(text), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HelpButton(title: String, text: String) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        Icon(Icons.Default.HelpOutline, contentDescription = "${ui("Ajuda")}: $title")
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            confirmButton = { TextButton(onClick = { open = false }) { Text(ui("Entendi")) } },
            title = { Text(title) },
            text = { Text(text) }
        )
    }
}

@Composable
private fun ConfirmDeleteDialog(
    title: String,
    text: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(ui(title)) },
        text = { Text(ui(text)) },
        confirmButton = {
            Button(onClick = onConfirm) {
                Icon(Icons.Default.Delete, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(ui(confirmText))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(ui("Cancelar")) }
        }
    )
}

@Composable
private fun StatusBadge(text: String, active: Boolean) {
    val color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    Row(
        Modifier
            .clip(MaterialTheme.shapes.small)
            .background(color.copy(alpha = if (active) 0.18f else 0.10f))
            .border(1.dp, color.copy(alpha = 0.55f), MaterialTheme.shapes.small)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            if (active) Icons.Default.CheckCircle else Icons.Default.Warning,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(14.dp)
        )
        Text(ui(text), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun PhotoThumbnailStrip(photos: List<MediaEntity>) {
    if (photos.isEmpty()) return
    val visible = photos.take(3)
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 4.dp)
    ) {
        visible.forEach { media ->
            val bitmap = remember(media.filePath, media.createdAt) { decodeThumbnail(media.filePath, 180) }
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f), MaterialTheme.shapes.small),
                contentAlignment = Alignment.Center
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = ui("Miniatura da foto"),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(Icons.Default.Photo, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        val remaining = photos.size - visible.size
        if (remaining > 0) {
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                shape = MaterialTheme.shapes.small,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
            ) {
                Text(
                    "+$remaining",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 22.dp)
                )
            }
        }
    }
}

@Composable
private fun LocationFields(
    coordinateFormat: String,
    lat: String,
    lon: String,
    alt: String,
    accuracy: String,
    onLat: (String) -> Unit,
    onLon: (String) -> Unit,
    onAlt: (String) -> Unit,
    onAccuracy: (String) -> Unit,
    onCapture: () -> Unit
) {
    val isUtm = coordinateFormat == "UTM"
    val invalidUtmText = ui("Coordenada UTM inválida.")
    var utmZone by remember(coordinateFormat) { mutableStateOf("") }
    var utmHemisphere by remember(coordinateFormat) { mutableStateOf("S") }
    var utmEasting by remember(coordinateFormat) { mutableStateOf("") }
    var utmNorthing by remember(coordinateFormat) { mutableStateOf("") }
    var utmError by remember(coordinateFormat) { mutableStateOf<String?>(null) }

    fun syncUtmFromDecimal() {
        val utm = CoordinateUtils.toUtm(lat.toDoubleOrNull(), lon.toDoubleOrNull())
        if (utm != null) {
            utmZone = utm.zone.toString()
            utmHemisphere = if (utm.hemisphere == UtmHemisphere.South) "S" else "N"
            utmEasting = String.format(Locale.US, "%.3f", utm.easting)
            utmNorthing = String.format(Locale.US, "%.3f", utm.northing)
        }
    }

    fun syncDecimalFromUtm() {
        val zone = utmZone.toIntOrNull()
        val easting = utmEasting.replace(',', '.').toDoubleOrNull()
        val northing = utmNorthing.replace(',', '.').toDoubleOrNull()
        if (zone == null || easting == null || northing == null) {
            utmError = null
            return
        }
        val hemisphere = if (utmHemisphere == "N") UtmHemisphere.North else UtmHemisphere.South
        val decimal = CoordinateUtils.fromUtm(zone, hemisphere, easting, northing)
        if (decimal == null) {
            utmError = invalidUtmText
        } else {
            utmError = null
            onLat(String.format(Locale.US, "%.8f", decimal.latitude))
            onLon(String.format(Locale.US, "%.8f", decimal.longitude))
        }
    }

    LaunchedEffect(isUtm, lat, lon) {
        if (isUtm && lat.isNotBlank() && lon.isNotBlank()) syncUtmFromDecimal()
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(ui("Coordenadas"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        HelpButton("Precisão GPS", "Mostra o erro aproximado da localização em metros. Quanto menor, melhor. As exportações usam Graus Decimais.")
    }
    if (isUtm) {
        HelpCard(
            "UTM",
            "Preencha Zona, Hemisfério, Leste (Easting) e Norte (Northing). O app converte e salva internamente como latitude/longitude em Graus Decimais para Darwin Core e GeoJSON."
        )
        TextInput("Zona UTM", utmZone, KeyboardType.Number) {
            utmZone = it.filter { char -> char.isDigit() }.take(2)
            syncDecimalFromUtm()
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(ui("Hemisfério"), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = utmHemisphere == "S", onClick = {
                    utmHemisphere = "S"
                    syncDecimalFromUtm()
                }, label = { Text(ui("Sul")) })
                FilterChip(selected = utmHemisphere == "N", onClick = {
                    utmHemisphere = "N"
                    syncDecimalFromUtm()
                }, label = { Text(ui("Norte")) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextInput("Leste (Easting)", utmEasting, KeyboardType.Decimal, Modifier.weight(1f)) {
                utmEasting = it
                syncDecimalFromUtm()
            }
            TextInput("Norte (Northing)", utmNorthing, KeyboardType.Decimal, Modifier.weight(1f)) {
                utmNorthing = it
                syncDecimalFromUtm()
            }
        }
        utmError?.let {
            Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextInput("Latitude DD", lat, KeyboardType.Decimal, Modifier.weight(1f), onChange = onLat)
            TextInput("Longitude DD", lon, KeyboardType.Decimal, Modifier.weight(1f), onChange = onLon)
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextInput("Latitude", lat, KeyboardType.Decimal, Modifier.weight(1f), onChange = onLat)
            TextInput("Longitude", lon, KeyboardType.Decimal, Modifier.weight(1f), onChange = onLon)
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextInput("Altitude", alt, KeyboardType.Decimal, Modifier.weight(1f), onChange = onAlt)
        TextInput("Precisão (m)", accuracy, KeyboardType.Decimal, Modifier.weight(1f), help = "Erro aproximado da localização em metros. Quanto menor, melhor.", onChange = onAccuracy)
    }
    OutlinedButton(onClick = onCapture) {
        Icon(Icons.Default.GpsFixed, null)
        Spacer(Modifier.width(8.dp))
        Text(ui("Capturar localização atual"))
    }
}

@Composable
private fun TextInput(
    label: String,
    value: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    modifier: Modifier = Modifier.fillMaxWidth(),
    minLines: Int = 1,
    help: String? = null,
    onChange: (String) -> Unit
) {
    Column(modifier) {
        val displayLabel = ui(label)
        val displayHelp = help?.let { ui(it) }
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text(displayLabel) },
            modifier = Modifier.fillMaxWidth(),
            minLines = minLines,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            trailingIcon = { if (displayHelp != null) HelpButton(displayLabel, displayHelp) }
        )
        if (displayHelp != null) {
            Text(displayHelp, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun DateInput(label: String, value: String, onChange: (String) -> Unit) {
    val context = LocalContext.current
    val normalizedValue = remember(value) { normalizeDateInput(value) }
    fun openPicker() {
        val parsed = normalizedValue?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now()
        DatePickerDialog(
            context,
            { _, year, month, day ->
                onChange(LocalDate.of(year, month + 1, day).toString())
            },
            parsed.year,
            parsed.monthValue - 1,
            parsed.dayOfMonth
        ).show()
    }
    Column {
        Text(ui(label), style = MaterialTheme.typography.labelLarge)
        OutlinedButton(onClick = ::openPicker, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Edit, null)
            Spacer(Modifier.width(8.dp))
            Text(normalizedValue ?: value.ifBlank { ui("Selecionar data") })
        }
        Text(
            ui("Salvo no padrão científico AAAA-MM-DD para evitar confusão entre datas brasileiras e norte-americanas."),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

private fun normalizeDateInput(value: String): String? {
    val trimmed = value.trim()
    if (trimmed.isBlank()) return null
    runCatching { LocalDate.parse(trimmed).toString() }.getOrNull()?.let { return it }
    val slash = Regex("""^(\d{1,2})/(\d{1,2})/(\d{4})$""").matchEntire(trimmed)
    if (slash != null) {
        val (day, month, year) = slash.destructured
        return runCatching { LocalDate.of(year.toInt(), month.toInt(), day.toInt()).toString() }.getOrNull()
    }
    val ptMonths = mapOf(
        "jan" to 1, "janeiro" to 1,
        "fev" to 2, "fevereiro" to 2,
        "mar" to 3, "março" to 3, "marco" to 3,
        "abr" to 4, "abril" to 4,
        "mai" to 5, "maio" to 5,
        "jun" to 6, "junho" to 6,
        "jul" to 7, "julho" to 7,
        "ago" to 8, "agosto" to 8,
        "set" to 9, "setembro" to 9,
        "out" to 10, "outubro" to 10,
        "nov" to 11, "novembro" to 11,
        "dez" to 12, "dezembro" to 12
    )
    val text = Regex("""^(\d{1,2})\s+([A-Za-zÀ-ÿ]{3,})\.?\s+(\d{4})$""").matchEntire(trimmed.lowercase(Locale("pt", "BR")))
    if (text != null) {
        val (day, monthText, year) = text.destructured
        val month = ptMonths[monthText.removeSuffix(".")]
        if (month != null) return runCatching { LocalDate.of(year.toInt(), month, day.toInt()).toString() }.getOrNull()
    }
    return null
}

@Composable
private fun TimeInput(label: String, value: String, onChange: (String) -> Unit) {
    val context = LocalContext.current
    fun openPicker() {
        val parts = value.split(":")
        val now = LocalTime.now()
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: now.hour
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: now.minute
        TimePickerDialog(
            context,
            { _, selectedHour, selectedMinute ->
                onChange(String.format(Locale.US, "%02d:%02d", selectedHour, selectedMinute))
            },
            hour,
            minute,
            true
        ).show()
    }
    Column {
        Text(ui(label), style = MaterialTheme.typography.labelLarge)
        OutlinedButton(onClick = ::openPicker, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Edit, null)
            Spacer(Modifier.width(8.dp))
            Text(value.ifBlank { ui("Selecionar hora") })
        }
    }
}

@Composable
private fun ChipRow(title: String, options: List<Pair<String, String>>, selected: String, onSelected: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(ui(title), style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { (value, label) ->
                FilterChip(selected = selected == value, onClick = { onSelected(value) }, label = { Text(ui(label)) })
            }
        }
    }
}

@Composable
private fun AdvancedOccurrenceSections(formType: String, values: MutableMap<String, String>, fields: List<FieldConfigEntity>) {
    Text(ui("Seções avançadas"), style = MaterialTheme.typography.titleLarge)
    HelpCard("Modo avançado opcional", "Preencha apenas o que fizer sentido. Campos morfológicos, ambientais e medidas podem sair como MeasurementOrFact nas exportações futuras.")
    val availableGroups = buildList {
        if (formType == "Fauna") add("Animal")
        if (formType == "Botanica") add("Planta")
        if (formType == "Fungos") add("Fungo")
        if (formType == "Fauna") add("Vestigio")
        add("Ambiente")
    }
    var selectedGroup by remember(formType) { mutableStateOf(availableGroups.first()) }
    AdvancedGroupSelector(availableGroups, selectedGroup) { selectedGroup = it }
    if (selectedGroup == "Animal") {
        AdvancedFieldGroup("Animal", "Use esta área para dados do animal observado, ouvido, capturado ou vestígio.", configuredAdvancedFields(animalAdvancedFields, fields), values)
    }
    if (selectedGroup == "Planta") {
        AdvancedFieldGroup("Planta", "Use esta área para detalhes botânicos, como folha, caule, flor e fruto.", configuredAdvancedFields(plantAdvancedFields, fields), values)
    }
    if (selectedGroup == "Vestigio") {
        AdvancedFieldGroup("Vestígio", "Use esta área para pegadas, fezes, tocas, ninhos, penas, pelos, carcaças e rastros.", configuredAdvancedFields(traceAdvancedFields, fields), values)
    }
    if (selectedGroup == "Fungo") {
        AdvancedFieldGroup("Funga", "Use esta área para substrato, corpo de frutificação, associações, microhabitat e observações micológicas.", configuredAdvancedFields(fungiAdvancedFields, fields), values)
    }
    if (selectedGroup == "Ambiente") {
        AdvancedFieldGroup("Ambiente e anotações", "Registre condições ambientais, ecologia, comportamento e observações para laboratório ou coleção.", configuredAdvancedFields(noteAdvancedFields, fields), values)
    }
}

private fun moduleDisplayName(module: String): String = when (module) {
    "Botanica" -> "Flora"
    "Fungos" -> "Funga"
    else -> module
}

private fun advancedGroupDisplayName(group: String): String = when (group) {
    "Vestigio" -> "Vestígio"
    "Fungo" -> "Funga"
    else -> group
}

private fun fieldGroupDisplayName(group: String): String = when (group) {
    "Botanica" -> "Flora"
    "Fungos" -> "Funga"
    "Basicos" -> "Básicos"
    "Localizacao" -> "Localização"
    "Ocorrencia" -> "Ocorrência"
    "Identificacao" -> "Identificação"
    "Anotacoes" -> "Anotações"
    else -> advancedGroupDisplayName(group)
}

private fun prettyPortugueseText(value: String): String {
    val fixedEncoding = value
        .replace("\u00c3\u00a1", "á")
        .replace("\u00c3\u00a0", "à")
        .replace("\u00c3\u00a2", "â")
        .replace("\u00c3\u00a3", "ã")
        .replace("\u00c3\u00a9", "é")
        .replace("\u00c3\u00aa", "ê")
        .replace("\u00c3\u00ad", "í")
        .replace("\u00c3\u00b3", "ó")
        .replace("\u00c3\u00b4", "ô")
        .replace("\u00c3\u00b5", "õ")
        .replace("\u00c3\u00ba", "ú")
        .replace("\u00c3\u00a7", "ç")
        .replace("\u00c3\u0081", "Á")
        .replace("\u00c3\u0089", "É")
        .replace("\u00c3\u008d", "Í")
        .replace("\u00c3\u0093", "Ó")
        .replace("\u00c3\u009a", "Ú")
        .replace("\u00c3\u0087", "Ç")
    return when (fixedEncoding) {
        "Condicoes ambientais" -> "Condições ambientais"
        "Observacoes ecologicas" -> "Observações ecológicas"
        "Metodo de coleta" -> "Método de coleta"
        "Esforco amostral" -> "Esforço amostral"
        "Anotacoes de campo" -> "Anotações de campo"
        "Anotacoes rapidas" -> "Anotações rápidas"
        "Duvidas de identificacao" -> "Dúvidas de identificação"
        "Hipotese de identificacao" -> "Hipótese de identificação"
        "Data da identificacao" -> "Data da identificação"
        "Nome da especie" -> "Nome da espécie"
        "Genero" -> "Gênero"
        "Epiteto especifico" -> "Epíteto específico"
        "Pais" -> "País"
        "Datum geodesico" -> "Datum geodésico"
        else -> fixedEncoding
    }
}

@Composable
private fun AdvancedGroupSelector(groups: List<String>, selectedGroup: String, onSelected: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        groups.forEach { group ->
            FilterChip(
                selected = selectedGroup == group,
                onClick = { onSelected(group) },
                label = { Text(ui(advancedGroupDisplayName(group))) }
            )
        }
    }
}

@Composable
private fun AdvancedFieldGroup(title: String, description: String, fields: List<Pair<String, String>>, values: MutableMap<String, String>) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(ui(title), style = MaterialTheme.typography.titleMedium)
            Text(ui(description), color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (fields.isEmpty()) {
                Text(ui("Nenhum campo deste grupo está ativo. Ative campos em Configurar campos do projeto."), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            fields.forEach { (key, label) ->
                val help = when (key) {
                    "dbh" -> "DAP em centímetros. Ao preencher, o app calcula CAP = DAP x pi."
                    "cap" -> "CAP em centímetros. Ao preencher, o app calcula DAP = CAP / pi."
                    else -> null
                }
                TextInput(label, values[key].orEmpty(), help = help) {
                    values[key] = it
                    if (title == "Planta") updateBotanicalDiameterPair(key, it, values)
                }
            }
        }
    }
}

@Composable
private fun configuredAdvancedFields(defaults: List<Pair<String, String>>, fields: List<FieldConfigEntity>): List<Pair<String, String>> =
    defaults.filter { (key, _) -> enabled(key, fields) }.map { (key, fallback) -> key to label(key, fields, fallback) }

private fun updateBotanicalDiameterPair(key: String, value: String, values: MutableMap<String, String>) {
    val numeric = value.replace(',', '.').toDoubleOrNull() ?: return
    when (key) {
        "dbh" -> values["cap"] = formatBotanicalMeasure(numeric * Math.PI)
        "cap" -> values["dbh"] = formatBotanicalMeasure(numeric / Math.PI)
    }
}

private fun formatBotanicalMeasure(value: Double): String =
    String.format(java.util.Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')

@Composable
private fun EmptyState(title: String, text: String) {
    Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(ui(title), style = MaterialTheme.typography.titleMedium)
        Text(ui(text), style = MaterialTheme.typography.bodyMedium)
    }
}

private fun ProjectEntity.enabledOccurrenceModules(): List<String> = buildList {
    if (enableFauna) add("Fauna")
    if (enableBotany) add("Botanica")
    if (enableFungi) add("Fungos")
}

private fun occurrenceTypesFor(module: String): List<String> = when (module) {
    "Botanica" -> listOf(
        "Planta",
        "Parte de planta",
        "Amostra botânica",
        "Registro fotográfico botânico",
        "Vegetação/parcela",
        "Outro"
    )
    "Fungos" -> listOf(
        "Funga",
        "Corpo de frutificação",
        "Micélio",
        "Amostra fúngica",
        "Registro fotográfico de funga",
        "Outro"
    )
    else -> listOf(
        "Animal observado",
        "Animal capturado",
        "Animal ouvido",
        "Vestígio animal",
        "Armadilha fotográfica",
        "Registro sonoro",
        "Outro"
    )
}

private val animalAdvancedFields = listOf(
    "animalGroup" to "Grupo animal",
    "recordType" to "Tipo de registro",
    "activity" to "Atividade",
    "microhabitat" to "Microhabitat",
    "substrate" to "Substrato",
    "heightAboveGround" to "Altura em relação ao solo",
    "distanceToWater" to "Distância da água",
    "airTemperature" to "Temperatura do ar",
    "humidity" to "Umidade",
    "vocalizationPresent" to "Vocalização presente",
    "vocalizationType" to "Tipo de vocalização",
    "vocalizationContext" to "Contexto da vocalização",
    "callingIndividuals" to "Indivíduos vocalizando",
    "audioQuality" to "Qualidade do áudio",
    "backgroundNoise" to "Ruído de fundo",
    "behaviorNotes" to "Observações comportamentais"
)

private val plantAdvancedFields = listOf(
    "plantHabit" to "Hábito",
    "estimatedHeight" to "Altura estimada",
    "dbh" to "DAP",
    "cap" to "CAP",
    "flowerPresent" to "Presença de flor",
    "fruitPresent" to "Presença de fruto",
    "phenologicalState" to "Estado fenológico",
    "stemType" to "Tipo de caule",
    "bark" to "Casca",
    "latexPresent" to "Presença de látex",
    "leafType" to "Folha simples/composta",
    "phyllotaxy" to "Filotaxia",
    "leafMargin" to "Margem da folha",
    "leafApex" to "Ápice foliar",
    "leafBase" to "Base foliar",
    "venation" to "Nervação",
    "flowerColor" to "Cor da flor",
    "fruitType" to "Tipo de fruto",
    "plantPhotoParts" to "Partes fotografadas",
    "botanicalNotes" to "Observações botânicas"
)

private val traceAdvancedFields = listOf(
    "traceType" to "Tipo de vestígio",
    "probableSpecies" to "Espécie provável",
    "identificationConfidence" to "Confiança da identificação",
    "traceConservation" to "Estado de conservação do vestígio",
    "traceMeasurements" to "Medidas do vestígio",
    "movementDirection" to "Direção do deslocamento",
    "traceNotes" to "Observações do vestígio"
)

private val fungiAdvancedFields = listOf(
    "fungusSubstrate" to "Substrato",
    "fruitingBodyPresent" to "Corpo de frutificação presente",
    "fruitingBodyColor" to "Cor do corpo de frutificação",
    "fruitingBodyShape" to "Forma do corpo de frutificação",
    "capDiameter" to "Diâmetro do píleo/chapéu",
    "stipeLength" to "Comprimento do estipe",
    "gillOrPoreType" to "Lamelas, poros ou superfície fértil",
    "sporePrintColor" to "Cor da esporada",
    "hostOrAssociation" to "Hospedeiro ou associação",
    "decayStage" to "Estado de decomposição do substrato",
    "fungusMicrohabitat" to "Microhabitat",
    "fungiNotes" to "Observações micológicas"
)

private val noteAdvancedFields = listOf(
    "quickNotes" to "Anotações rápidas",
    "detailedDescription" to "Descrição detalhada",
    "environmentalConditions" to "Condições ambientais",
    "ecologicalNotes" to "Observações ecológicas",
    "identificationQuestions" to "Dúvidas de identificação",
    "identificationHypothesis" to "Hipótese de identificação",
    "labNotes" to "Observações para laboratório",
    "collectionNotes" to "Observações para herbário/coleção"
)

private fun enabled(term: String, fields: List<FieldConfigEntity>): Boolean = fields.any { it.dwcTerm == term && it.enabled }
@Composable
private fun label(term: String, fields: List<FieldConfigEntity>, fallback: String): String =
    fields.firstOrNull { it.dwcTerm == term }?.let { fieldDisplayLabel(it) } ?: ui(fallback)

private val advancedOccurrenceTerms = (
    animalAdvancedFields + plantAdvancedFields + traceAdvancedFields + fungiAdvancedFields + noteAdvancedFields
).map { it.first }.toSet()

private val fixedOccurrenceTerms = setOf(
    "scientificName",
    "vernacularName",
    "occurrenceStatus",
    "individualCount",
    "sex",
    "lifeStage",
    "behavior",
    "reproductiveCondition",
    "recordedBy",
    "identifiedBy",
    "dateIdentified",
    "eventDate",
    "eventTime",
    "decimalLatitude",
    "decimalLongitude",
    "coordinateUncertaintyInMeters",
    "geodeticDatum",
    "minimumElevationInMeters",
    "occurrenceRemarks",
    "associatedMedia"
)

private data class MediaOccurrenceName(
    val occurrenceID: String = "",
    val scientificName: String = "",
    val recordNumber: String = ""
)

private fun OccurrenceEntity.toMediaOccurrenceName(): MediaOccurrenceName =
    MediaOccurrenceName(occurrenceId, scientificName, recordNumber)

private fun occurrenceToName(occurrenceID: String, scientificName: String, recordNumber: String): MediaOccurrenceName =
    MediaOccurrenceName(occurrenceID, scientificName, recordNumber)

private fun semanticOccurrenceId(project: ProjectEntity?, session: SessionEntity?, sequence: Int): String {
    val area = projectAcronym(project?.name.orEmpty())
    val date = session?.eventDate?.replace("-", "").orEmpty().ifBlank {
        LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
    }
    val suffix = UUID.randomUUID().toString().take(8).uppercase()
    return "$area-$date-${sequence.coerceAtLeast(1).toString().padStart(4, '0')}-$suffix"
}

private fun mediaBaseName(
    project: ProjectEntity?,
    session: SessionEntity?,
    occurrence: OccurrenceEntity?,
    subject: String,
    type: String
): String = mediaBaseName(project, session, occurrence?.toMediaOccurrenceName(), subject, type)

private fun mediaBaseName(
    project: ProjectEntity?,
    session: SessionEntity?,
    occurrence: MediaOccurrenceName?,
    subject: String,
    type: String
): String {
    val now = Date()
    val date = session?.eventDate?.takeIf { it.isNotBlank() } ?: SimpleDateFormat("yyyy-MM-dd", Locale.US).format(now)
    val time = SimpleDateFormat("HHmm", Locale.US).format(now)
    val area = projectAcronym(project?.name.orEmpty())
    val expedition = shortCode(session?.name.orEmpty())
    val point = occurrence?.recordNumber?.ifBlank { occurrence.occurrenceID.takeLast(4) }.orEmpty()
    val species = shortCode(occurrence?.scientificName.orEmpty())
    val subjectCode = shortCode(subject).takeIf { it !in setOf("INDIVIDUO_INTEIRO", "OBSERVACAO_NARRADA") }.orEmpty()
    return listOf(date, time, area, expedition, point, species, subjectCode, type)
        .filter { it.isNotBlank() }
        .joinToString("_")
}

private fun projectAcronym(value: String): String {
    val clean = asciiWords(value)
    val compact = clean.filter { it.length > 2 }.joinToString("") { it.first().toString() }
    return compact.ifBlank { "OIKOS" }.take(8)
}

private fun shortCode(value: String): String =
    asciiWords(value).joinToString("_").ifBlank { "" }.take(28).trim('_')

private fun asciiWords(value: String): List<String> =
    Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace("\\p{Mn}+".toRegex(), "")
        .uppercase(Locale.US)
        .replace("[^A-Z0-9]+".toRegex(), " ")
        .trim()
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() && it !in setOf("DE", "DA", "DO", "DAS", "DOS", "E", "A", "O") }

private fun mediaEntity(projectId: Long, sessionId: Long, occurrenceId: Long?, type: String, file: File, mimeType: String, subject: String = "", description: String = "", metadata: PhotoMetadata? = null): MediaEntity =
    MediaEntity(
        projectId = projectId,
        sessionId = sessionId,
        occurrenceId = occurrenceId,
        mediaType = type,
        filePath = file.absolutePath,
        fileName = file.name,
        mimeType = mimeType,
        createdAt = metadata?.capturedAt ?: System.currentTimeMillis(),
        latitude = metadata?.latitude,
        longitude = metadata?.longitude,
        caption = description,
        mediaSubject = subject,
        bodyPartOrPlantPart = subject,
        behaviorContext = subject,
        notes = description
    )

private data class PhotoMetadata(
    val capturedAt: Long? = null,
    val latitude: Double? = null,
    val longitude: Double? = null
)

private fun readPhotoMetadata(file: File): PhotoMetadata {
    if (!file.exists()) return PhotoMetadata()
    return runCatching {
        val exif = ExifInterface(file.absolutePath)
        val latLong = FloatArray(2)
        val hasLatLong = exif.getLatLong(latLong)
        val capturedAt = listOf(
            ExifInterface.TAG_DATETIME_ORIGINAL,
            ExifInterface.TAG_DATETIME_DIGITIZED,
            ExifInterface.TAG_DATETIME
        ).firstNotNullOfOrNull { tag ->
            exif.getAttribute(tag)?.let { value ->
                runCatching {
                    SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).parse(value)?.time
                }.getOrNull()
            }
        }
        PhotoMetadata(
            capturedAt = capturedAt ?: file.lastModified().takeIf { it > 0L },
            latitude = if (hasLatLong) latLong[0].toDouble() else null,
            longitude = if (hasLatLong) latLong[1].toDouble() else null
        )
    }.getOrDefault(PhotoMetadata(capturedAt = file.lastModified().takeIf { it > 0L }))
}

@SuppressLint("MissingPermission")
private fun startAudioRecorder(
    audioFormat: String,
    file: File,
    mp3Recorder: PureMp3AudioRecorder,
    wavRecorder: WavAudioRecorder
) {
    if (audioFormat == "mp3") {
        mp3Recorder.start(file)
    } else {
        wavRecorder.start(file)
    }
}

private fun decodeThumbnail(path: String, targetSize: Int): android.graphics.Bitmap? =
    runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
        var sample = 1
        while ((bounds.outWidth / sample) > targetSize * 2 || (bounds.outHeight / sample) > targetSize * 2) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
        }
        BitmapFactory.decodeFile(path, options)
    }.getOrNull()

private fun humanSize(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return if (mb >= 1) String.format(java.util.Locale.US, "%.2f MB", mb) else String.format(java.util.Locale.US, "%.1f KB", kb)
}

private fun humanDistance(meters: Double): String =
    if (meters >= 1000.0) String.format(java.util.Locale.US, "%.2f km", meters / 1000.0)
    else String.format(java.util.Locale.US, "%.0f m", meters)

private operator fun PaddingValues.plus(other: PaddingValues): PaddingValues =
    PaddingValues(
        start = calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr) + other.calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
        top = calculateTopPadding() + other.calculateTopPadding(),
        end = calculateRightPadding(androidx.compose.ui.unit.LayoutDirection.Ltr) + other.calculateRightPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
        bottom = calculateBottomPadding() + other.calculateBottomPadding()
    )

