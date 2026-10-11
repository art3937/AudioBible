package com.example.audiobible.fragments // Убедитесь, что этот пакет совпадает с вашим

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ContextThemeWrapper
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.navigation.fragment.NavHostFragment
import com.example.audiobible.R
import com.example.audiobible.databinding.ActivityAppBinding
import com.example.audiobible.viewModels.TrackViewModel
import dagger.hilt.android.AndroidEntryPoint
import androidx.lifecycle.lifecycleScope
import com.example.audiobible.generatorAll.ProxyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.core.content.edit
import com.airbnb.lottie.Lottie
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieDrawable
import com.example.audiobible.generatorAll.ImageGenerator
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class AppActivity : AppCompatActivity() {

    private val viewModel: TrackViewModel by viewModels()

    @javax.inject.Inject
    lateinit var bookStateRepository: com.example.audiobible.repository.BookStateRepository

    private lateinit var binding: ActivityAppBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ЗАПУСКАТЕЛЬ ДЯТЛА: Качаем базу один раз при старте приложения
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                Log.d(
                    "BREAD_PARSER_LOG",
                    "[PROXY] Инициализация прокси-базы при старте приложения..."
                )
                ProxyManager.fetchFreshProxies()
            } catch (e: Exception) {
                Log.e("BREAD_PARSER_LOG", "[PROXY] Сбой предзагрузки баз: ${e.message}")
            }
        }

        // 1. Включаем Edge-to-Edge: разрешаем контенту (книге) затекать под StatusBar
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT

        binding = ActivityAppBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val lottieView = LottieAnimationView(this).apply {
            setAnimation(R.raw.activity)
            repeatCount = LottieDrawable.INFINITE
            playAnimation()

            // КОД ДЛЯ ПЕРЕКРАСКИ ЛИНИИ В БЕЛЫЙ ЦВЕТ:
            // Маска "**" находит все слои, а "Stroke 1" — это точное название обводки внутри вашего JSON
            val keyPath = com.airbnb.lottie.model.KeyPath("**", "Stroke 1")

            // Создаем белый фильтр
            val whiteFilter = com.airbnb.lottie.SimpleColorFilter(android.graphics.Color.WHITE)

            // Применяем фильтр к цвету обводки
            addValueCallback(keyPath, com.airbnb.lottie.LottieProperty.COLOR_FILTER) { whiteFilter }
        }

        // Добавляем белый пульс в прозрачный контейнер
        binding.activityAnimationContainer.addView(lottieView)
        // Слушаем размеры системных окон (включая StatusBar)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            val statusBarHeight = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars()).top

            // 1. Сдвигаем саму панель вниз ровно на высоту StatusBar, чтобы она не перекрывала часы
            binding.topBar.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = statusBarHeight
            }

            // 2. Вычисляем общий отступ для контента: StatusBar + Высота вашей панели (64dp)
            val density = resources.displayMetrics.density
            val customBarHeight = (64 * density).toInt()
            val totalTopPadding = statusBarHeight + customBarHeight

            // 3. Передаем этот отступ в контейнер, чтобы тулбар больше ничего не закрывал!
            binding.navMain.setPadding(0, totalTopPadding, 0, 0)

            windowInsets
        }

        WindowCompat.getInsetsController(window, window.decorView).apply {
            // true — делает иконки в StatusBar ТЕМНЫМИ (чтобы их было видно поверх светлой книги)
            isAppearanceLightStatusBars = true
        }

        // Получаем навигационный контроллер по ID nav_main
        val navController =
            (supportFragmentManager.findFragmentById(binding.navMain.id) as NavHostFragment).navController

        requestNotificationsPermission()

        // Функция безопасного выхода назад с остановкой плеера
        fun handleBackNavigation() {
            if (navController.currentDestination?.id == R.id.fragmentChapter2) {
                Log.d("AppActivity", "==> Выходим из главы: ставим плеер на паузу")
                viewModel.pauseTrack() // Вызываем метод паузы нашей ViewModel!
            }
            navController.navigateUp()
        }

        // 1. КЛИК ПО ПАРЯЩЕЙ СТРЕЛОЧКЕ НАЗАД ВНУТРИ ПАНЕЛИ
        binding.btnBack.setOnClickListener {
            handleBackNavigation()
        }

        // КЛИК ПО КНОПКЕ МЕНЮ ВНУТРИ ПАНЕЛИ
        binding.fabMenu.setOnClickListener { view ->
            showFloatingMenu(view, navController)
        }

        // 2. СИСТЕМНАЯ КНОПКА НАЗАД (жест телефона или треугольник внизу экрана)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (navController.previousBackStackEntry != null) {
                    handleBackNavigation()
                } else {
                    finish()
                }
            }
        })

        // 3. СЛУШАТЕЛЬ НАВИГАЦИИ (управляет видимостью стрелочки "Назад" внутри панели)
        navController.addOnDestinationChangedListener { _, _, _ ->
            if (navController.previousBackStackEntry != null) {
                binding.btnBack.visibility = View.VISIBLE
            } else {
                binding.btnBack.visibility = View.GONE
            }
        }

        // Восстановление последнего трека из БД при холодном старте
        if (savedInstanceState == null) {
            viewModel.restoreLastGlobalTrack()
        }
    }

    /**
     * Метод для динамической установки названия книги по центру шапки.
     * Фрагменты могут вызывать этот метод через (activity as? AppActivity)?.updateTopBarTitle("...")
     */
    fun updateTopBarTitle(title: String?) {
        if (title.isNullOrEmpty()) {
            binding.tvBookTitle.text = "Аудио Библия"
        } else {
            binding.tvBookTitle.text = title
        }
    }

    // Метод создания и отображения стилизованного парящего меню
    private fun showFloatingMenu(
        anchorView: View,
        navController: androidx.navigation.NavController
    ) {
        val contextWrapper = ContextThemeWrapper(this, R.style.Theme_AudioBible_PopupWrapper)
        val popup = PopupMenu(contextWrapper, anchorView)

        popup.menuInflater.inflate(R.menu.auth_menu, popup.menu)

        val menu = popup.menu
        for (i in 0 until menu.size()) {
            val menuItem = menu.getItem(i)
            val rawTitle = menuItem.title.toString()
            val spannableTitle = SpannableString(rawTitle)

            spannableTitle.setSpan(
                ForegroundColorSpan(Color.WHITE),
                0,
                spannableTitle.length,
                Spanned.SPAN_INCLUSIVE_INCLUSIVE
            )

            spannableTitle.setSpan(
                StyleSpan(Typeface.BOLD),
                0,
                spannableTitle.length,
                Spanned.SPAN_INCLUSIVE_INCLUSIVE
            )

            menuItem.title = spannableTitle
        }

        // Add checkbox item for image generation
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val enabled = prefs.getBoolean("generate_images_enabled", false)
        // ... (ваш старый код добавления пункта "Генерировать картинки") ...
        val toggleItem = menu.add(
            0,
            com.example.audiobible.R.id.action_toggle_images,
            0,
            "Генерировать картинки"
        )
        toggleItem.isCheckable = true
        toggleItem.isChecked = enabled
        val spannableToggle = SpannableString(toggleItem.title.toString())
        spannableToggle.setSpan(
            ForegroundColorSpan(Color.WHITE),
            0,
            spannableToggle.length,
            Spanned.SPAN_INCLUSIVE_INCLUSIVE
        )
        spannableToggle.setSpan(
            StyleSpan(Typeface.BOLD),
            0,
            spannableToggle.length,
            Spanned.SPAN_INCLUSIVE_INCLUSIVE
        )
        toggleItem.title = spannableToggle

        // 🔥 ДОБАВЛЯЕМ СЮДА КНОПКУ ОЧИСТКИ КЭША:
        val clearItem = menu.add(
            0,
            999111,
            1,
            "Очистить картинки"
        ) // 999111 — это временный ID, можно заменить на ваш из R.id
        val spannableClear = SpannableString(clearItem.title.toString())
        spannableClear.setSpan(
            ForegroundColorSpan(Color.RED),
            0,
            spannableClear.length,
            Spanned.SPAN_INCLUSIVE_INCLUSIVE
        ) // Покрасим в красный
        spannableClear.setSpan(
            StyleSpan(Typeface.BOLD),
            0,
            spannableClear.length,
            Spanned.SPAN_INCLUSIVE_INCLUSIVE
        )
        clearItem.title = spannableClear

        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.favorites -> {
                    Log.d("AppActivity", "==> Переход на фрагмент избранных глав")
                    navController.navigate(R.id.favoriteFragment)
                    true
                }

                R.id.action_toggle_images -> {
                    val currently = prefs.getBoolean("generate_images_enabled", false)
                    val next = !currently
                    prefs.edit { putBoolean("generate_images_enabled", next) }
                    Log.d(
                        "AppActivity",
                        "==> Глобальный режим генерации картинок изменен на: $next"
                    )
                    menuItem.isChecked = next
                    true
                }

                // 🔥 ОБРАБОТЧИК ДЛЯ КНОПКИ ОЧИСТКИ В ВАШЕМ AppActivity:
                999111 -> {
                    // 1. Автоматически переводим флаг генерации в false
                    prefs.edit { putBoolean("generate_images_enabled", false) }

                    // Обновляем визуальное состояние галочки генерации, если она видна в меню
                    menu.findItem(com.example.audiobible.R.id.action_toggle_images)?.isChecked =
                        false

                    lifecycleScope.launch(Dispatchers.IO) {
                        // 2. Стираем файлы с диска
                        val success = ImageGenerator.clearImageCache(this@AppActivity)

                        withContext(Dispatchers.Main) {
                            if (success) {
                                Log.d(
                                    "AppActivity",
                                    "==> Кэш стерт, флаг сброшен в false. Перезапускаем экран..."
                                )

                                // 3. Перезапускаем текущий экран навигации, чтобы адаптер полностью обновился!
                                val currentDestination = navController.currentDestination?.id
                                if (currentDestination != null) {
                                    navController.popBackStack()
                                    navController.navigate(currentDestination)
                                }
                            }
                        }
                    }
                    true
                }

                else -> false
            }
        }
        popup.show()

    }

    // Запрос разрешений на отправку уведомлений для Android 13+
    private fun requestNotificationsPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val permission = Manifest.permission.POST_NOTIFICATIONS
        if (checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) return
        requestPermissions(arrayOf(permission), 1)
    }
}
