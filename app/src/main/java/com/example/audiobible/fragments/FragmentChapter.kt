package com.example.audiobible.fragments

import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.audiobible.R
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import androidx.lifecycle.Lifecycle
import android.widget.SeekBar
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.audiobible.adapter.AdapterChapters
import com.example.audiobible.databinding.FragmentNewChapterBinding
import com.example.audiobible.dto.AudioItem
import dagger.hilt.android.AndroidEntryPoint
import ru.netology.mediapleer2.TrackViewModel
import android.app.PendingIntent
import android.content.Intent
import android.content.Context
import android.os.Build
import androidx.activity.OnBackPressedCallback
import androidx.compose.remote.creation.compose.action.Action
import androidx.core.app.NotificationCompat
import androidx.core.view.isVisible
import com.example.audiobible.plaerManager.PlayerProgressState

@AndroidEntryPoint
class FragmentChapter() : Fragment(), AdapterChapters.OnAudioClickListener {
    private var _binding: FragmentNewChapterBinding? = null
    private val binding get() = _binding!!
    private lateinit var booksAdapter: AdapterChapters

    private var isUserTrackingSeekBar = false

    private val viewModel: TrackViewModel by activityViewModels()

    private var isMiniPlayerMinimized = false

    private val nextTrackHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var nextTrackRunnable: Runnable? = null



    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNewChapterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        booksAdapter = AdapterChapters(
            listener = this,
            onChapterClick = { item ->
                Toast.makeText(
                    requireContext(),
                    "Нажали на главу: ${item.name}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        )
        val idOfBook = arguments?.getInt("ARG_BOOK_ID") ?: 1
        viewModel.loadChapters(idOfBook)
//viewModel.loadChapters(arguments?.bookId ?: 0)

        binding.recyclerViewChapter.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = booksAdapter
        }

        // 3. Создаем красивый тестовый список книг
        viewModel.chaptersData.observe(viewLifecycleOwner) { tracks ->
            booksAdapter.submitList(tracks) {
            }
        }

        binding.layoutMiniPlayer.post {
            binding.layoutMiniPlayer.pivotX = binding.layoutMiniPlayer.width / 2f
            binding.layoutMiniPlayer.pivotY = binding.layoutMiniPlayer.height.toFloat()
        }

        // Подписка на состояние плеера и управление мини-плеером
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.playerState.collectLatest { state ->

                     val openedBookId = viewModel.getCurrentBookId()

                    // Если плеер заряжен И играет книга именно ЭТОГО экрана — показываем мини-плеер
                    if (state.total > 0 && state.playingBookId == openedBookId ) {

                        binding.layoutMiniPlayer.isVisible = true
                        binding.textMiniPlayerTitle.text = state.name
                        binding.seekBarMiniPlayer.max = state.total

                        if (!isUserTrackingSeekBar) {
                            binding.seekBarMiniPlayer.progress = state.current
                            binding.textCurrentTime.text = state.currentStr
                        }

                        binding.textTotalTime.text = state.totalStr
                    } else {
                        // Если плеер пуст ИЛИ играет глава из СОВСЕМ ДРУГОЙ книги — прячем мини-плеер
                        binding.layoutMiniPlayer.isVisible = false
                    }

                    val iconRes = if (state.isPlaying) R.drawable.pause else R.drawable.play
                    binding.buttonMiniPlayerPlayPause.setImageResource(iconRes)
                }
            }
        }


        // Обработка клика Play/Pause мини-плеера
        binding.buttonMiniPlayerPlayPause.setOnClickListener {
            val isPlaying = viewModel.playerState.value.isPlaying
            if (isPlaying) {
                viewModel.pauseTrack()
                val currentChapters = viewModel.chaptersData.value ?: emptyList()
                val currentChapter = currentChapters.getOrNull(viewModel.getCurrentPosition())
                if (currentChapter != null) {
                    viewModel.saveCurrentPlaybackPosition(currentChapter)
                }
            } else {
                viewModel.resumeTrack()
                booksAdapter.notifyItemChanged(viewModel.getCurrentPosition())
            }
        }

        binding.hide.setOnClickListener {
            // 1. Проверяем, скрыт ли сейчас мини-плеер (или его развернутая часть).
            // ОБЯЗАТЕЛЬНО замените 'layoutMiniPlayer' на реальный ID контейнера вашего плеера!
            // val isVisible = binding.layoutMiniPlayer.visibility == View.VISIBLE

            if (!isMiniPlayerMinimized) {
                binding.hide.animate().rotation(0f).setDuration(200).start()
                binding.layoutMiniPlayer.post {
                    binding.layoutMiniPlayer.pivotX = binding.layoutMiniPlayer.width / 2f
                    binding.layoutMiniPlayer.pivotY = binding.layoutMiniPlayer.height.toFloat()
                    binding.layoutMiniPlayer.animate()
                        .scaleX(0.3f)
                        .scaleY(0.3f)
                        .setDuration(300)
                        .start()
                }
                isMiniPlayerMinimized = true
            } else {
                binding.hide.animate().rotation(180f).setDuration(200).start()
                binding.layoutMiniPlayer.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(300)
                    .start()

                isMiniPlayerMinimized = false
            }
        }


        // Обработка перемотки через SeekBar
        binding.seekBarMiniPlayer.setOnSeekBarChangeListener(object :
            SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    binding.textCurrentTime.text =
                        String.format("%02d:%02d", (progress / 1000) / 60, (progress / 1000) % 60)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isUserTrackingSeekBar = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                viewModel.seekTo(binding.seekBarMiniPlayer.progress)
                isUserTrackingSeekBar = false
                val chapters = viewModel.chaptersData.value ?: emptyList()
                val currentChapter = chapters.getOrNull(viewModel.getCurrentPosition())
                if (currentChapter != null) {
                    viewModel.saveCurrentPlaybackPosition(currentChapter)
                }
            }
        })

        // Кнопки перемотки 15 секунд
        binding.buttonMiniPlayerRewind.setOnClickListener {
            viewModel.rewind15Seconds()
            val currentChapters = viewModel.chaptersData.value ?: emptyList()
            val currentChapter = currentChapters.getOrNull(viewModel.getCurrentPosition())
            if (currentChapter != null) {
                viewModel.saveCurrentPlaybackPosition(currentChapter)
            }
        }

        binding.buttonMiniPlayerForward.setOnClickListener {
            viewModel.forward15Seconds()
            val currentChapters = viewModel.chaptersData.value ?: emptyList()
            val currentChapter = currentChapters.getOrNull(viewModel.getCurrentPosition())
            if (currentChapter != null) {
                viewModel.saveCurrentPlaybackPosition(currentChapter)
            }
        }

        // Переключение на следующий/предыдущий трек
        binding.buttonPrevTrack.setOnClickListener {
            viewModel.previousTrack()
        }

        binding.buttonNextTrack.setOnClickListener {

                    viewModel.nextTrack()

        }



        // МЕХАНИЗМ ПЕРЕОПРЕДЕЛЕНИЯ КНОПКИ НАЗАД + ПАУЗА И ВЫХОД
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    viewModel.pauseTrack()
                    // 2. Находим и скрываем карточку плеера в Activity
                  //  hideMiniPlayer()


                    // 3. СРАЗУ выходим из фрагмента назад (без блокировки через return)
                    isEnabled = false // Отключаем перехватчик, чтобы избежать бесконечного цикла
                    requireActivity().onBackPressedDispatcher.onBackPressed() // Выполняем системный шаг назад
                }
            }
        )

    }

    override fun onPlayPauseClick(item: AudioItem) {
        // 🔥 Ищем индекс по уникальному ID главы, это сработает со 100% гарантией!
        val position = booksAdapter.currentList.indexOfFirst { it.id == item.id }


        viewModel.toggleChapter(item, position)

    }


    override fun onLikeClick(item: AudioItem) {
        viewModel.toggleLike(item)
    }

    //пока не нужно но может потом пригодится
    private fun showMiniPlayer() {
        binding.layoutMiniPlayer.isVisible = true
        binding.hide.animate().rotation(180f).setDuration(200).start()
        binding.layoutMiniPlayer.animate()
            .scaleX(1.0f)
            .scaleY(1.0f)
            .setDuration(300)
            .start()

        isMiniPlayerMinimized = false
    }

    //пока не нужно но может потом пригодится
    private fun hideMiniPlayer() {
        binding.hide.animate().rotation(0f).setDuration(200).start()
        binding.layoutMiniPlayer.animate()
            .scaleX(0.3f)
            .scaleY(0.3f)
            .setDuration(300)
            .start()
        isMiniPlayerMinimized = true
    }




}