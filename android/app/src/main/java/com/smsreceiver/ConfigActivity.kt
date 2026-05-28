package com.smsreceiver

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
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
    private lateinit var connectionStatusText: TextView
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_config)

        preferences = AppPreferences(this)
        serverUrlInput = findViewById(R.id.serverUrlInput)
        deviceNameInput = findViewById(R.id.deviceNameInput)
        connectionStatusText = findViewById(R.id.connectionStatusText)
        statusText = findViewById(R.id.statusText)

        serverUrlInput.setText(
            preferences.serverUrl.ifBlank { getString(R.string.default_server_url) },
        )
        deviceNameInput.setText(preferences.deviceName)
        updateStatusText()

        findViewById<Button>(R.id.testConnectionButton).setOnClickListener {
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
            showPhoneNumberDialog()
        }

        ensureSmsPermissions()
    }

    override fun onResume() {
        super.onResume()
        updateStatusText()

        if (!preferences.serviceEnabled || !preferences.isConfigured()) {
            return
        }

        if (!SmsInboxScanner.hasReadSmsPermission(this)) {
            ensureSmsPermissions()
            return
        }

        if (NotificationAccessHelper.isEnabled(this)) {
            NotificationAccessHelper.requestRebind(this)
        }

        ensureForwardServiceRunning()
    }

    private fun ensureForwardServiceRunning() {
        try {
            SmsForwardService.start(this)
        } catch (_: Exception) {
            return
        }

        Thread {
            val result = SmsInboxScanner.scanAndForward(applicationContext)
            runOnUiThread { updateStatusText() }
            result.lastForward?.let { (sender, forwardResult) ->
                SmsForwardService.showStatusNotification(applicationContext, forwardResult, sender)
            }
        }.start()
    }

    private fun saveInputs() {
        preferences.serverUrl = serverUrlInput.text?.toString().orEmpty()
        preferences.deviceName = deviceNameInput.text?.toString().orEmpty()
    }

    private fun testConnection() {
        val serverUrl = preferences.serverUrl
        if (serverUrl.isBlank()) {
            Toast.makeText(this, "请先填写服务器地址", Toast.LENGTH_SHORT).show()
            return
        }

        connectionStatusText.visibility = View.VISIBLE
        connectionStatusText.text = "正在测试连通..."

        CoroutineScope(Dispatchers.Main).launch {
            val result = withContext(Dispatchers.IO) {
                apiClient.healthCheck(serverUrl)
            }

            if (result.isSuccess) {
                connectionStatusText.text = "连通成功: ${result.getOrThrow()}"
                Toast.makeText(this@ConfigActivity, "服务器连通正常", Toast.LENGTH_SHORT).show()
                return@launch
            }

            connectionStatusText.text = "连通失败: ${result.exceptionOrNull()?.message}"
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
            SmsForwardService.start(this@ConfigActivity)
        }
    }

    private fun updateStatusText() {
        val lines = mutableListOf<String>()
        lines.add("服务器: ${preferences.serverUrl.ifBlank { "未设置" }}")
        lines.add("设备名: ${preferences.deviceName.ifBlank { "未设置" }}")
        lines.add("手机号: ${preferences.phoneNumber.ifBlank { "未设置" }}")
        lines.add("Device ID: ${preferences.deviceId.ifBlank { "未注册" }}")
        lines.add("API Key: ${maskSecret(preferences.apiKey)}")
        lines.add("转发服务: ${if (preferences.serviceEnabled) "已开启" else "未开启"}")
        statusText.text = lines.joinToString(separator = "\n")
    }

    private fun showPhoneNumberDialog() {
        val input = EditText(this).apply {
            setText(preferences.phoneNumber)
            hint = getString(R.string.phone_number_hint)
            inputType = InputType.TYPE_CLASS_TEXT
            minLines = 2
        }
        val container = FrameLayout(this).apply {
            val padding = (20 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding / 2, padding, 0)
            addView(input)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.phone_dialog_title)
            .setMessage(R.string.phone_dialog_message)
            .setView(container)
            .setPositiveButton(R.string.phone_dialog_confirm, null)
            .setNegativeButton(R.string.phone_dialog_cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                preferences.phoneNumber = input.text?.toString()?.trim().orEmpty()
                updateStatusText()
                dialog.dismiss()
                startForwardService()
            }
        }

        dialog.show()
    }

    private fun startForwardService() {
        if (!preferences.inboxBaselineSet) {
            SmsInboxScanner.markBaseline(this)
            preferences.inboxBaselineSet = true
        }

        preferences.serviceEnabled = true
        requestBatteryOptimizationExemption()
        SmsForwardService.start(this)

        if (!NotificationAccessHelper.isEnabled(this)) {
            Toast.makeText(this, "请在系统设置中开启本 App 的通知监听", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        } else {
            NotificationAccessHelper.requestRebind(this)
        }

        Toast.makeText(this, "转发服务已启动", Toast.LENGTH_SHORT).show()
        updateStatusText()
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

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_PERMISSIONS) {
            return
        }
        if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            Toast.makeText(this, "短信权限已授予，请再次点击启动转发服务", Toast.LENGTH_LONG).show()
            return
        }
        Toast.makeText(this, "未授予短信权限，无法转发", Toast.LENGTH_LONG).show()
    }

    companion object {
        private const val REQUEST_PERMISSIONS = 100
    }
}
