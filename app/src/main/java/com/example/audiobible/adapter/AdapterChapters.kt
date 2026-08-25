package com.example.audiobible.adapter

import android.graphics.Color
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.audiobible.R
import com.example.audiobible.databinding.ChaptersAudioBinding
import com.example.audiobible.dto.AudioItem
import java.io.IOException

class AdapterChapters(
    private val listener: OnAudioClickListener,
    private val onChapterClick: (AudioItem) -> Unit
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

    override fun onBindViewHolder(holder: ChapterViewHolder, position: Int, payloads: List<Any>) {
        if (payloads.isNotEmpty()) {
            val set = payloads.first() as? Set<*>
            if (set != null) {
                val item = getItem(position)

                if (set.contains("PAYLOAD_PLAY")) {
                    holder.updatePlayState(item)
                }

                if (set.contains("PAYLOAD_LIKE")) {
                    if (!holder.isWaitingForAnimationEnd) {
                        holder.binding.buttonLike.setMinAndMaxProgress(0f, 1f)
                        holder.binding.buttonLike.progress = if (item.isLiked) 1f else 0f
                    }
                }
                // Убираем преждевременный return, чтобы ListAdapter мог корректно обработать
                // остальные системные изменения (включая выделение строк при payload-обновлениях)
            }
        }
        super.onBindViewHolder(holder, position, payloads)
    }

    interface OnAudioClickListener {
        fun onPlayPauseClick(item: AudioItem)
        fun onLikeClick(item: AudioItem)
    }

    // Класс СНОВА ОБЫЧНЫЙ (НЕ inner), что гарантирует стабильную работу itemView и выделения
    class ChapterViewHolder(
        val binding: ChaptersAudioBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        val playPauseButton: ImageButton = binding.buttonPlayPause

        var isWaitingForAnimationEnd = false
        private var lastClickTime = 0L

        fun updatePlayState(item: AudioItem) {
            val iconRes = if (item.isPlaying) R.drawable.pause else R.drawable.play
            binding.buttonPlayPause.setImageResource(iconRes)
        }

        fun bind(
            item: AudioItem, // Сюда всегда прилетает самый свежий item из адаптера
            listener: OnAudioClickListener,
            onChapterClick: (AudioItem) -> Unit
        ) {
            binding.textViewTitle.text = item.name

            // --- НАСТРОЙКА LOTTIE ЛАЙКА + ЗАЩИТА ---
            binding.buttonLike.setOnClickListener(null)
            binding.buttonLike.removeAllAnimatorListeners()

            if (!isWaitingForAnimationEnd) {
                binding.buttonLike.setMinAndMaxProgress(0f, 1f)
                binding.buttonLike.progress = if (item.isLiked) 1f else 0f
            }

            binding.buttonLike.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {

                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastClickTime < 500L) {
                        return@setOnClickListener
                    }
                    lastClickTime = currentTime

                    isWaitingForAnimationEnd = true

                    if (!item.isLiked) {
                        binding.buttonLike.speed = 0.6f
                        binding.buttonLike.setMinAndMaxProgress(0f, 1f)
                        binding.buttonLike.playAnimation()
                    } else {
                        binding.buttonLike.speed = -0.6f
                        binding.buttonLike.setMinAndMaxProgress(0f, 1f)
                        binding.buttonLike.playAnimation()
                    }

                    binding.buttonLike.addAnimatorListener(object : android.animation.Animator.AnimatorListener {
                        override fun onAnimationStart(animation: android.animation.Animator) {}
                        override fun onAnimationRepeat(animation: android.animation.Animator) {}
                        override fun onAnimationCancel(animation: android.animation.Animator) {
                            isWaitingForAnimationEnd = false
                        }
                        override fun onAnimationEnd(animation: android.animation.Animator) {
                            isWaitingForAnimationEnd = false
                        }
                    })

                    listener.onLikeClick(item)
                }
            }

            updatePlayState(item)

            // --- ПОЧИНЕННОЕ ВЫДЕЛЕНИЕ СТРОК ---
            if (item.isSelected) {
                binding.textViewTitle.setTextColor(Color.YELLOW)
            } else {
                binding.textViewTitle.setTextColor(Color.WHITE)
            }

            binding.root.isActivated = item.isSelected

            playPauseButton.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    listener.onPlayPauseClick(item)
                }
            }

            binding.textViewChapterContent.visibility = View.GONE
            binding.imageViewArrow.rotation = 0f

            fun toggleExpandState() {
                val isVisible = binding.textViewChapterContent.visibility == View.VISIBLE

                if (isVisible) {
                    binding.textViewChapterContent.visibility = View.GONE
                    binding.imageViewArrow.animate().rotation(0f).setDuration(200).start()
                } else {
                    val context = itemView.context
                    try {
                        val rawText = context.assets.open(item.textPath).bufferedReader()
                            .use { it.readText() }

                        val spannableBuilder = SpannableStringBuilder(rawText)
                        val regex = """(?m)^\d+\.""".toRegex()
                        val matchResults = regex.findAll(rawText)
                        val numColor = Color.parseColor("#FF9800")

                        for (match in matchResults) {
                            spannableBuilder.setSpan(
                                ForegroundColorSpan(numColor),
                                match.range.first,
                                match.range.last + 1,
                                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                            )
                        }

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

            binding.textViewTitle.setOnClickListener { toggleExpandState() }
            binding.imageViewArrow.setOnClickListener { toggleExpandState() }

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

        override fun getChangePayload(oldItem: AudioItem, newItem: AudioItem): Any? {
            val payloads = mutableSetOf<String>()
            if (oldItem.isLiked != newItem.isLiked) payloads.add("PAYLOAD_LIKE")
            if (oldItem.isPlaying != newItem.isPlaying) payloads.add("PAYLOAD_PLAY")
            return if (payloads.isNotEmpty()) payloads else super.getChangePayload(oldItem, newItem)
        }
    }
}
