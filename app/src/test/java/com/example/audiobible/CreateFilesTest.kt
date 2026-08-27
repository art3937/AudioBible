import java.io.File
import org.junit.Test
import java.util.Locale

class BibleSystemSpellChecker {

    @Test
    fun fixBibleLocallyWithSystemDict() {
        val targetFolder = File("C:/Users/Admin/AndroidStudioProjects/AudioBible/app/src/main/assets/bible_data")

        val appData = System.getenv("APPDATA")
        val winDictFolder = File("$appData\\Microsoft\\Spelling\\ru-RU")
        val systemDictFile = File(winDictFolder, "default.dic")

        println("=========================================================")
        println("ЗАПУСК АВТОНОМНОГО СВЕРХЗРЯЧЕГО ИСПРАВЛЕНИЯ СТЫКОВ")
        println("=========================================================")

        val validWords = HashSet<String>()

        if (systemDictFile.exists()) {
            systemDictFile.readLines(Charsets.UTF_16).forEach { word ->
                val clean = word.trim().lowercase(Locale.ROOT)
                if (clean.isNotEmpty() && !clean.startsWith("#")) {
                    validWords.add(clean)
                }
            }
        }

        // Базовый список заведомо правильных слов
        val coreRussianWords = listOf(
            "была", "были", "было", "был", "господи", "господь", "которое", "которые",
            "земля", "небо", "человек", "брат", "смерть", "жизнь", "слово", "отец", "сын",
            "лучше", "придет", "говорю", "вострубит", "они", "она", "он", "и", "а",
            "вас", "вам", "нас", "нам", "все", "всё", "мне", "меня", "тебе", "тебя",
            "отечестве", "доме", "между", "пред", "чрез", "сквозь", "меж", "близ",
            "потопа", "родились", "силен", "народы", "народ",
            "один" // Добавили основу для фикса "одинне" -> "один не"
        )
        validWords.addAll(coreRussianWords)

        // Триггеры для левой части
        val stickyTriggers = listOf(
            "чтобы", "потому", "всякий", "изгнанником", "ибо", "кто", "что",
            "он", "она", "они", "вечный", "и", "Мною", "у", "в"
        )

        // Триггеры для правой части (когда короткое слово прилипло в конце)
        val trailingTriggers = listOf("на", "за", "по", "из", "до", "то", "не")

        val russianVowels = setOf('а', 'е', 'ё', 'и', 'о', 'у', 'ы', 'э', 'ю', 'я')

        var totalFixesMade = 0

        if (!targetFolder.exists() || !targetFolder.isDirectory) {
            println("=== ОШИБКА: Папка assets/bible_data не найдена! ===")
            return
        }

        targetFolder.walkTopDown().filter { it.isFile && it.extension == "txt" }.forEach { file ->
            val lines = file.readLines(Charsets.UTF_8)
            val updatedLines = mutableListOf<String>()
            var fileChanged = false

            lines.forEach { line ->
                if (line.isBlank()) {
                    updatedLines.add(line)
                    return@forEach
                }

                val tokens = line.split(Regex("(?<=[^а-яА-ЯёЁ0-9-])|(?=[^а-яА-ЯёЁ0-9-])")).toMutableList()
                var lineChanged = false

                for (i in tokens.indices) {
                    val token = tokens[i]

                    if (token.matches(Regex("[а-яА-ЯёЁ]+"))) {
                        val lowerToken = token.lowercase(Locale.ROOT)

                        if (validWords.contains(lowerToken)) continue

                        var tokenFixed = false

                        // ЭТАП 1: Проверка по начальным триггерам
                        for (trigger in stickyTriggers) {
                            val lowerTrigger = trigger.lowercase(Locale.ROOT)

                            if (lowerToken.startsWith(lowerTrigger) && lowerToken.length > lowerTrigger.length) {
                                val remainder = lowerToken.substring(lowerTrigger.length)
                                val minRemainderLength = if (lowerTrigger == "и") 3 else 2

                                if (remainder.length >= minRemainderLength && validWords.contains(remainder)) {
                                    val originalFirstPart = token.substring(0, trigger.length)
                                    val originalSecondPart = token.substring(trigger.length)

                                    if (remainder.matches(Regex("был[аои]?")) && (lowerTrigger == "в" || lowerTrigger == "у")) continue

                                    if (lowerTrigger == "в" || lowerTrigger == "у") {
                                        val firstCharOfRemainder = remainder.getOrNull(0)
                                        if (firstCharOfRemainder != null && russianVowels.contains(firstCharOfRemainder)) {
                                            if (originalSecondPart.getOrNull(0)?.isLowerCase() == true) continue
                                        }
                                    }
                                    if (lowerTrigger == "и") {
                                        if (token.startsWith("И") && originalSecondPart.all { it.isLowerCase() }) continue
                                        if (token.startsWith("И") && originalSecondPart.getOrNull(0)?.isUpperCase() == true && !token.startsWith("и")) continue
                                    }

                                    tokens[i] = "$originalFirstPart $originalSecondPart"
                                    println("[ИСПРАВЛЕНО] в файле ${file.name}: '$token' -> '${tokens[i]}'")
                                    totalFixesMade++
                                    lineChanged = true
                                    tokenFixed = true
                                    break
                                }
                            }
                        }

                        // ЭТАП 2: Проверка по КОНЕЧНЫМ триггерам (силенна -> силен на, одинне -> один не)
                        if (!tokenFixed) {
                            for (trigger in trailingTriggers) {
                                if (lowerToken.endsWith(trigger) && lowerToken.length > trigger.length) {
                                    val stem = lowerToken.substring(0, lowerToken.length - trigger.length)

                                    if (validWords.contains(stem)) {
                                        val originalFirstPart = token.substring(0, token.length - trigger.length)
                                        val originalSecondPart = token.substring(token.length - trigger.length)

                                        if (trigger == "то" && !stem.matches(Regex("кто|что|чей|как|где|куда"))) continue

                                        tokens[i] = "$originalFirstPart $originalSecondPart"
                                        println("[ИСПРАВЛЕНО КОНЦЕВЫМ ТРИГГЕРОМ] в файле ${file.name}: '$token' -> '${tokens[i]}'")
                                        totalFixesMade++
                                        lineChanged = true
                                        tokenFixed = true
                                        break
                                    }
                                }
                            }
                        }

                        // ЭТАП 3: СВОБОДНЫЙ ПОИСК СТЫКОВ + МОРФОЛОГИЧЕСКИЙ АНАЛИЗ ГЛАГОЛОВ
                        if (!tokenFixed && lowerToken.length >= 7) {
                            for (cutIndex in 4 until lowerToken.length - 2) {
                                val leftPart = lowerToken.substring(0, cutIndex)
                                val rightPart = lowerToken.substring(cutIndex)

                                val exactMatch = validWords.contains(leftPart) && validWords.contains(rightPart)

                                val isVerbEnding = leftPart.endsWith("лись") || leftPart.endsWith("лся") ||
                                        leftPart.endsWith("ли") || leftPart.endsWith("ла") ||
                                        leftPart.endsWith("ло") || leftPart.endsWith("ли")
                                val verbMatch = isVerbEnding && leftPart.length >= 6 && validWords.contains(rightPart)

                                if (exactMatch || verbMatch) {
                                    val originalFirstPart = token.substring(0, cutIndex)
                                    val originalSecondPart = token.substring(cutIndex)

                                    tokens[i] = "$originalFirstPart $originalSecondPart"
                                    println("[ИСПРАВЛЕНО СВОБОДНЫМ ПОИСКОМ] в файле ${file.name}: '$token' -> '${tokens[i]}'")
                                    totalFixesMade++
                                    lineChanged = true
                                    break
                                }
                            }
                        }
                    }
                }

                if (lineChanged) {
                    updatedLines.add(tokens.joinToString(""))
                    fileChanged = true
                } else {
                    updatedLines.add(line)
                }
            }

            if (fileChanged) {
                file.writeText(updatedLines.joinToString("\n"), Charsets.UTF_8)
            }
        }

        println("=========================================================")
        println("ОБРАБОТКА ЗАВЕРШЕНА. Всего исправлено склеек: $totalFixesMade")
        println("=========================================================")
    }
}
