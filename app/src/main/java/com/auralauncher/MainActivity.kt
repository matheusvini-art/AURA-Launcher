package com.auralauncher

import android.app.role.RoleManager
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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_HOME)
        } else {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo?.packageName == packageName
        }
    }

    private fun requestDefaultLauncher() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager.isRoleAvailable(RoleManager.ROLE_HOME) &&
                !roleManager.isRoleHeld(RoleManager.ROLE_HOME)
            ) {
                startActivity(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME))
            } else {
                startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
            }
        } else {
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        }
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

private val AuraBlack = Color(0xFF080A0F)
private val AuraPanel = Color(0xFF171C27)
private val AuraPanelRaised = Color(0xFF202737)
private val AuraMuted = Color(0xFF98A2B3)
private val AuraWhite = Color(0xFFF5F7FB)
private val AccentOptions = listOf(
    Color(0xFFC7D2FE),
    Color(0xFFB9F3D0),
    Color(0xFFF5C7E7),
    Color(0xFFFFD59E),
    Color(0xFF9DECF2)
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
        Box(modifier = Modifier.fillMaxSize().background(background)) {
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
                    themeMode = themeMode,
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
    themeMode: Int,
    animations: Boolean,
    onOpenDrawer: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenSettings: () -> Unit,
    onSetDefault: () -> Unit,
    onLaunch: (LaunchableApp) -> Unit,
    onFolder: (AppFolder) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "A U R A",
                        color = accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 5.sp
                    )
                    Text("Seu espaço. Seu ritmo.", color = AuraMuted, fontSize = 12.sp)
                }
                Text(
                    if (isDefault) "LAUNCHER ATIVO" else "DEFINIR COMO PRINCIPAL",
                    color = if (isDefault) Color(0xFF9DE5BD) else accent,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(CircleShape)
                        .background(AuraPanel)
                        .clickable { onSetDefault() }
                        .padding(horizontal = 11.dp, vertical = 10.dp)
                )
            }

            Spacer(Modifier.height(36.dp))
            Text(time, color = AuraWhite, fontSize = 76.sp, fontWeight = FontWeight.Light, letterSpacing = (-4).sp)
            Text(date, color = AuraMuted, fontSize = 13.sp)
            Spacer(Modifier.height(22.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.055f)),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Tudo no seu alcance", color = AuraWhite, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Text("Um espaço simples, rápido e do seu jeito.", color = AuraMuted, fontSize = 11.sp)
                    }
                    Text("✦", color = accent, fontSize = 30.sp)
                }
            }

            Spacer(Modifier.height(25.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Seus favoritos", color = AuraWhite, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text("VER TODOS", color = accent, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onOpenFavorites() }.padding(6.dp))
            }
            Spacer(Modifier.height(12.dp))
            if (favorites.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onOpenDrawer() },
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = AuraPanel)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Monte seu acesso rápido", color = AuraWhite, fontWeight = FontWeight.Medium)
                        Text("Abra Aplicativos e segure um ícone para favoritar.", color = AuraMuted, fontSize = 11.sp)
                    }
                }
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 3.dp)
                ) {
                    items(favorites.take(10), key = { it.packageName }) { app ->
                        Box(Modifier.width(70.dp)) {
                            AppIcon(app, iconSize, labels, onClick = { onLaunch(app) })
                        }
                    }
                }
            }

            if (recentApps.isNotEmpty()) {
                Spacer(Modifier.height(22.dp))
                Text("Abertos recentemente", color = AuraWhite, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(recentApps, key = { it.packageName }) { app ->
                        Box(Modifier.width(70.dp)) {
                            AppIcon(app, (iconSize * 0.88f), labels, onClick = { onLaunch(app) })
                        }
                    }
                }
            }

            if (folders.isNotEmpty()) {
                Spacer(Modifier.height(22.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Suas pastas", color = AuraWhite, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    Text("GERENCIAR", color = accent, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { onOpenSettings() }.padding(6.dp))
                }
                Spacer(Modifier.height(12.dp))
                folders.forEach { folder ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).clickable { onFolder(folder) },
                        colors = CardDefaults.cardColors(containerColor = AuraPanel),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 15.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("▦", color = accent, fontSize = 22.sp)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(folder.name, color = AuraWhite, fontWeight = FontWeight.Medium)
                                Text("${folder.packages.size} aplicativos", color = AuraMuted, fontSize = 11.sp)
                            }
                            Text("›", color = AuraMuted, fontSize = 24.sp)
                        }
                    }
                }
            }
        }

        Column {
            Card(
                colors = CardDefaults.cardColors(containerColor = AuraPanelRaised.copy(alpha = 0.96f)),
                shape = RoundedCornerShape(26.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DockAction("▦", "Aplicativos", accent, onOpenDrawer)
                    DockAction("✦", "Favoritos", accent, onOpenFavorites)
                    DockAction("⚙", "Personalizar", accent, onOpenSettings)
                }
            }
            Spacer(Modifier.height(9.dp))
            Text(
                if (isDefault) "AURA é sua tela inicial" else "Escolha o AURA como sua tela inicial",
                color = AuraMuted,
                fontSize = 10.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally).clickable { onSetDefault() }.padding(5.dp)
            )
        }
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
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("‹", color = AuraWhite, fontSize = 34.sp,
                modifier = Modifier.clickable { onBack() }.padding(end = 12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Aplicativos", color = AuraWhite, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                Text("${apps.size} exibidos · $totalApps encontrados", color = AuraMuted, fontSize = 11.sp)
            }
            Text("AURA", color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        }
        Spacer(Modifier.height(15.dp))
        TextField(
            value = search,
            onValueChange = onSearch,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp)),
            placeholder = { Text("Buscar aplicativo", color = AuraMuted) },
            singleLine = true,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = AuraPanel,
                unfocusedContainerColor = AuraPanel,
                focusedTextColor = AuraWhite,
                unfocusedTextColor = AuraWhite,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = accent
            )
        )
        Spacer(Modifier.height(13.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            ChoiceChip("Todos", tab == 0, accent) { onTab(0) }
            ChoiceChip("Favoritos", tab == 1, accent) { onTab(1) }
            ChoiceChip("Ocultos", tab == 2, accent) { onTab(2) }
        }
        Spacer(Modifier.height(14.dp))
        if (apps.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nenhum aplicativo nesta seção.", color = AuraMuted, fontSize = 13.sp)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns.coerceIn(3, 6)),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                items(apps, key = { it.packageName }) { app ->
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
        Text("Toque para abrir · Segure para opções", color = AuraMuted.copy(alpha = 0.65f),
            fontSize = 10.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp))
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
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 21.dp, vertical = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("‹", color = AuraWhite, fontSize = 34.sp,
                modifier = Modifier.clickable { onBack() }.padding(end = 12.dp))
            Column {
                Text("Personalização", color = AuraWhite, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                Text("Configure sua experiência", color = AuraMuted, fontSize = 12.sp)
            }
        }

        SettingsHeading("TELA INICIAL", accent)
        SettingCard {
            Text("Tamanho dos ícones", color = AuraWhite, fontWeight = FontWeight.Medium)
            Text("${iconSize.toInt()} dp", color = AuraMuted, fontSize = 11.sp)
            Slider(value = iconSize, onValueChange = onIconSize, valueRange = 42f..72f)
            Divider(color = Color.White.copy(alpha = 0.08f))
            Text("Ícones por linha", color = AuraWhite, fontWeight = FontWeight.Medium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (3..6).forEach { count ->
                    ChoiceChip(count.toString(), columns == count, accent) { onColumns(count) }
                }
            }
            Divider(color = Color.White.copy(alpha = 0.08f))
            SettingSwitch("Mostrar nomes dos aplicativos", "Exibe o nome abaixo dos ícones", labels, onLabels)
        }

        SettingsHeading("APARÊNCIA", accent)
        SettingCard {
            Text("Estilo do fundo", color = AuraWhite, fontWeight = FontWeight.Medium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChip("Midnight", themeMode == 0, accent) { onTheme(0) }
                ChoiceChip("AMOLED", themeMode == 1, accent) { onTheme(1) }
            }
            Divider(color = Color.White.copy(alpha = 0.08f))
            Text("Cor de destaque", color = AuraWhite, fontWeight = FontWeight.Medium)
            Row(horizontalArrangement = Arrangement.spacedBy(13.dp), verticalAlignment = Alignment.CenterVertically) {
                AccentOptions.forEachIndexed { index, color ->
                    Box(
                        modifier = Modifier.size(34.dp).clip(CircleShape).background(color).clickable { onAccent(index) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (accentIndex == index) Text("✓", color = AuraBlack, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Divider(color = Color.White.copy(alpha = 0.08f))
            Text("Organização da gaveta", color = AuraWhite, fontWeight = FontWeight.Medium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChip("A–Z", sortMode == 0, accent) { onSort(0) }
                ChoiceChip("Recentes", sortMode == 1, accent) { onSort(1) }
            }
            Divider(color = Color.White.copy(alpha = 0.08f))
            SettingSwitch("Animações", "Ativa transições e movimentos suaves", animations, onAnimations)
        }

        SettingsHeading("ORGANIZAÇÃO", accent)
        SettingCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Pastas", color = AuraWhite, fontWeight = FontWeight.Medium)
                    Text("${folders.size} pastas criadas", color = AuraMuted, fontSize = 11.sp)
                }
                TextButton(onClick = onCreateFolder) { Text("Criar pasta", color = accent) }
            }
            if (folders.isNotEmpty()) {
                folders.forEach { folder ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(folder.name, color = AuraWhite, modifier = Modifier.weight(1f))
                        TextButton(onClick = { onRemoveFolder(folder) }) {
                            Text("Remover", color = Color(0xFFFF9B9B))
                        }
                    }
                }
            }
            Divider(color = Color.White.copy(alpha = 0.08f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Aplicativos ocultos", color = AuraWhite, fontWeight = FontWeight.Medium)
                    Text("$hiddenCount ocultos da gaveta", color = AuraMuted, fontSize = 11.sp)
                }
                TextButton(onClick = onOpenHidden) { Text("Gerenciar", color = accent) }
            }
        }

        SettingsHeading("SISTEMA", accent)
        SettingCard {
            Text(
                if (isDefault) "AURA é seu launcher padrão" else "AURA ainda não é o launcher padrão",
                color = AuraWhite,
                fontWeight = FontWeight.Medium
            )
            Text("O Android exibirá a confirmação oficial para definir a tela inicial.", color = AuraMuted, fontSize = 11.sp)
            Button(
                onClick = onSetDefault,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = AuraBlack),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Definir AURA como principal", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(18.dp))
        Button(
            onClick = onSave,
            modifier = Modifier.fillMaxWidth().height(53.dp),
            colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = AuraBlack),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Salvar configurações", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(20.dp))
        Text("AURA Launcher · Personalização salva apenas neste dispositivo",
            color = AuraMuted.copy(alpha = 0.65f), fontSize = 10.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally))
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
