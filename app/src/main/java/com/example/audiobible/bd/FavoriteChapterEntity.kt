package com.example.audiobible.bd

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorite_chapters")
data class FavoriteChapterEntity(
    @PrimaryKey val id: Int, // ID главы
    val name: String,        // Название главы
    val bookId: Int,         // К какой книге относится (чтобы потом плеер понимал context)
    val textPath: String     // Путь к тексту
)
