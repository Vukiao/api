package com.example.smsbotapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.room.Room
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.google.gson.Gson
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class SmsService : Service() {
    private lateinit var db: AppDatabase
    private lateinit var appDao: AppDao
    private val client = OkHttpClient()
    private var isRunning = false

    override fun onCreate() {
        super.onCreate()
        db = Room.databaseBuilder(applicationContext, AppDatabase::class.java, "config-db").build()
        appDao = db.appDao()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1, createNotification())
        if (!isRunning) {
            isRunning = true
            GlobalScope.launch {
                listenToTelegram()
            }
        }
        return START_STICKY
    }

    private fun createNotification(): Notification {
        val channelId = "sms_service_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "SMS Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("SMS Bot Service")
            .setContentText("Listening for Telegram commands...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private suspend fun listenToTelegram() {
        val config = appDao.getConfig()
        if (config == null) {
            Log.e("SmsService", "No config found, stopping service")
            sendTelegramResponse(ShizukuUtils.getToken(this@SmsService) ?: "", 0, "No configuration found. Please save config in app.")
            stopSelf()
            return
        }

        var offset: Long? = null
        var lastShizukuWarning = 0L
        while (isRunning) {
            if (!ShizukuUtils.isShizukuAvailable()) {
                val now = System.currentTimeMillis()
                if (now - lastShizukuWarning > 3600000) {
                    sendTelegramResponse(config.token, 0, "Shizuku not available. Please restart Shizuku.")
                    lastShizukuWarning = now
                }
                delay(60000)
                continue
            }
            try {
                val url = "https://api.telegram.org/bot${config.token}/getUpdates?timeout=30" +
                        (offset?.let { "&offset=$it" } ?: "")
                val request = Request.Builder().url(url).build()
                client.newCall(request).execute().use { response ->
                    val json = Gson().fromJson(response.body?.string(), TelegramResponse::class.java)
                    json.result.forEach { update ->
                        offset = update.update_id + 1
                        val message = update.message?.text ?: return@forEach
                        val chatId = update.message.chat.id
                        when {
                            message.startsWith("/sms") -> {
                                val parts = message.split(" ", limit = 2)
                                if (parts.size == 2) {
                                    val phone = parts[1].substringBefore(" ")
                                    val content = parts[1].substringAfter(" ").trim().replace("%s", " ")
                                    if (phone.isNotEmpty() && content.isNotEmpty()) {
                                        enqueueSmsWork(phone, content, config, chatId)
                                        sendTelegramResponse(config.token, chatId, "SMS sent")
                                    } else {
                                        sendTelegramResponse(config.token, chatId, "Invalid command format. Use: /sms <phone> <message>")
                                    }
                                } else {
                                    sendTelegramResponse(config.token, chatId, "Invalid command format. Use: /sms <phone> <message>")
                                }
                            }
                            message == "/rs" -> {
                                sendTelegramResponse(config.token, chatId, "Toggling Airplane Mode. Note: This may temporarily disconnect the bot.")
                                val result = ShizukuUtils.toggleAirplaneMode(this@SmsService)
                                val status = if (result) "Success" else "Failed"
                                saveLog("Airplane Mode", "", status, null, chatId)
                                sendTelegramResponse(config.token, chatId, "Airplane Mode toggle: $status")
                            }
                            message.startsWith("/set") -> {
                                val parts = message.split(" ", limit = 5)
                                if (parts.size == 5) {
                                    val phone = parts[1]
                                    val content = parts[2].replace("%s", " ")
                                    val time = ShizukuUtils.parseTime(parts[3])
                                    val repeatCount = parts[4].toIntOrNull() ?: -1
                                    if (phone.isNotEmpty() && content.isNotEmpty() && time > 0 && repeatCount >= 0) {
                                        val jobId = scheduleJob(phone, content, time, repeatCount, chatId, config)
                                        sendTelegramResponse(config.token, chatId, "Scheduled job with ID: $jobId")
                                    } else {
                                        sendTelegramResponse(config.token, chatId, "Invalid /set command. Use: /set <phone> <message> <time> <repeat>")
                                    }
                                } else {
                                    sendTelegramResponse(config.token, chatId, "Invalid /set command. Use: /set <phone> <message> <time> <repeat>")
                                }
                            }
                            message.startsWith("/job") -> {
                                val jobs = appDao.getAllJobs()
                                if (jobs.isEmpty()) {
                                    sendTelegramResponse(config.token, chatId, "No scheduled jobs.")
                                } else {
                                    val jobList = jobs.mapIndexed { index, job ->
                                        val remaining = job.scheduledTime - System.currentTimeMillis()
                                        val timeStr = if (remaining > 0) ShizukuUtils.formatTime(remaining) else "Due now"
                                        "${index + 1}. ${job.phone} - ${job.content} - $timeStr - ${if (job.repeatCount == 0) "Unlimited" else "${job.repeatCount} times"}"
                                    }.joinToString("\n")
                                    sendTelegramResponse(config.token, chatId, jobList)
                                }
                            }
                            message.startsWith("/un") -> {
                                val parts = message.split(" ")
                                if (parts.size == 2) {
                                    val index = parts[1].toIntOrNull()?.minus(1) ?: -1
                                    val jobs = appDao.getAllJobs()
                                    if (index in jobs.indices) {
                                        val job = jobs[index]
                                        WorkManager.getInstance(applicationContext).cancelWorkById(job.workId?.let { java.util.UUID.fromString(it) } ?: return@forEach)
                                        appDao.deleteJob(job.id)
                                        saveLog("Unschedule Job", "Job ID: ${job.id}", "Success", null, chatId)
                                        sendTelegramResponse(config.token, chatId, "Job ${index + 1} cancelled.")
                                    } else {
                                        sendTelegramResponse(config.token, chatId, "Invalid job index.")
                                    }
                                } else {
                                    sendTelegramResponse(config.token, chatId, "Invalid command. Use: /un <index>")
                                }
                            }
                            message == "/stop" -> {
                                val jobs = appDao.getAllJobs()
                                jobs.forEach { job ->
                                    WorkManager.getInstance(applicationContext).cancelWorkById(job.workId?.let { java.util.UUID.fromString(it) } ?: return@forEach)
                                }
                                appDao.deleteAllJobs()
                                saveLog("Stop All Jobs", "All scheduled jobs stopped", "Success", null, chatId)
                                sendTelegramResponse(config.token, chatId, "All scheduled jobs stopped.")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("SmsService", "Error: ${e.message}")
                sendTelegramResponse(config.token, 0, "Error: ${e.message}. Retrying in 5 seconds...")
                delay(5000)
            }
        }
    }

    private suspend fun enqueueSmsWork(phone: String, content: String, config: Config, chatId: Long) {
        val workData = workDataOf(
            "phone" to phone,
            "content" to content,
            "chat_id" to chatId
        )
        val workRequest = OneTimeWorkRequestBuilder<SmsWorker>()
            .setInputData(workData)
            .build()
        WorkManager.getInstance(applicationContext).enqueue(workRequest)
    }

    private suspend fun scheduleJob(phone: String, content: String, interval: Long, repeatCount: Int, chatId: Long, config: Config): Int {
        val scheduledTime = System.currentTimeMillis() + interval
        val job = ScheduledJob(
            phone = phone,
            content = content,
            scheduledTime = scheduledTime,
            interval = interval,
            repeatCount = repeatCount,
            workId = null,
            chatId = chatId
        )
        val jobId = appDao.insertJob(job)
        val workData = workDataOf(
            "job_id" to jobId,
            "is_job" to true
        )
        val workRequest = OneTimeWorkRequestBuilder<SmsWorker>()
            .setInputData(workData)
            .setInitialDelay(interval, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(applicationContext).enqueue(workRequest)
        appDao.updateJob(job.copy(id = jobId.toInt(), workId = workRequest.id.toString()))
        return jobId.toInt()
    }

    private suspend fun saveLog(phone: String, content: String, status: String, imagePath: String?, chatId: Long) {
        val log = SmsLog(
            phone = phone,
            content = content,
            timestamp = System.currentTimeMillis(),
            status = status,
            imagePath = imagePath
        )
        appDao.insertLog(log)
        val config = appDao.getConfig()
        if (config != null && chatId != 0L) {
            sendTelegramResponse(config.token, chatId, "Log: $phone - $status")
        }
    }

    private fun sendTelegramResponse(token: String, chatId: Long, text: String) {
        if (chatId == 0L || token.isEmpty()) return
        val url = "https://api.telegram.org/bot$token/sendMessage?chat_id=$chatId&text=$text"
        val request = Request.Builder().url(url).build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e("SmsService", "Failed to send Telegram response: ${response.code}")
                }
            }
        } catch (e: Exception) {
            Log.e("SmsService", "Failed to send Telegram response: ${e.message}")
        }
    }

    override fun onDestroy() {
        isRunning = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}