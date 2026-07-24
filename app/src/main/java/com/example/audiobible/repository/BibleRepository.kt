package com.example.audiobible.repository

import android.content.Context
import com.example.audiobible.dto.Book
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BibleRepository @Inject constructor() {

    fun getTestBooks(): List<Book> {
        return listOf(
            // === ВЕТХИЙ ЗАВЕТ ===
            // Пятикнижие Моисея (Благородный глубокий синий)
            Book(id = 1, name = "Бытие", backgroundColor = "#1A365D"),
            Book(id = 2, name = "Исход", backgroundColor = "#1A365D"),
            Book(id = 3, name = "Левит", backgroundColor = "#1A365D"),
            Book(id = 4, name = "Числа", backgroundColor = "#1A365D"),
            Book(id = 5, name = "Второзаконие", backgroundColor = "#1A365D"),

            // Исторические книги (Приглушенный сине-стальной)
            Book(id = 6, name = "Книга Иисуса Навина", backgroundColor = "#2C5282"),
            Book(id = 7, name = "Книга Судей Израилевых", backgroundColor = "#2C5282"),
            Book(id = 8, name = "Книга Руфи", backgroundColor = "#2C5282"),
            Book(id = 9, name = "Первая книга Царств", backgroundColor = "#2C5282"),
            Book(id = 10, name = "Вторая книга Царств", backgroundColor = "#2C5282"),
            Book(id = 11, name = "Третья книга Царств", backgroundColor = "#2C5282"),
            Book(id = 12, name = "Четвертая книга Царств", backgroundColor = "#2C5282"),
            Book(id = 13, name = "Первая книга Паралипоменон", backgroundColor = "#2C5282"),
            Book(id = 14, name = "Вторая книга Паралипоменон", backgroundColor = "#2C5282"),
            Book(id = 15, name = "Первая книга Ездры", backgroundColor = "#2C5282"),
            Book(id = 16, name = "Книга Неемии", backgroundColor = "#2C5282"),
            Book(id = 17, name = "Книга Есфири", backgroundColor = "#2C5282"),

            // Учительные и поэтические (Темный изумруд / Хвойный)
            Book(id = 18, name = "Книга Иова", backgroundColor = "#234E52"),
            Book(id = 19, name = "Псалтирь", backgroundColor = "#1D4ED8"), // Выделим Псалтирь классическим синим
            Book(id = 20, name = "Книга Притчей Соломоновых", backgroundColor = "#234E52"),
            Book(id = 21, name = "Книга Екклесиаста, или Проповедника", backgroundColor = "#234E52"),
            Book(id = 22, name = "Книга Песни Песней Соломона", backgroundColor = "#234E52"),

            // Великие пророки (Темно-зеленый малахит)
            Book(id = 23, name = "Книга пророка Исаии", backgroundColor = "#2F855A"),
            Book(id = 24, name = "Книга пророка Иеремии", backgroundColor = "#2F855A"),
            Book(id = 25, name = "Книга Плач Иеремии", backgroundColor = "#2F855A"),
            Book(id = 26, name = "Книга пророка Иезекииля", backgroundColor = "#2F855A"),
            Book(id = 27, name = "Книга пророка Даниила", backgroundColor = "#2F855A"),

            // Малые пророки (Тёмный серо-зеленый)
            Book(id = 28, name = "Книга пророка Осии", backgroundColor = "#2A4365"),
            Book(id = 29, name = "Книга пророка Иоиля", backgroundColor = "#2A4365"),
            Book(id = 30, name = "Книга пророка Амоса", backgroundColor = "#2A4365"),
            Book(id = 31, name = "Книга пророка Авдия", backgroundColor = "#2A4365"),
            Book(id = 32, name = "Книга пророка Ионы", backgroundColor = "#2A4365"),
            Book(id = 33, name = "Книга пророка Михея", backgroundColor = "#2A4365"),
            Book(id = 34, name = "Книга пророка Наума", backgroundColor = "#2A4365"),
            Book(id = 35, name = "Книга пророка Аввакума", backgroundColor = "#2A4365"),
            Book(id = 36, name = "Книга пророка Софонии", backgroundColor = "#2A4365"),
            Book(id = 37, name = "Книга пророка Аггея", backgroundColor = "#2A4365"),
            Book(id = 38, name = "Книга пророка Захарии", backgroundColor = "#2A4365"),
            Book(id = 39, name = "Книга пророка Малахии", backgroundColor = "#2A4365"),


            // === НОВЫЙ ЗАВЕТ ===
            // Евангелия и Деяния (Благородный глубокий бордовый)
            Book(id = 40, name = "Евангелие от Матфея", backgroundColor = "#741B47"),
            Book(id = 41, name = "Евангелие от Марка", backgroundColor = "#741B47"),
            Book(id = 42, name = "Евангелие от Луки", backgroundColor = "#741B47"),
            Book(id = 43, name = "Евангелие от Иоанна", backgroundColor = "#741B47"),
            Book(id = 44, name = "Деяния святых апостолов", backgroundColor = "#5B0E2D"),

            // Послания апостола Павла (Сливовый / Глубокий фиолетовый)
            Book(id = 45, name = "Послание к Римлянам", backgroundColor = "#4C1D95"),
            Book(id = 46, name = "Первое послание к Коринфянам", backgroundColor = "#4C1D95"),
            Book(id = 47, name = "Второе послание к Коринфянам", backgroundColor = "#4C1D95"),
            Book(id = 48, name = "Послание к Галатам", backgroundColor = "#4C1D95"),
            Book(id = 49, name = "Послание к Ефесянам", backgroundColor = "#4C1D95"),
            Book(id = 50, name = "Послание к Филиппийцам", backgroundColor = "#4C1D95"),
            Book(id = 51, name = "Послание к Колоссянам", backgroundColor = "#4C1D95"),
            Book(id = 52, name = "Первое послание к Фессалоникийцам", backgroundColor = "#4C1D95"),
            Book(id = 53, name = "Второе послание к Фессалоникийцам", backgroundColor = "#4C1D95"),
            Book(id = 54, name = "Первое послание к Тимофею", backgroundColor = "#4C1D95"),
            Book(id = 55, name = "Второе послание к Тимофею", backgroundColor = "#4C1D95"),
            Book(id = 56, name = "Послание к Титу", backgroundColor = "#4C1D95"),
            Book(id = 57, name = "Послание к Филимону", backgroundColor = "#4C1D95"),
            Book(id = 58, name = "Послание к Евреям", backgroundColor = "#4C1D95"),

            // Соборные послания (Темно-пурпурный)
            Book(id = 59, name = "Послание Иакова", backgroundColor = "#6B21A8"),
            Book(id = 60, name = "Первое послание Петра", backgroundColor = "#6B21A8"),
            Book(id = 61, name = "Второе послание Петра", backgroundColor = "#6B21A8"),
            Book(id = 62, name = "Первое послание Иоанна", backgroundColor = "#6B21A8"),
            Book(id = 63, name = "Второе послание Иоанна", backgroundColor = "#6B21A8"),
            Book(id = 64, name = "Третье послание Иоанна", backgroundColor = "#6B21A8"),
            Book(id = 65, name = "Послание Иуды", backgroundColor = "#6B21A8"),

            // Пророческая книга (Антрацитовый темный)
            Book(id = 66, name = "Откровение Иоанна Богослова", backgroundColor = "#1F2937")
        )
    }
}