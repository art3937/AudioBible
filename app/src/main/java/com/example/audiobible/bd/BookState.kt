package com.example.audiobible.bd

import androidx.room.Entity
import androidx.room.PrimaryKey

// Простая модель главы для хранения состояния (прочитана/не прочитана и т.д.)
data class Chapter(
    val number: Int,
    val title: String? = null,
    val isRead: Boolean = false
)

@Entity(tableName = "books_state")
data class BookState(
    @PrimaryKey
    val id: Int = 0,
    val bookId: Int,
    val name: String,
    val backgroundColor: String? = null,
    val selectedChapter: Int = 1,
    val chapters: List<Chapter> = emptyList(),
    val generateImages: Boolean = true
)