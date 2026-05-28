package com.smsreceiver

import android.content.Context

object SmsForwardHelper {
    private val apiClient = ApiClient()

    data class FlushPendingResult(
        val remaining: Int,
        val lastError: String? = null,
        val authInvalid: Boolean = false,
    )

    fun forwardSms(context: Context, sender: String, body: String, receivedAt: String): ForwardResult {
        val normalizedSender = sender.trim()
        val normalizedBody = body.trim()
        if (normalizedBody.isBlank()) {
            return ForwardResult.Failure("短信内容为空")
        }

        if (SmsDedup.shouldSkip(normalizedSender, normalizedBody)) {
            return ForwardResult.AlreadyForwarded
        }

        val preferences = AppPreferences(context)
        val pendingStore = PendingMessageStore(context)

        if (!preferences.isConfigured()) {
            pendingStore.enqueue(normalizedSender, normalizedBody, receivedAt)
            return ForwardResult.NotConfigured
        }

        val result = apiClient.sendInboundSms(
            serverUrl = preferences.serverUrl,
            deviceId = preferences.deviceId,
            apiKey = preferences.apiKey,
            sender = normalizedSender,
            body = normalizedBody,
            receivedAt = receivedAt,
            phoneNumber = preferences.phoneNumber.ifBlank { null },
        )

        if (result.isSuccess) {
            flushPendingMessages(context)
            return ForwardResult.Success(result.getOrThrow())
        }

        pendingStore.enqueue(normalizedSender, normalizedBody, receivedAt)
        return ForwardResult.Failure(result.exceptionOrNull()?.message ?: "未知错误")
    }

    fun flushPendingMessages(context: Context): FlushPendingResult {
        val preferences = AppPreferences(context)
        if (!preferences.isConfigured()) {
            return FlushPendingResult(
                remaining = PendingMessageStore(context).count(),
                lastError = "设备未注册",
            )
        }

        val pendingStore = PendingMessageStore(context)
        val pendingMessages = pendingStore.listAll()
        if (pendingMessages.isEmpty()) {
            return FlushPendingResult(remaining = 0)
        }

        var lastError: String? = null
        var authInvalid = false

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

            if (result.isSuccess) {
                pendingStore.remove(message.id)
                continue
            }

            val errorMessage = result.exceptionOrNull()?.message ?: "未知错误"
            lastError = errorMessage
            if (errorMessage.contains("HTTP 401")) {
                authInvalid = true
            }
        }

        return FlushPendingResult(
            remaining = pendingStore.count(),
            lastError = lastError,
            authInvalid = authInvalid,
        )
    }
}

sealed class ForwardResult {
    data class Success(val inbound: InboundResult) : ForwardResult()
    data class Failure(val message: String) : ForwardResult()
    data object NotConfigured : ForwardResult()
    data object AlreadyForwarded : ForwardResult()
}
