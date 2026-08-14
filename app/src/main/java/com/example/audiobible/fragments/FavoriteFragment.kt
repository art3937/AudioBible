package com.example.audiobible.fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
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

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFavoriteBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, bundle: Bundle?) {
        super.onViewCreated(view, bundle)
        Log.d("FAV_FRAG", "onViewCreated: Экран избранного успешно запущен")

        // 1. Инициализируем адаптер
        val favoriteAdapter = AdapterChapters(
            object : AdapterChapters.OnAudioClickListener {
                override fun onPlayPauseClick(item: AudioItem) {
                    // Отдаем команду во ViewModel, она сама вычислит реальный индекс
                    trackViewModel.toggleChapter(item, 0)
                }

                override fun onLikeClick(item: AudioItem) {
                    favoriteViewModel.removeCardFromFavorites(item.id)
                    trackViewModel.syncLikeStatus(item.id, isLiked = false)
                }
            },
            onChapterClick = { item -> }
        )

        // 2. Настраиваем RecyclerView
        binding.recyclerViewFavorites.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewFavorites.adapter = favoriteAdapter

        // 3. Первоначальный разовый запрос данных при старте (с тремя параметрами!)
        val initialTrackId = trackViewModel.getCurrentPosition() // Берем ID главы, а не позицию секунд!
        val isPlaying = trackViewModel.isPlaying
        val initialBookId = trackViewModel.getCurrentBookId()

        Log.d("FAV_FRAG", "Старт — Глава ID: $initialTrackId, Книга ID: $initialBookId, Играет: $isPlaying")
        favoriteViewModel.loadAllFavorites(initialTrackId, isPlaying, initialBookId)

        // 4. Слушаем поток данных из новой изолированной ViewModel
        favoriteViewModel.favoriteChaptersData.observe(viewLifecycleOwner) { favorites ->
            Log.d("FAV_FRAG", "Observer сработал! Получено элементов из базы: ${favorites?.size ?: 0}")
            favoriteAdapter.submitList(favorites)
        }

        // 5. Динамическое обновление при любых действиях в плеере
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                trackViewModel.playerState.collectLatest { state ->
                    // Здесь тоже берем ID играющей главы, а не секунды!
                    val currentTrackId = trackViewModel.getCurrentPosition()
                    val isPlayingNow = state.isPlaying
                    val playingBookId = trackViewModel.getCurrentBookId()

                    Log.d("FAV_FRAG", "Плеер обновился — Глава ID: $currentTrackId, Книга ID: $playingBookId, Играет: $isPlayingNow")

                    // Обновляем список в FavoriteViewModel с точным учетом книги
                    favoriteViewModel.loadAllFavorites(currentTrackId, isPlayingNow, playingBookId)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
