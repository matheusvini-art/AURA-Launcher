package com.auralauncher

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        @Suppress("DEPRECATION")
        window.statusBarColor = android.graphics.Color.rgb(10, 12, 18)
        @Suppress("DEPRECATION")
        window.navigationBarColor = android.graphics.Color.rgb(10, 12, 18)

        val installedApps = loadLaunchableApps()
        setContent {
            AuraTheme {
                HomeScreen(
                    apps = installedApps,
                    onLaunch = { packageName ->
                        packageManager.getLaunchIntentForPackage(packageName)?.let(::startActivity)
                    }
                )
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun loadLaunchableApps(): List<LaunchableApp> {
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)
            .mapNotNull { resolveInfo ->
                val activityInfo = resolveInfo.activityInfo ?: return@mapNotNull null
                LaunchableApp(
                    title = resolveInfo.loadLabel(packageManager).toString(),
                    packageName = activityInfo.packageName,
                    icon = resolveInfo.loadIcon(packageManager)
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.title.lowercase(Locale.getDefault()) }
    }
}

private val AuraBackground = Color(0xFF090B10)
private val AuraPanel = Color(0xFF171B24)
private val AuraMuted = Color(0xFF9AA4B2)
private val AuraAccent = Color(0xFFC7D2FE)

@Composable
private fun AuraTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}

@Composable
private fun HomeScreen(
    apps: List<LaunchableApp>,
    onLaunch: (String) -> Unit
) {
    var searchText by remember { mutableStateOf("") }
    val visibleApps = remember(apps, searchText) {
        apps.filter { it.title.contains(searchText, ignoreCase = true) }
    }
    val currentTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
    val currentDate = SimpleDateFormat("EEEE, d 'de' MMMM", Locale("pt", "BR"))
        .format(Date())
        .replaceFirstChar { it.uppercase(Locale("pt", "BR")) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = AuraBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp, vertical = 24.dp)
        ) {
            Spacer(Modifier.height(24.dp))
            Text(
                text = "A U R A",
                color = AuraAccent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = currentTime,
                color = Color.White,
                fontSize = 68.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = (-3).sp
            )
            Text(
                text = currentDate,
                color = AuraMuted,
                fontSize = 14.sp
            )
            Spacer(Modifier.height(30.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Aplicativos",
                    color = Color.White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = visibleApps.size.toString(),
                    color = AuraMuted,
                    fontSize = 13.sp
                )
            }
            Spacer(Modifier.height(14.dp))

            TextField(
                value = searchText,
                onValueChange = { searchText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp)),
                placeholder = { Text("Buscar aplicativo", color = AuraMuted) },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = AuraPanel,
                    unfocusedContainerColor = AuraPanel,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = AuraAccent
                )
            )
            Spacer(Modifier.height(18.dp))

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 72.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                items(visibleApps, key = { it.packageName }) { app ->
                    AppIcon(app = app, onClick = { onLaunch(app.packageName) })
                }
            }

            Text(
                text = "Toque em um ícone para abrir",
                color = AuraMuted.copy(alpha = 0.7f),
                fontSize = 11.sp,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun AppIcon(app: LaunchableApp, onClick: () -> Unit) {
    val iconBitmap = remember(app.packageName) { app.icon.toImageBitmap() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            bitmap = iconBitmap,
            contentDescription = app.title,
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(15.dp))
        )
        Spacer(Modifier.height(7.dp))
        Text(
            text = app.title,
            color = Color.White,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun Drawable.toImageBitmap(): ImageBitmap {
    val bitmapWidth = intrinsicWidth.coerceAtLeast(1)
    val bitmapHeight = intrinsicHeight.coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bitmap.asImageBitmap()
}
