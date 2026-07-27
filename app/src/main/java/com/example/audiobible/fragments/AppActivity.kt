package com.example.audiobible.fragments

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.MenuProvider
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import com.example.audiobible.R
import com.example.audiobible.databinding.ActivityAppBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.netology.mediapleer2.TrackViewModel
import java.io.File


@AndroidEntryPoint
class AppActivity : AppCompatActivity() {

    private val viewModel: TrackViewModel by viewModels()
    private var isUserTrackingSeekBar = false // Флаг: держит ли пользователь ползунок пальцем


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Инициализируем binding и устанавливаем контент
        val binding = ActivityAppBinding.inflate(layoutInflater)
        setContentView(binding.root)
        //______________________
        //   generateBibleTxtFiles(this)// вот это прикол
        //___________________
        // 2. ВОТ ЭТА СТРОКА ОБЯЗАТЕЛЬНА: Назначаем наш Toolbar главным для Activity
        setSupportActionBar(binding.toolbar)

        // 3. Находим навигационный контроллер
        val navController =
            (supportFragmentManager.findFragmentById(binding.navMain.id) as NavHostFragment).navController

        // 4. Связываем Toolbar с навигацией (чтобы автоматически менялись заголовки)
        val appBarConfiguration = AppBarConfiguration(navController.graph)
        binding.toolbar.setupWithNavController(navController, appBarConfiguration)


        //   generateBibleStructure()
        // 5. Запрашиваем разрешения
        requestNotificationsPermission()

        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        // 6. Настраиваем меню
        addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.auth_menu, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.signin -> {
                        // navController.navigate(R.id.action_feedFragment_to_fragmentSignIn)
                        true
                    }

                    R.id.signup -> {
                        navController.navigate(R.id.action_feedFragment_to_fragmentChapter2)
                        true
                    }

                    R.id.logout -> {
                        // appAuth.removeAuth()
                        true
                    }

                    else -> false
                }
            }
        })

        if (savedInstanceState == null) {
            viewModel.restoreLastGlobalTrack()
        }
        // Мини-плеер перенесён в FragmentChapter — отображение и обработка пользовательских действий теперь выполняются в FragmentChapter.kt

    }

    private fun requestNotificationsPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val permission = Manifest.permission.POST_NOTIFICATIONS
        if (checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) return
        requestPermissions(arrayOf(permission), 1)
    }


//    suspend fun generateBibleStructure() = withContext(Dispatchers.IO) {
//        // Карта: имя книги -> точное количество глав/файлов
//        val bibleBooks = mapOf(
//            "genesis" to 50, "exodus" to 40, "leviticus" to 27, "numbers" to 36, "deuteronomy" to 34,
//            "joshua" to 24, "judges" to 21, "ruth" to 4, "samuel1" to 31, "samuel2" to 24,
//            "kings1" to 22, "kings2" to 25, "chronicles1" to 29, "chronicles2" to 36, "ezra" to 10,
//            "nehemiah" to 13, "esther" to 10, "job" to 42, "psalms" to 150, "proverbs" to 31,
//            "ecclesiastes" to 12, "song" to 8, "isaiah" to 66, "jeremiah" to 52, "lamentations" to 5,
//            "ezekiel" to 48, "daniel" to 12, "hosea" to 14, "joel" to 3, "amos" to 9,
//            "obadiah" to 1, "jonah" to 4, "micah" to 7, "nahum" to 3, "habakkuk" to 3,
//            "zephaniah" to 3, "haggai" to 2, "zechariah" to 14, "malachi" to 4, "matthew" to 28,
//            "mark" to 16, "luke" to 24, "john" to 21, "acts" to 28, "james" to 5,
//            "peter1" to 5, "peter2" to 3, "john1" to 5, "john2" to 1, "john3" to 1,
//            "jude" to 1, "romans" to 16, "corinthians1" to 16, "corinthians2" to 13, "galatians" to 6,
//            "ephesians" to 6, "philippians" to 4, "colossians" to 4, "thessalonians1" to 5, "thessalonians2" to 3,
//            "timothy1" to 6, "timothy2" to 4, "titus" to 3, "philemon" to 1, "hebrews" to 13,
//            "revelation" to 22
//        )
//
//        // Главная папка во внутренних файлах приложения
//        val baseDir = File(this@AppActivity.filesDir, "bible_data")
//        if (!baseDir.exists()) {
//            baseDir.mkdirs()
//        }
//
//        bibleBooks.forEach { (bookName, chaptersCount) ->
//            // 1. Создаем папку для конкретной книги (например: bible_data/genesis)
//            val bookFolder = File(baseDir, bookName)
//            if (!bookFolder.exists()) {
//                bookFolder.mkdirs()
//            }
//
//            // 2. Создаем файлы глав внутри этой папки (1.txt, 2.txt...)
//            for (chapterNumber in 1..chaptersCount) {
//                val fileName = "$chapterNumber.txt" // Если нужно для аудио, меняем на .mp3
//                val file = File(bookFolder, fileName)
//
//                if (!file.exists()) {
//                    try {
//                        file.createNewFile()
//                    } catch (e: Exception) {
//                        e.printStackTrace()
//                    }
//                }
//            }
//        }
//    }

}

