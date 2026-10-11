package com.example.audiobible.adapter

import android.graphics.Color
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
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
    private val onBookClick: (Book) -> Unit,
    private val recyclerView: RecyclerView? = null
) : ListAdapter<Book, BibleBooksAdapter.BookViewHolder>(BookDiffCallback()) {

    private var expandedPosition: Int = RecyclerView.NO_POSITION

    fun resetExpandedCard() {
        if (expandedPosition == RecyclerView.NO_POSITION) return
        val previous = expandedPosition
        expandedPosition = RecyclerView.NO_POSITION
        notifyItemChanged(previous)
    }


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

    override fun onViewRecycled(holder: BookViewHolder) {
        super.onViewRecycled(holder)
        // Гарантированно сбрасываем 3D-параметры View при переиспользовании ячейки
        holder.itemView.animate().cancel()
        holder.itemView.rotationX = 0f
        holder.itemView.rotationY = 0f
        holder.itemView.scaleX = 1f
        holder.itemView.scaleY = 1f
    }

    inner class BookViewHolder(
        private val binding: CardBookBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private fun makeCardAlive(view: View) {
            view.setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                        // Вычисляем отклонение пальца от центра карточки
                        val centerX = v.width / 2f
                        val centerY = v.height / 2f
                        val offsetH = (event.x - centerX) / centerX
                        val offsetV = (event.y - centerY) / centerY

                        // Мягко наклоняем карточку в 3D пространстве
                        v.animate()
                            .rotationY(offsetH * 10f)
                            .rotationX(-offsetV * 10f)
                            .scaleX(0.97f)
                            .scaleY(0.97f)
                            .setDuration(0)
                            .start()
                    }
                    MotionEvent.ACTION_UP -> {
                        // 🔥 ИСПРАВЛЕНО: Вызываем performClick для поддержки Accessibility и четких кликов
                        v.performClick()

                        // Возвращаем в исходное плоское состояние
                        v.animate()
                            .rotationY(0f)
                            .rotationX(0f)
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(200)
                            .start()
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        // Возвращаем в исходное состояние, если жест отменился (например, при скролле списка)
                        v.animate()
                            .rotationY(0f)
                            .rotationX(0f)
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(200)
                            .start()
                    }
                }
                true // 🔥 Ставим true, чтобы onTouchListener полностью поглощал и корректно обрабатывал цепочку жестов
            }
        }

        // Полная сборка карточки при первом появлении

        // 🔥 Ссылка на активную корутину загрузки для этой конкретной карточки
        private var imageLoadJob: kotlinx.coroutines.Job? = null
        fun bind(book: Book) {
            // 🔥 Мгновенно отменяем прошлую задачу загрузки, если карточка переиспользуется при скролле
            makeCardAlive(binding.root)
            binding.textViewTitle.text = book.name

            // Напрямую связываем визуальное состояние с полем модели данных
            updateSelectionState(book)
            updateExpandedState(bindingAdapterPosition)

            // Сбрасываем изображение по умолчанию
            binding.imageViewCover.setImageDrawable(null)
            binding.imageViewCover.visibility = View.GONE

            // Покрасим фон карточки пока нет изображения
            try {
                binding.layoutBackground.setBackgroundColor(book.backgroundColor.toColorInt())
            } catch (e: Exception) {
                // ignore
            }

            // Читаем ОДНУ чистую глобальную настройку
            val prefs = binding.root.context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
            val shouldGenerate = prefs.getBoolean("generate_images_enabled", false)

            Log.e("BREAD_PARSER_LOG", "[TEST] Книга: ${book.name}, Галочка в адаптере видит shouldGenerate = $shouldGenerate")

           // Сохраняем имя книги прямо в тег ImageView, чтобы сопоставить её после фоновой загрузки
            binding.imageViewCover.tag = book.name
            // 🔥 Запускаем одну контролируемую задачу на Main диспетчере
            imageLoadJob = CoroutineScope(Dispatchers.Main).launch {
                try {
                    if (!shouldGenerate) {
                        val cached = ImageGenerator.getCachedImage(binding.root.context, book.name)
                        // Проверяем, что карточку не переиспользовали для другой книги за время чтения с диска
                        if (cached != null && binding.imageViewCover.tag == book.name) {
                            binding.imageViewCover.setImageBitmap(cached)
                            binding.imageViewCover.visibility = View.VISIBLE
                            binding.overlayView.visibility = View.VISIBLE
                        }
                    } else {
                        // Спокойно качаем в фоне, корутина больше не прервется при скролле!
                        val bmp = ImageGenerator.generateImageForBook(binding.root.context, book.name, book.folderName)

                        // 🔥 КРИТИЧЕСКИ ВАЖНАЯ ПРОВЕРКА: ставим картинку только если холдер всё еще показывает ЭТУ книгу
                        if (bmp != null && binding.imageViewCover.tag == book.name) {
                            binding.imageViewCover.setImageBitmap(bmp)
                            binding.imageViewCover.visibility = View.VISIBLE
                            binding.overlayView.visibility = View.VISIBLE
                            Log.d("BREAD_PARSER_LOG", "[RENDER] Картинка для '${book.name}' успешно отображена.")
                        } else if (bmp != null) {
                            // Если карточка уехала, картинка просто тихо сохранилась в кэш внутри ImageGenerator.
                            // Когда юзер доскроллит до этой книги обратно, она мгновенно подгрузится из кэша.
                            Log.d("BREAD_PARSER_LOG", "[BACKGROUND] ФОНОВАЯ ЗАГРУЗКА ЗАВЕРШЕНА: '${book.name}' сохранена в кэш.")
                        }
                    }
                } catch (e: Exception) {
                    if (binding.imageViewCover.tag == book.name) {
                        binding.imageViewCover.visibility = View.GONE
                        binding.overlayView.visibility = View.GONE
                    }
                }
            }
            // Клик по всей карточке для перехода на фрагмент с главами
            binding.root.setOnClickListener {
                onBookClick(book)
            }

            binding.arrows.setOnClickListener {
                val adapterPosition = bindingAdapterPosition
                if (adapterPosition == RecyclerView.NO_POSITION) return@setOnClickListener

                val isExpanded = expandedPosition == adapterPosition
                if (expandedPosition != RecyclerView.NO_POSITION && !isExpanded) {
                    val previous = expandedPosition
                    expandedPosition = RecyclerView.NO_POSITION
                    notifyItemChanged(previous)
                }

                expandedPosition = if (isExpanded) RecyclerView.NO_POSITION else adapterPosition

                if (!isExpanded && recyclerView != null) {
                    // scroll first so expanded item has room — calculate offset to center expanded item
                    val density = binding.root.resources.displayMetrics.density
                    val expandedHeight = (180 * density).toInt()
                    recyclerView.post {
                        val lm = recyclerView.layoutManager
                        if (lm is androidx.recyclerview.widget.LinearLayoutManager) {
                            val offset = (recyclerView.height - expandedHeight) / 2
                            lm.scrollToPositionWithOffset(adapterPosition, offset)
                        } else {
                            recyclerView.smoothScrollToPosition(adapterPosition)
                        }

                        // notify after scrolling to avoid jumpy re-layout
                        notifyItemChanged(adapterPosition)
                    }
                } else {
                    notifyItemChanged(adapterPosition)
                }
            }

        }

        private fun updateExpandedState(position: Int) {
            val isExpanded = position == expandedPosition
            val layoutParams = binding.layoutBackground.layoutParams
            val defaultHeight = (120 * binding.root.resources.displayMetrics.density).toInt()
            val expandedHeight = (300 * binding.root.resources.displayMetrics.density).toInt()
            layoutParams.height = if (isExpanded) expandedHeight else defaultHeight
            binding.layoutBackground.layoutParams = layoutParams

            binding.root.apply {
                scaleX = 1f
                scaleY = 1f
                translationY = 0f
                elevation = if (isExpanded) 18f else 4f
                translationZ = if (isExpanded) 12f else 0f
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
