package com.smsreceiver

object SmsDedup {
    @Volatile
    private var lastKey: String? = null

    @Volatile
    private var lastTimeMs: Long = 0

    fun shouldSkip(sender: String, body: String, windowMs: Long = 300_000): Boolean {
        val key = buildKey(sender, body)
        val now = System.currentTimeMillis()
        synchronized(this) {
            if (key == lastKey && now - lastTimeMs < windowMs) {
                return true
            }
            lastKey = key
            lastTimeMs = now
            return false
        }
    }

    private fun buildKey(sender: String, body: String): String {
        return "${sender.trim()}|${body.trim()}"
    }
}
