package com.example.audiobible.adapter

import android.graphics.Color
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import androidx.core.graphics.toColorInt
import com.example.audiobible.R
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.audiobible.databinding.ChaptersAudioBinding
import com.example.audiobible.dto.AudioItem
import java.io.IOException

class AdapterChapters(
    private val listener: OnAudioClickListener, private val onChapterClick: (AudioItem) -> Unit
) : ListAdapter<AudioItem, AdapterChapters.ChapterViewHolder>(ChapterDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChapterViewHolder {
        val binding = ChaptersAudioBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ChapterViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChapterViewHolder, position: Int) {
        holder.bind(getItem(position), listener, onChapterClick)
    }

    interface OnAudioClickListener {
        fun onPlayPauseClick(item: AudioItem)
        fun onLikeClick(item: AudioItem) // НОВЫЙ МЕТОД ДЛЯ ЛАЙКА
    }

    class ChapterViewHolder(
        private val binding: ChaptersAudioBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        val playPauseButton: ImageButton = binding.buttonPlayPause

        fun bind(
            item: AudioItem, listener: OnAudioClickListener, onChapterClick: (AudioItem) -> Unit
        ) {
            binding.textViewTitle.text = item.name


            binding.buttonLike.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    listener.onLikeClick(item) // Передаем клик в листенер!
                }
            }
            // Настраиваем визуальное отображение лайка
            if (item.isLiked) {
                // Если лайкнуто — ставим закрашенную звезду/сердечко и красим в желтый/оранжевый
                binding.buttonLike.setImageResource(R.drawable.heart_red) // или ваше кастомное закрашенное сердечко
            } else {
                // Если лайк убран — ставим пустую иконку и красим в белый
                binding.buttonLike.setImageResource(R.drawable.heart) // или ваше пустое сердечко
            }


            // Состояние кнопки аудио-плеера
            val iconRes = if (item.isPlaying) R.drawable.pause else R.drawable.play
            binding.buttonPlayPause.setImageResource(iconRes)


            if (item.isSelected) {
                binding.textViewTitle.setTextColor(Color.YELLOW)
            } else {
                binding.textViewTitle.setTextColor(Color.WHITE)
            }
            // ВКЛЮЧАЕМ СЕЛЕКТОР ОБВОДКИ КАРТОЧКИ
            // Привязываем активацию фона к флагу модели. XML сам нарисует рамку!
            binding.root.isActivated = item.isSelected
            // Клик по кнопке Play/Pause аудиозаписи
            playPauseButton.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    listener.onPlayPauseClick(item)
                }
            }

            // Важный сброс видимости текста и угла стрелочки при прокрутке списка
            binding.textViewChapterContent.visibility = View.GONE
            binding.imageViewArrow.rotation = 0f

            // Функция управления раскрытием текста, его покраской и анимацией стрелочки
            fun toggleExpandState() {
                val isVisible = binding.textViewChapterContent.visibility == View.VISIBLE

                if (isVisible) {
                    binding.textViewChapterContent.visibility = View.GONE
                    binding.imageViewArrow.animate().rotation(0f).setDuration(200).start()
                } else {
                    val context = itemView.context
                    try {
                        // 1. Читаем чистый текст из assets
                        val rawText = context.assets.open(item.textPath).bufferedReader()
                            .use { it.readText() }

                        // 2. Создаем SpannableStringBuilder для раскраски отдельных частей текста
                        val spannableBuilder = SpannableStringBuilder(rawText)

                        // Регулярное выражение ищет цифры в начале строк (например: "1.", "12.")
                        val regex = """(?m)^\d+\.""".toRegex()
                        val matchResults = regex.findAll(rawText)

                        // Задаем цвет для номеров стихов (золотисто-оранжевый)
                        val numColor = Color.parseColor("#FF9800")

                        // Пробегаемся по всем найденным цифрам и красим их
                        for (match in matchResults) {
                            spannableBuilder.setSpan(
                                ForegroundColorSpan(numColor),
                                match.range.first,
                                match.range.last + 1, // Захватываем саму цифру и точку после неё
                                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                            )
                        }

                        // 3. Сетим красивый текст в TextView
                        binding.textViewChapterContent.text = spannableBuilder
                        binding.textViewChapterContent.visibility = View.VISIBLE
                        binding.imageViewArrow.animate().rotation(180f).setDuration(200).start()

                    } catch (e: IOException) {
                        e.printStackTrace()
                        binding.textViewChapterContent.text = "Текст главы временно недоступен"
                        binding.textViewChapterContent.visibility = View.VISIBLE
                    }
                }
            }

            // Нажатие на название главы ИЛИ на саму галочку гарантированно открывает текст
            binding.textViewTitle.setOnClickListener { toggleExpandState() }
            binding.imageViewArrow.setOnClickListener { toggleExpandState() }

            // Клик по всей карточке элемента списка
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onChapterClick(item)
                }
            }
        }
    }

    class ChapterDiffCallback : DiffUtil.ItemCallback<AudioItem>() {
        override fun areItemsTheSame(oldItem: AudioItem, newItem: AudioItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: AudioItem, newItem: AudioItem): Boolean {
            return oldItem == newItem
        }
    }
}
