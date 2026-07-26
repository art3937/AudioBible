package com.example.audiobible.plaerManager

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import android.os.Handler
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.RawResourceDataSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton


// Класс для хранения состояния плеера
data class PlayerProgressState(
    val current: Int = 0,
    val name: String = "",
    val total: Int = 0,
    val coverUrl: String = "https://sl.bing.net/jbxF3ktDtwy",
    val currentStr: String = "00:00",
    val totalStr: String = "00:00",
    val isPlaying: Boolean = false,
    val isTrackCompleted: Boolean = false,
    val isStateLoading: Boolean = false
)



@Singleton
class AudioPlayerManager @Inject constructor(@ApplicationContext context: Context) {
    // Используем applicationContext, чтобы избежать утечек памяти при повороте
    val exoPlayer = ExoPlayer.Builder(context.applicationContext).build().apply {
        addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    stopProgressUpdate()
                    _progressState.value = _progressState.value.copy(
                        isPlaying = false,
                        isTrackCompleted = true,
                        isStateLoading = isLoading
                    )
                }
                _progressState.value = _progressState.value.copy(
                    isStateLoading = playbackState == Player.STATE_BUFFERING
                )
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                // Автоматически обновляем флаг проигрывания в UI
                _progressState.value = _progressState.value.copy(isPlaying = isPlaying)
                if (isPlaying) startProgressUpdate() else stopProgressUpdate()
            }
        })
    }
            private val _progressState = MutableStateFlow(PlayerProgressState())
            val progressState: StateFlow<PlayerProgressState> = _progressState.asStateFlow()
    private val handler = Handler(Looper.getMainLooper())
    // ИСПРАВЛЕНИЕ: Вместо "object : Runnable" используем обычный метод,
    // ссылку на который безопасно передавать в Handler
    private fun updateProgressRunnable() {
        val current = exoPlayer.currentPosition.toInt()
        val total = if (exoPlayer.duration == androidx.media3.common.C.TIME_UNSET) 0 else exoPlayer.duration.toInt()

        _progressState.value = _progressState.value.copy(
            current = current,
            total = total,
            currentStr = formatTime(current),
            totalStr = formatTime(total),
            isPlaying = exoPlayer.isPlaying,
            isTrackCompleted = false,
            // ======= ВОТ ОНО, ИСПРАВЛЕНИЕ! =======
            // Говорим плееру: "Сохраняй то название, которое там уже лежало!"
            name = _progressState.value.name
            // ======================================
        )
        handler.postDelayed(::updateProgressRunnable, 500)
    }

    // Изменили метод: теперь можно передать позицию для восстановления
    fun startNewTrack(url: String, startPositionMs: Long = 0L) {
        // ЗАЩИТА ОТ ПОВТОРНОГО ЗАПУСКА:
        // Если этот трек УЖЕ играет прямо сейчас, игнорируем повторный вызов при повороте
        if (exoPlayer.currentMediaItem?.mediaId == url) {
            if (startPositionMs > 0) {
                exoPlayer.seekTo(startPositionMs)
            }
            exoPlayer.play()
        }
        else {
            // Присваиваем mediaId, чтобы плеер мог распознать, какой трек сейчас играет
            val mediaItem = MediaItem.Builder().setUri(url).setMediaId(url).build()
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.play()
        }
    }

    //Для локальных файлов
    fun startRawTrack(rawResourceId: Int,startPositionMs: Long = 0L,chapterName: String = "", bookName: String = "") {
        // Безопасный сборщика URI через стандартный Android SDK:
        // Результат будет в формате: android.resource://имя_пакета_приложения/идентификатор_ресурса
        val uri = Uri.Builder()
            .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
            .path(rawResourceId.toString())
            .build()

        val mediaId = uri.toString()
        val fullTitle = "$bookName — $chapterName"
        if (exoPlayer.currentMediaItem?.mediaId == mediaId) {
            if (startPositionMs > 0) {
                exoPlayer.seekTo(startPositionMs)
            }
            exoPlayer.play()
            _progressState.value = _progressState.value.copy(
                name = fullTitle// Записываем собранную строку в текстовое поле состояния
            )
        } else {
            val mediaItem = MediaItem.Builder()
                .setUri(uri)
                .setMediaId(mediaId)
                .build()
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.play()
        }
        _progressState.value = _progressState.value.copy(
            name = fullTitle// Записываем собранную строку в текстовое поле состояния
        )
    }

    val isPlaying: Boolean
        get() = exoPlayer.isPlaying


    private fun startProgressUpdate() {
        handler.removeCallbacks(::updateProgressRunnable)
        handler.post(::updateProgressRunnable)
    }

    private fun stopProgressUpdate() {
        handler.removeCallbacks(::updateProgressRunnable)
    }

    private fun formatTime(milliseconds: Int): String {
        val minutes = (milliseconds / 1000) / 60
        val seconds = (milliseconds / 1000) % 60
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }

    // Добавьте этот метод внутрь класса AudioPlayerManager
    // Добавьте этот метод внутрь вашего AudioPlayerManager
    fun prepareTrackWithoutPlaying(audioRawId: Int, progressMs: Int) {
        try {
            // 1. Создаем MediaItem из локального raw-ресурса
            // Замените "com.example.audiobible" на имя вашего пакета, если оно отличается
            val uriString = "android.resource://com.example.audiobible/$audioRawId"
            val mediaItem = MediaItem.fromUri(uriString)

            // 2. Говорим ExoPlayer, что запускаться САМОМУ НЕ НУЖНО (остаемся на паузе)
            exoPlayer.playWhenReady = false

            // 3. Устанавливаем трек в плеер
            exoPlayer.setMediaItem(mediaItem)

            // 4. Подготавливаем плеер (начнется буферизация файла)
            exoPlayer.prepare()

            // 5. Перематываем на нужную миллисекунду из базы данных
            exoPlayer.seekTo(progressMs.toLong())

            // 6. Обновляем ваш внутренний StateFlow (progressState),
            // чтобы нижний мини-плеер сразу узнал, где мы остановились
            _progressState.value = PlayerProgressState(
                isPlaying = false,
                totalStr = progressMs.toString(),
                // если в вашем PlayerProgressState есть другие поля (например, currentTrack),
                // передайте их сюда при необходимости
                name = "Библия — Загрузка последнего трека..." // Укажите имя, чтобы плеер появился!
            )

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }




    // Возвращает общую длительность трека в миллисекундах
    val duration: Long
        get() = if (exoPlayer.duration < 0) 0 else exoPlayer.duration

    // Возвращает текущую позицию воспроизведения в миллисекундах
    val currentPosition: Long
        get() = exoPlayer.currentPosition

    fun play() {
        exoPlayer.play()
        startProgressUpdate()
    }

    fun pause() {
        exoPlayer.pause()
        stopProgressUpdate()
    }

    fun resume() {
        exoPlayer.play()
    }

    fun stop() {
        exoPlayer.stop()
    }

    // Метод для перемотки на указанную позицию (в миллисекундах)
    fun seekTo(positionMs: Int) {
        exoPlayer.seekTo(positionMs.toLong())
    }

    fun release() {
        stopProgressUpdate()
        exoPlayer.release()
    }
}
