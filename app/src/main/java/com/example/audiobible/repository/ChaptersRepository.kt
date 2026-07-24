package com.example.audiobible.repository // Проверьте имя вашего пакета

import android.content.Context
import com.example.audiobible.R
import com.example.audiobible.dto.AudioItem
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton // Если нужен один экземпляр на всё приложение
class ChaptersRepository @Inject constructor() {

    fun getChaptersForBook(context: Context, bookId: Int): List<AudioItem> {

        // 1. ПОЛНЫЙ КАНОНИЧЕСКИЙ СПИСОК КОЛИЧЕСТВА ГЛАВ
        val chaptersCount = when (bookId) {
            // Ветхий Завет (1 - 39)
            1 -> 50  // Бытие
            2 -> 40  // Исход
            3 -> 27  // Левит
            4 -> 36  // Числа
            5 -> 34  // Второзаконие
            6 -> 24  // Иисус Навин
            7 -> 21  // Судьи
            8 -> 4   // Руфь
            9 -> 31  // 1 Царств
            10 -> 24 // 2 Царств
            11 -> 22 // 3 Царств
            12 -> 25 // 4 Царств
            13 -> 29 // 1 Паралипоменон
            14 -> 36 // 2 Паралипоменон
            15 -> 10 // Ездра
            16 -> 13 // Неемия
            17 -> 10 // Есфирь
            18 -> 42 // Иов
            19 -> 150 // Псалтирь
            20 -> 31 // Притчи
            21 -> 12 // Екклесиаст
            22 -> 8  // Песнь Песней
            23 -> 66 // Исаия
            24 -> 52 // Иеремия
            25 -> 5  // Плач Иеремии
            26 -> 48 // Иезекииль
            27 -> 12 // Даниил
            28 -> 14 // Осия
            29 -> 3  // Иоиль
            30 -> 9  // Амос
            31 -> 1  // Авдий
            32 -> 4  // Иона
            33 -> 7  // Михей
            34 -> 3  // Наум
            35 -> 3  // Аввакум
            36 -> 3  // Софония
            37 -> 2  // Аггей
            38 -> 14 // Захария
            39 -> 4  // Малахия

            // Новый Завет (40 - 66)
            40 -> 28 // Матфея
            41 -> 16 // Марка
            42 -> 24 // Луки
            43 -> 21 // Иоанна
            44 -> 28 // Деяния
            45 -> 5  // Иакова
            46 -> 5  // 1 Петра
            47 -> 3  // 2 Петра
            48 -> 5  // 1 Иоанна
            49 -> 1  // 2 Иоанна
            50 -> 1  // 3 Иоанна
            51 -> 1  // Иуда
            52 -> 16 // Римлянам
            53 -> 16 // 1 Коринфянам
            54 -> 13 // 2 Коринфянам
            55 -> 6  // Галатам
            56 -> 6  // Ефесянам
            57 -> 4  // Филиппийцам
            58 -> 4  // Колоссянам
            59 -> 5  // 1 Фессалоникийцам
            60 -> 3  // 2 Фессалоникийцам
            61 -> 6  // 1 Тимофею
            62 -> 4  // 2 Тимофею
            63 -> 3  // Титу
            64 -> 1  // Филимону
            65 -> 13 // Евреям
            66 -> 22 // Откровение

            else -> 1 // Защитная заглушка
        }

        // 2. УНИКАЛЬНЫЕ ПРЕФИКСЫ ДЛЯ НАЗВАНИЙ АУДИОФАЙЛОВ
        val filePrefix = when (bookId) {
            // Ветхий Завет
            1 -> "genesis_"
            2 -> "exodus_"
            3 -> "leviticus_"
            4 -> "numbers_"
            5 -> "deuteronomy_"
            6 -> "joshua_"
            7 -> "judges_"
            8 -> "ruth_"
            9 -> "samuel1_"
            10 -> "samuel2_"
            11 -> "kings1_"
            12 -> "kings2_"
            13 -> "chronicles1_"
            14 -> "chronicles2_"
            15 -> "ezra_"
            16 -> "nehemiah_"
            17 -> "esther_"
            18 -> "job_"
            19 -> "psalms_"
            20 -> "proverbs_"
            21 -> "ecclesiastes_"
            22 -> "song_"
            23 -> "isaiah_"
            24 -> "jeremiah_"
            25 -> "lamentations_"
            26 -> "ezekiel_"
            27 -> "daniel_"
            28 -> "hosea_"
            29 -> "joel_"
            30 -> "amos_"
            31 -> "obadiah_"
            32 -> "jonah_"
            33 -> "micah_"
            34 -> "nahum_"
            35 -> "habakkuk_"
            36 -> "zephaniah_"
            37 -> "haggai_"
            38 -> "zechariah_"
            39 -> "malachi_"

            // Новый Завет
            40 -> "matthew_"
            41 -> "mark_"
            42 -> "luke_"
            43 -> "john_"
            44 -> "acts_"
            45 -> "james_"
            46 -> "peter1_"
            47 -> "peter2_"
            48 -> "john1_"
            49 -> "john2_"
            50 -> "john3_"
            51 -> "jude_"
            52 -> "romans_"
            53 -> "corinthians1_"
            54 -> "corinthians2_"
            55 -> "galatians_"
            56 -> "ephesians_"
            57 -> "philippians_"
            58 -> "colossians_"
            59 -> "thessalonians1_"
            60 -> "thessalonians2_"
            61 -> "timothy1_"
            62 -> "timothy2_"
            63 -> "titus_"
            64 -> "philemon_"
            65 -> "hebrews_"
            66 -> "revelation_"

            else -> "genesis_"
        }

        // 3. СБОРКА И ПРОВЕРКА РЕСУРСОВ RAW
        return (1..chaptersCount).map { chapterNumber ->
            val fileName = "$filePrefix$chapterNumber"

            val resId = context.resources.getIdentifier(
                fileName,
                "raw",
                context.packageName
            )

            // Если конкретного файла в res/raw ещё нет, ставим Бытие 1 как заглушку от крашей
            val finalAudioId = if (resId != 0) resId else R.raw.genesis_1

            AudioItem(
                id = (bookId * 1000) + chapterNumber,
                name = "Глава $chapterNumber",
                audioRawId = finalAudioId,
                isPlaying = false
            )
        }
    }
}
