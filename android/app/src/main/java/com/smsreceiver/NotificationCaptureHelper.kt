package com.smsreceiver

import android.app.Notification
import android.content.Context
import android.os.Bundle
import java.util.Locale

object NotificationCaptureHelper {
    private val SMS_PACKAGE_KEYWORDS = listOf(
        "mms",
        "messaging",
        "message",
        "sms",
        "vivo",
        "iqoo",
    )

    private val SMS_CONTENT_KEYWORDS = listOf(
        "验证码",
        "校验码",
        "动态码",
        "verification",
        "otp",
    )

    private val REDACTED_NOTIFICATION_MARKERS = listOf(
        "已隐藏敏感通知内容",
        "敏感内容已隐藏",
        "隐藏了敏感通知内容",
        "隐藏敏感通知",
        "hidden sensitive content",
        "sensitive content hidden",
        "contents hidden",
    )

    fun isSmsPackage(packageName: String): Boolean {
        val lower = packageName.lowercase(Locale.ROOT)
        return SMS_PACKAGE_KEYWORDS.any { keyword -> lower.contains(keyword) }
    }

    fun isRedactedNotification(text: String): Boolean {
        val normalized = text.trim()
        if (normalized.isBlank()) {
            return true
        }

        return REDACTED_NOTIFICATION_MARKERS.any { marker ->
            normalized.equals(marker, ignoreCase = true) ||
                normalized.contains(marker, ignoreCase = true)
        }
    }

    fun shouldCapture(packageName: String, text: String): Boolean {
        if (text.isBlank() || isRedactedNotification(text)) {
            return false
        }

        if (isSmsPackage(packageName)) {
            return true
        }

        return SMS_CONTENT_KEYWORDS.any { keyword ->
            text.contains(keyword, ignoreCase = true)
        }
    }

    fun handleSmsNotification(context: Context, packageName: String, sender: String, body: String) {
        val combined = listOf(sender, body).filter { it.isNotBlank() }.joinToString("\n")
        if (shouldCapture(packageName, combined)) {
            captureFromNotification(context, sender, body)
            return
        }

        if (!isSmsPackage(packageName) && !isRedactedNotification(combined)) {
            return
        }

        captureFromInboxFallback(
            context = context,
            showRedactedHint = isRedactedNotification(combined),
        )
    }

    fun captureFromNotification(context: Context, sender: String, body: String) {
        val normalizedSender = sender.trim()
        val normalizedBody = body.trim()
        if (normalizedBody.isBlank()) {
            captureFromInboxFallback(context, showRedactedHint = false)
            return
        }

        if (isRedactedNotification(normalizedBody)) {
            captureFromInboxFallback(context, showRedactedHint = true)
            return
        }

        val actualSender = normalizedSender.ifBlank { "短信通知" }
        forwardInBackground(context, actualSender, normalizedBody)
    }

    fun captureFromInboxFallback(context: Context, showRedactedHint: Boolean) {
        val appContext = context.applicationContext
        Thread {
            var result = SmsInboxScanner.scanAndForward(appContext)
            if (result.forwardedCount == 0 && !result.missingReadSmsPermission) {
                result = SmsInboxScanner.scanLatestForce(appContext, limit = 5)
            }

            result.lastForward?.let { (sender, forwardResult) ->
                SmsForwardService.showStatusNotification(appContext, forwardResult, sender)
                try {
                    SmsForwardService.start(appContext)
                } catch (_: Exception) {
                    // 已上报即可。
                }
                return@Thread
            }

            if (showRedactedHint) {
                notifyRedactedContentBlocked(appContext)
            }
        }.start()
    }

    private fun forwardInBackground(context: Context, sender: String, body: String) {
        val appContext = context.applicationContext
        val receivedAt = SmsForwardService.formatNow()

        Thread {
            val result = SmsForwardHelper.forwardSms(
                context = appContext,
                sender = sender,
                body = body,
                receivedAt = receivedAt,
            )
            if (result is ForwardResult.AlreadyForwarded) {
                return@Thread
            }
            if (result is ForwardResult.Success || result is ForwardResult.Failure) {
                SmsForwardService.showStatusNotification(appContext, result, sender)
            }
            try {
                SmsForwardService.start(appContext)
            } catch (_: Exception) {
                // 已上报即可。
            }
        }.start()
    }

    fun notifyRedactedContentBlocked(context: Context) {
        val appContext = context.applicationContext
        AppPreferences(appContext).lastScanDebug =
            "通知内容被隐藏且收件箱未读到，请关闭「隐藏敏感通知」"
        SmsForwardService.showStatusNotification(
            appContext,
            ForwardResult.Failure("请关闭「隐藏敏感通知」并打开系统信息App"),
            "短信通知",
        )
    }

    fun extractNotificationContent(extras: Bundle): NotificationContent {
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty().trim()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty().trim()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty().trim()
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty().trim()
        val lines = extras.getCharSequence(Notification.EXTRA_TEXT_LINES)?.map {
            it?.toString().orEmpty().trim()
        }?.filter { it.isNotBlank() }.orEmpty()

        val body = when {
            text.isNotBlank() -> text
            bigText.isNotBlank() -> bigText
            lines.isNotEmpty() -> lines.joinToString("\n")
            else -> listOf(title, subText).filter { it.isNotBlank() }.joinToString("\n")
        }

        val sender = when {
            title.isNotBlank() && !title.contains("验证码") && title.length <= 30 -> title
            subText.isNotBlank() -> subText
            else -> ""
        }

        return NotificationContent(sender = sender, body = body)
    }
}

data class NotificationContent(
    val sender: String,
    val body: String,
)
