package com.example.audiobible.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.example.audiobible.R
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.audiobible.adapter.BibleBooksAdapter
import com.example.audiobible.databinding.FragmentFeedBinding
import com.example.audiobible.dto.Book

import dagger.hilt.android.AndroidEntryPoint
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import com.example.audiobible.viewModels.BookViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@AndroidEntryPoint

class FeedFragment : Fragment() {

    private var _binding: FragmentFeedBinding? = null
    private val binding get() = _binding!!


    private val viewModel: BookViewModel by viewModels()
    private lateinit var booksAdapter: BibleBooksAdapter

    // We'll keep the current list locally but source is ViewModel
    private var currentBooks: MutableList<Book> = mutableListOf()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFeedBinding.inflate(inflater, container, false)
        return binding.root
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        // 1. Инициализируем адаптер (код клика остается прежним)
        booksAdapter = BibleBooksAdapter(
            onBookClick = { clickedBook ->
                Toast.makeText(
                    requireContext(),
                    "Нажали на книгу: ${clickedBook.name}",
                    Toast.LENGTH_SHORT
                ).show()

                // Сообщаем ViewModel о выборе книги — LiveData сама обновит список
                viewModel.selectBook(clickedBook.id)

                findNavController().navigate(
                    R.id.action_feedFragment_to_fragmentChapter2,

    //                    chapterId = clickedBook.id
                    Bundle().apply {
                        putInt("ARG_BOOK_ID", clickedBook.id) // Передаем именно Int!
                        putString("ARG_BOOK_NAME", clickedBook.name)
                    }
                )
            },
            recyclerView = binding.recyclerViewBooks
        )
        (activity as? AppActivity)?.updateTopBarTitle("Аудио Библия")
        // 2. Настраиваем RecyclerView
        binding.recyclerViewBooks.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = booksAdapter
        }


        // Add scroll listener to scale centered item
        binding.recyclerViewBooks.addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: androidx.recyclerview.widget.RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (dy != 0) {
                    booksAdapter.resetExpandedCard()
                }
                applyScaleToChildren()
            }

            override fun onScrollStateChanged(recyclerView: androidx.recyclerview.widget.RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                // ensure final state when scrolling stops
                if (newState == androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_IDLE) {
                    booksAdapter.resetExpandedCard()
                    applyScaleToChildren()
                }
            }
        })

        // 3. ОБЫЧНЫЙ ОБСЕРВЕР ДЛЯ ОБНОВЛЕНИЯ АДАПТЕРА
        // Работает при старте, при кликах и при возвращении на этот экран
        viewModel.booksLiveData.observe(viewLifecycleOwner) { books ->
            booksAdapter.submitList(books)
            // apply scaling after list is submitted and layout happens
            binding.recyclerViewBooks.post { applyScaleToChildren() }
        }
    }


    // Scale children based on distance to RecyclerView center
    private fun applyScaleToChildren() {
        val recycler = binding.recyclerViewBooks
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

            // animate scale smoothly
            child.pivotX = (child.width / 2).toFloat()
            child.pivotY = (child.height / 2).toFloat()
            child.scaleX = scale
            child.scaleY = scale

            // optional elevation change to emphasize center
            child.elevation = if (factor < 0.25f) 12f else 4f
        }
    }

}
