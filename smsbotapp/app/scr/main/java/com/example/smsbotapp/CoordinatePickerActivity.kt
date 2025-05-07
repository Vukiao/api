package com.example.smsbotapp

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class CoordinatePickerActivity : AppCompatActivity() {
    private var isPickingTextField = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_coordinate_picker)

        isPickingTextField = intent.getBooleanExtra("pick_text_field", true)

        val previewText = findViewById<TextView>(R.id.preview_text)
        previewText.text = if (isPickingTextField) {
            "1. Press 'Open Messaging App'.\n2. Tap the text input field.\n3. Return here and tap to confirm."
        } else {
            "1. Press 'Open Messaging App'.\n2. Tap the send button.\n3. Return here and tap to confirm."
        }

        val openMessagingButton = findViewById<Button>(R.id.open_messaging_button)
        openMessagingButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = android.net.Uri.parse("sms:0973976398")
                component = ShizukuUtils.getDefaultMessagingApp(this@CoordinatePickerActivity)
            }
            startActivity(intent)
        }

        findViewById<View>(R.id.root_view).setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                AlertDialog.Builder(this)
                    .setTitle("Confirm Coordinates")
                    .setMessage("Selected: (${event.x}, ${event.y}). Confirm?")
                    .setPositiveButton("OK") { _, _ ->
                        val intent = Intent().apply {
                            putExtra("x", event.x)
                            putExtra("y", event.y)
                        }
                        setResult(RESULT_OK, intent)
                        finish()
                    }
                    .setNegativeButton("Cancel") { _, _ -> }
                    .show()
                true
            } else false
        }
    }
}