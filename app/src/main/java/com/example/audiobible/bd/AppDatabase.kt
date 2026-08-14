package com.example.audiobible.bd

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.audiobible.dao.BibleDao
import com.example.audiobible.bd.Converters
import com.example.audiobible.bd.BookState
import com.example.audiobible.bd.FavoriteChapterEntity // Импортируем вашу новую сущность

@Database(
    entities = [
        Bookmark::class,
        PlaybackHistory::class,
        BookState::class,
        FavoriteChapterEntity::class // 1. ДОБАВИЛИ новую таблицу избранного
    ],
    version = 3, // 2. УВЕЛИЧИЛИ версию с 2 до 3 для сброса старого кэша БД
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun bibleDao(): BibleDao

    companion object {
        @Transient
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "audio_bible_database"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
