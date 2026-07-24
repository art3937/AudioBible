package com.example.audiobible.bd

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bookmarks")
data class Bookmark(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Int,          // ID книги (например, 1 - Бытие)
    val chapterNumber: Int,   // Номер главы
    val verseNumber: Int?,    // Номер стиха (опционально, если закладка на текст)
    val timestamp: Long = System.currentTimeMillis() // Время добавления
)