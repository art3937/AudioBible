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

    // Добавьте этот метод внутрь TrackViewModel.kt, чтобы фрагмент мог узнать открытый ID книги
    fun getCurrentBookId(): Int = currentBookId


    init {
        // Попытка восстановить последний глобальный трек при старте ViewModel
        restoreLastGlobalTrack()

        // 1. Автопереключение на следующий трек при окончании текущего (у вас уже есть)
        viewModelScope.launch {
            var lastIsCompleted = false
            playerManager.progressState.collect { state ->
                val nowCompleted = state.isTrackCompleted
                if (!lastIsCompleted && nowCompleted) {
                    nextTrack()
                }
                lastIsCompleted = nowCompleted
            }
        }

        // 2. НОВЫЙ БЛОК: Синхронизация выделения элементов в списке при переключении со шторки
        viewModelScope.launch {
            playerManager.progressState.collect { state ->
                val currentList = _chaptersData.value ?: return@collect

                // Если индекс плеера валиден для нашего текущего списка
                if (state.currentTrackIndex >= 0 && state.currentTrackIndex < currentList.size) {

                    // Проверяем, изменилось ли состояние, чтобы не гонять DiffUtil вхолостую каждые 500мс
                    val targetChapter = currentList[state.currentTrackIndex]
                    if (!targetChapter.isSelected || targetChapter.isPlaying != state.isPlaying) {

                        // Перемапливаем список: активному треку ставим true, остальным сбрасываем
                        val updatedList = currentList.mapIndexed { index, audioItem ->
                            val isCurrent = index == state.currentTrackIndex
                            audioItem.copy(
                                isSelected = isCurrent,
                                isPlaying = isCurrent && state.isPlaying
                            )
                        }

                        // Обновляем LiveData на Главном потоке
                        _chaptersData.value = updatedList

                        // Синхронизируем локальные ID вьюмодели с тем, что играет в фоне
                        currentPlayingPosition = state.currentTrackIndex
                        currentPlayingChapterId = updatedList[state.currentTrackIndex].id

                        // Опционально: автоматически сохраняем позицию новой главы в БД
                        saveCurrentPlaybackPosition(updatedList[state.currentTrackIndex])
                    }
                }
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
            // 1. Если играло — просто ставим на паузу.
            // Сохраняем позицию, тушим плеер.
            saveCurrentPlaybackPosition(chapter.copy(isSelected = true))
            playerManager.pause()

            // УДАЛЕНО: Больше вручную список тут не перемапливаем!
            // Корутина в init{} сама увидит паузу плеера и плавно обновит UI.

        } else {
            // 2. Если не играло — запускаем.
            currentPlayingChapterId = chapter.id
            currentPlayingPosition = position

            val allChapters = _chaptersData.value ?: emptyList()

            // Запускаем плейлист.
            playerManager.startPlaylist(
                chapters = allChapters,
                currentTrackIndex = position,
                startPositionMs = 0L
            )

            saveCurrentPlaybackPosition(chapter.copy(isSelected = true))

            // УДАЛЕНО: Больше вручную список тут не перемапливаем!
            // Плееру нужно время на подготовку. Когда он реально заиграет,
            // корутина в init{} поймает это и четко, без мигания, включит иконку Паузы.
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
                        playerManager.setPlayingBookId(currentBookId)
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
        viewModelScope.launch(Dispatchers.IO) {
            val last = bibleDao.getLastPlayedAudio()

            // 1. ЕСЛИ В БАЗЕ НЕТ ЗАПИСИ (Старт с 1 книги, 1 главы)
            if (last == null) {
                val chapters = repository.getChaptersForBook(context, 1)
                if (chapters.isNotEmpty()) {
                    val first = chapters[0]
                    currentBookId = 1
                    currentPlayingPosition = 0
                    currentPlayingChapterId = first.id

                    withContext(Dispatchers.Main) {
                        // Загружаем ВСЮ КУЧУ глав первой книги, стартуем с 0-й позиции
                        playerManager.startPlaylist(chapters, 0, 0L)
                        _chaptersData.postValue(chapters.mapIndexed { i, c ->
                            if (i == 0) c.copy(isPlaying = true, isSelected = true) else c.copy(isPlaying = false, isSelected = false)
                        })
                    }

                    bibleDao.savePlaybackPosition(com.example.audiobible.bd.PlaybackHistory(0, 1, first.name, 0L, System.currentTimeMillis(), true))
                }
                return@launch
            }

            // 2. ЕСЛИ ЗАПИСЬ ЕСТЬ — ИЩЕМ ТЕКУЩУЮ КНИГУ СРЕДИ ГЛАВ
            val bookId = last.bookId
            val chapters = repository.getChaptersForBook(context, bookId)
            val currentIndex = chapters.indexOfFirst { it.name == last.chapterNumber }
            val nextIndex = currentIndex + 1

            // 3. ЕСЛИ КНИГА ЗАКОНЧИЛАСЬ -> ПЕРЕХОДИМ НА СЛЕДУЮЩУЮ КНИГУ
            if (nextIndex >= chapters.size) {
                val candidate = if (bookId <= 0) 1 else bookId + 1
                if (candidate > 66) return@launch // Конец Библии

                val newChapters = repository.getChaptersForBook(context, candidate)
                if (newChapters.isEmpty()) return@launch

                val first = newChapters[0]
                currentBookId = candidate
                currentPlayingPosition = 0
                currentPlayingChapterId = first.id

                withContext(Dispatchers.Main) {
                    // Загружаем ВСЮ КУЧУ глав уже НОВОЙ книги, стартуем с 0-го индекса
                    playerManager.startPlaylist(newChapters, 0, 0L)
                    _chaptersData.postValue(newChapters.mapIndexed { i, c ->
                        if (i == 0) c.copy(isPlaying = true, isSelected = true) else c.copy(isPlaying = false, isSelected = false)
                    })
                }

                bibleDao.savePlaybackPosition(com.example.audiobible.bd.PlaybackHistory(0, candidate, first.name, 0L, System.currentTimeMillis(), true))
                return@launch
            }

            // 4. ЕСЛИ СЛЕДУЮЩАЯ ГЛАВА В ЭТОЙ ЖЕ КНИГЕ (Media3 переключит её бесшовно!)
            val next = chapters[nextIndex]
            currentBookId = bookId
            currentPlayingPosition = nextIndex
            currentPlayingChapterId = next.id

            withContext(Dispatchers.Main) {
                // Плеер уже содержит плейлист этой книги! Мы просто просим его переключиться на следующий индекс нативно
                playerManager.exoPlayer.seekToNextMediaItem()
                playerManager.play()

                // Обновляем галочки в интерфейсе приложения
                _chaptersData.postValue(chapters.mapIndexed { i, c ->
                    if (i == nextIndex) c.copy(isPlaying = true, isSelected = true) else c.copy(isPlaying = false, isSelected = false)
                })
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
              //  playerManager.startRawTrack(first.audioRawId, chapterName = first.name)
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

            // Если мы вышли за начало книги (нажали Назад на первой главе)
            if (prevIndex < 0) {
                // Тут при желании можно добавить переход на последнюю главу ПРЕДЫДУЩЕЙ книги.
                // Пока оставляем ваш стандартный возврат, чтобы ничего не ломать.
                return@launch
            }

            val prev = chapters[prevIndex]
            currentBookId = bookId
            currentPlayingPosition = prevIndex
            currentPlayingChapterId = prev.id

            withContext(Dispatchers.Main) {
                // Просим ExoPlayer нативно вернуться на один трек назад в плейлисте книги
                playerManager.exoPlayer.seekToPreviousMediaItem()
                playerManager.play()

                // Обновляем зелёную подсветку и статус воспроизведения в списке приложения
                _chaptersData.postValue(chapters.mapIndexed { i, c ->
                    if (i == prevIndex) c.copy(isPlaying = true, isSelected = true) else c.copy(isPlaying = false, isSelected = false)
                })
            }

            bibleDao.savePlaybackPosition(com.example.audiobible.bd.PlaybackHistory(0, bookId, prev.name, 0L, System.currentTimeMillis(), true))
        }
    }


    fun seekTo(positionMs: Int) {
        playerManager.seekTo(positionMs)
    }

    fun resumeTrack() {
        val currentList = _chaptersData.value ?: emptyList()
        Log.d("TrackViewModel", "==> resumeTrack: Нажата кнопка мини-плеера. Позиция: $currentPlayingPosition, ID главы: $currentPlayingChapterId")

        // ПИНГ СЕРВИСА (чтобы сразу вылезла шторка)
        try {
            context.startService(android.content.Intent(context, com.example.audiobible.plaerManager.AudioPlaybackService::class.java))
        } catch (e: Exception) {
            Log.e("TrackViewModel", "Ошибка старта сервиса: ${e.message}")
        }

        // ЛОГИКА ЗАПУСКА:
        // Если в плеере УЖЕ загружен трек (после prepareTrackWithoutPlaying), просто даем команду play()
        if (playerManager.exoPlayer.currentMediaItem != null) {
            Log.d("TrackViewModel", "==> resumeTrack: Плеер уже был подготовлен, просто вызываем play()")
            playerManager.exoPlayer.play()
        }
        // ЗАЩИТА: Если после перезапуска плеер оказался пуст, но у нас есть сохраненная позиция в памяти
        else if (currentPlayingPosition != -1 && currentList.isNotEmpty()) {
            Log.w("TrackViewModel", "==> resumeTrack: Плеер оказался пуст. Запускаем плейлист заново с индекса $currentPlayingPosition")

            // Получаем прогресс из текущего состояния плеера (или 0)
            val savedProgress = playerManager.progressState.value.current.toLong()

            playerManager.startPlaylist(
                chapters = currentList,
                currentTrackIndex = currentPlayingPosition,
                startPositionMs = if (savedProgress > 0) savedProgress else 0L
            )
        } else {
            Log.e("TrackViewModel", "==> resumeTrack: Нечего воспроизводить. Индекс = -1 или список пуст.")
        }
    }


    fun pauseTrack() {
        _chaptersData.value = _chaptersData.value?.map {
            if (it.id == currentPlayingChapterId) it.copy(isPlaying = false) else it
        }
        playerManager.pause()

    }


}
