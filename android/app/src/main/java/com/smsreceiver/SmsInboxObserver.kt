package com.smsreceiver

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper

class SmsInboxObserver(
    context: Context,
    private val onScanComplete: (ScanResult) -> Unit,
) : ContentObserver(Handler(Looper.getMainLooper())) {
    private val appContext = context.applicationContext

    override fun onChange(selfChange: Boolean) {
        Thread {
            val result = SmsInboxScanner.scanAndForward(appContext)
            if (result.forwardedCount > 0 || result.missingReadSmsPermission || result.lastForward != null) {
                Handler(Looper.getMainLooper()).post {
                    onScanComplete(result)
                }
            }
        }.start()
    }
}
