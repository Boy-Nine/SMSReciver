package com.smsreceiver

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors

class SmsForwardService : Service() {
    private val apiClient = ApiClient()
    private val workerExecutor = Executors.newSingleThreadExecutor()
    private lateinit var preferences: AppPreferences
    private lateinit var pendingStore: PendingMessageStore

    override fun onCreate() {
        super.onCreate()
        preferences = AppPreferences(this)
        pendingStore = PendingMessageStore(this)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("等待短信"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_FORWARD_SMS -> {
                val sender = intent.getStringExtra(EXTRA_SENDER).orEmpty()
                val body = intent.getStringExtra(EXTRA_BODY).orEmpty()
                val receivedAt = intent.getStringExtra(EXTRA_RECEIVED_AT) ?: formatNow()
                workerExecutor.execute {
                    handleIncomingSms(sender, body, receivedAt)
                }
            }

            ACTION_FLUSH_PENDING -> workerExecutor.execute { flushPendingMessages() }

            else -> workerExecutor.execute { flushPendingMessages() }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        workerExecutor.shutdownNow()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun handleIncomingSms(sender: String, body: String, receivedAt: String) {
        if (!preferences.isConfigured()) {
            pendingStore.enqueue(sender, body, receivedAt)
            updateNotification("未配置，短信已加入待发送队列")
            return
        }

        val result = apiClient.sendInboundSms(
            serverUrl = preferences.serverUrl,
            deviceId = preferences.deviceId,
            apiKey = preferences.apiKey,
            sender = sender,
            body = body,
            receivedAt = receivedAt,
            phoneNumber = preferences.phoneNumber.ifBlank { null },
        )

        if (result.isSuccess) {
            val inbound = result.getOrThrow()
            val codeText = inbound.verificationCode?.let { " 验证码 $it" }.orEmpty()
            updateNotification("最近上报: $sender$codeText")
            flushPendingMessages()
            return
        }

        pendingStore.enqueue(sender, body, receivedAt)
        updateNotification("上报失败，已加入重试队列")
    }

    private fun flushPendingMessages() {
        if (!preferences.isConfigured()) {
            return
        }

        val pendingMessages = pendingStore.listAll()
        for (message in pendingMessages) {
            val result = apiClient.sendInboundSms(
                serverUrl = preferences.serverUrl,
                deviceId = preferences.deviceId,
                apiKey = preferences.apiKey,
                sender = message.sender,
                body = message.body,
                receivedAt = message.receivedAt,
                phoneNumber = preferences.phoneNumber.ifBlank { null },
            )

            if (result.isFailure) {
                updateNotification("仍有 ${pendingMessages.size} 条待重试")
                return
            }

            pendingStore.remove(message.id)
        }

        if (pendingMessages.isNotEmpty()) {
            updateNotification("待发送队列已清空")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.service_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(content: String): Notification {
        val launchIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, ConfigActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.service_notification_title))
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentIntent(launchIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(content: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(content))
    }

    companion object {
        const val ACTION_FORWARD_SMS = "com.smsreceiver.action.FORWARD_SMS"
        const val ACTION_FLUSH_PENDING = "com.smsreceiver.action.FLUSH_PENDING"
        const val EXTRA_SENDER = "extra_sender"
        const val EXTRA_BODY = "extra_body"
        const val EXTRA_RECEIVED_AT = "extra_received_at"

        private const val CHANNEL_ID = "sms_forward_channel"
        private const val NOTIFICATION_ID = 1001
        private val ISO_FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

        fun formatNow(): String {
            return OffsetDateTime.now().format(ISO_FORMATTER)
        }

        fun start(context: Context) {
            val intent = Intent(context, SmsForwardService::class.java).apply {
                action = ACTION_FLUSH_PENDING
            }
            context.startForegroundService(intent)
        }
    }
}
