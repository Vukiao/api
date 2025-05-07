package com.example.smsbotapp

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.room.Room
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    companion object {
        var screenCaptureIntent: Intent? = null
        private const val REQUEST_CODE_SCREEN_CAPTURE = 1001
        private const val REQUEST_CODE_STORAGE = 1002
    }

    private lateinit var db: AppDatabase
    private lateinit var appDao: AppDao
    private lateinit var x1Edit: EditText
    private lateinit var y1Edit: EditText
    private lateinit var x2Edit: EditText
    private lateinit var y2Edit: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        db = Room.databaseBuilder(applicationContext, AppDatabase::class.java, "config-db").build()
        appDao = db.appDao()

        x1Edit = findViewById(R.id.x1)
        y1Edit = findViewById(R.id.y1)
        x2Edit = findViewById(R.id.x2)
        y2Edit = findViewById(R.id.y2)
        val delayEdit = findViewById<EditText>(R.id.delay)
        val tokenEdit = findViewById<EditText>(R.id.token)
        val saveButton = findViewById<Button>(R.id.save_button)
        val startButton = findViewById<Button>(R.id.start_button)
        val stopButton = findViewById<Button>(R.id.stop_button)
        val pickTextCoordinatesButton = findViewById<Button>(R.id.pick_text_coordinates)
        val pickSendCoordinatesButton = findViewById<Button>(R.id.pick_send_coordinates)
        val testCoordinatesButton = findViewById<Button>(R.id.test_coordinates)
        val viewLogButton = findViewById<Button>(R.id.view_log)
        val statusText = findViewById<TextView>(R.id.shizuku_status)

        // Kiểm tra trạng thái Shizuku
        statusText.text = if (ShizukuUtils.isShizukuAvailable()) "Shizuku: Running" else "Shizuku: Not running"
        if (!ShizukuUtils.isShizukuAvailable()) {
            ShizukuUtils.requestPermission(this) { granted ->
                statusText.text = if (granted) "Shizuku: Running" else "Shizuku: Permission denied"
            }
        }

        // Xin quyền lưu trữ (cho Android 8.0-9.0)
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), REQUEST_CODE_STORAGE)
        }

        // Xin quyền MediaProjection
        requestScreenCapturePermission()

        // Chọn tọa độ cho trường nhập văn bản
        pickTextCoordinatesButton.setOnClickListener {
            val intent = Intent(this, CoordinatePickerActivity::class.java).apply {
                putExtra("pick_text_field", true)
            }
            startActivityForResult(intent, 100)
        }

        // Chọn tọa độ cho nút gửi
        pickSendCoordinatesButton.setOnClickListener {
            val intent = Intent(this, CoordinatePickerActivity::class.java).apply {
                putExtra("pick_text_field", false)
            }
            startActivityForResult(intent, 101)
        }

        // Lưu cấu hình
        saveButton.setOnClickListener {
            if (x1Edit.text.isEmpty() || y1Edit.text.isEmpty() || x2Edit.text.isEmpty() || y2Edit.text.isEmpty() || delayEdit.text.isEmpty() || tokenEdit.text.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val config = Config(
                x1 = x1Edit.text.toString().toInt(),
                y1 = y1Edit.text.toString().toInt(),
                x2 = x2Edit.text.toString().toInt(),
                y2 = y2Edit.text.toString().toInt(),
                delay = delayEdit.text.toString().toLong(),
                token = tokenEdit.text.toString()
            )
            GlobalScope.launch {
                appDao.insertConfig(config)
                ShizukuUtils.saveToken(this@MainActivity, tokenEdit.text.toString())
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Config saved", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Khởi động dịch vụ
        startButton.setOnClickListener {
            GlobalScope.launch {
                val config = appDao.getConfig()
                if (config == null) {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Please save config first", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }
                if (screenCaptureIntent == null) {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Please grant screen capture permission", Toast.LENGTH_SHORT).show()
                    }
                    requestScreenCapturePermission()
                    return@launch
                }
                requestBatteryOptimizationExemption()
                val intent = Intent(this@MainActivity, SmsService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Service started", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Dừng dịch vụ
        stopButton.setOnClickListener {
            stopService(Intent(this, SmsService::class.java))
            Toast.makeText(this, "Service stopped", Toast.LENGTH_SHORT).show()
        }

        // Kiểm tra tọa độ
        testCoordinatesButton.setOnClickListener {
            if (x1Edit.text.isEmpty() || y1Edit.text.isEmpty() || x2Edit.text.isEmpty() || y2Edit.text.isEmpty()) {
                Toast.makeText(this, "Please enter coordinates", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            GlobalScope.launch {
                ShizukuUtils.injectTap(x1Edit.text.toString().toFloat(), y1Edit.text.toString().toFloat())
                delay(1000)
                ShizukuUtils.injectTap(x2Edit.text.toString().toFloat(), y2Edit.text.toString().toFloat())
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Tested coordinates", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Xem log
        viewLogButton.setOnClickListener {
            startActivity(Intent(this, LogActivity::class.java))
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_SCREEN_CAPTURE && resultCode == Activity.RESULT_OK && data != null) {
            screenCaptureIntent = data
        } else if (requestCode == 100 || requestCode == 101) {
            if (resultCode == RESULT_OK && data != null) {
                val x = data.getFloatExtra("x", 0f).toInt()
                val y = data.getFloatExtra("y", 0f).toInt()
                when (requestCode) {
                    100 -> { // x1, y1
                        x1Edit.setText(x.toString())
                        y1Edit.setText(y.toString())
                    }
                    101 -> { // x2, y2
                        x2Edit.setText(x.toString())
                        y2Edit.setText(y.toString())
                    }
                }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_STORAGE && grantResults.isNotEmpty() && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Storage permission required for screenshots", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestScreenCapturePermission() {
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(projectionManager.createScreenCaptureIntent(), REQUEST_CODE_SCREEN_CAPTURE)
    }

    private fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            intent.data = android.net.Uri.parse("package:$packageName")
            startActivity(intent)
        }
    }
}