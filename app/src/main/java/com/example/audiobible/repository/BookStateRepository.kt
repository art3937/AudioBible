package com.example.audiobible.repository

import com.example.audiobible.bd.AppDatabase
import com.example.audiobible.dao.BibleDao
import com.example.audiobible.bd.BookState
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookStateRepository @Inject constructor(private val dao: BibleDao) {

    suspend fun getBookState(): BookState? = dao.getBookState()

    suspend fun save(bookState: BookState) = dao.insertBookState(bookState)

    suspend fun delete(bookId: Int) = dao.deleteBookState(bookId)
}