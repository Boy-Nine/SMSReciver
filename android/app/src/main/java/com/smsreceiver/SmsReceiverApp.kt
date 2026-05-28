package com.smsreceiver

import android.app.Application

class SmsReceiverApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SmsAutoStartHelper.tryStart(this)
    }
}
