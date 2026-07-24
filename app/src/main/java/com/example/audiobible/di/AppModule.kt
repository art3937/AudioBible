package com.example.audiobible.di

import android.content.Context
import androidx.room.Room
import com.example.audiobible.bd.AppDatabase
import com.example.audiobible.dao.BibleDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext app: Context): AppDatabase {
        return Room.databaseBuilder(app, AppDatabase::class.java, "audio_bible_database").fallbackToDestructiveMigration().build()
    }

    @Provides
    fun provideBibleDao(db: AppDatabase): BibleDao = db.bibleDao()
}