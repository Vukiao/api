package com.example.smsbotapp

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class ScheduledJob(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val phone: String,
    val content: String,
    val scheduledTime: Long,
    val interval: Long,
    val repeatCount: Int,
    val workId: String?,
    val chatId: Long
)