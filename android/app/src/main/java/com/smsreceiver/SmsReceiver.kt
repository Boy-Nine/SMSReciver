package com.smsreceiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
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
        val serviceIntent = Intent(context, SmsForwardService::class.java).apply {
            action = SmsForwardService.ACTION_FORWARD_SMS
            putExtra(SmsForwardService.EXTRA_SENDER, sender)
            putExtra(SmsForwardService.EXTRA_BODY, body)
            putExtra(SmsForwardService.EXTRA_RECEIVED_AT, receivedAt)
        }
        context.startForegroundService(serviceIntent)
    }
}
