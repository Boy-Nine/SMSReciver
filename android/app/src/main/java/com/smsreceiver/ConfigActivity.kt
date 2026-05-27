package com.smsreceiver

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ConfigActivity : AppCompatActivity() {
    private val apiClient = ApiClient()
    private lateinit var preferences: AppPreferences

    private lateinit var serverUrlInput: TextInputEditText
    private lateinit var deviceNameInput: TextInputEditText
    private lateinit var phoneNumberInput: TextInputEditText
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_config)

        preferences = AppPreferences(this)
        serverUrlInput = findViewById(R.id.serverUrlInput)
        deviceNameInput = findViewById(R.id.deviceNameInput)
        phoneNumberInput = findViewById(R.id.phoneNumberInput)
        statusText = findViewById(R.id.statusText)

        serverUrlInput.setText(preferences.serverUrl)
        deviceNameInput.setText(preferences.deviceName)
        phoneNumberInput.setText(preferences.phoneNumber)
        updateStatusText()

        findViewById<Button>(R.id.testButton).setOnClickListener {
            saveInputs()
            testConnection()
        }

        findViewById<Button>(R.id.registerButton).setOnClickListener {
            saveInputs()
            registerDevice()
        }

        findViewById<Button>(R.id.startServiceButton).setOnClickListener {
            saveInputs()
            if (!ensureSmsPermissions()) {
                return@setOnClickListener
            }
            requestBatteryOptimizationExemption()
            SmsForwardService.start(this)
            Toast.makeText(this, "转发服务已启动", Toast.LENGTH_SHORT).show()
            updateStatusText()
        }

        ensureSmsPermissions()
    }

    private fun saveInputs() {
        preferences.serverUrl = serverUrlInput.text?.toString().orEmpty()
        preferences.deviceName = deviceNameInput.text?.toString().orEmpty()
        preferences.phoneNumber = phoneNumberInput.text?.toString().orEmpty()
    }

    private fun testConnection() {
        val serverUrl = preferences.serverUrl
        if (serverUrl.isBlank()) {
            Toast.makeText(this, "请先填写服务器地址", Toast.LENGTH_SHORT).show()
            return
        }

        CoroutineScope(Dispatchers.Main).launch {
            val result = withContext(Dispatchers.IO) {
                apiClient.healthCheck(serverUrl)
            }

            if (result.isSuccess) {
                statusText.text = "连通成功: ${result.getOrThrow()}"
                Toast.makeText(this@ConfigActivity, "服务器连通正常", Toast.LENGTH_SHORT).show()
                return@launch
            }

            statusText.text = "连通失败: ${result.exceptionOrNull()?.message}"
            Toast.makeText(this@ConfigActivity, "连通失败", Toast.LENGTH_SHORT).show()
        }
    }

    private fun registerDevice() {
        val serverUrl = preferences.serverUrl
        val deviceName = preferences.deviceName.ifBlank { "Android设备" }

        if (serverUrl.isBlank()) {
            Toast.makeText(this, "请先填写服务器地址", Toast.LENGTH_SHORT).show()
            return
        }

        CoroutineScope(Dispatchers.Main).launch {
            val result = withContext(Dispatchers.IO) {
                apiClient.registerDevice(
                    serverUrl = serverUrl,
                    deviceName = deviceName,
                    phoneNumber = preferences.phoneNumber.ifBlank { null },
                )
            }

            if (result.isFailure) {
                statusText.text = "注册失败: ${result.exceptionOrNull()?.message}"
                Toast.makeText(this@ConfigActivity, "注册失败", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val registerResult = result.getOrThrow()
            preferences.deviceId = registerResult.deviceId
            preferences.apiKey = registerResult.apiKey
            preferences.deviceName = registerResult.deviceName
            deviceNameInput.setText(registerResult.deviceName)
            updateStatusText()
            Toast.makeText(this@ConfigActivity, "设备注册成功", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateStatusText() {
        val lines = mutableListOf<String>()
        lines.add("服务器: ${preferences.serverUrl.ifBlank { "未设置" }}")
        lines.add("设备名: ${preferences.deviceName.ifBlank { "未设置" }}")
        lines.add("Device ID: ${preferences.deviceId.ifBlank { "未注册" }}")
        lines.add("API Key: ${maskSecret(preferences.apiKey)}")
        statusText.text = lines.joinToString(separator = "\n")
    }

    private fun maskSecret(value: String): String {
        if (value.isBlank()) {
            return "未注册"
        }
        if (value.length <= 8) {
            return "****"
        }
        return value.take(4) + "..." + value.takeLast(4)
    }

    private fun ensureSmsPermissions(): Boolean {
        val permissions = mutableListOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isEmpty()) {
            return true
        }

        ActivityCompat.requestPermissions(this, missing.toTypedArray(), REQUEST_PERMISSIONS)
        return false
    }

    private fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return
        }

        val powerManager = getSystemService(POWER_SERVICE) as android.os.PowerManager
        if (powerManager.isIgnoringBatteryOptimizations(packageName)) {
            return
        }

        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:$packageName")
        }
        startActivity(intent)
    }

    companion object {
        private const val REQUEST_PERMISSIONS = 100
    }
}
