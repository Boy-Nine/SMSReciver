package com.smsreceiver

import android.content.Context

class AppPreferences(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var serverUrl: String
        get() = prefs.getString(KEY_SERVER_URL, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_SERVER_URL, value.trim()).apply()
        }

    var deviceName: String
        get() = prefs.getString(KEY_DEVICE_NAME, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_DEVICE_NAME, value.trim()).apply()
        }

    var phoneNumber: String
        get() = prefs.getString(KEY_PHONE_NUMBER, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_PHONE_NUMBER, value.trim()).apply()
        }

    var deviceId: String
        get() = prefs.getString(KEY_DEVICE_ID, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_DEVICE_ID, value.trim()).apply()
        }

    var apiKey: String
        get() = prefs.getString(KEY_API_KEY, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_API_KEY, value.trim()).apply()
        }

    fun isConfigured(): Boolean {
        return serverUrl.isNotBlank() && deviceId.isNotBlank() && apiKey.isNotBlank()
    }

    companion object {
        private const val PREFS_NAME = "sms_receiver_prefs"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_DEVICE_NAME = "device_name"
        private const val KEY_PHONE_NUMBER = "phone_number"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_API_KEY = "api_key"
    }
}
