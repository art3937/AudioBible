package com.example.audiobible.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
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

    private val viewModel: TrackViewModel by viewModels()


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
        viewModel.loadChapters(idOfBook)
//viewModel.loadChapters(arguments?.bookId ?: 0)

        binding.recyclerViewChapter.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = booksAdapter
        }

        // 3. Создаем красивый тестовый список книг
        viewModel.chaptersData.observe(viewLifecycleOwner) { tracks ->
            booksAdapter.submitList(tracks)
        }

        // МЕХАНИЗМ ПЕРЕОПРЕДЕЛЕНИЯ КНОПКИ НАЗАД + ПАУЗА И ВЫХОД
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    viewModel.pauseTrack()
                    // 2. Находим и скрываем карточку плеера в Activity
                    val cardMiniPlayer = requireActivity().findViewById<View>(com.example.audiobible.R.id.layoutMiniPlayer)
                    cardMiniPlayer.visibility = View.GONE


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
        viewModel.toggleChapter(item, position)
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // Освобождаем binding, чтобы не было утечек памяти
    }


}
