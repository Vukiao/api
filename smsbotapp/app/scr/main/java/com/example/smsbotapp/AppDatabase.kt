package com.example.smsbotapp

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [Config::class, SmsLog::class, ScheduledJob::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appDao(): AppDao
}