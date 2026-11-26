package com.auralauncher

import android.appwidget.AppWidgetProviderInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.content.pm.PackageManager
import android.widget.FrameLayout
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.auralauncher.widgets.AuraWidgetHostController
import org.json.JSONArray
import org.json.JSONObject


data class HomeShortcutPlacement(
    val packageName: String,
    val xDp: Float,
    val yDp: Float
)

data class HomeShortcutItem(
    val packageName: String,
    val title: String,
    val icon: Drawable
)

object HomeShortcutCodec {
    fun encode(items: List<HomeShortcutPlacement>): String {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject()
                    .put("package", item.packageName)
                    .put("x", item.xDp.coerceAtLeast(0f).toDouble())
                    .put("y", item.yDp.coerceAtLeast(0f).toDouble())
            )
        }
        return array.toString()
    }

    fun decode(raw: String?): List<HomeShortcutPlacement> = runCatching {
        val array = JSONArray(raw ?: "[]")
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val packageName = item.optString("package").trim()
            if (packageName.isBlank() || packageName.length > 255) return@mapNotNull null
            HomeShortcutPlacement(
                packageName = packageName,
                xDp = item.optDouble("x", 12.0).toFloat().coerceIn(0f, 2000f),
                yDp = item.optDouble("y", 12.0).toFloat().coerceIn(0f, 4000f)
            )
        }.distinctBy { it.packageName }
    }.getOrDefault(emptyList())
}

@Composable
fun HomeShortcutWorkspace(
    items: List<HomeShortcutItem>,
    placements: List<HomeShortcutPlacement>,
    accent: Color,
    editable: Boolean,
    onLaunch: (String) -> Unit,
    onMove: (HomeShortcutPlacement) -> Unit,
    onRemove: (String) -> Unit
) {
    val maxBottom = placements.maxOfOrNull { it.yDp + 108f } ?: 0f
    val workspaceHeight = (maxBottom + 160f).coerceAtLeast(320f).dp
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth().height(workspaceHeight)
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xFFF8F5FF))
            .border(1.dp, Color(0xFFE6DFF2), RoundedCornerShape(22.dp))
    ) {
        val availableWidth = maxWidth
        placements.forEach { placement ->
            val item = items.firstOrNull { it.packageName == placement.packageName } ?: return@forEach
            var x by remember(placement.packageName) { mutableFloatStateOf(placement.xDp) }
            var y by remember(placement.packageName) { mutableFloatStateOf(placement.yDp) }
            LaunchedEffect(placement.xDp, placement.yDp) {
                x = placement.xDp
                y = placement.yDp
            }
            Card(
                modifier = Modifier.offset {
                    IntOffset(
                        with(density) { x.dp.roundToPx() },
                        with(density) { y.dp.roundToPx() }
                    )
                }.width(84.dp).height(108.dp).clickable { onLaunch(item.packageName) },
                shape = RoundedCornerShape(19.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFFFF))
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 5.dp, vertical = 3.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(17.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.weight(1f).pointerInput(placement.packageName, availableWidth, editable) {
                                if (editable) {
                                    detectDragGestures(
                                        onDragEnd = { onMove(placement.copy(xDp = x, yDp = y)) }
                                    ) { change, amount ->
                                        change.consume()
                                        val maxX = (availableWidth - 84.dp).value.coerceAtLeast(0f)
                                        val maxY = (workspaceHeight - 108.dp).value.coerceAtLeast(0f)
                                        x = (x + amount.x / density.density).coerceIn(0f, maxX)
                                        y = (y + amount.y / density.density).coerceIn(0f, maxY)
                                    }
                                }
                            },
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text("⠿", color = accent, fontSize = 13.sp)
                        }
                        if (editable) {
                            Text(
                                "×",
                                color = Color(0xFFFFA0A0),
                                fontSize = 14.sp,
                                modifier = Modifier.clickable { onRemove(item.packageName) }
                            )
                        }
                    }
                    Image(
                        bitmap = remember(item.packageName) { item.icon.toShortcutImageBitmap() },
                        contentDescription = item.title,
                        modifier = Modifier.size(53.dp).clip(RoundedCornerShape(15.dp))
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        item.title,
                        color = Color(0xFF302A43),
                        fontSize = 9.sp,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private fun Drawable.toShortcutImageBitmap(): ImageBitmap {
    val width = intrinsicWidth.coerceIn(1, 512)
    val height = intrinsicHeight.coerceIn(1, 512)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bitmap.asImageBitmap()
}

data class WidgetPlacement(
    val widgetId: Int,
    val xDp: Float,
    val yDp: Float,
    val widthDp: Float = 270f,
    val heightDp: Float = 178f
)

object WidgetPlacementCodec {
    fun encode(items: List<WidgetPlacement>): String {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject()
                    .put("id", item.widgetId)
                    .put("x", item.xDp.coerceAtLeast(0f).toDouble())
                    .put("y", item.yDp.coerceAtLeast(0f).toDouble())
                    .put("w", item.widthDp.coerceIn(180f, 440f).toDouble())
                    .put("h", item.heightDp.coerceIn(120f, 480f).toDouble())
            )
        }
        return array.toString()
    }

    fun decode(raw: String?): List<WidgetPlacement> = runCatching {
        val array = JSONArray(raw ?: "[]")
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val id = item.optInt("id", -1)
            if (id < 0) return@mapNotNull null
            WidgetPlacement(
                widgetId = id,
                xDp = item.optDouble("x", 12.0).toFloat().coerceIn(0f, 2000f),
                yDp = item.optDouble("y", 12.0).toFloat().coerceIn(0f, 4000f),
                widthDp = item.optDouble("w", 270.0).toFloat().coerceIn(180f, 440f),
                heightDp = item.optDouble("h", 178.0).toFloat().coerceIn(120f, 480f)
            )
        }.distinctBy { it.widgetId }
    }.getOrDefault(emptyList())
}

@Composable
fun WidgetPickerDialog(
    providers: List<AppWidgetProviderInfo>,
    packageManager: PackageManager,
    onDismiss: () -> Unit,
    onChoose: (AppWidgetProviderInfo) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFFFFFFFF),
        titleContentColor = Color(0xFF302A43),
        textContentColor = Color(0xFF817990),
        title = { Text("Adicionar widget", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (providers.isEmpty()) {
                    Text("Nenhum aplicativo instalado oferece widgets.")
                } else {
                    Text("Escolha um widget disponível no dispositivo.", fontSize = 11.sp)
                    providers.forEach { provider ->
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFFF8F5FF))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    runCatching { provider.loadLabel(packageManager).toString() }
                                        .getOrDefault(provider.provider.packageName),
                                    color = Color(0xFF302A43),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "${provider.minWidth} × ${provider.minHeight} dp",
                                    color = Color(0xFF817990),
                                    fontSize = 9.sp
                                )
                            }
                            TextButton(onClick = { onChoose(provider) }) {
                                Text("Adicionar", color = Color(0xFF8067D9))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Fechar", color = Color(0xFF817990)) }
        }
    )
}

/**
 * Real Android AppWidgetHostView workspace. Drag each widget's header to move
 * it around the canvas; positions are stored in WidgetPlacementCodec.
 */
@Composable
fun WidgetWorkspace(
    placements: List<WidgetPlacement>,
    controller: AuraWidgetHostController,
    accent: Color,
    editable: Boolean,
    onMove: (WidgetPlacement) -> Unit,
    onRemove: (Int) -> Unit
) {
    val maxBottom = placements.maxOfOrNull { it.yDp + it.heightDp } ?: 0f
    val workspaceHeight = (maxBottom + 260f).coerceAtLeast(460f).dp
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth().height(workspaceHeight)
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xFFF8F5FF))
            .border(1.dp, Color(0xFFE6DFF2), RoundedCornerShape(22.dp))
    ) {
        val availableWidth = maxWidth
        placements.forEach { placement ->
            var x by remember(placement.widgetId) { mutableFloatStateOf(placement.xDp) }
            var y by remember(placement.widgetId) { mutableFloatStateOf(placement.yDp) }
            var width by remember(placement.widgetId) { mutableFloatStateOf(placement.widthDp) }
            var height by remember(placement.widgetId) { mutableFloatStateOf(placement.heightDp) }
            LaunchedEffect(placement.xDp, placement.yDp, placement.widthDp, placement.heightDp) {
                x = placement.xDp
                y = placement.yDp
                width = placement.widthDp
                height = placement.heightDp
            }
            val resizeMode = controller.manager.getAppWidgetInfo(placement.widgetId)?.resizeMode ?: 0
            val canResizeHorizontally = (resizeMode and 1) != 0
            val canResizeVertically = (resizeMode and 2) != 0
            val canResize = canResizeHorizontally || canResizeVertically
            Card(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            with(density) { x.dp.roundToPx() },
                            with(density) { y.dp.roundToPx() }
                        )
                    }
                    .width(width.dp)
                    .height(height.dp),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFFFF))
            ) {
                Column(Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .background(Color(0xFFEAE4F7))
                            .then(
                                if (editable) Modifier.pointerInput(placement.widgetId, availableWidth, editable) {
                                    detectDragGestures(
                                        onDragEnd = { onMove(placement.copy(xDp = x, yDp = y, widthDp = width, heightDp = height)) }
                                    ) { change, amount ->
                                        change.consume()
                                        val maxX = (availableWidth - width.dp).value.coerceAtLeast(0f)
                                        val maxY = (workspaceHeight - height.dp).value.coerceAtLeast(0f)
                                        x = (x + amount.x / density.density).coerceIn(0f, maxX)
                                        y = (y + amount.y / density.density).coerceIn(0f, maxY)
                                    }
                                } else Modifier
                            )
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("✥", color = accent, fontSize = 14.sp)
                        Spacer(Modifier.width(7.dp))
                        Text(if (editable) "MOVER WIDGET" else "WIDGET", color = Color(0xFF655B79), fontSize = 8.sp, modifier = Modifier.weight(1f))
                        if (editable) {
                            TextButton(onClick = { onRemove(placement.widgetId) }) {
                                Text("REMOVER", color = Color(0xFFFFA0A0), fontSize = 8.sp)
                            }
                        }
                    }
                    AndroidView(
                        factory = { ctx ->
                            controller.createHostView(placement.widgetId) ?: FrameLayout(ctx)
                        },
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        update = {}
                    )
                    if (editable && canResize) {
                        Row(
                            modifier = Modifier.fillMaxWidth().height(22.dp).background(Color(0xFFEAE4F7)),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.size(22.dp)
                                    .pointerInput(placement.widgetId, width, height) {
                                        detectDragGestures(
                                            onDragEnd = {
                                                onMove(placement.copy(xDp = x, yDp = y, widthDp = width, heightDp = height))
                                            }
                                        ) { change, amount ->
                                            change.consume()
                                            if (canResizeHorizontally) {
                                                width = (width + amount.x / density.density).coerceIn(180f, (availableWidth.value - x).coerceAtLeast(180f))
                                            }
                                            if (canResizeVertically) {
                                                height = (height + amount.y / density.density).coerceIn(120f, 480f)
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("◢", color = accent, fontSize = 16.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
