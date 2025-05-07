package com.example.smsbotapp

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.KeyEvent
import rikka.shizuku.Shizuku
import rikka.shizuku.SystemServiceHelper
import android.os.IActivityManager
import android.os.IInputManager
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.regex.Pattern
import android.util.Log

object ShizukuUtils {
    private val client = OkHttpClient()

    fun isShizukuAvailable(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Exception) {
            false
        }
    }

    fun requestPermission(context: Context, callback: (granted: Boolean) -> Unit) {
        if (Shizuku.shouldShowRequestPermissionRationale()) {
            callback(false)
            return
        }
        Shizuku.addRequestPermissionResultListener(object : Shizuku.OnRequestPermissionResultListener {
            override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                callback(grantResult == PackageManager.PERMISSION_GRANTED)
                Shizuku.removeRequestPermissionResultListener(this)
            }
        })
        Shizuku.requestPermission(0)
    }

    fun startSmsIntent(context: Context, phone: String): Boolean {
        return try {
            val binder = SystemServiceHelper.getSystemService("activity")
            val activityManager = IActivityManager.Stub.asInterface(binder)
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = android.net.Uri.parse("sms:$phone")
                getDefaultMessagingApp(context)?.let { setComponent(it) }
            }
            activityManager.startActivity(null, null, intent, null, null, null, 0, 0, null, null)
            true
        } catch (e: Exception) {
            Log.e("ShizukuUtils", "Failed to start SMS intent: ${e.message}")
            false
        }
    }

    fun injectTap(x: Float, y: Float): Boolean {
        return try {
            val binder = SystemServiceHelper.getSystemService("input")
            val inputManager = IInputManager.Stub.asInterface(binder)
            val downTime = SystemClock.uptimeMillis()
            val downEvent = MotionEvent.obtain(
                downTime, downTime, MotionEvent.ACTION_DOWN,
                x, y, 1.0f, 1.0f, 0, 1.0f, InputDevice.SOURCE_TOUCHSCREEN, 0
            )
            inputManager.injectInputEvent(downEvent, 0)
            downEvent.recycle()
            val upEvent = MotionEvent.obtain(
                downTime, downTime + 50, MotionEvent.ACTION_UP,
                x, y, 1.0f, 1.0f, 0, 1.0f, InputDevice.SOURCE_TOUCHSCREEN, 0
            )
            inputManager.injectInputEvent(upEvent, 0)
            upEvent.recycle()
            true
        } catch (e: Exception) {
            Log.e("ShizukuUtils", "Failed to inject tap: ${e.message}")
            false
        }
    }

    fun injectTextViaClipboard(context: Context, text: String): Boolean {
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("sms", text))
            val binder = SystemServiceHelper.getSystemService("input")
            val inputManager = IInputManager.Stub.asInterface(binder)
            val ctrlDown = KeyEvent(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_CTRL_LEFT, 0)
            val vDown = KeyEvent(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_V, 0)
            val vUp = KeyEvent(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, KeyEvent.KEYCODE_V, 0)
            val ctrlUp = KeyEvent(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, KeyEvent.KEYCODE_CTRL_LEFT, 0)
            inputManager.injectInputEvent(ctrlDown, 0)
            inputManager.injectInputEvent(vDown, 0)
            inputManager.injectInputEvent(vUp, 0)
            inputManager.injectInputEvent(ctrlUp, 0)
            true
        } catch (e: Exception) {
            Log.e("ShizukuUtils", "Failed to paste text: ${e.message}")
            false
        }
    }

    fun getDefaultMessagingApp(context: Context): ComponentName? {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = android.net.Uri.parse("sms:")
        }
        val resolveInfo = context.packageManager.resolveActivity(intent, 0)
        return resolveInfo?.activityInfo?.let {
            ComponentName(it.packageName, it.name)
        }
    }

    fun saveToken(context: Context, token: String) {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        val sharedPrefs = EncryptedSharedPreferences.create(
            context,
            "secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        sharedPrefs.edit().putString("telegram_token", token).apply()
    }

    fun getToken(context: Context): String? {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        val sharedPrefs = EncryptedSharedPreferences.create(
            context,
            "secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        return sharedPrefs.getString("telegram_token", null)
    }

    fun sendPhotoViaTelegram(context: Context, token: String, chatId: Long, imagePath: String) {
        val file = File(imagePath)
        if (!file.exists()) {
            Log.e("ShizukuUtils", "Image file not found: $imagePath")
            return
        }

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("chat_id", chatId.toString())
            .addFormDataPart("photo", file.name, okhttp3.RequestBody.create("image/jpeg".toMediaType(), file))
            .build()

        val request = Request.Builder()
            .url("https://api.telegram.org/bot$token/sendPhoto")
            .post(requestBody)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e("ShizukuUtils", "Failed to send photo: ${response.code}")
                }
            }
        } catch (e: Exception) {
            Log.e("ShizukuUtils", "Failed to send photo: ${e.message}")
        }
    }

    fun toggleAirplaneMode(context: Context): Boolean {
        return try {
            val enable = !isAirplaneModeOn(context)
            val command = "settings put global airplane_mode_on ${if (enable) 1 else 0}"
            val broadcast = "am broadcast -a android.intent.action.AIRPLANE_MODE --ez state $enable"
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "$command && $broadcast"))
            process.waitFor()
            process.exitValue() == 0
        } catch (e: Exception) {
            Log.e("ShizukuUtils", "Failed to toggle airplane mode: ${e.message}")
            false
        }
    }

    private fun isAirplaneModeOn(context: Context): Boolean {
        return try {
            val command = "settings get global airplane_mode_on"
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            val output = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()
            output == "1"
        } catch (e: Exception) {
            false
        }
    }

    fun parseTime(timeStr: String): Long {
        val pattern = Pattern.compile("(\\d+)([mhd])")
        val matcher = pattern.matcher(timeStr.lowercase())
        var totalMs = 0L
        while (matcher.find()) {
            val value = matcher.group(1)?.toLongOrNull() ?: return -1
            when (matcher.group(2)) {
                "m" -> totalMs += value * 60 * 1000
                "h" -> totalMs += value * 3600 * 1000
                "d" -> totalMs += value * 24 * 3600 * 1000
                else -> return -1
            }
        }
        return if (totalMs > 0) totalMs else -1
    }

    fun formatTime(ms: Long): String {
        if (ms <= 0) return "0s"
        val days = ms / (24 * 3600 * 1000)
        val hours = (ms % (24 * 3600 * 1000)) / (3600 * 1000)
        val minutes = (ms % (3600 * 1000)) / (60 * 1000)
        val seconds = (ms % (60 * 1000)) / 1000
        val parts = mutableListOf<String>()
        if (days > 0) parts.add("${days}d")
        if (hours > 0) parts.add("${hours}h")
        if (minutes > 0) parts.add("${minutes}m")
        if (seconds > 0 || parts.isEmpty()) parts.add("${seconds}s")
        return parts.joinToString(" ")
    }
}