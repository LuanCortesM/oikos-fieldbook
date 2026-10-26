package com.oikos.fieldbook

import com.oikos.fieldbook.util.UiLanguage

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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.produceState
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.TableChart
import com.oikos.fieldbook.export.ScientificName
import com.oikos.fieldbook.export.TaxonAnalysis
import com.oikos.fieldbook.ui.CollectorCurveChart
import com.oikos.fieldbook.ui.AnalysisBarChart
import com.oikos.fieldbook.ui.RankAbundanceChart
import com.oikos.fieldbook.ui.SimilarityHeatmap
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.NearMe
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
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.layout.layout
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
import com.oikos.fieldbook.checklist.MapIconGroup
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
import kotlinx.coroutines.flow.first
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
import com.oikos.fieldbook.ui.englishText
import com.oikos.fieldbook.ui.spanishText
import com.oikos.fieldbook.dwc.spanishFieldLabels
import com.oikos.fieldbook.util.AppLanguage
import com.oikos.fieldbook.util.inProjectLanguage
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
        val systemSplash = installSplashScreen()
        // A abertura do sistema (Android 12+) cobre os primeiros quadros do app:
        // a barra do Itaguare so comeca quando ela sai, para aparecer desde o inicio.
        val systemSplashGone = mutableStateOf(savedInstanceState != null)
        systemSplash.setOnExitAnimationListener { provider ->
            provider.remove()
            systemSplashGone.value = true
        }
        super.onCreate(savedInstanceState)
        applySystemBars(darkTheme = true)
        val languagePrefs = getSharedPreferences("oikos_app_settings", Context.MODE_PRIVATE)
        val deviceLanguage = currentDeviceUiLanguage()
        // A politica de tiles do OpenStreetMap pede um User-Agent proprio e
        // estavel, com o nome do app (operations.osmfoundation.org/policies/tiles).
        Configuration.getInstance().userAgentValue = "OikosFieldBook/${BuildConfig.VERSION_NAME} ($packageName)"
        setContent {
            // Tema e modo simples passam a persistir entre aberturas, junto do
            // idioma, que ja persistia. Antes voltavam ao padrao a cada inicio
            // e a cada rotacao de tela.
            var darkTheme by remember { mutableStateOf(languagePrefs.getBoolean("dark_theme", true)) }
            var simpleMode by remember { mutableStateOf(languagePrefs.getBoolean("simple_mode", true)) }
            var languageMode by remember { mutableStateOf(languagePrefs.getString("app_language", "auto") ?: "auto") }
            var showLaunch by rememberSaveable { mutableStateOf(true) }
            val uiLanguage = AppLanguage.resolve(languageMode, deviceLanguage)
            var launchBarRunning by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                // Sem o aviso de saida (abertura sem a tela do sistema), segue em 400 ms.
                kotlinx.coroutines.withTimeoutOrNull(400) {
                    androidx.compose.runtime.snapshotFlow { systemSplashGone.value }.first { it }
                }
                launchBarRunning = true
                // A caminhada e um respiro com o trilheiro parado no fim.
                delay(LAUNCH_WALK_MS + 250L)
                showLaunch = false
            }
            LaunchedEffect(darkTheme) {
                applySystemBars(darkTheme)
            }
            CompositionLocalProvider(LocalOikosLanguage provides uiLanguage) {
                OikosTheme(darkTheme = darkTheme) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        if (showLaunch) {
                            OikosLaunchScreen(barRunning = launchBarRunning)
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

    /** Codigo do idioma do celular ("pt", "es", "en"...), para o modo automatico. */
    private fun currentDeviceUiLanguage(): String = resources.configuration.locales[0].language

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

private val LocalOikosLanguage = staticCompositionLocalOf { AppLanguage.PT }

/** Mapa offline escolhido em Configuracoes; nulo = OpenStreetMap pela internet. */
private val LocalOfflineMap = compositionLocalOf<OfflineMap?> { null }

/** Tempo da caminhada do trilheiro na abertura. */
private const val LAUNCH_WALK_MS = 3_000L

/** Distancia entre o pe do logo e o topo do contorno do Itaguare na abertura. */
private val LAUNCH_BAR_GAP = 60.dp

/** Altura do logo na abertura: -1 no topo, 0 no centro; -0,42 deixa o logo perto de 1/3 da tela. */
private const val LAUNCH_LOGO_BIAS = -0.42f

@Composable
private fun OikosLaunchScreen(barRunning: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0B0D)),
        contentAlignment = Alignment.Center
    ) {
        // Logo do mesmo tamanho (84% da largura), um pouco acima do centro
        // (por volta de 1/3 da altura), para o credito caber embaixo. O
        // contorno do Itaguare fica abaixo dele, com o trilheiro andando por ele.
        Box(
            modifier = Modifier
                .align(androidx.compose.ui.BiasAlignment(0f, LAUNCH_LOGO_BIAS))
                .fillMaxWidth(0.84f)
                .widthIn(max = 485.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Image(
                painter = painterResource(id = R.drawable.oikos_brand_logo),
                contentDescription = "Oikos FieldBook logo",
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1200f / 520f),
                contentScale = ContentScale.Fit
            )
            com.oikos.fieldbook.ui.ItaguareLoadingBar(
                running = barRunning,
                durationMillis = LAUNCH_WALK_MS.toInt(),
                modifier = Modifier
                    .fillMaxWidth(0.70f)
                    .layout { measurable, constraints ->
                        val bar = measurable.measure(constraints)
                        layout(bar.width, bar.height) {
                            bar.place(0, bar.height + LAUNCH_BAR_GAP.roundToPx())
                        }
                    },
                hikerHeight = 11.25.dp
            )
        }
        // Deitado (pouca altura) o credito cairia em cima do contorno: so em pe.
        val tallEnough = LocalConfiguration.current.screenHeightDp >= 560
        if (tallEnough) {
            LaunchCredit(
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 30.dp)
            )
        }
    }
}

/**
 * Credito da abertura, no estilo do "— FIELD BOOK —" do logo: o projeto em
 * laranja e, embaixo, a autoria entre dois tracos do mesmo tamanho da linha de
 * cima. Em tela estreita a letra e o espacamento diminuem antes de cortar.
 */
@Composable
private fun LaunchCredit(modifier: Modifier = Modifier) {
    // O laranja das letras "FIELD BOOK" do logo (medido no PNG): sem azul, para nao ficar opaco ao lado dele.
    val orange = Color(0xFFFB8C00)
    val project = "PROJETO HERPETO MANTIQUEIRA"
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val measurer = androidx.compose.ui.text.rememberTextMeasurer()
        val available = constraints.maxWidth * 0.66f
        val sizes = listOf(11.5f to 0.3f, 11f to 0.26f, 10.5f to 0.22f, 10f to 0.16f)
        val (size, spacing) = sizes.firstOrNull { (fontSize, letterSpacing) ->
            val style = androidx.compose.ui.text.TextStyle(fontSize = fontSize.sp, letterSpacing = letterSpacing.em, fontWeight = FontWeight.SemiBold)
            measurer.measure(project, style, maxLines = 1, softWrap = false).size.width <= available
        } ?: sizes.last()
        Column(
            Modifier.width(IntrinsicSize.Max),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            // Altura de linha justa: a padrao do tema (24 sp) separava demais as duas linhas.
            Text(project, color = orange, fontSize = size.sp, lineHeight = (size * 1.2f).sp, letterSpacing = spacing.em, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).height(1.5.dp).background(orange))
                Text(
                    "DEVELOPED BY MACIEL, L. S. C.",
                    color = Color(0xFFE6E6E6),
                    fontSize = (size * 0.72f).sp,
                    lineHeight = (size * 0.72f * 1.2f).sp,
                    letterSpacing = (spacing + 0.12f).em,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(horizontal = 10.dp)
                )
                Box(Modifier.weight(1f).height(1.5.dp).background(orange))
            }
        }
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
            is Screen.Analysis -> listOf("analysis", tela.projectId)
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
            "analysis" -> Screen.Analysis(longEm(1))
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
    data class Analysis(val projectId: Long) : Screen
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

    // As mensagens nascem em portugues no ViewModel; as fixas sao traduzidas aqui.
    val messageLanguage = LocalOikosLanguage.current
    fun inUiLanguage(text: String) = uiText(text, messageLanguage)
    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(inUiLanguage(it))
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
                onAnalysis = { screen = Screen.Analysis(current.projectId) },
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
                warning?.let { scope.launch { snackbarHostState.showSnackbar(inUiLanguage(it)) } }
                context.startActivity(Intent.createChooser(intent, shareChooserTitle))
            }
            is Screen.Analysis -> AnalysisScreen(viewModel, current.projectId, screenModifier, onBack = {
                screen = Screen.ProjectDetail(current.projectId)
            }) { intent, warning ->
                warning?.let { scope.launch { snackbarHostState.showSnackbar(inUiLanguage(it)) } }
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
    is Screen.Analysis -> Screen.ProjectDetail(projectId)
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
        // a letra ("Exped / icao / 2026...") espremido pelos icones. Antes das
        // reticencias, a letra diminui um pouco se o titulo nao couber.
        title = { FittingTitle(ui(title)) },
        navigationIcon = {
            if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, ui("Voltar")) }
        },
        actions = { actions() }
    )
}

/** Titulo de uma linha: no tamanho normal se couber; senao 85% ou 75% dele; so entao reticencias. */
@Composable
private fun FittingTitle(text: String) {
    BoxWithConstraints {
        val base = androidx.compose.material3.LocalTextStyle.current
        val measurer = androidx.compose.ui.text.rememberTextMeasurer()
        val style = if (!constraints.hasBoundedWidth) base else {
            listOf(1f, 0.85f, 0.75f)
                .map { base.copy(fontSize = base.fontSize * it) }
                .firstOrNull { measurer.measure(text, it, maxLines = 1, softWrap = false).size.width <= constraints.maxWidth }
                ?: base.copy(fontSize = base.fontSize * 0.75f)
        }
        Text(text, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
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
    // Relato de problema ou ideia: um toque na tela inicial, ao lado da engrenagem.
    var showFeedback by rememberSaveable { mutableStateOf(false) }
    var feedbackKind by rememberSaveable { mutableStateOf("problem") }
    var feedbackText by rememberSaveable { mutableStateOf("") }
    if (showFeedback) {
        FeedbackDialog(feedbackKind, feedbackText, onKind = { feedbackKind = it }, onText = { feedbackText = it }, onDismiss = { showFeedback = false })
    }
    Scaffold(
        modifier = modifier,
        topBar = {
            OikosTopBar("Oikos FieldBook", actions = {
                IconButton(onClick = { showFeedback = true }) { Icon(Icons.Default.Feedback, ui("Relatar problema ou dar ideia")) }
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
    val defaultProjectLanguage = LocalOikosLanguage.current.code
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
                    "enableFauna" to "true", "enableBotany" to "false", "enableFungi" to "false", "csvStyle" to "br",
                    "nameGroups" to ""
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
                    "mediaBaseUrl" to project.mediaBaseUrl, "csvStyle" to project.csvStyle,
                    "nameGroups" to project.nameGroups
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
                        csvStyle = form["csvStyle"].ifBlank { "br" },
                        nameGroups = form["nameGroups"]
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
            // "Média" ja e chave de outra traducao ("Average", da media de GPS).
            val mediumQuality = tr("Média", "Medium", "Media")
            ChipRow("Qualidade da foto", listOf("low" to "Baixa", "medium" to mediumQuality, "high" to "Alta"), form["photoQuality"]) { form["photoQuality"] = it }
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
            NameGroupSelector(form["nameGroups"]) { form["nameGroups"] = it }
        }
    }
}

/**
 * Grupos das listas de especies que entram nas sugestoes de nome do projeto.
 * Nenhum marcado = todos. [selection]: codigos separados por virgula.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NameGroupSelector(selection: String, onChange: (String) -> Unit) {
    val chosen = com.oikos.fieldbook.checklist.NameGroup.parseSelection(selection)
    val language = LocalOikosLanguage.current
    fun toggle(group: com.oikos.fieldbook.checklist.NameGroup) {
        val next = if (group.code in chosen) chosen - group.code else chosen + group.code
        // Ordem fixa (a do enum) para o valor salvo nao mudar a toa.
        onChange(com.oikos.fieldbook.checklist.NameGroup.entries.filter { it.code in next }.joinToString(",") { it.code })
    }
    Text(ui("Nomes sugeridos neste projeto"), style = MaterialTheme.typography.titleMedium)
    Text(
        ui("Escolha os grupos das listas de espécies que entram nas sugestões de nome. Sem nenhum marcado, valem todos."),
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    FilterChip(selected = chosen.isEmpty(), onClick = { onChange("") }, label = { Text(ui("Todos os grupos")) })
    listOf(false to "Fauna", true to "Flora e Funga").forEach { (flora, title) ->
        Text(ui(title), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            com.oikos.fieldbook.checklist.NameGroup.entries.filter { it.flora == flora }.forEach { group ->
                FilterChip(
                    selected = group.code in chosen,
                    onClick = { toggle(group) },
                    label = { Text(when (language) { AppLanguage.PT -> group.pt; AppLanguage.EN -> group.en; AppLanguage.ES -> group.es }) }
                )
            }
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
    onAnalysis: () -> Unit,
    onDeleted: () -> Unit
) {
    val project by remember(viewModel, projectId) { viewModel.observeProject(projectId) }.collectAsState(initial = null)
    val sessions by remember(viewModel, projectId) { viewModel.observeSessions(projectId) }.collectAsState(initial = emptyList())
    val occurrences by remember(viewModel, projectId) { viewModel.observeProjectOccurrences(projectId) }.collectAsState(initial = emptyList())
    val analysis by produceState<TaxonAnalysis?>(null, sessions, occurrences) {
        value = withContext(Dispatchers.Default) { TaxonAnalysis(sessions, occurrences) }
    }
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
            // So aparece quando ha registro: sem dado nao ha o que analisar.
            if (occurrences.isNotEmpty()) item { AnalysisEntryCard(analysis, onAnalysis) }
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

/** Entrada da analise na tela do projeto, ja com o resumo. */
@Composable
private fun AnalysisEntryCard(analysis: TaxonAnalysis?, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Card(
        Modifier.fillMaxWidth(),
        border = BorderStroke(1.5.dp, colors.primary),
        colors = CardDefaults.cardColors(containerColor = colors.primary.copy(alpha = 0.08f))
    ) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(colors.primary), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.ShowChart, null, tint = colors.onPrimary)
            }
            Column(Modifier.weight(1f)) {
                Text(ui("Análise do projeto"), style = MaterialTheme.typography.titleMedium)
                if (analysis != null) {
                    val taxa = analysis.taxa.size
                    val individuals = analysis.totalIndividuals
                    val expeditions = analysis.expeditions.size
                    Text(
                        tr(
                            "$taxa ${if (taxa == 1) "táxon" else "táxons"} · $individuals ${if (individuals == 1) "indivíduo" else "indivíduos"} · $expeditions ${if (expeditions == 1) "expedição" else "expedições"}",
                            "$taxa ${if (taxa == 1) "taxon" else "taxa"} · $individuals ${if (individuals == 1) "individual" else "individuals"} · $expeditions ${if (expeditions == 1) "expedition" else "expeditions"}",
                            "$taxa ${if (taxa == 1) "taxón" else "taxones"} · $individuals ${if (individuals == 1) "individuo" else "individuos"} · $expeditions ${if (expeditions == 1) "salida" else "salidas"}"
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant
                    )
                }
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = colors.primary)
        }
    }
}

/** Taxons mostrados antes de "Mostrar todos": projeto grande tem centenas. */
private const val ANALYSIS_TAXA_PREVIEW = 30

/** Abas da analise: cada analise no seu lugar, para a tela nao virar uma lista sem fim. */
private enum class AnalysisTab(val title: String) {
    SUMMARY("Resumo"), TAXA("Táxons"), DIVERSITY("Diversidade"), TIME("Tempo")
}

/**
 * Analise do projeto, em abas: resumo e curva do coletor; taxons com
 * dominancia, constancia e perfil; diversidade e similaridade; familias;
 * meses e horarios. O calculo e o mesmo das abas de analise da exportacao
 * (TaxonAnalysis), entao o app e a planilha mostram sempre os mesmos numeros.
 * Nenhum registro muda.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnalysisScreen(viewModel: AppViewModel, projectId: Long, modifier: Modifier, onBack: () -> Unit, onShare: (Intent, String?) -> Unit) {
    val project by remember(viewModel, projectId) { viewModel.observeProject(projectId) }.collectAsState(initial = null)
    val sessions by remember(viewModel, projectId) { viewModel.observeSessions(projectId) }.collectAsState(initial = null)
    val occurrences by remember(viewModel, projectId) { viewModel.observeProjectOccurrences(projectId) }.collectAsState(initial = null)
    val exporting by viewModel.exporting.collectAsState()
    // Fora da thread principal: projeto grande tem milhares de registros.
    val analysis by produceState<TaxonAnalysis?>(null, sessions, occurrences) {
        val loadedSessions = sessions ?: return@produceState
        val loadedOccurrences = occurrences ?: return@produceState
        value = withContext(Dispatchers.Default) { TaxonAnalysis(loadedSessions, loadedOccurrences) }
    }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        modifier = modifier,
        topBar = {
            OikosTopBar("Análise", onBack) {
                IconButton(
                    onClick = { viewModel.exportProject(projectId, ExportFormat.Xlsx, ExportContent.SpreadsheetsOnly, onShare) },
                    enabled = !exporting
                ) { Icon(Icons.Default.Share, ui("Exportar planilha (XLSX)")) }
            }
        }
    ) { padding ->
        val current = analysis
        if (current == null) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        val projectName = project?.name.orEmpty()
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (current.recordsUsed > 0) {
                PrimaryScrollableTabRow(selectedTabIndex = tab, edgePadding = 16.dp) {
                    AnalysisTab.entries.forEachIndexed { index, entry ->
                        Tab(
                            selected = tab == index,
                            onClick = { tab = index },
                            text = { Text(ui(entry.title)) },
                            selectedContentColor = MaterialTheme.colorScheme.primary,
                            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            // Uma lista por aba: trocar de aba volta ao topo, em vez de herdar a rolagem da outra.
            key(tab) {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (current.recordsUsed == 0) {
                        item { AnalysisHeader(projectName, current) }
                        item { EmptyState("Nada para analisar ainda.", "Entram os registros com nome científico, menos os de ausência.") }
                    } else {
                        when (AnalysisTab.entries[tab]) {
                            AnalysisTab.SUMMARY -> analysisSummary(projectName, current)
                            AnalysisTab.TAXA -> analysisTaxa(current)
                            AnalysisTab.DIVERSITY -> analysisDiversity(current)
                            AnalysisTab.TIME -> analysisTime(current)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnalysisHeader(projectName: String, analysis: TaxonAnalysis) {
    val language = LocalOikosLanguage.current
    val count = analysis.expeditions.size
    val dates = analysis.expeditions.map { it.session.eventDate }.filter { it.isNotBlank() }
    val period = when {
        dates.isEmpty() -> ""
        dates.first() == dates.last() -> " · ${analysisDate(dates.first(), language)}"
        else -> " · ${analysisDate(dates.first(), language)} ${tr("a", "to", "a")} ${analysisDate(dates.last(), language)}"
    }
    Column {
        Text(projectName, style = MaterialTheme.typography.titleLarge)
        Text("$count ${ui(if (count == 1) "expedição" else "expedições")}$period")
    }
}

/** Cartao das abas: titulo, uma linha que explica e o conteudo. */
@Composable
private fun AnalysisCard(title: String, hint: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(ui(title), style = MaterialTheme.typography.titleMedium)
            if (hint != null) Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

/** Texto miudo do fim de cada aba: como os numeros foram contados. */
@Composable
private fun AnalysisNotes(vararg lines: String) {
    Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        lines.filter { it.isNotBlank() }.forEach {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AnalysisDivider() = HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

/** Barra fina de 0 a 1, laranja sobre trilho apagado. */
@Composable
private fun AnalysisBar(fraction: Float, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(colors.onSurface.copy(alpha = 0.12f))) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().background(colors.primary))
    }
}

private fun LazyListScope.analysisSummary(projectName: String, analysis: TaxonAnalysis) {
    item { AnalysisHeader(projectName, analysis) }
    item {
        val expeditions = analysis.expeditions.size
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AnalysisStat(analysis.taxa.size.toString(), ui("táxons (riqueza)"), Modifier.weight(1f))
                AnalysisStat(analysis.totalIndividuals.toString(), ui("indivíduos"), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AnalysisStat(analysis.recordsUsed.toString(), ui("registros"), Modifier.weight(1f))
                AnalysisStat(expeditions.toString(), ui("expedições"), Modifier.weight(1f))
            }
        }
    }
    item {
        val language = LocalOikosLanguage.current
        val values = analysis.expeditions.map { it.cumulativeRichness }
        AnalysisCard("Curva do coletor", ui("Táxons acumulados por expedição, em ordem de data.")) {
            CollectorCurveChart(
                values = values,
                labels = analysis.expeditions.mapIndexed { index, expedition -> analysisChartLabel(expedition.session.eventDate, index, language) },
                description = tr(
                    "Curva do coletor: ${values.joinToString(", ")} táxons acumulados.",
                    "Collector's curve: ${values.joinToString(", ")} cumulative taxa.",
                    "Curva del colector: ${values.joinToString(", ")} taxones acumulados."
                ),
                background = CardDefaults.cardColors().containerColor,
                modifier = Modifier.padding(top = 8.dp)
            )
            val last = analysis.expeditions.last()
            if (analysis.expeditions.size > 1) {
                Text(
                    if (last.newTaxa == 0) {
                        tr("A última expedição não trouxe táxon novo.", "The last expedition added no new taxon.", "La última salida de campo no agregó taxones nuevos.")
                    } else {
                        tr(
                            "A última expedição trouxe ${last.newTaxa} ${if (last.newTaxa == 1) "táxon novo" else "táxons novos"}.",
                            "The last expedition added ${last.newTaxa} new ${if (last.newTaxa == 1) "taxon" else "taxa"}.",
                            "La última salida de campo agregó ${last.newTaxa} ${if (last.newTaxa == 1) "taxón nuevo" else "taxones nuevos"}."
                        )
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
    item {
        val language = LocalOikosLanguage.current
        AnalysisCard("Por expedição") {
            analysis.expeditions.forEachIndexed { index, expedition ->
                if (index > 0) AnalysisDivider()
                AnalysisExpeditionRow(expedition, language)
            }
        }
    }
    item {
        AnalysisNotes(
            ui("Registros de ausência e sem nome científico ficam de fora. Registro sem contagem vale 1 indivíduo."),
            analysisLeftOut(
                analysis.absenceRecords to Triple("de ausência", "marked absent", "de ausencia"),
                analysis.recordsWithoutName to Triple("sem nome científico", "with no scientific name", "sin nombre científico")
            ),
            when (val grouping = analysis.groupingExpeditions) {
                0 -> ""
                1 -> tr(
                    "1 expedição só agrupa outras (parentEventID) e não entra na contagem.",
                    "1 expedition only groups others (parentEventID) and is not counted.",
                    "1 salida de campo solo agrupa otras (parentEventID) y no se cuenta."
                )
                else -> tr(
                    "$grouping expedições só agrupam outras (parentEventID) e não entram na contagem.",
                    "$grouping expeditions only group others (parentEventID) and are not counted.",
                    "$grouping salidas de campo solo agrupan otras (parentEventID) y no se cuentan."
                )
            }
        )
    }
}

private fun LazyListScope.analysisTaxa(analysis: TaxonAnalysis) {
    val ranked = analysis.taxa.sortedWith(compareByDescending<TaxonAnalysis.Taxon> { it.individuals }.thenBy { it.scientificName.lowercase(Locale.ROOT) })
    item {
        val language = LocalOikosLanguage.current
        val percents = ranked.map { analysis.relativeAbundancePercent(it) }
        AnalysisCard("Dominância", ui("Abundância relativa por táxon, em escala logarítmica.")) {
            RankAbundanceChart(
                percents = percents,
                description = tr(
                    "Rank-abundância de ${ranked.size} táxons.",
                    "Rank-abundance of ${ranked.size} taxa.",
                    "Rango-abundancia de ${ranked.size} taxones."
                ),
                background = CardDefaults.cardColors().containerColor,
                modifier = Modifier.padding(top = 8.dp)
            )
            ranked.firstOrNull()?.takeIf { it.individuals > 0 }?.let { top ->
                val share = analysisPercent(percents.first(), language)
                Text(
                    buildAnnotatedString {
                        append(tr("O mais abundante é ", "The most abundant is ", "El más abundante es "))
                        append(scientificNameText(top.scientificName))
                        append(tr(", com $share dos indivíduos.", ", with $share of the individuals.", ", con el $share de los individuos."))
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
    if (analysis.families.isNotEmpty()) item { AnalysisFamiliesCard(analysis) }
    item {
        var showAll by rememberSaveable { mutableStateOf(false) }
        val expanded = remember { mutableStateMapOf<String, Boolean>() }
        val classes = ranked.groupingBy { analysis.constancy(it) }.eachCount()
        val shown = if (showAll) ranked else ranked.take(ANALYSIS_TAXA_PREVIEW)
        AnalysisCard("Táxons", ui("Toque num táxon para ver os detalhes.")) {
            // So as classes que aparecem, no singular ou no plural: "Constância: 2 constantes · 5 acidentais".
            val parts = TaxonAnalysis.Constancy.entries.mapNotNull { constancy ->
                val count = classes[constancy] ?: return@mapNotNull null
                val many = count > 1
                "$count " + when (constancy) {
                    TaxonAnalysis.Constancy.CONSTANT -> tr(if (many) "constantes" else "constante", "constant", if (many) "constantes" else "constante")
                    TaxonAnalysis.Constancy.ACCESSORY -> tr(if (many) "acessórias" else "acessória", "accessory", if (many) "accesorias" else "accesoria")
                    TaxonAnalysis.Constancy.ACCIDENTAL -> tr(if (many) "acidentais" else "acidental", "accidental", if (many) "accidentales" else "accidental")
                }
            }
            Text(
                "${ui("Constância")}: ${parts.joinToString(" · ")}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
            )
            shown.forEachIndexed { index, taxon ->
                if (index > 0) AnalysisDivider()
                AnalysisTaxonRow(taxon, analysis, expanded[taxon.scientificName] == true) {
                    expanded[taxon.scientificName] = expanded[taxon.scientificName] != true
                }
            }
            if (ranked.size > ANALYSIS_TAXA_PREVIEW) {
                TextButton(onClick = { showAll = !showAll }) {
                    Text(if (showAll) ui("Mostrar menos") else tr("Mostrar todos (${ranked.size})", "Show all (${ranked.size})", "Mostrar todos (${ranked.size})"))
                }
            }
        }
    }
    item {
        AnalysisNotes(
            ui("Constância (Dajoz): constante em mais de 50% das expedições, acessória de 25% a 50%, acidental abaixo de 25%."),
            ui("Abundância relativa = indivíduos do táxon ÷ total de indivíduos."),
            if (analysis.families.isEmpty()) ui("Nenhum registro tem família. Ela vem junto quando o nome sai do checklist.")
            else ui("A família é a do registro; vem junto quando o nome sai do checklist."),
            when (val withoutFamily = if (analysis.families.isEmpty()) 0 else analysis.taxaWithoutFamily) {
                0 -> ""
                1 -> tr(
                    "1 táxon sem família fica fora da lista por família.",
                    "1 taxon with no family is left out of the family list.",
                    "1 taxón sin familia queda fuera de la lista por familia."
                )
                else -> tr(
                    "$withoutFamily táxons sem família ficam fora da lista por família.",
                    "$withoutFamily taxa with no family are left out of the family list.",
                    "$withoutFamily taxones sin familia quedan fuera de la lista por familia."
                )
            }
        )
    }
}

private fun LazyListScope.analysisDiversity(analysis: TaxonAnalysis) {
    item {
        val language = LocalOikosLanguage.current
        val diversity = analysis.diversity
        AnalysisCard("Índices do projeto") {
            if (diversity == null) {
                val missing = analysis.recordsWithoutCount
                Text(
                    if (missing == 1) tr(
                        "Falta a contagem de indivíduos em 1 registro. Os índices precisam dela em todos.",
                        "1 record has no individual count. The indices need it in every record.",
                        "Falta el conteo de individuos en 1 registro. Los índices lo necesitan en todos."
                    ) else tr(
                        "Falta a contagem de indivíduos em $missing registros. Os índices precisam dela em todos.",
                        "$missing records have no individual count. The indices need it in every record.",
                        "Falta el conteo de individuos en $missing registros. Los índices lo necesitan en todos."
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            } else {
                Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AnalysisStat(analysisNumber(diversity.shannon, 2, language), "Shannon (H')", Modifier.weight(1f), inner = true)
                        AnalysisStat(analysisNumber(diversity.simpson, 2, language), "Simpson (1−D)", Modifier.weight(1f), inner = true)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AnalysisStat(diversity.pielou?.let { analysisNumber(it, 2, language) } ?: "—", "Pielou (J')", Modifier.weight(1f), inner = true)
                        AnalysisStat(analysisNumber(diversity.bergerParker, 2, language), "Berger-Parker (d)", Modifier.weight(1f), inner = true)
                    }
                    Text(
                        tr(
                            "Equivale a ${analysisNumber(diversity.effectiveShannon, 1, language)} espécies igualmente abundantes (exp H').",
                            "Equivalent to ${analysisNumber(diversity.effectiveShannon, 1, language)} equally abundant species (exp H').",
                            "Equivale a ${analysisNumber(diversity.effectiveShannon, 1, language)} especies igualmente abundantes (exp H')."
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
    item {
        val language = LocalOikosLanguage.current
        AnalysisCard("Por expedição") {
            analysis.expeditions.forEachIndexed { index, expedition ->
                if (index > 0) AnalysisDivider()
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(expedition.session.name.ifBlank { expedition.session.eventId }, style = MaterialTheme.typography.bodyLarge)
                        Text(analysisDateOrNone(expedition.session.eventDate, language), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    val diversity = expedition.diversity
                    Column(horizontalAlignment = Alignment.End) {
                        if (diversity == null) {
                            Text("—", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                if (expedition.records == 0) ui("sem registros") else ui("falta contagem"),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                "H' ${analysisNumber(diversity.shannon, 2, language)} · 1−D ${analysisNumber(diversity.simpson, 2, language)}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                "J' ${diversity.pielou?.let { analysisNumber(it, 2, language) } ?: "—"} · d ${analysisNumber(diversity.bergerParker, 2, language)}",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
    if (analysis.expeditions.size >= 2) item { AnalysisSimilarityCard(analysis) }
    item {
        AnalysisNotes(
            ui("Índices com logaritmo natural, como no pacote vegan do R."),
            ui("H' sobe com mais táxons e abundâncias parecidas. 1−D: chance de dois indivíduos sorteados serem de táxons diferentes. J': de 0 a 1; perto de 1, abundâncias parecidas. d: fração do táxon mais abundante."),
            ui("Onde falta contagem de indivíduos, o índice fica sem valor (—).")
        )
    }
}

@Composable
private fun AnalysisSimilarityCard(analysis: TaxonAnalysis) {
    val language = LocalOikosLanguage.current
    var index by rememberSaveable { mutableIntStateOf(0) }
    val names = listOf("Jaccard", "Sørensen", "Bray-Curtis")
    val count = analysis.expeditions.size
    val matrix = remember(analysis, index) {
        List(count) { row ->
            List(count) { column ->
                if (column >= row) null else analysis.similarity(row, column).let {
                    when (index) {
                        0 -> it.jaccard
                        1 -> it.sorensen
                        else -> it.brayCurtis
                    }
                }
            }
        }
    }
    AnalysisCard("Similaridade entre expedições", ui("De 0 (nada em comum) a 1 (iguais). Jaccard e Sørensen usam só os táxons; Bray-Curtis, os indivíduos.")) {
        Row(Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            names.forEachIndexed { position, name ->
                FilterChip(
                    selected = index == position,
                    onClick = { index = position },
                    label = { Text(name) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                        selectedLabelColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }
        SimilarityHeatmap(
            values = matrix,
            format = { analysisNumber(it, 2, language) },
            description = tr(
                "Similaridade de ${names[index]} entre $count expedições.",
                "${names[index]} similarity between $count expeditions.",
                "Similitud de ${names[index]} entre $count salidas de campo."
            ),
            modifier = Modifier.padding(top = 10.dp)
        )
        val best = (1 until count).flatMap { row -> (0 until row).map { column -> Triple(row, column, matrix[row][column]) } }
            .filter { it.third != null }
            .maxByOrNull { it.third!! }
        if (best != null) {
            Text(
                tr(
                    "Mais parecidas: ${best.second + 1} e ${best.first + 1} (${analysisNumber(best.third!!, 2, language)}).",
                    "Most similar: ${best.second + 1} and ${best.first + 1} (${analysisNumber(best.third!!, 2, language)}).",
                    "Más parecidas: ${best.second + 1} y ${best.first + 1} (${analysisNumber(best.third!!, 2, language)})."
                ),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
        } else if (index == 2) {
            Text(
                ui("Bray-Curtis precisa da contagem de indivíduos nas duas expedições."),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            analysis.expeditions.forEachIndexed { position, expedition ->
                Text(
                    "${position + 1}  ${expedition.session.name.ifBlank { expedition.session.eventId }} · ${analysisDateOrNone(expedition.session.eventDate, language)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Composicao por familia, dentro da aba Taxons: so aparece quando algum taxon tem familia. */
@Composable
private fun AnalysisFamiliesCard(analysis: TaxonAnalysis) {
    val most = analysis.families.maxOf { it.taxa }.coerceAtLeast(1)
    AnalysisCard("Táxons por família") {
        analysis.families.forEachIndexed { index, family ->
            if (index > 0) AnalysisDivider()
            Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(family.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    Text(
                        tr(
                            "${family.taxa} ${if (family.taxa == 1) "táxon" else "táxons"} · ${family.individuals} ind.",
                            "${family.taxa} ${if (family.taxa == 1) "taxon" else "taxa"} · ${family.individuals} ind.",
                            "${family.taxa} ${if (family.taxa == 1) "taxón" else "taxones"} · ${family.individuals} ind."
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                AnalysisBar(family.taxa.toFloat() / most)
            }
        }
    }
}

private fun LazyListScope.analysisTime(analysis: TaxonAnalysis) {
    item {
        val language = LocalOikosLanguage.current
        val withData = analysis.months.filter { it.records > 0 || it.expeditions > 0 }
        AnalysisCard("Por mês", ui("Registros por mês, somando os anos.")) {
            if (analysis.months.none { it.records > 0 }) {
                Text(ui("Nenhum registro com data."), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
            } else {
                AnalysisBarChart(
                    values = analysis.months.map { it.records },
                    labels = analysisMonthNames(language, short = true),
                    description = tr("Registros por mês.", "Records per month.", "Registros por mes."),
                    modifier = Modifier.padding(top = 8.dp)
                )
                val names = analysisMonthNames(language, short = false)
                Column(Modifier.padding(top = 4.dp)) {
                    withData.forEachIndexed { index, month ->
                        if (index > 0) AnalysisDivider()
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(names[month.value - 1], style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            Text(
                                tr(
                                    "${month.expeditions} exp. · ${month.records} reg. · ${month.richness} ${if (month.richness == 1) "táxon" else "táxons"}",
                                    "${month.expeditions} exp. · ${month.records} rec. · ${month.richness} ${if (month.richness == 1) "taxon" else "taxa"}",
                                    "${month.expeditions} sal. · ${month.records} reg. · ${month.richness} ${if (month.richness == 1) "taxón" else "taxones"}"
                                ),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
    item {
        AnalysisCard("Horário de atividade", ui("Registros por hora do dia.")) {
            val peak = analysis.hours.maxOf { it.records }
            if (peak == 0) {
                Text(ui("Nenhum registro com hora."), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
            } else {
                AnalysisBarChart(
                    values = analysis.hours.map { it.records },
                    labels = analysis.hours.map { "${it.value}h" },
                    description = tr("Registros por hora do dia.", "Records per hour of the day.", "Registros por hora del día."),
                    modifier = Modifier.padding(top = 8.dp)
                )
                // "Mais registros às 19h (5)." ou, com empate, "às 8h, 9h e 19h (2 cada)."
                val busiest = analysis.hours.filter { it.records == peak }.map { "${it.value}h" }
                val hours = if (busiest.size == 1) busiest.first() else busiest.dropLast(1).joinToString(", ") + tr(" e ", " and ", " y ") + busiest.last()
                val each = if (busiest.size == 1) "$peak" else tr("$peak cada", "$peak each", "$peak cada una")
                Text(
                    tr("Mais registros às $hours ($each).", "Most records at $hours ($each).", "Más registros a las $hours ($each)."),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
    item {
        AnalysisNotes(
            ui("Mês pela data do registro ou, sem ela, da expedição. Compare com o número de expedições do mês."),
            ui("Hora pelo campo Hora do registro: 19h reúne de 19:00 a 19:59."),
            analysisLeftOut(
                analysis.recordsWithoutDate to Triple("sem data", "with no date", "sin fecha"),
                analysis.recordsWithoutTime to Triple("sem hora", "with no time", "sin hora")
            )
        )
    }
}

/**
 * "Fora: 2 registros de ausência e 1 sem nome científico." Só as partes que
 * existem, no singular ou no plural; vazio se nada ficou de fora.
 */
@Composable
private fun analysisLeftOut(vararg parts: Pair<Int, Triple<String, String, String>>): String {
    val present = parts.filter { it.first > 0 }
    if (present.isEmpty()) return ""
    val pieces = present.mapIndexed { index, (count, what) ->
        // O substantivo so na primeira parte: "2 registros sem data e 1 sem hora".
        val noun = when {
            index > 0 -> ""
            count == 1 -> tr("registro ", "record ", "registro ")
            else -> tr("registros ", "records ", "registros ")
        }
        "$count $noun${tr(what.first, what.second, what.third)}"
    }
    return tr("Fora: ", "Left out: ", "Fuera: ") + pieces.joinToString(tr(" e ", " and ", " y ")) + "."
}

@Composable
private fun AnalysisStat(value: String, label: String, modifier: Modifier, inner: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    // Dentro de outro cartao, um tom acima para nao sumir no fundo.
    val container = if (inner) colors.onSurface.copy(alpha = 0.06f) else CardDefaults.cardColors().containerColor
    Card(modifier, colors = CardDefaults.cardColors(containerColor = container)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall, color = colors.primary)
            Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
    }
}

/** Uma linha da lista de taxons; tocada, abre constancia, altitude, sexo, fase de vida e expedicoes. */
@Composable
private fun AnalysisTaxonRow(taxon: TaxonAnalysis.Taxon, analysis: TaxonAnalysis, expanded: Boolean, onToggle: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val language = LocalOikosLanguage.current
    val share = analysis.relativeAbundancePercent(taxon)
    val expeditions = analysis.expeditions.size
    val constancy = analysisConstancyLabel(analysis.constancy(taxon))
    Column(Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(scientificNameText(taxon.scientificName), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text("${taxon.individuals} ind. · ${analysisPercent(share, language)}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                listOf(taxon.family, taxon.vernacularName).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, modifier = Modifier.weight(1f)
            )
            Text(
                tr(
                    "${taxon.expeditionsWithTaxon} de $expeditions exp. · ${constancy.lowercase()}",
                    "${taxon.expeditionsWithTaxon} of $expeditions exp. · ${constancy.lowercase()}",
                    "${taxon.expeditionsWithTaxon} de $expeditions sal. · ${constancy.lowercase()}"
                ),
                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant
            )
        }
        AnalysisBar((share / 100.0).toFloat(), Modifier.padding(top = 6.dp))
        AnimatedVisibility(visible = expanded) {
            Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AnalysisDetail(ui("Constância"), "$constancy (${analysisPercent(analysis.frequencyPercent(taxon), language)} ${tr("das expedições", "of the expeditions", "de las salidas")})")
                val lowest = taxon.minimumAltitude
                val highest = taxon.maximumAltitude
                if (lowest != null && highest != null) {
                    AnalysisDetail(
                        ui("Altitude"),
                        if (Math.round(lowest) == Math.round(highest)) "${Math.round(lowest)} m" else "${Math.round(lowest)}–${Math.round(highest)} m"
                    )
                }
                if (taxon.sexes.isNotEmpty()) AnalysisDetail(ui("Sexo"), taxon.sexes.map { (value, count) -> "${dwcValueText(value)} $count" }.joinToString(" · "))
                if (taxon.lifeStages.isNotEmpty()) AnalysisDetail(ui("Fase de vida"), taxon.lifeStages.map { (value, count) -> "${dwcValueText(value)} $count" }.joinToString(" · "))
                AnalysisDetail(
                    ui("Expedições"),
                    analysis.expeditions.filterIndexed { index, _ -> taxon.recordsByExpedition[index] > 0 }
                        .joinToString(", ") { it.session.name.ifBlank { it.session.eventId } }
                )
            }
        }
    }
}

@Composable
private fun AnalysisDetail(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(96.dp))
        Text(value, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
    }
}

/** Uma expedicao: taxons, individuos e quantos taxons apareceram nela pela primeira vez. */
@Composable
private fun AnalysisExpeditionRow(expedition: TaxonAnalysis.Expedition, language: AppLanguage) {
    val colors = MaterialTheme.colorScheme
    val richness = expedition.richness
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f)) {
            Text(expedition.session.name.ifBlank { expedition.session.eventId }, style = MaterialTheme.typography.bodyLarge)
            Text(analysisDateOrNone(expedition.session.eventDate, language), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                tr(
                    "$richness ${if (richness == 1) "táxon" else "táxons"} · ${expedition.individuals} ind.",
                    "$richness ${if (richness == 1) "taxon" else "taxa"} · ${expedition.individuals} ind.",
                    "$richness ${if (richness == 1) "taxón" else "taxones"} · ${expedition.individuals} ind."
                ),
                style = MaterialTheme.typography.bodyMedium
            )
            val newTaxa = expedition.newTaxa
            Surface(shape = RoundedCornerShape(10.dp), color = if (newTaxa > 0) colors.primary else colors.onSurface.copy(alpha = 0.12f)) {
                Text(
                    if (newTaxa == 0) ui("nenhum novo") else tr(
                        "+$newTaxa ${if (newTaxa == 1) "novo" else "novos"}",
                        "+$newTaxa new",
                        "+$newTaxa ${if (newTaxa == 1) "nuevo" else "nuevos"}"
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (newTaxa > 0) colors.onPrimary else colors.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun analysisConstancyLabel(constancy: TaxonAnalysis.Constancy): String = when (constancy) {
    TaxonAnalysis.Constancy.CONSTANT -> ui("Constante")
    TaxonAnalysis.Constancy.ACCESSORY -> ui("Acessória")
    TaxonAnalysis.Constancy.ACCIDENTAL -> ui("Acidental")
}

/** Genero e epiteto em italico; autor, "sp." e "cf." sem italico. */
@Composable
private fun scientificNameText(name: String): AnnotatedString = buildAnnotatedString {
    ScientificName.runs(name).forEach { run ->
        if (run.italic) withStyle(SpanStyle(fontStyle = FontStyle.Italic, fontWeight = FontWeight.Medium)) { append(run.text) } else append(run.text)
    }
}

/** "2026-10-13" como se le: 13/10/2026; em ingles fica como esta. Intervalo usa a primeira data. */
private fun analysisDate(date: String, language: AppLanguage): String {
    val parsed = runCatching { LocalDate.parse(date.take(10)) }.getOrNull() ?: return date
    return if (language == AppLanguage.EN) parsed.toString() else parsed.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
}

@Composable
private fun analysisDateOrNone(date: String, language: AppLanguage): String =
    if (date.isBlank()) ui("Sem data") else analysisDate(date, language)

/** Rotulo do ponto no grafico, em duas linhas; sem data, o numero da expedicao. */
private fun analysisChartLabel(date: String, index: Int, language: AppLanguage): String {
    val parsed = runCatching { LocalDate.parse(date.take(10)) }.getOrNull() ?: return "${index + 1}"
    val dayMonth = if (language == AppLanguage.EN) DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH) else DateTimeFormatter.ofPattern("dd/MM")
    return "${parsed.format(dayMonth)}\n${parsed.year}"
}

private fun analysisMonthNames(language: AppLanguage, short: Boolean): List<String> = when (language) {
    AppLanguage.PT -> if (short) listOf("jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez")
    else listOf("Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho", "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro")
    AppLanguage.EN -> if (short) listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    else listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
    AppLanguage.ES -> if (short) listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")
    else listOf("Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio", "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre")
}

/** 0,53 (0.53 em ingles), com [decimals] casas. */
private fun analysisNumber(value: Double, decimals: Int, language: AppLanguage): String {
    val text = String.format(Locale.US, "%.${decimals}f", value)
    return if (language == AppLanguage.EN) text else text.replace('.', ',')
}

/** 50% ou 16,7% (16.7% em ingles). */
private fun analysisPercent(value: Double, language: AppLanguage): String {
    val rounded = Math.round(value * 10) / 10.0
    val text = if (rounded % 1.0 == 0.0) rounded.toLong().toString() else String.format(Locale.US, "%.1f", rounded)
    return (if (language == AppLanguage.EN) text else text.replace('.', ',')) + "%"
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
                    name = name.trim().ifBlank { inProjectLanguage(project?.language ?: "pt", "Expedição $date", "Expedition $date", "Salida de campo $date") },
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
            // O protocolo vai para o dado (dwc:samplingProtocol): no idioma do
            // projeto. Antes saia em portugues tambem nos projetos em ingles.
            val dataLanguage = project.dataLanguage()
            val preset = SAMPLING_PRESETS.firstOrNull { it.protocol.matches(protocol) }
            ChipRow(
                "Protocolo (modelos)",
                SAMPLING_PRESETS.map { it.protocol.of(dataLanguage) to it.label },
                preset?.protocol?.of(dataLanguage) ?: protocol
            ) { chosen ->
                SAMPLING_PRESETS.firstOrNull { it.protocol.of(dataLanguage) == chosen }?.let { picked ->
                    protocol = picked.protocol.of(dataLanguage)
                    if (sampleSizeUnit.isBlank() && picked.unit.isNotBlank()) sampleSizeUnit = picked.unit
                }
            }
            TextInput("Método de amostragem", protocol) { protocol = it }
            TextInput(
                "Esforço amostral",
                effort,
                help = preset?.effortExample?.let { example ->
                    "${ui("Exemplo para este protocolo")}: ${example.of(LocalOikosLanguage.current)}"
                }
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

/**
 * Protocolo de amostragem comum, com a unidade amostral do Darwin Core. O
 * protocolo vai para o dado, no idioma do projeto; o exemplo de esforco so
 * aparece na tela, no idioma da tela.
 */
private data class SamplingPreset(val label: String, val protocol: Trilingual, val unit: String, val effortExample: Trilingual?)

/** O mesmo texto em portugues, ingles e espanhol. */
private data class Trilingual(val pt: String, val en: String, val es: String) {
    fun of(language: AppLanguage): String = when (language) {
        AppLanguage.PT -> pt
        AppLanguage.EN -> en
        AppLanguage.ES -> es
    }

    fun matches(text: String): Boolean = text == pt || text == en || text == es
}

/**
 * Nomes padronizados evitam que a mesma tecnica apareca escrita de cinco jeitos
 * na planilha ("busca ativa", "BA", "procura visual"...), o que impede somar o
 * esforco entre expedicoes.
 */
private val SAMPLING_PRESETS = listOf(
    SamplingPreset(
        "Busca ativa",
        Trilingual("Busca ativa visual e auditiva", "Visual and acoustic active search", "Búsqueda activa visual y auditiva"),
        "hour",
        Trilingual("2 pessoas × 3 h", "2 people × 3 h", "2 personas × 3 h")
    ),
    SamplingPreset(
        "Ponto de escuta",
        Trilingual("Ponto de escuta (contagem por pontos)", "Point count", "Conteo por puntos"),
        "minute",
        Trilingual("10 min por ponto, 8 pontos", "10 min per point, 8 points", "10 min por punto, 8 puntos")
    ),
    SamplingPreset(
        "Transecto",
        Trilingual("Transecto linear", "Line transect", "Transecto lineal"),
        "metre",
        Trilingual("500 m percorridos em 1 h", "500 m walked in 1 h", "500 m recorridos en 1 h")
    ),
    SamplingPreset(
        "Parcela",
        Trilingual("Parcela", "Plot", "Parcela"),
        "squareMetre",
        Trilingual("parcela de 10 × 10 m", "10 × 10 m plot", "parcela de 10 × 10 m")
    ),
    SamplingPreset(
        "Pitfall",
        Trilingual(
            "Armadilhas de interceptação e queda (pitfall)",
            "Pitfall traps with drift fences",
            "Trampas de intercepción y caída (pitfall)"
        ),
        "trapNight",
        Trilingual("20 baldes × 5 noites", "20 buckets × 5 nights", "20 baldes × 5 noches")
    ),
    SamplingPreset(
        "Gravador automático",
        Trilingual(
            "Monitoramento acústico passivo (gravador automático)",
            "Passive acoustic monitoring (autonomous recorder)",
            "Monitoreo acústico pasivo (grabadora autónoma)"
        ),
        "minute",
        Trilingual("1 min a cada 15 min, 5 noites", "1 min every 15 min, 5 nights", "1 min cada 15 min, 5 noches")
    ),
    SamplingPreset(
        "Coleta botânica",
        Trilingual("Coleta de material botânico", "Botanical specimen collection", "Colecta de material botánico"),
        "",
        null
    )
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
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, ui("Editar")) }
                IconButton(onClick = onMedia) { Icon(Icons.Default.Photo, ui("Mídias")) }
                IconButton(onClick = onExport) { Icon(Icons.Default.Share, ui("Exportar expedição")) }
                IconButton(onClick = { deleteSession = true }) { Icon(Icons.Default.Delete, ui("Apagar expedição")) }
            }
        },
        floatingActionButton = { FloatingActionButton(onClick = onNewOccurrence) { Icon(Icons.Default.Add, ui("Nova ocorrência")) } }
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
                // Icone de cada registro no mapa, pela lista instalada (so leitura).
                val mapIcons by androidx.compose.runtime.produceState(emptyMap<Long, MapIconGroup>(), occurrences) {
                    value = viewModel.mapIconGroups(occurrences)
                }
                TrackPanel(
                    track = displayTrack,
                    points = trackPoints,
                    occurrences = occurrences,
                    iconGroups = mapIcons,
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
                                Text(occurrence.scientificName.ifBlank { ui("Ocorrência sem nome científico") }, style = MaterialTheme.typography.titleMedium)
                                Text(listOf(occurrence.vernacularName, occurrence.recordedBy, dwcValueText(occurrence.occurrenceStatus)).filter { it.isNotBlank() }.joinToString(" - "))
                            }
                            IconButton(onClick = { occurrenceToDelete = occurrence }) {
                                Icon(Icons.Default.Delete, ui("Apagar ocorrência"))
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                            StatusBadge(if (complete) "Completo" else "Incompleto", complete)
                            StatusBadge(if (hasGps) "GPS ok" else "Sem GPS", hasGps)
                            StatusBadge("$photos ${ui(if (photos == 1) "foto" else "fotos")}", photos > 0)
                            StatusBadge("$audios ${ui(if (audios == 1) "áudio" else "áudios")}", audios > 0)
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
    profile: TrackProfile = TrackProfile.PRECISE,
    iconGroups: Map<Long, MapIconGroup> = emptyMap()
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
            iconGroups = iconGroups,
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
                iconGroups = iconGroups,
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
                "${points.size} ${ui(if (points.size == 1) "ponto da trilha" else "pontos da trilha")} - " +
                    "$occurrencePoints ${ui(if (occurrencePoints == 1) "ocorrência com GPS" else "ocorrências com GPS")} - ${humanDistance(distance)}.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                if (lastPoint == null) {
                    ui("Ao iniciar, o app grava o ponto de saída e atualiza a linha automaticamente a cada poucos segundos.")
                } else {
                    val accuracy = lastPoint.accuracy?.let { "±${it.toInt()} m" }
                    val seconds = profile.intervalMillis / 1000
                    val meters = formatPlain(profile.minDistanceMeters.toDouble())
                    tr(
                        "Último ponto: ${formatClock(lastPoint.timestamp)} - precisão ${accuracy ?: "não informada"}. Atualização automática aproximada: $seconds s ou ${meters.replace('.', ',')} m.",
                        "Last point: ${formatClock(lastPoint.timestamp)} - accuracy ${accuracy ?: "not reported"}. Automatic update about every $seconds s or $meters m.",
                        "Último punto: ${formatClock(lastPoint.timestamp)} - precisión ${accuracy ?: "no informada"}. Actualización automática aproximada: cada $seconds s o ${meters.replace('.', ',')} m."
                    )
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
    iconGroups: Map<Long, MapIconGroup> = emptyMap(),
    modifier: Modifier = Modifier
) {
    val border = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    val context = LocalContext.current
    val offlineMap = LocalOfflineMap.current
    val occurrencePoints = occurrences.filter { it.latitude != null && it.longitude != null }
    val userArrow = remember(context) { oikosUserMarkerDrawable(context) }
    val youAreHere = ui("Você está aqui")
    // Um alfinete por grupo (fauna, flora, fungos), desenhado uma vez so.
    val recordPins = remember(context) { mutableMapOf<MapIconGroup, android.graphics.drawable.Drawable>() }
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
    // O ponteiro do usuario gira sem recompor a tela: so a rotacao do marcador muda.
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
                val occurrencesKey = occurrenceGeo.map { (point, occurrence) -> "${occurrence.id}:${point.latitude}:${point.longitude}:${occurrence.scientificName}:${iconGroups[occurrence.id] ?: provisionalMapGroup(occurrence.formType)}" }
                if (layers.occurrencesKey != occurrencesKey) {
                    layers.occurrencesKey = occurrencesKey
                    layers.occurrences = occurrenceGeo.map { (point, occurrence) ->
                        Marker(mapView).apply {
                            position = point
                            title = occurrence.scientificName.ifBlank { occurrence.occurrenceId }
                            icon = (iconGroups[occurrence.id] ?: provisionalMapGroup(occurrence.formType)).let { group ->
                                recordPins.getOrPut(group) { oikosPinMarkerDrawable(context, group) }
                            }
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
                        title = youAreHere
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
                // Ao abrir (e quando chegam dados novos) o mapa enquadra a trilha e
                // os registros inteiros; antes abria em zoom 17 no ultimo ponto e
                // cortava o resto. O botao de centralizar continua indo ao ponto.
                val askedRecenter = mapView.tag != null && mapView.tag != recenterToken
                if (askedRecenter) {
                    mapView.mapOrientation = 0f
                    mapView.controller.setZoom(17.0)
                    mapView.controller.setCenter(center)
                    mapView.tag = recenterToken
                    layers.centerLevel = centerLevel
                } else if (mapView.tag == null || centerLevel > layers.centerLevel) {
                    mapView.mapOrientation = 0f
                    mapView.frameData(trackGeo + occurrenceGeo.map { it.first }, center)
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
        // Credito exigido pela politica de tiles do OpenStreetMap: visivel no
        // mapa e com link para a pagina de direitos autorais.
        val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
        Surface(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
            shape = MaterialTheme.shapes.small
        ) {
            Text(
                offlineMap?.let { map -> map.attribution.ifBlank { map.name } } ?: "© OpenStreetMap contributors",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .then(
                        if (offlineMap == null) {
                            Modifier.clickable { runCatching { uriHandler.openUri(OSM_COPYRIGHT_URL) } }
                        } else {
                            Modifier
                        }
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private const val OSM_COPYRIGHT_URL = "https://www.openstreetmap.org/copyright"

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
    onDismiss: () -> Unit,
    iconGroups: Map<Long, MapIconGroup> = emptyMap()
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
                        Text(ui("O ponteiro laranja mostra onde você está e para onde aponta. Gire o mapa com dois dedos; o N indica o norte."), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = onDismiss) { Text(ui("Fechar")) }
                }
                TrackMapPreview(
                    points = points,
                    occurrences = occurrences,
                    iconGroups = iconGroups,
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

private val MAP_ORANGE = android.graphics.Color.rgb(255, 106, 0)
private val MAP_DARK = android.graphics.Color.rgb(23, 24, 28)
private val MAP_CREAM = android.graphics.Color.rgb(255, 230, 207)

private fun mapFill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    this.color = color
    style = Paint.Style.FILL
}

/**
 * Posicao do usuario: disco escuro com anel laranja, centro creme e ponteiro
 * de rumo no alto. Gira com a bussola em torno do centro (ancora no meio).
 * Desenho em unidades de 48 x 48, escalado para 40 dp.
 */
private fun oikosUserMarkerDrawable(context: Context): BitmapDrawable {
    val size = (40 * context.resources.displayMetrics.density).toInt()
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    canvas.scale(size / 48f, size / 48f)
    canvas.drawPath(Path().apply {
        moveTo(24f, 2f)
        lineTo(29f, 11f)
        lineTo(19f, 11f)
        close()
    }, mapFill(MAP_ORANGE))
    canvas.drawCircle(24f, 24f, 11f, mapFill(MAP_DARK))
    canvas.drawCircle(24f, 24f, 11f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = MAP_ORANGE
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    })
    canvas.drawCircle(24f, 24f, 4f, mapFill(MAP_CREAM))
    return BitmapDrawable(context.resources, bitmap)
}

/**
 * Alfinete dos registros: gota escura com contorno branco, ponta laranja e o
 * icone do grupo (anuro, ave, serpente, camera trap...; ver MapIcons).
 * Os icones sao PNG gerados por tools/icones/GerarIcones.java. Desenho em
 * unidades de 40 x 52, escalado para 30 dp de largura.
 */
private fun oikosPinMarkerDrawable(context: Context, group: MapIconGroup): BitmapDrawable {
    val scale = 30 * context.resources.displayMetrics.density / 40f
    val bitmap = Bitmap.createBitmap((40 * scale).toInt(), (52 * scale).toInt(), Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    canvas.scale(scale, scale)
    val drop = Path().apply {
        moveTo(20f, 50f)
        cubicTo(17f, 44f, 5f, 32f, 5f, 20f)
        arcTo(android.graphics.RectF(5f, 5f, 35f, 35f), 180f, 180f, false)
        cubicTo(35f, 32f, 23f, 44f, 20f, 50f)
        close()
    }
    canvas.drawPath(drop, mapFill(MAP_DARK))
    canvas.save()
    canvas.clipPath(drop)
    canvas.drawRect(0f, 35f, 40f, 52f, mapFill(MAP_ORANGE))
    canvas.restore()
    canvas.drawPath(drop, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        strokeJoin = Paint.Join.ROUND
    })
    val iconRes = when (group) {
        MapIconGroup.FROG -> R.drawable.map_icon_frog
        MapIconGroup.FROG_BROMELIAD -> R.drawable.map_icon_frog_bromeliad
        MapIconGroup.REPTILE -> R.drawable.map_icon_reptile
        MapIconGroup.SNAKE -> R.drawable.map_icon_snake
        MapIconGroup.BIRD -> R.drawable.map_icon_bird
        MapIconGroup.MAMMAL -> R.drawable.map_icon_mammal
        MapIconGroup.INSECT -> R.drawable.map_icon_insect
        MapIconGroup.ARACHNID -> R.drawable.map_icon_arachnid
        MapIconGroup.FISH -> R.drawable.map_icon_fish
        MapIconGroup.CAMERA_TRAP -> R.drawable.map_icon_camera_trap
        MapIconGroup.FLORA -> R.drawable.map_icon_flora
        MapIconGroup.FUNGI -> R.drawable.map_icon_fungi
        MapIconGroup.OTHER -> null
    }
    val icon = iconRes?.let { BitmapFactory.decodeResource(context.resources, it) }
    if (icon != null) {
        canvas.drawBitmap(
            icon,
            null,
            android.graphics.RectF(8.5f, 8.5f, 31.5f, 31.5f),
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        )
        icon.recycle()
    } else {
        // Sem grupo conhecido: so um ponto, sem sugerir fauna ou flora.
        canvas.drawCircle(20f, 20f, 4.5f, mapFill(MAP_CREAM))
    }
    return BitmapDrawable(context.resources, bitmap)
}

/** Grupo provisorio enquanto a consulta as listas nao termina. */
private fun provisionalMapGroup(formType: String): MapIconGroup = when (formType) {
    "Botanica" -> MapIconGroup.FLORA
    "Fungos" -> MapIconGroup.FUNGI
    else -> MapIconGroup.OTHER
}

/**
 * Enquadra a trilha e os registros inteiros, com folga para o alfinete, a
 * bussola e o credito. Um ponto so (ou nenhum) fica no centro, em zoom 17.
 */
private fun MapView.frameData(points: List<GeoPoint>, fallback: GeoPoint) {
    val distinct = points.distinctBy { it.latitude to it.longitude }
    if (distinct.size < 2) {
        controller.setZoom(17.0)
        controller.setCenter(distinct.firstOrNull() ?: fallback)
        return
    }
    val dataBox = org.osmdroid.util.BoundingBox.fromGeoPointsSafe(distinct)
    // O alfinete sobe a partir do ponto: folga extra no norte para o de cima
    // nao ficar cortado nem embaixo da bussola, no canto superior.
    val box = org.osmdroid.util.BoundingBox(
        (dataBox.latNorth + dataBox.latitudeSpan * 0.25).coerceAtMost(85.0),
        dataBox.lonEast,
        dataBox.latSouth,
        dataBox.lonWest
    )
    val border = (56 * resources.displayMetrics.density).toInt()
    // Pontos muito proximos pediriam zoom alem do que os tiles tem: no maximo 18.
    val apply = { zoomToBoundingBox(box, false, border, 18.0, null) }
    if (isLayoutOccurred) apply() else addOnFirstLayoutListener { _, _, _, _, _ -> apply() }
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
    val formLanguage = LocalOikosLanguage.current
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
                    viewModel.showMessage(
                        when (formLanguage) {
                            AppLanguage.PT -> "Preencha antes de salvar: "
                            AppLanguage.EN -> "Fill in before saving: "
                            AppLanguage.ES -> "Completa antes de guardar: "
                        } + faltando.joinToString(", ") { field -> fieldLabelIn(field, formLanguage) }
                    )
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
                groups = com.oikos.fieldbook.checklist.NameGroup.parseSelection(project?.nameGroups.orEmpty()),
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
            if (enabled("recordedBy", enabledFields)) TextInput(label("recordedBy", enabledFields, "Quem coletou"), recordedBy, help = if (recordedBy.isBlank() && !session?.recordedBy.isNullOrBlank()) {
                tr(
                    "Em branco, a exportação usa o coletor da expedição: ${session?.recordedBy}.",
                    "If blank, the export uses the expedition collector: ${session?.recordedBy}.",
                    "En blanco, la exportación usa el colector de la salida de campo: ${session?.recordedBy}."
                )
            } else null) { recordedBy = it }
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
                    dynamicValues["georeferenceRemarks"] = fix.remarks(project.dataLanguage())
                }
            }
            // Coordenada digitada a mao invalida a descricao automatica de como
            // ela foi obtida; o protocolo e a observacao saem do registro.
            fun manualCoordinateEdit() {
                if (isAutomaticGpsRemarks(dynamicValues["georeferenceRemarks"])) {
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
                            dynamicValues["georeferenceRemarks"] = singleFixRemarks(it.accuracyMeters, project.dataLanguage())
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

/** Inicios das observacoes de georreferenciamento que o app escreve (pt, en e es). */
private val AUTOMATIC_GPS_REMARKS = listOf(
    "Média de", "Leitura única do GNSS do celular",
    "Mean of", "Single phone GNSS reading",
    "Promedio de", "Lectura única del GNSS del celular"
)

private fun isAutomaticGpsRemarks(text: String): Boolean = AUTOMATIC_GPS_REMARKS.any { text.startsWith(it) }

/** Dado gravado no registro: segue o idioma do projeto, nao o da tela. */
private fun ProjectEntity?.dataLanguage(): AppLanguage = AppLanguage.ofProject(this?.language ?: "pt")

private fun singleFixRemarks(accuracyMeters: Double?, language: AppLanguage): String {
    val accuracy = accuracyMeters?.let { formatPlain(it) + " m" }
    return when (language) {
        AppLanguage.EN -> "Single phone GNSS reading; reported accuracy ${accuracy ?: "not reported"}; uncertainty = 2 × accuracy."
        AppLanguage.ES -> "Lectura única del GNSS del celular; precisión informada ${accuracy ?: "no informada"}; incertidumbre = 2 × precisión."
        AppLanguage.PT -> "Leitura única do GNSS do celular; precisão informada ${accuracy ?: "não informada"}; incerteza = 2 × precisão."
    }
}

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
            synonymNotice = suggestion.taxon.let { taxon ->
                UiLanguage.t(
                    "${taxon.canonicalName} é sinônimo de ${taxon.acceptedName.ifBlank { "outro nome" }} segundo a lista, mas o nome aceito não está nela. Registrado como ${taxon.canonicalName}: confira a identificação.",
                    "${taxon.canonicalName} is a synonym of ${taxon.acceptedName.ifBlank { "another name" }} in the list, but the accepted name is not in it. Recorded as ${taxon.canonicalName}: check the identification.",
                    "${taxon.canonicalName} es sinónimo de ${taxon.acceptedName.ifBlank { "otro nombre" }} según la lista, pero el nombre aceptado no está en ella. Registrado como ${taxon.canonicalName}: revisa la identificación."
                )
            }
        )
    }
    val accepted = found
    val synonym = suggestion.taxon.status == "S" && accepted.id != suggestion.taxon.id
    return ChosenTaxon(
        scientificName = fullName(accepted),
        fields = taxonFields(accepted, "${suggestion.checklistTitle} (v${suggestion.checklistVersion})") +
            ("verbatimIdentification" to if (synonym) fullName(suggestion.taxon) else ""),
        vernacular = viewModel.vernacularFor(accepted),
        synonymNotice = if (synonym) {
            UiLanguage.t(
                "${suggestion.taxon.canonicalName} é sinônimo: registrado como ${accepted.canonicalName}.",
                "${suggestion.taxon.canonicalName} is a synonym: recorded as ${accepted.canonicalName}.",
                "${suggestion.taxon.canonicalName} es sinónimo: registrado como ${accepted.canonicalName}."
            )
        } else null
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
    val exportProject by remember(viewModel, projectId) { viewModel.observeProject(projectId) }.collectAsState(initial = null)
    // Pastas do ZIP no idioma do projeto; o texto, no idioma da tela.
    val paths = com.oikos.fieldbook.export.ExportPaths.of(exportProject?.language ?: "pt")
    Scaffold(modifier = modifier, topBar = { OikosTopBar(if (sessionId == null) "Exportar projeto" else "Exportar expedição", onBack) }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(ui("Exportar dados"), style = MaterialTheme.typography.titleLarge)
            Text(if (sessionId == null) ui("Escopo: Projeto inteiro / planilha mãe consolidada") else ui("Escopo: Expedição atual"))
            Text(ui("Para não perder o vínculo entre fotos, áudios e registros, recomendamos exportar como ZIP completo."))
            HelpCard(
                "ZIP organizado",
                tr(
                    "O ZIP cria ${paths.main}, ${paths.support}, ${paths.gis}, ${paths.dwca} e ${paths.media}. A planilha principal reúne as ocorrências do escopo escolhido com os campos Darwin Core e os campos configurados no projeto.",
                    "The ZIP creates ${paths.main}, ${paths.support}, ${paths.gis}, ${paths.dwca} and ${paths.media}. The main spreadsheet gathers the occurrences in the chosen scope with the Darwin Core fields and the fields configured in the project.",
                    "El ZIP crea ${paths.main}, ${paths.support}, ${paths.gis}, ${paths.dwca} y ${paths.media}. La planilla principal reúne los registros del alcance elegido con los campos Darwin Core y los campos configurados en el proyecto."
                )
            )
            HelpCard(
                "Trilhas GPS",
                tr(
                    "O ZIP completo inclui ${paths.trackCsv} e o GeoJSON inclui a trilha da expedição como LineString. No QGIS, estilize featureType=expeditionTrack em laranja para ver o percurso.",
                    "The full ZIP includes ${paths.trackCsv}, and the GeoJSON includes the expedition track as a LineString. In QGIS, style featureType=expeditionTrack in orange to see the route.",
                    "El ZIP completo incluye ${paths.trackCsv} y el GeoJSON incluye el recorrido de la salida de campo como LineString. En QGIS, aplica a featureType=expeditionTrack un estilo naranja para ver el trayecto."
                )
            )
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
            // Um botao so para a troca entre celulares: direto para o celular ao
            // lado (Bluetooth + Wi-Fi, sem internet) ou como arquivo.
            var nearbySending by remember { mutableStateOf(false) }
            var choosingSend by remember { mutableStateOf(false) }
            val askNearbySend = rememberNearbyPermissions(viewModel) { nearbySending = true }
            if (nearbySending) NearbySendDialog(viewModel, projectId, sessionId, onClose = { nearbySending = false })
            if (choosingSend) {
                PhoneTransferChooser(
                    title = ui("Enviar para outro celular"),
                    nearbyTitle = ui("Para o celular ao lado"),
                    fileTitle = ui("Como arquivo"),
                    fileDetails = ui("Para mandar por WhatsApp, e-mail, Drive ou cabo."),
                    onNearby = {
                        choosingSend = false
                        askNearbySend()
                    },
                    onFile = {
                        choosingSend = false
                        viewModel.exportProjectPackage(projectId, sessionId, onShare)
                    },
                    onDismiss = { choosingSend = false }
                )
            }
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = !exporting,
                onClick = { choosingSend = true }
            ) {
                Icon(Icons.Default.PhoneAndroid, null)
                Spacer(Modifier.width(8.dp))
                Text(ui("Enviar para outro celular"))
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
    // Seletor de arquivos, nao o de fotos: no Android 14+ o GetContent abre o
    // seletor de fotos, que entrega a imagem com o GPS do EXIF zerado. Pelo
    // seletor de arquivos o GPS chega inteiro, sem permissao extra.
    val importPhoto = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
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
            OutlinedButton(onClick = { importPhoto.launch(arrayOf("image/*")) }) { Icon(Icons.Default.Photo, null); Spacer(Modifier.width(6.dp)); Text(ui("Anexar foto")) }
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
    // Tema de sol (claro): fundo branco e som em preto; tema escuro: escala Oikos.
    val palette = if (MaterialTheme.colorScheme.background.luminance() > 0.5f) com.oikos.fieldbook.audio.SpectrogramPalette.GRAY_INVERTED
    else com.oikos.fieldbook.audio.SpectrogramPalette.OIKOS
    // Uma coluna por pixel da imagem: com 900 colunas espremidas em ~600 px, a
    // reducao da tela diluia as notas curtas. O calculo ja guarda o maximo de
    // cada trecho, entao menos colunas nao perdem nota.
    val columnsTarget = with(androidx.compose.ui.platform.LocalDensity.current) {
        (LocalConfiguration.current.screenWidthDp.dp.toPx() * 0.6f).toInt().coerceIn(300, 1200)
    }
    var image by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var result by remember { mutableStateOf<com.oikos.fieldbook.audio.SpectrogramResult?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(media.filePath, maxKhz, palette, columnsTarget) {
        image = null
        failed = false
        val computed = withContext(Dispatchers.Default) {
            runCatching { DecodedSpectrogram.compute(File(media.filePath), maxColumns = columnsTarget, maxFrequencyHz = maxKhz * 1000.0) }.getOrNull()
        }
        result = computed
        if (computed == null) {
            failed = true
        } else {
            image = withContext(Dispatchers.Default) {
                android.graphics.Bitmap.createBitmap(computed.pixels(palette), computed.columns, computed.bins, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
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
                                            // No fundo branco (tema de sol) o cursor branco sumiria.
                                            .background(if (palette == com.oikos.fieldbook.audio.SpectrogramPalette.GRAY_INVERTED) Color(0xFFFF6A00) else Color.White)
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
                // Numero fixo da midia (o mesmo nas planilhas de toda a equipe) e o arquivo.
                Text(listOf(media.mediaCode, media.fileName).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
        val seconds = (millis + 500) / 1000
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
    // Data da captura: a pessoa informa quando o arquivo nao trazia (gravador sem
    // data) ou corrige um relogio errado. So muda o salvo se ela mexer.
    val captured = remember(media.id, media.createdAt, media.captureTimeKnown) {
        if (media.captureTimeKnown) Instant.ofEpochMilli(media.createdAt).atZone(ZoneId.systemDefault()) else null
    }
    val initialDate = captured?.toLocalDate()?.toString().orEmpty()
    val initialTime = captured?.let { String.format(Locale.US, "%02d:%02d", it.hour, it.minute) }.orEmpty()
    var captureDate by remember(media.id, media.filePath) { mutableStateOf(initialDate) }
    var captureTime by remember(media.id, media.filePath) { mutableStateOf(initialTime) }
    val needBoth = ui("Informe a data e a hora da captura.")
    val noFuture = ui("A data da captura não pode estar no futuro.")
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
                if (!media.captureTimeKnown) {
                    Text(ui("O arquivo não trazia a data. Informe quando a foto ou o áudio foi feito."), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DateInput("Data da captura", captureDate) { captureDate = it }
                TimeInput("Hora da captura", captureTime) { captureTime = it }
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
                val dateChanged = captureDate != initialDate || captureTime != initialTime
                if (dateChanged && (captureDate.isBlank() || captureTime.isBlank())) {
                    problem = needBoth
                    return@TextButton
                }
                val newCapture = if (dateChanged) {
                    runCatching {
                        LocalDate.parse(captureDate).atTime(LocalTime.parse(captureTime)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    }.getOrNull()
                } else null
                if (newCapture != null && newCapture > System.currentTimeMillis() + 60_000L) {
                    problem = noFuture
                    return@TextButton
                }
                onSave(
                    media.copy(
                        createdAt = newCapture ?: media.createdAt,
                        captureTimeKnown = media.captureTimeKnown || newCapture != null,
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
                            if (installedChecklists.isEmpty()) ui("Nenhuma instalada. Baixe uma para completar nomes científicos em campo.")
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
                        Text(ui("Alto contraste para uso ao sol."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = darkTheme, onCheckedChange = onThemeChange)
                }
            }
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), elevation = CardDefaults.cardElevation(2.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(ui("Idioma do aplicativo"), style = MaterialTheme.typography.titleMedium)
                    Text(ui("Automático segue o idioma do celular."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ChipRow(
                        "Idioma",
                        listOf("auto" to "Automático", "pt" to "Português", "en" to "English", "es" to "Español"),
                        languageMode,
                        onLanguageModeChange
                    )
                }
            }
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), elevation = CardDefaults.cardElevation(2.dp)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(ui("Modo simples / coleta rápida"), style = MaterialTheme.typography.titleMedium)
                        Text(ui("Mostra menos campos no formulário."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = simpleMode, onCheckedChange = onSimpleModeChange)
                }
            }
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), elevation = CardDefaults.cardElevation(2.dp)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(ui("Sugerir endereço pelo Google"), style = MaterialTheme.typography.titleMedium)
                        Text(
                            ui("Só para coletas fora do Brasil. Envia a coordenada ao Google."),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = reverseGeocoding, onCheckedChange = {
                        reverseGeocoding = it
                        viewModel.reverseGeocodingEnabled = it
                    })
                }
            }
            NetworkPrivacyCard()
            FeedbackCard()
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), elevation = CardDefaults.cardElevation(2.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Os antigos controles de "area" e "detalhe" do cache eram
                    // gravados e nunca lidos: nao faziam nada. O cache real e o
                    // dos blocos de mapa ja vistos, descrito aqui como e.
                    Text(ui("Mapa e uso offline"), style = MaterialTheme.typography.titleMedium)
                    Text(
                        ui("O OpenStreetMap precisa de internet. Para campo sem sinal, importe um mapa offline (.mbtiles)."),
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
                    "As listas oficiais do Brasil já vêm no app e funcionam sem internet: o nome é sugerido enquanto você digita, sinônimos viram o nome aceito e a distribuição por estado aparece junto. Com internet, dá para atualizar."
                )
            }
            items(ChecklistSources.all, key = { it.key }) { source ->
                ChecklistSourceCard(
                    source = source,
                    installed = installed.firstOrNull { it.sourceKey == source.key },
                    progress = progress[source.key],
                    latestVersion = latest[source.key],
                    onDownload = { viewModel.downloadChecklist(source) },
                    onInstallEmbedded = { viewModel.installEmbeddedChecklist(source) },
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
    onInstallEmbedded: () -> Unit,
    onRemove: (ChecklistEntity) -> Unit
) {
    val embedded = source.embeddedAsset != null
    val updateAvailable = installed != null && latestVersion?.isNewerThan(installed) == true
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(source.title, style = MaterialTheme.typography.titleMedium)
            Text(ui(source.scope), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(ui(source.description), color = MaterialTheme.colorScheme.onSurfaceVariant)
            when {
                installed == null && embedded -> Text("${ui("Não instalada")} · ${ui("vem no app, sem internet")} · ${ui("ocupa")} ~${source.approxInstalledMb} MB ${ui("no aparelho")}", style = MaterialTheme.typography.labelMedium)
                installed == null -> Text("${ui("Não instalada")} · ${ui("cerca de")} ${source.approxDownloadMb} MB ${ui("para baixar")} · ${ui("ocupa")} ~${source.approxInstalledMb} MB ${ui("no aparelho")}", style = MaterialTheme.typography.labelMedium)
                updateAvailable -> Text("${installedSummary(installed)}\n${ui("Versão nova disponível")}: ${latestVersion?.label}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                else -> Text(installedSummary(installed), style = MaterialTheme.typography.labelMedium)
            }
            if (installed != null && installed.sourceUrl == com.oikos.fieldbook.checklist.ChecklistService.EMBEDDED_URL) {
                Text(
                    ui("Cópia que vem no app. Com internet, toque em Atualizar pela internet para ter a versão mais recente."),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
                        installed == null && embedded -> {
                            Button(onClick = onInstallEmbedded) { Text(ui("Instalar")) }
                            OutlinedButton(onClick = onDownload) { Text(ui("Baixar")) }
                        }
                        installed == null -> Button(onClick = onDownload) { Text(ui("Baixar")) }
                        updateAvailable -> Button(onClick = onDownload) { Text(ui("Atualizar")) }
                        else -> OutlinedButton(onClick = onDownload) { Text(ui("Atualizar pela internet")) }
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
        "${String.format(LocalOikosLanguage.current.locale, "%,d", checklist.taxonCount)} ${ui("nomes")}",
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
                    fieldNotes = inProjectLanguage(currentProject.language, "Contagem rápida", "Quick count", "Conteo rápido"),
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
            form["georemarks"] = fix.remarks(project.dataLanguage())
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
                                    form["georemarks"] = singleFixRemarks(it.accuracyMeters, project.dataLanguage())
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
                groups = com.oikos.fieldbook.checklist.NameGroup.parseSelection(project?.nameGroups.orEmpty()),
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
    /** Grupos escolhidos no projeto; vazio = todos. */
    groups: Set<String> = emptySet(),
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
            suggestions = if (value.trim().length >= 2) viewModel.searchNames(value, groups) else emptyList()
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
                                    // Italico so de genero para baixo, como pedem os codigos de
                                    // nomenclatura: familia e tribo ("Aplastini") vao em redondo.
                                    val italic = taxon.rank.isBlank() || taxon.rank in ITALIC_RANKS
                                    if (italic) pushStyle(androidx.compose.ui.text.SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic))
                                    append(taxon.canonicalName)
                                    if (italic) pop()
                                    if (taxon.authorship.isNotBlank()) append(" ${taxon.authorship}")
                                },
                                style = MaterialTheme.typography.bodyLarge
                            )
                            val details = listOfNotNull(
                                suggestion.matchedVernacular?.let { "\"$it\"" },
                                taxon.family.takeIf { it.isNotBlank() },
                                dwcValueText(taxon.rank).takeIf { it.isNotBlank() },
                                suggestion.checklistTitle
                            ).joinToString(" · ")
                            Text(details, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            suggestion.distribution?.toDistribution()?.let { distribution ->
                                Text(
                                    distributionSummary(distribution),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
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
        if (!focused && check?.distribution != null) {
            Text(
                "${ui("Distribuição na lista")}: ${distributionSummary(check.distribution)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Resumo da distribuicao segundo a lista: origem, endemismo, dominios e estados.
 * Ex.: "Endêmica do Brasil · Mata Atlântica · BA, ES, RJ"; "Nativa · 12 estados".
 */
@Composable
private fun distributionSummary(d: com.oikos.fieldbook.checklist.TaxonDistribution): String {
    val language = LocalOikosLanguage.current
    fun say(pt: String, en: String, es: String) = when (language) {
        AppLanguage.PT -> pt
        AppLanguage.EN -> en
        AppLanguage.ES -> es
    }
    val origin = d.establishment.mapNotNull { code ->
        when (code) {
            "native" -> say("Nativa", "Native", "Nativa")
            "introduced" -> say("Introduzida", "Introduced", "Introducida")
            "cultivated" -> say("Cultivada", "Cultivated", "Cultivada")
            "naturalized" -> say("Naturalizada", "Naturalized", "Naturalizada")
            "uncertain" -> say("Origem incerta", "Uncertain origin", "Origen incierto")
            else -> null
        }
    }.joinToString(", ")
    val endemic = if (d.endemic == true) say("Endêmica do Brasil", "Endemic to Brazil", "Endémica de Brasil") else ""
    val domains = d.domains.joinToString(", ") { domain ->
        when (domain) {
            "Amazônia" -> say(domain, "Amazon", "Amazonia")
            "Mata Atlântica" -> say(domain, "Atlantic Forest", "Bosque Atlántico")
            else -> domain
        }
    }
    val states = when {
        d.states.isEmpty() -> ""
        d.states.size >= 27 -> say("todos os estados", "all states", "todos los estados")
        d.states.size > 8 -> say("${d.states.size} estados", "${d.states.size} states", "${d.states.size} estados")
        else -> d.states.joinToString(", ")
    }
    return listOf(endemic.ifBlank { origin }, domains, states).filter { it.isNotBlank() }.joinToString(" · ")
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
                TrackProfile.PRECISE to "Precisa: ponto a cada 3 s ou 1,5 m. Gasta mais bateria.",
                TrackProfile.BALANCED to "Equilibrada: a cada 10 s ou 5 m.",
                TrackProfile.SAVER to "Economia: a cada 30 s ou 15 m. Poupa bateria."
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
                ui("Vale para a próxima trilha. Se ela parar com a tela apagada, tire o Oikos da otimização de bateria."),
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
    var pending by remember { mutableStateOf<PendingImport?>(null) }
    var receiving by remember { mutableStateOf(false) }
    fun inspect(uri: Uri, received: File?) {
        scope.launch {
            viewModel.inspectProjectPackage(uri)
                .onSuccess { manifest -> pending = PendingImport(uri, manifest, received) }
                .onFailure {
                    received?.delete()
                    viewModel.showMessage(com.oikos.fieldbook.util.UiLanguage.t("Não foi possível abrir o pacote: ${it.message}", "Could not open the package: ${it.message}", "No se pudo abrir el paquete: ${it.message}"))
                }
        }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) inspect(uri, null)
    }
    val askNearby = rememberNearbyPermissions(viewModel) { receiving = true }
    if (receiving) {
        NearbyReceiveDialog(
            viewModel,
            onReceived = { file ->
                receiving = false
                viewModel.nearby.stop()
                inspect(Uri.fromFile(file), file)
            },
            onClose = {
                receiving = false
                viewModel.nearby.stop()
            }
        )
    }
    pending?.let { item ->
        ImportPackageDialogs(
            item = item,
            projects = projects,
            onCancel = {
                item.received?.delete()
                pending = null
            },
            onImport = { target, takeIncoming ->
                pending = null
                viewModel.importProjectPackage(item.uri, target, takeIncoming, cleanup = item.received) { id -> if (alive.get()) currentOnImported(id) }
            }
        )
    }
    // Um botao so, "Importar": o pacote que chegou como arquivo ou direto do
    // celular ao lado (sem internet e sem mexer em arquivos).
    var choosing by remember { mutableStateOf(false) }
    if (choosing) {
        PhoneTransferChooser(
            title = ui("Importar"),
            nearbyTitle = ui("Importar de outro celular"),
            nearbyDetails = ui("Direto do celular ao lado, sem internet (Bluetooth e Wi-Fi)."),
            fileTitle = ui("Importar de um arquivo"),
            fileDetails = ui("Pacote que chegou por WhatsApp, e-mail, Drive ou cabo."),
            fileFirst = true,
            onNearby = {
                choosing = false
                askNearby()
            },
            onFile = {
                choosing = false
                launcher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
            },
            onDismiss = { choosing = false }
        )
    }
    OutlinedButton(onClick = { choosing = true }, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.FileDownload, null)
        Spacer(Modifier.width(8.dp))
        Text(ui("Importar"))
    }
}

/** Escolha da troca entre celulares: direto com o celular ao lado ou por arquivo. */
@Composable
private fun PhoneTransferChooser(
    title: String,
    nearbyTitle: String,
    fileTitle: String,
    fileDetails: String,
    onNearby: () -> Unit,
    onFile: () -> Unit,
    onDismiss: () -> Unit,
    nearbyDetails: String = ui("Direto, sem internet (Bluetooth e Wi-Fi)."),
    fileFirst: Boolean = false
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (fileFirst) PhoneTransferOption(Icons.Default.FolderOpen, fileTitle, fileDetails, onFile)
                PhoneTransferOption(Icons.Default.NearMe, nearbyTitle, nearbyDetails, onNearby)
                if (!fileFirst) PhoneTransferOption(Icons.Default.FolderOpen, fileTitle, fileDetails, onFile)
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(ui("Cancelar")) } }
    )
}

@Composable
private fun PhoneTransferOption(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, details: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Pacote escolhido ou recebido, ja lido e comparado com este aparelho. [received] e apagado depois. */
private data class PendingImport(val uri: Uri, val manifest: TransferManifest, val received: File?)

/**
 * Onde o pacote entra (projeto novo com o nome de quem enviou, ou juntar a um
 * projeto deste aparelho) e, se houver registros editados nos dois celulares,
 * qual versao fica em cada um.
 */
@Composable
private fun ImportPackageDialogs(
    item: PendingImport,
    projects: List<ProjectEntity>,
    onCancel: () -> Unit,
    onImport: (Long?, Set<String>) -> Unit
) {
    val manifest = item.manifest
    // O mais provavel e juntar ao projeto que ja tem as expedicoes do pacote ou,
    // sem ele, a um projeto com o mesmo nome.
    var target by remember(item) { mutableStateOf(manifest.existingProjectId ?: projects.firstOrNull { it.name == manifest.projectName }?.id) }
    var choosingVersions by remember(item) { mutableStateOf(false) }
    // Ja vem marcada a versao editada por ultimo em cada registro.
    val takeIncoming = remember(item) { mutableStateListOf<String>().apply { addAll(manifest.conflicts.filter { it.incomingIsNewer }.map { it.key }) } }
    if (!choosingVersions) {
        AlertDialog(
            onDismissRequest = onCancel,
            title = { Text(ui("Importar pacote")) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "\"${manifest.projectName}\": ${manifest.sessions} ${ui(if (manifest.sessions == 1) "expedição" else "expedições")}, " +
                            "${manifest.occurrences} ${ui(if (manifest.occurrences == 1) "ocorrência" else "ocorrências")}, " +
                            "${manifest.media} ${ui(if (manifest.media == 1) "mídia" else "mídias")}."
                    )
                    if (manifest.senderName.isNotBlank()) Text("${ui("Enviado por")} ${manifest.senderName}")
                    Text(
                        ui("Registros que já estão neste aparelho (mesmo identificador) não são duplicados."),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // Pacote que ja esta todo aqui: so faz sentido juntar (criar novo daria erro).
                    if (!manifest.alreadyImported) {
                        OfflineMapChoice(selected = target == null, title = ui("Criar projeto novo"), details = manifest.newProjectName, onSelect = { target = null })
                    }
                    projects.forEach { project ->
                        OfflineMapChoice(selected = target == project.id, title = "${ui("Juntar a")} ${project.name}", details = ui("as expedições entram neste projeto"), onSelect = { target = project.id })
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (manifest.conflicts.isEmpty()) onImport(target, emptySet()) else choosingVersions = true
                }) { Text(if (manifest.conflicts.isEmpty()) ui("Importar") else ui("Continuar")) }
            },
            dismissButton = { TextButton(onClick = onCancel) { Text(ui("Cancelar")) } }
        )
    } else {
        val language = LocalOikosLanguage.current
        AlertDialog(
            onDismissRequest = {},
            title = { Text(ui("Diferentes nos dois celulares")) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(ui("Estes registros estão diferentes neste celular e no pacote que chegou. Escolha qual versão fica em cada um; já vem marcada a editada por último."))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { takeIncoming.clear() }) { Text(ui("Todos deste celular")) }
                        TextButton(onClick = { takeIncoming.clear(); takeIncoming.addAll(manifest.conflicts.map { it.key }) }) { Text(ui("Todos os recebidos")) }
                    }
                    manifest.conflicts.forEach { conflict ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.medium)
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), MaterialTheme.shapes.medium)
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                "${ui(if (conflict.isOccurrence) "Ocorrência" else "Expedição")} · ${conflict.title.ifBlank { ui("sem nome") }}",
                                style = MaterialTheme.typography.titleSmall
                            )
                            if (conflict.subtitle.isNotBlank()) Text(conflict.subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            conflict.differences.take(MAX_DIFFERENCES_SHOWN).forEach { difference ->
                                Text(transferFieldLabel(difference.column, language), style = MaterialTheme.typography.labelLarge)
                                Text("${ui("Este celular")}: ${transferValueText(difference.local)}", style = MaterialTheme.typography.bodySmall)
                                Text("${ui("Recebido")}: ${transferValueText(difference.incoming)}", style = MaterialTheme.typography.bodySmall)
                            }
                            val hidden = conflict.differences.size - MAX_DIFFERENCES_SHOWN
                            if (hidden > 0) Text("+ $hidden ${ui(if (hidden == 1) "campo diferente" else "campos diferentes")}", style = MaterialTheme.typography.labelMedium)
                            OfflineMapChoice(
                                selected = conflict.key !in takeIncoming,
                                title = ui("Manter a deste celular"),
                                details = "${ui("editada em")} ${transferDate(conflict.localUpdatedAt)}",
                                onSelect = { takeIncoming.remove(conflict.key) }
                            )
                            OfflineMapChoice(
                                selected = conflict.key in takeIncoming,
                                title = ui("Usar a que chegou"),
                                details = "${ui("editada em")} ${transferDate(conflict.incomingUpdatedAt)}",
                                onSelect = { if (conflict.key !in takeIncoming) takeIncoming.add(conflict.key) }
                            )
                        }
                    }
                }
            },
            confirmButton = { Button(onClick = { onImport(target, takeIncoming.toSet()) }) { Text(ui("Importar")) } },
            dismissButton = { TextButton(onClick = { choosingVersions = false }) { Text(ui("Voltar")) } }
        )
    }
}

private const val MAX_DIFFERENCES_SHOWN = 8

/** Nome do campo em conflito: rotulo do Darwin Core ou dos campos proprios do Oikos. */
private fun transferFieldLabel(column: String, language: AppLanguage): String {
    val term = column.removePrefix("dyn:")
    DarwinCoreCatalog.allFields.firstOrNull { it.term == term }?.let {
        return when (language) {
            AppLanguage.PT -> it.labelPt
            AppLanguage.EN -> it.labelEn
            AppLanguage.ES -> spanishFieldLabels[term] ?: it.labelEn
        }
    }
    val (pt, en, es) = when (term) {
        "name" -> Triple("Nome da expedição", "Expedition name", "Nombre de la salida de campo")
        "startTime" -> Triple("Hora de início", "Start time", "Hora de inicio")
        "endTime" -> Triple("Hora de término", "End time", "Hora de término")
        "fieldTeam" -> Triple("Equipe", "Field team", "Equipo de campo")
        "gpsTrackEnabled" -> Triple("Trilha GPS", "GPS track", "Recorrido GPS")
        "notes" -> Triple("Observações", "Notes", "Observaciones")
        "fieldNotes" -> Triple("Notas de campo", "Field notes", "Notas de campo")
        "formType" -> Triple("Tipo de ficha", "Form type", "Tipo de ficha")
        "occurrenceType" -> Triple("Tipo de registro", "Record type", "Tipo de registro")
        "environmentType" -> Triple("Tipo de ambiente", "Environment type", "Tipo de ambiente")
        "coordinateCapturedAt" -> Triple("Hora da coordenada", "Coordinate time", "Hora de la coordenada")
        else -> Triple(term, term, term)
    }
    return when (language) {
        AppLanguage.PT -> pt
        AppLanguage.EN -> en
        AppLanguage.ES -> es
    }
}

@Composable
private fun transferValueText(value: String): String = if (value.isBlank()) ui("(vazio)") else dwcValueText(value)

private fun transferDate(millis: Long): String =
    if (millis <= 0L) "?" else Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))

/**
 * Pede as permissoes do envio direto (dispositivos proximos e localizacao
 * precisa) e so entao segue. Sem elas o Nearby recusa anunciar ou procurar.
 */
@Composable
private fun rememberNearbyPermissions(viewModel: AppViewModel, onGranted: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val currentOnGranted by rememberUpdatedState(onGranted)
    fun granted(permission: String) =
        androidx.core.content.ContextCompat.checkSelfPermission(context, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        // Confere o estado real: quem escolhe "aproximada" fica sem a precisa.
        if (com.oikos.fieldbook.services.NearbyTransfer.requiredPermissions().all(::granted)) currentOnGranted()
        else viewModel.showMessage(
            com.oikos.fieldbook.util.UiLanguage.t(
                "O envio direto precisa das permissões de dispositivos próximos e de localização precisa (o Oikos não registra a localização nesse uso). Libere em Configurações do Android > Apps > Oikos FieldBook > Permissões, ou mande o pacote por WhatsApp, Drive ou cabo.",
                "Direct transfer needs the nearby devices and precise location permissions (Oikos does not record your location for this). Allow them in Android Settings > Apps > Oikos FieldBook > Permissions, or send the package via WhatsApp, Drive or cable.",
                "El envío directo necesita los permisos de dispositivos cercanos y de ubicación precisa (Oikos no registra tu ubicación para esto). Actívalos en Ajustes de Android > Aplicaciones > Oikos FieldBook > Permisos, o envía el paquete por WhatsApp, Drive o cable."
            )
        )
    }
    return {
        val required = com.oikos.fieldbook.services.NearbyTransfer.requiredPermissions()
        var missing = required.filterNot(::granted)
        // A precisa so e pedida junto com a aproximada (Android 12+), mesmo que esta ja esteja dada.
        if (android.Manifest.permission.ACCESS_FINE_LOCATION in missing) {
            missing = (missing + android.Manifest.permission.ACCESS_COARSE_LOCATION).distinct()
        }
        if (missing.isEmpty()) currentOnGranted() else launcher.launch(missing.toTypedArray())
    }
}

/** Tela acesa enquanto o dialogo de envio/recebimento esta aberto. */
@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

/**
 * Barra do envio direto, com a velocidade e o tempo que falta (media dos
 * ultimos 10 s) e a qualidade da conexao informada pelo Nearby.
 */
@Composable
private fun TransferProgress(bytes: Long, total: Long, quality: Int) {
    val samples = remember { mutableStateListOf<Pair<Long, Long>>() }
    LaunchedEffect(bytes) {
        val now = System.currentTimeMillis()
        samples.add(now to bytes)
        while (samples.size > 2 && samples.first().first < now - 10_000) samples.removeAt(0)
    }
    val speed = samples.takeIf { it.size >= 2 }?.let { list ->
        val (t0, b0) = list.first()
        val (t1, b1) = list.last()
        if (t1 - t0 >= 2_000 && b1 > b0) (b1 - b0) * 1000.0 / (t1 - t0) else null
    }
    if (total > 0) {
        LinearProgressIndicator(progress = { (bytes.toFloat() / total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
        Text("${formatMegabytes(bytes)} ${ui("de")} ${formatMegabytes(total)}", style = MaterialTheme.typography.labelMedium)
    } else {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
    if (speed != null) {
        val seconds = if (total > bytes) ((total - bytes) / speed).toLong() else 0L
        Text("${formatSpeed(speed)} · ${remainingTime(seconds)}", style = MaterialTheme.typography.labelMedium)
    }
    ConnectionQuality(quality)
    Text(ui("Não feche esta tela até terminar."), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Qualidade da conexao do Nearby, com o que fazer quando ela esta lenta. */
@Composable
private fun ConnectionQuality(quality: Int) {
    val text = when (quality) {
        com.google.android.gms.nearby.connection.BandwidthInfo.Quality.HIGH ->
            tr("Conexão rápida (Wi-Fi).", "Fast connection (Wi-Fi).", "Conexión rápida (Wi-Fi).")
        com.google.android.gms.nearby.connection.BandwidthInfo.Quality.MEDIUM ->
            tr(
                "Conexão média. Com o Wi-Fi ligado nos dois celulares, ela costuma ficar rápida sozinha.",
                "Medium connection. With Wi-Fi on in both phones, it usually speeds up on its own.",
                "Conexión media. Con el Wi-Fi activado en los dos celulares, suele volverse rápida sola."
            )
        com.google.android.gms.nearby.connection.BandwidthInfo.Quality.LOW ->
            tr(
                "Conexão lenta. Ligue o Wi-Fi nos dois celulares (não precisa de internet) e deixe um perto do outro.",
                "Slow connection. Turn on Wi-Fi in both phones (no internet needed) and keep them close together.",
                "Conexión lenta. Activa el Wi-Fi en los dos celulares (no hace falta internet) y mantenlos cerca."
            )
        else -> return
    }
    val fast = quality == com.google.android.gms.nearby.connection.BandwidthInfo.Quality.HIGH
    Text(text, style = MaterialTheme.typography.labelMedium, color = if (fast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Aviso enquanto o Wi-Fi deste celular estiver desligado: sem ele o envio direto fica no Bluetooth, lento. */
@Composable
private fun WifiHint(viewModel: AppViewModel) {
    var wifiOn by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) {
            wifiOn = viewModel.nearby.wifiEnabled()
            kotlinx.coroutines.delay(2_000)
        }
    }
    if (!wifiOn) {
        Text(
            tr(
                "O Wi-Fi deste celular está desligado. Ligue o Wi-Fi nos dois celulares (não precisa de internet): o envio fica muito mais rápido.",
                "This phone's Wi-Fi is off. Turn on Wi-Fi in both phones (no internet needed): the transfer gets much faster.",
                "El Wi-Fi de este celular está apagado. Activa el Wi-Fi en los dos celulares (no hace falta internet): el envío es mucho más rápido."
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }
}

/** "1,2 MB/s" ou "180 KB/s", no formato de numero do idioma do app. */
private fun formatSpeed(bytesPerSecond: Double): String {
    val locale = com.oikos.fieldbook.util.UiLanguage.current.locale
    return if (bytesPerSecond >= 1024 * 1024) String.format(locale, "%.1f MB/s", bytesPerSecond / (1024.0 * 1024.0))
    else String.format(locale, "%.0f KB/s", bytesPerSecond / 1024.0)
}

@Composable
private fun remainingTime(seconds: Long): String {
    if (seconds < 60) return tr("falta menos de 1 min", "less than 1 min left", "falta menos de 1 min")
    val minutes = (seconds + 59) / 60
    if (minutes < 90) return tr("faltam cerca de $minutes min", "about $minutes min left", "faltan unos $minutes min")
    val hours = minutes / 60
    val rest = minutes % 60
    return tr("faltam cerca de $hours h $rest min", "about $hours h $rest min left", "faltan unas $hours h $rest min")
}

/** "3,4 MB" no app em portugues ou espanhol e "3.4 MB" em ingles, seja qual for o idioma do aparelho. */
private fun formatMegabytes(bytes: Long): String =
    String.format(
        com.oikos.fieldbook.util.UiLanguage.current.locale,
        "%.1f MB",
        bytes / (1024.0 * 1024.0)
    )

@Composable
private fun TransferCode(code: String) {
    if (code.isBlank()) return
    Text("${ui("Código")}: $code", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
}

/** Quem envia: procura celulares com o "Receber" aberto e manda o pacote. */
@Composable
private fun NearbySendDialog(viewModel: AppViewModel, projectId: Long, sessionId: Long?, onClose: () -> Unit) {
    val state by viewModel.nearby.state.collectAsState()
    KeepScreenOn()
    LaunchedEffect(Unit) { viewModel.startNearbySend(projectId, sessionId) }
    val close = {
        viewModel.nearby.stop()
        onClose()
    }
    AlertDialog(
        onDismissRequest = {},
        title = { Text(ui("Enviar para outro celular")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (val current = state) {
                    is com.oikos.fieldbook.services.NearbyTransfer.State.Searching -> {
                        Text(ui("No outro celular, toque em Importar na tela inicial e escolha Importar de outro celular. Não precisa de internet."))
                        WifiHint(viewModel)
                        if (current.peers.isEmpty()) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Text(ui("Procurando celulares..."), style = MaterialTheme.typography.labelMedium)
                        }
                        current.peers.forEach { peer ->
                            OutlinedButton(onClick = { viewModel.nearby.connectTo(peer) }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.PhoneAndroid, null)
                                Spacer(Modifier.width(8.dp))
                                Text(peer.name)
                            }
                        }
                    }
                    is com.oikos.fieldbook.services.NearbyTransfer.State.Connecting -> {
                        Text("${ui("Conectando a")} ${current.name}...")
                        TransferCode(current.code)
                        Text(ui("Confira se o mesmo código aparece no outro celular e peça para tocar em Aceitar."))
                    }
                    is com.oikos.fieldbook.services.NearbyTransfer.State.Upgrading -> {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(
                            "${tr("Conectado a", "Connected to", "Conectado a")} ${current.name}. " +
                                tr(
                                    "Preparando a conexão rápida por Wi-Fi; o envio começa em instantes.",
                                    "Setting up the fast Wi-Fi connection; the transfer starts in a moment.",
                                    "Preparando la conexión rápida por Wi-Fi; el envío empieza en un momento."
                                )
                        )
                        WifiHint(viewModel)
                    }
                    is com.oikos.fieldbook.services.NearbyTransfer.State.Transferring -> {
                        Text("${ui("Enviando para")} ${current.name}")
                        TransferProgress(current.bytes, current.total, current.quality)
                    }
                    is com.oikos.fieldbook.services.NearbyTransfer.State.Confirming -> {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(ui("Conferindo se o pacote chegou inteiro..."))
                    }
                    is com.oikos.fieldbook.services.NearbyTransfer.State.Sent ->
                        Text("${ui("Pacote enviado para")} ${current.name}. ${ui("No outro celular, escolha se ele entra num projeto que já existe ou num projeto novo.")}")
                    is com.oikos.fieldbook.services.NearbyTransfer.State.Failed ->
                        Text("${ui("Não foi possível enviar")}: ${current.message}")
                    else -> {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(ui("Montando o pacote. Com muitos áudios pode levar alguns minutos; não feche o app."))
                    }
                }
            }
        },
        confirmButton = {
            when (state) {
                is com.oikos.fieldbook.services.NearbyTransfer.State.Sent -> Button(onClick = close) { Text(ui("Concluir")) }
                is com.oikos.fieldbook.services.NearbyTransfer.State.Failed -> Button(onClick = { viewModel.startNearbySend(projectId, sessionId) }) { Text(ui("Tentar de novo")) }
                else -> Unit
            }
        },
        dismissButton = {
            if (state !is com.oikos.fieldbook.services.NearbyTransfer.State.Sent) TextButton(onClick = close) { Text(ui("Cancelar")) }
        }
    )
}

/** Quem recebe: fica visivel para os celulares proximos e aceita o pacote. */
@Composable
private fun NearbyReceiveDialog(viewModel: AppViewModel, onReceived: (File) -> Unit, onClose: () -> Unit) {
    val state by viewModel.nearby.state.collectAsState()
    val currentOnReceived by rememberUpdatedState(onReceived)
    KeepScreenOn()
    LaunchedEffect(Unit) { viewModel.nearby.startReceiving() }
    LaunchedEffect(state) {
        (state as? com.oikos.fieldbook.services.NearbyTransfer.State.Received)?.let { currentOnReceived(it.file) }
    }
    AlertDialog(
        onDismissRequest = {},
        title = { Text(ui("Importar de outro celular")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (val current = state) {
                    is com.oikos.fieldbook.services.NearbyTransfer.State.Waiting -> {
                        Text(ui("No outro celular, abra o projeto, toque em Exportar, depois em Enviar para outro celular e escolha Para o celular ao lado. Não precisa de internet."))
                        Text("${ui("Este celular aparece como")}: ${current.myName}", style = MaterialTheme.typography.titleSmall)
                        WifiHint(viewModel)
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(ui("Aguardando..."), style = MaterialTheme.typography.labelMedium)
                    }
                    is com.oikos.fieldbook.services.NearbyTransfer.State.Incoming -> {
                        Text("${current.name} ${ui("quer enviar um pacote.")}")
                        TransferCode(current.code)
                        Text(ui("Confira se o mesmo código aparece no outro celular antes de aceitar."))
                    }
                    is com.oikos.fieldbook.services.NearbyTransfer.State.Transferring -> {
                        Text("${ui("Recebendo de")} ${current.name}")
                        TransferProgress(current.bytes, current.total, current.quality)
                    }
                    is com.oikos.fieldbook.services.NearbyTransfer.State.Failed ->
                        Text("${ui("Não foi possível receber")}: ${current.message}")
                    else -> {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(ui("Conectando..."))
                    }
                }
            }
        },
        confirmButton = {
            when (val current = state) {
                is com.oikos.fieldbook.services.NearbyTransfer.State.Incoming -> Button(onClick = { viewModel.nearby.accept(current.endpointId) }) { Text(ui("Aceitar")) }
                is com.oikos.fieldbook.services.NearbyTransfer.State.Failed -> Button(onClick = { viewModel.nearby.startReceiving() }) { Text(ui("Tentar de novo")) }
                else -> Unit
            }
        },
        dismissButton = {
            when (val current = state) {
                is com.oikos.fieldbook.services.NearbyTransfer.State.Incoming -> TextButton(onClick = { viewModel.nearby.reject(current.endpointId) }) { Text(ui("Recusar")) }
                else -> TextButton(onClick = onClose) { Text(ui("Cancelar")) }
            }
        }
    )
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
                ui("Mapa da sua área para usar sem sinal (satélite, topográfico, vegetação). Gere o .mbtiles no QGIS e importe aqui."),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OfflineMapChoice(
                selected = active == null,
                title = ui("OpenStreetMap (internet)"),
                details = ui("Precisa de internet."),
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
                        UiLanguage.count(manifest.projects, "projeto", "projetos", "project", "projects", "proyecto", "proyectos") + ", " +
                        UiLanguage.count(manifest.occurrences, "ocorrência", "ocorrências", "occurrence", "occurrences", "registro", "registros") + ", " +
                        UiLanguage.count(manifest.mediaFiles, "mídia", "mídias", "media file", "media files", "archivo multimedia", "archivos multimedia") +
                        " (${humanSize(manifest.mediaBytes)}).\n\n" +
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
                ui("Arquivo .zip com todos os projetos, fotos e áudios. Guarde fora do celular."),
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
                        ui("Produto científico desenvolvido por Luan da Silva Cortes Maciel (Maciel, L. S. C.), mestrando no Instituto de Pesquisa Jardim Botânico do Rio de Janeiro, sob orientação de Leandro Freitas e coorientação de Matheus de Toledo Moroti."),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(ui("Aplicativo vinculado à pesquisa:"), style = MaterialTheme.typography.labelLarge)
                    Text(
                        "\"Levantamento e Interações Ecológicas entre Anuros e Bromélias na RPPN Gigante do Itaguaré\".",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        ui("Mapa: © OpenStreetMap contributors (openstreetmap.org/copyright). Municípios: IBGE, Malha Municipal 2022, CC BY 4.0, adaptada para uso offline. Altitude: geoide EGM2008 (NGA). Listas de espécies: Flora e Funga do Brasil (JBRJ) e Catálogo Taxonômico da Fauna do Brasil, CC BY 4.0, obtidas no IPT do JBRJ ou na cópia do ChecklistBank."),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            }
            item { FeedbackCard() }
            item { HowToCiteCard() }
            item { OpenSourceLicensesCard() }
            item { PrivacyPolicyButton() }
            item {
            DeveloperSignature(Modifier.fillMaxWidth().padding(top = 20.dp))
            }
        }
    }
}

/**
 * Cartao que abre e fecha pelo botao + / -: deixa as telas Sobre e
 * Configuracoes curtas, com o detalhe a um toque.
 */
@Composable
private fun ExpandableInfoCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    var open by rememberSaveable(title) { mutableStateOf(false) }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().clickable { open = !open }.padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = { open = !open }) {
                    Icon(if (open) Icons.Default.Remove else Icons.Default.Add, contentDescription = if (open) ui("Fechar") else ui("Abrir"))
                }
            }
            AnimatedVisibility(visible = open) {
                Column(Modifier.padding(start = 18.dp, end = 18.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
            }
        }
    }
}

/** Citacao do aplicativo (ABNT, Vancouver e APA), cada uma com botao para copiar. */
@Composable
private fun HowToCiteCard() {
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val citations = remember { AppCitation.all(BuildConfig.VERSION_NAME) }
    var copied by remember { mutableStateOf<String?>(null) }
    ExpandableInfoCard(ui("Como citar")) {
        Text(
            ui("Se o Oikos ajudou no seu trabalho, cite o aplicativo. As exportações em ZIP trazem estas citações no arquivo COMO_CITAR.txt."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text("DOI: ${AppCitation.DOI}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        citations.forEach { (style, text) ->
            Text(style, style = MaterialTheme.typography.labelLarge)
            // ABNT pede o titulo em destaque (NBR 6023, 6.7): negrito na tela.
            val shown = if (style == "ABNT") {
                androidx.compose.ui.text.buildAnnotatedString {
                    val at = text.indexOf(AppCitation.TITLE)
                    append(text)
                    if (at >= 0) addStyle(androidx.compose.ui.text.SpanStyle(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), at, at + AppCitation.TITLE.length)
                }
            } else {
                androidx.compose.ui.text.AnnotatedString(text)
            }
            SelectionContainer { Text(shown, style = MaterialTheme.typography.bodySmall) }
            TextButton(onClick = {
                clipboard.setText(androidx.compose.ui.text.AnnotatedString(text))
                copied = style
            }) { Text(if (copied == style) ui("Copiado") else "${ui("Copiar")} $style") }
        }
    }
}

/**
 * Bibliotecas de codigo aberto do app. A Apache 2.0 pede que o texto da
 * licenca acompanhe o app, e o Google Play services traz avisos de
 * componentes de terceiros (assets/licencas; o arquivo do Play services e
 * gerado por tools/licencas/AvisosPlayServices.java).
 */
private val OPEN_SOURCE_LIBRARIES = listOf(
    "AndroidX, Jetpack Compose" to "Apache 2.0",
    "Kotlin, kotlinx.coroutines, kotlinx.serialization" to "Apache 2.0",
    "osmdroid" to "Apache 2.0",
    "ZXing" to "Apache 2.0",
    "Google Play services Location" to "Android Software Development Kit License"
)

@Composable
private fun OpenSourceLicensesCard() {
    var reading by remember { mutableStateOf<Pair<String, String>?>(null) }
    val apacheTitle = ui("Licença Apache 2.0")
    val googleTitle = ui("Avisos de terceiros do Google Play services")
    ExpandableInfoCard(ui("Licenças de código aberto")) {
        Text(
            ui("O Oikos usa estas bibliotecas de código aberto:"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OPEN_SOURCE_LIBRARIES.forEach { (name, license) ->
            Text("$name: $license", style = MaterialTheme.typography.bodySmall)
        }
        TextButton(onClick = { reading = apacheTitle to "licencas/apache-2.0.txt" }) { Text(ui("Ler a licença Apache 2.0")) }
        TextButton(onClick = { reading = googleTitle to "licencas/google_play_services.txt" }) { Text(googleTitle) }
    }
    reading?.let { (title, asset) -> LicenseTextDialog(title, asset, onDismiss = { reading = null }) }
}

/**
 * Texto integral de uma licenca guardada em assets. Vai em paragrafos numa
 * lista preguicosa: o arquivo do Play services tem cerca de 470 KB.
 */
@Composable
private fun LicenseTextDialog(title: String, asset: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val paragraphs by androidx.compose.runtime.produceState<List<String>?>(null, asset) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open(asset).bufferedReader().use { it.readText() }
                    .split(Regex("\\n\\s*\\n"))
                    .map { it.trim().replace(Regex("\\s*\\n\\s*"), " ") }
                    .filter { it.isNotEmpty() }
            }.getOrDefault(emptyList())
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            // 90% da altura e nao fillMaxSize: no Android 15+ a janela do dialogo
            // passa da borda de baixo e o fim do texto ficava sob a barra de navegacao.
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(horizontal = 12.dp),
            color = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp
        ) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text(ui("Fechar")) }
                }
                val list = paragraphs
                when {
                    list == null -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    list.isEmpty() -> Text(ui("Não foi possível abrir o texto."), color = MaterialTheme.colorScheme.error)
                    else -> LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(list) { paragraph ->
                            if (paragraph.startsWith("### ")) {
                                Text(
                                    paragraph.removePrefix("### "),
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.padding(top = 12.dp)
                                )
                            } else {
                                Text(paragraph, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Para onde vai (e nao vai) a informacao; aberto sob demanda nas Configuracoes. */
/**
 * Relato de problema, ideia ou duvida. A pessoa escreve aqui e o app de e-mail
 * do celular abre com tudo pronto para ela conferir e enviar: sem servidor, sem
 * sinal a mensagem espera na caixa de saida, e da para responder a quem
 * escreveu. O texto fica guardado enquanto a tela estiver aberta, para nao se
 * perder se a pessoa voltar do e-mail sem enviar.
 */
@Composable
private fun FeedbackCard() {
    var open by rememberSaveable { mutableStateOf(false) }
    var kind by rememberSaveable { mutableStateOf("problem") }
    var text by rememberSaveable { mutableStateOf("") }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(ui("Relatar problema ou dar ideia"), style = MaterialTheme.typography.titleMedium)
            Text(
                ui("Achou um erro ou tem uma ideia para o app? Escreva aqui; a mensagem chega direto ao desenvolvedor."),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = { open = true }) {
                Icon(Icons.Default.Feedback, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(ui("Escrever"))
            }
        }
    }
    if (open) {
        FeedbackDialog(kind, text, onKind = { kind = it }, onText = { text = it }, onDismiss = { open = false })
    }
}

@Composable
private fun FeedbackDialog(kind: String, text: String, onKind: (String) -> Unit, onText: (String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val language = LocalOikosLanguage.current
    var noEmailApp by remember { mutableStateOf(false) }
    val kinds = listOf("problem" to "Problema", "idea" to "Ideia", "question" to "Dúvida")
    val kindLabel = ui(kinds.first { it.first == kind }.second)
    val deviceLabel = tr("Aparelho", "Device", "Dispositivo")
    val languageLabel = tr("Idioma do app", "App language", "Idioma de la app")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(ui("Relatar problema ou dar ideia")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    kinds.forEach { (value, label) ->
                        FilterChip(selected = kind == value, onClick = { onKind(value) }, label = { Text(ui(label)) })
                    }
                }
                TextInput(
                    when (kind) {
                        "idea" -> "Qual é a ideia?"
                        "question" -> "Qual é a dúvida?"
                        else -> "O que aconteceu?"
                    },
                    text,
                    minLines = 4
                ) { onText(it) }
                Text(
                    ui("Abre o seu app de e-mail com a mensagem pronta para você conferir e enviar. Junto vão a versão do app e do Android e o modelo do aparelho, para ajudar a achar o problema."),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (noEmailApp) {
                    Text(
                        ui("Nenhum app de e-mail encontrado. A mensagem foi copiada: cole num e-mail para herpetomantiqueira@gmail.com."),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(enabled = text.isNotBlank(), onClick = {
                val subject = com.oikos.fieldbook.ui.FeedbackEmail.subject(BuildConfig.VERSION_NAME, kindLabel)
                val body = com.oikos.fieldbook.ui.FeedbackEmail.body(
                    text = text,
                    appVersion = BuildConfig.VERSION_NAME,
                    appBuild = BuildConfig.VERSION_CODE,
                    androidRelease = Build.VERSION.RELEASE.orEmpty(),
                    androidSdk = Build.VERSION.SDK_INT,
                    device = "${Build.MANUFACTURER} ${Build.MODEL}",
                    deviceLabel = deviceLabel,
                    languageLabel = languageLabel,
                    languageCode = language.code
                )
                try {
                    context.startActivity(com.oikos.fieldbook.ui.FeedbackEmail.intent(subject, body))
                    onDismiss()
                } catch (_: android.content.ActivityNotFoundException) {
                    clipboard.setText(androidx.compose.ui.text.AnnotatedString("$subject\n\n$body"))
                    noEmailApp = true
                }
            }) { Text(ui("Abrir e-mail")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(ui("Cancelar")) } }
    )
}

@Composable
private fun NetworkPrivacyCard() {
    ExpandableInfoCard(ui("Uso de rede e privacidade")) {
        Text(
            ui("Seus dados ficam só neste aparelho e não vão para servidor nenhum. A internet serve apenas para o mapa, para baixar listas de espécies e, se ligado, para o endereço pelo Google."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        PrivacyPolicyButton()
    }
}

/** Endereco publico da Politica de Privacidade, o mesmo informado no Play Console. */
private const val PRIVACY_POLICY_URL = "https://luancortesm.github.io/oikos-fieldbook/privacidade/"

/**
 * Abre a Politica de Privacidade no navegador. O Google pede o link dentro do
 * app, alem do informado no Play. Sem navegador, o endereco e copiado.
 */
@Composable
private fun PrivacyPolicyButton(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedButton(onClick = {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY_URL)))
            } catch (_: android.content.ActivityNotFoundException) {
                clipboard.setText(androidx.compose.ui.text.AnnotatedString(PRIVACY_POLICY_URL))
                copied = true
            }
        }) {
            Icon(Icons.Default.Policy, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(ui("Política de privacidade"))
        }
        if (copied) {
            Text(
                ui("Nenhum navegador encontrado. O endereço foi copiado."),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ui(portuguese: String): String = uiText(portuguese, LocalOikosLanguage.current)

/** Texto montado na hora (com valores dentro), escrito nos tres idiomas. */
@Composable
private fun tr(pt: String, en: String, es: String): String = when (LocalOikosLanguage.current) {
    AppLanguage.PT -> pt
    AppLanguage.EN -> en
    AppLanguage.ES -> es
}

/** Texto fixo da tela no idioma pedido; sem traducao, fica o portugues. */
private fun uiText(portuguese: String, language: AppLanguage): String = when (language) {
    AppLanguage.PT -> portuguese
    AppLanguage.EN -> englishUiText(portuguese)
    AppLanguage.ES -> spanishText[portuguese] ?: portuguese
}

@Composable
private fun fieldDisplayLabel(field: FieldConfigEntity): String = fieldLabelIn(field, LocalOikosLanguage.current)

private fun fieldLabelIn(field: FieldConfigEntity, language: AppLanguage): String = when (language) {
    AppLanguage.PT -> prettyPortugueseText(field.labelPt)
    AppLanguage.EN -> field.labelEn.ifBlank { englishUiText(prettyPortugueseText(field.labelPt)) }
    AppLanguage.ES -> spanishFieldLabels[field.dwcTerm]
        ?: spanishText[prettyPortugueseText(field.labelPt)]
        ?: field.labelEn.ifBlank { prettyPortugueseText(field.labelPt) }
}

private fun englishUiText(portuguese: String): String = englishText[portuguese] ?: portuguese


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
        TextInput(tr("Latitude ($coordinateFormat)", "Latitude ($coordinateFormat)", "Latitud ($coordinateFormat)"), sexLat, autoCorrect = false) {
            sexLat = it
            syncDecimalFromSexagesimal()
        }
        TextInput(tr("Longitude ($coordinateFormat)", "Longitude ($coordinateFormat)", "Longitud ($coordinateFormat)"), sexLon, autoCorrect = false) {
            sexLon = it
            syncDecimalFromSexagesimal()
        }
        sexError?.let {
            Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val suffix = if (isUtm || isSexagesimal) " DD" else ""
        TextInput(tr("Latitude$suffix", "Latitude$suffix", "Latitud$suffix"), lat, KeyboardType.Decimal, Modifier.weight(1f), signToggle = true, onChange = onLat)
        TextInput(tr("Longitude$suffix", "Longitude$suffix", "Longitud$suffix"), lon, KeyboardType.Decimal, Modifier.weight(1f), signToggle = true, onChange = onLon)
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
                    label = { Text(dwcValueText(option)) }
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
/** Postos que vao em italico: genero e abaixo (ICZN e ICN). */
private val ITALIC_RANKS = setOf("genus", "subgenus", "species", "subspecies", "variety", "form")

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
    "subphylum" -> "Subfilo"
    "class" -> "Classe"
    "subclass" -> "Subclasse"
    "order" -> "Ordem"
    "suborder" -> "Subordem"
    "infraorder" -> "Infraordem"
    "family" -> "Família"
    "superfamily" -> "Superfamília"
    "subfamily" -> "Subfamília"
    "tribe" -> "Tribo"
    "subtribe" -> "Subtribo"
    "genus" -> "Gênero"
    "subgenus" -> "Subgênero"
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

/** Rotulo em espanhol para um valor de vocabulario controlado; o valor gravado continua o do padrao. */
private fun dwcValueLabelEs(value: String): String = when (value) {
    "HumanObservation" -> "Observación humana"
    "MachineObservation" -> "Observación con máquina"
    "PreservedSpecimen" -> "Espécimen preservado"
    "MaterialSample" -> "Muestra de material"
    "LivingSpecimen" -> "Espécimen vivo"
    "FossilSpecimen" -> "Espécimen fósil"
    "MaterialCitation" -> "Citación de material"
    "present" -> "Presente"
    "absent" -> "Ausente"
    "male" -> "Macho"
    "female" -> "Hembra"
    "hermaphrodite" -> "Hermafrodita"
    "undetermined", "unknown" -> "Indeterminado"
    "uncertain" -> "Incierto"
    "egg" -> "Huevo"
    "larva" -> "Larva"
    "tadpole" -> "Renacuajo"
    "juvenile" -> "Juvenil"
    "subadult" -> "Subadulto"
    "adult" -> "Adulto"
    "seedling" -> "Plántula"
    "sapling" -> "Brinzal"
    "seed" -> "Semilla"
    "kingdom" -> "Reino"
    "phylum" -> "Filo"
    "subphylum" -> "Subfilo"
    "class" -> "Clase"
    "subclass" -> "Subclase"
    "order" -> "Orden"
    "suborder" -> "Suborden"
    "infraorder" -> "Infraorden"
    "family" -> "Familia"
    "superfamily" -> "Superfamilia"
    "subfamily" -> "Subfamilia"
    "tribe" -> "Tribu"
    "subtribe" -> "Subtribu"
    "genus" -> "Género"
    "subgenus" -> "Subgénero"
    "species" -> "Especie"
    "subspecies" -> "Subespecie"
    "variety" -> "Variedad"
    "form" -> "Forma"
    "holotype" -> "Holotipo"
    "paratype" -> "Paratipo"
    "isotype" -> "Isotipo"
    "lectotype" -> "Lectotipo"
    "neotype" -> "Neotipo"
    "syntype" -> "Sintipo"
    "topotype" -> "Topotipo"
    "notAType" -> "No es tipo"
    "native" -> "Nativa"
    "nativeReintroduced" -> "Nativa (reintroducida)"
    "introduced" -> "Introducida"
    "introducedAssistedColonisation" -> "Introducida (colonización asistida)"
    "vagrant" -> "Transitoria (casual)"
    "captive" -> "En cautiverio"
    "cultivated" -> "Cultivada"
    "released" -> "Liberada"
    "failing" -> "En declive"
    "casual" -> "Ocasional"
    "reproducing" -> "En reproducción"
    "established" -> "Establecida"
    "colonising" -> "Colonizadora"
    "invasive" -> "Invasora"
    "widespreadInvasive" -> "Invasora generalizada"
    "corridor" -> "Corredor"
    "escapeFromConfinement" -> "Escape de confinamiento"
    "releaseInNature" -> "Liberación en la naturaleza"
    "transportContaminant" -> "Contaminante de transporte"
    "transportStowaway" -> "Polizón en transporte"
    "unaided" -> "Dispersión propia"
    "alive" -> "Vivo"
    "dead" -> "Muerto"
    "mixed" -> "Mixto"
    "unverified" -> "No verificada"
    "verifiedBySpecialist" -> "Verificada por especialista"
    "requiresVerification" -> "Requiere verificación"
    "verified" -> "Verificada"
    "exsicata" -> "Exsicata"
    "alcohol" -> "Alcohol"
    "driedWhole" -> "Seco entero"
    "pinned" -> "Montado en alfiler"
    "tissueInSilica" -> "Tejido en sílica gel"
    "tissueInEthanol" -> "Tejido en etanol"
    "skeleton" -> "Esqueleto"
    "skin" -> "Piel"
    "slide" -> "Preparación microscópica"
    "photographOnly" -> "Solo fotografía"
    "inCollection" -> "Depositado"
    "inProcessing" -> "En procesamiento"
    "onLoan" -> "En préstamo"
    "missing" -> "Extraviado"
    "destroyed" -> "Destruido"
    "notCollected" -> "No colectado"
    "metre" -> "metro"
    "squareMetre" -> "metro cuadrado"
    "hectare" -> "hectárea"
    "kilometre" -> "kilómetro"
    "hour" -> "hora"
    "minute" -> "minuto"
    "day" -> "día"
    "trapNight" -> "trampa-noche"
    "individual" -> "individuo"
    else -> value
}

/** Valor de vocabulario na lingua da tela; em ingles, o proprio termo em palavras ("notAType" -> "Not a type"). */
@Composable
private fun dwcValueText(value: String): String {
    val portuguese = dwcValueLabel(value)
    if (LocalOikosLanguage.current == AppLanguage.ES) return dwcValueLabelEs(value)
    if (LocalOikosLanguage.current == AppLanguage.PT || portuguese == value) return portuguese
    val words = value.replace(Regex("([a-z])([A-Z])"), "$1 $2").lowercase()
    return if (portuguese.first().isUpperCase()) words.replaceFirstChar { it.uppercase() } else words
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

