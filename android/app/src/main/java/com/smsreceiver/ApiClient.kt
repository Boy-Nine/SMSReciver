package com.smsreceiver

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ApiClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    fun healthCheck(serverUrl: String): Result<String> {
        val request = Request.Builder()
            .url(normalizeUrl(serverUrl) + "/api/health")
            .get()
            .build()

        return execute(request) { body ->
            val json = JSONObject(body)
            json.optString("status", "unknown")
        }
    }

    fun registerDevice(
        serverUrl: String,
        deviceName: String,
        phoneNumber: String?,
    ): Result<RegisterResult> {
        val payload = JSONObject()
            .put("device_name", deviceName)

        if (!phoneNumber.isNullOrBlank()) {
            payload.put("phone_number", phoneNumber)
        }

        val request = Request.Builder()
            .url(normalizeUrl(serverUrl) + "/api/devices/register")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()

        return execute(request) { body ->
            val json = JSONObject(body)
            RegisterResult(
                deviceId = json.getString("device_id"),
                apiKey = json.getString("api_key"),
                deviceName = json.getString("device_name"),
            )
        }
    }

    fun sendInboundSms(
        serverUrl: String,
        deviceId: String,
        apiKey: String,
        sender: String,
        body: String,
        receivedAt: String,
        phoneNumber: String?,
    ): Result<InboundResult> {
        val payload = JSONObject()
            .put("sender", sender)
            .put("body", body)
            .put("received_at", receivedAt)

        if (!phoneNumber.isNullOrBlank()) {
            payload.put("phone_number", phoneNumber)
        }

        val request = Request.Builder()
            .url(normalizeUrl(serverUrl) + "/api/sms/inbound")
            .addHeader("X-Device-Id", deviceId)
            .addHeader("X-Api-Key", apiKey)
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()

        return execute(request) { responseBody ->
            val json = JSONObject(responseBody)
            InboundResult(
                id = json.getInt("id"),
                verificationCode = json.optString("verification_code").ifBlank { null },
                duplicate = json.optBoolean("duplicate", false),
            )
        }
    }

    private fun <T> execute(request: Request, parser: (String) -> T): Result<T> {
        return try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return Result.failure(IllegalStateException("HTTP ${response.code}: $body"))
                }
                Result.success(parser(body))
            }
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    private fun normalizeUrl(serverUrl: String): String {
        return serverUrl.trim().trimEnd('/')
    }
}

data class RegisterResult(
    val deviceId: String,
    val apiKey: String,
    val deviceName: String,
)

data class InboundResult(
    val id: Int,
    val verificationCode: String?,
    val duplicate: Boolean,
)
