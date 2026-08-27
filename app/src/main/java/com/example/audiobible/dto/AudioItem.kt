package com.example.audiobible.dto

data class AudioItem(
    val id: Int,

    val name: String,
    val audioPath: String = "", // Сюда будем передавать R.raw.имя_файла
    val textPath: String, // Путь к файлу в assets (например, "bible_data/genesis/1.txt")
    var isPlaying: Boolean = false,
    var isSelected: Boolean = false,
    val isLiked: Boolean = false
)