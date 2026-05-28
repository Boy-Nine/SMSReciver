package com.smsreceiver

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

object SmsAutoStartHelper {
    fun tryStart(context: Context) {
        val appContext = context.applicationContext
        val preferences = AppPreferences(appContext)
        if (!preferences.isConfigured()) {
            return
        }

        if (!hasRequiredPermissions(appContext)) {
            return
        }

        if (!preferences.inboxBaselineSet) {
            SmsInboxScanner.markBaseline(appContext)
            preferences.inboxBaselineSet = true
        }

        preferences.serviceEnabled = true

        try {
            SmsForwardService.start(appContext)
        } catch (_: Exception) {
            return
        }

        if (NotificationAccessHelper.isEnabled(appContext)) {
            NotificationAccessHelper.requestRebind(appContext)
        }
    }

    fun hasRequiredPermissions(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECEIVE_SMS,
        ) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_SMS,
            ) == PackageManager.PERMISSION_GRANTED
    }
}
