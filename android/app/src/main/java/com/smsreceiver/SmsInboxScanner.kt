package com.smsreceiver

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Telephony
import androidx.core.content.ContextCompat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object SmsInboxScanner {
    private val INBOX_URI: Uri = Telephony.Sms.Inbox.CONTENT_URI
    private val SMS_URI: Uri = Telephony.Sms.CONTENT_URI
    private val DIAGNOSTIC_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")

    fun markBaseline(context: Context) {
        if (!hasReadSmsPermission(context)) {
            return
        }

        val maxId = queryMaxSmsId(context) ?: return
        val preferences = AppPreferences(context)
        preferences.lastProcessedSmsId = maxId
    }

    fun hasReadSmsPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_SMS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun scanAndForward(context: Context): ScanResult {
        if (!hasReadSmsPermission(context)) {
            return ScanResult(
                forwardedCount = 0,
                lastForward = null,
                missingReadSmsPermission = true,
                debugInfo = "缺少读取短信权限",
            )
        }

        val preferences = AppPreferences(context)
        if (!preferences.isConfigured()) {
            return ScanResult(0, null, debugInfo = "设备未注册")
        }

        repairCursorIfNeeded(context, preferences)

        val maxInboxId = queryMaxSmsId(context) ?: 0L
        val cursorId = preferences.lastProcessedSmsId
        val newMessages = loadNewMessages(context, cursorId)

        if (newMessages.isEmpty()) {
            val debugInfo = if (maxInboxId <= cursorId) {
                "无新短信（收件箱最新ID $maxInboxId，游标 $cursorId）"
            } else {
                "收件箱有更新但读取失败（最新ID $maxInboxId，游标 $cursorId）"
            }
            preferences.lastScanDebug = debugInfo
            preferences.lastInboxDiagnostic = getInboxDiagnostic(context)
            return ScanResult(0, null, debugInfo = debugInfo)
        }

        preferences.lastInboxDiagnostic = getInboxDiagnostic(context)

        var forwardedCount = 0
        var lastForward: Pair<String, ForwardResult>? = null

        for (message in newMessages) {
            if (message.sender.isBlank() || message.body.isBlank()) {
                preferences.lastProcessedSmsId = message.id
                continue
            }

            val result = SmsForwardHelper.forwardSms(
                context = context,
                sender = message.sender,
                body = message.body,
                receivedAt = SmsForwardService.formatFromEpochMillis(message.dateMs),
            )
            lastForward = Pair(message.sender, result)
            forwardedCount++

            if (result is ForwardResult.Success || result is ForwardResult.Failure) {
                preferences.lastProcessedSmsId = message.id
            }
        }

        val debugInfo = "已处理 ${newMessages.size} 条新短信，最新ID ${newMessages.last().id}"
        preferences.lastScanDebug = debugInfo
        return ScanResult(forwardedCount, lastForward, debugInfo = debugInfo)
    }

    fun getInboxDiagnostic(context: Context): String {
        if (!hasReadSmsPermission(context)) {
            return "收件箱诊断：无读取权限"
        }

        val messages = loadLatestMessages(context, 3)
        if (messages.isEmpty()) {
            return "收件箱诊断：读不到短信，请开启通知监听"
        }

        val preview = messages.joinToString(separator = " | ") { message ->
            val dateText = Instant.ofEpochMilli(message.dateMs)
                .atZone(ZoneId.systemDefault())
                .format(DIAGNOSTIC_FORMATTER)
            "$dateText ${message.sender.take(12)}"
        }
        return "收件箱最新: $preview"
    }

    fun scanLatestForce(context: Context, limit: Int = 10): ScanResult {
        if (!hasReadSmsPermission(context)) {
            return ScanResult(0, null, missingReadSmsPermission = true, debugInfo = "缺少读取短信权限")
        }

        val preferences = AppPreferences(context)
        if (!preferences.isConfigured()) {
            return ScanResult(0, null, debugInfo = "设备未注册")
        }

        val latestMessages = loadLatestMessages(context, limit)
        if (latestMessages.isEmpty()) {
            preferences.lastScanDebug = "收件箱为空或无法读取"
            return ScanResult(0, null, debugInfo = preferences.lastScanDebug)
        }

        var forwardedCount = 0
        var lastForward: Pair<String, ForwardResult>? = null

        for (message in latestMessages.sortedBy { it.id }) {
            if (message.sender.isBlank() || message.body.isBlank()) {
                continue
            }

            val result = SmsForwardHelper.forwardSms(
                context = context,
                sender = message.sender,
                body = message.body,
                receivedAt = SmsForwardService.formatFromEpochMillis(message.dateMs),
            )
            lastForward = Pair(message.sender, result)
            forwardedCount++
        }

        preferences.lastProcessedSmsId = latestMessages.maxOf { it.id }
        val debugInfo = "强制同步 ${latestMessages.size} 条，最新ID ${preferences.lastProcessedSmsId}"
        preferences.lastScanDebug = debugInfo
        preferences.lastInboxDiagnostic = getInboxDiagnostic(context)
        return ScanResult(forwardedCount, lastForward, debugInfo = debugInfo)
    }

    private fun loadLatestMessages(context: Context, limit: Int): List<InboxMessage> {
        val merged = linkedMapOf<Long, InboxMessage>()
        loadLatestFromUri(context, INBOX_URI, limit, merged)
        loadLatestFromUri(context, SMS_URI, limit, merged, inboxOnly = true)
        return merged.values.sortedByDescending { it.id }.take(limit)
    }

    private fun loadLatestFromUri(
        context: Context,
        uri: Uri,
        limit: Int,
        merged: LinkedHashMap<Long, InboxMessage>,
        inboxOnly: Boolean = false,
    ) {
        val selection = if (inboxOnly) "type = ?" else null
        val selectionArgs = if (inboxOnly) {
            arrayOf(Telephony.Sms.MESSAGE_TYPE_INBOX.toString())
        } else {
            null
        }

        val cursor = context.contentResolver.query(
            uri,
            arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
            ),
            selection,
            selectionArgs,
            "${Telephony.Sms._ID} DESC LIMIT $limit",
        ) ?: return

        cursor.use {
            while (it.moveToNext()) {
                val id = it.getLong(0)
                merged[id] = InboxMessage(
                    id = id,
                    sender = it.getString(1).orEmpty(),
                    body = it.getString(2).orEmpty(),
                    dateMs = normalizeDateMs(it.getLong(3)),
                )
            }
        }
    }

    private fun repairCursorIfNeeded(context: Context, preferences: AppPreferences) {
        val maxId = queryMaxSmsId(context) ?: return
        if (maxId < preferences.lastProcessedSmsId) {
            preferences.lastProcessedSmsId = 0L
        }
    }

    private fun loadNewMessages(context: Context, afterId: Long): List<InboxMessage> {
        val merged = linkedMapOf<Long, InboxMessage>()
        loadFromUri(context, INBOX_URI, afterId, merged)
        loadFromUri(
            context = context,
            uri = SMS_URI,
            afterId = afterId,
            merged = merged,
            inboxOnly = true,
        )
        return merged.values.sortedBy { it.id }
    }

    private fun loadFromUri(
        context: Context,
        uri: Uri,
        afterId: Long,
        merged: LinkedHashMap<Long, InboxMessage>,
        inboxOnly: Boolean = false,
    ) {
        val selection = if (inboxOnly) {
            "_id > ? AND type = ?"
        } else {
            "_id > ?"
        }
        val selectionArgs = if (inboxOnly) {
            arrayOf(afterId.toString(), Telephony.Sms.MESSAGE_TYPE_INBOX.toString())
        } else {
            arrayOf(afterId.toString())
        }

        val cursor = context.contentResolver.query(
            uri,
            arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
            ),
            selection,
            selectionArgs,
            "${Telephony.Sms._ID} ASC LIMIT 20",
        ) ?: return

        cursor.use {
            while (it.moveToNext()) {
                val id = it.getLong(0)
                val sender = it.getString(1).orEmpty()
                val body = it.getString(2).orEmpty()
                merged[id] = InboxMessage(id, sender, body, normalizeDateMs(it.getLong(3)))
            }
        }
    }

    private fun queryMaxSmsId(context: Context): Long? {
        val uris = listOf(INBOX_URI, SMS_URI)
        var maxId: Long? = null

        for (uri in uris) {
            val selection = if (uri == SMS_URI) "type = ?" else null
            val selectionArgs = if (uri == SMS_URI) {
                arrayOf(Telephony.Sms.MESSAGE_TYPE_INBOX.toString())
            } else {
                null
            }

            val cursor = context.contentResolver.query(
                uri,
                arrayOf(Telephony.Sms._ID),
                selection,
                selectionArgs,
                "${Telephony.Sms._ID} DESC LIMIT 1",
            ) ?: continue

            cursor.use {
                if (it.moveToFirst()) {
                    val id = it.getLong(0)
                    val currentMax = maxId
                    if (currentMax == null || id > currentMax) {
                        maxId = id
                    }
                }
            }
        }

        return maxId
    }

    private fun normalizeDateMs(raw: Long): Long {
        if (raw in 1..9_999_999_999L) {
            return raw * 1000
        }
        return raw
    }
}

private data class InboxMessage(
    val id: Long,
    val sender: String,
    val body: String,
    val dateMs: Long,
)

data class ScanResult(
    val forwardedCount: Int,
    val lastForward: Pair<String, ForwardResult>?,
    val missingReadSmsPermission: Boolean = false,
    val debugInfo: String = "",
)
