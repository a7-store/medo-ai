package com.medo.aiassistant.data

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface NotificationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(notification: NotificationEntity): Long

    @Query("SELECT * FROM notifications ORDER BY timestamp DESC")
    fun getAllLive(): LiveData<List<NotificationEntity>>

    @Query("SELECT * FROM notifications WHERE timestamp >= :sinceTimestamp ORDER BY timestamp DESC")
    suspend fun getNotificationsSince(sinceTimestamp: Long): List<NotificationEntity>

    @Query("SELECT COUNT(*) FROM notifications")
    fun getCountLive(): LiveData<Int>

    @Query("DELETE FROM notifications")
    suspend fun clearAll()
}
