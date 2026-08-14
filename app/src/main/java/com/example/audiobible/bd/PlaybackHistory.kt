package com.example.audiobible.bd

import androidx.room.Entity

// Объявляем составной первичный ключ из двух колонок
@Entity(tableName = "playback_history", primaryKeys = ["bookId", "chapterNumber"])
data class PlaybackHistory(
    val bookId: Int,             // ID книги
    val chapterNumber: String,   // Номер или название главы (например, "Глава 1")
    val playbackPositionMs: Long,
    val lastAccessed: Long,
    val isSelected: Boolean,
    val isLiked: Boolean = false // Наш флаг лайка, который теперь будет жить вечно!
)
