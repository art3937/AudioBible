package com.example.audiobible // Укажите ваш пакет

import org.junit.Test
import java.io.File

class CreateFilesTest {
    @Test
    fun clearRawFolder() {
        // Укажите абсолютный путь к вашей папке res/raw на компьютере
        val rawFolder = File("C:/Users/Admin/AndroidStudioProjects/AudioBible/app/src/main/res/raw")

        if (!rawFolder.exists() || !rawFolder.isDirectory) {
            println("=== ОШИБКА: Папка не найдена по указанному пути! ===")
            return
        }

        // Получаем список всех файлов в папке
        val files = rawFolder.listFiles()
        if (files == null || files.isEmpty()) {
            println("=== Папка уже пуста ===")
            return
        }

        var deletedCount = 0
        var skippedCount = 0

        for (file in files) {
            // ЗАЩИТА: Не удаляем реальные аудиофайлы (mp3), которые вы туда добавили вручную
            // Скрипт удалит только пустые сгенерированные заглушки .txt
            if (file.extension == "txt") {
                if (file.delete()) {
                    deletedCount++
                }
            } else {
                // Все mp3 файлы останутся нетронутыми
                skippedCount++
            }
        }

        println("=== ОЧИСТКА ЗАВЕРШЕНА ===")
        println("Удалено файлов-заглушек (.txt): $deletedCount")
        println("Сохранено реальных аудиофайлов: $skippedCount")
        println("Текущий путь: ${rawFolder.absolutePath}")
    }


//         создаем файлы
    @Test
    fun generateFiles() {
        val bibleBooks = mapOf(
            "genesis_" to 50, "exodus_" to 40, "leviticus_" to 27, "numbers_" to 36, "deuteronomy_" to 34,
            "joshua_" to 24, "judges_" to 21, "ruth_" to 4, "samuel1_" to 31, "samuel2_" to 24,
            "kings1_" to 22, "kings2_" to 25, "chronicles1_" to 29, "chronicles2_" to 36, "ezra_" to 10,
            "nehemiah_" to 13, "esther_" to 10, "job_" to 42, "psalms_" to 150, "proverbs_" to 31,
            "ecclesiastes_" to 12, "song_" to 8, "isaiah_" to 66, "jeremiah_" to 52, "lamentations_" to 5,
            "ezekiel_" to 48, "daniel_" to 12, "hosea_" to 14, "joel_" to 3, "amos_" to 9,
            "obadiah_" to 1, "jonah_" to 4, "micah_" to 7, "nahum_" to 3, "habakkuk_" to 3,
            "zephaniah_" to 3, "haggai_" to 2, "zechariah_" to 14, "malachi_" to 4, "matthew_" to 28,
            "mark_" to 16, "luke_" to 24, "john_" to 21, "acts_" to 28, "james_" to 5,
            "peter1_" to 5, "peter2_" to 3, "john1_" to 5, "john2_" to 1, "john3_" to 1,
            "jude_" to 1, "romans_" to 16, "corinthians1_" to 16, "corinthians2_" to 13, "galatians_" to 6,
            "ephesians_" to 6, "philippians_" to 4, "colossians_" to 4, "thessalonians1_" to 5, "thessalonians2_" to 3,
            "timothy1_" to 6, "timothy2_" to 4, "titus_" to 3, "philemon_" to 1, "hebrews_" to 13,
            "revelation_" to 22
        )

        // ВНИМАНИЕ: Замените этот путь на реальный путь к вашей папке res/raw на ПК!
        // Вы можете узнать его, нажав правой кнопкой на папку raw в студии -> Copy Path/Reference -> Absolute Path
        val rawFolder = File("C:/Users/Admin/AndroidStudioProjects/AudioBible/app/src/main/res/raw")

        if (!rawFolder.exists()) {
            rawFolder.mkdirs()
        }

        var count = 0
        bibleBooks.forEach { (prefix, chaptersCount) ->
            for (i in 1..chaptersCount) {
                // Создаем файлы с расширением .txt (или .mp3, если хотите обмануть плеер)
                val file = File(rawFolder, "$prefix$i.txt")
                if (file.createNewFile()) {
                    count++
                }
            }
        }

        println("=== УСПЕХ! Создано файлов: $count в папке ${rawFolder.absolutePath} ===")
    }
}
