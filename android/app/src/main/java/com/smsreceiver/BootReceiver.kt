package com.smsreceiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) {
            return
        }

        val preferences = AppPreferences(context)
        if (!preferences.serviceEnabled || !preferences.isConfigured()) {
            return
        }

        SmsForwardService.start(context)
    }
}
