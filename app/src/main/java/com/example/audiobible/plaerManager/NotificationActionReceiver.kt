package com.example.audiobible.plaerManager

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import com.example.audiobible.plaerManager.NotificationActionQueue
import com.example.audiobible.plaerManager.AudioPlayerManager

/**
 * Приёмник действий нотификации:
 * - Для ACTION_PLAY / ACTION_PAUSE напрямую вызывает AudioPlayerManager.play()/pause() (быстрая реакция даже без UI)
 * - Для всех действий кладёт action в очередь NotificationActionQueue — чтобы ViewModel обработал логику с БД
 * - Отправляет broadcast com.example.audiobible.ACTION_QUEUE_UPDATED чтобы Activity/Fragment могли быстро обработать очередь
 *
 * Важно: receiver НЕ выполняет работу с БД — это делает ViewModel. Здесь только быстрые операции и очередь.
 */
@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var audioPlayerManager: AudioPlayerManager

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        try {
            // Быстрая реакция для play/pause
            when (action) {
                "com.example.audiobible.ACTION_PLAY" -> {
                    try { audioPlayerManager.play() } catch (_: Exception) {}
                }
                "com.example.audiobible.ACTION_PAUSE" -> {
                    try { audioPlayerManager.pause() } catch (_: Exception) {}
                }
            }

            // В любом случае кладём в очередь, чтобы ViewModel мог сохранить/обновить БД и UI
            NotificationActionQueue.enqueue(action)

            // Оповещаем приложение, если оно слушает обновления очереди
            val notify = Intent("com.example.audiobible.ACTION_QUEUE_UPDATED")
            context.sendBroadcast(notify)
        } catch (_: Exception) {}
    }
}
