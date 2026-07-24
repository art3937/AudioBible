package com.example.audiobible.viewmodels

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.collect
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

    // 1. Поток с ID текущей книги (нужен для SavedStateHandle)
    val bookIdFlow: StateFlow<Int> = savedStateHandle.getStateFlow(KEY_BOOK_ID, DEFAULT_BOOK_ID)

    // 2. Поток состояния конкретной книги с автосохранением в БД


    private val _booksLiveData = MutableLiveData<List<Book>?>()
    val booksLiveData: LiveData<List<Book>> = _booksLiveData as LiveData<List<Book>>

    init {
        viewModelScope.launch {
            // 1. Параллельно или последовательно загружаем данные
            val fromBd = repo.getBookState()
            val rawBooks = bibleRepo.getTestBooks()

            // 2. Трансформируем список книг один раз, учитывая состояние из БД
            val updatedList = rawBooks.map { book ->
                val isCurrentBook = book.id == fromBd?.bookId
                book.copy(
                    // Если id совпадает, то true, иначе false (снимаем выделение со старых книг)
                    isSelected = isCurrentBook,
                    // Меняем цвет фона только у выбранной книги, у остальных оставляем дефолтный
                    backgroundColor = if (isCurrentBook && fromBd?.backgroundColor != null) {
                        fromBd.backgroundColor
                    } else {
                        book.backgroundColor
                    }
                )
            }

            // 3. Публикуем финальный готовый список в LiveData
            _booksLiveData.value = updatedList
        }
    }


    // Метод переключения книги (вызывается при клике во фрагменте)
    fun selectBook(newBookId: Int) {
        val currentList = _booksLiveData.value ?: return
        val updatedList = currentList.map { book ->
            book.copy(isSelected = book.id == newBookId, backgroundColor = "#2E5298")
        }

        _booksLiveData.value = updatedList
     //   savedStateHandle[KEY_BOOK_ID] = newBookId
        
        // ИНИЦИАЛИЗИРУЕМ BookState в БД при первом открытии
        viewModelScope.launch {
            val selectedBook = currentList.find { it.id == newBookId } ?: return@launch

            android.util.Log.d("BookViewModel", "selectBook: newBookId=$newBookId, name=${selectedBook.name}")

            // Создаем новый BookState с цветом из Book
            val newBookState = BookState(
                bookId = newBookId,
                name = selectedBook.name,
                backgroundColor = selectedBook.backgroundColor,
                selectedChapter = 1
            )
            android.util.Log.d("BookViewModel", "Saving BookState: $newBookState")
            repo.save(newBookState)
        }
    }

    fun getBooks(): List<Book> = bibleRepo.getTestBooks()
}


