package com.example.audiobible.dto

data class Book (
    val id: Int,
    val name: String,
    var isSelected: Boolean = false,
    val totalChapters: Int = 0,
    val backgroundColor: String, // HEX-код цвета (например, "#2E5298")
//    var chapterList: List<AudioItem> = emptyList()
)