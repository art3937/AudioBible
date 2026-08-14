package com.example.audiobible.fragments

import android.Manifest
import android.content.Intent
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
import android.view.ContextThemeWrapper
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.navigation.fragment.NavHostFragment
import com.example.audiobible.R
import com.example.audiobible.databinding.ActivityAppBinding
import dagger.hilt.android.AndroidEntryPoint
import ru.netology.mediapleer2.TrackViewModel

@AndroidEntryPoint
class AppActivity : AppCompatActivity() {

    private val viewModel: TrackViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val binding = ActivityAppBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Получаем навигационный контроллер по вашему ID nav_main
        val navController =
            (supportFragmentManager.findFragmentById(binding.navMain.id) as NavHostFragment).navController

        requestNotificationsPermission()

        // Функция безопасного выхода назад с остановкой плеера
        fun handleBackNavigation() {
            // Если мы находимся на экране плеера глав (fragmentChapter2)
            // перед выходом принудительно ставим воспроизведение на паузу
            if (navController.currentDestination?.id == R.id.fragmentChapter2) {
                Log.d("AppActivity", "==> Выходим из главы: ставим плеер на паузу")
                viewModel.pauseTrack() // Вызываем метод паузы нашей ViewModel!
            }
            // Выполняем физический шаг назад в навигации фрагментов
            navController.navigateUp()
        }

        // 1. КЛИК ПО ПАРЯЩЕЙ СТРЕЛОЧКЕ НАЗАД (наша кнопка в углу)
        binding.btnBack.setOnClickListener {
            handleBackNavigation()
        }


        binding.fabMenu.setOnClickListener { view ->
            // Передаем саму кнопку (view) в качестве якоря и наш navController
            showFloatingMenu(view, navController)
        }
        // 2. СИСТЕМНАЯ КНОПКА НАЗАД (жест телефона или треугольник внизу экрана)
        // Регистрируем коллбэк для перехвата стандартного системного Android-назад
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Если есть куда возвращаться внутри приложения — идем назад по нашей логике с паузой
                if (navController.previousBackStackEntry != null) {
                    handleBackNavigation()
                } else {
                    // Если мы на самом первом экране — закрываем приложение полностью
                    finish()
                }
            }
        })

        // 3. СЛУШАТЕЛЬ НАВИГАЦИИ (управляет видимостью парящей стрелочки)
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

    // Метод создания и отображения стилизованного парящего меню
    private fun showFloatingMenu(anchorView: View, navController: androidx.navigation.NavController) {
        // Оборачиваем контекст в нашу тему для PopupMenu из themes.xml
        val contextWrapper = ContextThemeWrapper(this, R.style.Theme_AudioBible_PopupWrapper)
        val popup = PopupMenu(contextWrapper, anchorView)

        // Накатываем ваш существующий файл разметки меню
        popup.menuInflater.inflate(R.menu.auth_menu, popup.menu)

        // ЖЕЛЕЗОБЕТОННАЯ ПРОГРАММНАЯ ПОКРАСКА ТЕКСТА МЕНЮ (чтобы ничего не сливалось)
        val menu = popup.menu
        for (i in 0 until menu.size()) {
            val menuItem = menu.getItem(i)
            val rawTitle = menuItem.title.toString()
            val spannableTitle = SpannableString(rawTitle)

            // Красим текст в ярко-белый цвет
            spannableTitle.setSpan(
                ForegroundColorSpan(Color.WHITE),
                0,
                spannableTitle.length,
                Spanned.SPAN_INCLUSIVE_INCLUSIVE
            )

            // Делаем текст жирным для максимальной контрастности на полупрозрачном фоне
            spannableTitle.setSpan(
                StyleSpan(Typeface.BOLD),
                0,
                spannableTitle.length,
                Spanned.SPAN_INCLUSIVE_INCLUSIVE
            )

            menuItem.title = spannableTitle
        }

        // Обработка нажатий на пункты меню
        // Внутри метода showFloatingMenu в AppActivity.kt
        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.signin -> true

                // ОБРАБОТКА КЛИКА ПО НАШЕМУ НОВОМУ ПУНКТУ
                R.id.favorites -> {
                    Log.d("AppActivity", "==> Переход на фрагмент избранных глав")

                 navController.navigate(R.id.favoriteFragment)
                    true
                }

                R.id.signup -> {
                    navController.navigate(R.id.action_feedFragment_to_fragmentChapter2)
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
