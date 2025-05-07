package com.example.smsbotapp

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Environment
import android.util.Log
import androidx.room.Room
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.delay
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class SmsWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val db = Room.databaseBuilder(applicationContext, AppDatabase::class.java, "config-db").build()
        val appDao = db.appDao()
        val config = appDao.getConfig() ?: run {
            Log.e("SmsWorker", "No config found")
            return Result.failure()
        }

        val isJob = inputData.getBoolean("is_job", false)
        val jobId = inputData.getInt("job_id", -1)
        val phone: String
        val content: String
        val chatId: Long
        if (isJob) {
            val job = appDao.getJobById(jobId) ?: run {
                Log.e("SmsWorker", "Job not found: $jobId")
                return Result.failure()
            }
            phone = job.phone
            content = job.content
            chatId = job.chatId
        } else {
            phone = inputData.getString("phone") ?: run {
                Log.e("SmsWorker", "Phone not provided")
                return Result.failure()
            }
            content = inputData.getString("content") ?: run {
                Log.e("SmsWorker", "Content not provided")
                return Result.failure()
            }
            chatId = inputData.getLong("chat_id", 0L)
        }

        if (!ShizukuUtils.isShizukuAvailable()) {
            Log.e("SmsWorker", "Shizuku not available")
            saveLog(phone, content, "Failed", null, chatId)
            return Result.failure()
        }

        wakeScreen()
        delay(500) // Đợi màn hình bật

        // Gửi SMS
        if (!ShizukuUtils.startSmsIntent(applicationContext, phone)) {
            Log.e("SmsWorker", "Failed to start SMS intent")
            saveLog(phone, content, "Failed", null, chatId)
            return Result.failure()
        }
        delay(config.delay)

        if (!ShizukuUtils.injectTap(config.x1.toFloat(), config.y1.toFloat())) {
            Log.e("SmsWorker", "Failed to tap text field")
            saveLog(phone, content, "Failed", null, chatId)
            return Result.failure()
        }
        delay(config.delay)

        if (!ShizukuUtils.injectTextViaClipboard(applicationContext, content)) {
            Log.e("SmsWorker", "Failed to inject text")
            saveLog(phone, content, "Failed", null, chatId)
            return Result.failure()
        }
        delay(config.delay)

        if (!ShizukuUtils.injectTap(config.x2.toFloat(), config.y2.toFloat())) {
            Log.e("SmsWorker", "Failed to tap send button")
            saveLog(phone, content, "Failed", null, chatId)
            return Result.failure()
        }
        delay(500) // Đợi tin nhắn gửi

        // Chụp màn hình và gửi qua Telegram
        val imagePath = captureScreenshot()
        if (imagePath != null && chatId != 0L) {
            ShizukuUtils.sendPhotoViaTelegram(applicationContext, config.token, chatId, imagePath)
        }

        // Lưu log
        saveLog(phone, content, "Success", imagePath, chatId)

        // Xử lý lặp lại cho job
        if (isJob) {
            val job = appDao.getJobById(jobId) ?: return Result.success()
            if (job.repeatCount == 0 || job.repeatCount > 1) {
                val newRepeatCount = if (job.repeatCount > 0) job.repeatCount - 1 else 0
                val newScheduledTime = System.currentTimeMillis() + job.interval
                val updatedJob = job.copy(scheduledTime = newScheduledTime, repeatCount = newRepeatCount)
                appDao.updateJob(updatedJob)
                val workData = workDataOf(
                    "job_id" to jobId,
                    "is_job" to true
                )
                val workRequest = OneTimeWorkRequestBuilder<SmsWorker>()
                    .setInputData(workData)
                    .setInitialDelay(job.interval, TimeUnit.MILLISECONDS)
                    .build()
                WorkManager.getInstance(applicationContext).enqueue(workRequest)
                appDao.updateJob(updatedJob.copy(workId = workRequest.id.toString()))
            } else {
                appDao.deleteJob(jobId)
            }
        }

        return Result.success()
    }

    private fun wakeScreen() {
        val intent = Intent(applicationContext, WakeScreenActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        applicationContext.startActivity(intent)
    }

    private suspend fun saveLog(phone: String, content: String, status: String, imagePath: String?, chatId: Long) {
        val db = Room.databaseBuilder(applicationContext, AppDatabase::class.java, "config-db").build()
        val log = SmsLog(
            phone = phone,
            content = content,
            timestamp = System.currentTimeMillis(),
            status = status,
            imagePath = imagePath
        )
        db.appDao().insertLog(log)
    }

    private fun captureScreenshot(): String? {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            Log.e("SmsWorker", "WRITE_EXTERNAL_STORAGE permission not granted")
            return null
        }

        val projectionManager = applicationContext.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val intent = MainActivity.screenCaptureIntent ?: run {
            Log.e("SmsWorker", "Screen capture intent not available")
            return null
        }
        val mediaProjection = projectionManager.getMediaProjection(Activity.RESULT_OK, intent) ?: run {
            Log.e("SmsWorker", "Failed to get MediaProjection")
            return null
        }

        val metrics = applicationContext.resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        val virtualDisplay = mediaProjection.createVirtualDisplay(
            "ScreenCapture",
            width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader.surface, null, null
        )

        Thread.sleep(500)
        val image = imageReader.acquireLatestImage() ?: run {
            virtualDisplay.release()
            mediaProjection.stop()
            imageReader.close()
            Log.e("SmsWorker", "Failed to acquire image")
            return null
        }

        val planes = image.planes
        val buffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val bitmap = Bitmap.createBitmap(rowStride / pixelStride, height, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(buffer)
        image.close()

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val storageDir = File(applicationContext.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "SmsBotApp")
        storageDir.mkdirs()
        val imageFile = File(storageDir, "SMS_$timeStamp.jpg")

        try {
            FileOutputStream(imageFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            return imageFile.absolutePath
        } catch (e: Exception) {
            Log.e("SmsWorker", "Failed to save screenshot: ${e.message}")
            return null
        } finally {
            bitmap.recycle()
            virtualDisplay.release()
            mediaProjection.stop()
            imageReader.close()
        }
    }
}