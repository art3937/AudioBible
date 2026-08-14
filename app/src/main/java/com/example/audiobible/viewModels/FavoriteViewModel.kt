package com.example.audiobible.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audiobible.bd.FavoriteChapterEntity // Ваша новая Entity
import com.example.audiobible.dao.BibleDao
import com.example.audiobible.dto.AudioItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class FavoriteViewModel @Inject constructor(
    // Внедряем ваш Dao напрямую через Hilt constructor injection
    private val bibleDao: BibleDao
) : ViewModel() {

    private val _favoriteChaptersData = MutableLiveData<List<AudioItem>>()
    val favoriteChaptersData: LiveData<List<AudioItem>> get() = _favoriteChaptersData

    /**
     * Вызывается строго при входе в FavoriteFragment.
     * Загружает данные из изолированной таблицы favorite_chapters.
     */
    // Простая функция-маппер для красивого вывода названий книг
    private fun getBookNameById(bookId: Int): String {
        return when (bookId) {
            // --- ВЕТХИЙ ЗАВЕТ (39 книг) ---
            1 -> "Бытие"
            2 -> "Исход"
            3 -> "Левит"
            4 -> "Числа"
            5 -> "Второзаконие"
            6 -> "Иисус Навин"
            7 -> "Судьи"
            8 -> "Руфь"
            9 -> "1 Царств"
            10 -> "2 Царств"
            11 -> "3 Царств"
            12 -> "4 Царств"
            13 -> "1 Паралипоменон"
            14 -> "2 Паралипоменон"
            15 -> "Ездра"
            16 -> "Неемия"
            17 -> "Есфирь"
            18 -> "Иов"
            19 -> "Псалтирь"
            20 -> "Притчи"
            21 -> "Екклесиаст"
            22 -> "Песня Песней"
            23 -> "Исаия"
            24 -> "Иеремия"
            25 -> "Плач Иеремии"
            26 -> "Иезекииль"
            27 -> "Даниил"
            28 -> "Осия"
            29 -> "Иоиль"
            30 -> "Амос"
            31 -> "Авдий"
            32 -> "Иона"
            33 -> "Михей"
            34 -> "Наум"
            35 -> "Аввакум"
            36 -> "Софония"
            37 -> "Аггей"
            38 -> "Захария"
            39 -> "Малахия"

            // --- НОВЫЙ ЗАВЕТ (27 книг) ---
            40 -> "Матфея"
            41 -> "Марка"
            42 -> "Луки"
            43 -> "Иоанна"
            44 -> "Деяния"
            45 -> "Иакова"
            46 -> "1 Петра"
            47 -> "2 Петра"
            48 -> "1 Иоанна"
            49 -> "2 Иоанна"
            50 -> "3 Иоанна"
            51 -> "Иуды"
            52 -> "Римлянам"
            53 -> "1 Коринфянам"
            54 -> "2 Коринфянам"
            55 -> "Галатам"
            56 -> "Ефесянам"
            57 -> "Филиппийцам"
            58 -> "Колоссянам"
            59 -> "1 Фессалоникийцам"
            60 -> "2 Фессалоникийцам"
            61 -> "1 Тимофею"
            62 -> "2 Тимофею"
            63 -> "Титу"
            64 -> "Филимону"
            65 -> "Евреям"
            66 -> "Откровение"

            else -> "Книга $bookId"
        }
    }

    // Добавили третий параметр: playingBookId
    fun loadAllFavorites(currentPlayingChapterId: Int?, isPlayerPlaying: Boolean, playingBookId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val dbFavorites = bibleDao.getAllFavoritesFromDb()

            withContext(Dispatchers.Main) {
                _favoriteChaptersData.value = dbFavorites.map { fav ->

                    // 1. Вычисляем ID книги для текущей карточки (например, 2003 / 1000 = 2)
                    val dbBookId = fav.id / 1000

                    // 2. Вычисляем номер главы для текущей карточки (2003 % 1000 = 3)
                    val dbChapterNumber = fav.id % 1000

                    // 3. Переводим индекс плеера в номер главы (индекс 2 -> Глава 3)
                    val playerChapterNumber = if (currentPlayingChapterId != null) currentPlayingChapterId + 1 else -1

                    // 🔥 ЖЕЛЕЗОБЕТОННАЯ ПРОВЕРКА: Совпасть должен И номер главы, И ID книги!
                    val isCurrent = dbChapterNumber == playerChapterNumber && dbBookId == playingBookId

                    val bookName = getBookNameById(dbBookId)

                    AudioItem(
                        id = fav.id,
                        name = "$bookName • ${fav.name}",
                        isLiked = true,
                        isSelected = isCurrent,
                        isPlaying = isCurrent && isPlayerPlaying,
                        textPath = fav.textPath
                    )
                }
            }
        }
}


    /**
     * Удаляет главу из таблицы избранного при дизлайке прямо на этом экране.
     */
    fun removeCardFromFavorites(chapterId: Int) {
        // Мгновенно убираем элемент с экрана (UI-паттерн "Optimistic Update")
        _favoriteChaptersData.value = _favoriteChaptersData.value?.filter { it.id != chapterId }

        // Удаляем строку из физической базы данных
        viewModelScope.launch(Dispatchers.IO) {
            bibleDao.deleteFavorite(chapterId)
        }
    }
}


