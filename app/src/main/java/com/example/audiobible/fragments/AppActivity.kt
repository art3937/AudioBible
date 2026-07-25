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
import ru.netology.mediapleer2.TrackViewModel


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
        val navController = (supportFragmentManager.findFragmentById(binding.navMain.id) as NavHostFragment).navController

        // 4. Связываем Toolbar с навигацией (чтобы автоматически менялись заголовки)
        val appBarConfiguration = AppBarConfiguration(navController.graph)
        binding.toolbar.setupWithNavController(navController, appBarConfiguration)

        // 5. Запрашиваем разрешения
        requestNotificationsPermission()

        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        // 6. Настраиваем меню
        addMenuProvider(
            object : MenuProvider {
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
            }
        )

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





//    fun generateBibleTxtFiles(context: Context) {
//        // Словарь: префикс книги -> количество глав
//        val bibleBooks = mapOf(
//            "genesis_" to 50, "exodus_" to 40, "leviticus_" to 27, "numbers_" to 36, "deuteronomy_" to 34,
//            "joshua_" to 24, "judges_" to 21, "ruth_" to 4, "samuel1_" to 31, "samuel2_" to 24,
//            "kings1_" to 22, "kings2_" to 25, "chronicles1_" to 29, "chronicles2_" to 36, "ezra_" to 10,
//            "nehemiah_" to 13, "esther_" to 10, "job_" to 42, "psalms_" to 150, "proverbs_" to 31,
//            "ecclesiastes_" to 12, "song_" to 8, "isaiah_" to 66, "jeremiah_" to 52, "lamentations_" to 5,
//            "ezekiel_" to 48, "daniel_" to 12, "hosea_" to 14, "joel_" to 3, "amos_" to 9,
//            "obadiah_" to 1, "jonah_" to 4, "micah_" to 7, "nahum_" to 3, "habakkuk_" to 3,
//            "zephaniah_" to 3, "haggai_" to 2, "zechariah_" to 14, "malachi_" to 4, "matthew_" to 28,
//            "mark_" to 16, "luke_" to 24, "john_" to 21, "acts_" to 28, "james_" to 5,
//            "peter1_" to 5, "peter2_" to 3, "john1_" to 5, "john2_" to 1, "john3_" to 1,
//            "jude_" to 1, "romans_" to 16, "corinthians1_" to 16, "corinthians2_" to 13, "galatians_" to 6,
//            "ephesians_" to 6, "philippians_" to 4, "colossians_" to 4, "thessalonians1_" to 5, "thessalonians2_" to 3,
//            "timothy1_" to 6, "timothy2_" to 4, "titus_" to 3, "philemon_" to 1, "hebrews_" to 13,
//            "revelation_" to 22
//        )

//        // Создаем папку bible_txt в файлах приложения
//        val outputDir = File(context.filesDir, "bible_txt")
//        if (!outputDir.exists()) {
//            outputDir.mkdirs()
//        }
//
//        // Запускаем генерацию файлов
//        bibleBooks.forEach { (prefix, chaptersCount) ->
//            for (chapterNumber in 1..chaptersCount) {
//                val fileName = "$prefix$chapterNumber.txt" // Можно заменить .txt на .mp3
//                val file = File(outputDir, fileName)
//
//                if (!file.exists()) {
//                    file.createNewFile() // Создаем пустой файл
//                }
//            }
//        }
//
//        // Выводим в лог путь к папке, чтобы вы знали, где забрать файлы
//        println("Успешно создано 1189 файлов в папке: ${outputDir.absolutePath}")
    }

