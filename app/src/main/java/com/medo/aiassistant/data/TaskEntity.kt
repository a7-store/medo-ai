package com.medo.aiassistant.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val requester: String,
    val taskDescription: String,
    val deadline: String? = null,
    val isUrgent: Boolean = false,
    var isCompleted: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)
