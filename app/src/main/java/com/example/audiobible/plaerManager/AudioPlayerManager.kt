package com.example.audiobible.plaerManager

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.audiobible.dto.AudioItem
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
    val hasNext: Boolean = false // Нужен для логики блокировки шторки
)

@Singleton
class AudioPlayerManager @Inject constructor(@ApplicationContext private val appContext: Context) {

    private val _progressState = MutableStateFlow(PlayerProgressState())
    val progressState: StateFlow<PlayerProgressState> = _progressState.asStateFlow()
    private val handler = Handler(Looper.getMainLooper())

    // Ссылка на токен нашего сервиса шторки
    private val sessionToken = SessionToken(
        appContext,
        android.content.ComponentName(appContext, AudioPlaybackService::class.java)
    )

    // Асинхронный контроллер Media3
    private var mediaController: MediaController? = null

    // Локальный плеер на случай, если сервис еще стартует
    private val localPlayer = ExoPlayer.Builder(appContext.applicationContext).build()

    // Главное свойство: все вызовы автоматически идут на контроллер сессии (для шторки)
    val exoPlayer: Player
        get() = mediaController ?: localPlayer

    init {
        // Подключаем UI-контроллер к нашему сервису. Это заставит Android вывести шторку!
        val controllerFuture = MediaController.Builder(appContext, sessionToken).buildAsync()
        controllerFuture.addListener({
            try {
                mediaController = controllerFuture.get()
                Log.d("AudioPlayerManager", "==> Контроллер успешно подключен к MediaSession. Шторка активирована.")

                // Перевешиваем слушатель состояния на контроллер сессии
                mediaController?.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_ENDED) {
                            stopProgressUpdate()
                            _progressState.value = _progressState.value.copy(
                                isPlaying = false,
                                isTrackCompleted = true,
                                isStateLoading = exoPlayer.isLoading
                            )
                        }
                        _progressState.value = _progressState.value.copy(
                            isStateLoading = playbackState == Player.STATE_BUFFERING
                        )
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _progressState.value = _progressState.value.copy(isPlaying = isPlaying)
                        if (isPlaying) startProgressUpdate() else stopProgressUpdate()
                    }
                })
            } catch (e: Exception) {
                Log.e("AudioPlayerManager", "Ошибка подключения MediaController: ${e.message}")
            }
        }, Handler(Looper.getMainLooper())::post)
    }

    var onNextTrackRequested: (() -> Unit)? = null

    private fun updateProgressRunnable() {
        val current = exoPlayer.currentPosition.toInt()
        val total = if (exoPlayer.duration == androidx.media3.common.C.TIME_UNSET) 0 else exoPlayer.duration.toInt()

        _progressState.value = _progressState.value.copy(
            current = current,
            total = total,
            currentStr = formatTime(current),
            totalStr = formatTime(total),
            isPlaying = exoPlayer.isPlaying,
            isTrackCompleted = false
        )
        handler.postDelayed(::updateProgressRunnable, 500)
    }

    // Для локальных файлов из папки raw
    fun startRawTrack(rawResourceId: Int, startPositionMs: Long = 0L, chapterName: String = "") {
        Log.d("AudioPlayerManager", "==> startRawTrack: запуск главы '$chapterName'")

        val packageName = appContext.packageName
        val uriString = "android.resource://$packageName/$rawResourceId"

        // 1. Создаем текущий трек
        val currentMediaItem = MediaItem.Builder()
            .setUri(Uri.parse(uriString))
            .setMediaId(uriString)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(chapterName).build())
            .build()

        // 2. Создаем пустой "хвостик" (фейковый трек)
        // Как только Media3 увидит второй элемент, она САМА включит кнопку "Вперед" в шторке и на часах!
        val fakeNextMediaItem = MediaItem.Builder()
            .setUri(Uri.parse(uriString))
            .setMediaId(uriString)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(chapterName).build())
            .build()

        // Загружаем в плеер список из двух элементов вместо одного
        exoPlayer.setMediaItems(listOf(currentMediaItem, fakeNextMediaItem))
        exoPlayer.seekTo(0, startPositionMs)
        exoPlayer.prepare()
        exoPlayer.play()
    }


    // Подготовка трека из БД без авто-воспроизведения
    fun prepareTrackWithoutPlaying(audioRawId: Int, progressMs: Int, chapterName: String = "") {
        try {
            val uriString = "android.resource://${appContext.packageName}/$audioRawId"

            val mediaMetadata = MediaMetadata.Builder()
                .setTitle(chapterName.ifEmpty { "AudioBible" })
                .setArtist("Аудиобиблия")
                .build()

            val mediaItem = MediaItem.Builder()
                .setUri(Uri.parse(uriString))
                .setMediaId(uriString)
                .setMediaMetadata(mediaMetadata)
                .build()

            exoPlayer.playWhenReady = false
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.seekTo(progressMs.toLong())

            _progressState.value = PlayerProgressState(
                isPlaying = false,
                current = progressMs,
                currentStr = formatTime(progressMs),
                name = chapterName.ifEmpty { "Библия — Загрузка..." }
            )
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

    val duration: Long
        get() = if (exoPlayer.duration < 0) 0 else exoPlayer.duration

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

    fun seekTo(positionMs: Int) {
        exoPlayer.seekTo(positionMs.toLong())
    }

    fun release() {
        stopProgressUpdate()
        localPlayer.release()
    }

    fun startPlaylist(chapters: List<AudioItem>, currentTrackIndex: Int, startPositionMs: Long = 0L) {
        Log.d("AudioPlayerManager", "==> Запуск плейлиста кучей. Всего глав: ${chapters.size}, индекс: $currentTrackIndex")

        try {
            appContext.startService(Intent(appContext, AudioPlaybackService::class.java))
        } catch (e: Exception) {
            Log.e("AudioPlayerManager", "Ошибка старта сервиса: ${e.message}")
        }

        val packageName = appContext.packageName

        val mediaItemsList = chapters.map { audioItem ->
            // ЖЕЛЕЗОБЕТОННЫЙ ФОРМАТ URI ДЛЯ EXOPLAYER:
            // Плееру часто нужен формат: android.resource://имя_пакета/raw/имя_ресурса
            // Или числовой: android.resource://имя_пакета/число
            // Мы соберем URI через официальный билдер, чтобы исключить синтаксические ошибки с косыми чертами
            val uri = android.net.Uri.Builder()
                .scheme(android.content.ContentResolver.SCHEME_ANDROID_RESOURCE)
                .authority(packageName)
                .path(audioItem.audioRawId.toString()) // Убедитесь, что тут именно имя свойства вашего ID (например, audioRawId)
                .build()

            val mediaId = uri.toString()

            MediaItem.Builder()
                .setUri(uri)
                .setMediaId(mediaId)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(audioItem.name) // Имя главы
                        .setArtist("Аудиобиблия")
                        .build()
                )
                .build()
        }

        if (mediaItemsList.isEmpty()) {
            Log.e("AudioPlayerManager", "Список глав пуст, нечего загружать!")
            return
        }

        // Загружаем всю кучу
        exoPlayer.setMediaItems(mediaItemsList)

        // Безопасно переходим на нужный индекс
        val safeIndex = if (currentTrackIndex in mediaItemsList.indices) currentTrackIndex else 0
        exoPlayer.seekTo(safeIndex, startPositionMs)

        exoPlayer.prepare()
        exoPlayer.play()

        val currentChapter = chapters.getOrNull(safeIndex)
        _progressState.value = _progressState.value.copy(name = currentChapter?.name ?: "")
    }


}
