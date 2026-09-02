package com.example.audiobible.generatorAll

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object ImageGenerator {

    private val apiKey = "AQVNzPwHVC9lDUrlE2DZGzQbS_H_4sFriSsE-Fhw"
    private val folderId = "b1gnho54qb0fv9om5ktf"
    private val TAG = "BREAD_PARSER_LOG"

    private const val GENERATE_URL =
        "https://llm.api.cloud.yandex.net/foundationModels/v1/imageGenerationAsync"
    private fun getOperationUrl(operationId: String) =
        "https://llm.api.cloud.yandex.net:443/operations/$operationId"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun generateImage(finalPrompt: String): Bitmap? = withContext(Dispatchers.IO) {
        try {

            Log.d(TAG, "[IMAGE] Запуск генерации YandexArt. Текст: \"$finalPrompt\"")

            val messagesArray = JSONArray().apply {
                put(JSONObject().apply {
                    put("weight", "1")
                    put("text", finalPrompt)
                })
            }

            val jsonBody = JSONObject().apply {
                put("modelUri", "art://$folderId/yandex-art/latest")
                put("generationOptions", JSONObject().apply {
                    put("seed", (1..100_000).random())  // число, не строка
                    put("aspectRatio", JSONObject().apply {
                        put("widthRatio", "1")
                        put("heightRatio", "1")
                    })
                })
                put("messages", messagesArray)
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = jsonBody.toString().toRequestBody(mediaType)

            val generateRequest = Request.Builder()
                .url(GENERATE_URL)
                .post(requestBody)
                .addHeader("Authorization", "Api-Key $apiKey")
                .addHeader("Content-Type", "application/json")
                .build()

            Log.d(TAG, "[IMAGE] Отправка POST запроса на создание задачи...")
            client.newCall(generateRequest).execute().use { response ->
                val bodyStr = response.body?.string()?.replace("\n", " ")?.trim()
                Log.d(TAG, "[IMAGE] Шаг 1 Код ответа: ${response.code} | Тело: $bodyStr")

                if (!response.isSuccessful || bodyStr.isNullOrBlank()) {
                    Log.e(TAG, "[IMAGE] Ошибка создания задачи: ${response.code} | $bodyStr")
                    return@withContext null
                }

                val jsonResponse = JSONObject(bodyStr)
                val operationId = jsonResponse.optString("id", "")
                if (operationId.isBlank()) {
                    Log.e(TAG, "[IMAGE] operationId пуст. Ответ: $bodyStr")
                    return@withContext null
                }

                Log.d(TAG, "[IMAGE] Задача принята! ID: $operationId. Опрос готовности...")

                var base64Image: String? = null

                for (attempt in 1..40) {
                    delay(2500)
                    Log.d(TAG, "[IMAGE] Проверка статуса, попытка №$attempt...")

                    val checkRequest = Request.Builder()
                        .url(getOperationUrl(operationId))
                        .get()
                        .addHeader("Authorization", "Api-Key $apiKey")
                        .build()

                    client.newCall(checkRequest).execute().use { checkResponse ->
                        val checkBody = checkResponse.body?.string()
                        if (checkResponse.isSuccessful && !checkBody.isNullOrBlank() &&
                            checkBody.trim().startsWith("{")
                        ) {
                            val jsonCheck = JSONObject(checkBody)
                            val isDone = jsonCheck.optBoolean("done", false)
                            Log.d(TAG, "[IMAGE] done = $isDone")

                            if (isDone) {
                                if (jsonCheck.has("error")) {
                                    Log.e(TAG, "[IMAGE] Ошибка генерации: ${jsonCheck.optJSONObject("error")}")
                                    return@withContext null
                                }

                                val responseObj = jsonCheck.optJSONObject("response")
                                base64Image = responseObj?.optString("image", "")
                                break
                            }
                        } else {
                            Log.w(TAG, "[IMAGE] Неожиданный ответ: ${checkResponse.code} | ${checkBody?.take(200)}")
                        }
                    }
                    if (base64Image != null) break
                }

                if (!base64Image.isNullOrBlank()) {
                    Log.d(TAG, "[IMAGE] Декодируем Base64 в Bitmap...")
                    val imageBytes = Base64.decode(base64Image, Base64.DEFAULT)
                    return@withContext BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                } else {
                    Log.e(TAG, "[IMAGE] Картинка не получена за отведённое время.")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "[IMAGE] Крах: ${e.localizedMessage}", e)
        }
        return@withContext null
    }
}
