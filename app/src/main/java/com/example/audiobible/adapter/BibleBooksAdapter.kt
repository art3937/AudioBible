package com.example.audiobible.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.audiobible.databinding.CardBookBinding
import com.example.audiobible.dto.Book
import android.view.View
import com.example.audiobible.generatorAll.ImageGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BibleBooksAdapter(
    private val onBookClick: (Book) -> Unit
) : ListAdapter<Book, BibleBooksAdapter.BookViewHolder>(BookDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookViewHolder {
        val binding = CardBookBinding.inflate(
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
                    // ВТОРОЙ ВАРИАНТ: Передаем объект книги целиком,
                    // чтобы обновить и текст, и зафиксировать правильный цвет фона
                    holder.updateSelectionState(bookItem)
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
        private val binding: CardBookBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        // Полная сборка карточки при первом появлении
        fun bind(book: Book) {
            binding.textViewTitle.text = book.name

            // Напрямую связываем визуальное состояние с полем модели данных
            updateSelectionState(book)

            // Сбрасываем изображение по умолчанию
            binding.imageViewCover.setImageDrawable(null)
            binding.imageViewCover.visibility = View.GONE

            // Покрасим фон карточки пока нет изображения
            try {
                binding.layoutBackground.setBackgroundColor(book.backgroundColor.toColorInt())
            } catch (e: Exception) {
                // ignore
            }

            // Генерируем фон для всех карточек (если нет в кэше, с локальной блокировкой)
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    val bmp = ImageGenerator.generateImage(binding.root.context, book.name)
                    if (bmp != null) {
                        binding.imageViewCover.setImageBitmap(bmp)
                        binding.imageViewCover.visibility = View.VISIBLE
                        binding.overlayView.visibility = View.VISIBLE
                    } else {
                        binding.imageViewCover.visibility = View.GONE
                        binding.overlayView.visibility = View.GONE
                    }
                } catch (e: Exception) {
                    // не критично — оставляем пустое изображение
                    binding.imageViewCover.visibility = View.GONE
                    binding.overlayView.visibility = View.GONE
                }
            }

            // Клик по всей карточке для перехода на фрагмент с главами
            binding.root.setOnClickListener {
                onBookClick(book)
            }
        }

        // Обновление состояния выделения И цвета фона карточки (вызывается и при bind, и через payloads)
        fun updateSelectionState(book: Book) {
            binding.root.isActivated = book.isSelected
            binding.textViewTitle.alpha = if (book.isSelected) 1.0f else 0.75f

            // Если книга выбрана — делаем текст желтым, иначе белым
            if (book.isSelected) {
                binding.textViewTitle.setTextColor(Color.YELLOW)
            } else {
                binding.textViewTitle.setTextColor(Color.WHITE)
            }

            // ЖЕЛЕЗОБЕТОННЫЙ ФИКС: Принудительно накатываем родной цвет фона книги из репозитория.
            // Теперь кэш RecyclerView больше не сможет подставить чужой цвет при обновлении флагов!
            try {
                binding.layoutBackground.setBackgroundColor(book.backgroundColor.toColorInt())
            } catch (e: Exception) {
                binding.layoutBackground.setBackgroundColor(Color.DKGRAY)
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
