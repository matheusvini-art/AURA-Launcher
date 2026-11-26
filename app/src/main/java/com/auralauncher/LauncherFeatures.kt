package com.auralauncher

import android.appwidget.AppWidgetProviderInfo
import android.content.pm.PackageManager
import android.widget.FrameLayout
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.auralauncher.widgets.AuraWidgetHostController
import org.json.JSONArray
import org.json.JSONObject

data class WidgetPlacement(
    val widgetId: Int,
    val xDp: Float,
    val yDp: Float
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
                yDp = item.optDouble("y", 12.0).toFloat().coerceIn(0f, 4000f)
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
        containerColor = Color(0xFF111A2B),
        titleContentColor = Color(0xFFF2F7FF),
        textContentColor = Color(0xFF91A4C2),
        title = { Text("Adicionar widget", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (providers.isEmpty()) {
                    Text("Nenhum aplicativo instalado oferece widgets.")
                } else {
                    Text("Escolha um widget disponível no dispositivo.", fontSize = 11.sp)
                    providers.forEach { provider ->
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF1A2A40))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    runCatching { provider.loadLabel(packageManager).toString() }
                                        .getOrDefault(provider.provider.packageName),
                                    color = Color(0xFFF2F7FF),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "\${provider.minWidth} × \${provider.minHeight} dp",
                                    color = Color(0xFF91A4C2),
                                    fontSize = 9.sp
                                )
                            }
                            TextButton(onClick = { onChoose(provider) }) {
                                Text("Adicionar", color = Color(0xFF45E6FF))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Fechar", color = Color(0xFF91A4C2)) }
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
    onMove: (WidgetPlacement) -> Unit,
    onRemove: (Int) -> Unit
) {
    val maxBottom = placements.maxOfOrNull { it.yDp } ?: 0f
    val workspaceHeight = (maxBottom + 190f).coerceAtLeast(190f).dp
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth().height(workspaceHeight)
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0x33111F33))
            .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(22.dp))
    ) {
        placements.forEach { placement ->
            var x by remember(placement.widgetId) { mutableFloatStateOf(placement.xDp) }
            var y by remember(placement.widgetId) { mutableFloatStateOf(placement.yDp) }
            LaunchedEffect(placement.xDp, placement.yDp) {
                x = placement.xDp
                y = placement.yDp
            }
            Card(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            with(density) { x.dp.roundToPx() },
                            with(density) { y.dp.roundToPx() }
                        )
                    }
                    .width(270.dp)
                    .height(178.dp),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF18243A))
            ) {
                Column(Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .background(Color(0xFF243750))
                            .pointerInput(placement.widgetId, maxWidth) {
                                detectDragGestures(
                                    onDragEnd = { onMove(placement.copy(xDp = x, yDp = y)) }
                                ) { change, amount ->
                                    change.consume()
                                    val maxX = (maxWidth - 270.dp).value.coerceAtLeast(0f)
                                    x = (x + amount.x / density.density).coerceIn(0f, maxX)
                                    y = (y + amount.y / density.density).coerceIn(0f, 2400f)
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("✥", color = accent, fontSize = 14.sp)
                        Spacer(Modifier.width(7.dp))
                        Text("MOVER WIDGET", color = Color(0xFFDCE8F6), fontSize = 8.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = { onRemove(placement.widgetId) }) {
                            Text("REMOVER", color = Color(0xFFFFA0A0), fontSize = 8.sp)
                        }
                    }
                    AndroidView(
                        factory = { ctx ->
                            controller.createHostView(placement.widgetId) ?: FrameLayout(ctx)
                        },
                        modifier = Modifier.fillMaxSize(),
                        update = {}
                    )
                }
            }
        }
    }
}
