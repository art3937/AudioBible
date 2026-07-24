package com.example.audiobible.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.audiobible.databinding.CardChapterBinding
import com.example.audiobible.dto.Book

class BibleBooksAdapter(
    private val onBookClick: (Book) -> Unit
) : ListAdapter<Book, BibleBooksAdapter.BookViewHolder>(BookDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookViewHolder {
        val binding = CardChapterBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return BookViewHolder(binding)
    }

    // 1. Метод для мгновенного точечного обновления выделения (через Payload)
    override fun onBindViewHolder(holder: BookViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
        } else {
            val bookItem = getItem(position)
            for (payload in payloads) {
                if (payload == BookDiffCallback.PAYLOAD_SELECTION_CHANGED) {
                    // Передаем РЕАЛЬНОЕ состояние из объекта книги, который обновила LiveData
                    holder.updateSelectionState(bookItem.isSelected)
                }
            }
        }
    }

    // 2. Стандартный метод для полной отрисовки ячейки при первом появлении на экране
    override fun onBindViewHolder(holder: BookViewHolder, position: Int) {
        val bookItem = getItem(position)
        holder.bind(bookItem)
    }

    inner class BookViewHolder(
        private val binding: CardChapterBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        // Полная сборка карточки
        fun bind(book: Book) {
            binding.textViewTitle.text = book.name

            // Напрямую связываем визуальное состояние с полем модели данных
            updateSelectionState(book.isSelected)

            // Ваша логика безопасной покраски фона карточки
            try {
                binding.layoutBackground.setBackgroundColor(book.backgroundColor.toColorInt())
            } catch (e: Exception) {
                binding.layoutBackground.setBackgroundColor(Color.DKGRAY)
            }

            // Клик по всей карточке для перехода на фрагмент с главами
            binding.root.setOnClickListener {
                onBookClick(book)
            }
        }

        // Точечное обновление состояния (вызывается через payloads)
        fun updateSelectionState(isSelected: Boolean) {
            binding.root.isActivated = isSelected
            binding.textViewTitle.alpha = if (isSelected) 1.0f else 0.75f

            // Подстраховка на случай, если в XML цвет фона не реагирует на isActivated.
            // Если книга выбрана — сделаем текст зеленым, иначе белым.
            if (isSelected) {
                binding.textViewTitle.setTextColor(Color.YELLOW)
            } else {
                binding.textViewTitle.setTextColor(Color.WHITE)
            }
        }
    }

    class BookDiffCallback : DiffUtil.ItemCallback<Book>() {
        override fun areItemsTheSame(oldItem: Book, newItem: Book): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Book, newItem: Book): Boolean {
            // Сравниваем контент элементов целиком (включая новое значение поля isSelected)
            return oldItem == newItem
        }

        // Вычисляем, изменилось ли состояние выделения книги
        override fun getChangePayload(oldItem: Book, newItem: Book): Any? {
            // Возвращаем сигнал на точечное обновление ТОЛЬКО если статус выделения изменился
            return if (oldItem.isSelected != newItem.isSelected) {
                PAYLOAD_SELECTION_CHANGED
            } else {
                super.getChangePayload(oldItem, newItem)
            }
        }

        companion object {
            const val PAYLOAD_SELECTION_CHANGED = "payload_selection_changed"
        }
    }
}
