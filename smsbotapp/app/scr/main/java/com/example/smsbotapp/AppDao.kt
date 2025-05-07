package com.example.smsbotapp

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface AppDao {
    @Insert
    suspend fun insertConfig(config: Config)

    @Query("SELECT * FROM config LIMIT 1")
    suspend fun getConfig(): Config?

    @Insert
    suspend fun insertLog(log: SmsLog)

    @Query("SELECT * FROM smslog ORDER BY timestamp DESC")
    suspend fun getAllLogs(): List<SmsLog>

    @Insert
    suspend fun insertJob(job: ScheduledJob): Long

    @Query("SELECT * FROM scheduledjob ORDER BY scheduledTime ASC")
    suspend fun getAllJobs(): List<ScheduledJob>

    @Query("DELETE FROM scheduledjob WHERE id = :id")
    suspend fun deleteJob(id: Int)

    @Query("DELETE FROM scheduledjob")
    suspend fun deleteAllJobs()

    @Update
    suspend fun updateJob(job: ScheduledJob)

    @Query("SELECT * FROM scheduledjob WHERE id = :id")
    suspend fun getJobById(id: Int): ScheduledJob?
}