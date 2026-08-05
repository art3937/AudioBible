package com.example.audiobible.plaerManager

import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Простая потокобезопасная очередь действий из нотификации.
 * Receiver кладёт action сюда, Activity/Fragment при старте/возобновлении их обрабатывает через ViewModel.
 */
object NotificationActionQueue {
    private val queue = ConcurrentLinkedQueue<String>()

    fun enqueue(action: String) {
        queue.add(action)
    }

    fun drain(): List<String> {
        val out = mutableListOf<String>()
        while (true) {
            val a = queue.poll() ?: break
            out.add(a)
        }
        return out
    }
}
