package com.auralauncher.widgets

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager

/**
 * Host wrapper for real Android widgets. Provider binding/configuration remains
 * owned by the platform picker and each widget's own configuration Activity.
 */
class AuraWidgetHostController(private val activity: Activity) {
    val manager: AppWidgetManager = AppWidgetManager.getInstance(activity)
    val host: AppWidgetHost = AppWidgetHost(activity, HOST_ID)

    fun startListening() = host.startListening()

    fun stopListening() = runCatching { host.stopListening() }

    fun allocateWidgetId(): Int = host.allocateAppWidgetId()

    fun createHostView(widgetId: Int): AppWidgetHostView? {
        val provider = manager.getAppWidgetInfo(widgetId) ?: return null
        return host.createView(activity, widgetId, provider)
    }

    fun deleteWidget(widgetId: Int) {
        runCatching { host.deleteAppWidgetId(widgetId) }
    }

    companion object {
        private const val HOST_ID = 0x41555241 // "AURA"
    }
}
