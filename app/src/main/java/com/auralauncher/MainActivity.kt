package com.auralauncher

import android.app.role.RoleManager
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.IntentFilter
import android.provider.Settings.Secure
import com.auralauncher.backup.LauncherBackup
import com.auralauncher.widgets.AuraWidgetHostController
import com.auralauncher.icons.IconPackManager
import com.auralauncher.icons.InstalledIconPack
import com.auralauncher.notifications.AuraNotificationListenerService
import com.auralauncher.backup.LauncherBackupCodec
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import androidx.compose.foundation.gestures.detectTapGestures
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class LaunchableApp(
    val title: String,
    val packageName: String,
    val icon: Drawable,
    val activityClassName: String = "",
    val notificationCount: Int = 0
)

private data class AppFolder(
    val name: String,
    val packages: List<String>
)

class MainActivity : ComponentActivity() {
    private lateinit var appState: androidx.compose.runtime.MutableState<List<LaunchableApp>>
    private lateinit var defaultState: androidx.compose.runtime.MutableState<Boolean>
    private lateinit var notificationAccessState: androidx.compose.runtime.MutableState<Boolean>
    private lateinit var widgetHostController: AuraWidgetHostController
    private lateinit var widgetPlacementsState: androidx.compose.runtime.MutableState<List<WidgetPlacement>>
    private lateinit var wallpaperState: androidx.compose.runtime.MutableState<String?>
    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var appLoadJob: Job? = null
    private var pendingWidgetId: Int? = null

    private fun refreshLaunchableApps() {
        if (!::appState.isInitialized) return
        appLoadJob?.cancel()
        appLoadJob = activityScope.launch {
            try {
                val loadedApps = withContext(Dispatchers.IO) { loadLaunchableApps() }
                if (!isFinishing && !isDestroyed) appState.value = loadedApps
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Keep the previous app list if the package manager fails temporarily.
            }
        }
    }

    private val notificationRefreshReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshLaunchableApps()
        }
    }

    private val wallpaperPickerLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                activityScope.launch {
                    try {
                        val file = withContext(Dispatchers.IO) { copyWallpaperToAppStorage(uri) }
                        wallpaperState.value = file.absolutePath
                        getSharedPreferences("aura_preferences", MODE_PRIVATE).edit()
                            .putString("wallpaper_path", file.absolutePath)
                            .apply()
                        Toast.makeText(this@MainActivity, "Papel de parede aplicado.", Toast.LENGTH_SHORT).show()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        Toast.makeText(
                            this@MainActivity,
                            error.message ?: "Não foi possível usar essa imagem.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }

    private val widgetBindLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val widgetId = pendingWidgetId ?: return@registerForActivityResult
            if (result.resultCode == RESULT_OK) {
                finishWidgetConfiguration(widgetId)
            } else {
                widgetHostController.deleteWidget(widgetId)
                pendingWidgetId = null
            }
        }

    private val widgetConfigLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val widgetId = pendingWidgetId ?: return@registerForActivityResult
            if (result.resultCode == RESULT_OK) {
                keepWidget(widgetId)
            } else {
                widgetHostController.deleteWidget(widgetId)
                pendingWidgetId = null
            }
        }

    private val backupExportLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
            if (uri != null) {
                runCatching {
                    val prefs = getSharedPreferences("aura_preferences", MODE_PRIVATE)
                    val snapshot = LauncherBackup(
                        favorites = prefs.getStringSet("favorites", emptySet()) ?: emptySet(),
                        hidden = prefs.getStringSet("hidden", emptySet()) ?: emptySet(),
                        recentPackages = readPackageList(prefs.getString("recent_order", "")),
                        foldersJson = prefs.getString("folders", "[]") ?: "[]",
                        iconSizeDp = prefs.getFloat("icon_size", 54f),
                        columns = prefs.getInt("columns", 4),
                        labelsEnabled = prefs.getBoolean("labels", true),
                        accentIndex = prefs.getInt("accent", 0),
                        themeMode = prefs.getInt("theme", 0),
                        sortMode = prefs.getInt("sort_mode", 0),
                        animationsEnabled = prefs.getBoolean("animations", true),
                        iconPackPackage = prefs.getString("icon_pack", null),
                        swipeLeftGestureEnabled = prefs.getBoolean("gesture_swipe_left", true),
                        doubleTapSettingsEnabled = prefs.getBoolean("gesture_double_tap", true),
                        widgetPlacementsJson = prefs.getString("widget_placements", "[]") ?: "[]",
                        homeShortcutsJson = prefs.getString("home_shortcuts", "[]") ?: "[]"
                    )
                    val output = contentResolver.openOutputStream(uri)
                        ?: error("Não foi possível abrir o arquivo de destino.")
                    output.bufferedWriter(Charsets.UTF_8).use { it.write(LauncherBackupCodec.encode(snapshot)) }
                }.onSuccess {
                    Toast.makeText(this, "Backup AURA exportado.", Toast.LENGTH_LONG).show()
                }.onFailure {
                    Toast.makeText(this, "Falha ao exportar: ${it.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

    private val backupImportLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                runCatching {
                    val raw = contentResolver.openInputStream(uri)?.use { it.readUtf8Limited(512_000) }
                        ?: error("Não foi possível ler o arquivo.")
                    val snapshot = LauncherBackupCodec.decode(raw)
                    getSharedPreferences("aura_preferences", MODE_PRIVATE).edit()
                        .putStringSet("favorites", snapshot.favorites)
                        .putStringSet("hidden", snapshot.hidden)
                        .putString("recent_order", snapshot.recentPackages.joinToString("|"))
                        .putString("folders", snapshot.foldersJson)
                        .putFloat("icon_size", snapshot.iconSizeDp)
                        .putInt("columns", snapshot.columns)
                        .putBoolean("labels", snapshot.labelsEnabled)
                        .putInt("accent", snapshot.accentIndex)
                        .putInt("theme", snapshot.themeMode)
                        .putInt("sort_mode", snapshot.sortMode)
                        .putBoolean("animations", snapshot.animationsEnabled)
                        .putString("icon_pack", snapshot.iconPackPackage)
                        .putBoolean("gesture_swipe_left", snapshot.swipeLeftGestureEnabled)
                        .putBoolean("gesture_double_tap", snapshot.doubleTapSettingsEnabled)
                        .putString("widget_placements", snapshot.widgetPlacementsJson)
                        .putString("home_shortcuts", snapshot.homeShortcutsJson)
                        .apply()
                }.onSuccess {
                    Toast.makeText(this, "Backup restaurado. Atualizando o AURA…", Toast.LENGTH_LONG).show()
                    recreate()
                }.onFailure {
                    Toast.makeText(this, "Backup inválido: ${it.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

    private val homeRoleLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            val selected = isDefaultHome()
            if (::defaultState.isInitialized) defaultState.value = selected
            Toast.makeText(
                this,
                if (selected) "AURA definido como tela inicial." else "O Android não alterou o app de início.",
                Toast.LENGTH_LONG
            ).show()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        window.statusBarColor = android.graphics.Color.rgb(17, 16, 15)
        @Suppress("DEPRECATION")
        window.navigationBarColor = android.graphics.Color.rgb(17, 16, 15)

        widgetHostController = AuraWidgetHostController(this)
        val prefs = getSharedPreferences("aura_preferences", MODE_PRIVATE)
        widgetPlacementsState = mutableStateOf(
            WidgetPlacementCodec.decode(prefs.getString("widget_placements", "[]"))
                .filter { widgetHostController.manager.getAppWidgetInfo(it.widgetId) != null }
        )
        prefs.edit().putString("widget_placements", WidgetPlacementCodec.encode(widgetPlacementsState.value)).apply()
        defaultState = mutableStateOf(isDefaultHome())
        notificationAccessState = mutableStateOf(isNotificationAccessEnabled())
        if (!notificationAccessState.value) {
            prefs.edit().remove(AuraNotificationListenerService.KEY_COUNTS).apply()
        }
        appState = mutableStateOf(emptyList())
        val savedWallpaper = File(filesDir, WALLPAPER_FILE_NAME)
        val previousWallpaper = File(filesDir, "$WALLPAPER_FILE_NAME.bak")
        if (!savedWallpaper.isFile && previousWallpaper.isFile) {
            previousWallpaper.renameTo(savedWallpaper)
        } else {
            previousWallpaper.delete()
        }
        wallpaperState = mutableStateOf(savedWallpaper.takeIf { it.isFile }?.absolutePath)

        setContent {
            AuraTheme {
                AuraLauncher(
                    apps = appState.value,
                    isDefaultLauncher = defaultState.value,
                    initialFavorites = prefs.getStringSet("favorites", emptySet()) ?: emptySet(),
                    initialHidden = prefs.getStringSet("hidden", emptySet()) ?: emptySet(),
                    initialRecents = readPackageList(prefs.getString("recent_order", "")),
                    initialFolders = decodeFolders(prefs.getString("folders", "[]")),
                    initialHomeShortcuts = HomeShortcutCodec.decode(prefs.getString("home_shortcuts", "[]")),
                    initialWallpaperPath = wallpaperState.value,
                    onPickWallpaper = { wallpaperPickerLauncher.launch(arrayOf("image/*")) },
                    onRemoveWallpaper = {
                        File(filesDir, WALLPAPER_FILE_NAME).delete()
                        prefs.edit().remove("wallpaper_path").apply()
                        wallpaperState.value = null
                    },
                    iconSize = prefs.getFloat("icon_size", 54f),
                    columns = prefs.getInt("columns", 4),
                    labels = prefs.getBoolean("labels", true),
                    accent = prefs.getInt("accent", 0),
                    theme = prefs.getInt("theme", 0),
                    sortMode = prefs.getInt("sort_mode", 0),
                    animations = prefs.getBoolean("animations", true),
                    initialWidgetPlacements = widgetPlacementsState.value,
                    widgetController = widgetHostController,
                    widgetProviders = widgetHostController.manager.installedProviders.orEmpty(),
                    installedIconPacks = IconPackManager.installedPacks(this),
                    initialIconPack = prefs.getString("icon_pack", null),
                    notificationAccessEnabled = notificationAccessState.value,
                    swipeLeftEnabled = prefs.getBoolean("gesture_swipe_left", true),
                    doubleTapSettingsEnabled = prefs.getBoolean("gesture_double_tap", true),
                    onSetDefault = ::requestDefaultLauncher,
                    onAddWidget = ::beginWidgetSetup,
                    onRemoveWidget = ::removeWidget,
                    onMoveWidget = ::moveWidget,
                    onSelectIconPack = ::saveIconPack,
                    onOpenNotificationSettings = ::openNotificationAccessSettings,
                    onSaveGestureSettings = { swipe, doubleTap ->
                        prefs.edit().putBoolean("gesture_swipe_left", swipe)
                            .putBoolean("gesture_double_tap", doubleTap).apply()
                    },
                    onExportBackup = { backupExportLauncher.launch("AURA-backup.aura") },
                    onImportBackup = { backupImportLauncher.launch(arrayOf("application/octet-stream", "text/plain", "*/*")) },
                    onLaunch = { packageName ->
                        val recent = readPackageList(prefs.getString("recent_order", ""))
                            .filterNot { it == packageName }
                            .let { listOf(packageName) + it }
                            .take(12)
                        prefs.edit().putString("recent_order", recent.joinToString("|")).apply()
                        packageManager.getLaunchIntentForPackage(packageName)?.let(::startActivity)
                    },
                    onAppInfo = { packageName ->
                        startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                .setData(Uri.parse("package:$packageName"))
                        )
                    },
                    onUninstall = { packageName ->
                        runCatching {
                            startActivity(
                                Intent(Intent.ACTION_UNINSTALL_PACKAGE, Uri.parse("package:$packageName"))
                            )
                        }.onFailure {
                            Toast.makeText(
                                this@MainActivity,
                                "Não foi possível abrir a confirmação de desinstalação do Android.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    },
                    onSaveFavorites = { value ->
                        prefs.edit().putStringSet("favorites", value.toSet()).apply()
                    },
                    onSaveHidden = { value ->
                        prefs.edit().putStringSet("hidden", value.toSet()).apply()
                    },
                    onSaveFolders = { value ->
                        prefs.edit().putString("folders", encodeFolders(value)).apply()
                    },
                    onSaveHomeShortcuts = { value ->
                        prefs.edit().putString("home_shortcuts", HomeShortcutCodec.encode(value)).apply()
                    },
                    onSaveSettings = { size, count, showLabels, color, themeMode, order, motion ->
                        prefs.edit()
                            .putFloat("icon_size", size)
                            .putInt("columns", count)
                            .putBoolean("labels", showLabels)
                            .putInt("accent", color)
                            .putInt("theme", themeMode)
                            .putInt("sort_mode", order)
                            .putBoolean("animations", motion)
                            .apply()
                    }
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (::widgetHostController.isInitialized) runCatching { widgetHostController.startListening() }
        val filter = IntentFilter(AuraNotificationListenerService.ACTION_COUNTS_CHANGED)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(notificationRefreshReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(notificationRefreshReceiver, filter)
        }
    }

    override fun onStop() {
        runCatching { unregisterReceiver(notificationRefreshReceiver) }
        if (::widgetHostController.isInitialized) widgetHostController.stopListening()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        if (::defaultState.isInitialized) defaultState.value = isDefaultHome()
        if (::notificationAccessState.isInitialized) {
            val hasNotificationAccess = isNotificationAccessEnabled()
            notificationAccessState.value = hasNotificationAccess
            if (!hasNotificationAccess) {
                getSharedPreferences("aura_preferences", MODE_PRIVATE).edit()
                    .remove(AuraNotificationListenerService.KEY_COUNTS)
                    .apply()
            }
        }
        refreshLaunchableApps()
    }

    override fun onDestroy() {
        activityScope.cancel()
        super.onDestroy()
    }

    private fun copyWallpaperToAppStorage(uri: Uri): File {
        val temporary = File(filesDir, "$WALLPAPER_FILE_NAME.tmp")
        try {
            val input = contentResolver.openInputStream(uri)
                ?: error("Não foi possível abrir a imagem selecionada.")
            var copiedBytes = 0L
            input.use { source ->
                temporary.outputStream().buffered().use { target ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        val count = source.read(buffer)
                        if (count < 0) break
                        copiedBytes += count
                        require(copiedBytes <= MAX_WALLPAPER_BYTES) {
                            "A imagem é muito grande. Escolha uma foto de até 20 MB."
                        }
                        target.write(buffer, 0, count)
                    }
                }
            }
            require(copiedBytes > 0L) { "O arquivo selecionado está vazio." }

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(temporary.absolutePath, bounds)
            require(bounds.outWidth in 1..20_000 && bounds.outHeight in 1..20_000) {
                "O arquivo selecionado não é uma imagem compatível."
            }

            var sampleSize = 1
            while (bounds.outWidth / sampleSize > 1920 || bounds.outHeight / sampleSize > 1920) {
                sampleSize *= 2
            }
            val verification = BitmapFactory.decodeFile(
                temporary.absolutePath,
                BitmapFactory.Options().apply { inSampleSize = sampleSize }
            ) ?: error("Não foi possível decodificar essa imagem.")
            verification.recycle()

            val destination = File(filesDir, WALLPAPER_FILE_NAME)
            val previous = File(filesDir, "$WALLPAPER_FILE_NAME.bak")
            previous.delete()
            val hadPrevious = destination.isFile
            if (hadPrevious && !destination.renameTo(previous)) {
                error("Não foi possível preparar a troca do papel de parede.")
            }
            try {
                if (!temporary.renameTo(destination)) {
                    temporary.copyTo(destination, overwrite = true)
                    temporary.delete()
                }
                previous.delete()
                return destination
            } catch (error: Exception) {
                destination.delete()
                if (hadPrevious) previous.renameTo(destination)
                throw error
            }
        } catch (error: Exception) {
            temporary.delete()
            throw error
        }
    }

    private fun beginWidgetSetup(provider: AppWidgetProviderInfo) {
        val widgetId = widgetHostController.allocateWidgetId()
        pendingWidgetId = widgetId
        val bound = runCatching {
            widgetHostController.manager.bindAppWidgetIdIfAllowed(widgetId, provider.provider)
        }.getOrDefault(false)
        if (bound) {
            finishWidgetConfiguration(widgetId)
        } else {
            val bindIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider.provider)
            widgetBindLauncher.launch(bindIntent)
        }
    }

    private fun finishWidgetConfiguration(widgetId: Int) {
        val info = widgetHostController.manager.getAppWidgetInfo(widgetId)
        val configure = info?.configure
        if (configure == null) {
            keepWidget(widgetId)
            return
        }
        val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
            .setComponent(configure)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        widgetConfigLauncher.launch(intent)
    }

    private fun keepWidget(widgetId: Int) {
        val current = widgetPlacementsState.value
        val next = current + WidgetPlacement(widgetId = widgetId, xDp = 8f, yDp = 8f + current.size * 190f)
        widgetPlacementsState.value = next
        getSharedPreferences("aura_preferences", MODE_PRIVATE).edit()
            .putString("widget_placements", WidgetPlacementCodec.encode(next)).apply()
        pendingWidgetId = null
    }

    private fun removeWidget(widgetId: Int) {
        widgetHostController.deleteWidget(widgetId)
        val next = widgetPlacementsState.value.filterNot { it.widgetId == widgetId }
        widgetPlacementsState.value = next
        getSharedPreferences("aura_preferences", MODE_PRIVATE).edit()
            .putString("widget_placements", WidgetPlacementCodec.encode(next)).apply()
    }

    private fun moveWidget(placement: WidgetPlacement) {
        val next = widgetPlacementsState.value.map {
            if (it.widgetId == placement.widgetId) placement else it
        }
        widgetPlacementsState.value = next
        getSharedPreferences("aura_preferences", MODE_PRIVATE).edit()
            .putString("widget_placements", WidgetPlacementCodec.encode(next)).apply()
        runCatching {
            widgetHostController.manager.updateAppWidgetOptions(
                placement.widgetId,
                Bundle().apply {
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, placement.widthDp.toInt())
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, placement.widthDp.toInt())
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, placement.heightDp.toInt())
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, placement.heightDp.toInt())
                }
            )
        }
    }

    private fun saveIconPack(packageName: String?) {
        getSharedPreferences("aura_preferences", MODE_PRIVATE).edit().putString("icon_pack", packageName).apply()
        refreshLaunchableApps()
    }

    private fun isNotificationAccessEnabled(): Boolean =
        Secure.getString(contentResolver, "enabled_notification_listeners")
            ?.split(':')
            ?.mapNotNull { ComponentName.unflattenFromString(it) }
            ?.any { it.packageName == packageName } == true

    private fun openNotificationAccessSettings() {
        runCatching { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
            .onFailure {
                Toast.makeText(this, "Não foi possível abrir as permissões de notificações.", Toast.LENGTH_LONG).show()
            }
    }

    private fun isDefaultHome(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager?.isRoleHeld(RoleManager.ROLE_HOME) == true) return true
        }
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = packageManager.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolved?.activityInfo?.packageName == packageName
    }

    private fun requestDefaultLauncher() {
        if (isDefaultHome()) {
            if (::defaultState.isInitialized) defaultState.value = true
            Toast.makeText(this, "O AURA já é sua tela inicial.", Toast.LENGTH_SHORT).show()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                try {
                    homeRoleLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME))
                    return
                } catch (_: ActivityNotFoundException) {
                    // Fall through to the system settings fallback.
                } catch (_: SecurityException) {
                    // Fall through to the system settings fallback.
                }
            }
        }
        openHomeAppSettings()
    }

    private fun openHomeAppSettings() {
        val actions = listOf(
            Settings.ACTION_HOME_SETTINGS,
            Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS,
            Settings.ACTION_SETTINGS
        )
        for (action in actions) {
            try {
                startActivity(Intent(action))
                Toast.makeText(
                    this,
                    "Em Aplicativo de início, selecione AURA Launcher e escolha Sempre.",
                    Toast.LENGTH_LONG
                ).show()
                return
            } catch (_: ActivityNotFoundException) {
                // Try the next system settings entry point.
            } catch (_: SecurityException) {
                // Try the next system settings entry point.
            }
        }
        Toast.makeText(
            this,
            "Abra Configurações > Aplicativos padrão > App de início e selecione AURA Launcher.",
            Toast.LENGTH_LONG
        ).show()
    }

    @Suppress("DEPRECATION")
    private fun loadLaunchableApps(): List<LaunchableApp> {
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)
            .mapNotNull { info ->
                val activityInfo = info.activityInfo ?: return@mapNotNull null
                val prefs = getSharedPreferences("aura_preferences", MODE_PRIVATE)
                val packageName = activityInfo.packageName
                val iconPack = prefs.getString("icon_pack", null)
                val counts = runCatching {
                    JSONObject(prefs.getString(AuraNotificationListenerService.KEY_COUNTS, "{}") ?: "{}")
                        .optInt(packageName, 0)
                }.getOrDefault(0)
                val appIcon = IconPackManager.loadDrawable(
                    this, packageName, activityInfo.name, iconPack
                ) ?: info.loadIcon(packageManager)
                LaunchableApp(
                    title = info.loadLabel(packageManager).toString(),
                    packageName = packageName,
                    icon = appIcon,
                    activityClassName = activityInfo.name,
                    notificationCount = counts
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.title.lowercase(Locale.getDefault()) }
    }
}

private fun readPackageList(raw: String?): List<String> =
    raw.orEmpty().split("|").filter { it.isNotBlank() }

private fun encodeFolders(folders: List<AppFolder>): String {
    val array = JSONArray()
    folders.forEach { folder ->
        array.put(
            JSONObject()
                .put("name", folder.name)
                .put("packages", JSONArray(folder.packages))
        )
    }
    return array.toString()
}

private fun decodeFolders(raw: String?): List<AppFolder> {
    return try {
        val array = JSONArray(raw ?: "[]")
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val packageArray = item.optJSONArray("packages") ?: JSONArray()
            val packages = (0 until packageArray.length())
                .map { packageArray.optString(it) }
                .filter { it.isNotBlank() }
            val name = item.optString("name").trim()
            if (name.isBlank() || packages.isEmpty()) null else AppFolder(name, packages)
        }
    } catch (_: Exception) {
        emptyList()
    }
}

private val AuraBlack = Color(0xFF070B14)
private val AuraPanel = Color(0xFF111A2B)
private val AuraPanelRaised = Color(0xFF18243A)
private val AuraMuted = Color(0xFF91A4C2)
private val AuraWhite = Color(0xFFF2F7FF)
private val AccentOptions = listOf(
    Color(0xFF45E6FF),
    Color(0xFF8B7CFF),
    Color(0xFFB6FF5C),
    Color(0xFFFF5EC4),
    Color(0xFF4F9CFF)
)

@Composable
private fun AuraTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}

@Composable
private fun AuraLauncher(
    apps: List<LaunchableApp>,
    isDefaultLauncher: Boolean,
    initialFavorites: Set<String>,
    initialHidden: Set<String>,
    initialRecents: List<String>,
    initialFolders: List<AppFolder>,
    initialHomeShortcuts: List<HomeShortcutPlacement>,
    iconSize: Float,
    columns: Int,
    labels: Boolean,
    accent: Int,
    theme: Int,
    sortMode: Int,
    animations: Boolean,
    initialWidgetPlacements: List<WidgetPlacement>,
    widgetController: AuraWidgetHostController,
    widgetProviders: List<AppWidgetProviderInfo>,
    installedIconPacks: List<InstalledIconPack>,
    initialIconPack: String?,
    notificationAccessEnabled: Boolean,
    swipeLeftEnabled: Boolean,
    doubleTapSettingsEnabled: Boolean,
    onSetDefault: () -> Unit,
    onAddWidget: (AppWidgetProviderInfo) -> Unit,
    onRemoveWidget: (Int) -> Unit,
    onMoveWidget: (WidgetPlacement) -> Unit,
    onSelectIconPack: (String?) -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onSaveGestureSettings: (Boolean, Boolean) -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    onLaunch: (String) -> Unit,
    onAppInfo: (String) -> Unit,
    onUninstall: (String) -> Unit,
    onSaveFavorites: (Set<String>) -> Unit,
    onSaveHidden: (Set<String>) -> Unit,
    onSaveFolders: (List<AppFolder>) -> Unit,
    onSaveHomeShortcuts: (List<HomeShortcutPlacement>) -> Unit,
    onSaveSettings: (Float, Int, Boolean, Int, Int, Int, Boolean) -> Unit,
    initialWallpaperPath: String?,
    onPickWallpaper: () -> Unit,
    onRemoveWallpaper: () -> Unit
) {
    var favorites by remember { mutableStateOf(initialFavorites) }
    var hidden by remember { mutableStateOf(initialHidden) }
    var recents by remember { mutableStateOf(initialRecents) }
    var folders by remember { mutableStateOf(initialFolders) }
    var homeShortcuts by remember { mutableStateOf(initialHomeShortcuts) }
    var currentIconSize by remember { mutableStateOf(iconSize.coerceIn(42f, 72f)) }
    var currentColumns by remember { mutableStateOf(columns.coerceIn(3, 6)) }
    var showLabels by remember { mutableStateOf(labels) }
    var accentIndex by remember { mutableStateOf(accent.coerceIn(0, AccentOptions.lastIndex)) }
    var themeMode by remember { mutableStateOf(theme.coerceIn(0, 1)) }
    var currentSort by remember { mutableStateOf(sortMode.coerceIn(0, 1)) }
    var motionEnabled by remember { mutableStateOf(animations) }
    var isDefault by remember { mutableStateOf(isDefaultLauncher) }
    var showDrawer by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var drawerTab by remember { mutableStateOf(0) }
    var search by remember { mutableStateOf("") }
    var selectedApp by remember { mutableStateOf<LaunchableApp?>(null) }
    var activeFolder by remember { mutableStateOf<AppFolder?>(null) }
    var showFolderBuilder by remember { mutableStateOf(false) }
    var clockNow by remember { mutableStateOf(System.currentTimeMillis()) }
    var widgetPlacements by remember { mutableStateOf(initialWidgetPlacements) }
    var showWidgetPicker by remember { mutableStateOf(false) }
    var selectedIconPack by remember { mutableStateOf(initialIconPack) }
    var swipeLeftGestureEnabled by remember { mutableStateOf(swipeLeftEnabled) }
    var doubleTapGestureEnabled by remember { mutableStateOf(doubleTapSettingsEnabled) }
    var editHomeMode by remember { mutableStateOf(false) }
    var wallpaperPath by remember { mutableStateOf(initialWallpaperPath) }

    LaunchedEffect(initialWidgetPlacements) { widgetPlacements = initialWidgetPlacements }
    LaunchedEffect(initialHomeShortcuts) { homeShortcuts = initialHomeShortcuts }
    LaunchedEffect(initialWallpaperPath) { wallpaperPath = initialWallpaperPath }
    LaunchedEffect(initialIconPack) { selectedIconPack = initialIconPack }
    LaunchedEffect(swipeLeftEnabled, doubleTapSettingsEnabled) {
        swipeLeftGestureEnabled = swipeLeftEnabled
        doubleTapGestureEnabled = doubleTapSettingsEnabled
    }

    BackHandler(enabled = showSettings || showDrawer) {
        if (showSettings) {
            showSettings = false
        } else {
            showDrawer = false
            search = ""
            drawerTab = 0
        }
    }

    LaunchedEffect(isDefaultLauncher) { isDefault = isDefaultLauncher }
    LaunchedEffect(Unit) {
        while (true) {
            clockNow = System.currentTimeMillis()
            delay(1_000L)
        }
    }

    val accentColor = AccentOptions[accentIndex]
    val background = if (themeMode == 1) {
        Brush.verticalGradient(listOf(Color.Black, Color(0xFF050505), Color.Black))
    } else {
        Brush.verticalGradient(listOf(Color(0xFF132642), AuraBlack, Color(0xFF050810)))
    }
    val appMap = remember(apps) { apps.associateBy { it.packageName } }
    val favoriteApps = apps.filter { it.packageName in favorites && it.packageName !in hidden }
    val recentApps = recents.mapNotNull { appMap[it] }.filter { it.packageName !in hidden }.take(5)
    val orderedApps = remember(apps, currentSort, recents) {
        if (currentSort == 1) {
            val order = recents.withIndex().associate { it.value to it.index }
            apps.sortedWith(compareBy<LaunchableApp> { order[it.packageName] ?: Int.MAX_VALUE }
                .thenBy { it.title.lowercase(Locale.getDefault()) })
        } else apps.sortedBy { it.title.lowercase(Locale.getDefault()) }
    }
    val filteredDrawerApps = orderedApps.filter { app ->
        val matchesSearch = app.title.contains(search, ignoreCase = true)
        val matchesTab = when (drawerTab) {
            1 -> app.packageName in favorites && app.packageName !in hidden
            2 -> app.packageName in hidden
            else -> app.packageName !in hidden
        }
        matchesSearch && matchesTab
    }

    fun toggleFavorite(app: LaunchableApp) {
        favorites = favorites.toMutableSet().apply {
            if (contains(app.packageName)) remove(app.packageName) else add(app.packageName)
        }
        onSaveFavorites(favorites)
    }

    fun toggleHidden(app: LaunchableApp) {
        hidden = hidden.toMutableSet().apply {
            if (contains(app.packageName)) remove(app.packageName) else add(app.packageName)
        }
        if (app.packageName in hidden) {
            favorites = favorites - app.packageName
            onSaveFavorites(favorites)
            homeShortcuts = homeShortcuts.filterNot { it.packageName == app.packageName }
            onSaveHomeShortcuts(homeShortcuts)
        }
        onSaveHidden(hidden)
    }

    fun toggleHomeShortcut(app: LaunchableApp) {
        homeShortcuts = if (homeShortcuts.any { it.packageName == app.packageName }) {
            homeShortcuts.filterNot { it.packageName == app.packageName }
        } else {
            homeShortcuts + HomeShortcutPlacement(
                packageName = app.packageName,
                xDp = 12f,
                yDp = 12f + homeShortcuts.size * 116f
            )
        }
        onSaveHomeShortcuts(homeShortcuts)
    }

    fun launchApp(app: LaunchableApp) {
        recents = (listOf(app.packageName) + recents.filterNot { it == app.packageName }).take(12)
        onLaunch(app.packageName)
    }

    Surface(modifier = Modifier.fillMaxSize(), color = AuraBlack) {
        Box(modifier = Modifier.fillMaxSize().background(background).windowInsetsPadding(WindowInsets.safeDrawing)) {
            when {
                showSettings -> SettingsScreen(
                    iconSize = currentIconSize,
                    columns = currentColumns,
                    labels = showLabels,
                    accentIndex = accentIndex,
                    themeMode = themeMode,
                    sortMode = currentSort,
                    animations = motionEnabled,
                    isDefault = isDefault,
                    hiddenCount = hidden.size,
                    folders = folders,
                    wallpaperPath = wallpaperPath,
                    onPickWallpaper = onPickWallpaper,
                    onRemoveWallpaper = {
                        wallpaperPath = null
                        onRemoveWallpaper()
                    },
                    widgetCount = widgetPlacements.size,
                    installedIconPacks = installedIconPacks,
                    selectedIconPack = selectedIconPack,
                    notificationAccessEnabled = notificationAccessEnabled,
                    swipeLeftEnabled = swipeLeftGestureEnabled,
                    doubleTapSettingsEnabled = doubleTapGestureEnabled,
                    onBack = { showSettings = false },
                    onAddWidget = { showWidgetPicker = true },
                    onSelectIconPack = { selectedIconPack = it; onSelectIconPack(it) },
                    onOpenNotificationSettings = onOpenNotificationSettings,
                    onSwipeLeftGesture = { swipeLeftGestureEnabled = it },
                    onDoubleTapGesture = { doubleTapGestureEnabled = it },
                    onIconSize = { currentIconSize = it },
                    onColumns = { currentColumns = it },
                    onLabels = { showLabels = it },
                    onAccent = { accentIndex = it },
                    onTheme = { themeMode = it },
                    onSort = { currentSort = it },
                    onAnimations = { motionEnabled = it },
                    onSetDefault = onSetDefault,
                    onExportBackup = onExportBackup,
                    onImportBackup = onImportBackup,
                    onCreateFolder = { showFolderBuilder = true },
                    onOpenHidden = { drawerTab = 2; search = ""; showSettings = false; showDrawer = true },
                    onRemoveFolder = { folder ->
                        folders = folders.filterNot { it == folder }
                        onSaveFolders(folders)
                    },
                    onSave = {
                        onSaveSettings(
                            currentIconSize, currentColumns, showLabels, accentIndex,
                            themeMode, currentSort, motionEnabled
                        )
                        onSaveGestureSettings(swipeLeftGestureEnabled, doubleTapGestureEnabled)
                        showSettings = false
                    }
                )
                showDrawer -> DrawerScreen(
                    apps = filteredDrawerApps,
                    totalApps = apps.size,
                    tab = drawerTab,
                    search = search,
                    iconSize = currentIconSize,
                    columns = currentColumns,
                    labels = showLabels,
                    accent = accentColor,
                    onTab = { drawerTab = it },
                    onSearch = { search = it },
                    onBack = { showDrawer = false; search = ""; drawerTab = 0 },
                    onLaunch = ::launchApp,
                    onAppMenu = { selectedApp = it }
                )
                else -> HomeScreen(
                    time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(clockNow)),
                    date = SimpleDateFormat("EEEE, d 'de' MMMM", Locale("pt", "BR"))
                        .format(Date(clockNow)).replaceFirstChar { it.uppercase(Locale("pt", "BR")) },
                    favorites = favoriteApps,
                    recentApps = recentApps,
                    folders = folders,
                    wallpaperPath = wallpaperPath,
                    homeShortcutItems = apps.filterNot { it.packageName in hidden }.map {
                        HomeShortcutItem(it.packageName, it.title, it.icon)
                    },
                    homeShortcuts = homeShortcuts,
                    iconSize = currentIconSize,
                    labels = showLabels,
                    accent = accentColor,
                    isDefault = isDefault,
                    animations = motionEnabled,
                    onOpenDrawer = { showDrawer = true; drawerTab = 0 },
                    onOpenFavorites = { showDrawer = true; drawerTab = 1 },
                    onOpenSettings = { showSettings = true },
                    onSetDefault = onSetDefault,
                    onLaunch = ::launchApp,
                    onFolder = { activeFolder = it },
                    widgetPlacements = widgetPlacements,
                    widgetController = widgetController,
                    editMode = editHomeMode,
                    swipeLeftEnabled = swipeLeftGestureEnabled,
                    doubleTapSettingsEnabled = doubleTapGestureEnabled,
                    onToggleEditMode = { editHomeMode = !editHomeMode },
                    onOpenWidgetPicker = { showWidgetPicker = true },
                    onOpenLibraryForShortcut = { showDrawer = true; drawerTab = 0 },
                    onMoveHomeShortcut = { placement ->
                        homeShortcuts = homeShortcuts.map {
                            if (it.packageName == placement.packageName) placement else it
                        }
                        onSaveHomeShortcuts(homeShortcuts)
                    },
                    onRemoveHomeShortcut = { packageName ->
                        homeShortcuts = homeShortcuts.filterNot { it.packageName == packageName }
                        onSaveHomeShortcuts(homeShortcuts)
                    },
                    onMoveWidget = { placement ->
                        widgetPlacements = widgetPlacements.map {
                            if (it.widgetId == placement.widgetId) placement else it
                        }
                        onMoveWidget(placement)
                    },
                    onRemoveWidget = { widgetId ->
                        widgetPlacements = widgetPlacements.filterNot { it.widgetId == widgetId }
                        onRemoveWidget(widgetId)
                    }
                )
            }
        }
    }

    selectedApp?.let { app ->
        AppActionsDialog(
            app = app,
            isFavorite = app.packageName in favorites,
            isHidden = app.packageName in hidden,
            isOnHome = homeShortcuts.any { it.packageName == app.packageName },
            onDismiss = { selectedApp = null },
            onAddToHome = { toggleHomeShortcut(app); selectedApp = null },
            onFavorite = { toggleFavorite(app); selectedApp = null },
            onHide = { toggleHidden(app); selectedApp = null },
            onInfo = { selectedApp = null; onAppInfo(app.packageName) },
            onUninstall = { selectedApp = null; onUninstall(app.packageName) }
        )
    }

    activeFolder?.let { folder ->
        FolderContentsDialog(
            folder = folder,
            apps = folder.packages.mapNotNull { appMap[it] },
            accent = accentColor,
            onDismiss = { activeFolder = null },
            onLaunch = { app -> activeFolder = null; launchApp(app) }
        )
    }

    if (showWidgetPicker) {
        WidgetPickerDialog(
            providers = widgetProviders,
            packageManager = androidx.compose.ui.platform.LocalContext.current.packageManager,
            onDismiss = { showWidgetPicker = false },
            onChoose = { provider ->
                showWidgetPicker = false
                onAddWidget(provider)
            }
        )
    }

    if (showFolderBuilder) {
        FolderBuilderDialog(
            apps = apps.filter { it.packageName !in hidden },
            onDismiss = { showFolderBuilder = false },
            onCreate = { name, packages ->
                folders = folders + AppFolder(name, packages)
                onSaveFolders(folders)
                showFolderBuilder = false
                showSettings = false
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeScreen(
    time: String,
    date: String,
    favorites: List<LaunchableApp>,
    recentApps: List<LaunchableApp>,
    folders: List<AppFolder>,
    homeShortcutItems: List<HomeShortcutItem>,
    homeShortcuts: List<HomeShortcutPlacement>,
    iconSize: Float,
    labels: Boolean,
    accent: Color,
    isDefault: Boolean,
    animations: Boolean,
    onOpenDrawer: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenSettings: () -> Unit,
    onSetDefault: () -> Unit,
    onLaunch: (LaunchableApp) -> Unit,
    onFolder: (AppFolder) -> Unit,
    widgetPlacements: List<WidgetPlacement>,
    widgetController: AuraWidgetHostController,
    editMode: Boolean,
    swipeLeftEnabled: Boolean,
    doubleTapSettingsEnabled: Boolean,
    onToggleEditMode: () -> Unit,
    onOpenWidgetPicker: () -> Unit,
    onOpenLibraryForShortcut: () -> Unit,
    onMoveHomeShortcut: (HomeShortcutPlacement) -> Unit,
    onRemoveHomeShortcut: (String) -> Unit,
    onMoveWidget: (WidgetPlacement) -> Unit,
    onRemoveWidget: (Int) -> Unit,
    wallpaperPath: String?
) {
    val hour = time.substringBefore(":").toIntOrNull() ?: 12
    val greeting = when (hour) {
        in 5..11 -> "Bom dia"
        in 12..17 -> "Boa tarde"
        else -> "Boa noite"
    }

    val wallpaperBitmap = rememberWallpaperBitmap(wallpaperPath)
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFF050812))
    ) {
        if (wallpaperBitmap != null) {
            Image(
                bitmap = wallpaperBitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF173A58), Color(0xFF0A1424), Color(0xFF050812)),
                        radius = 1050f
                    )
                )
            )
        }
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    colors = listOf(Color(0x66050812), Color(0x99050812), Color(0xE6050812))
                )
            )
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 72.dp, end = 8.dp)
                .size(210.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(accent.copy(alpha = 0.10f), Color.Transparent)
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .pointerInput(swipeLeftEnabled) {
                        var dragX = 0f
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (swipeLeftEnabled && dragX < -85f) onOpenDrawer()
                                dragX = 0f
                            },
                            onDragCancel = { dragX = 0f }
                        ) { change, amount ->
                            dragX += amount
                            change.consume()
                        }
                    }
            ) {
                // Header compacto: identidade e estado do launcher.
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 3.dp, bottom = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(43.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(accent.copy(alpha = 0.28f), Color(0xFF172A42))
                                )
                            )
                            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(15.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("A", color = AuraWhite, fontSize = 22.sp, fontWeight = FontWeight.Black)
                    }
                    Spacer(Modifier.width(11.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "AURA",
                            color = AuraWhite,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 3.2.sp
                        )
                        Text(
                            "PERSONAL INTERFACE  /  04",
                            color = AuraMuted,
                            fontSize = 8.sp,
                            letterSpacing = 1.35.sp
                        )
                    }
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color(0xFF10283A))
                            .border(1.dp, Color(0xFF4BE0A1).copy(alpha = 0.28f), CircleShape)
                            .clickable { onSetDefault() }
                            .padding(horizontal = 11.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(6.dp).clip(CircleShape)
                                .background(if (isDefault) Color(0xFF4BE0A1) else Color(0xFFFFC46B))
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(
                            if (isDefault) "ONLINE" else "ATIVAR",
                            color = if (isDefault) Color(0xFF9AF0C9) else Color(0xFFFFD18D),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }

                // Relógio principal com profundidade, órbitas e data.
                Card(
                    modifier = Modifier.fillMaxWidth().pointerInput(doubleTapSettingsEnabled) {
                        detectTapGestures(
                            onDoubleTap = { if (doubleTapSettingsEnabled) onOpenSettings() },
                            onLongPress = { onToggleEditMode() }
                        )
                    },
                    shape = RoundedCornerShape(30.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF173B57), Color(0xFF12243C), Color(0xFF10172C))
                                )
                            )
                            .border(1.dp, Color.White.copy(alpha = 0.11f), RoundedCornerShape(30.dp))
                            .padding(start = 21.dp, top = 20.dp, end = 18.dp, bottom = 20.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 0.dp)
                                .size(142.dp)
                                .clip(CircleShape)
                                .border(1.dp, accent.copy(alpha = 0.18f), CircleShape)
                                .background(Color.White.copy(alpha = 0.018f))
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 20.dp)
                                .size(102.dp)
                                .clip(CircleShape)
                                .border(1.dp, accent.copy(alpha = 0.30f), CircleShape)
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 50.dp)
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        listOf(accent.copy(alpha = 0.30f), accent.copy(alpha = 0.04f))
                                    )
                                )
                                .border(1.dp, accent.copy(alpha = 0.46f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("✦", color = accent, fontSize = 22.sp)
                        }

                        Column(modifier = Modifier.fillMaxWidth(0.78f)) {
                            Text(
                                "TEMPO LOCAL  /  24H",
                                color = accent,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.8.sp
                            )
                            Spacer(Modifier.height(9.dp))
                            Text(
                                greeting,
                                color = Color(0xFFB8CBE0),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                time,
                                color = Color.White,
                                fontSize = 61.sp,
                                fontWeight = FontWeight.Light,
                                letterSpacing = (-4).sp,
                                maxLines = 1
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                date.uppercase(Locale("pt", "BR")),
                                color = Color(0xFFA5B9D1),
                                fontSize = 8.sp,
                                letterSpacing = 1.2.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            "AURA CORE",
                            modifier = Modifier.align(Alignment.BottomEnd),
                            color = Color.White.copy(alpha = 0.35f),
                            fontSize = 7.sp,
                            letterSpacing = 1.6.sp
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))

                // Barra de busca funcional, integrada ao painel.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF111F33))
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
                        .clickable { onOpenDrawer() }
                        .padding(horizontal = 15.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("⌕", color = accent, fontSize = 25.sp)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Pesquisar no dispositivo", color = AuraWhite, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Text("Aplicativos, ferramentas e atalhos", color = AuraMuted, fontSize = 9.sp)
                    }
                    Text("↗", color = accent, fontSize = 18.sp)
                }

                Spacer(Modifier.height(22.dp))

                // Ações rápidas em blocos independentes.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("CENTRAL DE COMANDO", color = AuraWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
                        Text("Acesso imediato ao seu espaço", color = AuraMuted, fontSize = 9.sp)
                    }
                    Text("3 ATALHOS", color = accent, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
                Spacer(Modifier.height(11.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    HomeCommandTile(
                        symbol = "▦",
                        title = "Aplicativos",
                        detail = "Abrir biblioteca",
                        accent = accent,
                        modifier = Modifier.weight(1f),
                        onClick = onOpenDrawer
                    )
                    HomeCommandTile(
                        symbol = "✧",
                        title = "Favoritos",
                        detail = "${favorites.size} fixados",
                        accent = accent,
                        modifier = Modifier.weight(1f),
                        onClick = onOpenFavorites
                    )
                    HomeCommandTile(
                        symbol = "◈",
                        title = "Personalizar",
                        detail = "Seu visual",
                        accent = accent,
                        modifier = Modifier.weight(1f),
                        onClick = onOpenSettings
                    )
                }

                Spacer(Modifier.height(22.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("ÁREA LIVRE", color = AuraWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
                        Text("Atalhos posicionados por você", color = AuraMuted, fontSize = 9.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onToggleEditMode) {
                            Text(if (editMode) "CONCLUIR" else "EDITAR", color = if (editMode) Color(0xFF9BE4C0) else accent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                        TextButton(onClick = onOpenLibraryForShortcut) {
                            Text("+ APP", color = accent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                if (homeShortcuts.isEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                            .background(Color(0xFF111F33))
                            .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(18.dp))
                            .clickable { onOpenLibraryForShortcut() }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(39.dp).clip(RoundedCornerShape(12.dp)).background(accent.copy(alpha = 0.13f)), contentAlignment = Alignment.Center) {
                            Text("＋", color = accent, fontSize = 20.sp)
                        }
                        Spacer(Modifier.width(11.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Fixe aplicativos na Home", color = AuraWhite, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Text("Abra a biblioteca, segure um app e escolha Adicionar à Home.", color = AuraMuted, fontSize = 9.sp)
                        }
                        Text("›", color = accent, fontSize = 22.sp)
                    }
                } else {
                    HomeShortcutWorkspace(
                        items = homeShortcutItems,
                        placements = homeShortcuts,
                        accent = accent,
                        editable = editMode,
                        onLaunch = { packageName ->
                            homeShortcutItems.firstOrNull { it.packageName == packageName }?.let { item ->
                                onLaunch(LaunchableApp(item.title, item.packageName, item.icon))
                            }
                        },
                        onMove = onMoveHomeShortcut,
                        onRemove = onRemoveHomeShortcut
                    )
                }

                Spacer(Modifier.height(22.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("WIDGETS", color = AuraWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
                        Text(
                            if (widgetPlacements.isEmpty()) "Traga informações para sua Home" else "${widgetPlacements.size} widget(s) no seu espaço",
                            color = AuraMuted,
                            fontSize = 9.sp
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onToggleEditMode) {
                            Text(if (editMode) "CONCLUIR" else "EDITAR", color = if (editMode) Color(0xFF9BE4C0) else accent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                        TextButton(onClick = onOpenWidgetPicker) {
                            Text("+ ADICIONAR", color = accent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                if (editMode) {
                    Text(
                        "MODO DE EDIÇÃO ATIVO · ARRASTE A BARRA SUPERIOR DOS WIDGETS",
                        color = accent,
                        fontSize = 8.sp,
                        letterSpacing = 0.7.sp,
                        modifier = Modifier.padding(top = 5.dp, bottom = 8.dp)
                    )
                }
                if (widgetPlacements.isEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color(0xFF111F33))
                            .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(18.dp))
                            .clickable { onOpenWidgetPicker() }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(39.dp).clip(RoundedCornerShape(12.dp)).background(accent.copy(alpha = 0.13f)),
                            contentAlignment = Alignment.Center
                        ) { Text("▦", color = accent, fontSize = 20.sp) }
                        Spacer(Modifier.width(11.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Adicionar widget do Android", color = AuraWhite, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Text("Calendário, clima, música e outros", color = AuraMuted, fontSize = 9.sp)
                        }
                        Text("›", color = accent, fontSize = 22.sp)
                    }
                } else {
                    WidgetWorkspace(
                        placements = widgetPlacements,
                        controller = widgetController,
                        accent = accent,
                        editable = editMode,
                        onMove = onMoveWidget,
                        onRemove = onRemoveWidget
                    )
                }

                Spacer(Modifier.height(25.dp))

                // Favoritos deixam de ser duas linhas de cartões genéricos e viram dock de ícones.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("SEU DOCK", color = AuraWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
                        Text("O que você usa todos os dias", color = AuraMuted, fontSize = 9.sp)
                    }
                    Text(
                        "EDITAR  ↗",
                        color = accent,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        modifier = Modifier.clickable { onOpenFavorites() }.padding(7.dp)
                    )
                }
                Spacer(Modifier.height(12.dp))
                if (favorites.isEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFF111F33))
                            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
                            .clickable { onOpenDrawer() }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(43.dp).clip(RoundedCornerShape(14.dp)).background(accent.copy(alpha = 0.13f)),
                            contentAlignment = Alignment.Center
                        ) { Text("+", color = accent, fontSize = 25.sp) }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Monte seu dock", color = AuraWhite, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text("Segure um app na biblioteca para fixá-lo aqui.", color = AuraMuted, fontSize = 9.sp)
                        }
                        Text("›", color = accent, fontSize = 23.sp)
                    }
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                        items(favorites.take(8), key = { it.packageName }) { app ->
                            Column(
                                modifier = Modifier.width(68.dp).clip(RoundedCornerShape(17.dp))
                                    .clickable { onLaunch(app) }.padding(vertical = 5.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier.size(61.dp).clip(RoundedCornerShape(20.dp))
                                        .background(
                                            Brush.linearGradient(
                                                listOf(Color(0xFF243B55), Color(0xFF142239))
                                            )
                                        )
                                        .border(1.dp, Color.White.copy(alpha = 0.09f), RoundedCornerShape(20.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AppIconArtwork(app, iconSize.coerceIn(42f, 54f))
                                }
                                if (labels) {
                                    Spacer(Modifier.height(7.dp))
                                    Text(
                                        app.title,
                                        color = Color(0xFFDCE8F6),
                                        fontSize = 9.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                AnimatedVisibility(
                    visible = recentApps.isNotEmpty(),
                    enter = if (animations) fadeIn() else EnterTransition.None,
                    exit = if (animations) fadeOut() else ExitTransition.None
                ) {
                    Column {
                        Spacer(Modifier.height(25.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("RETOMAR", color = AuraWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
                                Text("Acessados recentemente", color = AuraMuted, fontSize = 9.sp)
                            }
                            Text("RECENTES", color = accent, fontSize = 8.sp, letterSpacing = 1.sp)
                        }
                        Spacer(Modifier.height(11.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            items(recentApps, key = { it.packageName }) { app ->
                                Row(
                                    modifier = Modifier.width(145.dp)
                                        .clip(RoundedCornerShape(17.dp))
                                        .background(Color(0xFF111F33))
                                        .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(17.dp))
                                        .clickable { onLaunch(app) }
                                        .padding(horizontal = 11.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AppIconArtwork(app, 35f, RoundedCornerShape(11.dp))
                                    Spacer(Modifier.width(9.dp))
                                    Text(
                                        app.title,
                                        color = AuraWhite,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                if (folders.isNotEmpty()) {
                    Spacer(Modifier.height(25.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("COLEÇÕES", color = AuraWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
                            Text("Sua organização pessoal", color = AuraMuted, fontSize = 9.sp)
                        }
                        Text(
                            "GERENCIAR ↗",
                            color = accent,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { onOpenSettings() }.padding(6.dp)
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    folders.forEach { folder ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                                .clip(RoundedCornerShape(17.dp))
                                .background(Color(0xFF111F33))
                                .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(17.dp))
                                .clickable { onFolder(folder) }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(accent.copy(alpha = 0.13f)),
                                contentAlignment = Alignment.Center
                            ) { Text("▦", color = accent, fontSize = 19.sp) }
                            Spacer(Modifier.width(11.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(folder.name, color = AuraWhite, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                Text("${folder.packages.size} aplicativos", color = AuraMuted, fontSize = 9.sp)
                            }
                            Text("›", color = accent, fontSize = 22.sp)
                        }
                    }
                }

                Spacer(Modifier.height(22.dp))
            }

            // Dock flutuante persistente.
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xE615263B)),
                shape = RoundedCornerShape(25.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 7.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DockAction("▦", "Apps", accent, onOpenDrawer)
                    DockAction("✧", "Fixados", accent, onOpenFavorites)
                    DockAction("◈", "Studio", accent, onOpenSettings)
                }
            }
            Spacer(Modifier.height(5.dp))
            Text(
                if (isDefault) "AURA  /  HOME SYSTEM ACTIVE" else "AURA  /  TOQUE PARA DEFINIR COMO INÍCIO",
                color = AuraMuted.copy(alpha = 0.62f),
                fontSize = 7.sp,
                letterSpacing = 1.1.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally)
                    .clickable { onSetDefault() }
                    .padding(4.dp)
            )
        }
    }
}

@Composable
private fun AppIconArtwork(
    app: LaunchableApp,
    sizeDp: Float,
    shape: RoundedCornerShape = RoundedCornerShape(15.dp)
) {
    val bitmap = remember(app.packageName, app.icon) { app.icon.toImageBitmap() }
    Box(modifier = Modifier.size(sizeDp.dp)) {
        Image(
            bitmap = bitmap,
            contentDescription = app.title,
            modifier = Modifier.fillMaxSize().clip(shape)
        )
        if (app.notificationCount > 0) {
            Box(
                modifier = Modifier.align(Alignment.TopEnd)
                    .size(17.dp).clip(CircleShape)
                    .background(Color(0xFFFF526B))
                    .border(1.dp, AuraBlack, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (app.notificationCount > 9) "9+" else app.notificationCount.toString(),
                    color = Color.White,
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun HomeCommandTile(
    symbol: String,
    title: String,
    detail: String,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(19.dp))
            .background(Color(0xFF14263D))
            .border(1.dp, Color.White.copy(alpha = 0.075f), RoundedCornerShape(19.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Box(
            Modifier.size(34.dp).clip(RoundedCornerShape(11.dp))
                .background(accent.copy(alpha = 0.13f)),
            contentAlignment = Alignment.Center
        ) {
            Text(symbol, color = accent, fontSize = 19.sp)
        }
        Spacer(Modifier.height(10.dp))
        Text(title, color = AuraWhite, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(detail, color = AuraMuted, fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DrawerScreen(
    apps: List<LaunchableApp>,
    totalApps: Int,
    tab: Int,
    search: String,
    iconSize: Float,
    columns: Int,
    labels: Boolean,
    accent: Color,
    onTab: (Int) -> Unit,
    onSearch: (String) -> Unit,
    onBack: () -> Unit,
    onLaunch: (LaunchableApp) -> Unit,
    onAppMenu: (LaunchableApp) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0E2138), Color(0xFF081321), Color(0xFF050812))))
            .padding(horizontal = 19.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 3.dp, bottom = 17.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(43.dp).clip(RoundedCornerShape(15.dp))
                    .background(Color(0xFF162A43))
                    .border(1.dp, Color.White.copy(alpha = 0.09f), RoundedCornerShape(15.dp))
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) { Text("‹", color = AuraWhite, fontSize = 28.sp) }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("APP SPACE", color = AuraWhite, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.7.sp)
                Text("Sua biblioteca pessoal", color = AuraMuted, fontSize = 9.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(totalApps.toString().padStart(2, '0'), color = accent, fontSize = 24.sp, fontWeight = FontWeight.Light)
                Text("INSTALADOS", color = AuraMuted, fontSize = 7.sp, letterSpacing = 1.sp)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(17.dp))
                .background(Color(0xFF14263D))
                .border(1.dp, Color.White.copy(alpha = 0.085f), RoundedCornerShape(17.dp))
                .padding(start = 14.dp, end = 10.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("⌕", color = accent, fontSize = 25.sp)
            TextField(
                value = search,
                onValueChange = onSearch,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Pesquisar aplicativos...", color = AuraMuted, fontSize = 12.sp) },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedTextColor = AuraWhite,
                    unfocusedTextColor = AuraWhite,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = accent
                )
            )
            if (search.isNotBlank()) {
                Box(
                    Modifier.size(30.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.07f))
                        .clickable { onSearch("") },
                    contentAlignment = Alignment.Center
                ) { Text("×", color = AuraWhite, fontSize = 18.sp) }
            }
        }

        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp))
                .background(Color(0xFF101E31))
                .border(1.dp, Color.White.copy(alpha = 0.055f), RoundedCornerShape(15.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            DrawerTab("Todos", tab == 0, accent, Modifier.weight(1f)) { onTab(0) }
            DrawerTab("Favoritos", tab == 1, accent, Modifier.weight(1f)) { onTab(1) }
            DrawerTab("Ocultos", tab == 2, accent, Modifier.weight(1f)) { onTab(2) }
        }

        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    when (tab) { 1 -> "FIXADOS"; 2 -> "ÁREA PRIVADA"; else -> "TODOS OS APPS" },
                    color = AuraWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.25.sp
                )
                Text(
                    when {
                        search.isNotBlank() -> "Resultados para “$search”"
                        tab == 1 -> "Seus acessos favoritos"
                        tab == 2 -> "Aplicativos ocultos"
                        else -> "Encontre e abra qualquer aplicativo"
                    },
                    color = AuraMuted, fontSize = 9.sp
                )
            }
            Text("${apps.size} RESULTADOS", color = accent, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.7.sp)
        }
        Spacer(Modifier.height(13.dp))

        if (apps.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(25.dp))
                        .background(Color(0xFF111F33))
                        .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(25.dp))
                        .padding(25.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(Modifier.size(60.dp).clip(RoundedCornerShape(20.dp)).background(accent.copy(alpha = 0.13f)), contentAlignment = Alignment.Center) {
                        Text("⌕", color = accent, fontSize = 33.sp)
                    }
                    Spacer(Modifier.height(13.dp))
                    Text(
                        if (search.isNotBlank()) "Nenhum resultado" else "Nada por aqui ainda",
                        color = AuraWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        if (search.isNotBlank()) "Tente outro nome ou limpe a pesquisa." else "Os aplicativos desta seção aparecerão aqui.",
                        color = AuraMuted, fontSize = 10.sp
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns.coerceIn(3, 6)),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(top = 3.dp, bottom = 22.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(15.dp)
            ) {
                items(apps, key = { it.packageName }) { app ->
                    Column(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                            .combinedClickable(
                                onClick = { if (tab == 2) onAppMenu(app) else onLaunch(app) },
                                onLongClick = { onAppMenu(app) }
                            )
                            .padding(vertical = 7.dp, horizontal = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier.size((iconSize + 12f).coerceIn(54f, 82f).dp)
                                .clip(RoundedCornerShape(22.dp))
                                .background(Brush.linearGradient(listOf(Color(0xFF223B58), Color(0xFF122238))))
                                .border(1.dp, Color.White.copy(alpha = 0.075f), RoundedCornerShape(22.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            AppIconArtwork(app, iconSize.coerceIn(38f, 62f))
                        }
                        if (labels) {
                            Spacer(Modifier.height(7.dp))
                            Text(app.title, color = Color(0xFFE5EDF7), fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        Text(
            "SEGURE UM ÍCONE PARA MAIS OPÇÕES",
            color = AuraMuted.copy(alpha = 0.60f),
            fontSize = 7.sp,
            letterSpacing = 1.1.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 3.dp)
        )
    }
}

@Composable
private fun DrawerTab(
    title: String,
    selected: Boolean,
    accent: Color,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier.clip(RoundedCornerShape(11.dp))
            .background(if (selected) accent else Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            title,
            color = if (selected) AuraBlack else AuraMuted,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun SettingsScreen(
    iconSize: Float,
    columns: Int,
    labels: Boolean,
    accentIndex: Int,
    themeMode: Int,
    sortMode: Int,
    animations: Boolean,
    isDefault: Boolean,
    hiddenCount: Int,
    folders: List<AppFolder>,
    widgetCount: Int,
    installedIconPacks: List<InstalledIconPack>,
    selectedIconPack: String?,
    notificationAccessEnabled: Boolean,
    wallpaperPath: String?,
    onPickWallpaper: () -> Unit,
    onRemoveWallpaper: () -> Unit,
    swipeLeftEnabled: Boolean,
    doubleTapSettingsEnabled: Boolean,
    onBack: () -> Unit,
    onAddWidget: () -> Unit,
    onSelectIconPack: (String?) -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onSwipeLeftGesture: (Boolean) -> Unit,
    onDoubleTapGesture: (Boolean) -> Unit,
    onIconSize: (Float) -> Unit,
    onColumns: (Int) -> Unit,
    onLabels: (Boolean) -> Unit,
    onAccent: (Int) -> Unit,
    onTheme: (Int) -> Unit,
    onSort: (Int) -> Unit,
    onAnimations: (Boolean) -> Unit,
    onSetDefault: () -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    onCreateFolder: () -> Unit,
    onOpenHidden: () -> Unit,
    onRemoveFolder: (AppFolder) -> Unit,
    onSave: () -> Unit
) {
    val accent = AccentOptions[accentIndex]
    val wallpaperPreview = rememberWallpaperBitmap(wallpaperPath)
    Column(
        modifier = Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF142C4C), AuraBlack)))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 21.dp, vertical = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(AuraPanel)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Text("‹", color = AuraWhite, fontSize = 28.sp)
            }
            Spacer(Modifier.width(13.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Seu AURA", color = AuraWhite, fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
                Text("Personalize cada detalhe", color = AuraMuted, fontSize = 11.sp)
            }
            Text("SETTINGS", color = accent, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        }

        Spacer(Modifier.height(22.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF4A3A32))
        ) {
            Row(
                modifier = Modifier.padding(17.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("AURA LAUNCHER", color = accent, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.7.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (isDefault) "Seu espaço está ativo." else "Seu espaço está quase pronto.",
                        color = AuraWhite,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        if (isDefault) "O AURA está cuidando da sua tela inicial." else "Escolha o AURA como tela inicial do Android.",
                        color = Color(0xFFB9CBE5),
                        fontSize = 10.sp
                    )
                }
                Box(
                    modifier = Modifier.size(48.dp).clip(CircleShape)
                        .background(if (isDefault) Color(0xFF9BE4C0).copy(alpha = 0.15f) else accent.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (isDefault) "✓" else "✦", color = if (isDefault) Color(0xFF9BE4C0) else accent, fontSize = 23.sp)
                }
            }
        }

        SettingsHeading("TELA INICIAL", accent)
        SettingCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Tamanho dos ícones", color = AuraWhite, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text("Ajuste a presença dos aplicativos", color = AuraMuted, fontSize = 10.sp)
                }
                Text("${iconSize.toInt()} dp", color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Slider(value = iconSize, onValueChange = onIconSize, valueRange = 42f..72f)
            Divider(color = Color.White.copy(alpha = 0.08f))
            Text("Colunas da biblioteca", color = AuraWhite, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                (3..6).forEach { count ->
                    ChoiceChip(count.toString(), columns == count, accent) { onColumns(count) }
                }
            }
            Divider(color = Color.White.copy(alpha = 0.08f))
            SettingSwitch("Nome dos aplicativos", "Mostrar o nome abaixo dos ícones", labels, onLabels)
        }

        SettingsHeading("IDENTIDADE VISUAL", accent)
        SettingCard {
            Text("Tema", color = AuraWhite, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                ThemePreview("Graphite", themeMode == 0, Color(0xFF142239), accent) { onTheme(0) }
                ThemePreview("AMOLED", themeMode == 1, Color(0xFF050505), accent) { onTheme(1) }
            }
            Divider(color = Color.White.copy(alpha = 0.08f))
            Text("Cor de destaque", color = AuraWhite, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AccentOptions.forEachIndexed { index, color ->
                    Box(
                        modifier = Modifier.size(36.dp).clip(CircleShape)
                            .background(color)
                            .clickable { onAccent(index) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (accentIndex == index) Text("✓", color = AuraBlack, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Divider(color = Color.White.copy(alpha = 0.08f))
            Text("Ordem da biblioteca", color = AuraWhite, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChip("Nome A–Z", sortMode == 0, accent) { onSort(0) }
                ChoiceChip("Usados recentemente", sortMode == 1, accent) { onSort(1) }
            }
            Divider(color = Color.White.copy(alpha = 0.08f))
            SettingSwitch("Movimento suave", "Animação da seção de aplicativos recentes", animations, onAnimations)
        }

        SettingsHeading("PAPEL DE PAREDE", accent)
        SettingCard {
            Text("Sua foto na tela inicial", color = AuraWhite, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(
                "Escolha qualquer imagem do dispositivo. O AURA guarda uma cópia privada no próprio aparelho.",
                color = AuraMuted,
                fontSize = 10.sp,
                lineHeight = 15.sp
            )
            if (wallpaperPreview != null) {
                Image(
                    bitmap = wallpaperPreview,
                    contentDescription = "Prévia do papel de parede",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(118.dp).clip(RoundedCornerShape(15.dp))
                )
            }
            Button(
                onClick = onPickWallpaper,
                modifier = Modifier.fillMaxWidth().height(46.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = AuraBlack),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(if (wallpaperPath == null) "Escolher foto" else "Trocar papel de parede", fontWeight = FontWeight.Bold)
            }
            if (wallpaperPath != null) {
                TextButton(onClick = onRemoveWallpaper, modifier = Modifier.fillMaxWidth()) {
                    Text("Restaurar fundo original do AURA", color = accent, fontWeight = FontWeight.SemiBold)
                }
            }
            Text(
                "A imagem não é enviada para a internet e não é incluída no arquivo de backup.",
                color = AuraMuted.copy(alpha = 0.75f),
                fontSize = 9.sp,
                lineHeight = 13.sp
            )
        }

        SettingsHeading("ORGANIZAÇÃO", accent)
        SettingCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Pastas", color = AuraWhite, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text("${folders.size} coleções no seu espaço", color = AuraMuted, fontSize = 10.sp)
                }
                TextButton(onClick = onCreateFolder) { Text("+ Criar", color = accent, fontWeight = FontWeight.Bold) }
            }
            folders.forEach { folder ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("▦", color = accent, fontSize = 18.sp)
                    Spacer(Modifier.width(9.dp))
                    Text(folder.name, color = AuraWhite, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = { onRemoveFolder(folder) }) {
                        Text("Remover", color = Color(0xFFFFA3A3), fontSize = 10.sp)
                    }
                }
            }
            Divider(color = Color.White.copy(alpha = 0.08f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Aplicativos ocultos", color = AuraWhite, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text("$hiddenCount aplicativos fora da biblioteca principal", color = AuraMuted, fontSize = 10.sp)
                }
                TextButton(onClick = onOpenHidden) { Text("Gerenciar", color = accent) }
            }
        }

        SettingsHeading("WIDGETS DO ANDROID", accent)
        SettingCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Área de widgets", color = AuraWhite, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text("$widgetCount widget(s) adicionados à Home", color = AuraMuted, fontSize = 10.sp)
                }
                Text("ANDROID", color = accent, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                "Use os widgets originais dos aplicativos instalados. Depois de adicionar, arraste a barra superior para posicionar cada um na tela.",
                color = AuraMuted,
                fontSize = 10.sp,
                lineHeight = 15.sp
            )
            Button(
                onClick = onAddWidget,
                modifier = Modifier.fillMaxWidth().height(46.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = AuraBlack),
                shape = RoundedCornerShape(14.dp)
            ) { Text("Adicionar widget", fontWeight = FontWeight.Bold) }
        }

        SettingsHeading("PACOTES DE ÍCONES", accent)
        SettingCard {
            Text("Escolha o estilo dos aplicativos", color = AuraWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            ChoiceChip("Ícones originais", selectedIconPack == null, accent) { onSelectIconPack(null) }
            if (installedIconPacks.isEmpty()) {
                Text(
                    "Nenhum pacote compatível encontrado. Instale um icon pack que ofereça appfilter.xml e suporte ADW/Nova.",
                    color = AuraMuted,
                    fontSize = 10.sp,
                    lineHeight = 15.sp
                )
            } else {
                installedIconPacks.forEach { pack ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(pack.label, color = AuraWhite, fontSize = 11.sp, modifier = Modifier.weight(1f))
                        ChoiceChip("Aplicar", selectedIconPack == pack.packageName, accent) {
                            onSelectIconPack(pack.packageName)
                        }
                    }
                }
            }
        }

        SettingsHeading("INDICADORES DE NOTIFICAÇÃO", accent)
        SettingCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Acesso às notificações", color = AuraWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (notificationAccessEnabled) "Permissão concedida" else "Permissão ainda não concedida",
                        color = if (notificationAccessEnabled) Color(0xFF9BE4C0) else AuraMuted,
                        fontSize = 10.sp
                    )
                }
                Box(
                    Modifier.size(9.dp).clip(CircleShape)
                        .background(if (notificationAccessEnabled) Color(0xFF61E6A7) else Color(0xFFFFC46B))
                )
            }
            Text(
                "O AURA lê somente a quantidade de notificações por aplicativo para mostrar os indicadores. O conteúdo das mensagens não é salvo.",
                color = AuraMuted,
                fontSize = 10.sp,
                lineHeight = 15.sp
            )
            Button(
                onClick = onOpenNotificationSettings,
                modifier = Modifier.fillMaxWidth().height(46.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = AuraBlack),
                shape = RoundedCornerShape(14.dp)
            ) { Text(if (notificationAccessEnabled) "Gerenciar permissão" else "Ativar indicadores", fontWeight = FontWeight.Bold) }
        }

        SettingsHeading("GESTOS", accent)
        SettingCard {
            SettingSwitch(
                "Deslizar para a esquerda",
                "Abrir a biblioteca a partir da tela inicial",
                swipeLeftEnabled,
                onSwipeLeftGesture
            )
            Divider(color = Color.White.copy(alpha = 0.08f))
            SettingSwitch(
                "Toque duplo no relógio",
                "Abrir o Studio de personalização",
                doubleTapSettingsEnabled,
                onDoubleTapGesture
            )
            Text(
                "Pressione e segure o painel do relógio para alternar o modo de edição dos widgets.",
                color = AuraMuted,
                fontSize = 10.sp,
                lineHeight = 14.sp
            )
        }

        SettingsHeading("BACKUP E RESTAURAÇÃO", accent)
        SettingCard {
            Text("Seus ajustes, suas regras", color = AuraWhite, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text("Exporte favoritos, ocultos, pastas, aparência e preferências para um arquivo local. A restauração substitui essas configurações.", color = AuraMuted, fontSize = 10.sp, lineHeight = 15.sp)
            Button(
                onClick = onExportBackup,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = AuraBlack),
                shape = RoundedCornerShape(14.dp)
            ) { Text("Exportar backup", fontWeight = FontWeight.Bold) }
            TextButton(onClick = onImportBackup, modifier = Modifier.fillMaxWidth()) {
                Text("Restaurar de um arquivo", color = accent, fontWeight = FontWeight.SemiBold)
            }
        }

        SettingsHeading("SISTEMA ANDROID", accent)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF142239))
        ) {
            Column(modifier = Modifier.padding(17.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Tela inicial padrão", color = AuraWhite, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (isDefault) "AURA está selecionado no Android" else "O Android ainda usa outro aplicativo",
                            color = if (isDefault) Color(0xFF9BE4C0) else AuraMuted,
                            fontSize = 10.sp
                        )
                    }
                    Text(if (isDefault) "✓" else "○", color = if (isDefault) Color(0xFF9BE4C0) else accent, fontSize = 23.sp)
                }
                Spacer(Modifier.height(13.dp))
                Button(
                    onClick = onSetDefault,
                    modifier = Modifier.fillMaxWidth().height(49.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = AuraBlack),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        if (isDefault) "AURA já é o principal" else "Definir AURA como principal",
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(7.dp))
                Text(
                    "A confirmação é feita pelo próprio Android. Em alguns aparelhos, a seleção fica em Configurações > Aplicativos padrão > App de início.",
                    color = AuraMuted,
                    fontSize = 10.sp,
                    lineHeight = 15.sp
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        Button(
            onClick = onSave,
            modifier = Modifier.fillMaxWidth().height(53.dp),
            colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = AuraBlack),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Salvar personalização", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(20.dp))
        Text(
            "AURA Launcher  ·  Preferências salvas neste dispositivo",
            color = AuraMuted.copy(alpha = 0.65f),
            fontSize = 9.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

@Composable
private fun ThemePreview(
    title: String,
    selected: Boolean,
    background: Color,
    accent: Color,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier.width(120.dp).clip(RoundedCornerShape(15.dp))
            .background(Color(0xFF182B45)).clickable { onClick() }.padding(8.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(10.dp))
                .background(background),
            contentAlignment = Alignment.Center
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                repeat(3) {
                    Box(Modifier.size(13.dp).clip(CircleShape).background(accent.copy(alpha = 0.75f)))
                }
            }
        }
        Spacer(Modifier.height(7.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = AuraWhite, fontSize = 11.sp, modifier = Modifier.weight(1f))
            if (selected) Text("✓", color = accent, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SettingsHeading(title: String, accent: Color) {
    Spacer(Modifier.height(22.dp))
    Text(title, color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp)
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun SettingCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AuraPanel),
        shape = RoundedCornerShape(21.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            content()
        }
    }
}

@Composable
private fun SettingSwitch(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = AuraWhite, fontWeight = FontWeight.Medium, fontSize = 13.sp)
            Text(description, color = AuraMuted, fontSize = 10.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    Text(
        label,
        color = if (selected) AuraBlack else AuraWhite,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        modifier = Modifier.clip(RoundedCornerShape(12.dp))
            .background(if (selected) accent else Color(0xFF1D3452))
            .clickable { onClick() }
            .padding(horizontal = 15.dp, vertical = 9.dp)
    )
}

@Composable
private fun DockAction(symbol: String, title: String, accent: Color, onClick: () -> Unit) {
    Column(
        modifier = Modifier.clip(RoundedCornerShape(15.dp)).clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(symbol, color = accent, fontSize = 22.sp)
        Text(title, color = AuraWhite, fontSize = 9.sp, fontWeight = FontWeight.Medium)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppIcon(
    app: LaunchableApp,
    iconSize: Float,
    labels: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    val bitmap = remember(app.packageName, app.icon) { app.icon.toImageBitmap() }
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.size(iconSize.dp)) {
            Image(
                bitmap = bitmap,
                contentDescription = app.title,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(17.dp))
            )
            if (app.notificationCount > 0) {
                Box(
                    modifier = Modifier.align(Alignment.TopEnd).size(17.dp)
                        .clip(CircleShape).background(Color(0xFFFF526B))
                        .border(1.dp, AuraBlack, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (app.notificationCount > 9) "9+" else app.notificationCount.toString(),
                        color = Color.White,
                        fontSize = 7.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        if (labels) {
            Spacer(Modifier.height(6.dp))
            Text(
                app.title,
                color = AuraWhite,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AppActionsDialog(
    app: LaunchableApp,
    isFavorite: Boolean,
    isHidden: Boolean,
    isOnHome: Boolean,
    onDismiss: () -> Unit,
    onAddToHome: () -> Unit,
    onFavorite: () -> Unit,
    onHide: () -> Unit,
    onInfo: () -> Unit,
    onUninstall: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AuraPanel,
        titleContentColor = AuraWhite,
        textContentColor = AuraMuted,
        title = { Text(app.title, fontWeight = FontWeight.SemiBold) },
        text = { Text("Ações do aplicativo") },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = onFavorite) {
                    Text(if (isFavorite) "Remover dos favoritos" else "Adicionar aos favoritos")
                }
                TextButton(onClick = onAddToHome) {
                    Text(if (isOnHome) "Remover da Home" else "Adicionar à Home")
                }
                TextButton(onClick = onHide) {
                    Text(if (isHidden) "Mostrar na gaveta" else "Ocultar aplicativo")
                }
                TextButton(onClick = onInfo) { Text("Informações do aplicativo") }
                TextButton(onClick = onUninstall) { Text("Desinstalar", color = Color(0xFFFF9B9B)) }
                TextButton(onClick = onDismiss) { Text("Cancelar", color = AuraMuted) }
            }
        }
    )
}

@Composable
private fun FolderBuilderDialog(
    apps: List<LaunchableApp>,
    onDismiss: () -> Unit,
    onCreate: (String, List<String>) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AuraPanel,
        titleContentColor = AuraWhite,
        textContentColor = AuraMuted,
        title = { Text("Criar pasta", fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("Nome da pasta") },
                    singleLine = true
                )
                Spacer(Modifier.height(12.dp))
                Text("Selecione pelo menos dois aplicativos", color = AuraMuted, fontSize = 11.sp)
                Column(
                    modifier = Modifier.heightIn(max = 310.dp).verticalScroll(rememberScrollState())
                ) {
                    apps.forEach { app ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                selected = selected.toMutableSet().apply {
                                    if (contains(app.packageName)) remove(app.packageName) else add(app.packageName)
                                }
                            }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = app.packageName in selected,
                                onCheckedChange = { checked ->
                                    selected = selected.toMutableSet().apply {
                                        if (checked) add(app.packageName) else remove(app.packageName)
                                    }
                                }
                            )
                            Text(app.title, color = AuraWhite, fontSize = 12.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && selected.size >= 2,
                onClick = { onCreate(name.trim(), selected.toList()) }
            ) { Text("Criar pasta") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun FolderContentsDialog(
    folder: AppFolder,
    apps: List<LaunchableApp>,
    accent: Color,
    onDismiss: () -> Unit,
    onLaunch: (LaunchableApp) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AuraPanel,
        titleContentColor = AuraWhite,
        title = { Text(folder.name, fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                apps.forEach { app ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onLaunch(app) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppIconArtwork(app, 34f, RoundedCornerShape(9.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(app.title, color = AuraWhite, modifier = Modifier.weight(1f))
                        Text("Abrir", color = accent, fontSize = 11.sp)
                    }
                }
                if (apps.isEmpty()) Text("Os aplicativos desta pasta não estão disponíveis.", color = AuraMuted)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar") } }
    )
}


private const val WALLPAPER_FILE_NAME = "aura-wallpaper.img"
private const val MAX_WALLPAPER_BYTES = 20L * 1024L * 1024L

@Composable
private fun rememberWallpaperBitmap(path: String?): ImageBitmap? {
    var image by remember(path) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(path) {
        image = null
        if (!path.isNullOrBlank()) {
            image = withContext(Dispatchers.IO) {
                decodeWallpaperBitmap(path)?.asImageBitmap()
            }
        }
    }
    return image
}

private fun decodeWallpaperBitmap(path: String): Bitmap? {
    return try {
        val file = File(path)
        if (!file.isFile) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while (bounds.outWidth / sampleSize > 1920 || bounds.outHeight / sampleSize > 1920) {
            sampleSize *= 2
        }
        BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
        )
    } catch (_: Exception) {
        null
    }
}

private fun InputStream.readUtf8Limited(maxBytes: Int): String {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8 * 1024)
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        require(output.size() + count <= maxBytes) {
            "O backup excede o limite de tamanho permitido (512 KB)."
        }
        output.write(buffer, 0, count)
    }
    return String(output.toByteArray(), Charsets.UTF_8)
}

private fun Drawable.toImageBitmap(): ImageBitmap {
    val width = intrinsicWidth.coerceAtLeast(1)
    val height = intrinsicHeight.coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bitmap.asImageBitmap()
}
