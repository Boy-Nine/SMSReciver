package com.smsreceiver

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Telephony
import androidx.core.app.NotificationCompat
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors

class SmsForwardService : Service() {
    private val workerExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var inboxObserver: SmsInboxObserver? = null

    private val periodicScan = object : Runnable {
        override fun run() {
            workerExecutor.execute {
                performInboxScan()
            }
            mainHandler.postDelayed(this, SCAN_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("监听中，等待短信"))
        NotificationAccessHelper.requestRebind(this)
        registerInboxObserver()
        mainHandler.post(periodicScan)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        workerExecutor.execute {
            flushPendingMessages()
            performInboxScan()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(periodicScan)
        unregisterInboxObserver()
        workerExecutor.shutdownNow()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun registerInboxObserver() {
        val observer = SmsInboxObserver(this) { result ->
            if (result.missingReadSmsPermission) {
                updateNotification("缺少读取短信权限，请到 App 内重新授权")
                return@SmsInboxObserver
            }
            result.lastForward?.let { (sender, forwardResult) ->
                updateNotificationFromResult(forwardResult, sender)
            }
        }
        contentResolver.registerContentObserver(
            Telephony.Sms.Inbox.CONTENT_URI,
            true,
            observer,
        )
        inboxObserver = observer
    }

    private fun unregisterInboxObserver() {
        inboxObserver?.let { contentResolver.unregisterContentObserver(it) }
        inboxObserver = null
    }

    private fun performInboxScan() {
        val result = SmsInboxScanner.scanAndForward(this)
        if (result.missingReadSmsPermission) {
            updateNotification("缺少读取短信权限，请到 App 内重新授权")
            return
        }
        result.lastForward?.let { (sender, forwardResult) ->
            updateNotificationFromResult(forwardResult, sender)
        }
    }

    private fun flushPendingMessages() {
        val pendingCount = PendingMessageStore(this).listAll().size
        if (pendingCount == 0) {
            return
        }

        val remaining = SmsForwardHelper.flushPendingMessages(this)
        if (remaining > 0) {
            updateNotification("仍有 $remaining 条待重试")
            return
        }

        updateNotification("待发送队列已清空")
    }

    private fun formatCodeText(verificationCode: String?): String {
        if (verificationCode.isNullOrBlank()) {
            return " 验证码未识别"
        }
        return " 验证码 $verificationCode"
    }

    private fun updateNotificationFromResult(result: ForwardResult, sender: String) {
        when (result) {
            is ForwardResult.Success -> {
                val duplicateText = if (result.inbound.duplicate) "(重复)" else ""
                val codeText = formatCodeText(result.inbound.verificationCode)
                updateNotification("最近上报$duplicateText: $sender$codeText")
            }

            is ForwardResult.Failure -> {
                updateNotification("上报失败: ${result.message.take(40)}")
            }

            ForwardResult.NotConfigured -> {
                updateNotification("未配置，短信已加入待发送队列")
            }

            ForwardResult.AlreadyForwarded -> {
                // 已由其他链路处理，不重复提示。
            }
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
        const val ACTION_FLUSH_PENDING = "com.smsreceiver.action.FLUSH_PENDING"

        private const val CHANNEL_ID = "sms_forward_channel"
        private const val NOTIFICATION_ID = 1001
        private const val SCAN_INTERVAL_MS = 10_000L
        private val ISO_FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

        fun formatNow(): String {
            return OffsetDateTime.now().format(ISO_FORMATTER)
        }

        fun formatFromEpochMillis(epochMillis: Long): String {
            return OffsetDateTime.ofInstant(
                Instant.ofEpochMilli(epochMillis),
                ZoneId.systemDefault(),
            ).format(ISO_FORMATTER)
        }

        fun start(context: Context) {
            val intent = Intent(context, SmsForwardService::class.java).apply {
                action = ACTION_FLUSH_PENDING
            }
            context.startForegroundService(intent)
        }

        fun showStatusNotification(context: Context, result: ForwardResult, sender: String) {
            if (result is ForwardResult.AlreadyForwarded) {
                return
            }

            val appContext = context.applicationContext
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    appContext.getString(R.string.service_channel_name),
                    NotificationManager.IMPORTANCE_LOW,
                )
                val manager = appContext.getSystemService(NotificationManager::class.java)
                manager.createNotificationChannel(channel)
            }

            val content = when (result) {
                is ForwardResult.Success -> {
                    val duplicateText = if (result.inbound.duplicate) "(重复)" else ""
                    val codeText = if (result.inbound.verificationCode.isNullOrBlank()) {
                        " 验证码未识别"
                    } else {
                        " 验证码 ${result.inbound.verificationCode}"
                    }
                    "最近上报$duplicateText: $sender$codeText"
                }

                is ForwardResult.Failure -> "上报失败: ${result.message.take(40)}"
                ForwardResult.NotConfigured -> "未配置，短信已加入待发送队列"
                ForwardResult.AlreadyForwarded -> return
            }

            val launchIntent = PendingIntent.getActivity(
                appContext,
                0,
                Intent(appContext, ConfigActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
                .setContentTitle(appContext.getString(R.string.service_notification_title))
                .setContentText(content)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentIntent(launchIntent)
                .setOngoing(true)
                .build()

            val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, notification)
        }
    }
}
