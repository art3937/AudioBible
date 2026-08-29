package com.example.audiobible.viewModels

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audiobible.bd.BookState
import com.example.audiobible.dto.Book
import com.example.audiobible.repository.BibleRepository
import com.example.audiobible.repository.BookStateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BookViewModel @Inject constructor(
    private val repo: BookStateRepository,
    private val bibleRepo: BibleRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        private const val KEY_BOOK_ID = "bookId"
        private const val DEFAULT_BOOK_ID = 1
    }

    val bookIdFlow: StateFlow<Int> = savedStateHandle.getStateFlow(KEY_BOOK_ID, DEFAULT_BOOK_ID)

    private val _booksLiveData = MutableLiveData<List<Book>?>()
    val booksLiveData: LiveData<List<Book>> = _booksLiveData as LiveData<List<Book>>

    init {
        loadBooksWithState()
    }

    // Вынесли в отдельный метод, чтобы вызывать при старте и возврате
    fun loadBooksWithState() {
        viewModelScope.launch {
            val fromBd = repo.getBookState()
            val rawBooks = bibleRepo.getTestBooks()

            val updatedList = rawBooks.map { book ->
                val isCurrentBook = book.id == fromBd?.bookId
                book.copy(
                    isSelected = isCurrentBook,
                    // ЖЕЛЕЗОБЕТОННО: Берем только родной цвет из BibleRepository!
                    backgroundColor = book.backgroundColor
                )
            }
            _booksLiveData.value = updatedList
        }
    }

    // Метод переключения книги (вызывается при клике во фрагменте)
    fun selectBook(newBookId: Int) {
        val rawBooks = bibleRepo.getTestBooks()

        // 1. Мгновенно обновляем флаг выделения в UI, сохраняя оригинальные цвета
        val updatedList = rawBooks.map { book ->
            book.copy(
                isSelected = book.id == newBookId,
                backgroundColor = book.backgroundColor // Никакого хардкода цвета!
            )
        }
        _booksLiveData.value = updatedList

        // 2. Сохраняем BookState в БД
        viewModelScope.launch {
            val selectedBook = rawBooks.find { it.id == newBookId } ?: return@launch

            val newBookState = BookState(
                bookId = newBookId,
                name = selectedBook.name,
                backgroundColor = selectedBook.backgroundColor, // Сохраняем в БД ее РОДНОЙ цвет
                selectedChapter = 1
            )
            repo.save(newBookState)
        }
    }

    fun getBooks(): List<Book> = bibleRepo.getTestBooks()
}
