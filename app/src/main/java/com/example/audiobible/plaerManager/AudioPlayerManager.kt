package com.example.audiobible.plaerManager

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.audiobible.dto.AudioItem
import com.google.common.util.concurrent.Futures
import dagger.hilt.android.qualifiers.ApplicationContext
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
    val coverUrl: String = "https://bing.net",
    val currentStr: String = "00:00",
    val totalStr: String = "00:00",
    val isPlaying: Boolean = false,
    val isTrackCompleted: Boolean = false,
    val isStateLoading: Boolean = false,
    // НОВОЕ ПОЛЕ: Индекс текущего трека в плеере ExoPlayer
    val currentTrackIndex: Int = 0,
    // НОВОЕ ПОЛЕ: ID книги, которая РЕАЛЬНО сейчас играет
    val playingBookId: Int = -1,
    val hasNext: Boolean = false // Нужен для логики блокировки шторки
)


@Singleton
class AudioPlayerManager @Inject constructor(
    @ApplicationContext private val appContext: Context
) {

    private val _progressState = MutableStateFlow(PlayerProgressState())
    val progressState: StateFlow<PlayerProgressState> = _progressState.asStateFlow()
    private val handler = Handler(Looper.getMainLooper())

    private val sessionToken = SessionToken(
        appContext, android.content.ComponentName(appContext, AudioPlaybackService::class.java)
    )

    // Контроллер нужен только для поддержания связи с MediaSessionService (для вывода шторки)
    private var mediaController: MediaController? = null


    val exoPlayer: ExoPlayer = ExoPlayer.Builder(appContext.applicationContext).build()

    init {

        // Слушаем состояние НАПРЯМУЮ из физического плеера, а не из контроллера
        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    stopProgressUpdate()
                }

                // На всякий случай дублируем проверку и тут
                if (exoPlayer.isPlaying) {
                    startProgressUpdate()
                } else if (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED) {
                    stopProgressUpdate()
                }
            }


            // Перехватываем любое изменение состояния Play/Pause (включая шторку и кнопки мини-плеера)
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {


                // 🔥 АВТОМАТИЧЕСКИЙ СТАРТ СЕКУНД:
                if (playWhenReady) {
                    startProgressUpdate() // Если плеер поехал — включаем таймер секунд
                } else {
                    stopProgressUpdate()  // Если встал на паузу — тушим таймер, чтобы не жрать батарею
                }
            }

            // ПРИНУДИТЕЛЬНЫЙ ПЕРЕХВАТ ПЕРЕКЛЮЧЕНИЯ ТРЕКОВ:
            // Чтобы индекс вьюмодели обновлялся мгновенно, когда шторка или плеер листают главы
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                _progressState.value = _progressState.value.copy(
                    currentTrackIndex = exoPlayer.currentMediaItemIndex,
                    isPlaying = exoPlayer.isPlaying
                )
            }
        })

        // Подключаем контроллер, чтобы Android знал, что у приложения есть UI-клиент плеера.
        // Это активирует системную интеграцию и шторку.
        val controllerFuture = MediaController.Builder(appContext,sessionToken).buildAsync()
        controllerFuture.addListener({
            try {
                mediaController = controllerFuture.get()
                Log.d(
                    "AudioPlayerManager",
                    "==> Контроллер успешно подключен к MediaSession. Шторка синхронизирована."
                )
            } catch (e: Exception) {
                Log.e("AudioPlayerManager", "Ошибка подключения MediaController: ${e.message}")
            }
        }, Handler(Looper.getMainLooper())::post)
    }


    private var currentPlayingBookId: Int = -1

    fun setPlayingBookId(bookId: Int) {
        currentPlayingBookId = bookId
        _progressState.value = _progressState.value.copy(playingBookId = bookId)
    }

    private fun updateProgressRunnable() {
        // Проверяем, жив ли поток плеера, прежде чем опрашивать прогресс
        if (!exoPlayer.applicationLooper.thread.isAlive) return

        val current = exoPlayer.currentPosition.toInt()
        val total = if (exoPlayer.duration == C.TIME_UNSET) 0 else exoPlayer.duration.toInt()
        // 🔥 ДОСТАЕМ РЕАЛЬНОЕ ИМЯ ТЕКУЩЕЙ ГЛАВЫ ИЗ МЕТАДАННЫХ ПЛЕЕРА
        val currentTrackName = exoPlayer.currentMediaItem?.mediaMetadata?.title?.toString() ?: "Глава"

        _progressState.value = _progressState.value.copy(
            current = current,
            total = total,
            currentStr = formatTime(current),
            totalStr = formatTime(total),
            isPlaying = exoPlayer.isPlaying,
            playingBookId = currentPlayingBookId,
            name = currentTrackName, // ПЕРЕДАЕМ СЮДА
            isTrackCompleted = false
        )
        handler.postDelayed(::updateProgressRunnable, 500)
    }

    // Воспроизведение списка (плейлиста) из БД кучей
    fun startPlaylist(
        chapters: List<AudioItem>,
        currentTrackIndex: Int,
        startPositionMs: Long = 0L,
        bookId: Int = 0
    ) {
        Log.d(
            "AudioPlayerManager",
            "==> Запуск плейлиста кучей. Всего глав: ${chapters.size}, индекс: $currentTrackIndex"
        )

        currentPlayingBookId = bookId

        val packageName = appContext.packageName

        // Собираем список MediaItem из списка глав через официальный Uri.Builder
        val mediaItemsList = chapters.map { audioItem ->

            val uri = Uri.Builder().scheme(android.content.ContentResolver.SCHEME_ANDROID_RESOURCE)
                .authority(packageName).path(audioItem.audioRawId.toString()).build()

            MediaItem.Builder().setUri(uri).setMediaId(uri.toString()).setMediaMetadata(
                MediaMetadata.Builder().setTitle(audioItem.name).setArtist("Аудиобиблия")
                    .build()
            ).build()

        }

        if (mediaItemsList.isNotEmpty()) {
            // Загружаем весь список в единый плеер
            exoPlayer.setMediaItems(mediaItemsList)
            // Переходим к выбранному пользователем треку в списке
            exoPlayer.seekTo(currentTrackIndex, startPositionMs)
            exoPlayer.prepare()
            exoPlayer.play()
        }
    }

    // Подготовка трека из БД без авто-воспроизведения (для отображения последнего сохраненного трека при старте)
    fun prepareTrackWithoutPlaying(chapters: List<AudioItem>,audioRawId: Int, progressMs: Int, chapterName: String ) {
        val packageName = appContext.packageName
        try {
            val uriString = "android.resource://${appContext.packageName}/$audioRawId"

            val mediaMetadata = MediaMetadata.Builder().setTitle(chapterName.ifEmpty { "AudioBible" })
                    .setArtist("Аудиобиблия").build()

            val mediaItemsList = chapters.map { audioItem ->

                val uri = Uri.Builder().scheme(android.content.ContentResolver.SCHEME_ANDROID_RESOURCE)
                    .authority(packageName).path(audioItem.audioRawId.toString()).build()

                MediaItem.Builder().setUri(uri).setMediaId(uri.toString()).setMediaMetadata(
                    MediaMetadata.Builder().setTitle(audioItem.name).setArtist("Аудиобиблия")
                        .build()
                ).build()

            }

            exoPlayer.playWhenReady = false
            //exoPlayer.setMediaItem(mediaItem)
            exoPlayer.setMediaItems(mediaItemsList)
            exoPlayer.prepare()
            exoPlayer.seekTo(progressMs.toLong())
            exoPlayer.seekTo(audioRawId, progressMs.toLong())

        } catch (e: Exception) {
            e.printStackTrace()
        }
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

    fun seekTo(positionMs: Int) {
        exoPlayer.seekTo(positionMs.toLong())
    }

    // Метод вызывается, когда приложение полностью закрывается пользователем
    fun appRelease() {
        stopProgressUpdate()
        exoPlayer.release()
        mediaController?.let {
            MediaController.releaseFuture(Futures.immediateFuture(it))
            mediaController = null
        }
    }

    fun clearMedia3Playlist() {
        // Останавливаем проигрывание
        exoPlayer.stop()

        // 🔥 ОЧИЩАЕМ ВСЮ ГУРЬБУ (плейлист) ИЗ ПАМЯТИ MEDIA3!
        exoPlayer.clearMediaItems()
    }

}

