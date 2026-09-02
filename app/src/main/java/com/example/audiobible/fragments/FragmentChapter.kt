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
import android.app.PendingIntent
import android.content.Intent
import android.content.Context
import android.os.Build
import androidx.activity.OnBackPressedCallback
import androidx.compose.remote.creation.compose.action.Action
import androidx.core.app.NotificationCompat
import androidx.core.view.isVisible
import com.example.audiobible.plaerManager.PlayerProgressState
import com.example.audiobible.viewModels.TrackViewModel

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
        // 2. Стучимся в Активити и отдаем название в шапку
        val bookName = viewModel.getBookName(idOfBook)
        (activity as? AppActivity)?.updateTopBarTitle(bookName)


        binding.recyclerViewChapter.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = booksAdapter
        }

        // Add scroll listener to scale centered item (and keep mini-player behavior)
        binding.recyclerViewChapter.addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: androidx.recyclerview.widget.RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)

                // Scale visible children based on center
                applyScaleToChildren()

                // Проверяем, упёрся ли пользователь в самый низ списка
                val isAtBottom = !recyclerView.canScrollVertically(1)

                if (isAtBottom) {
                    // Поднимаем плеер на 80 пикселей вверх
                    binding.layoutMiniPlayer.animate()
                        .translationY(-80f)
                        .setDuration(200)
                        .start()
                } else {
                    // Возвращаем плеер на место к нижнему краю
                    binding.layoutMiniPlayer.animate()
                        .translationY(0f)
                        .setDuration(200)
                        .start()
                }
            }

            override fun onScrollStateChanged(recyclerView: androidx.recyclerview.widget.RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (newState == androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_IDLE) {
                    applyScaleToChildren()
                }
            }
        })

        // 3. Создаем красивый тестовый список книг
        viewModel.chaptersData.observe(viewLifecycleOwner) { tracks ->
            booksAdapter.submitList(tracks) {
                // ensure scaling is applied after layout
                binding.recyclerViewChapter.post { applyScaleToChildren() }
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

        binding.recyclerViewChapter.addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: androidx.recyclerview.widget.RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)

                // Проверяем, упёрся ли пользователь в самый низ списка
                val isAtBottom = !recyclerView.canScrollVertically(1)

                if (isAtBottom) {
                    // Поднимаем плеер на 80 пикселей вверх
                    binding.layoutMiniPlayer.animate()
                        .translationY(-80f)
                        .setDuration(200)
                        .start()
                } else {
                    // Возвращаем плеер на место к нижнему краю
                    binding.layoutMiniPlayer.animate()
                        .translationY(0f)
                        .setDuration(200)
                        .start()
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

    override fun onPlayPauseClick(item: AudioItem,position: Int) {
        viewModel.toggleChapter(item)
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

    // Scale children based on distance to RecyclerView center (same behavior as books list)
    private fun applyScaleToChildren() {
        val recycler = binding.recyclerViewChapter
        if (recycler.childCount == 0) return
        val centerY = recycler.height / 2f
        val maxDistance = recycler.height / 2f
        val maxScale = 1.08f
        val minScale = 0.96f

        for (i in 0 until recycler.childCount) {
            val child = recycler.getChildAt(i) ?: continue
            val childCenterY = (child.top + child.bottom) / 2f
            val distance = kotlin.math.abs(childCenterY - centerY)
            val factor = (distance / maxDistance).coerceIn(0f, 1f)
            val scale = maxScale - (maxScale - minScale) * factor

            // apply scale
            child.pivotX = (child.width / 2).toFloat()
            child.pivotY = (child.height / 2).toFloat()
            child.scaleX = scale
            child.scaleY = scale

            // elevation accent
            child.elevation = if (factor < 0.25f) 12f else 4f
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Стучимся в Активити и полностью очищаем текст в шапке
        (activity as? AppActivity)?.updateTopBarTitle("")
    }

}