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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Backup
import com.oikos.fieldbook.audio.DecodedSpectrogram
import androidx.compose.material3.RadioButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material.icons.filled.Remove
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontStyle
import java.time.Instant
import java.time.ZoneId
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.oikos.fieldbook.services.PhotoMetadata
import com.oikos.fieldbook.services.AveragedFix
import com.oikos.fieldbook.services.FixQuality
import com.oikos.fieldbook.services.GpsAveraging
import com.oikos.fieldbook.services.GpsReading
import com.oikos.fieldbook.backup.BackupManifest
import com.oikos.fieldbook.checklist.ChecklistSource
import com.oikos.fieldbook.checklist.ChecklistSources
import com.oikos.fieldbook.checklist.RemoteVersion
import com.oikos.fieldbook.data.ChecklistEntity
import com.oikos.fieldbook.data.TaxonSuggestion
import androidx.compose.ui.focus.onFocusChanged
import com.oikos.fieldbook.ui.FormState
import com.oikos.fieldbook.ui.QuickEntry
import com.oikos.fieldbook.geo.CoordinateQuality
import com.oikos.fieldbook.geo.Geoid
import com.oikos.fieldbook.geo.Municipality
import com.oikos.fieldbook.ui.RepeatOption
import com.oikos.fieldbook.ui.rememberFormState
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
import com.oikos.fieldbook.dwc.DwcVocabulary
import com.oikos.fieldbook.export.AppCitation
import com.oikos.fieldbook.export.ExportContent
import com.oikos.fieldbook.export.ExportFormat
import com.oikos.fieldbook.services.CapturedLocation
import com.oikos.fieldbook.services.WavAudioRecorder
import com.oikos.fieldbook.ui.theme.OikosTheme
import com.oikos.fieldbook.util.CoordinateUtils
import com.oikos.fieldbook.util.UtmHemisphere
import com.oikos.fieldbook.util.Axis
import com.oikos.fieldbook.util.flatJson
import com.oikos.fieldbook.util.parseFlatJsonMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.tileprovider.modules.OfflineTileProvider
import org.osmdroid.tileprovider.util.SimpleRegisterReceiver
import com.oikos.fieldbook.services.OfflineMap
import com.oikos.fieldbook.services.TrashItem
import com.oikos.fieldbook.services.TrashService
import com.oikos.fieldbook.services.TrackProfile
import com.oikos.fieldbook.services.TransferManifest
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.key
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
            // Tema e modo simples passam a persistir entre aberturas, junto do
            // idioma, que ja persistia. Antes voltavam ao padrao a cada inicio
            // e a cada rotacao de tela.
            var darkTheme by remember { mutableStateOf(languagePrefs.getBoolean("dark_theme", true)) }
            var simpleMode by remember { mutableStateOf(languagePrefs.getBoolean("simple_mode", true)) }
            var languageMode by remember { mutableStateOf(languagePrefs.getString("app_language", "auto") ?: "auto") }
            var showLaunch by rememberSaveable { mutableStateOf(true) }
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
                                onThemeChange = {
                                    darkTheme = it
                                    languagePrefs.edit().putBoolean("dark_theme", it).apply()
                                },
                                onSimpleModeChange = {
                                    simpleMode = it
                                    languagePrefs.edit().putBoolean("simple_mode", it).apply()
                                },
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

    /**
     * App saindo da tela (nao rotacao): a gravacao de audio e encerrada e
     * salva. Em segundo plano o Android 9+ entrega silencio no lugar do som, e
     * a gravacao seguiria ocupando o microfone sem ninguem ver.
     */
    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            viewModel.stopRecording("Gravação encerrada e salva: o app saiu da tela.")
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

/** Mapa offline escolhido em Configuracoes; nulo = OpenStreetMap pela internet. */
private val LocalOfflineMap = compositionLocalOf<OfflineMap?> { null }

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

/**
 * Saver da navegacao.
 *
 * Sem isto o estado de tela vivia so em remember: girar o aparelho ou o Android
 * matar o processo em segundo plano (comum em campo, com a tela do mapa aberta
 * e pouca memoria) devolvia o usuario a lista de projetos. Serializa como lista
 * de primitivos, que e o que o Bundle aceita.
 */
private val ScreenSaver: Saver<Screen, Any> = listSaver(
    save = { tela ->
        when (tela) {
            is Screen.Projects -> listOf("projects")
            is Screen.ProjectEdit -> listOf("projectEdit", tela.projectId)
            is Screen.ProjectDetail -> listOf("projectDetail", tela.projectId)
            is Screen.FieldConfig -> listOf("fieldConfig", tela.projectId)
            is Screen.SessionEdit -> listOf("sessionEdit", tela.projectId, tela.sessionId)
            is Screen.SessionDetail -> listOf("sessionDetail", tela.projectId, tela.sessionId)
            is Screen.OccurrenceEdit -> listOf("occurrenceEdit", tela.projectId, tela.sessionId, tela.occurrenceId)
            is Screen.Media -> listOf("media", tela.projectId, tela.sessionId, tela.occurrenceId)
            is Screen.Export -> listOf("export", tela.projectId, tela.sessionId)
            is Screen.Settings -> listOf("settings")
            is Screen.About -> listOf("about")
            is Screen.Checklists -> listOf("checklists")
            is Screen.Tally -> listOf("tally", tela.projectId, tela.sessionId)
            is Screen.Trash -> listOf("trash")
        }
    },
    restore = { valores ->
        val id = valores.getOrNull(0) as? String
        fun longEm(indice: Int): Long = (valores.getOrNull(indice) as? Long) ?: 0L
        fun longOpcional(indice: Int): Long? = valores.getOrNull(indice) as? Long
        when (id) {
            "projectEdit" -> Screen.ProjectEdit(longOpcional(1))
            "projectDetail" -> Screen.ProjectDetail(longEm(1))
            "fieldConfig" -> Screen.FieldConfig(longEm(1))
            "sessionEdit" -> Screen.SessionEdit(longEm(1), longOpcional(2))
            "sessionDetail" -> Screen.SessionDetail(longEm(1), longEm(2))
            "occurrenceEdit" -> Screen.OccurrenceEdit(longEm(1), longEm(2), longOpcional(3))
            "media" -> Screen.Media(longEm(1), longEm(2), longOpcional(3))
            "export" -> Screen.Export(longEm(1), longOpcional(2))
            "settings" -> Screen.Settings
            "about" -> Screen.About
            "checklists" -> Screen.Checklists
            "tally" -> Screen.Tally(longEm(1), longEm(2))
            "trash" -> Screen.Trash
            else -> Screen.Projects
        }
    }
)

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
    data object Checklists : Screen
    data class Tally(val projectId: Long, val sessionId: Long) : Screen
    data object Trash : Screen
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
    var screen by rememberSaveable(stateSaver = ScreenSaver) { mutableStateOf<Screen>(Screen.Projects) }
    val snackbarHostState = remember { SnackbarHostState() }
    val message by viewModel.message.collectAsState()
    val context = LocalContext.current
    val shareChooserTitle = ui("Compartilhar, salvar ou enviar exportação")
    var pendingLocation by remember { mutableStateOf<((CapturedLocation?) -> Unit)?>(null) }
    val scope = rememberCoroutineScope()
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        // So as permissoes de localizacao contam: no Android 13+ a de notificacao
        // vem junto, e aceitar so ela levava a "Sem posicao de GPS agora".
        val locationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (locationGranted) {
            viewModel.captureLocation { pendingLocation?.invoke(it) }
        } else {
            viewModel.showMessage("Sem permissão de localização. Ative em Configurações do Android > Apps > Oikos FieldBook > Permissões.")
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

    // Com edge-to-edge, o teclado chega como inset: o conteudo encolhe acima
    // dele em vez de ficar escondido (sugestoes, botao Salvar).
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets.systemBars.union(WindowInsets.ime)
    ) { padding ->
        // Consumir os insets evita que a barra de cada tela desconte a barra de
        // status de novo (sobrava uma faixa vazia acima do titulo).
        val screenModifier = Modifier.padding(padding).consumeWindowInsets(padding)
        val offlineMap by viewModel.activeOfflineMap.collectAsState()
        CompositionLocalProvider(LocalOfflineMap provides offlineMap) {
        when (val current = screen) {
            Screen.Projects -> ProjectListScreen(
                viewModel = viewModel,
                modifier = screenModifier,
                onCreate = { screen = Screen.ProjectEdit() },
                onOpen = { screen = Screen.ProjectDetail(it) },
                onDeleteProject = { project -> viewModel.deleteProject(project) { } },
                onStorage = { screen = Screen.Settings },
                onAbout = { screen = Screen.About }
            )
            is Screen.ProjectEdit -> ProjectEditScreen(viewModel, current.projectId, screenModifier, { screen = Screen.Projects }) {
                screen = Screen.ProjectDetail(it)
            }
            is Screen.ProjectDetail -> ProjectDetailScreen(
                viewModel,
                current.projectId,
                screenModifier,
                onBack = { screen = Screen.Projects },
                onEdit = { screen = Screen.ProjectEdit(current.projectId) },
                onFields = { screen = Screen.FieldConfig(current.projectId) },
                onNewSession = { screen = Screen.SessionEdit(current.projectId) },
                onOpenSession = { screen = Screen.SessionDetail(current.projectId, it) },
                onExport = { screen = Screen.Export(current.projectId) },
                onDeleted = { screen = Screen.Projects }
            )
            is Screen.FieldConfig -> FieldConfigScreen(viewModel, current.projectId, screenModifier) {
                screen = Screen.ProjectDetail(current.projectId)
            }
            is Screen.SessionEdit -> SessionEditScreen(viewModel, current.projectId, current.sessionId, screenModifier, requestLocation, { screen = Screen.ProjectDetail(current.projectId) }) {
                screen = Screen.SessionDetail(current.projectId, it)
            }
            is Screen.SessionDetail -> SessionDetailScreen(
                viewModel,
                current.projectId,
                current.sessionId,
                screenModifier,
                onBack = { screen = Screen.ProjectDetail(current.projectId) },
                onEdit = { screen = Screen.SessionEdit(current.projectId, current.sessionId) },
                onNewOccurrence = { screen = Screen.OccurrenceEdit(current.projectId, current.sessionId) },
                onTally = { screen = Screen.Tally(current.projectId, current.sessionId) },
                onOpenOccurrence = { screen = Screen.OccurrenceEdit(current.projectId, current.sessionId, it) },
                onMedia = { screen = Screen.Media(current.projectId, current.sessionId, null) },
                onExport = { screen = Screen.Export(current.projectId, current.sessionId) },
                onDeleted = { screen = Screen.ProjectDetail(current.projectId) },
                requestLocation = requestLocation
            )
            is Screen.OccurrenceEdit -> OccurrenceEditScreen(viewModel, current.projectId, current.sessionId, current.occurrenceId, simpleMode, screenModifier, requestLocation, { screen = Screen.SessionDetail(current.projectId, current.sessionId) }, onDeleted = { screen = Screen.SessionDetail(current.projectId, current.sessionId) })
            is Screen.Media -> MediaScreen(viewModel, current.projectId, current.sessionId, current.occurrenceId, screenModifier) {
                screen = if (current.occurrenceId == null) Screen.SessionDetail(current.projectId, current.sessionId) else Screen.OccurrenceEdit(current.projectId, current.sessionId, current.occurrenceId)
            }
            is Screen.Export -> ExportScreen(viewModel, current.projectId, current.sessionId, screenModifier, onBack = {
                screen = current.sessionId?.let { Screen.SessionDetail(current.projectId, it) } ?: Screen.ProjectDetail(current.projectId)
            }) { intent, warning ->
                warning?.let { scope.launch { snackbarHostState.showSnackbar(it) } }
                context.startActivity(Intent.createChooser(intent, shareChooserTitle))
            }
            Screen.Settings -> SettingsScreen(viewModel, darkTheme, simpleMode, languageMode, onThemeChange, onSimpleModeChange, onLanguageModeChange, screenModifier, onChecklists = { screen = Screen.Checklists }, onTrash = { screen = Screen.Trash }) { screen = Screen.Projects }
            Screen.Checklists -> ChecklistsScreen(viewModel, screenModifier) { screen = Screen.Settings }
            Screen.Trash -> TrashScreen(viewModel, screenModifier) { screen = Screen.Settings }
            is Screen.Tally -> TallyScreen(viewModel, current.projectId, current.sessionId, screenModifier, requestLocation) {
                screen = Screen.SessionDetail(current.projectId, current.sessionId)
            }
            Screen.About -> AboutScreen(screenModifier) { screen = Screen.Projects }
        }
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
    Screen.Checklists -> Screen.Settings
    is Screen.Tally -> Screen.SessionDetail(projectId, sessionId)
    Screen.Trash -> Screen.Settings
}

/**
 * O conteudo encolhe acima do teclado; sem isto o botao Salvar flutuaria por
 * cima do campo em digitacao. Fechar o teclado traz o botao de volta.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HideWithKeyboard(content: @Composable () -> Unit) {
    if (!WindowInsets.isImeVisible) content()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OikosTopBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable () -> Unit = {}) {
    CenterAlignedTopAppBar(
        // Uma linha com reticencias: em tela de 320 dp o titulo quebrava letra
        // a letra ("Exped / icao / 2026...") espremido pelos icones.
        title = { Text(ui(title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, ui("Voltar")) }
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
    val lastBackupAt by viewModel.lastBackupAt.collectAsState()
    var projectToDelete by remember { mutableStateOf<ProjectEntity?>(null) }
    projectToDelete?.let { project ->
        ConfirmDeleteDialog(
            title = "Apagar projeto?",
            text = "O projeto, com expedições, ocorrências, trilhas e mídias, vai para a lixeira e pode ser restaurado por 30 dias em Configurações > Lixeira.",
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
        }
    ) { padding ->
        // Sem botao flutuante: o "Criar projeto" grande ja fica no topo, e o
        // botao flutuante cobria os selos do cartao em telas de 320 dp.
        LazyColumn(contentPadding = padding.plus(PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 32.dp)), verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
                            ImportPackageButton(viewModel, projects, onImported = onOpen)
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
            if (projects.isNotEmpty()) {
                item { BackupReminder(lastBackupAt, onBackup = onStorage) }
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
    val existing by remember(viewModel, projectId) { projectId?.let { viewModel.observeProject(it) } ?: kotlinx.coroutines.flow.flowOf(null) }.collectAsState(initial = null)
    val savedFields by remember(viewModel, projectId) { projectId?.let { viewModel.observeFields(it) } ?: kotlinx.coroutines.flow.flowOf(emptyList()) }.collectAsState(initial = emptyList())
    // Todo o formulario, inclusive a escolha de campos de um projeto novo,
    // sobrevive a rotacao e ao encerramento do app em segundo plano.
    val form = rememberFormState("project:${projectId ?: "new"}")
    LaunchedEffect(existing, projectId) {
        if (form.initialized) return@LaunchedEffect
        if (projectId == null) {
            form.load(
                mapOf(
                    "datum" to "SIRGAS 2000", "coordFormat" to "DD", "photoQuality" to "medium", "audioFormat" to "wav",
                    "enableFauna" to "true", "enableBotany" to "false", "enableFungi" to "false", "csvStyle" to "br"
                )
            )
        } else {
            val project = existing ?: return@LaunchedEffect
            form.load(
                mapOf(
                    "name" to project.name, "description" to project.description, "institution" to project.institution,
                    "pi" to project.principalInvestigator, "datum" to project.defaultDatum, "coordFormat" to project.coordinateFormat,
                    "photoQuality" to project.photoQuality, "audioFormat" to project.audioFormat,
                    "enableFauna" to project.enableFauna.toString(), "enableBotany" to project.enableBotany.toString(),
                    "enableFungi" to project.enableFungi.toString(), "license" to project.license,
                    "mediaBaseUrl" to project.mediaBaseUrl, "csvStyle" to project.csvStyle
                )
            )
        }
    }
    var name by form.field("name")
    var description by form.field("description")
    var institution by form.field("institution")
    var pi by form.field("pi")
    var mediaBaseUrl by form.field("mediaBaseUrl")
    val enableFauna = form.bool("enableFauna", true)
    val enableBotany = form.bool("enableBotany", false)
    val enableFungi = form.bool("enableFungi", false)
    var openFieldGroup by rememberSaveable { mutableStateOf<String?>(null) }

    // Projeto novo: escolhas por termo guardadas no formulario como "ligado,obrigatorio".
    val draftFields = DarwinCoreCatalog.allFields.map { definition ->
        val choice = form.values["field:${definition.term}"]?.split(",")
        FieldConfigEntity(
            projectId = 0,
            dwcTerm = definition.term,
            labelPt = definition.labelPt,
            labelEn = definition.labelEn,
            enabled = choice?.getOrNull(0)?.let { it == "1" } ?: definition.enabled,
            required = choice?.getOrNull(1)?.let { it == "1" } ?: definition.required,
            fieldType = definition.type,
            groupName = definition.group,
            displayOrder = definition.order
        )
    }
    val fields = if (projectId == null) draftFields else savedFields

    LaunchedEffect(projectId) {
        projectId?.let { viewModel.ensureFieldCatalog(it) }
    }

    fun updateProjectField(field: FieldConfigEntity) {
        if (projectId == null) {
            form["field:${field.dwcTerm}"] = "${if (field.enabled) 1 else 0},${if (field.required) 1 else 0}"
        } else {
            viewModel.updateField(field)
        }
    }

    val guardedBack = rememberGuardedBack(form.dirty, onBack = onBack)
    var savingProject by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = { OikosTopBar(if (projectId == null) "Novo projeto" else "Editar projeto", guardedBack) },
        floatingActionButton = { HideWithKeyboard {
            FloatingActionButton(onClick = {
                if (savingProject) return@FloatingActionButton
                if (name.isBlank()) {
                    viewModel.showMessage("Dê um nome ao projeto antes de salvar.")
                    return@FloatingActionButton
                }
                val base = existing ?: ProjectEntity(name = name.trim(), language = defaultProjectLanguage)
                savingProject = true
                viewModel.saveProject(
                    base.copy(
                        name = name.trim(),
                        description = description,
                        institution = institution,
                        principalInvestigator = pi,
                        defaultDatum = form["datum"].ifBlank { "SIRGAS 2000" },
                        coordinateFormat = form["coordFormat"].ifBlank { "DD" },
                        photoQuality = form["photoQuality"].ifBlank { "medium" },
                        // Gravacao so em WAV: o MP3 nao tinha codificador no Android
                        // e perde informacao do sinal.
                        audioFormat = "wav",
                        enableFauna = enableFauna,
                        enableBotany = enableBotany,
                        enableFungi = enableFungi,
                        license = form["license"],
                        mediaBaseUrl = mediaBaseUrl.trim(),
                        csvStyle = form["csvStyle"].ifBlank { "br" }
                    ),
                    onFailed = { savingProject = false }
                ) { savedId ->
                    savingProject = false
                    if (projectId == null) {
                        viewModel.saveFieldConfiguration(savedId, draftFields.map { it.copy(projectId = savedId) })
                    }
                    form.dirty = false
                    onSaved(savedId)
                }
            }) { Icon(Icons.Default.Save, ui("Salvar")) }
        } }
    ) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TextInput("Nome do projeto", name) { name = it }
            TextInput("Descrição", description, minLines = 3) { description = it }
            TextInput("Instituição", institution) { institution = it }
            TextInput("Responsáveis", pi, help = "Nome de quem responde pelos dados. Vai como autor e contato no eml.xml do Darwin Core Archive.") { pi = it }
            ChipRow("Datum padrão", listOf("SIRGAS 2000" to "SIRGAS 2000", "WGS84" to "WGS84"), form["datum"]) { form["datum"] = it }
            ChipRow("Formato de coordenadas", listOf("DD" to "Graus Decimais", "UTM" to "UTM", "DMS" to "DMS", "DMM" to "DMM"), form["coordFormat"]) { form["coordFormat"] = it }
            ChipRow("Qualidade da foto", listOf("low" to "Baixa", "medium" to "Media", "high" to "Alta"), form["photoQuality"]) { form["photoQuality"] = it }
            Text(ui("Publicação dos dados"), style = MaterialTheme.typography.titleMedium)
            ChipRow(
                "Licença dos dados",
                listOf("" to "Definir depois", "CC-BY-4.0" to "CC BY 4.0", "CC0-1.0" to "CC0", "CC-BY-NC-4.0" to "CC BY-NC 4.0"),
                form["license"]
            ) { form["license"] = it }
            Text(
                ui("O GBIF e o SiBBr só publicam com CC0, CC BY ou CC BY-NC. CC BY 4.0 permite qualquer uso, desde que citem a fonte."),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextInput(
                "URL pública das mídias (opcional)",
                mediaBaseUrl,
                keyboardType = KeyboardType.Uri,
                autoCorrect = false,
                help = "Endereço onde as fotos e áudios exportados ficarão publicados (Zenodo, site do laboratório). Sem ele, o Darwin Core Archive sai sem a extensão de mídia, porque o padrão exige link público."
            ) { mediaBaseUrl = it }
            ChipRow(
                "Planilha CSV",
                listOf("br" to "Excel Brasil (; e vírgula)", "intl" to "Internacional (, e ponto)"),
                form["csvStyle"]
            ) { form["csvStyle"] = it }
            Text(ui("Módulos do projeto"), style = MaterialTheme.typography.titleMedium)
            Text(ui("Escolha os grupos que este projeto pode registrar. Na ocorrência, o app mostra primeiro o grupo e depois apenas o questionário específico."), color = MaterialTheme.colorScheme.onSurfaceVariant)
            ProjectModuleFieldCard("Fauna", ui("Animais observados, capturados, ouvidos, vestígios e armadilhas fotográficas."), enableFauna, fields, openFieldGroup == "Fauna", onToggle = { form.setBool("enableFauna", it) }, onOpen = { openFieldGroup = if (openFieldGroup == "Fauna") null else "Fauna" }, onFieldChange = ::updateProjectField)
            ProjectModuleFieldCard("Botanica", ui("Flora, partes de plantas, flor, fruto, caule, folha e dados botânicos."), enableBotany, fields, openFieldGroup == "Botanica", onToggle = { form.setBool("enableBotany", it) }, onOpen = { openFieldGroup = if (openFieldGroup == "Botanica") null else "Botanica" }, onFieldChange = ::updateProjectField)
            ProjectModuleFieldCard("Fungos", ui("Funga, substrato, corpo de frutificação, associações e observações micológicas."), enableFungi, fields, openFieldGroup == "Fungos", onToggle = { form.setBool("enableFungi", it) }, onOpen = { openFieldGroup = if (openFieldGroup == "Fungos") null else "Fungos" }, onFieldChange = ::updateProjectField)
        }
    }
}

/**
 * Voltar com alteracoes nao salvas pergunta antes de descartar. Vale para o
 * botao de voltar do aparelho e para a seta da barra superior.
 */
@Composable
private fun rememberGuardedBack(dirty: Boolean, onDiscard: () -> Unit = {}, onBack: () -> Unit): () -> Unit {
    var asking by remember { mutableStateOf(false) }
    if (asking) {
        AlertDialog(
            onDismissRequest = { asking = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(ui("Descartar alterações?")) },
            text = { Text(ui("O que foi preenchido nesta tela ainda não foi salvo.")) },
            confirmButton = {
                TextButton(onClick = {
                    asking = false
                    onDiscard()
                    onBack()
                }) { Text(ui("Descartar")) }
            },
            dismissButton = {
                Button(onClick = { asking = false }) { Text(ui("Continuar editando")) }
            }
        )
    }
    BackHandler(enabled = dirty) { asking = true }
    return { if (dirty) asking = true else onBack() }
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
    val project by remember(viewModel, projectId) { viewModel.observeProject(projectId) }.collectAsState(initial = null)
    val sessions by remember(viewModel, projectId) { viewModel.observeSessions(projectId) }.collectAsState(initial = emptyList())
    var deleteProject by remember { mutableStateOf(false) }
    var sessionToDelete by remember { mutableStateOf<SessionEntity?>(null) }
    if (deleteProject) {
        val currentProject = project
        ConfirmDeleteDialog(
            title = "Apagar projeto?",
            text = "O projeto, com expedições, ocorrências, trilhas e mídias, vai para a lixeira e pode ser restaurado por 30 dias em Configurações > Lixeira.",
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
            text = "A expedição, com ocorrências, trilhas e mídias, vai para a lixeira e pode ser restaurada por 30 dias em Configurações > Lixeira.",
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
                Text("${sessions.size} ${ui(if (sessions.size == 1) "expedição" else "expedições")} - Datum ${project?.defaultDatum ?: "SIRGAS 2000"}")
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
    val fields by remember(viewModel, projectId) { viewModel.observeFields(projectId) }.collectAsState(initial = emptyList())
    LaunchedEffect(projectId) {
        viewModel.ensureFieldCatalog(projectId)
    }
    Scaffold(modifier = modifier, topBar = { OikosTopBar("Campos Darwin Core", onBack) }) { padding ->
        LazyColumn(contentPadding = padding.plus(PaddingValues(16.dp)), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Agrupa pelo nome exibido, nao pela chave crua: o catalogo herdou
            // variantes com e sem acento do mesmo grupo ("Identificacao" e
            // "Identificação"), que apareciam como duas secoes identicas.
            // Normalizar aqui resolve sem migrar groupName no banco, o que
            // quebraria o vinculo dos cards de modulo e do formType gravado.
            fields.groupBy { fieldGroupDisplayName(it.groupName) }
                .toList()
                .sortedBy { (group, _) -> fieldGroupOrder.indexOf(group).let { if (it < 0) Int.MAX_VALUE else it } }
                .forEach { (group, groupFields) ->
                item { Text(ui(group), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
                items(groupFields.sortedBy { it.displayOrder }) { field ->
                    Card {
                        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                            Text(fieldDisplayLabel(field), style = MaterialTheme.typography.bodyLarge)
                            Text("${field.labelEn} -> ${field.dwcTerm}", style = MaterialTheme.typography.labelMedium)
                            // Dois interruptores com rotulo proprio: antes o segundo
                            // ("ativo") nao tinha nome e parecia repeticao do primeiro.
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                Text(ui("Ativo"), style = MaterialTheme.typography.labelLarge)
                                Spacer(Modifier.width(6.dp))
                                Switch(checked = field.enabled, onCheckedChange = { viewModel.updateField(field.copy(enabled = it, required = field.required && it)) })
                                Spacer(Modifier.width(16.dp))
                                Text(ui("Obrigatório"), style = MaterialTheme.typography.labelLarge)
                                Spacer(Modifier.width(6.dp))
                                Switch(checked = field.required, onCheckedChange = { viewModel.updateField(field.copy(required = it, enabled = field.enabled || it)) })
                            }
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
    val project by remember(viewModel, projectId) { viewModel.observeProject(projectId) }.collectAsState(initial = null)
    val existing by remember(viewModel, sessionId) { sessionId?.let { viewModel.observeSession(it) } ?: kotlinx.coroutines.flow.flowOf(null) }.collectAsState(initial = null)
    val projectSessions by remember(viewModel, projectId) { viewModel.observeSessions(projectId) }.collectAsState(initial = emptyList())
    val form = rememberFormState("session:$projectId:${sessionId ?: "new"}")
    LaunchedEffect(existing, project, sessionId) {
        if (form.initialized) return@LaunchedEffect
        if (sessionId == null) {
            val currentProject = project ?: return@LaunchedEffect
            form.load(
                mapOf(
                    "eventId" to AppViewModel.newEventId(),
                    "date" to LocalDate.now().toString(),
                    "startTime" to LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")),
                    "country" to "Brasil",
                    "fieldTeam" to currentProject.principalInvestigator
                )
            )
            // Expedicao nova: captura a posicao inicial uma vez.
            // So se o pesquisador ainda nao preencheu: sob mata a primeira leitura
            // pode levar meio minuto e sobrescrevia a coordenada digitada ou a media.
            requestLocation { location -> location?.let { if (form["lat"].isBlank() && form["lon"].isBlank()) applyLocation(form, it) } }
        } else {
            val session = existing ?: return@LaunchedEffect
            form.load(
                mapOf(
                    "name" to session.name, "eventId" to session.eventId, "date" to session.eventDate,
                    "startTime" to session.startTime, "endTime" to session.endTime, "locality" to session.locality,
                    "municipality" to session.municipality, "stateProvince" to session.stateProvince,
                    "country" to session.country, "habitat" to session.habitat, "environmentType" to session.environmentType,
                    "protocol" to session.samplingProtocol, "effort" to session.samplingEffort,
                    "recordedBy" to session.recordedBy, "fieldTeam" to session.fieldTeam,
                    "trackEnabled" to session.gpsTrackEnabled.toString(),
                    "lat" to (session.latitude?.toString() ?: ""), "lon" to (session.longitude?.toString() ?: ""),
                    "alt" to (session.altitude?.toString() ?: ""), "accuracy" to (session.coordinateUncertaintyInMeters?.toString() ?: ""),
                    "notes" to session.notes, "parentEventId" to session.parentEventId,
                    "sampleSizeValue" to (session.sampleSizeValue?.let { formatPlain(it) } ?: ""),
                    "sampleSizeUnit" to session.sampleSizeUnit, "verbatimEventDate" to session.verbatimEventDate
                )
            )
        }
    }
    var name by form.field("name")
    var eventId by form.field("eventId")
    var date by form.field("date")
    var startTime by form.field("startTime")
    var endTime by form.field("endTime")
    var locality by form.field("locality")
    var municipality by form.field("municipality")
    var stateProvince by form.field("stateProvince")
    var country by form.field("country")
    var habitat by form.field("habitat")
    var environmentType by form.field("environmentType")
    var protocol by form.field("protocol")
    var effort by form.field("effort")
    var recordedBy by form.field("recordedBy")
    var fieldTeam by form.field("fieldTeam")
    var lat by form.field("lat")
    var lon by form.field("lon")
    var alt by form.field("alt")
    var accuracy by form.field("accuracy")
    var notes by form.field("notes")
    var parentEventId by form.field("parentEventId")
    var sampleSizeValue by form.field("sampleSizeValue")
    var sampleSizeUnit by form.field("sampleSizeUnit")
    var verbatimEventDate by form.field("verbatimEventDate")
    val trackEnabled = form.bool("trackEnabled", false)
    var showSampling by rememberSaveable { mutableStateOf(false) }

    val guardedBack = rememberGuardedBack(form.dirty, onBack = onBack)

    var savingSession by remember { mutableStateOf(false) }
    var sessionLocationError by remember { mutableStateOf<String?>(null) }
    Scaffold(
        modifier = modifier,
        topBar = { OikosTopBar(if (sessionId == null) "Nova expedição" else "Editar expedição", guardedBack) },
        floatingActionButton = { HideWithKeyboard {
            FloatingActionButton(onClick = {
                if (savingSession) return@FloatingActionButton
                sessionLocationError?.let {
                    viewModel.showMessage(it)
                    return@FloatingActionButton
                }
                coordinateInputProblem(lat, lon, alt, accuracy)?.let {
                    viewModel.showMessage(it)
                    return@FloatingActionButton
                }
                val sizeValue = sampleSizeValue.replace(',', '.').toDoubleOrNull()
                if (sampleSizeValue.isNotBlank() && sizeValue == null) {
                    viewModel.showMessage("Tamanho da unidade amostral precisa ser um número.")
                    return@FloatingActionButton
                }
                // Edicao parte do registro salvo: campos que esta tela nao mostra
                // sao preservados. Antes o registro era recriado do zero.
                val base = existing ?: SessionEntity(projectId = projectId, eventId = eventId, name = "")
                val session = base.copy(
                    eventId = eventId.trim(),
                    name = name.trim().ifBlank { "Expedição $date" },
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
                    parentEventId = parentEventId,
                    sampleSizeValue = sizeValue,
                    sampleSizeUnit = sampleSizeUnit,
                    verbatimEventDate = verbatimEventDate,
                    recordedBy = recordedBy,
                    fieldTeam = fieldTeam,
                    gpsTrackEnabled = trackEnabled,
                    latitude = lat.replace(',', '.').toDoubleOrNull(),
                    longitude = lon.replace(',', '.').toDoubleOrNull(),
                    altitude = alt.replace(',', '.').toDoubleOrNull(),
                    coordinateUncertaintyInMeters = accuracy.replace(',', '.').toDoubleOrNull(),
                    // O datum de uma expedicao existente nao muda se o padrao do
                    // projeto mudar depois: a coordenada foi tomada naquele datum.
                    geodeticDatum = existing?.geodeticDatum ?: project?.defaultDatum ?: "SIRGAS 2000",
                    notes = notes
                )
                savingSession = true
                viewModel.saveSession(session, onFailed = { savingSession = false }) {
                    savingSession = false
                    form.dirty = false
                    onSaved(it)
                }
            }) { Icon(Icons.Default.Save, ui("Salvar")) }
        } }
    ) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            HelpCard("Expedição", "Expedição é uma saída de campo, dia de coleta ou campanha. Internamente ela continua como Darwin Core Event.")
            TextInput("Nome da expedição", name) { name = it }
            TextInput("eventID", eventId, autoCorrect = false) { eventId = it }
            DateInput("Data", date) { date = it }
            TimeInput("Hora de início", startTime) { startTime = it }
            TimeInput("Hora de término", endTime) { endTime = it }
            TextInput("Localidade", locality) { locality = it }
            TextInput("Município", municipality) { municipality = it }
            TextInput("Estado", stateProvince) { stateProvince = it }
            TextInput("País", country) { country = it }
            TextInput("Habitat geral", habitat) { habitat = it }
            TextInput("Tipo de ambiente", environmentType) { environmentType = it }
            // Modelos: o nome padronizado do protocolo e a unidade amostral; o
            // esforco continua digitado, com um exemplo do proprio protocolo.
            val preset = SAMPLING_PRESETS.firstOrNull { it.protocol == protocol }
            ChipRow("Protocolo (modelos)", SAMPLING_PRESETS.map { it.protocol to it.label }, protocol) { chosen ->
                SAMPLING_PRESETS.firstOrNull { it.protocol == chosen }?.let { picked ->
                    protocol = picked.protocol
                    if (sampleSizeUnit.isBlank() && picked.unit.isNotBlank()) sampleSizeUnit = picked.unit
                }
            }
            TextInput("Método de amostragem", protocol) { protocol = it }
            TextInput(
                "Esforço amostral",
                effort,
                help = preset?.effortExample?.let { "${ui("Exemplo para este protocolo")}: $it" }
                    ?: "Tempo, distância ou método usado na coleta. Exemplo: 2 horas de busca ativa."
            ) { effort = it }
            TextInput("Equipe de campo", fieldTeam) { fieldTeam = it }
            TextInput("Coletor principal", recordedBy) { recordedBy = it }
            OutlinedButton(onClick = { showSampling = !showSampling }) {
                Text(if (showSampling) ui("Ocultar amostragem quantitativa") else ui("Amostragem quantitativa (parcela, transecto)"))
            }
            AnimatedVisibility(visible = showSampling || parentEventId.isNotBlank() || sampleSizeValue.isNotBlank()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    HelpCard(
                        "Unidade amostral",
                        "Para inventário quantitativo: indique a expedição de que esta faz parte (campanha > dia > parcela) e o tamanho da unidade amostral. Sem isso não se calcula densidade nem abundância relativa a partir da exportação."
                    )
                    val parents = projectSessions.filter { it.id != sessionId }
                    ChipRow(
                        "Faz parte de (parentEventID)",
                        listOf("" to "Nenhuma") + parents.map { it.eventId to it.name.ifBlank { it.eventId } },
                        parentEventId
                    ) { parentEventId = it }
                    TextInput("Tamanho da unidade amostral (sampleSizeValue)", sampleSizeValue, KeyboardType.Decimal) { sampleSizeValue = it }
                    DwcVocabularyInput(
                        label = ui("Unidade (sampleSizeUnit)"),
                        value = sampleSizeUnit,
                        options = DwcVocabulary.sampleSizeUnit
                    ) { sampleSizeUnit = it }
                    TextInput(
                        "Data como anotada (verbatimEventDate)",
                        verbatimEventDate,
                        help = "Exatamente como está na caderneta ou etiqueta, por exemplo \"3-5.iii.2026\"."
                    ) { verbatimEventDate = it }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(ui("Registrar trilha da expedição"), style = MaterialTheme.typography.titleMedium)
                    Text(ui("Salva o percurso geral da expedição para exportar como LineString."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = trackEnabled, onCheckedChange = { form.setBool("trackEnabled", it) })
            }
            var showGps by rememberSaveable { mutableStateOf(false) }
            val context = LocalContext.current
            if (showGps) {
                GpsCaptureDialog(viewModel, onDismiss = { showGps = false }) { fix ->
                    showGps = false
                    form["lat"] = fix.latitude.toString()
                    form["lon"] = fix.longitude.toString()
                    form["alt"] = fix.altitude?.let { formatPlain(it) }.orEmpty()
                    form["accuracy"] = formatPlain(fix.uncertainty)
                }
            }
            LocationFields(project?.coordinateFormat ?: "DD", lat, lon, alt, accuracy, { lat = it }, { lon = it }, { alt = it }, { accuracy = it }, onInputError = { sessionLocationError = it }) {
                if (hasAnyLocationPermission(context)) showGps = true
                else requestLocation { location -> location?.let { applyLocation(form, it) } }
            }
            CoordinateCheckPanel(viewModel, lat, lon, stateProvince, municipality, autoFill = true) { found ->
                municipality = found.name
                stateProvince = CoordinateQuality.ufFullName[found.uf] ?: found.uf
                if (country.isBlank()) country = "Brasil"
            }
            TextInput("Observações gerais", notes, minLines = 3) { notes = it }
        }
    }
}

/** Protocolo de amostragem comum, com a unidade amostral do Darwin Core. */
private data class SamplingPreset(val label: String, val protocol: String, val unit: String, val effortExample: String?)

/**
 * Nomes padronizados evitam que a mesma tecnica apareca escrita de cinco jeitos
 * na planilha ("busca ativa", "BA", "procura visual"...), o que impede somar o
 * esforco entre expedicoes.
 */
private val SAMPLING_PRESETS = listOf(
    SamplingPreset("Busca ativa", "Busca ativa visual e auditiva", "hour", "2 pessoas × 3 h"),
    SamplingPreset("Ponto de escuta", "Ponto de escuta (contagem por pontos)", "minute", "10 min por ponto, 8 pontos"),
    SamplingPreset("Transecto", "Transecto linear", "metre", "500 m percorridos em 1 h"),
    SamplingPreset("Parcela", "Parcela", "squareMetre", "parcela de 10 × 10 m"),
    SamplingPreset("Pitfall", "Armadilhas de interceptação e queda (pitfall)", "trapNight", "20 baldes × 5 noites"),
    SamplingPreset("Gravador automático", "Monitoramento acústico passivo (gravador automático)", "minute", "1 min a cada 15 min, 5 noites"),
    SamplingPreset("Coleta botânica", "Coleta de material botânico", "", null)
)

/**
 * Municipio do ponto pela malha do IBGE, sem internet, e os avisos de
 * qualidade da coordenada. [onUseMunicipality] (na expedicao) oferece preencher
 * municipio e estado; com [autoFill], preenche sozinho quando estao vazios e o
 * ponto nao esta perto de divisa.
 */
@Composable
private fun CoordinateCheckPanel(
    viewModel: AppViewModel,
    lat: String,
    lon: String,
    declaredUf: String,
    declaredMunicipality: String,
    autoFill: Boolean = false,
    onUseMunicipality: ((Municipality) -> Unit)? = null
) {
    val latitude = lat.replace(',', '.').toDoubleOrNull()?.takeIf { it in -90.0..90.0 }
    val longitude = lon.replace(',', '.').toDoubleOrNull()?.takeIf { it in -180.0..180.0 }
    var report by remember { mutableStateOf<CoordinateQuality.Report?>(null) }
    LaunchedEffect(latitude, longitude, declaredUf, declaredMunicipality) {
        if (latitude == null || longitude == null) {
            report = null
            return@LaunchedEffect
        }
        delay(300) // espera terminar de digitar
        val result = viewModel.coordinateReport(latitude, longitude, declaredUf, declaredMunicipality)
        report = result
        val found = result?.place?.municipality
        if (autoFill && onUseMunicipality != null && found != null && result.place.nearest == null &&
            declaredUf.isBlank() && declaredMunicipality.isBlank()
        ) {
            onUseMunicipality(found)
        }
    }
    val current = report ?: return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        current.place?.municipality?.let { found ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Place, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "${found.label} · ${ui("malha do IBGE 2022")}",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f)
                )
                val alreadyFilled = CoordinateQuality.normalize(declaredMunicipality) == CoordinateQuality.normalize(found.name) &&
                    CoordinateQuality.ufCode(declaredUf) == found.uf
                if (onUseMunicipality != null && !alreadyFilled) {
                    TextButton(onClick = { onUseMunicipality(found) }) { Text(ui("Usar")) }
                }
            }
        }
        current.issues.forEach { issue ->
            Text(
                issue.message,
                style = MaterialTheme.typography.labelMedium,
                color = when (issue.level) {
                    CoordinateQuality.Level.ERROR -> MaterialTheme.colorScheme.error
                    CoordinateQuality.Level.WARNING -> Color(0xFFE65100)
                    CoordinateQuality.Level.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

/**
 * Preenche coordenada e, se a geocodificacao estiver ligada, municipio e estado.
 * A incerteza segue o guia do GBIF: o dobro da precisao informada pelo GPS.
 */
private fun applyLocation(form: FormState, location: CapturedLocation) {
    form["lat"] = location.latitude.toString()
    form["lon"] = location.longitude.toString()
    form["alt"] = location.altitude?.let { formatPlain(it) }.orEmpty()
    form["accuracy"] = formatPlain(GpsAveraging.uncertaintyFor(location.accuracyMeters))
    if (form["municipality"].isBlank() && location.municipality.isNotBlank()) form["municipality"] = location.municipality
    if (form["stateProvince"].isBlank() && location.stateProvince.isNotBlank()) form["stateProvince"] = location.stateProvince
    if ((form["country"].isBlank() || form["country"] == "Brasil") && location.country.isNotBlank()) form["country"] = location.country
}

/** 12.0 -> "12", 12.5 -> "12.5": sem ".0" sobrando nos campos. */
private fun formatPlain(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

@Composable
private fun SessionDetailScreen(
    viewModel: AppViewModel,
    projectId: Long,
    sessionId: Long,
    modifier: Modifier,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onNewOccurrence: () -> Unit,
    onTally: () -> Unit,
    onOpenOccurrence: (Long) -> Unit,
    onMedia: () -> Unit,
    onExport: () -> Unit,
    onDeleted: () -> Unit,
    requestLocation: ((CapturedLocation?) -> Unit) -> Unit
) {
    val session by remember(viewModel, sessionId) { viewModel.observeSession(sessionId) }.collectAsState(initial = null)
    val occurrences by remember(viewModel, sessionId) { viewModel.observeOccurrences(sessionId) }.collectAsState(initial = emptyList())
    val media by remember(viewModel, sessionId) { viewModel.observeSessionMedia(sessionId) }.collectAsState(initial = emptyList())
    val tracks by remember(viewModel, sessionId) { viewModel.observeTracks(sessionId) }.collectAsState(initial = emptyList())
    val activeTrack = tracks.firstOrNull { it.active }
    val displayTrack = activeTrack ?: tracks.firstOrNull()
    val trackPoints by remember(viewModel, displayTrack?.id) { displayTrack?.let { viewModel.observeTrackPoints(it.id) } ?: kotlinx.coroutines.flow.flowOf(emptyList()) }.collectAsState(initial = emptyList())
    val context = LocalContext.current
    var deleteSession by remember { mutableStateOf(false) }
    var occurrenceToDelete by remember { mutableStateOf<OccurrenceEntity?>(null) }
    var currentLocation by remember { mutableStateOf<CapturedLocation?>(null) }
    val importTrackLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val currentSession = session
        if (uri != null && currentSession != null) {
            viewModel.importTrackFile(currentSession, uri)
        }
    }
    // Posicao ao vivo para o mapa, so enquanto a tela esta visivel. Antes eram
    // capturas completas (com consulta de endereco pela internet) ao abrir a
    // tela e a cada 3 s com o mapa grande aberto. locationToken reinicia a
    // coleta depois que o usuario concede a permissao.
    var locationToken by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(sessionId, locationToken, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.locationUpdates().collect { currentLocation = it }
        }
    }
    if (deleteSession) {
        val currentSession = session
        ConfirmDeleteDialog(
            title = "Apagar expedição?",
            text = "A expedição, com ocorrências, trilhas e mídias, vai para a lixeira e pode ser restaurada por 30 dias em Configurações > Lixeira.",
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
            text = "O registro e suas fotos e áudios vão para a lixeira e podem ser restaurados por 30 dias em Configurações > Lixeira.",
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
                Text(
                    "${occurrences.size} ${ui(if (occurrences.size == 1) "registro nesta expedição" else "registros nesta expedição")}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
                OutlinedButton(onClick = onTally, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(50.dp)) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(6.dp))
                    Text(ui("Contagem rápida (ponto, transecto)"))
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
                        requestLocation { location ->
                            if (location != null) currentLocation = location
                            locationToken++
                        }
                    },
                    onStart = {
                        session?.let { currentSession ->
                            // Sob mata a primeira posicao pode demorar ou nao vir: a trilha
                            // comeca assim mesmo e o servico grava os pontos quando houver sinal.
                            requestLocation { location ->
                                viewModel.startTrack(currentSession) { trackId ->
                                    location?.let { viewModel.addTrackPoint(trackId, it) }
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
                    onExport = onExport,
                    profile = viewModel.trackProfile
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
                                Text(listOf(occurrence.vernacularName, occurrence.recordedBy, dwcValueLabel(occurrence.occurrenceStatus)).filter { it.isNotBlank() }.joinToString(" - "))
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
    onExport: () -> Unit,
    profile: TrackProfile = TrackProfile.PRECISE
) {
    var showLargeMap by remember { mutableStateOf(false) }
    var recenterToken by remember { mutableStateOf(0) }
    var showTrackDisclosure by remember { mutableStateOf(false) }
    val bearingState = rememberCompassBearing()
    val bearing: () -> Float = remember(bearingState) { { bearingState.value } }
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
                        if (isActive) ui("Registrando em segundo plano. A linha laranja é a trilha base; pontos das ocorrências aparecem por cima.") else ui("Inicie a trilha para acompanhar o percurso contínuo da expedição."),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusBadge(if (isActive) ui("Ativa") else ui("Parada"), isActive)
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
                    Text(if (isActive) ui("Atualizar agora") else ui("Iniciar trilha"))
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
                    "Último ponto: ${formatClock(lastPoint.timestamp)} - precisão ${lastPoint.accuracy?.let { "±${it.toInt()} m" } ?: "não informada"}. Atualização automática aproximada: ${profile.intervalMillis / 1000} s ou ${formatPlain(profile.minDistanceMeters.toDouble()).replace('.', ',')} m."
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
    bearing: () -> Float,
    recenterToken: Int,
    modifier: Modifier = Modifier
) {
    val border = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    val context = LocalContext.current
    val offlineMap = LocalOfflineMap.current
    val occurrencePoints = occurrences.filter { it.latitude != null && it.longitude != null }
    val userArrow = remember(context) { oikosArrowMarkerDrawable(context) }
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var mapOrientation by remember { mutableStateOf(0f) }
    // Camadas guardadas entre recomposicoes: antes, a cada giro da bussola, a
    // linha da trilha (milhares de pontos) e todos os marcadores eram refeitos.
    val layers = remember { TrackMapLayers() }
    LaunchedEffect(mapViewRef) {
        while (mapViewRef != null) {
            mapOrientation = mapViewRef?.mapOrientation ?: 0f
            delay(120)
        }
    }
    // A seta do usuario gira sem recompor a tela: so a rotacao do marcador muda.
    LaunchedEffect(mapViewRef) {
        val view = mapViewRef ?: return@LaunchedEffect
        androidx.compose.runtime.snapshotFlow { bearing() }.collect { value ->
            layers.user?.let { marker ->
                marker.rotation = value
                view.invalidate()
            }
        }
    }
    Box(
        modifier
            .clip(MaterialTheme.shapes.large)
            .background(Color(0xFF101114))
            .border(1.dp, border, MaterialTheme.shapes.large)
    ) {
        // Trocar de mapa recria o MapView: o provedor de tiles e fixado na criacao.
        key(offlineMap?.file?.path) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                MapView(context).apply {
                    mapViewRef = this
                    useTiles(offlineMap)
                    setMultiTouchControls(true)
                    controller.setZoom(16.0)
                }
            },
            // Libera as threads de tiles e o MBTiles aberto quando o mapa sai da tela.
            onRelease = { mapView ->
                if (mapViewRef === mapView) mapViewRef = null
                layers.view = null
                mapView.onDetach()
            },
            update = { mapView ->
                mapViewRef = mapView
                var changed = false
                if (layers.view !== mapView) {
                    layers.view = mapView
                    layers.rotation = RotationGestureOverlay(mapView).apply { isEnabled = true }
                    layers.pointsKey = null
                    layers.occurrencesKey = null
                    layers.user = null
                    layers.centerLevel = -1
                    changed = true
                }
                val trackGeo = points.map { GeoPoint(it.latitude, it.longitude) }
                val pointsKey = listOf(points.size.toLong(), points.firstOrNull()?.id ?: 0L, points.lastOrNull()?.id ?: 0L)
                if (layers.pointsKey != pointsKey) {
                    layers.pointsKey = pointsKey
                    layers.line = if (trackGeo.size >= 2) Polyline().apply {
                        setPoints(trackGeo)
                        outlinePaint.color = android.graphics.Color.rgb(255, 106, 0)
                        outlinePaint.strokeWidth = 10f
                        outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                        outlinePaint.strokeJoin = android.graphics.Paint.Join.ROUND
                    } else null
                    changed = true
                }
                val occurrenceGeo = occurrencePoints.mapNotNull {
                    val lat = it.latitude
                    val lon = it.longitude
                    if (lat != null && lon != null) GeoPoint(lat, lon) to it else null
                }
                val occurrencesKey = occurrenceGeo.map { (point, occurrence) -> "${occurrence.id}:${point.latitude}:${point.longitude}:${occurrence.scientificName}" }
                if (layers.occurrencesKey != occurrencesKey) {
                    layers.occurrencesKey = occurrencesKey
                    layers.occurrences = occurrenceGeo.map { (point, occurrence) ->
                        Marker(mapView).apply {
                            position = point
                            title = occurrence.scientificName.ifBlank { occurrence.occurrenceId }
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        }
                    }
                    changed = true
                }
                val userGeo = currentLocation?.let { GeoPoint(it.latitude, it.longitude) }
                if (userGeo == null) {
                    if (layers.user != null) {
                        layers.user = null
                        changed = true
                    }
                } else {
                    val marker = layers.user ?: Marker(mapView).apply {
                        title = "Você está aqui"
                        icon = userArrow
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    }.also {
                        layers.user = it
                        changed = true
                    }
                    marker.position = userGeo
                    marker.rotation = bearing()
                }
                if (changed) {
                    mapView.overlays.clear()
                    layers.rotation?.let(mapView.overlays::add)
                    layers.line?.let(mapView.overlays::add)
                    layers.user?.let(mapView.overlays::add)
                    mapView.overlays.addAll(layers.occurrences)
                }
                val offlineCenter = offlineMap?.let { map ->
                    val lat = map.centerLatitude
                    val lon = map.centerLongitude
                    if (lat != null && lon != null) GeoPoint(lat, lon) else null
                }
                val dataCenter = trackGeo.lastOrNull() ?: userGeo ?: occurrenceGeo.firstOrNull()?.first
                val center = dataCenter ?: offlineCenter ?: GeoPoint(-22.739521, -45.591234)
                val centerLevel = when {
                    trackGeo.isNotEmpty() -> 4
                    userGeo != null -> 3
                    occurrenceGeo.isNotEmpty() -> 2
                    offlineCenter != null -> 1
                    else -> 0
                }
                // Os pontos chegam do banco depois da primeira montagem do mapa:
                // antes ele ficava no ponto padrao, com a trilha fora da tela.
                if (mapView.tag != recenterToken || centerLevel > layers.centerLevel) {
                    mapView.mapOrientation = 0f
                    mapView.controller.setZoom(17.0)
                    mapView.controller.setCenter(center)
                    mapView.tag = recenterToken
                    layers.centerLevel = centerLevel
                }
                mapView.invalidate()
            }
        )
        }
        MapCompass(
            rotationDegrees = { -mapOrientation },
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
                offlineMap?.let { map -> map.attribution.ifBlank { map.name } } ?: "© OpenStreetMap",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * OpenStreetMap pela internet ou o MBTiles escolhido. No offline o zoom fica
 * limitado ao que o arquivo tem: alem disso a tela ficaria cinza.
 */
private fun MapView.useTiles(map: OfflineMap?) {
    if (map == null) {
        setTileSource(TileSourceFactory.MAPNIK)
        minZoomLevel = 3.0
        maxZoomLevel = 20.0
        return
    }
    setTileProvider(OfflineTileProvider(SimpleRegisterReceiver(context), arrayOf(map.file)))
    setTileSource(XYTileSource("oikos-${map.file.nameWithoutExtension}", map.minZoom, map.maxZoom, 256, ".${map.format}", emptyArray()))
    setUseDataConnection(false)
    minZoomLevel = map.minZoom.toDouble()
    maxZoomLevel = map.maxZoom.toDouble()
}

@Composable
private fun TrackMapDialog(
    points: List<GpsTrackPointEntity>,
    occurrences: List<OccurrenceEntity>,
    currentLocation: CapturedLocation?,
    bearing: () -> Float,
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
                    // Altura proporcional a tela: os 520 dp fixos empurravam os
                    // botoes para fora em aparelhos de 320 x 569 dp.
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((LocalConfiguration.current.screenHeightDp - 250).coerceIn(220, 520).dp)
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
private fun MapCompass(rotationDegrees: () -> Float, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
        shape = MaterialTheme.shapes.small
    ) {
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            // Lido so no desenho: girar o mapa nao recompoe a tela.
            Canvas(Modifier.size(32.dp)) {
                rotate(rotationDegrees()) {
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

/** Camadas do mapa da trilha, reaproveitadas entre recomposicoes. */
private class TrackMapLayers {
    var view: MapView? = null
    var rotation: RotationGestureOverlay? = null
    var line: Polyline? = null
    var pointsKey: List<Long>? = null
    var occurrences: List<Marker> = emptyList()
    var occurrencesKey: List<String>? = null
    var user: Marker? = null
    /** De onde veio o centro atual: 0 padrao, 1 mapa offline, 2 ocorrencia, 3 usuario, 4 trilha. */
    var centerLevel: Int = -1
}

/**
 * Direcao da bussola como estado: quem le dentro de lambda (desenho, efeito)
 * nao recompoe a tela inteira a cada leitura do sensor.
 */
@Composable
private fun rememberCompassBearing(): androidx.compose.runtime.State<Float> {
    val context = LocalContext.current
    val bearingState = remember { mutableStateOf(0f) }
    var bearing by bearingState
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
    return bearingState
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

private fun hasPermission(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

private fun formatClock(timestamp: Long): String =
    SimpleDateFormat("HH:mm:ss", Locale.forLanguageTag("pt-BR")).format(Date(timestamp))

/**
 * Coordenada, altitude ou incerteza digitada que nao e numero, ou so metade do
 * par: antes o valor era descartado em silencio e o registro saia sem
 * longitude, ou sem a incerteza que o pesquisador digitou como "15 m".
 */
private fun coordinateInputProblem(lat: String, lon: String, alt: String, accuracy: String): String? {
    fun invalid(text: String) = text.isNotBlank() && text.trim().replace(',', '.').toDoubleOrNull() == null
    return when {
        invalid(lat) || invalid(lon) -> "A coordenada não é um número em graus decimais. Confira antes de salvar (ex.: -22.43017)."
        lat.isBlank() != lon.isBlank() -> "Preencha latitude e longitude, ou deixe as duas em branco."
        invalid(alt) -> "A altitude precisa ser só o número, em metros (ex.: 1520)."
        invalid(accuracy) -> "A incerteza precisa ser só o número, em metros (ex.: 15)."
        else -> null
    }
}

/** Registro salvo -> valores do formulario (edicao e "repetir do anterior"). */
private fun occurrenceFormValues(occurrence: OccurrenceEntity, sessionDate: String, defaultModule: String): Map<String, String> {
    val dynamic = parseFlatJsonMap(occurrence.dynamicFieldsJson)
    val savedDate = dynamic["eventDate"].orEmpty().ifBlank { sessionDate }
    return mapOf(
        "occurrenceID" to occurrence.occurrenceId,
        "formType" to occurrence.formType.ifBlank { defaultModule },
        "occurrenceType" to occurrence.occurrenceType.ifBlank { occurrenceTypesFor(occurrence.formType).first() },
        "date" to (normalizeDateInput(savedDate) ?: savedDate),
        "time" to dynamic["eventTime"].orEmpty(),
        "recordNumber" to occurrence.recordNumber,
        "scientificName" to occurrence.scientificName,
        "vernacularName" to occurrence.vernacularName,
        "basis" to occurrence.basisOfRecord,
        "status" to occurrence.occurrenceStatus,
        "count" to (occurrence.individualCount?.toString() ?: ""),
        "sex" to occurrence.sex,
        "lifeStage" to occurrence.lifeStage,
        "behavior" to occurrence.behavior,
        "reproductive" to occurrence.reproductiveCondition,
        "recordedBy" to occurrence.recordedBy,
        "identifiedBy" to occurrence.identifiedBy,
        "dateIdentified" to (normalizeDateInput(occurrence.dateIdentified) ?: occurrence.dateIdentified),
        "lat" to (occurrence.latitude?.toString() ?: ""),
        "lon" to (occurrence.longitude?.toString() ?: ""),
        "alt" to (occurrence.altitude?.let { formatPlain(it) } ?: ""),
        "accuracy" to (occurrence.coordinateUncertaintyInMeters?.let { formatPlain(it) } ?: ""),
        "remarks" to occurrence.occurrenceRemarks,
        "fieldNotes" to occurrence.fieldNotes
    ) + dynamic.filterKeys { it != "eventDate" && it != "eventTime" }.mapKeys { "dyn:${it.key}" }
}

/** Acesso aos campos dinamicos do formulario, guardados com o prefixo "dyn:". */
private class DynamicValues(private val form: FormState) {
    operator fun get(key: String): String = form["dyn:$key"]
    operator fun set(key: String, value: String) {
        form["dyn:$key"] = value
    }
    fun asMap(): Map<String, String> =
        form.values.filterKeys { it.startsWith("dyn:") }.mapKeys { it.key.removePrefix("dyn:") }
}

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
    onDeleted: () -> Unit
) {
    val project by remember(viewModel, projectId) { viewModel.observeProject(projectId) }.collectAsState(initial = null)
    val session by remember(viewModel, sessionId) { viewModel.observeSession(sessionId) }.collectAsState(initial = null)
    val existing by remember(viewModel, occurrenceId) { occurrenceId?.let { viewModel.observeOccurrence(it) } ?: kotlinx.coroutines.flow.flowOf(null) }.collectAsState(initial = null)
    val sessionOccurrences by remember(viewModel, sessionId) { viewModel.observeOccurrences(sessionId) }.collectAsState(initial = emptyList())
    val fields by remember(viewModel, projectId) { viewModel.observeFields(projectId) }.collectAsState(initial = emptyList())
    val enabledFields = fields.filter { it.enabled }
    val moduleOptions = project?.enabledOccurrenceModules().orEmpty().ifEmpty { listOf("Fauna") }

    // Rascunho: sobrevive a rotacao e ao Android fechar o app em segundo plano
    // (comum com a camera aberta). Antes, girar a tela apagava o que tinha sido
    // digitado e as fotos ainda nao salvas ficavam orfas no armazenamento.
    // Cada registro novo tem rascunho proprio: midia que termina de processar
    // depois de "Salvar e novo", ou de um descarte, nao cai no registro seguinte.
    var draftNonce by rememberSaveable(sessionId, occurrenceId) { mutableStateOf(UUID.randomUUID().toString().take(8)) }
    val draftKey = "occ:$sessionId:${occurrenceId ?: "new-$draftNonce"}"
    val form = rememberFormState(draftKey)
    val dynamicValues = remember(form) { DynamicValues(form) }

    // form na chave: "Salvar e novo" troca o rascunho e isto roda de novo.
    LaunchedEffect(form, project, session, existing, occurrenceId, form.initialized) {
        if (form.initialized) return@LaunchedEffect
        // O identificador so e gerado com projeto e expedicao carregados; antes
        // saia "OIKOS-..." porque era gerado enquanto o projeto ainda era nulo.
        val currentProject = project ?: return@LaunchedEffect
        val currentSession = session ?: return@LaunchedEffect
        if (occurrenceId == null) {
            val module = moduleOptions.first()
            // Lido do banco, e nao do fluxo da tela: o fluxo podia ainda estar
            // vazio aqui e o numero sequencial saia repetido.
            val previous = viewModel.occurrencesInSession(sessionId)
            val carried = previous.firstOrNull()?.let { last ->
                QuickEntry.carried(occurrenceFormValues(last, currentSession.eventDate, moduleOptions.first()), viewModel.repeatFields(projectId))
            }.orEmpty()
            form.load(
                mapOf(
                    "occurrenceID" to semanticOccurrenceId(currentProject, currentSession, previous.size + 1),
                    "formType" to module,
                    "occurrenceType" to occurrenceTypesFor(module).first(),
                    "date" to (normalizeDateInput(currentSession.eventDate) ?: LocalDate.now().toString()),
                    "time" to LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")),
                    "basis" to "HumanObservation",
                    "status" to "present"
                ) + carried.filterKeys { it !in QuickEntry.NEVER }
            )
        } else {
            val occurrence = existing ?: return@LaunchedEffect
            form.load(occurrenceFormValues(occurrence, currentSession.eventDate, moduleOptions.first()))
        }
    }

    // Midias capturadas para este rascunho chegam pelo ViewModel (sobrevivem a
    // rotacao mesmo se a foto terminar de processar com a tela sendo recriada).
    val incoming by viewModel.pendingMedia.collectAsState()
    LaunchedEffect(incoming[draftKey]) {
        val arrived = viewModel.takePendingMedia(draftKey)
        if (arrived.isNotEmpty()) {
            form.pendingMedia = form.pendingMedia + arrived
            form.dirty = true
        }
    }

    var occurrenceID by form.field("occurrenceID")
    var formType by form.field("formType")
    var occurrenceType by form.field("occurrenceType")
    var occurrenceDate by form.field("date")
    var occurrenceTime by form.field("time")
    var recordNumber by form.field("recordNumber")
    var scientificName by form.field("scientificName")
    var vernacularName by form.field("vernacularName")
    var basis by form.field("basis")
    var status by form.field("status")
    var count by form.field("count")
    var sex by form.field("sex")
    var lifeStage by form.field("lifeStage")
    var behavior by form.field("behavior")
    var reproductive by form.field("reproductive")
    var recordedBy by form.field("recordedBy")
    var identifiedBy by form.field("identifiedBy")
    var dateIdentified by form.field("dateIdentified")
    var lat by form.field("lat")
    var lon by form.field("lon")
    var alt by form.field("alt")
    var accuracy by form.field("accuracy")
    var remarks by form.field("remarks")
    var fieldNotes by form.field("fieldNotes")
    // Modo simples: campos avancados comecam recolhidos (antes o interruptor
    // existia e nao mudava nada).
    var showAdvanced by rememberSaveable { mutableStateOf(!simpleMode) }
    val taxonScope = rememberCoroutineScope()
    var deleteOccurrence by remember { mutableStateOf(false) }

    // Foto comprimindo ou gravacao em curso/fechando contam como edicao: sair
    // sem aviso deixava a midia orfa ou a mandava para outro registro.
    val busyDrafts by viewModel.busyDrafts.collectAsState()
    val recordingNow by viewModel.recording.collectAsState()
    val mediaBusy = (busyDrafts[draftKey] ?: 0) > 0 || recordingNow?.target?.draftKey == draftKey
    val guardedBack = rememberGuardedBack(
        dirty = form.dirty || form.pendingMedia.isNotEmpty() || mediaBusy,
        onDiscard = { viewModel.discardDraft(draftKey, form.pendingMedia) },
        onBack = onBack
    )

    if (deleteOccurrence) {
        val currentOccurrence = existing
        ConfirmDeleteDialog(
            title = "Apagar ocorrência?",
            text = "O registro e suas fotos e áudios vão para a lixeira e podem ser restaurados por 30 dias em Configurações > Lixeira.",
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

    LaunchedEffect(project, form.initialized) {
        // So com o projeto carregado e so em registro novo. Antes, depois de girar
        // a tela, o projeto ainda nulo dava ["Fauna"] e um registro de Flora era
        // trocado para Fauna sem aviso; e um registro antigo de modulo desligado
        // era convertido ao ser aberto.
        if (project == null || occurrenceId != null || !form.initialized) return@LaunchedEffect
        if (formType !in moduleOptions) {
            formType = moduleOptions.first()
            occurrenceType = occurrenceTypesFor(formType).first()
        }
    }

    val extraFields = enabledFields
        .filterNot { it.dwcTerm in fixedOccurrenceTerms }
        .filterNot { it.dwcTerm in advancedOccurrenceTerms }
        .filterNot { it.dwcTerm in formOwnedTerms }
    // No modo simples, extras opcionais ficam recolhidos; obrigatorios aparecem sempre.
    val visibleExtras = if (showAdvanced) extraFields else extraFields.filter { it.required }

    val scrollState = rememberScrollState()
    // Um toque duplo salvava duas vezes (ou um registro vazio no meio da troca de rascunho).
    var saving by remember { mutableStateOf(false) }
    var locationInputError by remember { mutableStateOf<String?>(null) }
    fun save(andNew: Boolean) {
                if (saving || !form.initialized) return
                if (viewModel.recording.value?.target?.draftKey == draftKey) {
                    viewModel.showMessage("Pare a gravação antes de salvar o registro.")
                    return
                }
                if ((busyDrafts[draftKey] ?: 0) > 0) {
                    viewModel.showMessage("Aguarde: uma foto ou um áudio ainda está sendo processado.")
                    return
                }
                locationInputError?.let {
                    viewModel.showMessage(it)
                    return
                }
                coordinateInputProblem(lat, lon, alt, accuracy)?.let {
                    viewModel.showMessage(it)
                    return
                }
                val dynamic = dynamicValues.asMap() + mapOf("eventDate" to occurrenceDate, "eventTime" to occurrenceTime)
                val base = existing ?: OccurrenceEntity(projectId = projectId, sessionId = sessionId, occurrenceId = occurrenceID)
                val occurrenceToSave = base.copy(
                    occurrenceId = occurrenceID.trim(),
                    occurrenceType = occurrenceType,
                    formType = formType,
                    recordNumber = recordNumber.trim(),
                    scientificName = scientificName.trim(),
                    vernacularName = vernacularName.trim(),
                    basisOfRecord = basis,
                    occurrenceStatus = status,
                    individualCount = count.trim().toIntOrNull(),
                    sex = sex,
                    lifeStage = lifeStage,
                    behavior = behavior,
                    reproductiveCondition = reproductive,
                    recordedBy = recordedBy,
                    identifiedBy = identifiedBy,
                    dateIdentified = dateIdentified,
                    latitude = lat.replace(',', '.').toDoubleOrNull(),
                    longitude = lon.replace(',', '.').toDoubleOrNull(),
                    altitude = alt.replace(',', '.').toDoubleOrNull(),
                    coordinateUncertaintyInMeters = accuracy.replace(',', '.').toDoubleOrNull(),
                    geodeticDatum = existing?.geodeticDatum ?: session?.geodeticDatum ?: project?.defaultDatum ?: "SIRGAS 2000",
                    occurrenceRemarks = remarks,
                    fieldNotes = fieldNotes,
                    dynamicFieldsJson = flatJson(dynamic)
                )
                if (count.isNotBlank() && occurrenceToSave.individualCount == null) {
                    viewModel.showMessage("Número de indivíduos precisa ser um número inteiro.")
                    return
                }
                val faltando = missingRequiredFields(
                    enabledFields = enabledFields,
                    occurrence = occurrenceToSave,
                    dynamicValues = dynamic
                )
                if (faltando.isNotEmpty()) {
                    if (faltando.any { it.dwcTerm !in fixedOccurrenceTerms }) showAdvanced = true
                    viewModel.showMessage("Preencha antes de salvar: " + faltando.joinToString(", ") { it.labelPt })
                } else {
                    val pending = form.pendingMedia
                    saving = true
                    viewModel.saveOccurrenceWithMedia(occurrenceToSave, pending, onFailed = { saving = false }) {
                        form.pendingMedia = emptyList()
                        form.dirty = false
                        saving = false
                        if (andNew) {
                            // Proximo registro em rascunho novo: os padroes e os campos
                            // escolhidos para repetir sao carregados de novo.
                            if (viewModel.message.value == null) viewModel.showMessage("Registro salvo. Pronto para o próximo.")
                            draftNonce = UUID.randomUUID().toString().take(8)
                            taxonScope.launch { scrollState.scrollTo(0) }
                        } else {
                            onBack()
                        }
                    }
                }
    }

    var showRepeatDialog by rememberSaveable { mutableStateOf(false) }
    var repeatKeys by remember { mutableStateOf(viewModel.repeatFields(projectId)) }
    val lastOccurrence = if (occurrenceId == null) sessionOccurrences.firstOrNull() else null
    val previousValues = remember(lastOccurrence, session) {
        lastOccurrence?.let { occurrenceFormValues(it, session?.eventDate.orEmpty(), moduleOptions.first()) }.orEmpty()
    }
    val dynamicLabels = enabledFields.associate { it.dwcTerm to fieldDisplayLabel(it) }
    val repeatOptions = QuickEntry.options(previousValues) { term -> dynamicLabels[term] ?: term }
    if (showRepeatDialog && repeatOptions.isNotEmpty()) {
        RepeatFieldsDialog(
            options = repeatOptions,
            selected = repeatKeys,
            onDismiss = { showRepeatDialog = false }
        ) { chosen ->
            showRepeatDialog = false
            val added = chosen - repeatKeys
            repeatKeys = chosen
            viewModel.setRepeatFields(projectId, chosen)
            // So os campos recem-marcados, e so onde nada foi digitado ainda:
            // antes, confirmar o dialogo trocava a coordenada e a especie ja
            // preenchidas neste registro pelas do anterior.
            val defaults = mapOf(
                "formType" to moduleOptions.first(),
                "occurrenceType" to occurrenceTypesFor(moduleOptions.first()).first(),
                "basis" to "HumanObservation",
                "status" to "present"
            )
            added.forEach { option ->
                val keys = QuickEntry.formKeysFor(option)
                val primary = keys.firstOrNull() ?: return@forEach
                if (form[primary].isBlank() || form[primary] == defaults[primary]) {
                    keys.forEach { key -> form[key] = previousValues[key].orEmpty() }
                }
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            OikosTopBar(if (occurrenceId == null) "Nova ocorrência" else "Editar ocorrência", guardedBack) {
                if (occurrenceId == null) IconButton(onClick = { save(andNew = true) }) {
                    Icon(Icons.AutoMirrored.Filled.PlaylistAdd, ui("Salvar e criar o próximo"))
                }
                if (occurrenceId != null) IconButton(onClick = { deleteOccurrence = true }) { Icon(Icons.Default.Delete, ui("Apagar ocorrência")) }
            }
        },
        floatingActionButton = { HideWithKeyboard {
            FloatingActionButton(onClick = { save(andNew = false) }) { Icon(Icons.Default.Save, ui("Salvar")) }
        } }
    ) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(scrollState).padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!simpleMode) HelpCard("Registro de ocorrência", "Registro é cada espécie, indivíduo, som, foto, vestígio ou observação feita dentro da expedição.")
            if (repeatOptions.isNotEmpty()) RepeatBanner(repeatOptions, repeatKeys) { showRepeatDialog = true }
            ChipRow("Grupo do registro", moduleOptions.map { it to moduleDisplayName(it) }, formType) {
                formType = it
                occurrenceType = occurrenceTypesFor(it).first()
            }
            ChipRow("Tipo de ocorrência", occurrenceTypesFor(formType).map { it to it }, occurrenceType) { occurrenceType = it }
            if (enabled("scientificName", enabledFields)) ScientificNameField(
                viewModel = viewModel,
                label = label("scientificName", enabledFields, "Nome da espécie"),
                value = scientificName,
                authorship = dynamicValues["scientificNameAuthorship"],
                onValueChange = {
                    scientificName = it
                    // Nome digitado a mao: tudo o que veio da lista deixa de valer.
                    // Antes so autoria e fonte saiam; genero, epiteto, familia e
                    // rank do nome antigo iam para a exportacao com o nome novo.
                    if (dynamicValues["nameAccordingTo"].isNotBlank()) {
                        QuickEntry.TAXON_DETAILS.forEach { key -> dynamicValues[key] = "" }
                    }
                },
                onChoose = { suggestion ->
                    taxonScope.launch {
                        val chosen = resolveSuggestion(viewModel, suggestion)
                        scientificName = chosen.scientificName
                        chosen.fields.forEach { (key, value) -> dynamicValues[key] = value }
                        if (vernacularName.isBlank()) chosen.vernacular?.let { vernacularName = it }
                        chosen.synonymNotice?.let { viewModel.showMessage(it) }
                    }
                }
            )
            if (enabled("vernacularName", enabledFields)) TextInput(label("vernacularName", enabledFields, "Nome popular"), vernacularName) { vernacularName = it }
            if (enabled("eventDate", enabledFields)) DateInput(label("eventDate", enabledFields, "Data do registro"), occurrenceDate) { occurrenceDate = it }
            if (enabled("eventTime", enabledFields)) TimeInput(label("eventTime", enabledFields, "Hora do registro"), occurrenceTime) { occurrenceTime = it }
            // occurrenceID continua sendo gerado sempre; o campo so aparece se o
            // projeto pedir. Ocultar a caixa nao desliga o identificador.
            if (enabled("occurrenceID", enabledFields)) TextInput(label("occurrenceID", enabledFields, "Registro científico"), occurrenceID, autoCorrect = false, help = "Identificador Darwin Core occurrenceID. É gerado automaticamente; edite apenas se precisar casar com um identificador externo.") { occurrenceID = it }
            if (enabled("recordNumber", enabledFields)) TextInput(label("recordNumber", enabledFields, "Registro de campo"), recordNumber, autoCorrect = false, help = "Código amigável do ponto ou registro. Exemplo: P03, T1-P03 ou Registro #0003.") { recordNumber = it }
            if (enabled("basisOfRecord", enabledFields)) DwcVocabularyInput(
                label = label("basisOfRecord", enabledFields, "Natureza do registro"),
                value = basis,
                options = DwcVocabulary.basisOfRecord,
                help = "Define se o registro é uma observação, um espécime tombado ou uma amostra. Exportado como dwc:basisOfRecord."
            ) { basis = it }
            if (enabled("occurrenceStatus", enabledFields)) DwcVocabularyInput(
                label = label("occurrenceStatus", enabledFields, "Status da ocorrência"),
                value = status,
                options = DwcVocabulary.occurrenceStatus
            ) { status = it }
            if (enabled("individualCount", enabledFields)) TextInput(label("individualCount", enabledFields, "Número de indivíduos"), count, KeyboardType.Number) { count = it }
            if (enabled("sex", enabledFields)) DwcVocabularyInput(
                label = label("sex", enabledFields, "Sexo"),
                value = sex,
                options = DwcVocabulary.sex
            ) { sex = it }
            if (enabled("lifeStage", enabledFields)) DwcVocabularyInput(
                label = label("lifeStage", enabledFields, "Fase de vida"),
                value = lifeStage,
                options = DwcVocabulary.lifeStage
            ) { lifeStage = it }
            if (enabled("behavior", enabledFields)) TextInput(label("behavior", enabledFields, "Comportamento"), behavior) { behavior = it }
            if (enabled("reproductiveCondition", enabledFields)) TextInput(label("reproductiveCondition", enabledFields, "Condição reprodutiva"), reproductive) { reproductive = it }
            if (enabled("recordedBy", enabledFields)) TextInput(label("recordedBy", enabledFields, "Quem coletou"), recordedBy, help = if (recordedBy.isBlank() && !session?.recordedBy.isNullOrBlank()) "Em branco, a exportação usa o coletor da expedição: ${session?.recordedBy}." else null) { recordedBy = it }
            if (enabled("identifiedBy", enabledFields)) TextInput(label("identifiedBy", enabledFields, "Quem identificou"), identifiedBy) { identifiedBy = it }
            if (enabled("dateIdentified", enabledFields)) DateInput(label("dateIdentified", enabledFields, "Data da identificação"), dateIdentified) { dateIdentified = it }
            var showGps by rememberSaveable { mutableStateOf(false) }
            val context = LocalContext.current
            if (showGps) {
                GpsCaptureDialog(viewModel, onDismiss = { showGps = false }) { fix ->
                    showGps = false
                    lat = fix.latitude.toString()
                    lon = fix.longitude.toString()
                    alt = fix.altitude?.let { formatPlain(it) }.orEmpty()
                    accuracy = formatPlain(fix.uncertainty)
                    // Altitude do GPS ja convertida para o nivel do mar pelo EGM2008.
                    dynamicValues["verticalDatum"] = if (fix.altitude != null) Geoid.VERTICAL_DATUM else ""
                    dynamicValues["georeferenceProtocol"] = GpsAveraging.GBIF_PROTOCOL
                    dynamicValues["georeferenceRemarks"] = fix.remarks()
                }
            }
            // Coordenada digitada a mao invalida a descricao automatica de como
            // ela foi obtida; o protocolo e a observacao saem do registro.
            fun manualCoordinateEdit() {
                if (dynamicValues["georeferenceRemarks"].startsWith(GPS_REMARKS_PREFIX) ||
                    dynamicValues["georeferenceRemarks"].startsWith(SINGLE_FIX_REMARKS_PREFIX)
                ) {
                    dynamicValues["georeferenceRemarks"] = ""
                    dynamicValues["georeferenceProtocol"] = ""
                }
            }
            LocationFields(
                project?.coordinateFormat ?: "DD", lat, lon, alt, accuracy,
                { lat = it; manualCoordinateEdit() }, { lon = it; manualCoordinateEdit() },
                {
                    alt = it
                    // Altitude digitada (altimetro, carta): o datum vertical do GPS nao vale mais.
                    if (dynamicValues["verticalDatum"] == Geoid.VERTICAL_DATUM) dynamicValues["verticalDatum"] = ""
                },
                { accuracy = it },
                onInputError = { locationInputError = it }
            ) {
                if (hasAnyLocationPermission(context)) {
                    showGps = true
                } else {
                    requestLocation { location ->
                        location?.let {
                            lat = it.latitude.toString()
                            lon = it.longitude.toString()
                            alt = it.altitude?.let { value -> formatPlain(value) }.orEmpty()
                            accuracy = formatPlain(GpsAveraging.uncertaintyFor(it.accuracyMeters))
                            dynamicValues["verticalDatum"] = if (it.altitude != null) Geoid.VERTICAL_DATUM else ""
                            dynamicValues["georeferenceProtocol"] = GpsAveraging.GBIF_PROTOCOL
                            dynamicValues["georeferenceRemarks"] =
                                "$SINGLE_FIX_REMARKS_PREFIX; precisão informada ${it.accuracyMeters?.let { a -> formatPlain(a) + " m" } ?: "não informada"}; incerteza = 2 × precisão."
                        }
                    }
                }
            }
            CoordinateCheckPanel(viewModel, lat, lon, session?.stateProvince.orEmpty(), session?.municipality.orEmpty())
            if (enabled("occurrenceRemarks", enabledFields)) TextInput(label("occurrenceRemarks", enabledFields, "Observações da ocorrência"), remarks, minLines = 3) { remarks = it }
            if (enabled("fieldNotes", enabledFields)) TextInput(label("fieldNotes", enabledFields, "Anotações de campo"), fieldNotes, minLines = 5, help = "Use para anotações rápidas, descrição detalhada, dúvidas de identificação, observações de laboratório ou coleção.") { fieldNotes = it }
            visibleExtras.forEach { field -> ExtraFieldInput(field, dynamicValues) }
            OutlinedButton(onClick = { showAdvanced = !showAdvanced }) {
                Text(if (showAdvanced) ui("Ocultar campos avançados") else ui("Mostrar campos avançados"))
            }
            AnimatedVisibility(visible = showAdvanced) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AdvancedOccurrenceSections(formType, dynamicValues, enabledFields)
                }
            }
            Text(ui("Mídias da ocorrência"), style = MaterialTheme.typography.titleMedium)
            val currentProject = project
            val currentSession = session
            if (currentProject != null && currentSession != null) {
                InlineMediaCapture(
                    viewModel = viewModel,
                    project = currentProject,
                    session = currentSession,
                    target = MediaTarget(projectId, sessionId, occurrenceId, if (occurrenceId == null) draftKey else null),
                    occurrenceForName = occurrenceToName(occurrenceID, scientificName, recordNumber),
                    pendingMedia = form.pendingMedia,
                    onPendingMediaChanged = {
                        form.pendingMedia = it
                        form.dirty = true
                    },
                    onPhotoMetadata = { metadata ->
                        if (lat.isBlank()) metadata.latitude?.let { lat = CoordinateUtils.roundCoordinate(it).toString() }
                        if (lon.isBlank()) metadata.longitude?.let { lon = CoordinateUtils.roundCoordinate(it).toString() }
                    }
                )
            }
        }
    }
}

private const val GPS_REMARKS_PREFIX = "Média de"
private const val SINGLE_FIX_REMARKS_PREFIX = "Leitura única do GNSS do celular"

private fun hasAnyLocationPermission(context: Context): Boolean =
    hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ||
        hasPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)

/**
 * Captura de posicao com media de leituras e semaforo de qualidade.
 * O pesquisador ve quando a posicao "esta boa" em vez de aceitar a primeira
 * leitura, que pode errar muito sob dossel ou logo que o GPS liga.
 */
@Composable
private fun GpsCaptureDialog(viewModel: AppViewModel, onDismiss: () -> Unit, onResult: (AveragedFix) -> Unit) {
    val readings = remember { mutableStateListOf<GpsReading>() }
    val startedAt = remember { System.currentTimeMillis() }
    var elapsed by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        viewModel.locationUpdates(1_000L).collect { location ->
            readings += GpsReading(location.latitude, location.longitude, location.altitude, location.accuracyMeters, location.capturedAt)
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            elapsed = System.currentTimeMillis() - startedAt
            delay(500)
        }
    }
    val latest = readings.lastOrNull()
    val quality = GpsAveraging.quality(latest?.accuracy)
    val average = remember(readings.size) { GpsAveraging.average(readings.toList()) }
    val (color, qualityText) = when {
        latest == null -> MaterialTheme.colorScheme.outline to ui("Procurando satélites...")
        quality == FixQuality.GOOD -> Color(0xFF2E7D32) to ui("Boa (até 10 m)")
        quality == FixQuality.FAIR -> Color(0xFFF9A825) to ui("Razoável (até 30 m)")
        else -> MaterialTheme.colorScheme.error to ui("Fraca (mais de 30 m)")
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.GpsFixed, contentDescription = null) },
        title = { Text(ui("Capturando posição")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(22.dp).clip(androidx.compose.foundation.shape.CircleShape).background(color))
                    Text(qualityText, style = MaterialTheme.typography.titleMedium)
                }
                Text("${ui("Leituras")}: ${readings.size} · ${elapsed / 1000} s", style = MaterialTheme.typography.bodyMedium)
                latest?.accuracy?.let { Text("${ui("Precisão agora")}: ±${formatPlain(it)} m", style = MaterialTheme.typography.bodyMedium) }
                average?.let {
                    Text("${ui("Média")}: ${it.latitude}, ${it.longitude}", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${ui("Incerteza a registrar")}: ${formatPlain(it.uncertainty)} m (${it.used} ${ui("leituras usadas")})",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Text(
                    ui("Fique parado no ponto. Céu aberto e alguns segundos de leitura melhoram a média. A incerteza segue o guia do GBIF: no mínimo o dobro da precisão informada pelo GPS."),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(enabled = average != null, onClick = { average?.let(onResult) }) { Text(ui("Usar média")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(ui("Cancelar")) } }
    )
}

/**
 * Campos taxonomicos vindos da lista escolhida. Vao para o Darwin Core mesmo
 * que o pesquisador nao tenha ligado esses campos no formulario.
 */
private fun taxonFields(taxon: com.oikos.fieldbook.data.TaxonNameEntity, accordingTo: String): Map<String, String> {
    val words = taxon.canonicalName.split(' ').filter { it.isNotBlank() && !it.endsWith(".") }
    return mapOf(
        "scientificNameAuthorship" to taxon.authorship,
        "taxonRank" to taxon.rank,
        "family" to taxon.family,
        "genus" to taxon.genus,
        "kingdom" to taxon.kingdom,
        "specificEpithet" to if (taxon.rank in setOf("species", "subspecies", "variety", "form")) words.getOrNull(1).orEmpty() else "",
        "infraspecificEpithet" to if (taxon.rank in setOf("subspecies", "variety", "form")) words.getOrNull(2).orEmpty() else "",
        "nameAccordingTo" to accordingTo
    )
}

private fun fullName(taxon: com.oikos.fieldbook.data.TaxonNameEntity): String =
    listOf(taxon.canonicalName, taxon.authorship).filter { it.isNotBlank() }.joinToString(" ")

/** Nome escolhido na lista, ja trocado pelo aceito quando era sinonimo. */
private data class ChosenTaxon(
    val scientificName: String,
    /** Campos Darwin Core do taxon, com o sinonimo original em verbatimIdentification. */
    val fields: Map<String, String>,
    val vernacular: String?,
    /** Aviso para o pesquisador quando o nome escolhido era sinonimo. */
    val synonymNotice: String?
)

private suspend fun resolveSuggestion(viewModel: AppViewModel, suggestion: TaxonSuggestion): ChosenTaxon {
    val found = viewModel.acceptedFor(suggestion)
    if (found == null) {
        // Sinonimo cujo nome aceito nao esta na lista: fica o nome escolhido,
        // com aviso. Antes ele entrava como se fosse o aceito, calado.
        return ChosenTaxon(
            scientificName = fullName(suggestion.taxon),
            fields = taxonFields(suggestion.taxon, "${suggestion.checklistTitle} (v${suggestion.checklistVersion})") + ("verbatimIdentification" to ""),
            vernacular = viewModel.vernacularFor(suggestion.taxon),
            synonymNotice = "${suggestion.taxon.canonicalName} é sinônimo de ${suggestion.taxon.acceptedName.ifBlank { "outro nome" }} segundo a lista, mas o nome aceito não está nela. Registrado como ${suggestion.taxon.canonicalName}: confira a identificação."
        )
    }
    val accepted = found
    val synonym = suggestion.taxon.status == "S" && accepted.id != suggestion.taxon.id
    return ChosenTaxon(
        scientificName = fullName(accepted),
        fields = taxonFields(accepted, "${suggestion.checklistTitle} (v${suggestion.checklistVersion})") +
            ("verbatimIdentification" to if (synonym) fullName(suggestion.taxon) else ""),
        vernacular = viewModel.vernacularFor(accepted),
        synonymNotice = if (synonym) "${suggestion.taxon.canonicalName} é sinônimo: registrado como ${accepted.canonicalName}." else null
    )
}

/** Campo extra do catalogo, com o tipo de entrada que ele declara. */
@Composable
private fun ExtraFieldInput(field: FieldConfigEntity, values: DynamicValues) {
    val fieldLabel = "${fieldDisplayLabel(field)} (${field.dwcTerm})"
    val definition = DarwinCoreCatalog.byTerm[field.dwcTerm]
    val autoCorrect = field.dwcTerm !in DarwinCoreCatalog.noAutocorrectTerms
    when {
        definition != null && definition.vocabulary.isNotEmpty() ->
            DwcVocabularyInput(fieldLabel, values[field.dwcTerm], definition.vocabulary) { values[field.dwcTerm] = it }
        field.fieldType == "date" ->
            DateInput(fieldLabel, values[field.dwcTerm]) { values[field.dwcTerm] = it }
        field.fieldType == "time" ->
            TimeInput(fieldLabel, values[field.dwcTerm]) { values[field.dwcTerm] = it }
        field.fieldType == "integer" ->
            TextInput(fieldLabel, values[field.dwcTerm], KeyboardType.Number) { values[field.dwcTerm] = it }
        field.fieldType == "decimal" ->
            TextInput(fieldLabel, values[field.dwcTerm], KeyboardType.Decimal) { values[field.dwcTerm] = it }
        field.fieldType == "long_text" ->
            TextInput(fieldLabel, values[field.dwcTerm], minLines = 3) { values[field.dwcTerm] = it }
        else ->
            TextInput(fieldLabel, values[field.dwcTerm], autoCorrect = autoCorrect) { values[field.dwcTerm] = it }
    }
}

@Composable
private fun MediaScreen(viewModel: AppViewModel, projectId: Long, sessionId: Long, occurrenceId: Long?, modifier: Modifier, onBack: () -> Unit) {
    val project by remember(viewModel, projectId) { viewModel.observeProject(projectId) }.collectAsState(initial = null)
    val session by remember(viewModel, sessionId) { viewModel.observeSession(sessionId) }.collectAsState(initial = null)
    val occurrence by remember(viewModel, occurrenceId) { occurrenceId?.let { viewModel.observeOccurrence(it) } ?: kotlinx.coroutines.flow.flowOf(null) }.collectAsState(initial = null)
    val media by remember(viewModel, occurrenceId, sessionId) { occurrenceId?.let { viewModel.observeOccurrenceMedia(it) } ?: viewModel.observeSessionMedia(sessionId) }.collectAsState(initial = emptyList())
    // Especie atual de cada ocorrencia, para o cartao da midia na galeria da expedicao.
    val sessionOccurrences by remember(viewModel, sessionId) { viewModel.observeOccurrences(sessionId) }.collectAsState(initial = emptyList())
    val occurrenceNames = remember(sessionOccurrences) { sessionOccurrences.associate { it.id to it.scientificName.ifBlank { it.occurrenceId } } }
    RecordingScreenGuard(viewModel, MediaTarget(projectId, sessionId, occurrenceId, null))

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
            val currentProject = project
            val currentSession = session
            if (currentProject != null && currentSession != null) {
                item {
                    MediaCaptureControls(
                        viewModel = viewModel,
                        project = currentProject,
                        session = currentSession,
                        target = MediaTarget(projectId, sessionId, occurrenceId, null),
                        occurrenceForName = occurrence?.toMediaOccurrenceName()
                    )
                }
            }
            if (media.isEmpty()) {
                item { EmptyState("Sem mídias.", "Fotos e áudios ficam em arquivos locais; o banco guarda só metadados.") }
            } else {
                items(media, key = { it.id }) { MediaListCard(it, onUpdate = { updated -> viewModel.updateMedia(updated) }, linkedName = it.occurrenceId?.let(occurrenceNames::get), onDelete = { viewModel.deleteMedia(it) }) }
            }
        }
    }
}

@Composable
private fun ExportScreen(viewModel: AppViewModel, projectId: Long, sessionId: Long?, modifier: Modifier, onBack: () -> Unit, onShare: (Intent, String?) -> Unit) {
    var selectedContent by rememberSaveable { mutableStateOf(ExportContent.SpreadsheetsAndAllMedia) }
    var selectedFormat by rememberSaveable { mutableStateOf(ExportFormat.Zip) }
    val exporting by viewModel.exporting.collectAsState()
    Scaffold(modifier = modifier, topBar = { OikosTopBar(if (sessionId == null) "Exportar projeto" else "Exportar expedição", onBack) }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(ui("Exportar dados"), style = MaterialTheme.typography.titleLarge)
            Text(if (sessionId == null) ui("Escopo: Projeto inteiro / planilha mãe consolidada") else ui("Escopo: Expedição atual"))
            Text(ui("Para não perder o vínculo entre fotos, áudios e registros, recomendamos exportar como ZIP completo."))
            HelpCard(
                "ZIP organizado",
                "O ZIP cria 01_planilha_principal, 02_tabelas_de_apoio, 03_sig_qgis, 04_darwin_core_archive e 05_midias. A planilha principal reúne as ocorrências do escopo escolhido com os campos Darwin Core e os campos configurados no projeto."
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
                        label = {
                            Text(
                                when (format) {
                                    ExportFormat.Zip -> ui("ZIP completo recomendado")
                                    ExportFormat.DwcArchive -> ui("Darwin Core Archive (GBIF/SiBBr)")
                                    else -> format.name.uppercase()
                                }
                            )
                        }
                    )
                }
                if (selectedFormat == ExportFormat.DwcArchive) {
                    HelpCard(
                        "Darwin Core Archive",
                        "Pacote com meta.xml e eml.xml, no formato que o IPT, o GBIF e o SiBBr " +
                            "importam sem retrabalho. Campos próprios do Oikos saem como " +
                            "MeasurementOrFact. As mídias entram como Simple Multimedia quando o " +
                            "projeto tem URL pública das mídias. Licença, autor e URL ficam em Editar projeto."
                    )
                }
            }
            Button(
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = !exporting,
                onClick = {
                    if (sessionId == null) viewModel.exportProject(projectId, selectedFormat, selectedContent, onShare)
                    else viewModel.exportSession(projectId, sessionId, selectedFormat, selectedContent, onShare)
                }
            ) {
                Icon(Icons.Default.Share, null)
                Spacer(Modifier.width(8.dp))
                Text(if (exporting) ui("Exportando...") else ui("Exportar e compartilhar/salvar"))
            }
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = !exporting,
                onClick = { viewModel.exportLabels(projectId, sessionId, onShare) }
            ) {
                Icon(Icons.Default.Print, null)
                Spacer(Modifier.width(8.dp))
                Text(ui("Etiquetas de herbário/coleção (PDF)"))
            }
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = !exporting,
                onClick = { viewModel.exportProjectPackage(projectId, sessionId, onShare) }
            ) {
                Icon(Icons.Default.Share, null)
                Spacer(Modifier.width(8.dp))
                Text(ui("Pacote para outro celular"))
            }
            if (exporting) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    ui("Montando o pacote. Com muitos áudios pode levar alguns minutos; não feche o app."),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                ui("Depois de tocar no botão, escolha WhatsApp, Google Drive, Files/Arquivos ou outro app na tela de compartilhamento do Android."),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Tela acesa enquanto grava para [target]; ao sair da tela (nao em rotacao),
 * a gravacao e salva. Fica no nivel da tela: dentro de um item de lista, rolar
 * a lista destruia o item e parava a gravacao no meio.
 */
@Composable
private fun RecordingScreenGuard(viewModel: AppViewModel, target: MediaTarget) {
    val context = LocalContext.current
    val view = LocalView.current
    val recording by viewModel.recording.collectAsState()
    val recordingHere = recording?.target == target
    DisposableEffect(recordingHere) {
        view.keepScreenOn = recordingHere
        onDispose { view.keepScreenOn = false }
    }
    DisposableEffect(target) {
        onDispose {
            val activity = context.findActivity()
            if (activity?.isChangingConfigurations != true && viewModel.recording.value?.target == target) {
                viewModel.stopRecording("Gravação salva ao sair da tela.")
            }
        }
    }
}

/**
 * Botoes de foto e audio, com o estado da gravacao.
 *
 * A gravacao pertence ao ViewModel: girar a tela nao a interrompe. Sair da tela
 * (ou o app ir para segundo plano) encerra e SALVA a gravacao. Antes, sair da
 * tela deixava o gravador rodando sem dono: o arquivo crescia sem parar, nunca
 * entrava na lista e o microfone ficava preso.
 */
@Composable
private fun MediaCaptureControls(
    viewModel: AppViewModel,
    project: ProjectEntity,
    session: SessionEntity,
    target: MediaTarget,
    occurrenceForName: MediaOccurrenceName?,
    onPhotoMetadata: (PhotoMetadata) -> Unit = {}
) {
    val context = LocalContext.current
    // Por destino: a descricao de uma midia nao passa para o registro seguinte.
    var mediaSubject by rememberSaveable(target) { mutableStateOf("") }
    var mediaDescription by rememberSaveable(target) { mutableStateOf("") }
    // Caminho da foto em andamento: salvo no Bundle porque o Android pode fechar
    // o app enquanto a camera esta aberta, e a foto se perdia na volta.
    var pendingPhotoPath by rememberSaveable { mutableStateOf<String?>(null) }
    val recording by viewModel.recording.collectAsState()
    val recordingHere = recording?.target == target
    val recordingElsewhere = recording != null && !recordingHere

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val path = pendingPhotoPath
        pendingPhotoPath = null
        if (path == null) return@rememberLauncherForActivityResult
        val file = File(path)
        if (success && file.length() > 0L) {
            viewModel.processCapturedPhoto(file, target, project.photoQuality, mediaSubject, mediaDescription, onPhotoMetadata)
        } else {
            file.delete()
        }
    }
    val importPhoto = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val baseName = mediaBaseName(project, session, occurrenceForName, mediaSubject, "FOTO")
            viewModel.importMedia(uri, "photo", target, baseName, project.photoQuality, mediaSubject, mediaDescription, onPhotoMetadata)
        }
    }
    val importAudio = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val baseName = mediaBaseName(project, session, occurrenceForName, mediaSubject, "AUDIO")
            viewModel.importMedia(uri, "audio", target, baseName, project.photoQuality, mediaSubject, mediaDescription)
        }
    }
    fun launchCameraCapture() {
        if (!viewModel.hasRoomForMedia()) return
        val baseName = mediaBaseName(project, session, occurrenceForName, mediaSubject, "FOTO")
        val file = viewModel.mediaFiles.createImageFile(project.id, session.id, baseName)
        pendingPhotoPath = file.absolutePath
        runCatching { takePicture.launch(viewModel.mediaFiles.uriFor(file)) }
            .onFailure {
                file.delete()
                pendingPhotoPath = null
                viewModel.showMessage("Nenhum app de câmera disponível.")
            }
    }
    var showConditions by rememberSaveable { mutableStateOf(false) }
    var microphone by rememberSaveable { mutableStateOf(viewModel.lastMicrophone) }
    var airTemperature by rememberSaveable { mutableStateOf("") }
    var relativeHumidity by rememberSaveable { mutableStateOf("") }
    var distance by rememberSaveable { mutableStateOf("") }
    fun startAudioRecording() {
        // Valor que nao e numero ("22 °C") era descartado sem aviso.
        conditionsProblem(airTemperature, relativeHumidity, distance)?.let {
            viewModel.showMessage(it)
            showConditions = true
            return
        }
        val baseName = mediaBaseName(project, session, occurrenceForName, mediaSubject, "AUDIO")
        val conditions = RecordingConditions(
            microphone = microphone.trim(),
            airTemperature = airTemperature.replace(',', '.').toDoubleOrNull(),
            relativeHumidity = relativeHumidity.replace(',', '.').toDoubleOrNull(),
            distanceToSourceMeters = distance.replace(',', '.').toDoubleOrNull()
        )
        viewModel.startRecording(target, baseName, mediaSubject, mediaDescription, conditions)
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCameraCapture() else viewModel.showMessage("Permissão de câmera negada.")
    }
    val audioPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startAudioRecording() else viewModel.showMessage("Permissão de áudio negada.")
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
            Button(
                enabled = !recordingElsewhere,
                onClick = {
                    if (recordingHere) {
                        viewModel.stopRecording()
                    } else if (hasPermission(context, Manifest.permission.RECORD_AUDIO)) {
                        startAudioRecording()
                    } else {
                        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
            ) {
                Icon(Icons.Default.Mic, null)
                Spacer(Modifier.width(6.dp))
                Text(if (recordingHere) ui("Parar e salvar") else "${ui("Gravar")} WAV")
            }
            OutlinedButton(onClick = { importAudio.launch("audio/*") }) { Icon(Icons.Default.FileUpload, null); Spacer(Modifier.width(6.dp)); Text(ui("Anexar áudio")) }
        }
        // Metadados acusticos recomendados por Gannon et al. (2022): temperatura
        // e umidade alteram parametros de vocalizacao; equipamento e distancia
        // sao necessarios para comparar niveis entre gravacoes.
        TextButton(onClick = { showConditions = !showConditions }) {
            Text(if (showConditions) ui("Ocultar condições da gravação") else ui("Condições da gravação (microfone, temperatura, umidade)"))
        }
        AnimatedVisibility(visible = showConditions) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextInput("Microfone ou gravador", microphone, help = "Lembrado para as próximas gravações. Exemplo: microfone do celular, Sennheiser ME66, Zoom H5.") { microphone = it }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextInput("Temperatura do ar (°C)", airTemperature, KeyboardType.Decimal, Modifier.weight(1f)) { airTemperature = it }
                    TextInput("Umidade (%)", relativeHumidity, KeyboardType.Decimal, Modifier.weight(1f)) { relativeHumidity = it }
                }
                TextInput("Distância até o animal (m)", distance, KeyboardType.Decimal) { distance = it }
                Text(
                    ui("Taxa de amostragem, bits, duração, fonte de áudio e modelo do aparelho são registrados automaticamente."),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        recording?.takeIf { recordingHere }?.let { state -> RecordingStatus(viewModel, state) }
        if (recordingElsewhere) {
            Text(ui("Há uma gravação em andamento em outra tela."), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
        }
    }
}

/** Tempo decorrido e nivel do sinal, para o pesquisador ver que esta gravando. */
@Composable
private fun RecordingStatus(viewModel: AppViewModel, state: RecordingState) {
    var elapsed by remember(state.startedAt) { mutableLongStateOf(0L) }
    var level by remember { mutableStateOf(0f) }
    LaunchedEffect(state.startedAt) {
        while (true) {
            elapsed = System.currentTimeMillis() - state.startedAt
            level = viewModel.recordingLevel()
            delay(200)
        }
    }
    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val seconds = elapsed / 1000
            Text(
                "● ${ui("Gravando")} ${String.format(Locale.US, "%02d:%02d", seconds / 60, seconds % 60)}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            LinearProgressIndicator(progress = { level }, modifier = Modifier.fillMaxWidth())
            Text(
                ui("A tela fica acesa durante a gravação. Sair da tela ou do app encerra e salva o áudio."),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
private fun InlineMediaCapture(
    viewModel: AppViewModel,
    project: ProjectEntity,
    session: SessionEntity,
    target: MediaTarget,
    occurrenceForName: MediaOccurrenceName?,
    pendingMedia: List<MediaEntity>,
    onPendingMediaChanged: (List<MediaEntity>) -> Unit,
    onPhotoMetadata: (PhotoMetadata) -> Unit = {}
) {
    val savedMedia by remember(viewModel, target.occurrenceId) { target.occurrenceId?.let { viewModel.observeOccurrenceMedia(it) } ?: kotlinx.coroutines.flow.flowOf(emptyList()) }.collectAsState(initial = emptyList())
    val isDraft = target.draftKey != null
    val media = if (isDraft) pendingMedia else savedMedia

    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            RecordingScreenGuard(viewModel, target)
            MediaCaptureControls(viewModel, project, session, target, occurrenceForName, onPhotoMetadata)
            if (media.isEmpty()) {
                EmptyState("Sem mídias.", "Fotos e áudios ficam em arquivos locais; o banco guarda apenas metadados.")
            } else {
                if (isDraft) {
                    Text(
                        ui("Estas mídias entram no registro ao salvar."),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                media.forEach { item ->
                    MediaListCard(
                        item,
                        permanentDelete = isDraft,
                        onUpdate = { updated ->
                            if (isDraft) onPendingMediaChanged(pendingMedia.map { if (it.filePath == item.filePath) updated else it })
                            else viewModel.updateMedia(updated)
                        },
                        onDelete = {
                            if (isDraft) {
                                onPendingMediaChanged(pendingMedia.filterNot { it.filePath == item.filePath })
                                viewModel.discardPendingMedia(listOf(item))
                            } else {
                                viewModel.deleteMedia(item)
                            }
                        }
                    )
                }
            }
        }
    }
}

private tailrec fun Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Espectrograma e reproducao do audio, para conferir em campo se o canto ficou
 * registrado (e sem vento ou clipping) antes de sair do ponto.
 */
@Composable
private fun SpectrogramDialog(media: MediaEntity, onDismiss: () -> Unit) {
    var maxKhz by rememberSaveable { mutableStateOf(12) }
    var image by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var result by remember { mutableStateOf<com.oikos.fieldbook.audio.SpectrogramResult?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(media.filePath, maxKhz) {
        image = null
        failed = false
        val computed = withContext(Dispatchers.Default) {
            runCatching { DecodedSpectrogram.compute(File(media.filePath), maxColumns = 900, maxFrequencyHz = maxKhz * 1000.0) }.getOrNull()
        }
        result = computed
        if (computed == null) {
            failed = true
        } else {
            image = withContext(Dispatchers.Default) {
                android.graphics.Bitmap.createBitmap(computed.pixels(), computed.columns, computed.bins, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
            }
        }
    }
    // Reproducao simples, com cursor sobre o espectrograma.
    val player = remember(media.filePath) { android.media.MediaPlayer() }
    var playing by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var playerReady by remember { mutableStateOf(false) }
    DisposableEffect(player) {
        runCatching {
            player.setDataSource(media.filePath)
            player.setOnCompletionListener { playing = false; progress = 0f }
            player.prepare()
            playerReady = true
        }
        onDispose { runCatching { player.release() } }
    }
    LaunchedEffect(playing) {
        while (playing) {
            val duration = player.duration.takeIf { it > 0 } ?: break
            progress = player.currentPosition.toFloat() / duration
            delay(50)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(media.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val current = image
                val data = result
                when {
                    failed -> Text(ui("Não foi possível ler este áudio."), color = MaterialTheme.colorScheme.error)
                    current == null || data == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(ui("Calculando o espectrograma..."))
                    }
                    else -> {
                        Row {
                            Column(Modifier.height(180.dp), verticalArrangement = Arrangement.SpaceBetween) {
                                Text(String.format(Locale.US, "%.0f kHz", data.maxFrequencyHz / 1000), style = MaterialTheme.typography.labelSmall)
                                Text(String.format(Locale.US, "%.0f", data.maxFrequencyHz / 2000), style = MaterialTheme.typography.labelSmall)
                                Text("0", style = MaterialTheme.typography.labelSmall)
                            }
                            Spacer(Modifier.width(4.dp))
                            BoxWithConstraints(Modifier.weight(1f).height(180.dp)) {
                                Image(
                                    bitmap = current,
                                    contentDescription = ui("Espectrograma"),
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.FillBounds,
                                    filterQuality = androidx.compose.ui.graphics.FilterQuality.Low
                                )
                                if (progress > 0f) {
                                    Box(
                                        Modifier
                                            .offset { androidx.compose.ui.unit.IntOffset((maxWidth * progress.coerceIn(0f, 1f)).roundToPx(), 0) }
                                            .fillMaxHeight()
                                            .width(2.dp)
                                            .background(Color.White)
                                    )
                                }
                            }
                        }
                        Text(
                            "0 s — ${String.format(Locale.US, "%.1f s", data.durationSeconds)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(ui("Até"), style = MaterialTheme.typography.labelMedium)
                            listOf(6, 12, 24).forEach { khz ->
                                FilterChip(selected = maxKhz == khz, onClick = { maxKhz = khz }, label = { Text("$khz kHz", maxLines = 1) })
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(enabled = playerReady, onClick = {
                if (playing) {
                    player.pause()
                    playing = false
                } else {
                    player.start()
                    playing = true
                }
            }) { Text(ui(if (playing) "Pausar" else "Ouvir")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(ui("Fechar")) } }
    )
}

@Composable
private fun MediaListCard(
    media: MediaEntity,
    onUpdate: (MediaEntity) -> Unit = {},
    permanentDelete: Boolean = false,
    /** Especie (ou occurrenceID) atual da ocorrencia ligada, na galeria da expedicao. */
    linkedName: String? = null,
    onDelete: () -> Unit
) {
    var editing by remember(media.id, media.filePath) { mutableStateOf(false) }
    // Um toque no lixo apagava na hora; a midia de rascunho, de vez.
    var confirmDelete by remember(media.id, media.filePath) { mutableStateOf(false) }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(ui("Remover esta mídia?")) },
            text = {
                Text(
                    if (permanentDelete) ui("Ela ainda não foi salva no registro e será apagada de vez.")
                    else ui("Ela fica na Lixeira por 30 dias e pode ser restaurada.")
                )
            },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text(ui("Remover")) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(ui("Cancelar")) } }
        )
    }
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
                val bitmap = rememberThumbnail(media.filePath, media.createdAt)
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
                var showSpectrogram by remember(media.id) { mutableStateOf(false) }
                if (showSpectrogram) SpectrogramDialog(media) { showSpectrogram = false }
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                        .clickable { showSpectrogram = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.GraphicEq, contentDescription = ui("Ouvir e ver o espectrograma"), tint = MaterialTheme.colorScheme.primary)
                }
            }
            Column(Modifier.weight(1f)) {
                // Tipo e momento da captura, em vez do nome do arquivo: o nome
                // guardado nao acompanha a especie corrigida depois.
                Text(
                    "${if (media.mediaType == "audio") ui("Áudio") else ui("Foto")} · ${mediaCaptureLabel(media)}",
                    style = MaterialTheme.typography.titleSmall
                )
                if (media.caption.isNotBlank()) Text(media.caption, style = MaterialTheme.typography.bodySmall)
                Text(
                    media.mediaSubject.ifBlank { ui("Toque para descrever esta mídia.") },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
                mediaTechnicalSummary(media).takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                Text(
                    when {
                        media.occurrenceId == null -> ui("Mídia geral da expedição")
                        linkedName != null -> "${ui("Ocorrência")}: $linkedName"
                        else -> ui("Mídia vinculada a uma ocorrência")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(media.fileName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = { editing = true }) {
                Icon(Icons.Default.Edit, ui("Editar mídia"))
            }
            IconButton(onClick = { confirmDelete = true }) {
                Icon(Icons.Default.Delete, ui("Remover mídia"))
            }
        }
    }
}

/** "12 s · 44.1 kHz / 16 bit / mono · UNPROCESSED · 22 °C · 85 %" */
/** Momento da captura para o cartao; arquivo importado sem data fica "data desconhecida". */
@Composable
private fun mediaCaptureLabel(media: MediaEntity): String =
    if (media.captureTimeKnown) {
        Instant.ofEpochMilli(media.createdAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
    } else {
        ui("data de captura desconhecida")
    }

private fun mediaTechnicalSummary(media: MediaEntity): String {
    if (media.mediaType != "audio") return media.captureDevice
    val duration = media.durationMillis?.let { millis ->
        val seconds = millis / 1000
        if (seconds >= 60) String.format(Locale.US, "%d min %02d s", seconds / 60, seconds % 60) else "$seconds s"
    }
    return listOfNotNull(
        duration,
        com.oikos.fieldbook.services.AudioInfo(media.sampleRateHz, media.bitsPerSample, media.channels, null).summary().takeIf { it.isNotBlank() },
        media.audioSource.takeIf { it.isNotBlank() },
        media.airTemperature?.let { "${formatPlain(it)} °C" },
        media.relativeHumidity?.let { "${formatPlain(it)} % UR" },
        media.distanceToSourceMeters?.let { "${formatPlain(it)} m" },
        media.microphone.takeIf { it.isNotBlank() }
    ).joinToString(" · ")
}

@Composable
private fun EditMediaDialog(media: MediaEntity, onDismiss: () -> Unit, onSave: (MediaEntity) -> Unit) {
    var subject by remember(media.id, media.filePath) { mutableStateOf(media.mediaSubject) }
    var description by remember(media.id, media.filePath) { mutableStateOf(media.caption) }
    var microphone by remember(media.id, media.filePath) { mutableStateOf(media.microphone) }
    var temperature by remember(media.id, media.filePath) { mutableStateOf(media.airTemperature?.let { formatPlain(it) }.orEmpty()) }
    var humidity by remember(media.id, media.filePath) { mutableStateOf(media.relativeHumidity?.let { formatPlain(it) }.orEmpty()) }
    var distance by remember(media.id, media.filePath) { mutableStateOf(media.distanceToSourceMeters?.let { formatPlain(it) }.orEmpty()) }
    var problem by remember(media.id) { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(ui("Editar mídia")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(media.fileName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextInput(
                    "O que esta mídia mostra ou registra?",
                    subject,
                    help = "Exemplos: indivíduo inteiro, construção/vestígio, folha, flor, microhabitat, canto reprodutivo, observação narrada."
                ) { subject = it }
                TextInput("Descrição da mídia", description, minLines = 2) { description = it }
                if (media.mediaType == "audio") {
                    TextInput("Microfone ou gravador", microphone) { microphone = it }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextInput("Temperatura do ar (°C)", temperature, KeyboardType.Decimal, Modifier.weight(1f)) { temperature = it }
                        TextInput("Umidade (%)", humidity, KeyboardType.Decimal, Modifier.weight(1f)) { humidity = it }
                    }
                    TextInput("Distância até o animal (m)", distance, KeyboardType.Decimal) { distance = it }
                }
                problem?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                // Antes "22 °C" virava vazio e apagava a temperatura ja salva.
                problem = if (media.mediaType == "audio") conditionsProblem(temperature, humidity, distance) else null
                if (problem != null) return@TextButton
                onSave(
                    media.copy(
                        mediaSubject = subject.trim(),
                        caption = description.trim(),
                        notes = description.trim(),
                        microphone = microphone.trim(),
                        airTemperature = temperature.replace(',', '.').toDoubleOrNull(),
                        relativeHumidity = humidity.replace(',', '.').toDoubleOrNull(),
                        distanceToSourceMeters = distance.replace(',', '.').toDoubleOrNull()
                    )
                )
            }) { Text(ui("Salvar")) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(ui("Cancelar")) }
        }
    )
}

/** Condicoes de gravacao digitadas que nao sao numero valido. */
private fun conditionsProblem(temperature: String, humidity: String, distance: String): String? {
    fun parse(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()
    return when {
        temperature.isNotBlank() && parse(temperature) == null -> "Temperatura do ar: digite só o número em °C (por exemplo 22,5)."
        humidity.isNotBlank() && parse(humidity)?.takeIf { it in 0.0..100.0 } == null -> "Umidade: digite um número de 0 a 100."
        distance.isNotBlank() && parse(distance)?.takeIf { it >= 0.0 } == null -> "Distância: digite só o número em metros."
        else -> null
    }
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
    onChecklists: () -> Unit,
    onTrash: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val installedChecklists by viewModel.checklists.collectAsState(initial = emptyList())
    // Os quatro tamanhos exigem percorrer recursivamente o armazenamento do
    // app. Eram calculados durante a composicao, na main thread: com muitas
    // fotos isso segura a interface e pode gerar ANR. Agora sao medidos em
    // Dispatchers.IO e a tela abre imediatamente com zero.
    var cache by remember { mutableLongStateOf(0L) }
    var mapCache by remember { mutableLongStateOf(0L) }
    var files by remember { mutableLongStateOf(0L) }
    var db by remember { mutableLongStateOf(0L) }
    var lists by remember { mutableLongStateOf(0L) }
    var maps by remember { mutableLongStateOf(0L) }
    var medindoArmazenamento by remember { mutableStateOf(true) }
    var recarregarArmazenamento by remember { mutableIntStateOf(0) }
    LaunchedEffect(recarregarArmazenamento) {
        medindoArmazenamento = true
        val medidas = withContext(Dispatchers.IO) {
            listOf(
                viewModel.mediaFiles.cacheSizeBytes(),
                viewModel.mediaFiles.mapCacheSizeBytes(),
                viewModel.mediaFiles.projectFilesSizeBytes(),
                viewModel.mediaFiles.databaseSizeBytes(),
                viewModel.mediaFiles.checklistsSizeBytes(),
                viewModel.mediaFiles.offlineMapsSizeBytes()
            )
        }
        cache = medidas[0]
        mapCache = medidas[1]
        files = medidas[2]
        db = medidas[3]
        lists = medidas[4]
        maps = medidas[5]
        medindoArmazenamento = false
    }
    var confirmClearCache by remember { mutableStateOf(false) }
    var confirmClearMapCache by remember { mutableStateOf(false) }
    var reverseGeocoding by remember { mutableStateOf(viewModel.reverseGeocodingEnabled) }
    if (confirmClearCache) {
        ConfirmDeleteDialog(
            title = "Limpar cache?",
            text = "Isso apaga exportações temporárias e arquivos de apoio. Projetos, expedições, ocorrências, fotos e áudios não serão removidos.",
            confirmText = "Limpar cache",
            onDismiss = { confirmClearCache = false },
            onConfirm = {
                confirmClearCache = false
                val removed = viewModel.mediaFiles.clearCache()
                recarregarArmazenamento++
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
                recarregarArmazenamento++
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
            BackupCard(viewModel)
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), elevation = CardDefaults.cardElevation(2.dp)) {
                Row(Modifier.fillMaxWidth().clickable(onClick = onChecklists).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(ui("Listas de espécies"), style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (installedChecklists.isEmpty()) ui("Nenhuma instalada. Baixe a Flora e Funga do Brasil ou a Fauna do Brasil para ter autocompletar do nome científico em campo.")
                            else ui("versão").let { word -> installedChecklists.joinToString("\n") { "${it.title} · $word ${it.version}" } },
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(onClick = onChecklists) { Text(ui("Gerenciar")) }
                }
            }
            OfflineMapsCard(viewModel)
            TrackProfileCard(viewModel)
            val trashItems by viewModel.trashItems.collectAsState()
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), elevation = CardDefaults.cardElevation(2.dp)) {
                Row(Modifier.fillMaxWidth().clickable(onClick = onTrash).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(ui("Lixeira"), style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (trashItems.isEmpty()) ui("Vazia. O que for apagado fica aqui por 30 dias.")
                            else "${trashItems.size} ${ui(if (trashItems.size == 1) "item" else "itens")} · ${ui("restauráveis por 30 dias")}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(onClick = onTrash) { Text(ui("Abrir")) }
                }
            }
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
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(ui("Sugerir endereço pelo Google"), style = MaterialTheme.typography.titleMedium)
                        Text(
                            ui("No Brasil, município e estado já são sugeridos sem internet, pela malha do IBGE. Esta opção serve para coletas fora do Brasil e usa a internet: a coordenada capturada vai ao serviço de endereços do Google. Deixe desligada em locais de espécies sensíveis."),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = reverseGeocoding, onCheckedChange = {
                        reverseGeocoding = it
                        viewModel.reverseGeocodingEnabled = it
                    })
                }
            }
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), elevation = CardDefaults.cardElevation(2.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Os antigos controles de "area" e "detalhe" do cache eram
                    // gravados e nunca lidos: nao faziam nada. O cache real e o
                    // dos blocos de mapa ja vistos, descrito aqui como e.
                    Text(ui("Mapa e uso offline"), style = MaterialTheme.typography.titleMedium)
                    Text(
                        ui("A coleta, o GPS e a trilha funcionam sem internet. O mapa de fundo guarda os trechos já vistos: abra o mapa da área de trabalho enquanto houver internet e ele continua disponível em campo."),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text("${ui("Cache do mapa")}: ${humanSize(mapCache)}")
                    OutlinedButton(onClick = { confirmClearMapCache = true }) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(ui("Apagar cache do mapa"))
                    }
                }
            }
            if (medindoArmazenamento) {
                Text(
                    ui("Calculando o espaço usado..."),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text("${ui("Arquivos de projetos")}: ${humanSize(files)}")
            Text("${ui("Banco local")}: ${humanSize(db)}")
            if (lists > 0) Text("${ui("Listas de espécies")}: ${humanSize(lists)}")
            if (maps > 0) Text("${ui("Mapas offline")}: ${humanSize(maps)}")
            Text("${ui("Cache/exportações temporárias")}: ${humanSize(cache)}")
            Button(onClick = { confirmClearCache = true }) { Text(ui("Limpar cache")) }
            DeveloperSignature(Modifier.fillMaxWidth().padding(top = 20.dp))
        }
    }
}

/**
 * Listas de especies baixadas da fonte oficial para uso offline. Alimentam o
 * autocompletar do nome cientifico, a autoria, a familia e o aviso de sinonimo.
 */
@Composable
private fun ChecklistsScreen(viewModel: AppViewModel, modifier: Modifier, onBack: () -> Unit) {
    val installed by viewModel.checklists.collectAsState(initial = emptyList())
    val progress by viewModel.checklistProgress.collectAsState()
    val latest by viewModel.latestVersions.collectAsState()
    var toRemove by remember { mutableStateOf<ChecklistEntity?>(null) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.importChecklistFile(it) }
    }
    LaunchedEffect(Unit) { viewModel.checkChecklistUpdates() }

    toRemove?.let { checklist ->
        ConfirmDeleteDialog(
            title = "Remover lista?",
            text = "Os registros já feitos não mudam. O autocompletar deixa de usar esta lista até você baixá-la de novo.",
            confirmText = "Remover lista",
            onDismiss = { toRemove = null },
            onConfirm = {
                toRemove = null
                viewModel.removeChecklist(checklist)
            }
        )
    }

    Scaffold(modifier = modifier, topBar = { OikosTopBar("Listas de espécies", onBack) }) { padding ->
        LazyColumn(
            contentPadding = padding.plus(PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp)),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                HelpCard(
                    "Listas de espécies",
                    "Baixe uma vez, com internet, e use em campo sem rede: o nome científico é sugerido enquanto você digita, a autoria e a família são preenchidas e sinônimos são trocados pelo nome aceito. As listas vêm direto da fonte oficial, e o app confere se há versão nova sempre que houver internet."
                )
            }
            items(ChecklistSources.all, key = { it.key }) { source ->
                ChecklistSourceCard(
                    source = source,
                    installed = installed.firstOrNull { it.sourceKey == source.key },
                    progress = progress[source.key],
                    latestVersion = latest[source.key],
                    onDownload = { viewModel.downloadChecklist(source) },
                    onRemove = { toRemove = it }
                )
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(ui("Outra lista (arquivo)"), style = MaterialTheme.typography.titleMedium)
                        Text(
                            ui("Qualquer lista publicada como Darwin Core Archive com núcleo Taxon, como as de outros países no GBIF, no ChecklistBank ou em um IPT. Baixe o arquivo .zip e importe aqui."),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val importing = progress["arquivo"]
                        OutlinedButton(enabled = importing == null, onClick = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }) {
                            Icon(Icons.Default.FileUpload, null); Spacer(Modifier.width(6.dp)); Text(ui("Importar arquivo"))
                        }
                        importing?.let { ProgressLine(it) }
                    }
                }
            }
            items(installed.filter { it.sourceKey.startsWith("arquivo:") }, key = { it.id }) { checklist ->
                Card {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(checklist.title, style = MaterialTheme.typography.titleMedium)
                        Text(installedSummary(checklist), style = MaterialTheme.typography.labelMedium)
                        if (checklist.citation.isNotBlank()) Text(checklist.citation, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { toRemove = checklist }) { Text(ui("Remover")) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChecklistSourceCard(
    source: ChecklistSource,
    installed: ChecklistEntity?,
    progress: AppViewModel.BackupProgress?,
    latestVersion: RemoteVersion?,
    onDownload: () -> Unit,
    onRemove: (ChecklistEntity) -> Unit
) {
    val updateAvailable = installed != null && latestVersion?.isNewerThan(installed) == true
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(source.title, style = MaterialTheme.typography.titleMedium)
            Text(ui(source.scope), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(ui(source.description), color = MaterialTheme.colorScheme.onSurfaceVariant)
            when {
                installed == null -> Text("${ui("Não instalada")} · ${ui("cerca de")} ${source.approxDownloadMb} MB ${ui("para baixar")} · ${ui("ocupa")} ~${source.approxInstalledMb} MB ${ui("no aparelho")}", style = MaterialTheme.typography.labelMedium)
                updateAvailable -> Text("${installedSummary(installed)}\n${ui("Versão nova disponível")}: ${latestVersion?.label}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                else -> Text(installedSummary(installed), style = MaterialTheme.typography.labelMedium)
            }
            if (installed != null && ChecklistSources.isMirror(installed.sourceUrl)) {
                Text(
                    ui("Baixada da cópia guardada no ChecklistBank, porque o IPT do JBRJ recusou a conexão. A cópia pode estar algumas semanas atrás do IPT."),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (installed != null && installed.citation.isNotBlank()) {
                Text("${ui("Citação")}: ${installed.citation}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (progress != null) {
                ProgressLine(progress)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    when {
                        installed == null -> Button(onClick = onDownload) { Text(ui("Baixar")) }
                        updateAvailable -> Button(onClick = onDownload) { Text(ui("Atualizar")) }
                        else -> OutlinedButton(onClick = onDownload) { Text(ui("Baixar de novo")) }
                    }
                    if (installed != null) TextButton(onClick = { onRemove(installed) }) { Text(ui("Remover")) }
                }
            }
        }
    }
}

@Composable
private fun ProgressLine(progress: AppViewModel.BackupProgress) {
    // "Baixando 12 MB": so a palavra muda de idioma.
    val label = progress.label
    val prefix = listOf("Baixando", "Copiando o mapa:").firstOrNull { label.startsWith("$it ") }
    Text(if (prefix != null) ui(prefix) + label.removePrefix(prefix) else ui(label), style = MaterialTheme.typography.labelMedium)
    val fraction = progress.fraction
    if (fraction != null) LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
    else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
}

@Composable
private fun installedSummary(checklist: ChecklistEntity): String {
    val date = runCatching { LocalDate.parse(checklist.pubDate.trim()).format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) }.getOrDefault(checklist.pubDate)
    return listOfNotNull(
        "${ui("Instalada")}: ${ui("versão")} ${checklist.version.ifBlank { "?" }}",
        date.takeIf { it.isNotBlank() }?.let { "${ui("publicada em")} $it" },
        "${String.format(if (LocalOikosLanguage.current == OikosLanguage.Portuguese) Locale.forLanguageTag("pt-BR") else Locale.US, "%,d", checklist.taxonCount)} ${ui("nomes")}",
        checklist.license.takeIf { it.isNotBlank() }
    ).joinToString(" · ")
}

/** Uma especie na contagem rapida. */
private data class TallyRow(
    val name: String,
    val vernacular: String,
    /** Campos do taxon quando o nome veio da lista (familia, autoria...). */
    val fields: Map<String, String>,
    val count: Int
)

private fun tallyToJson(rows: List<TallyRow>): String = JSONArray(rows.map { row ->
    JSONObject().put("name", row.name).put("vernacular", row.vernacular).put("count", row.count).put("fields", JSONObject(row.fields))
}).toString()

private fun tallyFromJson(json: String): List<TallyRow> = runCatching {
    val array = JSONArray(json)
    List(array.length()) { index ->
        val item = array.getJSONObject(index)
        val fields = item.optJSONObject("fields")
        TallyRow(
            name = item.optString("name"),
            vernacular = item.optString("vernacular"),
            fields = fields?.keys()?.asSequence()?.associateWith { fields.optString(it) }.orEmpty(),
            count = item.optInt("count")
        )
    }
}.getOrDefault(emptyList())

/**
 * Contagem rapida (ponto de escuta, transecto, censo): um toque por individuo,
 * sem tirar os olhos do campo. Ao salvar, cada especie vira um registro com o
 * numero de individuos, o intervalo de horario da contagem e o ponto. O
 * rascunho sobrevive a rotacao e ao Android fechar o app.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TallyScreen(
    viewModel: AppViewModel,
    projectId: Long,
    sessionId: Long,
    modifier: Modifier,
    requestLocation: ((CapturedLocation?) -> Unit) -> Unit,
    onBack: () -> Unit
) {
    val project by remember(viewModel, projectId) { viewModel.observeProject(projectId) }.collectAsState(initial = null)
    val session by remember(viewModel, sessionId) { viewModel.observeSession(sessionId) }.collectAsState(initial = null)
    val form = rememberFormState("tally:$sessionId")
    LaunchedEffect(form.initialized) {
        if (!form.initialized) form.load(mapOf("start" to System.currentTimeMillis().toString()))
    }
    val rowsJson = form["rows"]
    val rows = remember(rowsJson) { tallyFromJson(rowsJson.ifBlank { "[]" }) }
    // Sempre a partir do que esta salvo no rascunho: a lista capturada na tela
    // podia estar velha (um "+" tocado enquanto um nome da lista era resolvido se perdia).
    fun currentRows(): List<TallyRow> = tallyFromJson(form["rows"].ifBlank { "[]" })
    fun update(newRows: List<TallyRow>) {
        form["rows"] = tallyToJson(newRows)
    }
    var savingTally by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var showGps by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val total = rows.sumOf { it.count }
    val counted = rows.filter { it.count > 0 }
    val start = form["start"].toLongOrNull()
    val modules =project?.enabledOccurrenceModules().orEmpty().ifEmpty { listOf("Fauna") }
    val module = form["module"].takeIf { it in modules } ?: if ("Fauna" in modules) "Fauna" else modules.first()
    val guardedBack = rememberGuardedBack(dirty = total > 0, onDiscard = { form.reset() }, onBack = onBack)

    fun add(name: String, vernacular: String, fields: Map<String, String>) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        val rows = currentRows()
        val index = rows.indexOfFirst { it.name.equals(name, ignoreCase = true) }
        // Especie nova entra no topo, onde esta o dedo; repetida soma um.
        update(
            if (index >= 0) rows.mapIndexed { i, row -> if (i == index) row.copy(count = row.count + 1) else row }
            else listOf(TallyRow(name, vernacular, fields, 1)) + rows
        )
        query = ""
    }

    fun save() {
        val currentProject = project ?: return
        val currentSession = session ?: return
        if (counted.isEmpty() || savingTally) return
        savingTally = true
        scope.launch {
            val previous = viewModel.occurrencesInSession(sessionId).size
            val end = System.currentTimeMillis()
            fun clock(millis: Long): String =
                Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))
            val begin = clock(start ?: end)
            // Intervalo ISO 8601, aceito em dwc:eventTime ("07:42/07:52").
            val eventTime = if (begin == clock(end)) begin else "$begin/${clock(end)}"
            val eventDate = normalizeDateInput(currentSession.eventDate) ?: LocalDate.now().toString()
            val georeference = mapOf(
                "georeferenceProtocol" to form["protocol"],
                "georeferenceRemarks" to form["georemarks"],
                // Altitude da contagem vem sempre do GPS, ja no nivel do mar (EGM2008).
                "verticalDatum" to if (form["alt"].isNotBlank()) Geoid.VERTICAL_DATUM else ""
            ).filterValues { it.isNotBlank() }
            // Na ordem em que as especies foram detectadas.
            val entities = counted.reversed().mapIndexed { index, row ->
                OccurrenceEntity(
                    projectId = projectId,
                    sessionId = sessionId,
                    occurrenceId = semanticOccurrenceId(currentProject, currentSession, previous + index + 1),
                    scientificName = row.name,
                    vernacularName = row.vernacular,
                    formType = module,
                    occurrenceType = occurrenceTypesFor(module).first(),
                    basisOfRecord = "HumanObservation",
                    occurrenceStatus = "present",
                    individualCount = row.count,
                    latitude = form["lat"].toDoubleOrNull(),
                    longitude = form["lon"].toDoubleOrNull(),
                    altitude = form["alt"].toDoubleOrNull(),
                    coordinateUncertaintyInMeters = form["accuracy"].toDoubleOrNull(),
                    geodeticDatum = currentSession.geodeticDatum,
                    fieldNotes = "Contagem rápida",
                    dynamicFieldsJson = flatJson(row.fields.filterValues { it.isNotBlank() } + georeference + mapOf("eventDate" to eventDate, "eventTime" to eventTime)),
                    createdAt = end + index,
                    updatedAt = end + index
                )
            }
            viewModel.saveOccurrences(entities, onFailed = { savingTally = false }) {
                savingTally = false
                form.reset()
                onBack()
            }
        }
    }

    if (showGps) {
        GpsCaptureDialog(viewModel, onDismiss = { showGps = false }) { fix ->
            showGps = false
            form["lat"] = fix.latitude.toString()
            form["lon"] = fix.longitude.toString()
            form["alt"] = fix.altitude?.let { formatPlain(it) }.orEmpty()
            form["accuracy"] = formatPlain(fix.uncertainty)
            form["protocol"] = GpsAveraging.GBIF_PROTOCOL
            form["georemarks"] = fix.remarks()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = { OikosTopBar("Contagem rápida", guardedBack) },
        bottomBar = {
            if (!WindowInsets.isImeVisible) {
                Surface(tonalElevation = 3.dp) {
                    Button(
                        onClick = ::save,
                        enabled = counted.isNotEmpty() && project != null && session != null && !savingTally,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).height(52.dp)
                    ) {
                        Icon(Icons.Default.Save, null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (counted.isEmpty()) ui("Conte ao menos um indivíduo")
                            else "${ui("Salvar")} ${counted.size} ${ui(if (counted.size == 1) "registro" else "registros")} · $total ${ui(if (total == 1) "indivíduo" else "indivíduos")}"
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                ui("Toque em + a cada indivíduo visto ou ouvido. Ao salvar, cada espécie vira um registro com o número de indivíduos, o horário da contagem e o ponto."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TallyClock(start, "${rows.size} ${ui(if (rows.size == 1) "espécie" else "espécies")} · $total ${ui(if (total == 1) "indivíduo" else "indivíduos")}")
            if (modules.size > 1) ChipRow("Grupo do registro", modules.map { it to moduleDisplayName(it) }, module) { form["module"] = it }
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(ui("Ponto da contagem"), style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (form["lat"].isBlank()) ui("Sem coordenada: os registros serão salvos sem ponto.")
                            else "${form["lat"]}, ${form["lon"]} ± ${form["accuracy"].ifBlank { "?" }} m",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(onClick = {
                        if (hasAnyLocationPermission(context)) {
                            showGps = true
                        } else {
                            requestLocation { location ->
                                location?.let {
                                    form["lat"] = it.latitude.toString()
                                    form["lon"] = it.longitude.toString()
                                    form["alt"] = it.altitude?.let { value -> formatPlain(value) }.orEmpty()
                                    form["accuracy"] = formatPlain(GpsAveraging.uncertaintyFor(it.accuracyMeters))
                                    form["protocol"] = GpsAveraging.GBIF_PROTOCOL
                                    form["georemarks"] = "$SINGLE_FIX_REMARKS_PREFIX; precisão informada ${it.accuracyMeters?.let { a -> formatPlain(a) + " m" } ?: "não informada"}; incerteza = 2 × precisão."
                                }
                            }
                        }
                    }) {
                        Icon(Icons.Default.GpsFixed, null)
                        Spacer(Modifier.width(6.dp))
                        Text(ui("GPS"))
                    }
                }
            }
            ScientificNameField(
                viewModel = viewModel,
                label = "Adicionar espécie",
                value = query,
                authorship = "",
                onValueChange = { query = it },
                onChoose = { suggestion ->
                    scope.launch {
                        val chosen = resolveSuggestion(viewModel, suggestion)
                        add(chosen.scientificName, chosen.vernacular.orEmpty(), chosen.fields)
                        chosen.synonymNotice?.let { viewModel.showMessage(it) }
                    }
                }
            )
            if (query.isNotBlank()) {
                OutlinedButton(onClick = { add(query.trim(), "", emptyMap()) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(6.dp))
                    Text("${ui("Adicionar como digitado")}: ${query.trim()}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (rows.isEmpty()) {
                EmptyState("Nenhuma espécie ainda.", "Digite o nome acima. Espécies sem nome definido podem entrar como \"Turdus sp.\" ou \"morfoespécie 1\".")
            }
            rows.forEachIndexed { index, row ->
                TallyRowCard(
                    row = row,
                    onMinus = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        update(
                            if (row.count > 0) currentRows().map { r -> if (r.name == row.name) r.copy(count = r.count - 1) else r }
                            else currentRows().filterNot { r -> r.name == row.name }
                        )
                    },
                    onPlus = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        update(currentRows().map { r -> if (r.name == row.name) r.copy(count = r.count + 1) else r })
                    }
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/**
 * Inicio e tempo decorrido da contagem (protocolos de ponto fixam 5 ou 10 min).
 * Fica isolado: o tique de cada segundo redesenha so esta linha, nao a tela.
 */
@Composable
private fun TallyClock(start: Long?, summary: String) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val elapsed = ((now - (start ?: now)) / 1000).coerceAtLeast(0)
    val begin = start?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")) } ?: "--:--"
    Text(
        "${ui("Início")} $begin · ${String.format(Locale.US, "%02d:%02d", elapsed / 60, elapsed % 60)} · $summary",
        style = MaterialTheme.typography.titleSmall
    )
}

@Composable
private fun TallyRowCard(row: TallyRow, onMinus: () -> Unit, onPlus: () -> Unit) {
    Card {
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                val authorship = row.fields["scientificNameAuthorship"].orEmpty()
                Text(
                    androidx.compose.ui.text.buildAnnotatedString {
                        if (row.fields.isNotEmpty()) {
                            pushStyle(androidx.compose.ui.text.SpanStyle(fontStyle = FontStyle.Italic))
                            append(row.name.removeSuffix(authorship).trim())
                            pop()
                            if (authorship.isNotBlank()) append(" $authorship")
                        } else {
                            append(row.name)
                        }
                    },
                    style = MaterialTheme.typography.bodyLarge
                )
                val details = listOf(row.vernacular, row.fields["family"].orEmpty()).filter { it.isNotBlank() }.joinToString(" · ")
                if (details.isNotBlank()) Text(details, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onMinus, modifier = Modifier.size(48.dp)) {
                if (row.count > 0) Icon(Icons.Default.Remove, ui("Menos um indivíduo"))
                else Icon(Icons.Default.Delete, ui("Tirar da contagem"))
            }
            Text(
                "${row.count}",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(min = 40.dp)
            )
            FilledIconButton(onClick = onPlus, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Default.Add, ui("Mais um indivíduo"))
            }
        }
    }
}

/** Mostra o que vem repetido do registro anterior; tocar abre a escolha dos campos. */
@Composable
private fun RepeatBanner(options: List<RepeatOption>, selected: Set<String>, onEdit: () -> Unit) {
    val active = options.filter { it.key in selected }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onEdit).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Default.Repeat, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    ui(if (active.isEmpty()) "Repetir campos do registro anterior" else "Repetindo do registro anterior"),
                    style = MaterialTheme.typography.titleSmall
                )
                val labels = active.map { ui(it.label) }
                Text(
                    if (active.isEmpty()) ui("Escolha uma vez o que não muda entre registros: coletor, ponto, habitat...")
                    else labels.joinToString(", "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(ui(if (active.isEmpty()) "Escolher" else "Alterar"), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/**
 * Escolha dos campos a repetir. A escolha vale para o projeto todo; campos que
 * o registro anterior nao tem continuam marcados, so nao aparecem aqui.
 */
@Composable
private fun RepeatFieldsDialog(
    options: List<RepeatOption>,
    selected: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit
) {
    var chosen by remember { mutableStateOf(selected) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(ui("Repetir do registro anterior")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    ui("Os campos marcados são copiados agora e em cada novo registro deste projeto. Data, hora e identificador nunca são repetidos."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                options.forEach { option ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { chosen = if (option.key in chosen) chosen - option.key else chosen + option.key }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = option.key in chosen, onCheckedChange = null)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(ui(option.label), style = MaterialTheme.typography.bodyMedium)
                            Text(
                                option.preview,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onConfirm(chosen) }) { Text(ui("Repetir")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(ui("Cancelar")) } }
    )
}

/**
 * Campo de nome cientifico com autocompletar a partir das listas instaladas.
 * "boa fab" sugere Boana faber; nomes populares tambem sao buscados. Sinonimos
 * aparecem marcados e, ao escolher, o registro recebe o nome aceito.
 */
@OptIn(ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ScientificNameField(
    viewModel: AppViewModel,
    label: String,
    value: String,
    authorship: String,
    onValueChange: (String) -> Unit,
    onChoose: (TaxonSuggestion) -> Unit
) {
    var suggestions by remember { mutableStateOf<List<TaxonSuggestion>>(emptyList()) }
    var focused by remember { mutableStateOf(false) }
    // Escolher uma sugestao muda o valor do campo; essa mudanca nao reabre a lista.
    var suppressNext by remember { mutableStateOf(false) }
    var known by remember { mutableStateOf<com.oikos.fieldbook.checklist.NameCheck?>(null) }
    // Em celular pequeno a lista caia atras do teclado.
    val suggestionsView = remember { BringIntoViewRequester() }
    LaunchedEffect(suggestions, focused) {
        if (focused && suggestions.isNotEmpty()) {
            delay(80)
            suggestionsView.bringIntoView()
        }
    }
    LaunchedEffect(value) {
        if (suppressNext) {
            suppressNext = false
            suggestions = emptyList()
        } else {
            delay(220)
            suggestions = if (value.trim().length >= 2) viewModel.searchNames(value) else emptyList()
        }
        known = if (value.isBlank()) null else viewModel.checkName(value, authorship)
    }
    // O campo e a lista juntos: se nao couberem, o campo fica no topo e as
    // primeiras sugestoes logo abaixo, acima do teclado.
    Column(Modifier.fillMaxWidth().bringIntoViewRequester(suggestionsView).onFocusChanged { focused = it.hasFocus }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        TextInput(
            label,
            value,
            autoCorrect = false,
            capitalization = KeyboardCapitalization.Sentences,
            help = "Digite o início do gênero e do epíteto (\"boa fab\") ou o nome popular. O teclado não corrige este campo."
        ) { onValueChange(it) }
        if (focused && suggestions.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Column {
                    suggestions.take(6).forEach { suggestion ->
                        val taxon = suggestion.taxon
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    suppressNext = true
                                    suggestions = emptyList()
                                    onChoose(suggestion)
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                androidx.compose.ui.text.buildAnnotatedString {
                                    pushStyle(androidx.compose.ui.text.SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic))
                                    append(taxon.canonicalName)
                                    pop()
                                    if (taxon.authorship.isNotBlank()) append(" ${taxon.authorship}")
                                },
                                style = MaterialTheme.typography.bodyLarge
                            )
                            val details = listOfNotNull(
                                suggestion.matchedVernacular?.let { "\"$it\"" },
                                taxon.family.takeIf { it.isNotBlank() },
                                dwcValueLabel(taxon.rank).takeIf { it.isNotBlank() },
                                suggestion.checklistTitle
                            ).joinToString(" · ")
                            Text(details, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (taxon.status == "S") {
                                Text(
                                    "${ui("Sinônimo de")} ${taxon.acceptedName.ifBlank { "?" }}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
        // Durante a digitacao o nome ainda esta incompleto: confere so ao sair do campo.
        val check = known
        if (!focused && check != null) when {
            !check.found -> Text(
                ui("Nome não encontrado nas listas instaladas: confira a grafia. O registro pode ser salvo assim mesmo."),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error
            )
            check.synonymOf != null -> Text(
                "${ui("Na lista instalada, este nome é sinônimo de")} ${check.synonymOf}. ${ui("Confira a identificação.")}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error
            )
            else -> Text(ui("Nome confere com a lista instalada."), style = MaterialTheme.typography.labelMedium, color = Color(0xFF2E7D32))
        }
    }
}

/**
 * Densidade da trilha GPS x bateria, e atalho para a otimizacao de bateria do
 * Android: em varios fabricantes ela encerra a trilha com a tela apagada.
 */
@Composable
private fun TrackProfileCard(viewModel: AppViewModel) {
    val context = LocalContext.current
    var profile by remember { mutableStateOf(viewModel.trackProfile) }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(ui("Trilha GPS e bateria"), style = MaterialTheme.typography.titleMedium)
            listOf(
                TrackProfile.PRECISE to "Precisa: um ponto a cada 3 s ou 1,5 m. Melhor desenho, gasta mais bateria.",
                TrackProfile.BALANCED to "Equilibrada: a cada 10 s ou 5 m. Boa para um dia de caminhada.",
                TrackProfile.SAVER to "Economia: a cada 30 s ou 15 m. Para campanhas longas ou celular com bateria fraca."
            ).forEach { (option, description) ->
                Row(
                    Modifier.fillMaxWidth().clickable {
                        profile = option
                        viewModel.trackProfile = option
                    },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = profile == option, onClick = {
                        profile = option
                        viewModel.trackProfile = option
                    })
                    Text(ui(description), style = MaterialTheme.typography.bodyMedium)
                }
            }
            Text(
                ui("Vale para a próxima trilha iniciada. Se a trilha parar com a tela apagada, tire o Oikos da otimização de bateria do aparelho."),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(onClick = {
                runCatching { context.startActivity(Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
                    .onFailure { runCatching { context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS)) } }
            }) { Text(ui("Abrir otimização de bateria")) }
        }
    }
}

/**
 * Importa o pacote gerado em outro celular: como projeto novo ou juntando a um
 * projeto que ja existe (o caso do coordenador que recebe o trabalho da equipe).
 */
@Composable
private fun ImportPackageButton(viewModel: AppViewModel, projects: List<ProjectEntity>, onImported: (Long) -> Unit) {
    val scope = rememberCoroutineScope()
    // A importacao pode terminar depois que o usuario ja saiu desta tela (para
    // um formulario ainda nao salvo): so abre o projeto se ainda estiver aqui.
    val alive = remember { java.util.concurrent.atomic.AtomicBoolean(true) }
    DisposableEffect(Unit) { onDispose { alive.set(false) } }
    val currentOnImported by rememberUpdatedState(onImported)
    var pending by remember { mutableStateOf<Pair<Uri, TransferManifest>?>(null) }
    var target by remember { mutableStateOf<Long?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            viewModel.inspectProjectPackage(uri)
                .onSuccess { manifest ->
                    // Se ja existe projeto com o mesmo nome, o mais provavel e juntar a ele.
                    target = projects.firstOrNull { it.name == manifest.projectName }?.id
                    pending = uri to manifest
                }
                .onFailure { viewModel.showMessage("Não foi possível abrir o pacote: ${it.message}") }
        }
    }
    pending?.let { (uri, manifest) ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(ui("Importar pacote")) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "\"${manifest.projectName}\": ${manifest.sessions} ${ui(if (manifest.sessions == 1) "expedição" else "expedições")}, " +
                            "${manifest.occurrences} ${ui(if (manifest.occurrences == 1) "ocorrência" else "ocorrências")}, " +
                            "${manifest.media} ${ui(if (manifest.media == 1) "mídia" else "mídias")}."
                    )
                    Text(
                        ui("Registros que já estão neste aparelho (mesmo identificador) não são duplicados."),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OfflineMapChoice(selected = target == null, title = ui("Criar projeto novo"), details = manifest.projectName, onSelect = { target = null })
                    projects.forEach { project ->
                        OfflineMapChoice(selected = target == project.id, title = "${ui("Juntar a")} ${project.name}", details = ui("as expedições entram neste projeto"), onSelect = { target = project.id })
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    pending = null
                    viewModel.importProjectPackage(uri, target) { id -> if (alive.get()) currentOnImported(id) }
                }) { Text(ui("Importar")) }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text(ui("Cancelar")) } }
        )
    }
    OutlinedButton(
        onClick = { launcher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Default.FileUpload, null)
        Spacer(Modifier.width(8.dp))
        Text(ui("Importar de outro celular"))
    }
}

/** Lixeira: o que foi apagado fica 30 dias e pode voltar inteiro. */
@Composable
private fun TrashScreen(viewModel: AppViewModel, modifier: Modifier, onBack: () -> Unit) {
    val items by viewModel.trashItems.collectAsState()
    var confirmEmpty by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<TrashItem?>(null) }
    LaunchedEffect(Unit) { viewModel.refreshTrash() }
    if (confirmEmpty) {
        ConfirmDeleteDialog(
            title = "Esvaziar lixeira?",
            text = "Tudo o que está na lixeira será apagado de vez, com fotos e áudios. Não dá para desfazer.",
            confirmText = "Esvaziar lixeira",
            onDismiss = { confirmEmpty = false },
            onConfirm = {
                confirmEmpty = false
                viewModel.emptyTrash()
            }
        )
    }
    toDelete?.let { item ->
        ConfirmDeleteDialog(
            title = "Apagar de vez?",
            text = "\"${item.title}\" será apagado definitivamente, com fotos e áudios. Não dá para desfazer.",
            confirmText = "Apagar de vez",
            onDismiss = { toDelete = null },
            onConfirm = {
                toDelete = null
                viewModel.deleteForever(item)
            }
        )
    }
    Scaffold(modifier = modifier, topBar = {
        OikosTopBar("Lixeira", onBack) {
            if (items.isNotEmpty()) TextButton(onClick = { confirmEmpty = true }) { Text(ui("Esvaziar")) }
        }
    }) { padding ->
        LazyColumn(
            contentPadding = padding.plus(PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp)),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    ui("Projetos, expedições, ocorrências e mídias apagados ficam aqui por 30 dias e voltam com fotos, áudios e trilhas. Depois disso são eliminados."),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (items.isEmpty()) item { EmptyState("Lixeira vazia.", "Nada foi apagado nos últimos 30 dias.") }
            items(items, key = { it.id }) { item ->
                Card {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(ui(trashKindLabel(item.kind)), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text(item.title.ifBlank { "—" }, style = MaterialTheme.typography.titleMedium)
                        val daysLeft = (TrashService.RETENTION_DAYS - (System.currentTimeMillis() - item.deletedAt) / (24L * 60 * 60 * 1000)).coerceAtLeast(0)
                        Text(
                            listOfNotNull(
                                "${ui("Apagado em")} ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("pt-BR")).format(Date(item.deletedAt))}",
                                item.occurrences.takeIf { it > 0 && item.kind != "occurrence" }?.let { "$it ${ui(if (it == 1) "ocorrência" else "ocorrências")}" },
                                item.media.takeIf { it > 0 }?.let { "$it ${ui(if (it == 1) "mídia" else "mídias")}" },
                                "${ui("sai em")} $daysLeft ${ui(if (daysLeft == 1L) "dia" else "dias")}"
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { viewModel.restoreFromTrash(item) }) { Text(ui("Restaurar")) }
                            TextButton(onClick = { toDelete = item }) { Text(ui("Apagar de vez")) }
                        }
                    }
                }
            }
        }
    }
}

private fun trashKindLabel(kind: String): String = when (kind) {
    "project" -> "Projeto"
    "session" -> "Expedição"
    "occurrence" -> "Ocorrência"
    "media" -> "Mídia"
    else -> kind
}

/**
 * Mapas offline (MBTiles). O pesquisador gera o mapa da area de estudo no QGIS
 * e copia para o celular; em campo o mapa funciona sem sinal.
 */
@Composable
private fun OfflineMapsCard(viewModel: AppViewModel) {
    val maps by viewModel.offlineMaps.collectAsState()
    val active by viewModel.activeOfflineMap.collectAsState()
    val progress by viewModel.offlineMapProgress.collectAsState()
    var toRemove by remember { mutableStateOf<OfflineMap?>(null) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.importOfflineMap(it) }
    }
    toRemove?.let { map ->
        ConfirmDeleteDialog(
            title = "Remover mapa?",
            text = "O arquivo do mapa sai do aparelho. Os registros não mudam.",
            confirmText = "Remover mapa",
            onDismiss = { toRemove = null },
            onConfirm = {
                toRemove = null
                viewModel.removeOfflineMap(map)
            }
        )
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(ui("Mapas offline"), style = MaterialTheme.typography.titleMedium)
            Text(
                ui("Use em campo, sem sinal, o mapa da sua área de estudo: imagem de satélite, carta topográfica ou mapa de vegetação. Gere no QGIS com a ferramenta \"Gerar tiles XYZ (MBTiles)\" e importe o arquivo .mbtiles aqui."),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OfflineMapChoice(
                selected = active == null,
                title = ui("OpenStreetMap (internet)"),
                details = ui("Precisa de internet; as áreas já vistas ficam guardadas por um tempo."),
                onSelect = { viewModel.setActiveOfflineMap(null) }
            )
            maps.forEach { map ->
                OfflineMapChoice(
                    selected = active?.file == map.file,
                    title = map.name,
                    details = "${ui("zoom")} ${map.minZoom}–${map.maxZoom} · ${map.format.uppercase(Locale.ROOT)} · ${humanSize(map.sizeBytes)}",
                    onSelect = { viewModel.setActiveOfflineMap(map) },
                    onRemove = { toRemove = map }
                )
            }
            val current = progress
            if (current != null) {
                ProgressLine(current)
            } else {
                OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/vnd.sqlite3", "application/x-sqlite3", "application/octet-stream", "*/*")) }) {
                    Icon(Icons.Default.FileUpload, null)
                    Spacer(Modifier.width(6.dp))
                    Text(ui("Importar mapa (.mbtiles)"))
                }
            }
        }
    }
}

@Composable
private fun OfflineMapChoice(selected: Boolean, title: String, details: String, onSelect: () -> Unit, onRemove: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onSelect), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onSelect)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(details, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (onRemove != null) IconButton(onClick = onRemove) { Icon(Icons.Default.Delete, ui("Remover mapa")) }
    }
}

/**
 * Backup e restauracao. Sem servidor, o arquivo guardado fora do celular e o
 * que salva os dados quando o aparelho e perdido, trocado ou reinstalado.
 */
@Composable
private fun BackupCard(viewModel: AppViewModel) {
    val context = LocalContext.current
    val lastBackupAt by viewModel.lastBackupAt.collectAsState()
    val progress by viewModel.backupProgress.collectAsState()
    var restoreCandidate by remember { mutableStateOf<Pair<Uri, BackupManifest>?>(null) }
    val createLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let { viewModel.createBackup(it) }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { chosen -> viewModel.inspectBackup(chosen) { manifest -> if (manifest != null) restoreCandidate = chosen to manifest } }
    }

    restoreCandidate?.let { (uri, manifest) ->
        AlertDialog(
            onDismissRequest = { restoreCandidate = null },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(ui("Restaurar este backup?")) },
            text = {
                Text(
                    "${ui("Feito em")} ${formatBackupDate(manifest.createdAt)} (${ui("versão")} ${manifest.appVersion}): " +
                        "${manifest.projects} ${ui("projeto(s)")}, ${manifest.occurrences} ${ui("ocorrência(s)")}, " +
                        "${manifest.mediaFiles} ${ui("mídia(s)")} (${humanSize(manifest.mediaBytes)}).\n\n" +
                        ui("Todos os dados atuais deste aparelho serão substituídos pelos do backup. O app vai fechar e abrir de novo para concluir.") +
                        "\n\n" + ui("As listas de espécies já instaladas neste aparelho são mantidas.")
                )
            },
            confirmButton = {
                Button(onClick = {
                    restoreCandidate = null
                    viewModel.restoreBackup(uri) { restartApp(context) }
                }) { Text(ui("Substituir e restaurar")) }
            },
            dismissButton = { TextButton(onClick = { restoreCandidate = null }) { Text(ui("Cancelar")) } }
        )
    }

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(ui("Backup e restauração"), style = MaterialTheme.typography.titleMedium)
            Text(
                ui("Um arquivo .zip com todos os projetos, fotos e áudios. Guarde fora do celular (computador, Drive, pen drive): é o que salva seus dados se o aparelho for perdido ou trocado."),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                if (lastBackupAt > 0L) "${ui("Último backup")}: ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(lastBackupAt))}"
                else ui("Nenhum backup feito neste aparelho."),
                style = MaterialTheme.typography.labelLarge
            )
            val busy = progress != null
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                Button(enabled = !busy, onClick = {
                    val stamp = SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.US).format(Date())
                    createLauncher.launch("oikos-backup-$stamp.zip")
                }) { Icon(Icons.Default.Save, null); Spacer(Modifier.width(6.dp)); Text(ui("Criar backup")) }
                OutlinedButton(enabled = !busy, onClick = { restoreLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }) {
                    Icon(Icons.Default.FileUpload, null); Spacer(Modifier.width(6.dp)); Text(ui("Restaurar"))
                }
            }
            progress?.let { state ->
                Text(ui(state.label), style = MaterialTheme.typography.labelMedium)
                val fraction = state.fraction
                if (fraction != null) LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

private fun formatBackupDate(iso: String): String = runCatching {
    java.time.OffsetDateTime.parse(iso).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
}.getOrDefault(iso)

/**
 * Reinicia o app para concluir a restauracao: a troca do banco acontece no
 * Application.onCreate do novo processo, antes de o banco abrir.
 */
private fun restartApp(context: Context) {
    val activity = context.findActivity() ?: return
    val intent = activity.packageManager.getLaunchIntentForPackage(activity.packageName)
        ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    if (intent != null) activity.startActivity(intent)
    activity.finishAffinity()
    Runtime.getRuntime().exit(0)
}

/** Aviso na tela inicial quando ha dados e o ultimo backup tem mais de 3 dias. */
@Composable
private fun BackupReminder(lastBackupAt: Long, onBackup: () -> Unit) {
    val days = if (lastBackupAt > 0L) ((System.currentTimeMillis() - lastBackupAt) / 86_400_000L).toInt() else -1
    if (days in 0..2) return
    // Lembrete discreto, numa linha: o cartao vermelho de antes parecia um erro
    // e pesava na tela inicial.
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Backup, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                if (days < 0) ui("Nenhum backup ainda") else "${ui("Último backup há")} $days ${ui("dias")}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onBackup) { Text(ui("Fazer backup")) }
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
                    Text(
                        "${ui("Versão")} ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
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
                        ui("Mapas por © OpenStreetMap contributors. Municípios: IBGE, Malha Municipal 2022. Listas de espécies: Flora e Funga do Brasil (JBRJ) e Catálogo Taxonômico da Fauna do Brasil, CC BY 4.0, obtidas no IPT do JBRJ ou na cópia guardada pelo ChecklistBank."),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(ui("Uso de rede e privacidade"), style = MaterialTheme.typography.labelLarge)
                    Text(
                        ui("Seus registros, fotos e áudios ficam só neste aparelho; o app não os envia a nenhum servidor. A internet só é usada para: imagens do mapa (OpenStreetMap), quando não há mapa offline; baixar as listas de espécies; e sugerir endereço pelo Google, se você ligar essa opção em Configurações."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            }
            item { HowToCiteCard() }
            item {
            DeveloperSignature(Modifier.fillMaxWidth().padding(top = 20.dp))
            }
        }
    }
}

/** Citacao do aplicativo (ABNT e APA), com botao para copiar. */
@Composable
private fun HowToCiteCard() {
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val abnt = remember { AppCitation.abnt(BuildConfig.VERSION_NAME) }
    val apa = remember { AppCitation.apa(BuildConfig.VERSION_NAME) }
    var copied by remember { mutableStateOf<String?>(null) }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(ui("Como citar"), style = MaterialTheme.typography.titleMedium)
            Text(
                ui("Se o Oikos ajudou no seu trabalho, cite o aplicativo. As exportações em ZIP trazem esta citação no arquivo COMO_CITAR.txt."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            listOf("ABNT" to abnt, "APA" to apa).forEach { (style, text) ->
                Text(style, style = MaterialTheme.typography.labelLarge)
                SelectionContainer { Text(text, style = MaterialTheme.typography.bodySmall) }
                TextButton(onClick = {
                    clipboard.setText(androidx.compose.ui.text.AnnotatedString(text))
                    copied = style
                }) { Text(if (copied == style) ui("Copiado") else "${ui("Copiar")} $style") }
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
    "expedição" to "expedition",
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
    "Registrando em segundo plano. A linha laranja é a trilha base; pontos das ocorrências aparecem por cima." to "Recording in the background. The orange line is the track; occurrence points appear on top.",
    "Inicie a trilha para acompanhar o percurso contínuo da expedição." to "Start the track to follow the expedition route.",
    "Ativa" to "Active",
    "Parada" to "Stopped",
    "Parar" to "Stop",
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
    "Áudio" to "Audio",
    "data de captura desconhecida" to "capture date unknown",
    "Editar mídia" to "Edit media",
    "Remover mídia" to "Remove media",
    "Remover esta mídia?" to "Remove this media?",
    "Ela ainda não foi salva no registro e será apagada de vez." to "It has not been saved to the record yet and will be deleted permanently.",
    "Ela fica na Lixeira por 30 dias e pode ser restaurada." to "It stays in the Trash for 30 days and can be restored.",
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
    "Versão" to "Version",
    "Uso de rede e privacidade" to "Network use and privacy",
    "Como citar" to "How to cite",
    "Se o Oikos ajudou no seu trabalho, cite o aplicativo. As exportações em ZIP trazem esta citação no arquivo COMO_CITAR.txt." to "If Oikos helped your work, please cite the app. ZIP exports include this citation in the COMO_CITAR.txt file.",
    "Copiar" to "Copy",
    "Copiado" to "Copied",
    "Seus registros, fotos e áudios ficam só neste aparelho; o app não os envia a nenhum servidor. A internet só é usada para: imagens do mapa (OpenStreetMap), quando não há mapa offline; baixar as listas de espécies; e sugerir endereço pelo Google, se você ligar essa opção em Configurações." to "Your records, photos and audio stay only on this device; the app sends them to no server. The internet is used only for: map imagery (OpenStreetMap) when there is no offline map; downloading species lists; and suggesting an address via Google, if you turn that option on in Settings.",
    "Darwin Core Archive (GBIF/SiBBr)" to "Darwin Core Archive (GBIF/SiBBr)",
    "Calculando o espaço usado..." to "Calculating storage use...",
    "Outro" to "Other",
    "Valor livre" to "Free value",
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
    "Mapas por © OpenStreetMap contributors. Municípios: IBGE, Malha Municipal 2022. Listas de espécies: Flora e Funga do Brasil (JBRJ) e Catálogo Taxonômico da Fauna do Brasil, CC BY 4.0, obtidas no IPT do JBRJ ou na cópia guardada pelo ChecklistBank." to "Maps by © OpenStreetMap contributors. Municipalities: IBGE, 2022 Municipal Mesh. Species lists: Flora e Funga do Brasil (JBRJ) and Catálogo Taxonômico da Fauna do Brasil, CC BY 4.0, obtained from the JBRJ IPT or the copy kept by ChecklistBank.",
    "Ajuda" to "Help",
    "Entendi" to "Got it",
    "Coordenadas" to "Coordinates",
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
    "Descartar alterações?" to "Discard changes?",
    "O que foi preenchido nesta tela ainda não foi salvo." to "What you filled in on this screen has not been saved yet.",
    "Descartar" to "Discard",
    "Continuar editando" to "Keep editing",
    "Publicação dos dados" to "Data publication",
    "Licença dos dados" to "Data license",
    "Definir depois" to "Decide later",
    "O GBIF e o SiBBr só publicam com CC0, CC BY ou CC BY-NC. CC BY 4.0 permite qualquer uso, desde que citem a fonte." to "GBIF and SiBBr only publish under CC0, CC BY or CC BY-NC. CC BY 4.0 allows any use as long as the source is credited.",
    "URL pública das mídias (opcional)" to "Public media URL (optional)",
    "Endereço onde as fotos e áudios exportados ficarão publicados (Zenodo, site do laboratório). Sem ele, o Darwin Core Archive sai sem a extensão de mídia, porque o padrão exige link público." to "Address where exported photos and audio will be published (Zenodo, lab website). Without it the Darwin Core Archive omits the media extension, because the standard requires a public link.",
    "Planilha CSV" to "CSV spreadsheet",
    "Excel Brasil (; e vírgula)" to "Excel Brazil (; and comma)",
    "Internacional (, e ponto)" to "International (, and dot)",
    "Nome de quem responde pelos dados. Vai como autor e contato no eml.xml do Darwin Core Archive." to "Person responsible for the data. Used as creator and contact in the Darwin Core Archive eml.xml.",
    "Dê um nome ao projeto antes de salvar." to "Name the project before saving.",
    "Ativo" to "Active",
    "Obrigatório" to "Required",
    "Ocultar amostragem quantitativa" to "Hide quantitative sampling",
    "Amostragem quantitativa (parcela, transecto)" to "Quantitative sampling (plot, transect)",
    "Unidade amostral" to "Sampling unit",
    "Para inventário quantitativo: indique a expedição de que esta faz parte (campanha > dia > parcela) e o tamanho da unidade amostral. Sem isso não se calcula densidade nem abundância relativa a partir da exportação." to "For quantitative inventories: set the parent expedition (campaign > day > plot) and the size of the sampling unit. Without it, density and relative abundance cannot be computed from the export.",
    "Faz parte de (parentEventID)" to "Part of (parentEventID)",
    "Nenhuma" to "None",
    "Tamanho da unidade amostral (sampleSizeValue)" to "Sampling unit size (sampleSizeValue)",
    "Unidade (sampleSizeUnit)" to "Unit (sampleSizeUnit)",
    "Data como anotada (verbatimEventDate)" to "Date as written (verbatimEventDate)",
    "Exatamente como está na caderneta ou etiqueta, por exemplo \"3-5.iii.2026\"." to "Exactly as written in the notebook or label, for example \"3-5.iii.2026\".",
    "Gravar" to "Record",
    "Parar e salvar" to "Stop and save",
    "Gravando" to "Recording",
    "A tela fica acesa durante a gravação. Sair da tela ou do app encerra e salva o áudio." to "The screen stays on while recording. Leaving the screen or the app stops and saves the audio.",
    "Há uma gravação em andamento em outra tela." to "A recording is in progress on another screen.",
    "Estas mídias entram no registro ao salvar." to "These media are added to the record when you save.",
    "Exportando..." to "Exporting...",
    "Montando o pacote. Com muitos áudios pode levar alguns minutos; não feche o app." to "Building the package. With many audio files this may take a few minutes; do not close the app.",
    "Mapa e uso offline" to "Map and offline use",
    "A coleta, o GPS e a trilha funcionam sem internet. O mapa de fundo guarda os trechos já vistos: abra o mapa da área de trabalho enquanto houver internet e ele continua disponível em campo." to "Data collection, GPS and tracks work without internet. The base map keeps the areas already viewed: open the map of your work area while online and it stays available in the field.",
    "Sugerir município e estado pelo GPS" to "Suggest municipality and state from GPS",
    "Usa a internet: a coordenada capturada é enviada ao serviço de endereços do Google. Desligado, nada sai do aparelho. Deixe desligado em locais de espécies sensíveis." to "Uses the internet: the captured coordinate is sent to Google's address service. When off, nothing leaves the device. Keep it off at sites of sensitive species.",
    "Incerteza (m)" to "Uncertainty (m)",
    "Altitude (m)" to "Elevation (m)",
    "Incerteza da coordenada" to "Coordinate uncertainty",
    "Raio, em metros, dentro do qual o ponto verdadeiro deve estar. O GPS do celular informa uma estimativa; corrija se usou outro receptor. As exportações guardam a coordenada em graus decimais." to "Radius, in meters, within which the true point should lie. The phone GPS gives an estimate; correct it if you used another receiver. Exports store the coordinate in decimal degrees.",
    "Não consegui ler a coordenada. Indique o hemisfério (N/S e L/O, ou E/W) e preencha as duas. Exemplos: 22°25'51.6\"S ou 22 25 51.6 S." to "Could not read the coordinate. Give the hemisphere (N/S and E/W) and fill in both. Examples: 22°25'51.6\"S or 22 25 51.6 S.",
    "Graus, minutos e segundos" to "Degrees, minutes and seconds",
    "Graus e minutos decimais" to "Degrees and decimal minutes",
    "Digite como está no GPS de mão ou na caderneta, com N/S e L/O (ou E/W). Exemplos: 22°25'51.6\"S, 22 25 51.6 S, 44 58.74 O. O app guarda em graus decimais e exporta também como foi lido (verbatimCoordinates)." to "Type it as shown on the handheld GPS or notebook, with N/S and E/W. Examples: 22°25'51.6\"S, 22 25 51.6 S, 44 58.74 W. The app stores decimal degrees and also exports it as read (verbatimCoordinates).",
    "Use o nome científico, se souber. Exemplo: Boana faber. O teclado não corrige este campo." to "Use the scientific name if you know it. Example: Boana faber. The keyboard does not autocorrect this field.",
    "Ocultar campos avançados" to "Hide advanced fields",
    "Mostrar campos avançados" to "Show advanced fields",
    "Backup e restauração" to "Backup and restore",
    "Um arquivo .zip com todos os projetos, fotos e áudios. Guarde fora do celular (computador, Drive, pen drive): é o que salva seus dados se o aparelho for perdido ou trocado." to "A single .zip with all projects, photos and audio. Keep it off the phone (computer, Drive, USB stick): it is what saves your data if the device is lost or replaced.",
    "Último backup" to "Last backup",
    "Nenhum backup feito neste aparelho." to "No backup made on this device.",
    "Criar backup" to "Create backup",
    "Restaurar" to "Restore",
    "Criando backup..." to "Creating backup...",
    "Preparando a restauração..." to "Preparing restore...",
    "Restaurar este backup?" to "Restore this backup?",
    "Feito em" to "Made on",
    "versão" to "version",
    "projeto(s)" to "project(s)",
    "ocorrência(s)" to "occurrence(s)",
    "mídia(s)" to "media file(s)",
    "Todos os dados atuais deste aparelho serão substituídos pelos do backup. O app vai fechar e abrir de novo para concluir." to "All current data on this device will be replaced by the backup. The app will close and reopen to finish.",
    "Substituir e restaurar" to "Replace and restore",
    "Seus dados existem só neste aparelho" to "Your data exists only on this device",
    "Nenhum backup feito ainda." to "No backup made yet.",
    "Nenhum backup ainda" to "No backup yet",
    "Último backup há" to "Last backup",
    "dias" to "days ago",
    "Fazer backup" to "Back up now",
    "Procurando satélites..." to "Searching for satellites...",
    "Boa (até 10 m)" to "Good (up to 10 m)",
    "Razoável (até 30 m)" to "Fair (up to 30 m)",
    "Fraca (mais de 30 m)" to "Poor (over 30 m)",
    "Capturando posição" to "Capturing position",
    "Leituras" to "Readings",
    "Precisão agora" to "Accuracy now",
    "Média" to "Average",
    "Incerteza a registrar" to "Uncertainty to record",
    "leituras usadas" to "readings used",
    "Fique parado no ponto. Céu aberto e alguns segundos de leitura melhoram a média. A incerteza segue o guia do GBIF: no mínimo o dobro da precisão informada pelo GPS." to "Stay still at the point. Open sky and a few seconds of readings improve the average. Uncertainty follows the GBIF guide: at least twice the accuracy reported by the GPS.",
    "Usar média" to "Use average",
    "Ocultar condições da gravação" to "Hide recording conditions",
    "Condições da gravação (microfone, temperatura, umidade)" to "Recording conditions (microphone, temperature, humidity)",
    "Microfone ou gravador" to "Microphone or recorder",
    "Lembrado para as próximas gravações. Exemplo: microfone do celular, Sennheiser ME66, Zoom H5." to "Remembered for the next recordings. Example: phone microphone, Sennheiser ME66, Zoom H5.",
    "Temperatura do ar (°C)" to "Air temperature (°C)",
    "Umidade (%)" to "Humidity (%)",
    "Distância até o animal (m)" to "Distance to the animal (m)",
    "Taxa de amostragem, bits, duração, fonte de áudio e modelo do aparelho são registrados automaticamente." to "Sample rate, bit depth, duration, audio source and device model are recorded automatically.",
    "Outro" to "Other",
    "As listas de espécies já instaladas neste aparelho são mantidas." to "Species lists already installed on this device are kept.",
    "Listas de espécies" to "Species lists",
    "Nenhuma instalada. Baixe a Flora e Funga do Brasil ou a Fauna do Brasil para ter autocompletar do nome científico em campo." to "None installed. Download Flora e Funga do Brasil or the Fauna do Brasil catalogue to get scientific name autocomplete in the field.",
    "Gerenciar" to "Manage",
    "Baixe uma vez, com internet, e use em campo sem rede: o nome científico é sugerido enquanto você digita, a autoria e a família são preenchidas e sinônimos são trocados pelo nome aceito. As listas vêm direto da fonte oficial, e o app confere se há versão nova sempre que houver internet." to "Download once with internet and use in the field with no signal: the scientific name is suggested as you type, authorship and family are filled in and synonyms are replaced by the accepted name. Lists come straight from the official source, and the app checks for a new version whenever there is internet.",
    "Remover lista?" to "Remove list?",
    "Os registros já feitos não mudam. O autocompletar deixa de usar esta lista até você baixá-la de novo." to "Existing records do not change. Autocomplete stops using this list until you download it again.",
    "Remover lista" to "Remove list",
    "Remover" to "Remove",
    "Outra lista (arquivo)" to "Other list (file)",
    "Qualquer lista publicada como Darwin Core Archive com núcleo Taxon, como as de outros países no GBIF, no ChecklistBank ou em um IPT. Baixe o arquivo .zip e importe aqui." to "Any list published as a Darwin Core Archive with a Taxon core, such as other countries' lists on GBIF, ChecklistBank or an IPT. Download the .zip file and import it here.",
    "Importar arquivo" to "Import file",
    "Brasil · plantas, algas e fungos" to "Brazil · plants, algae and fungi",
    "Lista oficial do Jardim Botânico do Rio de Janeiro, republicada a cada atualização no IPT do JBRJ. CC BY 4.0." to "Official list from the Rio de Janeiro Botanical Garden, republished on the JBRJ IPT with every update. CC BY 4.0.",
    "Baixada da cópia guardada no ChecklistBank, porque o IPT do JBRJ recusou a conexão. A cópia pode estar algumas semanas atrás do IPT." to "Downloaded from the copy kept by ChecklistBank, because the JBRJ IPT refused the connection. The copy may be a few weeks behind the IPT.",
    "Brasil · animais" to "Brazil · animals",
    "Catálogo oficial da fauna brasileira, publicado no IPT do JBRJ. CC BY 4.0." to "Official catalogue of Brazilian fauna, published on the JBRJ IPT. CC BY 4.0.",
    "Não instalada" to "Not installed",
    "cerca de" to "about",
    "para baixar" to "to download",
    "ocupa" to "takes",
    "no aparelho" to "on the device",
    "Versão nova disponível" to "New version available",
    "Citação" to "Citation",
    "Baixar" to "Download",
    "Atualizar" to "Update",
    "Baixar de novo" to "Download again",
    "Instalada" to "Installed",
    "publicada em" to "published on",
    "nomes" to "names",
    "Conectando..." to "Connecting...",
    "Lendo o arquivo..." to "Reading the file...",
    "Importando nomes" to "Importing names",
    "Baixando" to "Downloading",
    "Digite o início do gênero e do epíteto (\"boa fab\") ou o nome popular. O teclado não corrige este campo." to "Type the start of the genus and epithet (\"boa fab\") or the common name. The keyboard does not autocorrect this field.",
    "Sinônimo de" to "Synonym of",
    "Nome não encontrado nas listas instaladas: confira a grafia. O registro pode ser salvo assim mesmo." to "Name not found in the installed lists: check the spelling. The record can still be saved.",
    "Nome confere com a lista instalada." to "Name matches the installed list.",
    "Na lista instalada, este nome é sinônimo de" to "In the installed list, this name is a synonym of",
    "Confira a identificação." to "Check the identification.",
    "Salvar e criar o próximo" to "Save and start the next one",
    "Repetir campos do registro anterior" to "Repeat fields from the previous record",
    "Repetindo do registro anterior" to "Repeating from the previous record",
    "Escolha uma vez o que não muda entre registros: coletor, ponto, habitat..." to "Choose once what stays the same between records: collector, point, habitat...",
    "Escolher" to "Choose",
    "Alterar" to "Change",
    "Repetir do registro anterior" to "Repeat from the previous record",
    "Os campos marcados são copiados agora e em cada novo registro deste projeto. Data, hora e identificador nunca são repetidos." to "Checked fields are copied now and into every new record in this project. Date, time and identifier are never repeated.",
    "Repetir" to "Repeat",
    "Grupo e tipo do registro" to "Record group and type",
    "Natureza do registro" to "Basis of record",
    "Coordenadas (mesmo ponto)" to "Coordinates (same point)",
    "Observações" to "Remarks",
    "Notas de campo" to "Field notes",
    "Contagem rápida" to "Quick count",
    "Contagem rápida (ponto, transecto)" to "Quick count (point, transect)",
    "registro nesta expedição" to "record in this expedition",
    "Conte ao menos um indivíduo" to "Count at least one individual",
    "registro" to "record",
    "registros" to "records",
    "indivíduo" to "individual",
    "indivíduos" to "individuals",
    "Toque em + a cada indivíduo visto ou ouvido. Ao salvar, cada espécie vira um registro com o número de indivíduos, o horário da contagem e o ponto." to "Tap + for each individual seen or heard. When saved, each species becomes a record with the number of individuals, the count time and the point.",
    "Início" to "Start",
    "espécie" to "species",
    "espécies" to "species",
    "Ponto da contagem" to "Count point",
    "Sem coordenada: os registros serão salvos sem ponto." to "No coordinate: records will be saved without a point.",
    "Adicionar espécie" to "Add species",
    "Adicionar como digitado" to "Add as typed",
    "Nenhuma espécie ainda." to "No species yet.",
    "Digite o nome acima. Espécies sem nome definido podem entrar como \"Turdus sp.\" ou \"morfoespécie 1\"." to "Type the name above. Species without a definite name can go in as \"Turdus sp.\" or \"morphospecies 1\".",
    "Menos um indivíduo" to "One individual less",
    "Tirar da contagem" to "Remove from count",
    "Mais um indivíduo" to "One more individual",
    "malha do IBGE 2022" to "IBGE 2022 municipal mesh",
    "Usar" to "Use",
    "Pacote para outro celular" to "Package for another phone",
    "Importar de outro celular" to "Import from another phone",
    "Importar pacote" to "Import package",
    "Registros que já estão neste aparelho (mesmo identificador) não são duplicados." to "Records already on this device (same identifier) are not duplicated.",
    "Criar projeto novo" to "Create a new project",
    "Juntar a" to "Merge into",
    "as expedições entram neste projeto" to "expeditions go into this project",
    "Importar" to "Import",
    "Ouvir e ver o espectrograma" to "Listen and view the spectrogram",
    "Não foi possível ler este áudio." to "Could not read this audio.",
    "Calculando o espectrograma..." to "Computing the spectrogram...",
    "Espectrograma" to "Spectrogram",
    "Até" to "Up to",
    "Pausar" to "Pause",
    "Ouvir" to "Listen",
    "Trilha GPS e bateria" to "GPS track and battery",
    "Precisa: um ponto a cada 3 s ou 1,5 m. Melhor desenho, gasta mais bateria." to "Precise: a point every 3 s or 1.5 m. Best detail, uses more battery.",
    "Equilibrada: a cada 10 s ou 5 m. Boa para um dia de caminhada." to "Balanced: every 10 s or 5 m. Good for a day of walking.",
    "Economia: a cada 30 s ou 15 m. Para campanhas longas ou celular com bateria fraca." to "Saver: every 30 s or 15 m. For long campaigns or phones with weak batteries.",
    "Vale para a próxima trilha iniciada. Se a trilha parar com a tela apagada, tire o Oikos da otimização de bateria do aparelho." to "Applies to the next track started. If the track stops with the screen off, exclude Oikos from the device battery optimization.",
    "Abrir otimização de bateria" to "Open battery optimization",
    "Protocolo (modelos)" to "Protocol (templates)",
    "Exemplo para este protocolo" to "Example for this protocol",
    "Busca ativa" to "Active search",
    "Ponto de escuta" to "Point count",
    "Transecto" to "Transect",
    "Parcela" to "Plot",
    "Pitfall" to "Pitfall",
    "Gravador automático" to "Autonomous recorder",
    "Coleta botânica" to "Botanical collection",
    "Etiquetas de herbário/coleção (PDF)" to "Herbarium/collection labels (PDF)",
    "Não" to "No",
    "ocorrência" to "occurrence",
    "ocorrências" to "occurrences",
    "mídia" to "media file",
    "mídias" to "media files",
    "dia" to "day",
    "Abrir" to "Open",
    "item" to "item",
    "itens" to "items",
    "Lixeira" to "Trash",
    "Vazia. O que for apagado fica aqui por 30 dias." to "Empty. Anything deleted stays here for 30 days.",
    "restauráveis por 30 dias" to "restorable for 30 days",
    "Esvaziar lixeira?" to "Empty trash?",
    "Tudo o que está na lixeira será apagado de vez, com fotos e áudios. Não dá para desfazer." to "Everything in the trash will be permanently deleted, including photos and audio. This cannot be undone.",
    "Esvaziar lixeira" to "Empty trash",
    "Esvaziar" to "Empty",
    "Apagar de vez?" to "Delete permanently?",
    "Apagar de vez" to "Delete permanently",
    "Projetos, expedições, ocorrências e mídias apagados ficam aqui por 30 dias e voltam com fotos, áudios e trilhas. Depois disso são eliminados." to "Deleted projects, expeditions, occurrences and media stay here for 30 days and come back with photos, audio and tracks. After that they are removed.",
    "Lixeira vazia." to "Trash is empty.",
    "Nada foi apagado nos últimos 30 dias." to "Nothing was deleted in the last 30 days.",
    "Mídia" to "Media",
    "Apagado em" to "Deleted on",
    "sai em" to "removed in",
    "O projeto, com expedições, ocorrências, trilhas e mídias, vai para a lixeira e pode ser restaurado por 30 dias em Configurações > Lixeira." to "The project, with its expeditions, occurrences, tracks and media, goes to the trash and can be restored for 30 days in Settings > Trash.",
    "A expedição, com ocorrências, trilhas e mídias, vai para a lixeira e pode ser restaurada por 30 dias em Configurações > Lixeira." to "The expedition, with its occurrences, tracks and media, goes to the trash and can be restored for 30 days in Settings > Trash.",
    "O registro e suas fotos e áudios vão para a lixeira e podem ser restaurados por 30 dias em Configurações > Lixeira." to "The record and its photos and audio go to the trash and can be restored for 30 days in Settings > Trash.",
    "Sugerir endereço pelo Google" to "Suggest address via Google",
    "No Brasil, município e estado já são sugeridos sem internet, pela malha do IBGE. Esta opção serve para coletas fora do Brasil e usa a internet: a coordenada capturada vai ao serviço de endereços do Google. Deixe desligada em locais de espécies sensíveis." to "In Brazil, municipality and state are already suggested offline from the IBGE mesh. This option is for collecting outside Brazil and uses the internet: the captured coordinate goes to the Google address service. Keep it off at sites with sensitive species.",
    "Copiando o mapa:" to "Copying the map:",
    "Copiando o mapa..." to "Copying the map...",
    "Remover mapa?" to "Remove map?",
    "O arquivo do mapa sai do aparelho. Os registros não mudam." to "The map file is removed from the device. Records do not change.",
    "Remover mapa" to "Remove map",
    "Mapas offline" to "Offline maps",
    "Use em campo, sem sinal, o mapa da sua área de estudo: imagem de satélite, carta topográfica ou mapa de vegetação. Gere no QGIS com a ferramenta \"Gerar tiles XYZ (MBTiles)\" e importe o arquivo .mbtiles aqui." to "Use your study area map in the field with no signal: satellite image, topographic chart or vegetation map. Create it in QGIS with the \"Generate XYZ tiles (MBTiles)\" tool and import the .mbtiles file here.",
    "OpenStreetMap (internet)" to "OpenStreetMap (internet)",
    "Precisa de internet; as áreas já vistas ficam guardadas por um tempo." to "Needs internet; areas already viewed stay cached for a while.",
    "zoom" to "zoom",
    "Importar mapa (.mbtiles)" to "Import map (.mbtiles)"
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
        Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = "${ui("Ajuda")}: $title")
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
            val bitmap = rememberThumbnail(media.filePath, media.createdAt)
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
    // Coordenada digitada em UTM/GMS que nao foi entendida: o formulario nao salva.
    onInputError: (String?) -> Unit = {},
    onCapture: () -> Unit
) {
    val isUtm = coordinateFormat == "UTM"
    val isSexagesimal = coordinateFormat == "DMS" || coordinateFormat == "DMM"
    val invalidUtmText = ui("Coordenada UTM inválida.")
    val invalidSexagesimalText = ui("Não consegui ler a coordenada. Indique o hemisfério (N/S e L/O, ou E/W) e preencha as duas. Exemplos: 22°25'51.6\"S ou 22 25 51.6 S.")
    var utmZone by rememberSaveable(coordinateFormat) { mutableStateOf("") }
    var utmHemisphere by rememberSaveable(coordinateFormat) { mutableStateOf("S") }
    var utmEasting by rememberSaveable(coordinateFormat) { mutableStateOf("") }
    var utmNorthing by rememberSaveable(coordinateFormat) { mutableStateOf("") }
    var utmError by remember(coordinateFormat) { mutableStateOf<String?>(null) }
    var sexLat by rememberSaveable(coordinateFormat) { mutableStateOf("") }
    var sexLon by rememberSaveable(coordinateFormat) { mutableStateOf("") }
    var sexError by remember(coordinateFormat) { mutableStateOf<String?>(null) }

    // Par decimal que acabou de ser escrito a partir dos campos UTM/GMS. Quando
    // lat/lon mudam por outro motivo (GPS, digitacao em graus decimais), os
    // campos UTM/GMS sao refeitos; quando a mudanca veio deles, nao. Antes o app
    // reescrevia o Leste/Norte enquanto a pessoa ainda digitava.
    var pushedPair by remember { mutableStateOf<Pair<String, String>?>(null) }

    fun pushDecimal(latitude: Double, longitude: Double) {
        val latText = CoordinateUtils.roundCoordinate(latitude).toString()
        val lonText = CoordinateUtils.roundCoordinate(longitude).toString()
        pushedPair = latText to lonText
        onLat(latText)
        onLon(lonText)
    }

    fun syncUtmFromDecimal() {
        val utm = CoordinateUtils.toUtm(lat.replace(',', '.').toDoubleOrNull(), lon.replace(',', '.').toDoubleOrNull())
        if (utm != null) {
            utmZone = utm.zone.toString()
            utmHemisphere = if (utm.hemisphere == UtmHemisphere.South) "S" else "N"
            utmEasting = String.format(Locale.US, "%.1f", utm.easting)
            utmNorthing = String.format(Locale.US, "%.1f", utm.northing)
        }
    }

    // Os campos de UTM/GMS ficaram vazios: a coordenada decimal sai junto.
    // Antes ficava a ultima convertida, e o registro era salvo com uma
    // coordenada que a tela nao mostrava mais.
    fun clearDecimal() {
        if (lat.isBlank() && lon.isBlank()) return
        pushedPair = "" to ""
        onLat("")
        onLon("")
    }

    fun syncDecimalFromUtm() {
        if (utmZone.isBlank() && utmEasting.isBlank() && utmNorthing.isBlank()) {
            utmError = null
            onInputError(null)
            clearDecimal()
            return
        }
        val zone = utmZone.toIntOrNull()
        val easting = utmEasting.replace(',', '.').toDoubleOrNull()
        val northing = utmNorthing.replace(',', '.').toDoubleOrNull()
        val hemisphere = if (utmHemisphere == "N") UtmHemisphere.North else UtmHemisphere.South
        val decimal = if (zone == null || easting == null || northing == null) null else CoordinateUtils.fromUtm(zone, hemisphere, easting, northing)
        if (decimal == null) {
            utmError = invalidUtmText
            onInputError(invalidUtmText)
        } else {
            utmError = null
            onInputError(null)
            pushDecimal(decimal.latitude, decimal.longitude)
        }
    }

    fun formatSexagesimal(value: Double, axis: Axis): String =
        if (coordinateFormat == "DMS") CoordinateUtils.toDms(value, axis) else CoordinateUtils.toDmm(value, axis)

    fun syncSexagesimalFromDecimal() {
        lat.replace(',', '.').toDoubleOrNull()?.let { sexLat = formatSexagesimal(it, Axis.Latitude) }
        lon.replace(',', '.').toDoubleOrNull()?.let { sexLon = formatSexagesimal(it, Axis.Longitude) }
    }

    fun syncDecimalFromSexagesimal() {
        if (sexLat.isBlank() && sexLon.isBlank()) {
            sexError = null
            onInputError(null)
            clearDecimal()
            return
        }
        val latitude = CoordinateUtils.parseSexagesimal(sexLat, Axis.Latitude)
        val longitude = CoordinateUtils.parseSexagesimal(sexLon, Axis.Longitude)
        if (latitude == null || longitude == null) {
            sexError = invalidSexagesimalText
            onInputError(invalidSexagesimalText)
        } else {
            sexError = null
            onInputError(null)
            pushDecimal(latitude, longitude)
        }
    }

    LaunchedEffect(coordinateFormat, lat, lon) {
        if (lat.isBlank() || lon.isBlank()) return@LaunchedEffect
        if (pushedPair == (lat to lon)) return@LaunchedEffect
        if (isUtm) syncUtmFromDecimal()
        if (isSexagesimal) syncSexagesimalFromDecimal()
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(ui("Coordenadas"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        HelpButton(ui("Incerteza da coordenada"), ui("Raio, em metros, dentro do qual o ponto verdadeiro deve estar. O GPS do celular informa uma estimativa; corrija se usou outro receptor. As exportações guardam a coordenada em graus decimais."))
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
    }
    if (isSexagesimal) {
        HelpCard(
            if (coordinateFormat == "DMS") "Graus, minutos e segundos" else "Graus e minutos decimais",
            "Digite como está no GPS de mão ou na caderneta, com N/S e L/O (ou E/W). Exemplos: 22°25'51.6\"S, 22 25 51.6 S, 44 58.74 O. O app guarda em graus decimais e exporta também como foi lido (verbatimCoordinates)."
        )
        TextInput("Latitude ($coordinateFormat)", sexLat, autoCorrect = false) {
            sexLat = it
            syncDecimalFromSexagesimal()
        }
        TextInput("Longitude ($coordinateFormat)", sexLon, autoCorrect = false) {
            sexLon = it
            syncDecimalFromSexagesimal()
        }
        sexError?.let {
            Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val suffix = if (isUtm || isSexagesimal) " DD" else ""
        TextInput("Latitude$suffix", lat, KeyboardType.Decimal, Modifier.weight(1f), signToggle = true, onChange = onLat)
        TextInput("Longitude$suffix", lon, KeyboardType.Decimal, Modifier.weight(1f), signToggle = true, onChange = onLon)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextInput("Altitude (m)", alt, KeyboardType.Decimal, Modifier.weight(1f), onChange = onAlt)
        TextInput("Incerteza (m)", accuracy, KeyboardType.Decimal, Modifier.weight(1f), onChange = onAccuracy)
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
    autoCorrect: Boolean = true,
    // Botao ± para coordenada: alguns teclados numericos (Samsung, por exemplo)
    // nao mostram o sinal de menos, e no Brasil toda coordenada e negativa.
    signToggle: Boolean = false,
    capitalization: KeyboardCapitalization = if (autoCorrect) KeyboardCapitalization.Sentences else KeyboardCapitalization.None,
    onChange: (String) -> Unit
) {
    Column(modifier) {
        val displayLabel = ui(label)
        val displayHelp = help?.let { ui(it) }
        val numeric = keyboardType == KeyboardType.Number || keyboardType == KeyboardType.Decimal
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text(displayLabel) },
            modifier = Modifier.fillMaxWidth(),
            minLines = minLines,
            // Campo curto e de uma linha: o Enter nao insere quebra em nome ou ID.
            singleLine = minLines == 1,
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                autoCorrectEnabled = autoCorrect && !numeric && keyboardType != KeyboardType.Uri,
                capitalization = if (numeric || keyboardType == KeyboardType.Uri) KeyboardCapitalization.None else capitalization
            ),
            trailingIcon = when {
                signToggle -> {
                    {
                        TextButton(onClick = {
                            val trimmed = value.trim()
                            onChange(if (trimmed.startsWith("-")) trimmed.removePrefix("-") else "-$trimmed")
                        }) { Text("±", style = MaterialTheme.typography.titleMedium) }
                    }
                }
                displayHelp != null -> { { HelpButton(displayLabel, displayHelp) } }
                else -> null
            }
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
    val text = Regex("""^(\d{1,2})\s+([A-Za-zÀ-ÿ]{3,})\.?\s+(\d{4})$""").matchEntire(trimmed.lowercase(Locale.forLanguageTag("pt-BR")))
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

/**
 * Campo de vocabulario controlado do Darwin Core.
 *
 * Os chips mostram o rotulo em portugues, mas o valor gravado e exportado e
 * sempre o termo do padrao em ingles, que e o que GBIF, SiBBr e IPT consomem.
 * "Outro" libera digitacao para os casos que o vocabulario nao cobre, sem
 * impedir o registro em campo.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DwcVocabularyInput(
    label: String,
    value: String,
    options: List<String>,
    help: String? = null,
    onChange: (String) -> Unit
) {
    val isFreeText = value.isNotBlank() && value !in options
    var freeText by remember(label) { mutableStateOf(isFreeText) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            if (help != null) HelpButton(label, help)
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            options.forEach { option ->
                FilterChip(
                    selected = !freeText && value == option,
                    onClick = {
                        freeText = false
                        onChange(option)
                    },
                    label = { Text(dwcValueLabel(option)) }
                )
            }
            FilterChip(
                selected = freeText,
                onClick = { freeText = true },
                label = { Text(ui("Outro")) }
            )
        }
        if (freeText) {
            TextInput(ui("Valor livre"), value, help = "Sai na exportação exatamente como digitado.") { onChange(it) }
        } else if (value.isNotBlank()) {
            Text(
                "dwc: $value",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Rotulo em portugues para um valor de vocabulario controlado. */
private fun dwcValueLabel(value: String): String = when (value) {
    "HumanObservation" -> "Observação"
    "MachineObservation" -> "Registro automático"
    "PreservedSpecimen" -> "Espécime tombado"
    "MaterialSample" -> "Amostra"
    "LivingSpecimen" -> "Espécime vivo"
    "FossilSpecimen" -> "Fóssil"
    "MaterialCitation" -> "Citação de material"
    "present" -> "Presente"
    "absent" -> "Ausente"
    "male" -> "Macho"
    "female" -> "Fêmea"
    "hermaphrodite" -> "Hermafrodita"
    "undetermined", "unknown", "uncertain" -> "Indeterminado"
    "egg" -> "Ovo"
    "larva" -> "Larva"
    "tadpole" -> "Girino"
    "juvenile" -> "Jovem"
    "subadult" -> "Subadulto"
    "adult" -> "Adulto"
    "seedling" -> "Plântula"
    "sapling" -> "Juvenil arbóreo"
    "seed" -> "Semente"
    "kingdom" -> "Reino"
    "phylum" -> "Filo"
    "class" -> "Classe"
    "order" -> "Ordem"
    "family" -> "Família"
    "genus" -> "Gênero"
    "species" -> "Espécie"
    "subspecies" -> "Subespécie"
    "variety" -> "Variedade"
    "form" -> "Forma"
    "holotype" -> "Holótipo"
    "paratype" -> "Parátipo"
    "isotype" -> "Isótipo"
    "lectotype" -> "Lectótipo"
    "neotype" -> "Neótipo"
    "syntype" -> "Sintipo"
    "topotype" -> "Topótipo"
    "notAType" -> "Não é tipo"
    "native" -> "Nativa"
    "nativeReintroduced" -> "Nativa reintroduzida"
    "introduced" -> "Introduzida"
    "introducedAssistedColonisation" -> "Introduzida (colonização assistida)"
    "vagrant" -> "Errante"
    "captive" -> "Cativeiro"
    "cultivated" -> "Cultivada"
    "released" -> "Solta"
    "failing" -> "Em declínio"
    "casual" -> "Casual"
    "reproducing" -> "Reproduzindo"
    "established" -> "Estabelecida"
    "colonising" -> "Colonizando"
    "invasive" -> "Invasora"
    "widespreadInvasive" -> "Invasora amplamente distribuída"
    "corridor" -> "Corredor"
    "escapeFromConfinement" -> "Escape de cativeiro"
    "releaseInNature" -> "Soltura na natureza"
    "transportContaminant" -> "Contaminante de transporte"
    "transportStowaway" -> "Clandestino em transporte"
    "unaided" -> "Dispersão própria"
    "alive" -> "Vivo"
    "dead" -> "Morto"
    "mixed" -> "Misto"
    "unverified" -> "Não verificada"
    "verifiedBySpecialist" -> "Verificada por especialista"
    "requiresVerification" -> "Requer verificação"
    "verified" -> "Verificada"
    "exsicata" -> "Exsicata"
    "alcohol" -> "Álcool"
    "driedWhole" -> "Seco inteiro"
    "pinned" -> "Alfinetado"
    "tissueInSilica" -> "Tecido em sílica"
    "tissueInEthanol" -> "Tecido em etanol"
    "skeleton" -> "Esqueleto"
    "skin" -> "Pele"
    "slide" -> "Lâmina"
    "photographOnly" -> "Somente fotografia"
    "inCollection" -> "Depositado"
    "inProcessing" -> "Em processamento"
    "onLoan" -> "Emprestado"
    "missing" -> "Extraviado"
    "destroyed" -> "Destruído"
    "notCollected" -> "Não coletado"
    "metre" -> "metro"
    "squareMetre" -> "metro quadrado"
    "hectare" -> "hectare"
    "kilometre" -> "quilômetro"
    "hour" -> "hora"
    "minute" -> "minuto"
    "day" -> "dia"
    "trapNight" -> "armadilha-noite"
    "individual" -> "indivíduo"
    else -> value
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
private fun AdvancedOccurrenceSections(formType: String, values: DynamicValues, fields: List<FieldConfigEntity>) {
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

/** Ordem dos grupos na tela de campos: do essencial ao especializado. */
private val fieldGroupOrder = listOf(
    "Básicos", "Localização", "Ocorrência", "Identificação", "Taxonomia", "Amostragem", "Ambiente",
    "Anotações", "Mídia", "Fauna", "Flora", "Funga", "Vestígios", "Coleção", "Ecologia",
    "Georreferenciamento", "Autoria", "MeasurementOrFact"
)

private fun fieldGroupDisplayName(group: String): String = when (group) {
    "Botanica" -> "Flora"
    "Fungos" -> "Funga"
    "Basicos" -> "Básicos"
    "Localizacao" -> "Localização"
    "Ocorrencia" -> "Ocorrência"
    "Identificacao" -> "Identificação"
    "Anotacoes" -> "Anotações"
    "Localização" -> "Localização"
    "Identificação" -> "Identificação"
    "Anotações" -> "Anotações"
    "Mídia" -> "Mídia"
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
private fun AdvancedFieldGroup(title: String, description: String, fields: List<Pair<String, String>>, values: DynamicValues) {
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
                // Teclado conforme o tipo declarado no catalogo: antes DAP, CAP,
                // alturas e temperatura abriam teclado de texto.
                val definition = DarwinCoreCatalog.byTerm[key]
                val keyboard = when (definition?.type) {
                    "decimal" -> KeyboardType.Decimal
                    "integer" -> KeyboardType.Number
                    else -> KeyboardType.Text
                }
                TextInput(
                    label,
                    values[key],
                    keyboard,
                    minLines = if (definition?.type == "long_text") 3 else 1,
                    help = help,
                    autoCorrect = key !in DarwinCoreCatalog.noAutocorrectTerms
                ) {
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

private fun updateBotanicalDiameterPair(key: String, value: String, values: DynamicValues) {
    // Campo apagado apaga o par calculado, para nao sobrar um CAP de um DAP que nao existe mais.
    if (value.isBlank()) {
        when (key) {
            "dbh" -> values["cap"] = ""
            "cap" -> values["dbh"] = ""
        }
        return
    }
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

/**
 * Campos habilitados e marcados como obrigatorios que ainda estao vazios.
 *
 * Le o valor onde ele de fato mora: colunas tipadas da OccurrenceEntity para os
 * termos do nucleo, e dynamicFieldsJson para o restante do catalogo.
 */
private fun missingRequiredFields(
    enabledFields: List<FieldConfigEntity>,
    occurrence: OccurrenceEntity,
    dynamicValues: Map<String, String>
): List<FieldConfigEntity> {
    val tipados: Map<String, String> = mapOf(
        "occurrenceID" to occurrence.occurrenceId,
        "basisOfRecord" to occurrence.basisOfRecord,
        "scientificName" to occurrence.scientificName,
        "vernacularName" to occurrence.vernacularName,
        "recordNumber" to occurrence.recordNumber,
        "occurrenceStatus" to occurrence.occurrenceStatus,
        "individualCount" to occurrence.individualCount?.toString().orEmpty(),
        "sex" to occurrence.sex,
        "lifeStage" to occurrence.lifeStage,
        "behavior" to occurrence.behavior,
        "reproductiveCondition" to occurrence.reproductiveCondition,
        "recordedBy" to occurrence.recordedBy,
        "identifiedBy" to occurrence.identifiedBy,
        "dateIdentified" to occurrence.dateIdentified,
        "occurrenceRemarks" to occurrence.occurrenceRemarks,
        "fieldNotes" to occurrence.fieldNotes,
        "decimalLatitude" to occurrence.latitude?.toString().orEmpty(),
        "decimalLongitude" to occurrence.longitude?.toString().orEmpty(),
        "minimumElevationInMeters" to occurrence.altitude?.toString().orEmpty(),
        "coordinateUncertaintyInMeters" to occurrence.coordinateUncertaintyInMeters?.toString().orEmpty(),
        "geodeticDatum" to occurrence.geodeticDatum
    )
    return enabledFields
        .filter { it.required }
        .filter { campo ->
            val valor = tipados[campo.dwcTerm] ?: dynamicValues[campo.dwcTerm].orEmpty()
            valor.isBlank()
        }
}

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

/**
 * Termos que o formulario ja mostra no bloco principal e que nao estao em
 * [fixedOccurrenceTerms]. Sem esta lista eles apareciam de novo em "campos
 * extras": basisOfRecord tinha dois controles, e o segundo, gravado no JSON
 * dinamico, sobrescrevia o primeiro na exportacao.
 */
private val formOwnedTerms = setOf("occurrenceID", "recordNumber", "basisOfRecord", "fieldNotes")

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
    // Data e hora da propria captura. Antes a data era a da expedicao e a hora
    // a da captura: um audio gravado as 00:36 do dia 18 numa expedicao do dia
    // 17 ganhava o nome "17_0036", um instante que nao existiu.
    val now = Date()
    val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(now)
    val time = SimpleDateFormat("HHmm", Locale.US).format(now)
    val area = projectAcronym(project?.name.orEmpty())
    val expedition = shortCode(session?.name.orEmpty())
    val point = occurrence?.recordNumber?.ifBlank { occurrence.occurrenceID.takeLast(4) }.orEmpty()
    // Sem especie nem assunto: os dois podem ser corrigidos depois, e o nome
    // guardado no aparelho ficaria com a identificacao antiga. A exportacao
    // monta o nome com a especie atual.
    return listOf(date, time, area, expedition, point, type)
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


/**
 * Miniatura decodificada fora da main thread. Antes era decodificada durante a
 * composicao de cada cartao: abrir uma expedicao com muitas fotos travava a
 * rolagem em aparelho modesto.
 */
@Composable
private fun rememberThumbnail(path: String, version: Long): android.graphics.Bitmap? =
    androidx.compose.runtime.produceState<android.graphics.Bitmap?>(null, path, version) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { decodeThumbnail(path, 180) }
    }.value

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

