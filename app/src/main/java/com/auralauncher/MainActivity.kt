package com.auralauncher

import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class LaunchableApp(
    val title: String,
    val packageName: String,
    val icon: Drawable
)

private data class AppFolder(
    val name: String,
    val packages: List<String>
)

class MainActivity : ComponentActivity() {
    private lateinit var appState: androidx.compose.runtime.MutableState<List<LaunchableApp>>
    private lateinit var defaultState: androidx.compose.runtime.MutableState<Boolean>

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
        window.statusBarColor = android.graphics.Color.rgb(9, 11, 16)
        @Suppress("DEPRECATION")
        window.navigationBarColor = android.graphics.Color.rgb(9, 11, 16)

        appState = mutableStateOf(loadLaunchableApps())
        defaultState = mutableStateOf(isDefaultHome())
        val prefs = getSharedPreferences("aura_preferences", MODE_PRIVATE)

        setContent {
            AuraTheme {
                AuraLauncher(
                    apps = appState.value,
                    isDefaultLauncher = defaultState.value,
                    initialFavorites = prefs.getStringSet("favorites", emptySet()) ?: emptySet(),
                    initialHidden = prefs.getStringSet("hidden", emptySet()) ?: emptySet(),
                    initialRecents = readPackageList(prefs.getString("recent_order", "")),
                    initialFolders = decodeFolders(prefs.getString("folders", "[]")),
                    iconSize = prefs.getFloat("icon_size", 54f),
                    columns = prefs.getInt("columns", 4),
                    labels = prefs.getBoolean("labels", true),
                    accent = prefs.getInt("accent", 0),
                    theme = prefs.getInt("theme", 0),
                    sortMode = prefs.getInt("sort_mode", 0),
                    animations = prefs.getBoolean("animations", true),
                    onSetDefault = ::requestDefaultLauncher,
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
                        startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName")))
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

    override fun onResume() {
        super.onResume()
        if (::appState.isInitialized) appState.value = loadLaunchableApps()
        if (::defaultState.isInitialized) defaultState.value = isDefaultHome()
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
                LaunchableApp(
                    title = info.loadLabel(packageManager).toString(),
                    packageName = activityInfo.packageName,
                    icon = info.loadIcon(packageManager)
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

private val AuraBlack = Color(0xFF0B1424)
private val AuraPanel = Color(0xFF17243A)
private val AuraPanelRaised = Color(0xFF202F49)
private val AuraMuted = Color(0xFF9EACC1)
private val AuraWhite = Color(0xFFF3F6FC)
private val AccentOptions = listOf(
    Color(0xFF77DCE5),
    Color(0xFFB6B9FF),
    Color(0xFFB9E8D2),
    Color(0xFFF3C6DD),
    Color(0xFFFFD59F)
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
    iconSize: Float,
    columns: Int,
    labels: Boolean,
    accent: Int,
    theme: Int,
    sortMode: Int,
    animations: Boolean,
    onSetDefault: () -> Unit,
    onLaunch: (String) -> Unit,
    onAppInfo: (String) -> Unit,
    onUninstall: (String) -> Unit,
    onSaveFavorites: (Set<String>) -> Unit,
    onSaveHidden: (Set<String>) -> Unit,
    onSaveFolders: (List<AppFolder>) -> Unit,
    onSaveSettings: (Float, Int, Boolean, Int, Int, Int, Boolean) -> Unit
) {
    var favorites by remember { mutableStateOf(initialFavorites) }
    var hidden by remember { mutableStateOf(initialHidden) }
    var recents by remember { mutableStateOf(initialRecents) }
    var folders by remember { mutableStateOf(initialFolders) }
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
        Brush.verticalGradient(listOf(Color(0xFF182131), AuraBlack, Color(0xFF090B11)))
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
        }
        onSaveHidden(hidden)
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
                    onBack = { showSettings = false },
                    onIconSize = { currentIconSize = it },
                    onColumns = { currentColumns = it },
                    onLabels = { showLabels = it },
                    onAccent = { accentIndex = it },
                    onTheme = { themeMode = it },
                    onSort = { currentSort = it },
                    onAnimations = { motionEnabled = it },
                    onSetDefault = onSetDefault,
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
                    onFolder = { activeFolder = it }
                )
            }
        }
    }

    selectedApp?.let { app ->
        AppActionsDialog(
            app = app,
            isFavorite = app.packageName in favorites,
            isHidden = app.packageName in hidden,
            onDismiss = { selectedApp = null },
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
    onFolder: (AppFolder) -> Unit
) {
    val hour = time.substringBefore(":").toIntOrNull() ?: 12
    val greeting = when (hour) {
        in 5..11 -> "BOM DIA"
        in 12..17 -> "BOA TARDE"
        else -> "BOA NOITE"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF15243A), Color(0xFF0B1424), Color(0xFF080D17))
                )
            )
            .padding(horizontal = 22.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .pointerInput(Unit) {
                    var dragX = 0f
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (dragX < -105f) onOpenDrawer()
                            dragX = 0f
                        },
                        onDragCancel = { dragX = 0f }
                    ) { change, amount ->
                        dragX += amount
                        change.consume()
                    }
                }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "A U R A",
                        color = accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 5.sp
                    )
                    Text(
                        "SEU ESPAÇO PESSOAL",
                        color = AuraMuted.copy(alpha = 0.78f),
                        fontSize = 9.sp,
                        letterSpacing = 2.1.sp
                    )
                }
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(accent.copy(alpha = 0.28f), Color.White.copy(alpha = 0.07f))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text("A", color = AuraWhite, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(32.dp))
            Text(
                greeting,
                color = accent,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.3.sp
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    time,
                    color = AuraWhite,
                    fontSize = 62.sp,
                    fontWeight = FontWeight.Light,
                    letterSpacing = (-3.5).sp,
                    modifier = Modifier.weight(1f)
                )
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Text(
                        if (isDefault) "● ATIVO" else "○ NÃO PADRÃO",
                        color = if (isDefault) Color(0xFF9BE4C0) else AuraMuted,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text("TELA INICIAL", color = AuraMuted, fontSize = 8.sp, letterSpacing = 1.2.sp)
                }
            }
            Text(
                date.replaceFirstChar { it.uppercase(Locale("pt", "BR")) },
                color = AuraMuted,
                fontSize = 13.sp
            )

            Spacer(Modifier.height(23.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenDrawer() },
                shape = RoundedCornerShape(19.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1B2A40))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("⌕", color = accent, fontSize = 25.sp)
                    Spacer(Modifier.width(11.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Buscar aplicativos", color = AuraWhite, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text("Encontre o que precisa rapidamente", color = AuraMuted, fontSize = 10.sp)
                    }
                    Text("↗", color = AuraMuted, fontSize = 17.sp)
                }
            }

            Spacer(Modifier.height(20.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF344D69), Color(0xFF1A304A), Color(0xFF18253A))
                            )
                        )
                        .padding(19.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "AURA / VISÃO GERAL",
                                color = accent,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.7.sp
                            )
                            Spacer(Modifier.height(7.dp))
                            Text(
                                "Seu celular.\nDo seu jeito.",
                                color = AuraWhite,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.SemiBold,
                                lineHeight = 27.sp
                            )
                            Spacer(Modifier.height(7.dp))
                            Text(
                                "${favorites.size} favoritos  ·  ${recentApps.size} recentes  ·  ${folders.size} pastas",
                                color = Color(0xFFC1CBD9),
                                fontSize = 10.sp
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.07f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(accent.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("✦", color = accent, fontSize = 34.sp)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(25.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Acesso rápido", color = AuraWhite, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text("Seus aplicativos favoritos", color = AuraMuted, fontSize = 10.sp)
                }
                Text(
                    "EDITAR",
                    color = accent,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.clickable { onOpenFavorites() }.padding(7.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
            if (favorites.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onOpenDrawer() },
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = AuraPanel)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(42.dp).clip(RoundedCornerShape(13.dp))
                                .background(accent.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("+", color = accent, fontSize = 25.sp)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Monte seu espaço", color = AuraWhite, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text("Segure um app na biblioteca para favoritar.", color = AuraMuted, fontSize = 10.sp)
                        }
                        Text("›", color = AuraMuted, fontSize = 22.sp)
                    }
                }
            } else {
                favorites.take(4).chunked(2).forEach { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        pair.forEach { app ->
                            Card(
                                modifier = Modifier.weight(1f).clickable { onLaunch(app) },
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF17243A))
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 13.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Image(
                                        bitmap = remember(app.packageName) { app.icon.toImageBitmap() },
                                        contentDescription = app.title,
                                        modifier = Modifier.size(39.dp).clip(RoundedCornerShape(12.dp))
                                    )
                                    Spacer(Modifier.width(9.dp))
                                    Text(
                                        app.title,
                                        color = AuraWhite,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(9.dp))
                }
            }

            AnimatedVisibility(
                visible = recentApps.isNotEmpty(),
                enter = if (animations) fadeIn() else EnterTransition.None,
                exit = if (animations) fadeOut() else ExitTransition.None
            ) {
                Column {
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Continue de onde parou", color = AuraWhite, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                            Text("Aplicativos abertos recentemente", color = AuraMuted, fontSize = 10.sp)
                        }
                        Text("RECENTES", color = accent, fontSize = 8.sp, letterSpacing = 1.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(recentApps, key = { it.packageName }) { app ->
                            Card(
                                modifier = Modifier.width(82.dp).clickable { onLaunch(app) },
                                colors = CardDefaults.cardColors(containerColor = AuraPanel),
                                shape = RoundedCornerShape(17.dp)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 13.dp, horizontal = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Image(
                                        bitmap = remember(app.packageName) { app.icon.toImageBitmap() },
                                        contentDescription = app.title,
                                        modifier = Modifier.size(39.dp).clip(RoundedCornerShape(12.dp))
                                    )
                                    Spacer(Modifier.height(7.dp))
                                    Text(
                                        app.title,
                                        color = AuraWhite,
                                        fontSize = 9.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (folders.isNotEmpty()) {
                Spacer(Modifier.height(22.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Suas coleções", color = AuraWhite, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        Text("Aplicativos organizados por você", color = AuraMuted, fontSize = 10.sp)
                    }
                    Text(
                        "GERENCIAR",
                        color = accent,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { onOpenSettings() }.padding(6.dp)
                    )
                }
                Spacer(Modifier.height(11.dp))
                folders.forEach { folder ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).clickable { onFolder(folder) },
                        colors = CardDefaults.cardColors(containerColor = AuraPanel),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 15.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.size(37.dp).clip(RoundedCornerShape(11.dp))
                                    .background(accent.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("▦", color = accent, fontSize = 18.sp)
                            }
                            Spacer(Modifier.width(11.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(folder.name, color = AuraWhite, fontWeight = FontWeight.Medium, fontSize = 12.sp)
                                Text("${folder.packages.size} aplicativos", color = AuraMuted, fontSize = 10.sp)
                            }
                            Text("›", color = AuraMuted, fontSize = 21.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(22.dp))
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1D2B40)),
            shape = RoundedCornerShape(23.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                DockAction("▦", "Biblioteca", accent, onOpenDrawer)
                DockAction("✦", "Favoritos", accent, onOpenFavorites)
                DockAction("◈", "Personalizar", accent, onOpenSettings)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            if (isDefault) "AURA é sua tela inicial" else "Toque aqui para definir o AURA como principal",
            color = AuraMuted,
            fontSize = 10.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally)
                .clickable { onSetDefault() }
                .padding(4.dp)
        )
    }
}

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
            .background(Brush.verticalGradient(listOf(Color(0xFF15243A), AuraBlack, Color(0xFF080D17))))
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape)
                    .background(AuraPanel).clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Text("‹", color = AuraWhite, fontSize = 27.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Biblioteca", color = AuraWhite, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                Text("Seus aplicativos em um só lugar", color = AuraMuted, fontSize = 10.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("AURA", color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Text("$totalApps APPS", color = AuraMuted, fontSize = 8.sp, letterSpacing = 1.sp)
            }
        }

        Spacer(Modifier.height(19.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2A41)),
            shape = RoundedCornerShape(18.dp)
        ) {
            Row(
                modifier = Modifier.padding(start = 14.dp, end = 9.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("⌕", color = accent, fontSize = 24.sp)
                TextField(
                    value = search,
                    onValueChange = onSearch,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Buscar aplicativo...", color = AuraMuted) },
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
                    Text(
                        "×",
                        color = AuraMuted,
                        fontSize = 20.sp,
                        modifier = Modifier.clickable { onSearch("") }.padding(horizontal = 8.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(15.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF121D2E)),
            shape = RoundedCornerShape(15.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(5.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                DrawerTab("Todos", tab == 0, accent, Modifier.weight(1f)) { onTab(0) }
                DrawerTab("Favoritos", tab == 1, accent, Modifier.weight(1f)) { onTab(1) }
                DrawerTab("Ocultos", tab == 2, accent, Modifier.weight(1f)) { onTab(2) }
            }
        }

        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    when (tab) {
                        1 -> "Seus favoritos"
                        2 -> "Aplicativos ocultos"
                        else -> "Todos os aplicativos"
                    },
                    color = AuraWhite,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "${apps.size} ${if (apps.size == 1) "aplicativo" else "aplicativos"}",
                    color = AuraMuted,
                    fontSize = 10.sp
                )
            }
            Text("TOQUE PARA ABRIR", color = AuraMuted.copy(alpha = 0.72f), fontSize = 8.sp, letterSpacing = 1.sp)
        }
        Spacer(Modifier.height(11.dp))

        if (apps.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = AuraPanel)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("⌕", color = accent, fontSize = 36.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (search.isNotBlank()) "Nada encontrado" else "Esta seção está vazia",
                            color = AuraWhite,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            if (search.isNotBlank()) "Experimente outro nome ou termo." else "Os aplicativos aparecerão aqui quando estiverem disponíveis.",
                            color = AuraMuted,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns.coerceIn(3, 6)),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 22.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(apps, key = { it.packageName }) { app ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(17.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF17243A))
                    ) {
                        AppIcon(
                            app = app,
                            iconSize = iconSize,
                            labels = labels,
                            onClick = { if (tab == 2) onAppMenu(app) else onLaunch(app) },
                            onLongClick = { onAppMenu(app) }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(7.dp))
        Text(
            "Segure um aplicativo para favoritar, ocultar ou gerenciar.",
            color = AuraMuted.copy(alpha = 0.72f),
            fontSize = 9.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally)
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
    onBack: () -> Unit,
    onIconSize: (Float) -> Unit,
    onColumns: (Int) -> Unit,
    onLabels: (Boolean) -> Unit,
    onAccent: (Int) -> Unit,
    onTheme: (Int) -> Unit,
    onSort: (Int) -> Unit,
    onAnimations: (Boolean) -> Unit,
    onSetDefault: () -> Unit,
    onCreateFolder: () -> Unit,
    onOpenHidden: () -> Unit,
    onRemoveFolder: (AppFolder) -> Unit,
    onSave: () -> Unit
) {
    val accent = AccentOptions[accentIndex]
    Column(
        modifier = Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF15243A), AuraBlack)))
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
            colors = CardDefaults.cardColors(containerColor = Color(0xFF263C58))
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
                        color = Color(0xFFC0CBD9),
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
                ThemePreview("Midnight", themeMode == 0, Color(0xFF17243A), accent) { onTheme(0) }
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

        SettingsHeading("SISTEMA ANDROID", accent)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF17243A))
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
            .background(Color(0xFF222C3C)).clickable { onClick() }.padding(8.dp)
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
            .background(if (selected) accent else Color(0xFF2A3040))
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
    val bitmap = remember(app.packageName) { app.icon.toImageBitmap() }
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            bitmap = bitmap,
            contentDescription = app.title,
            modifier = Modifier.size(iconSize.dp).clip(RoundedCornerShape(17.dp))
        )
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
    onDismiss: () -> Unit,
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
                        Image(
                            bitmap = remember(app.packageName) { app.icon.toImageBitmap() },
                            contentDescription = app.title,
                            modifier = Modifier.size(34.dp).clip(RoundedCornerShape(9.dp))
                        )
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

private fun Drawable.toImageBitmap(): ImageBitmap {
    val width = intrinsicWidth.coerceAtLeast(1)
    val height = intrinsicHeight.coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bitmap.asImageBitmap()
}
