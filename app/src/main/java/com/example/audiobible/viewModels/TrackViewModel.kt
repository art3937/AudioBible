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
import androidx.media3.session.SessionToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.media3.session.MediaSession
import javax.inject.Inject

@HiltViewModel
// TrackViewModel: bridge between UI (FragmentChapter) and playback logic
// Responsibilities:
// - Expose chaptersData and playerState for UI binding
// - Start/pause/seek tracks via AudioPlayerManager when user interacts with UI
// - Delegate next/previous track decision to PlaybackController (which handles DB + player start safely)
// - Update in-memory chaptersData so UI shows which item is selected / playing
// Important thread rules:
// - Calls that touch ExoPlayer must be on Main thread (AudioPlayerManager ensures this where needed)
// - DB operations are dispatched to IO (viewModelScope.launch(Dispatchers.IO))
class TrackViewModel @Inject constructor(
    private val playerManager: AudioPlayerManager,
    private val savedStateHandle: SavedStateHandle,
    private val repository: ChaptersRepository,
    private val bibleDao: BibleDao, // ИНЖЕКТИРУЕМ DAO ЧЕРЕЗ HILT (ViewModel управляет БД)
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
        // Попытка восстановить последний глобальный трек при старте ViewModel
        restoreLastGlobalTrack()

        // Автопереключение на следующий трек при окончании текущего
        viewModelScope.launch {
            var lastIsCompleted = false
            playerManager.progressState.collect { state ->
                val nowCompleted = state.isTrackCompleted
                if (!lastIsCompleted && nowCompleted) {
                    // трек только что завершился
                    nextTrack()
                }
                lastIsCompleted = nowCompleted
            }
        }
    }

    fun getCurrentPosition(): Int = currentPlayingPosition

    fun loadChapters(bookId: Int, forcedIsPlaying: Boolean? = null) {
        currentBookId = bookId
        val chapters = repository.getChaptersForBook(context, bookId)
        val isPlayerPlaying = forcedIsPlaying ?: playerManager.isPlaying

        // Сначала выставляем базовый список (без выделения)
        _chaptersData.value = chapters.map { chapter ->
            if (chapter.id == currentPlayingChapterId) chapter.copy(isPlaying = isPlayerPlaying) else chapter.copy(isSelected = false)
        }

        // Проверяем в БД есть ли последняя сохранённая запись и помечаем её, если она относится к текущей книге
        viewModelScope.launch(Dispatchers.IO) {
            val last = bibleDao.getLastPlayedAudio()
            if (last != null && last.bookId == bookId) {
                val index = chapters.indexOfFirst { it.name == last.chapterNumber }
                if (index >= 0) {
                    // Обновляем на главном потоке
                    withContext(Dispatchers.Main) {
                        _chaptersData.value = chapters.mapIndexed { i, c ->
                            c.copy(isSelected = (i == index), isPlaying = (i == index && isPlayerPlaying))
                        }
                    }
                }
            }
        }
    }

    fun toggleChapter(chapter: AudioItem, position: Int) {
        val isPlayingCurrent = currentPlayingChapterId == chapter.id && playerManager.isPlaying

        if (isPlayingCurrent) {
            // При паузе отправляем в базу состояние с сохраненным выделением
            saveCurrentPlaybackPosition(chapter.copy(isSelected = true))
            playerManager.pause()
            currentPlayingChapterId = -1
            currentPlayingPosition = -1

            // На паузе трек перестает играть (isPlaying = false), но выделение (isSelected) ОСТАЕТСЯ true
            _chaptersData.value = _chaptersData.value?.map {
                if (it.id == chapter.id) it.copy(isPlaying = false, isSelected = true) else it
            }
        } else {
            currentPlayingChapterId = chapter.id
            currentPlayingPosition = position

            playerManager.startRawTrack(chapter.audioRawId, chapterName = chapter.name)
            // При старте отправляем в базу новую главу с активным селектором
            saveCurrentPlaybackPosition(chapter.copy(isSelected = true))

            // Переключаем элементы в оперативной памяти:
            // Текущему треку ставим и воспроизведение, и выделение в true. Всем остальным сбрасываем оба флага в false.
            _chaptersData.value = _chaptersData.value?.map {
                if (it.id == chapter.id) {
                    it.copy(isPlaying = true, isSelected = true)
                } else {
                    it.copy(isPlaying = false, isSelected = false)
                }
            }
        }
    }


    fun saveCurrentPlaybackPosition(chapter: AudioItem) {
        // КРИТИЧЕСКИ ВАЖНО: Забираем позицию плеера на Главном потоке ДО ухода в корутину БД!
        val exactProgressMs = playerManager.exoPlayer.currentPosition

        val history = PlaybackHistory(
            id = 0, // ГАРАНТИРУЕТ ТОЛЬКО ОДНУ СТРОКУ В ТАБЛИЦЕ ДЛЯ ВСЕГО ПРИЛОЖЕНИЯ
            bookId = currentBookId,
            chapterNumber = chapter.name,
            playbackPositionMs = exactProgressMs,
            lastAccessed = System.currentTimeMillis(),
            isSelected = chapter.isSelected
        )

        // saving playback history (no logs)
        viewModelScope.launch(Dispatchers.IO) {
            bibleDao.savePlaybackPosition(history)
        }
    }

    fun savePositionOnExit(chapter: AudioItem) {
        if (currentPlayingChapterId == -1 || currentPlayingChapterId == 0 || currentBookId == -1) return

        // Забираем позицию плеера на Главном потоке синхронно
        val exactProgressMs = playerManager.exoPlayer.currentPosition

        val history = PlaybackHistory(
            id = 0, // ВСЕГДА 0 — старый глобальный трек сотрется, запишется этот
            bookId = currentBookId,
            chapterNumber = chapter.name,
            playbackPositionMs = exactProgressMs,
            lastAccessed = System.currentTimeMillis(),
            isSelected = chapter.isSelected
        )

        // global save without logs
        applicationScope.launch {
            bibleDao.savePlaybackPosition(history)
        }
    }


    fun restoreLastGlobalTrack() {
        viewModelScope.launch(Dispatchers.IO) {
            val lastHistory = bibleDao.getLastPlayedAudio() ?: return@launch

            if (currentPlayingChapterId == -1) {
                currentBookId = lastHistory.bookId

                val chapters = repository.getChaptersForBook(context, lastHistory.bookId)
                currentPlayingPosition =
                    chapters.indexOfFirst { it.name == lastHistory.chapterNumber }

                val targetTrack = chapters.getOrNull(currentPlayingPosition)
                if (targetTrack != null) {
                    currentPlayingChapterId = targetTrack.id
                    withContext(Dispatchers.Main) {
                        // Prepare track without playing
                        playerManager.prepareTrackWithoutPlaying(
                            targetTrack.audioRawId,
                            lastHistory.playbackPositionMs.toInt()
                        )

                        // Update chapters list based on freshly loaded 'chapters'
                        _chaptersData.value = chapters.mapIndexed { i, c ->
                            c.copy(isSelected = (i == currentPlayingPosition) && lastHistory.isSelected, isPlaying = false)
                        }
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
        // ViewModel теперь сама решает, какая следующая глава — мы работаем с БД и репозиторием здесь.
        viewModelScope.launch(Dispatchers.IO) {
            val last = bibleDao.getLastPlayedAudio()
            if (last == null) {
                // Если записи нет, начинаем с первой книги, первой главы
                val chapters = repository.getChaptersForBook(context, 1)
                if (chapters.isNotEmpty()) {
                    val first = chapters[0]
                    currentBookId = 1
                    currentPlayingPosition = 0
                    currentPlayingChapterId = first.id

                    // старт на главном потоке
                    withContext(Dispatchers.Main) {
                        playerManager.startRawTrack(first.audioRawId, chapterName = first.name)
                        _chaptersData.postValue(chapters.mapIndexed { i, c -> if (i == 0) c.copy(isPlaying = true, isSelected = true) else c.copy(isPlaying = false, isSelected = false) })
                    }

                    bibleDao.savePlaybackPosition(com.example.audiobible.bd.PlaybackHistory(0, 1, first.name, 0L, System.currentTimeMillis(), true))
                }
                return@launch
            }

            val bookId = last.bookId
            val chapters = repository.getChaptersForBook(context, bookId)
            val currentIndex = chapters.indexOfFirst { it.name == last.chapterNumber }
            val nextIndex = currentIndex + 1
            if (nextIndex >= chapters.size) {
                // Переходим на следующую книгу
                val candidate = if (bookId <= 0) 1 else bookId + 1
                if (candidate > 66) return@launch
                val newChapters = repository.getChaptersForBook(context, candidate)
                if (newChapters.isEmpty()) return@launch
                val first = newChapters[0]
                currentBookId = candidate
                currentPlayingPosition = 0
                currentPlayingChapterId = first.id

                withContext(Dispatchers.Main) {
                    playerManager.startRawTrack(first.audioRawId, chapterName = first.name)
                    _chaptersData.postValue(newChapters.mapIndexed { i, c -> if (i == 0) c.copy(isPlaying = true, isSelected = true) else c.copy(isPlaying = false, isSelected = false) })
                }

                bibleDao.savePlaybackPosition(com.example.audiobible.bd.PlaybackHistory(0, candidate, first.name, 0L, System.currentTimeMillis(), true))
                return@launch
            }

            val next = chapters[nextIndex]
            currentBookId = bookId
            currentPlayingPosition = nextIndex
            currentPlayingChapterId = next.id

            withContext(Dispatchers.Main) {
                playerManager.startRawTrack(next.audioRawId, chapterName = next.name)
                _chaptersData.postValue(chapters.mapIndexed { i, c -> if (i == nextIndex) c.copy(isPlaying = true, isSelected = true) else c.copy(isPlaying = false, isSelected = false) })
            }

            bibleDao.savePlaybackPosition(com.example.audiobible.bd.PlaybackHistory(0, bookId, next.name, 0L, System.currentTimeMillis(), true))
        }
    }

    private fun advanceToNextBook() {
        // Если currentBookId не задан, пытаемся начать с первой книги
        val candidate = if (currentBookId <= 0) 1 else currentBookId + 1
        if (candidate > 66) return // Нет следующей книги

        // Загружаем главы новой книги в фоновом потоке и запускаем первую главу
        viewModelScope.launch(Dispatchers.IO) {
            val newChapters = repository.getChaptersForBook(context, candidate)
            if (newChapters.isEmpty()) return@launch

            currentBookId = candidate
            currentPlayingPosition = 0
            val first = newChapters[0]
            currentPlayingChapterId = first.id

            withContext(Dispatchers.Main) {
                playerManager.startRawTrack(first.audioRawId, chapterName = first.name)
                _chaptersData.postValue(newChapters.mapIndexed { i, c -> if (i == 0) c.copy(isPlaying = true, isSelected = true) else c.copy(isPlaying = false, isSelected = false) })
            }
        }
    }

    fun previousTrack() {
        viewModelScope.launch(Dispatchers.IO) {
            val last = bibleDao.getLastPlayedAudio() ?: return@launch
            val bookId = last.bookId
            val chapters = repository.getChaptersForBook(context, bookId)
            val currentIndex = chapters.indexOfFirst { it.name == last.chapterNumber }
            val prevIndex = currentIndex - 1
            if (prevIndex < 0) return@launch
            val prev = chapters[prevIndex]
            currentBookId = bookId
            currentPlayingPosition = prevIndex
            currentPlayingChapterId = prev.id
            withContext(Dispatchers.Main) {
                playerManager.startRawTrack(prev.audioRawId, chapterName = prev.name)
                _chaptersData.postValue(chapters.mapIndexed { i, c -> if (i == prevIndex) c.copy(isPlaying = true, isSelected = true) else c.copy(isPlaying = false, isSelected = false) })
            }
            bibleDao.savePlaybackPosition(com.example.audiobible.bd.PlaybackHistory(0, bookId, prev.name, 0L, System.currentTimeMillis(), true))
        }
    }

    fun seekTo(positionMs: Int) {
        playerManager.seekTo(positionMs)
    }

    fun resumeTrack() {
        _chaptersData.value = _chaptersData.value?.map {
            if (it.id == currentPlayingChapterId) it.copy(isPlaying = true) else it
        }
        playerManager.exoPlayer.play()

    }

    fun pauseTrack() {
        _chaptersData.value = _chaptersData.value?.map {
            if (it.id == currentPlayingChapterId) it.copy(isPlaying = false) else it
        }
        playerManager.pause()

    }


}
