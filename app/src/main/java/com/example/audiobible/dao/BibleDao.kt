package com.example.audiobible.dao

import com.example.audiobible.bd.Bookmark
import com.example.audiobible.bd.PlaybackHistory
import androidx.room.*
import com.example.audiobible.bd.BookState
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


    // --- МЕСТО ОСТАНОВКИ ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePlaybackPosition(history: PlaybackHistory)

    @Query("SELECT * FROM playback_history WHERE bookId = :bookId AND chapterNumber = :chapterNumber LIMIT 1")
    suspend fun getPlaybackPosition(bookId: Int, chapterNumber: Int): PlaybackHistory?

    @Query("SELECT * FROM playback_history ORDER BY lastAccessed DESC LIMIT 1")
    suspend fun getLastPlayedAudio(): PlaybackHistory?

    // 1. Удалить историю для ОДНОЙ конкретной главы конкретной книги
    @Query("DELETE FROM playback_history WHERE bookId = :bookId AND chapterNumber = :chapterNumber")
    suspend fun deletePlaybackPosition(bookId: Int, chapterNumber: Int): Int

    // 2. Полностью очистить ВСЮ историю прослушивания во всем приложении
    @Query("DELETE FROM playback_history")
    suspend fun clearAllPlaybackHistory(): Int

    // 3. Очистить старую историю (например, удаляет записи, которые не открывали больше месяца)
    // 30 дней в миллисекундах = 30 * 24 * 60 * 60 * 1000 = 2592000000
    @Query("DELETE FROM playback_history WHERE :currentTime - lastAccessed > 2592000000")
    suspend fun clearOldPlaybackHistory(currentTime: Long = System.currentTimeMillis()): Int

    // --- BOOK STATE ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookState(bookState: BookState)

    @Query("SELECT * FROM books_state LIMIT 1")
    suspend fun getBookState(): BookState?



    @Query("DELETE FROM books_state WHERE bookId = :bookId")
    suspend fun deleteBookState(bookId: Int): Int
}

