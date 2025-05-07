package com.example.smsbotapp

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.room.Room
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class LogActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log)

        val recyclerView = findViewById<RecyclerView>(R.id.log_list)
        recyclerView.layoutManager = LinearLayoutManager(this)

        GlobalScope.launch {
            val db = Room.databaseBuilder(applicationContext, AppDatabase::class.java, "config-db").build()
            val logs = db.appDao().getAllLogs()
            runOnUiThread {
                recyclerView.adapter = LogAdapter(logs)
            }
        }
    }
}