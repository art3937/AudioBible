package com.example.audiobible.dto

import android.net.Uri
import androidx.core.net.toUri
import java.net.URI

data class AudioItem(
    val id: Int,
    val name: String,
    val audioRawId: Int = 0, // Сюда будем передавать R.raw.имя_файла
    var isPlaying: Boolean = false
)