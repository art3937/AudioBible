package com.example.audiobible.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageButton
import androidx.core.graphics.toColorInt
import com.example.audiobible.R
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.audiobible.databinding.ChaptersAudioBinding
// Проверьте этот импорт
import com.example.audiobible.dto.AudioItem

class AdapterChapters(
    private val listener: OnAudioClickListener,
    private val onChapterClick: (AudioItem) -> Unit
) : ListAdapter<AudioItem, AdapterChapters.ChapterViewHolder>(ChapterDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChapterViewHolder {
        val binding = ChaptersAudioBinding.inflate(LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ChapterViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChapterViewHolder, position: Int) {
        holder.bind(getItem(position))
    }




    interface OnAudioClickListener {
        fun onPlayPauseClick(item: AudioItem)

    }
    inner class ChapterViewHolder(
        private val binding: ChaptersAudioBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        // Выносим кнопку наружу, если она нужна в onBindViewHolder,
        // но лучше управлять состоянием прямо внутри bind()
        val playPauseButton: ImageButton = binding.buttonPlayPause


        fun bind(item: AudioItem) {
            // Убедитесь, что внутри вашего XML (chapters_audio.xml)
            // есть ID: textViewTitle и layoutBackground (или замените на свои)
            binding.textViewTitle.text = item.name
            val iconRes = if (item.isPlaying) R.drawable.pause else R.drawable.play
            binding.buttonPlayPause.setImageResource(iconRes)
            try {
                // Если у AudioItem есть цвет, используем его, иначе — заглушку
                binding.layoutBackground.setBackgroundColor("#2C5282".toColorInt())
            } catch (e: Exception) {
                binding.layoutBackground.setBackgroundColor(Color.DKGRAY)
            }

            playPauseButton.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    listener.onPlayPauseClick(item)
                }
            }

//            // Клик по элементу списка
//            binding.root.setOnClickListener {
//                onChapterClick(item)
//            }
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
