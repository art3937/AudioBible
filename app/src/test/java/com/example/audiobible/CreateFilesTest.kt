import java.io.File
import org.junit.Test

class BibleSafeFixer {

    @Test
    fun fixBiblePerfect() {
        val targetFolder = File("C:/Users/Admin/AndroidStudioProjects/AudioBible/app/src/main/assets/bible_data")

        println("=========================================================")
        println("ЗАПУСК БЕЗОПАСНОГО ИСПРАВЛЕНИЯ ТЕКСТА")
        println("=========================================================")

        if (!targetFolder.exists() || !targetFolder.isDirectory) {
            println("=== ОШИБКА: Папка assets/bible_data не найдена! ===")
            return
        }

        // КАРТА СТРОГИХ ЗАМЕН: Только эти склеенные слова будут разделены пробелом.
        // Никакие другие слова (вроде "была", "которое", "Господи") задеты не будут!
        val exactReplacements = mapOf(
            "ктовтретится" to "кто встретится",
            "Онвознесся" to "Он вознесся",
            "изгнанникоми" to "изгнанником и",
            "чтоони" to "что они",
            "чтоона" to "что она",
            "потомучтоони" to "потому что они",
            "потомучтоона" to "потому что она"
        )

        var totalFixes = 0

        targetFolder.walkTopDown().filter { it.isFile && it.extension == "txt" }.forEach { file ->
            val lines = file.readLines(Charsets.UTF_8)
            val fixedLines = mutableListOf<String>()
            var fileChanged = false

            lines.forEachIndexed { index, originalLine ->
                val lineNumber = index + 1
                var line = originalLine

                // 1. Стандартная чистка пробелов вокруг знаков препинания (Безопасно)
                line = line.replace(Regex("\\s+([.,;:?])"), "$1")
                line = line.replace(Regex("([.,;:!?])(?=[а-яёА-ЯЁ])"), "$1 ")

                // 2. Замена склеенных слов строго по нашей карте
                var lineHasExactFix = false
                for ((stickyWord, correctPhrase) in exactReplacements) {
                    // Ищем слово целиком, игнорируя регистр букв
                    val regex = Regex("\\b$stickyWord\\b", RegexOption.IGNORE_CASE)
                    if (regex.containsMatchIn(line)) {
                        line = line.replace(regex, correctPhrase)
                        lineHasExactFix = true
                    }
                }

                // 3. Убираем случайные двойные пробелы
                line = line.replace(Regex("[ ]{2,}"), " ")

                // Если строка изменилась, выводим подробный лог «Было / Стало»
                if (originalLine != line) {
                    println("[${file.parentFile.name.uppercase()}][Глава ${file.nameWithoutExtension}][Стих $lineNumber]")
                    println("   БЫЛО:  $originalLine")
                    println("   СТАЛО: $line")
                    fileChanged = true
                    totalFixes++
                }

                fixedLines.add(line)
            }

            // Перезаписываем файл только при наличии изменений
            if (fileChanged) {
                file.writeText(fixedLines.joinToString("\n"), Charsets.UTF_8)
            }
        }

        println("=========================================================")
        println("ГОТОВО! Всего точечно исправлено стихов: $totalFixes")
        println("=========================================================")
    }
}
