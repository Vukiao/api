package com.example.smsbotapp

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class SmsLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val phone: String,
    val content: String,
    val timestamp: Long,
    val status: String,
    val imagePath: String? = null
)