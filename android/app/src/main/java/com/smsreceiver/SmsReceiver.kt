package com.smsreceiver

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            return
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) {
            return
        }

        val sender = messages.first().displayOriginatingAddress.orEmpty()
        val body = messages.joinToString(separator = "") { it.messageBody.orEmpty() }
        if (sender.isBlank() || body.isBlank()) {
            return
        }

        val receivedAt = SmsForwardService.formatNow()
        val pendingResult = goAsync()

        Thread {
            try {
                dispatchSms(context, sender, body, receivedAt)
            } finally {
                pendingResult.finish()
            }
        }.start()
    }

    private fun dispatchSms(context: Context, sender: String, body: String, receivedAt: String) {
        val result = SmsForwardHelper.forwardSms(context, sender, body, receivedAt)
        if (result is ForwardResult.AlreadyForwarded) {
            return
        }

        if (result is ForwardResult.Success || result is ForwardResult.Failure) {
            SmsForwardService.showStatusNotification(context, result, sender)
        }

        if (result is ForwardResult.Failure) {
            NotificationCaptureHelper.captureFromInboxFallback(context, showRedactedHint = false)
        }

        try {
            SmsForwardService.start(context)
        } catch (_: Exception) {
            // 服务拉不起来时，上面已直连上报。
        }
    }
}
