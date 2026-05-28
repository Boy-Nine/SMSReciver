package com.smsreceiver

import android.content.Context

object SmsForwardHelper {
    private val apiClient = ApiClient()

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

    fun flushPendingMessages(context: Context): Int {
        val preferences = AppPreferences(context)
        if (!preferences.isConfigured()) {
            return 0
        }

        val pendingStore = PendingMessageStore(context)
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
                return pendingMessages.size
            }

            pendingStore.remove(message.id)
        }

        return 0
    }
}

sealed class ForwardResult {
    data class Success(val inbound: InboundResult) : ForwardResult()
    data class Failure(val message: String) : ForwardResult()
    data object NotConfigured : ForwardResult()
    data object AlreadyForwarded : ForwardResult()
}
