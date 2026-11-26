package com.auralauncher.notifications

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.content.Intent
import org.json.JSONObject

/**
 * Notification access is granted explicitly by the user in Android settings.
 * The launcher stores only per-package counts; notification content is never persisted.
 */
class AuraNotificationListenerService : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        publishCounts()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        publishCounts()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        publishCounts()
    }

    private fun publishCounts() {
        val counts = activeNotifications
            .orEmpty()
            .filterNot { it.isClearable.not() && it.notification.flags and android.app.Notification.FLAG_ONGOING_EVENT != 0 }
            .groupingBy { it.packageName }
            .eachCount()
        val json = JSONObject()
        counts.forEach { (packageName, count) -> json.put(packageName, count) }
        getSharedPreferences(PREFERENCES, MODE_PRIVATE)
            .edit()
            .putString(KEY_COUNTS, json.toString())
            .apply()
        sendBroadcast(
            Intent(ACTION_COUNTS_CHANGED).setPackage(packageName)
        )
    }

    companion object {
        const val PREFERENCES = "aura_preferences"
        const val KEY_COUNTS = "notification_counts"
        const val ACTION_COUNTS_CHANGED = "com.auralauncher.NOTIFICATION_COUNTS_CHANGED"
    }
}
