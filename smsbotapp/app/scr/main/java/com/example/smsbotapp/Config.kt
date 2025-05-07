package com.example.smsbotapp

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class Config(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val x1: Int,
    val y1: Int,
    val x2: Int,
    val y2: Int,
    val delay: Long,
    val token: String
)