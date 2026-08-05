package com.example.audiobible.bd


import androidx.room.Entity

@Entity(tableName = "playback_history", primaryKeys = ["bookId", "chapterNumber"])
data class PlaybackHistory(
    val id: Int = 0,
    val bookId: Int,
    val chapterNumber: String,
    val playbackPositionMs: Long, // Позиция в миллисекундах (где остановился плеер)
    val lastAccessed: Long = System.currentTimeMillis(),
    val isSelected: Boolean = false // Пометка выделения главы
)
