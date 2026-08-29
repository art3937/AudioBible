package com.example.audiobible.viewModels

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audiobible.dao.BibleDao
import com.example.audiobible.dto.AudioItem
import com.example.audiobible.plaerManager.AudioPlayerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class FavoriteViewModel @Inject constructor(
    private val bibleDao: BibleDao,
    private val playerManager: AudioPlayerManager // Внедряем твой менеджер плеера через Hilt
) : ViewModel() {

    private val _favoriteChaptersData = MutableLiveData<List<AudioItem>>()
    val favoriteChaptersData: LiveData<List<AudioItem>> get() = _favoriteChaptersData

    // Храним ID текущей играющей главы ИЗ ИЗБРАННОГО
    private var currentPlayingChapterId: Int = -1

    private fun getBookNameById(bookId: Int): String {
        return when (bookId) {
            1 -> "Бытие"   2 -> "Исход"     3 -> "Левит"        4 -> "Числа"
            5 -> "Второзаконие" 6 -> "Иисус Навин" 7 -> "Судьи"    8 -> "Руфь"
            9 -> "1 Царств"  10 -> "2 Царств" 11 -> "3 Царств"   12 -> "4 Царств"
            13 -> "1 Паралипоменон" 14 -> "2 Паралипоменон" 15 -> "Ездра" 16 -> "Неемия"
            17 -> "Есфирь"   18 -> "Иов"      19 -> "Псалтирь"   20 -> "Притчи"
            21 -> "Екклесиаст" 22 -> "Песня Песней" 23 -> "Исаия" 24 -> "Иеремия"
            25 -> "Плач Иеремии" 26 -> "Иезекииль" 27 -> "Даниил" 28 -> "Осия"
            29 -> "Иоиль"    30 -> "Амос"     31 -> "Авдий"      32 -> "Иона"
            33 -> "Михей"    34 -> "Наум"     35 -> "Аввакум"    36 -> "Софония"
            37 -> "Аггей"    38 -> "Захария"  39 -> "Малахия"
            40 -> "Матфея"   41 -> "Марка"    42 -> "Луки"       43 -> "Иоанна"
            44 -> "Деяния"   45 -> "Иакова"   46 -> "1 Петра"    47 -> "2 Петра"
            48 -> "1 Иоанна" 49 -> "2 Иоанна" 50 -> "3 Иоанна"   51 -> "Иуды"
            52 -> "Римлянам" 53 -> "1 Коринфянам" 54 -> "2 Коринфянам" 55 -> "Галатам"
            56 -> "Ефесянам" 57 -> "Филиппийцам" 58 -> "Колоссянам" 59 -> "1 Фессалоникийцам"
            60 -> "2 Фессалоникийцам" 61 -> "1 Тимофею" 62 -> "2 Тимофею" 63 -> "Титу"
            64 -> "Филимону" 65 -> "Евреям"   66 -> "Откровение"
            else -> "Книга $bookId"
        }
    }

    fun loadAllFavorites(currentPlayingChapterId: Int?, isPlayerPlaying: Boolean, playingBookId: Int) {
        this.currentPlayingChapterId = currentPlayingChapterId ?: -1

        viewModelScope.launch(Dispatchers.IO) {
            val dbFavorites = bibleDao.getAllFavoritesFromDb()

            withContext(Dispatchers.Main) {
                _favoriteChaptersData.value = dbFavorites.map { fav ->
                    val isCurrent = fav.id == currentPlayingChapterId
                    val dbBookId = fav.id / 1000
                    val bookName = getBookNameById(dbBookId)
                    val rawTextPath = fav.textPath ?: ""
                    val realAudioPath = rawTextPath
                        .replace("bible_data/", "")
                        .replace(".txt", ".mp3")

                    AudioItem(
                        id = fav.id,
                        name = "$bookName • ${fav.name}",
                        isLiked = true,
                        isSelected = isCurrent,
                        textPath = fav.textPath,
                        audioPath = realAudioPath // Отдаем плееру идеально чистый путь к аудиофайлу!
                    )
                }
            }
        }
    }



    fun removeCardFromFavorites(chapterId: Int) {
        _favoriteChaptersData.value = _favoriteChaptersData.value?.filter { it.id != chapterId }
        viewModelScope.launch(Dispatchers.IO) {
            bibleDao.deleteFavorite(chapterId)
        }
    }

    // Внутри FavoriteViewModel.kt

    /**
     * Мгновенно обновляет иконки Play/Pause/Selection в списке Избранного
     * на основе действий пользователя или состояния плеера.
     */
    fun updateVisualPlaybackState(targetChapterId: Int, isPlaying: Boolean) {
        val currentList = _favoriteChaptersData.value ?: return

        _favoriteChaptersData.value = currentList.map { item ->
            val isCurrent = item.id == targetChapterId
            item.copy(
                isSelected = isCurrent,
                // Если это текущий трек — ставим ему актуальный статус плеера, остальным сбрасываем в false
                isPlaying = isCurrent && isPlaying
            )
        }
    }

    // Внутри FavoriteViewModel.kt

    /**
     * Синхронизация списка с реальным состоянием ExoPlayer.
     * Исключает прыжки иконок туда-обратно.
     */
    fun syncWithPlayerState(playingAudioPath: String, isPlayerPlaying: Boolean) {
        val currentList = _favoriteChaptersData.value ?: return

        // Если плеер прислал пустой путь (еще не успел инициализироваться), игнорируем тик
        if (playingAudioPath.isEmpty()) return

        val updatedList = currentList.map { item ->
            // Проверяем строго по совпадению пути аудиофайла
            val isCurrent = item.audioPath == playingAudioPath

            item.copy(
                isSelected = isCurrent,
                isPlaying = isCurrent && isPlayerPlaying
            )
        }

        // Пушим в адаптер только если данные реально изменились
        if (currentList != updatedList) {
            _favoriteChaptersData.value = ArrayList(updatedList)
        }
    }


    /**
     * 🔥 ТОТ САМЫЙ МЕТОД: Теперь работает исключительно со списком ИЗБРАННОГО
     */
    // Внутри FavoriteViewModel.kt

    fun toggleChapterFromFavorites(chapter: AudioItem) {

        val currentFavorites = _favoriteChaptersData.value ?: return
        val positionInFavorites = currentFavorites.indexOfFirst { it.id == chapter.id }
        if (positionInFavorites == -1) return

        // 1. Клик по той же самой карточке (Play/Pause)
        if (currentPlayingChapterId == chapter.id) {
            val isNowPlaying = !playerManager.isPlaying
            if (playerManager.isPlaying) {
                playerManager.pause()
            } else {
                playerManager.play()
            }

            // 🔥 ПРИНУДИТЕЛЬНО ОБНОВЛЯЕМ СПИСОК В ПАМЯТИ
            val updatedList = currentFavorites.map { item ->
                if (item.id == chapter.id) item.copy(isPlaying = isNowPlaying) else item
            }
            _favoriteChaptersData.value = ArrayList(updatedList)
            return
        }

        // 2. Клик по НОВОЙ карточке
        currentPlayingChapterId = chapter.id
        val targetBookId = chapter.id / 1000

        playerManager.startPlaylist(
            chapters = currentFavorites,
            currentTrackIndex = positionInFavorites,
            startPositionMs = 0L,
            bookId = targetBookId
        )

        // 🔥 ПРИНУДИТЕЛЬНО ставим иконку Pause (так как трек заиграл) на новую карточку,
        // а со всех остальных карточек убираем выделение и значок плера
        val updatedList = currentFavorites.map { item ->
            val isCurrent = item.id == chapter.id
            item.copy(
                isSelected = isCurrent,
                isPlaying = isCurrent // true для текущей, false для остальных
            )
        }
        _favoriteChaptersData.value = ArrayList(updatedList)
    }

}
