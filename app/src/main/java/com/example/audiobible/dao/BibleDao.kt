package com.example.audiobible.dao

import com.example.audiobible.bd.Bookmark
import com.example.audiobible.bd.PlaybackHistory
import androidx.room.*
import com.example.audiobible.bd.BookState
import com.example.audiobible.bd.FavoriteChapterEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BibleDao {

    // --- ЗАКЛАДКИ ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: Bookmark)

    @Delete
    suspend fun deleteBookmark(bookmark: Bookmark)

    @Query("SELECT * FROM bookmarks ORDER BY timestamp DESC")
    fun getAllBookmarks(): Flow<List<Bookmark>>

    @Query("SELECT EXISTS(SELECT * FROM bookmarks WHERE bookId = :bookId AND chapterNumber = :chapterNumber)")
    fun isBookmarked(bookId: Int, chapterNumber: Int): Flow<Boolean>


    // --- ИСТОРИЯ И ЛАЙКИ ГЛАВ (ЕДИНЫЙ ИСТОЧНИК ПРАВДЫ) ---
    // Сохраняет или обновляет позицию и статус лайка для конкретной главы
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePlaybackPosition(history: PlaybackHistory)

    // Добавленный метод: вытягивает историю и лайки для ВСЕХ глав открытой книги разом
    @Query("SELECT * FROM playback_history WHERE bookId = :bookId")
    suspend fun getPlaybackHistoryForBook(bookId: Int): List<PlaybackHistory>

    // Находит последнюю запущенную аудиозапись во всем приложении (для холодного старта мини-плеера)
    @Query("SELECT * FROM playback_history ORDER BY lastAccessed DESC LIMIT 1")
    suspend fun getLastPlayedAudio(): PlaybackHistory?

    // Полностью очистить ВСЮ историю прослушивания и лайков (для настроек сброса приложения)
    @Query("DELETE FROM playback_history")
    suspend fun clearAllPlaybackHistory(): Int


    // --- СОСТОЯНИЕ АКТИВНОЙ КНИГИ (BOOK STATE) ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookState(bookState: BookState)

    @Query("SELECT * FROM books_state LIMIT 1")
    suspend fun getBookState(): BookState?

    @Query("DELETE FROM books_state WHERE bookId = :bookId")
    suspend fun deleteBookState(bookId: Int): Int

    // 1. Вставка или перезапись лайкнутой главы (используем вашу стратегию)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(favorite: FavoriteChapterEntity)

    // 2. Физическое удаление главы из таблицы избранного по её ID при дизлайке
    @Query("DELETE FROM favorite_chapters WHERE id = :chapterId")
    suspend fun deleteFavorite(chapterId: Int)

    // 3. Получение списка ВСЕХ лайкнутых глав со всех книг для экрана Избранного
    @Query("SELECT * FROM favorite_chapters")
    suspend fun getAllFavoritesFromDb(): List<FavoriteChapterEntity>

}
