package com.yourpackage // Укажите ваш пакет

import org.junit.Test
import java.io.File

class RenameAssetsTest {

    @Test
    fun renameAssetsInPlace() {
        // Указываем путь к папке, где лежат файлы
        val sourceDir = File("src/main/assets/exodus")

        if (!sourceDir.exists() || !sourceDir.isDirectory) {
            println("Ошибка: Папка не найдена по пути: ${sourceDir.absolutePath}")
            return
        }

        val files = sourceDir.listFiles()
        if (files.isNullOrEmpty()) {
            println("Папка пуста.")
            return
        }

        var successCount = 0

        for (file in files) {
            if (file.isDirectory) continue // Пропускаем папки

            val originalName = file.nameWithoutExtension
            val extension = file.extension.lowercase() // mp3

            // Проверяем, что имя файла — это число (1, 2, 3...)
            // И что файл еще НЕ переименован (на случай повторного запуска теста)
            if (originalName.toLongOrNull() != null && !originalName.startsWith("exodus_")) {

                // Новое имя в той же папке: genesis_1.mp3
                val newFileName = "exodus_$originalName.$extension"
                val targetFile = File(sourceDir, newFileName)

                // Переименовываем файл
                val isRenamed = file.renameTo(targetFile)

                if (isRenamed) {
                    println("Переименован: ${file.name} -> $newFileName")
                    successCount++
                } else {
                    println("Не удалось переименовать файл: ${file.name}")
                }
            } else {
                println("Пропущен файл (уже переименован или имя не число): ${file.name}")
            }
        }

        println("\nГотово! Успешно переименовано файлов: $successCount")
    }
}
