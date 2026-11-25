package com.auralauncher

import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class LaunchableApp(
    val title: String,
    val packageName: String,
    val icon: Drawable
)

class MainActivity : ComponentActivity() {
    private lateinit var appState: androidx.compose.runtime.MutableState<List<LaunchableApp>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        window.statusBarColor = android.graphics.Color.rgb(9, 11, 16)
        @Suppress("DEPRECATION")
        window.navigationBarColor = android.graphics.Color.rgb(9, 11, 16)

        appState = mutableStateOf(loadLaunchableApps())
        val preferences = getSharedPreferences("aura_preferences", MODE_PRIVATE)
        setContent {
            AuraTheme {
                AuraLauncher(
                    apps = appState.value,
                    defaultIconSize = preferences.getFloat("icon_size", 54f),
                    defaultColumns = preferences.getInt("columns", 4),
                    defaultLabels = preferences.getBoolean("labels", true),
                    defaultAccent = preferences.getInt("accent", 0),
                    isDefaultLauncher = { isDefaultHome() },
                    onSetDefault = { requestDefaultLauncher() },
                    onLaunch = { packageName ->
                        packageManager.getLaunchIntentForPackage(packageName)?.let(::startActivity)
                    },
                    onSaveSettings = { iconSize, columns, labels, accent ->
                        preferences.edit()
                            .putFloat("icon_size", iconSize)
                            .putInt("columns", columns)
                            .putBoolean("labels", labels)
                            .putInt("accent", accent)
                            .apply()
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::appState.isInitialized) appState.value = loadLaunchableApps()
    }

    private fun isDefaultHome(): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolved?.activityInfo?.packageName == packageName
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

private val Night = Color(0xFF090B10)
private val Panel = Color(0xFF171B24)
private val Muted = Color(0xFF98A2B3)
private val White = Color(0xFFF7F8FC)
private val AccentOptions = listOf(
    Color(0xFFC7D2FE),
    Color(0xFFB9F3D0),
    Color(0xFFF5C7E7),
    Color(0xFFFFD59E)
)

@Composable
private fun AuraTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}

@Composable
private fun AuraLauncher(
    apps: List<LaunchableApp>,
    defaultIconSize: Float,
    defaultColumns: Int,
    defaultLabels: Boolean,
    defaultAccent: Int,
    isDefaultLauncher: () -> Boolean,
    onSetDefault: () -> Unit,
    onLaunch: (String) -> Unit,
    onSaveSettings: (Float, Int, Boolean, Int) -> Unit
) {
    var showDrawer by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var iconSize by remember { mutableStateOf(defaultIconSize) }
    var columns by remember { mutableStateOf(defaultColumns) }
    var labels by remember { mutableStateOf(defaultLabels) }
    var accentIndex by remember { mutableStateOf(defaultAccent.coerceIn(0, AccentOptions.lastIndex)) }
    var defaultStatus by remember { mutableStateOf(isDefaultLauncher()) }
    val accent = AccentOptions[accentIndex]
    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
    val date = SimpleDateFormat("EEEE, d 'de' MMMM", Locale("pt", "BR"))
        .format(Date()).replaceFirstChar { it.uppercase(Locale("pt", "BR")) }
    val filteredApps = apps.filter { it.title.contains(search, ignoreCase = true) }

    Surface(modifier = Modifier.fillMaxSize(), color = Night) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFF171D2A), Night, Color(0xFF08090D))))
        ) {
            if (showSettings) {
                SettingsScreen(
                    iconSize = iconSize,
                    columns = columns,
                    labels = labels,
                    accentIndex = accentIndex,
                    isDefault = defaultStatus,
                    onBack = { showSettings = false },
                    onIconSize = { iconSize = it },
                    onColumns = { columns = it },
                    onLabels = { labels = it },
                    onAccent = { accentIndex = it },
                    onSetDefault = onSetDefault,
                    onSave = {
                        onSaveSettings(iconSize, columns, labels, accentIndex)
                        defaultStatus = isDefaultLauncher()
                        showSettings = false
                    }
                )
            } else if (showDrawer) {
                DrawerScreen(
                    apps = filteredApps,
                    search = search,
                    onSearch = { search = it },
                    iconSize = iconSize,
                    columns = columns,
                    labels = labels,
                    onBack = { showDrawer = false; search = "" },
                    onLaunch = onLaunch
                )
            } else {
                HomeScreen(
                    apps = apps,
                    time = time,
                    date = date,
                    accent = accent,
                    iconSize = iconSize,
                    labels = labels,
                    isDefault = defaultStatus,
                    onOpenDrawer = { showDrawer = true },
                    onOpenSettings = { showSettings = true },
                    onSetDefault = onSetDefault,
                    onLaunch = onLaunch
                )
            }
        }
    }
}

@Composable
private fun HomeScreen(
    apps: List<LaunchableApp>,
    time: String,
    date: String,
    accent: Color,
    iconSize: Float,
    labels: Boolean,
    isDefault: Boolean,
    onOpenDrawer: () -> Unit,
    onOpenSettings: () -> Unit,
    onSetDefault: () -> Unit,
    onLaunch: (String) -> Unit
) {
    val favorites = apps.take(4)
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("A U R A", color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 5.sp)
                    Text("Seu espaço. Seu ritmo.", color = Muted, fontSize = 12.sp)
                }
                Text(
                    text = if (isDefault) "ATIVO" else "CONFIGURAR",
                    color = if (isDefault) Color(0xFF9DE5BD) else accent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(CircleShape).background(Panel).clickable { onSetDefault() }.padding(horizontal = 13.dp, vertical = 9.dp)
                )
            }
            Spacer(Modifier.height(72.dp))
            Text(time, color = White, fontSize = 76.sp, fontWeight = FontWeight.Light, letterSpacing = (-4).sp)
            Text(date, color = Muted, fontSize = 14.sp)
            Spacer(Modifier.height(30.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.055f)),
                shape = RoundedCornerShape(26.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Tudo no seu alcance", color = White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Text("Organize seus aplicativos do seu jeito.", color = Muted, fontSize = 12.sp)
                    }
                    Text("✦", color = accent, fontSize = 30.sp)
                }
            }
            Spacer(Modifier.height(28.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Acesso rápido", color = White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text("EDITAR EM BREVE", color = Muted.copy(alpha = 0.65f), fontSize = 9.sp, letterSpacing = 1.sp)
            }
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                favorites.forEach { app ->
                    AppIcon(app, iconSize, labels, accent, onClick = { onLaunch(app.packageName) })
                }
            }
        }

        Column {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF202634).copy(alpha = 0.92f)),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DockAction("▦", "Aplicativos", accent, onOpenDrawer)
                    DockAction("◈", "Personalizar", accent, onOpenSettings)
                    DockAction("⚙", "Sistema", accent) { onSetDefault() }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = if (isDefault) "AURA é sua tela inicial" else "Defina o AURA como tela inicial padrão",
                color = Muted,
                fontSize = 11.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally).clickable { onSetDefault() }.padding(8.dp)
            )
        }
    }
}

@Composable
private fun DrawerScreen(
    apps: List<LaunchableApp>,
    search: String,
    onSearch: (String) -> Unit,
    iconSize: Float,
    columns: Int,
    labels: Boolean,
    onBack: () -> Unit,
    onLaunch: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("‹", color = White, fontSize = 34.sp, modifier = Modifier.clickable { onBack() }.padding(end = 12.dp))
            Column {
                Text("Aplicativos", color = White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                Text("${apps.size} disponíveis", color = Muted, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(18.dp))
        TextField(
            value = search,
            onValueChange = onSearch,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)),
            placeholder = { Text("Buscar aplicativo", color = Muted) },
            singleLine = true,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Panel,
                unfocusedContainerColor = Panel,
                focusedTextColor = White,
                unfocusedTextColor = White,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = Color(0xFFC7D2FE)
            )
        )
        Spacer(Modifier.height(16.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            items(apps, key = { it.packageName }) { app ->
                AppIcon(app, iconSize, labels, Color(0xFFC7D2FE), onClick = { onLaunch(app.packageName) })
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    iconSize: Float,
    columns: Int,
    labels: Boolean,
    accentIndex: Int,
    isDefault: Boolean,
    onBack: () -> Unit,
    onIconSize: (Float) -> Unit,
    onColumns: (Int) -> Unit,
    onLabels: (Boolean) -> Unit,
    onAccent: (Int) -> Unit,
    onSetDefault: () -> Unit,
    onSave: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("‹", color = White, fontSize = 34.sp, modifier = Modifier.clickable { onBack() }.padding(end = 12.dp))
            Column {
                Text("Personalização", color = White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                Text("Deixe o AURA com a sua identidade", color = Muted, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(26.dp))
        Text("TELA INICIAL", color = AccentOptions[accentIndex], fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        Spacer(Modifier.height(12.dp))
        SettingCard {
            Text("Tamanho dos ícones", color = White, fontWeight = FontWeight.Medium)
            Text("${iconSize.toInt()} dp", color = Muted, fontSize = 12.sp)
            Slider(value = iconSize, onValueChange = onIconSize, valueRange = 42f..68f)
            Spacer(Modifier.height(8.dp))
            Text("Ícones por linha", color = White, fontWeight = FontWeight.Medium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (3..5).forEach { count ->
                    ChoiceChip(count.toString(), columns == count) { onColumns(count) }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Mostrar nomes", color = White, fontWeight = FontWeight.Medium)
                    Text("Exibe o nome abaixo de cada ícone", color = Muted, fontSize = 11.sp)
                }
                Switch(checked = labels, onCheckedChange = onLabels)
            }
        }
        Spacer(Modifier.height(20.dp))
        Text("COR DE DESTAQUE", color = AccentOptions[accentIndex], fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        Spacer(Modifier.height(12.dp))
        SettingCard {
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                AccentOptions.forEachIndexed { index, color ->
                    Box(
                        modifier = Modifier.size(38.dp).clip(CircleShape).background(color)
                            .clickable { onAccent(index) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (accentIndex == index) Text("✓", color = Night, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Text("LAUNCHER PADRÃO", color = AccentOptions[accentIndex], fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        Spacer(Modifier.height(12.dp))
        SettingCard {
            Text(
                if (isDefault) "AURA está definido como padrão" else "AURA ainda não é seu launcher padrão",
                color = White,
                fontWeight = FontWeight.Medium
            )
            Text("O Android abrirá a tela do sistema para você confirmar.", color = Muted, fontSize = 11.sp)
            Button(
                onClick = onSetDefault,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = AccentOptions[accentIndex], contentColor = Night),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Definir AURA como principal", fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.weight(1f))
        Button(
            onClick = onSave,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AccentOptions[accentIndex], contentColor = Night),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Salvar personalização", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SettingCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Panel),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            content()
        }
    }
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (selected) Night else White,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(RoundedCornerShape(12.dp))
            .background(if (selected) Color(0xFFC7D2FE) else Color(0xFF2A3040))
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 10.dp)
    )
}

@Composable
private fun DockAction(symbol: String, title: String, accent: Color, onClick: () -> Unit) {
    Column(
        modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable { onClick() }.padding(horizontal = 12.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(symbol, color = accent, fontSize = 23.sp)
        Text(title, color = White, fontSize = 10.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun AppIcon(
    app: LaunchableApp,
    iconSize: Float,
    labels: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    val bitmap = remember(app.packageName) { app.icon.toImageBitmap() }
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick).padding(vertical = 3.dp),
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
                color = White,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
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
