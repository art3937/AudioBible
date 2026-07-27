package com.example.audiobible.dto

data class Book (
    val id: Int,
    val name: String,
    val folderName: String,       // Техническое имя папки (например, "genesis")
    var isSelected: Boolean = false,
    val totalChapters: Int = 0,
    val backgroundColor: String,  // HEX-код цвета
//    var chapterList: List<AudioItem> = emptyList()
)