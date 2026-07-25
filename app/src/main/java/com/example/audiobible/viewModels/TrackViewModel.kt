package ru.netology.mediapleer2

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audiobible.bd.PlaybackHistory
import com.example.audiobible.dao.BibleDao
import com.example.audiobible.dto.AudioItem
import com.example.audiobible.plaerManager.AudioPlayerManager
import com.example.audiobible.repository.ChaptersRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class TrackViewModel @Inject constructor(
    private val playerManager: AudioPlayerManager,
    private val savedStateHandle: SavedStateHandle,
    private val repository: ChaptersRepository,
    private val bibleDao: BibleDao, // ИНЖЕКТИРУЕМ DAO ЧЕРЕЗ HILT (Убрали AppDatabase.getDatabase)
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var currentPlayingPosition: Int = -1
    private var currentBookId: Int = -1
    private var currentPlayingChapterId: Int = -1

    private val _chaptersData = MutableLiveData<List<AudioItem>>()
    val chaptersData: LiveData<List<AudioItem>> get() = _chaptersData

    val playerState = playerManager.progressState

    init {
//        restoreLastGlobalTrack()
    }

    fun getCurrentPosition(): Int = currentPlayingPosition

    fun loadChapters(bookId: Int, forcedIsPlaying: Boolean? = null) {
        currentBookId = bookId
        val chapters = repository.getChaptersForBook(context, bookId)
        val isPlayerPlaying = forcedIsPlaying ?: playerManager.isPlaying

        _chaptersData.value = chapters.map { chapter ->
            if (chapter.id == currentPlayingChapterId) chapter.copy(isPlaying = isPlayerPlaying, ) else chapter
        }
    }

    fun toggleChapter(chapter: AudioItem, position: Int) {
        val isPlayingCurrent = currentPlayingChapterId == chapter.id && playerManager.isPlaying

        if (isPlayingCurrent) {
            saveCurrentPlaybackPosition(chapter)
            playerManager.pause()
            currentPlayingChapterId = -1
            currentPlayingPosition = -1

            _chaptersData.value = _chaptersData.value?.map {
                if (it.id == chapter.id) it.copy(isPlaying = false) else it
            }
        } else {
            currentPlayingChapterId = chapter.id
            currentPlayingPosition = position

            playerManager.startRawTrack(chapter.audioRawId, chapterName = chapter.name)
            saveCurrentPlaybackPosition(chapter)

            _chaptersData.value = _chaptersData.value?.map {
                if (it.id == chapter.id) it.copy(isPlaying = true) else it.copy(isPlaying = false)
            }
        }
    }

    fun saveCurrentPlaybackPosition(chapter: AudioItem) {
        // КРИТИЧЕСКИ ВАЖНО: Забираем позицию плеера на Главном потоке ДО ухода в корутину БД!
        val exactProgressMs = playerManager.exoPlayer.currentPosition

        viewModelScope.launch(Dispatchers.IO) {
            bibleDao.savePlaybackPosition(
                PlaybackHistory(
                    id = 0, // ГАРАНТИРУЕТ ТОЛЬКО ОДНУ СТРОКУ В ТАБЛИЦЕ ДЛЯ ВСЕГО ПРИЛОЖЕНИЯ
                    bookId = currentBookId,
                    chapterNumber = chapter.name,
                    playbackPositionMs = exactProgressMs,
                    lastAccessed = System.currentTimeMillis()
                )
            )
            Log.d("DB_INSPECTOR", "МЫ ПЕРЕЗАПИСАЛИ  ${currentBookId}  ЕДИНСТВЕННЫЙ ТРЕК В БАЗЕ: ${chapter.name}, Прогресс $exactProgressMs мс")
        }
    }

    fun savePositionOnExit(chapter: AudioItem) {
        if (currentPlayingChapterId == -1 || currentPlayingChapterId == 0 || currentBookId == -1) return

        // Забираем позицию плеера на Главном потоке синхронно
        val exactProgressMs = playerManager.exoPlayer.currentPosition

        applicationScope.launch {
            bibleDao.savePlaybackPosition(
                PlaybackHistory(
                    id = 0, // ВСЕГДА 0 — старый глобальный трек сотрется, запишется этот
                    bookId = currentBookId,
                    chapterNumber = chapter.name,
                    playbackPositionMs = exactProgressMs,
                    lastAccessed = System.currentTimeMillis()
                )
            )
            Log.d("DB_INSPECTOR", "ГЛОБАЛЬНОЕ СОХРАНЕНИЕ ПРИ ВЫХОДЕ: ${chapter.name}, Прогресс $exactProgressMs мс")
        }
    }

    fun resumeTrack() {
        playerManager.exoPlayer.play()
    }

    fun restoreLastGlobalTrack() {
        viewModelScope.launch(Dispatchers.IO) {
            val lastHistory = bibleDao.getLastPlayedAudio() ?: return@launch

            if (currentPlayingChapterId == -1) {
                currentBookId = lastHistory.bookId

                val chapters = repository.getChaptersForBook(context, lastHistory.bookId)
                currentPlayingPosition = chapters.indexOfFirst { it.name == lastHistory.chapterNumber }

                val targetTrack = chapters.getOrNull(currentPlayingPosition)
                if (targetTrack != null) {
                    currentPlayingChapterId = targetTrack.id
                    withContext(Dispatchers.Main) {
                        // Просто готовим трек в памяти плеера
                        playerManager.prepareTrackWithoutPlaying(
                            targetTrack.audioRawId,
                            lastHistory.playbackPositionMs.toInt()
                        )


//                        playerManager.startRawTrack(chapter.audioRawId, chapterName = chapter.name)
//                        saveCurrentPlaybackPosition(chapter)
//
//                        _chaptersData.value = _chaptersData.value?.map {
//                            if (it.id == chapter.id) it.copy(isPlaying = true) else it.copy(isPlaying = false)
//                        } /// доделать идею
                    }
                }
            }
        }
    }



    fun rewind15Seconds() {
        val currentPos = playerManager.exoPlayer.currentPosition
        val newPos = (currentPos - 15000).coerceAtLeast(0)
        playerManager.seekTo(newPos.toInt())
    }

    fun forward15Seconds() {
        val currentPos = playerManager.exoPlayer.currentPosition
        val totalDuration = playerManager.exoPlayer.duration

        if (totalDuration != androidx.media3.common.C.TIME_UNSET) {
            val newPos = (currentPos + 15000).coerceAtMost(totalDuration)
            playerManager.seekTo(newPos.toInt())
        }
    }

    fun nextTrack() {
        val chapters = _chaptersData.value ?: return
        val nextIndex = currentPlayingPosition + 1
        if (nextIndex >= chapters.size) return // нет следующего

        // сохраняем позицию текущего трека
        val currentChapter = chapters.getOrNull(currentPlayingPosition)
        if (currentChapter != null) {
            saveCurrentPlaybackPosition(currentChapter)
        }

        val next = chapters[nextIndex]
        currentPlayingChapterId = next.id
        currentPlayingPosition = nextIndex
        playerManager.startRawTrack(next.audioRawId, chapterName = next.name)

        _chaptersData.value = chapters.mapIndexed { i, c ->
            if (i == nextIndex) c.copy(isPlaying = true) else c.copy(isPlaying = false)
        }
    }

    fun previousTrack() {
        val chapters = _chaptersData.value ?: return
        val prevIndex = currentPlayingPosition - 1
        if (prevIndex < 0) return // нет предыдущего

        val currentChapter = chapters.getOrNull(currentPlayingPosition)
        if (currentChapter != null) {
            saveCurrentPlaybackPosition(currentChapter)
        }

        val prev = chapters[prevIndex]
        currentPlayingChapterId = prev.id
        currentPlayingPosition = prevIndex
        playerManager.startRawTrack(prev.audioRawId, chapterName = prev.name)

        _chaptersData.value = chapters.mapIndexed { i, c ->
            if (i == prevIndex) c.copy(isPlaying = true) else c.copy(isPlaying = false)
        }
    }

    fun seekTo(positionMs: Int) { playerManager.seekTo(positionMs) }

    fun pauseTrack() {
        _chaptersData.value = _chaptersData.value?.map {
            if (it.isPlaying) it.copy(isPlaying = false) else it
        }
        playerManager.pause()

    }
}
