package com.example.audiobible.repository

import android.content.Context
import com.example.audiobible.dto.Book
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BibleRepository @Inject constructor() {

    fun getTestBooks(): List<Book> {
        return listOf(
            // ==========================================
            // === ВЕТХИЙ ЗАВЕТ ===
            // ==========================================

            // Пятикнижие Моисея (Благородный глубокий синий)
            Book(id = 1, name = "Бытие", folderName = "genesis", totalChapters = 50, backgroundColor = "#1A365D"),
            Book(id = 2, name = "Исход", folderName = "exodus", totalChapters = 40, backgroundColor = "#1A365D"),
            Book(id = 3, name = "Левит", folderName = "leviticus", totalChapters = 27, backgroundColor = "#1A365D"),
            Book(id = 4, name = "Числа", folderName = "numbers", totalChapters = 36, backgroundColor = "#1A365D"),
            Book(id = 5, name = "Второзаконие", folderName = "deuteronomy", totalChapters = 34, backgroundColor = "#1A365D"),

            // Исторические книги (Приглушенный сине-стальной)
            Book(id = 6, name = "Книга Иисуса Навина", folderName = "joshua", totalChapters = 24, backgroundColor = "#2C5282"),
            Book(id = 7, name = "Книга Судей Израилевых", folderName = "judges", totalChapters = 21, backgroundColor = "#2C5282"),
            Book(id = 8, name = "Книга Руфи", folderName = "ruth", totalChapters = 4, backgroundColor = "#2C5282"),
            Book(id = 9, name = "Первая книга Царств", folderName = "samuel1", totalChapters = 31, backgroundColor = "#2C5282"),
            Book(id = 10, name = "Вторая книга Царств", folderName = "samuel2", totalChapters = 24, backgroundColor = "#2C5282"),
            Book(id = 11, name = "Третья книга Царств", folderName = "kings1", totalChapters = 22, backgroundColor = "#2C5282"),
            Book(id = 12, name = "Четвертая книга Царств", folderName = "kings2", totalChapters = 25, backgroundColor = "#2C5282"),
            Book(id = 13, name = "Первая книга Паралипоменон", folderName = "chronicles1", totalChapters = 29, backgroundColor = "#2C5282"),
            Book(id = 14, name = "Вторая книга Паралипоменон", folderName = "chronicles2", totalChapters = 36, backgroundColor = "#2C5282"),
            Book(id = 15, name = "Первая книга Ездры", folderName = "ezra", totalChapters = 10, backgroundColor = "#2C5282"),
            Book(id = 16, name = "Книга Неемии", folderName = "nehemiah", totalChapters = 13, backgroundColor = "#2C5282"),
            Book(id = 17, name = "Книга Есфири", folderName = "esther", totalChapters = 10, backgroundColor = "#2C5282"),

            // Учительные / Поэтические книги (Темно-изумрудный зеленый)
            Book(id = 18, name = "Книга Иова", folderName = "job", totalChapters = 42, backgroundColor = "#1A4D3E"),
            Book(id = 19, name = "Псалтирь", folderName = "psalms", totalChapters = 150, backgroundColor = "#1A4D3E"),
            Book(id = 20, name = "Книга Притчей Соломоновых", folderName = "proverbs", totalChapters = 31, backgroundColor = "#1A4D3E"),
            Book(id = 21, name = "Книга Екклесиаста, или Проповедника", folderName = "ecclesiastes", totalChapters = 12, backgroundColor = "#1A4D3E"),
            Book(id = 22, name = "Книга Песни Песней Соломона", folderName = "song", totalChapters = 8, backgroundColor = "#1A4D3E"),

            // Великие пророки (Глубокий бордовый / винный)
            Book(id = 23, name = "Книга пророка Исаии", folderName = "isaiah", totalChapters = 66, backgroundColor = "#4C1D24"),
            Book(id = 24, name = "Книга пророка Иеремии", folderName = "jeremiah", totalChapters = 52, backgroundColor = "#4C1D24"),
            Book(id = 25, name = "Плач Иеремии", folderName = "lamentations", totalChapters = 5, backgroundColor = "#4C1D24"),
            Book(id = 26, name = "Книга пророка Иезекииля", folderName = "ezekiel", totalChapters = 48, backgroundColor = "#4C1D24"),
            Book(id = 27, name = "Книга пророка Даниила", folderName = "daniel", totalChapters = 12, backgroundColor = "#4C1D24"),

            // Малые пророки (Тёмно-сливовый)
            Book(id = 28, name = "Книга пророка Осии", folderName = "hosea", totalChapters = 14, backgroundColor = "#3C1F42"),
            Book(id = 29, name = "Книга пророка Иоиля", folderName = "joel", totalChapters = 3, backgroundColor = "#3C1F42"),
            Book(id = 30, name = "Книга пророка Амоса", folderName = "amos", totalChapters = 9, backgroundColor = "#3C1F42"),
            Book(id = 31, name = "Книга пророка Авдия", folderName = "obadiah", totalChapters = 1, backgroundColor = "#3C1F42"),
            Book(id = 32, name = "Книга пророка Ионы", folderName = "jonah", totalChapters = 4, backgroundColor = "#3C1F42"),
            Book(id = 33, name = "Книга пророка Михея", folderName = "micah", totalChapters = 7, backgroundColor = "#3C1F42"),
            Book(id = 34, name = "Книга пророка Наума", folderName = "nahum", totalChapters = 3, backgroundColor = "#3C1F42"),
            Book(id = 35, name = "Книга пророка Аввакума", folderName = "habakkuk", totalChapters = 3, backgroundColor = "#3C1F42"),
            Book(id = 36, name = "Книга пророка Софонии", folderName = "zephaniah", totalChapters = 3, backgroundColor = "#3C1F42"),
            Book(id = 37, name = "Книга пророка Аггея", folderName = "haggai", totalChapters = 2, backgroundColor = "#3C1F42"),
            Book(id = 38, name = "Книга пророка Захарии", folderName = "zechariah", totalChapters = 14, backgroundColor = "#3C1F42"),
            Book(id = 39, name = "Книга пророка Малахии", folderName = "malachi", totalChapters = 4, backgroundColor = "#3C1F42"),

            // ==========================================
            // === НОВЫЙ ЗАВЕТ ===
            // ==========================================

            // Евангелия и Деяния (Благородный тёмно-оливковый)
            Book(id = 40, name = "От Матфея святое благовествование", folderName = "matthew", totalChapters = 28, backgroundColor = "#3E4A24"),
            Book(id = 41, name = "От Марка святое благовествование", folderName = "mark", totalChapters = 16, backgroundColor = "#3E4A24"),
            Book(id = 42, name = "От Луки святое благовествование", folderName = "luke", totalChapters = 24, backgroundColor = "#3E4A24"),
            Book(id = 43, name = "От Иоанна святое благовествование", folderName = "john", totalChapters = 21, backgroundColor = "#3E4A24"),
            Book(id = 44, name = "Деяния святых апостолов", folderName = "acts", totalChapters = 28, backgroundColor = "#4A522A"),

            // Соборные послания (Приглушенный фиолетовый)
            Book(id = 45, name = "Соборное послание святого апостола Иакова", folderName = "james", totalChapters = 5, backgroundColor = "#432E54"),
            Book(id = 46, name = "Первое соборное послание святого апостола Петра", folderName = "peter1", totalChapters = 5, backgroundColor = "#432E54"),
            Book(id = 47, name = "Второе соборное послание святого апостола Петра", folderName = "peter2", totalChapters = 3, backgroundColor = "#432E54"),
            Book(id = 48, name = "Первое соборное послание святого апостола Иоанна", folderName = "john1", totalChapters = 5, backgroundColor = "#432E54"),
            Book(id = 49, name = "Второе соборное послание святого апостола Иоанна", folderName = "john2", totalChapters = 1, backgroundColor = "#432E54"),
            Book(id = 50, name = "Третье соборное послание святого апостола Иоанна", folderName = "john3", totalChapters = 1, backgroundColor = "#432E54"),
            Book(id = 51, name = "Соборное послание святого апостола Иуды", folderName = "jude", totalChapters = 1, backgroundColor = "#432E54"),

            // Послания апостола Павла (Строгий тёмно-серый шифер)
            Book(id = 52, name = "Послание к Римлянам святого апостола Павла", folderName = "romans", totalChapters = 16, backgroundColor = "#2D3748"),
            Book(id = 53, name = "Первое послание к Коринфянам святого апостола Павла", folderName = "corinthians1", totalChapters = 16, backgroundColor = "#2D3748"),
            Book(id = 54, name = "Второе послание к Коринфянам святого апостола Павла", folderName = "corinthians2", totalChapters = 13, backgroundColor = "#2D3748"),
            Book(id = 55, name = "Послание к Галатам святого апостола Павла", folderName = "galatians", totalChapters = 6, backgroundColor = "#2D3748"),
            Book(id = 56, name = "Послание к Ефесянам святого апостола Павла", folderName = "ephesians", totalChapters = 6, backgroundColor = "#2D3748"),
            Book(id = 57, name = "Послание к Филиппийцам святого апостола Павла", folderName = "philippians", totalChapters = 4, backgroundColor = "#2D3748"),
            Book(id = 58, name = "Послание к Колоссянам святого апостола Павла", folderName = "colossians", totalChapters = 4, backgroundColor = "#2D3748"),
            Book(id = 59, name = "Первое послание к Фессалоникийцам (Солунянам) святого апостола Павла", folderName = "thessalonians1", totalChapters = 5, backgroundColor = "#2D3748"),
            Book(id = 60, name = "Второе послание к Фессалоникийцам (Солунянам) святого апостола Павла", folderName = "thessalonians2", totalChapters = 3, backgroundColor = "#2D3748"),
            Book(id = 61, name = "Первое послание к Тимофею святого апостола Павла", folderName = "timothy1", totalChapters = 6, backgroundColor = "#2D3748"),
            Book(id = 62, name = "Второе послание к Тимофею святого апостола Павла", folderName = "timothy2", totalChapters = 4, backgroundColor = "#2D3748"),
            Book(id = 63, name = "Послание к Титу святого апостола Павла", folderName = "titus", totalChapters = 3, backgroundColor = "#2D3748"),
            Book(id = 64, name = "Послание к Филимону святого апостола Павла", folderName = "philemon", totalChapters = 1, backgroundColor = "#2D3748"),
            Book(id = 65, name = "Послание к Евреям святого апостола Павла", folderName = "hebrews", totalChapters = 13, backgroundColor = "#2D3748"),

            // Пророческая книга (Антрацитовый глубокий черный)


            // Пророческая книга (Антрацитовый темный)
            Book(id = 66, name = "Откровение святого Иоанна Богослова (Апокалипсис)", folderName = "revelation", totalChapters = 22, backgroundColor = "#1A202C")
        )
    }
}