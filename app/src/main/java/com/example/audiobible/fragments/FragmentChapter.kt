package com.example.audiobible.fragments

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
import androidx.activity.OnBackPressedCallback

@AndroidEntryPoint
class FragmentChapter() : Fragment(), AdapterChapters.OnAudioClickListener {
    private var _binding: FragmentNewChapterBinding? = null
    private val binding get() = _binding!!
    private lateinit var booksAdapter: AdapterChapters

    private var isUserTrackingSeekBar = false

    private val viewModel: TrackViewModel by activityViewModels()

    private var nameBook = ""

    private var isMenuOpen = false
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNewChapterBinding.inflate(inflater, container, false)




// При открытии фрагмента или при выборе книги
        binding.tvBookTitle.text = nameBook

        binding.btnBack.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        binding.btnMenu.setOnClickListener {
            val targetY = if (isMenuOpen) binding.menuPanel.height else 0f
            binding.menuPanel.animate()
             //   .translationY(targetY)
                .setDuration(300)
                .withEndAction { isMenuOpen = !isMenuOpen }
                .start()
        }


        return binding.root
    }



    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. РАСКОММЕНТИРОВАНО И ИСПРАВЛЕНО: Создаем адаптер
        // Передаем 'this' как OnAudioClickListener, а во втором лямбда-выражении обрабатываем клик по самой главе
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
         nameBook = arguments?.getString("ARG_BOOK_NAME") ?: "Null"
        viewModel.loadChapters(idOfBook)
        // Обращаемся к MainActivity и меняем тулбар
        (activity as? AppActivity)?.changeToolbar(
            title = nameBook
        )
        binding.recyclerViewChapter.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = booksAdapter
        }

        // 3. Создаем красивый тестовый список глав
        viewModel.chaptersData.observe(viewLifecycleOwner) { tracks ->
            booksAdapter.submitList(tracks)
        }

        // Подписка на состояние плеера и управление мини-плеером (перенесено из AppActivity)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.playerState.collectLatest { state ->
                    if (state.total > 0) {
                            showMiniPlayer()
                        binding.textMiniPlayerTitle.text = "$nameBook " + state.name
                        binding.seekBarMiniPlayer.max = state.total
                        if (!isUserTrackingSeekBar) {
                            binding.seekBarMiniPlayer.progress = state.current
                            binding.textCurrentTime.text = state.currentStr
                        }

                        binding.textTotalTime.text = state.totalStr
                    } else {
                            hideMiniPlayer()
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
            }
        }

        // Обработка перемотки через SeekBar
        binding.seekBarMiniPlayer.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    binding.textCurrentTime.text = String.format("%02d:%02d", (progress / 1000) / 60, (progress / 1000) % 60)
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
                    hideMiniPlayer()


                    // 3. СРАЗУ выходим из фрагмента назад (без блокировки через return)
                    isEnabled = false // Отключаем перехватчик, чтобы избежать бесконечного цикла
                    requireActivity().onBackPressedDispatcher.onBackPressed() // Выполняем системный шаг назад
                }
            }
        )

    }

    override fun onPlayPauseClick(item: AudioItem) {
        val position = booksAdapter.currentList.indexOf(item)
        if (position == -1) return

        // Просто отдаем команду во ViewModel, передавая главу и её позицию в адаптере
        viewModel.toggleChapter(item, position, bookName = nameBook)
    }


    private fun showMiniPlayer() {
        binding.layoutMiniPlayer.apply {
            if (visibility != View.VISIBLE) {
                // быстрый слайд вверх с появлением
                visibility = View.VISIBLE
                alpha = 0f
                translationY = 50f
                animate().alpha(1f).translationY(0f).setDuration(250).start()
            }
        }
    }

    private fun hideMiniPlayer() {
        binding.layoutMiniPlayer.apply {
            if (visibility == View.VISIBLE) {
                animate().alpha(0f).translationY(50f).setDuration(200).withEndAction {
                    visibility = View.GONE
                    alpha = 1f
                    translationY = 0f
                }.start()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // Освобождаем binding, чтобы не было утечек памяти
    }


}
