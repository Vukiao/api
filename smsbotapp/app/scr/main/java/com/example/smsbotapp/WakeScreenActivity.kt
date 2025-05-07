package com.example.smsbotapp

import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity

class WakeScreenActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        android.os.Handler(mainLooper).postDelayed({
            finish()
        }, 5000)
    }
}