package com.example.audiobible.fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.activity.OnBackPressedCallback
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.audiobible.R
import com.example.audiobible.adapter.AdapterChapters
import com.example.audiobible.dto.AudioItem
import dagger.hilt.android.AndroidEntryPoint
import ru.netology.mediapleer2.TrackViewModel
import com.example.audiobible.viewmodel.FavoriteViewModel
import com.example.audiobible.databinding.FragmentFavoriteBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class FavoriteFragment : Fragment() {

    private val trackViewModel: TrackViewModel by activityViewModels()
    private val favoriteViewModel: FavoriteViewModel by viewModels()

    private var _binding: FragmentFavoriteBinding? = null
    private val binding get() = _binding!!

    // Состояния управления мини-плеером из твоего оригинала
    private var isUserTrackingSeekBar = false
    private var isMiniPlayerMinimized = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        trackViewModel.clearPlayer()
        _binding = FragmentFavoriteBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, bundle: Bundle?) {
        super.onViewCreated(view, bundle)
        Log.d("FAV_FRAG", "onViewCreated: Экран избранного успешно запущен")

        // Начальная настройка пивотов для анимации скрытия плера
        binding.layoutMiniPlayer.post {
            binding.layoutMiniPlayer.pivotX = binding.layoutMiniPlayer.width / 2f
            binding.layoutMiniPlayer.pivotY = binding.layoutMiniPlayer.height.toFloat()
        }

        // 1. Инициализируем адаптер списков
        val favoriteAdapter = AdapterChapters(
            object : AdapterChapters.OnAudioClickListener {
                override fun onPlayPauseClick(item: AudioItem) {
                    trackViewModel.toggleChapter(item, 0)
                }

                override fun onLikeClick(item: AudioItem) {
                    favoriteViewModel.removeCardFromFavorites(item.id)
                    trackViewModel.syncLikeStatus(item.id, isLiked = false)
                }
            },
            onChapterClick = { item -> }
        )

        binding.recyclerViewFavorites.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewFavorites.adapter = favoriteAdapter

        // 2. Первоначальный разовый запрос данных при старте
        val initialTrackId = trackViewModel.getCurrentPosition()
        val isPlaying = trackViewModel.isPlaying
        val initialBookId = trackViewModel.getCurrentBookId()
        favoriteViewModel.loadAllFavorites(initialTrackId, isPlaying, initialBookId)

        // 3. Подписка на список избранного из базы данных
        favoriteViewModel.favoriteChaptersData.observe(viewLifecycleOwner) { favorites ->
            favoriteAdapter.submitList(favorites)
        }

        // 4. ПОДПИСКА НА СОСТОЯНИЕ ПЛЕЕРА И УПРАВЛЕНИЕ МИНИ-ПЛЕЕРОМ
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                trackViewModel.playerState.collectLatest { state ->
                    val currentTrackId = trackViewModel.getCurrentPosition()
                    val isPlayingNow = state.isPlaying
                    val playingBookId = trackViewModel.getCurrentBookId()

                    // Обновляем кнопки и подсветку внутри самого списка RecyclerView
                    favoriteViewModel.loadAllFavorites(currentTrackId, isPlayingNow, playingBookId)

                    // Если плеер заряжен — всегда показываем мини-плеер в Избранном (без привязки к конкретной книге!)
                    if (state.total > 0) {
                        binding.layoutMiniPlayer.isVisible = true
                        binding.textMiniPlayerTitle.text = state.name
                        binding.seekBarMiniPlayer.max = state.total

                        if (!isUserTrackingSeekBar) {
                            binding.seekBarMiniPlayer.progress = state.current
                            binding.textCurrentTime.text = state.currentStr
                        }
                        binding.textTotalTime.text = state.totalStr
                    } else {
                        binding.layoutMiniPlayer.isVisible = false
                    }

                    val iconRes = if (state.isPlaying) R.drawable.pause else R.drawable.play
                    binding.buttonMiniPlayerPlayPause.setImageResource(iconRes)
                }
            }
        }

        // 5. ОБРАБОТКА КЛИКА PLAY/PAUSE МИНИ-ПЛЕЕРА
        binding.buttonMiniPlayerPlayPause.setOnClickListener {
            val isPlayingNow = trackViewModel.playerState.value.isPlaying
            if (isPlayingNow) {
                trackViewModel.pauseTrack() // Вызываем метод паузы из твоей TrackViewModel (замени на реальное имя, если отличается)
            } else {
                trackViewModel.resumeTrack() // Вызываем метод возобновления из твоей TrackViewModel

                // Обновляем текущий элемент в Избранном, чтобы заиграл
                val favorites = favoriteAdapter.currentList
                val currentItem = favorites.find { it.id == trackViewModel.getCurrentPosition() }
                if (currentItem != null) {
                    val position = favorites.indexOf(currentItem)
                    favoriteAdapter.notifyItemChanged(position)
                }
            }
        }

        // 6. КНОПКА СВЕРНУТЬ/РАЗВЕРНУТЬ МИНИ-ПЛЕЕР (ТВОЯ АНИМАЦИЯ)
        binding.hide.setOnClickListener {
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

        // 7. ОБРАБОТКА ПЕРЕМОТКИ ЧЕРЕЗ SEЕKBAR
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
                trackViewModel.seekTo(binding.seekBarMiniPlayer.progress)
                isUserTrackingSeekBar = false
            }
        })

        // 8. КНОПКИ ПЕРЕМОТКИ НА 15 СЕКУНД НАЗАД/ВПЕРЕД
        binding.buttonMiniPlayerRewind.setOnClickListener {
            trackViewModel.rewind15Seconds()
        }

        binding.buttonMiniPlayerForward.setOnClickListener {
            trackViewModel.forward15Seconds()
        }

        // 9. ПЕРЕКЛЮЧЕНИЕ НА СЛЕДУЮЩИЙ/ПРЕДЫДУЩИЙ ТРЕК СРАЗУ ИЗ ИЗБРАННОГО
        binding.buttonPrevTrack.setOnClickListener {
            trackViewModel.previousTrack()
        }

        binding.buttonNextTrack.setOnClickListener {
            trackViewModel.nextTrack()
        }

        // 10. МЕХАНИЗМ ПЕРЕОПРЕДЕЛЕНИЯ КНОПКИ НАЗАД + ПАУЗА И ВЫХОД ИЗ ФРАГМЕНТА
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    trackViewModel.pauseTrack()
                    isEnabled = false
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                }
            }
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
