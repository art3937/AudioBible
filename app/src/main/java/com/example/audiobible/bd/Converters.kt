package com.example.audiobible.bd

import androidx.room.TypeConverter
import org.json.JSONArray
import org.json.JSONObject

class Converters {

    @TypeConverter
    fun fromChapters(value: List<Chapter>?): String? {
        if (value == null) return null
        val array = JSONArray()
        for (chapter in value) {
            val obj = JSONObject()
            obj.put("number", chapter.number)
            obj.put("title", chapter.title)
            obj.put("isRead", chapter.isRead)
            array.put(obj)
        }
        return array.toString()
    }

    @TypeConverter
    fun toChapters(value: String?): List<Chapter> {
        if (value.isNullOrEmpty()) return emptyList()
        val array = JSONArray(value)
        val list = mutableListOf<Chapter>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val number = obj.optInt("number", i + 1)
            val title = if (obj.has("title") && !obj.isNull("title")) obj.optString("title") else null
            val isRead = obj.optBoolean("isRead", false)
            list.add(Chapter(number = number, title = title, isRead = isRead))
        }
        return list
    }
}