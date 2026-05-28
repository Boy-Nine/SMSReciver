package com.smsreceiver

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.text.TextUtils

object NotificationAccessHelper {
    fun isEnabled(context: Context): Boolean {
        val flat = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners",
        )
        if (flat.isNullOrBlank()) {
            return false
        }

        val component = ComponentName(context, SmsNotificationListener::class.java)
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(flat)
        while (splitter.hasNext()) {
            val enabledComponent = ComponentName.unflattenFromString(splitter.next())
            if (enabledComponent != null && enabledComponent == component) {
                return true
            }
        }
        return false
    }

    fun requestRebind(context: Context) {
        if (!isEnabled(context)) {
            return
        }

        NotificationListenerService.requestRebind(
            ComponentName(context, SmsNotificationListener::class.java),
        )
    }
}
