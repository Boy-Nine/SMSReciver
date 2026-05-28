package com.smsreceiver

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class SmsNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.isOngoing) {
            return
        }

        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return
        val content = NotificationCaptureHelper.extractNotificationContent(extras)
        NotificationCaptureHelper.handleSmsNotification(
            context = applicationContext,
            packageName = sbn.packageName,
            sender = content.sender,
            body = content.body,
        )
    }
}
