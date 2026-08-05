package com.example.audiobible.plaerManager

import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.Log as Media3Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class AudioPlaybackService : MediaSessionService() {

    companion object {
        private const val TAG = "AudioPlaybackService"
    }

    private var mediaSession: MediaSession? = null

    @Inject
    lateinit var audioPlayerManager: AudioPlayerManager

    // Слушатель для отслеживания состояния плеера прямо внутри сервиса
    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            Log.d(TAG, "==> [Слушатель Плеера] onIsPlayingChanged: играть = $isPlaying")
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val stateStr = when (playbackState) {
                Player.STATE_IDLE -> "IDLE (Выключен/Ошибка)"
                Player.STATE_BUFFERING -> "BUFFERING (Загрузка трека)"
                Player.STATE_READY -> "READY (Готов к воспроизведению)"
                Player.STATE_ENDED -> "ENDED (Трек завершился)"
                else -> "UNKNOWN"
            }
            Log.d(TAG, "==> [Слушатель Плеера] onPlaybackStateChanged: Новое состояние = $stateStr")
        }
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "==> onCreate: Сервис шторки запускается...")

        // Включаем внутренние системные логи самого Media3 ExoPlayer в консоль Android Studio
        Media3Log.setLogLevel(Media3Log.LOG_LEVEL_ALL)

        try {
            // Регистрация слушателя событий плеера для отладки
            audioPlayerManager.exoPlayer.addListener(playerListener)

            // Настраиваем клик по самой шторке для разворота вашей Activity
            val intent = Intent(this, com.example.audiobible.fragments.AppActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            // Создаем MediaSession и связываем его с ExoPlayer
            // Создаем MediaSession и связываем его с ExoPlayer
            mediaSession = MediaSession.Builder(this, audioPlayerManager.exoPlayer)
                .setSessionActivity(pendingIntent)
                .setCallback(object : MediaSession.Callback() { // Добавили круглые скобки () к Callback

                    override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
                        Log.d(TAG, "==> [Callback] Подключился контроллер: ${controller.packageName}")

                        // Разрешаем базовые команды подключения
                        val connectionResult = super.onConnect(session, controller)

                        // Жестко активируем кнопки "Вперед" и "Назад" для шторки и часов
                        val playerCommands = Player.Commands.Builder()
                            .add(Player.COMMAND_SEEK_TO_NEXT)
                            .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                            .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                            .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                            .build()

                        return MediaSession.ConnectionResult.accept(connectionResult.availableSessionCommands, playerCommands)
                    }

                    // МЕТОД ДЛЯ ПЕРЕХВАТА НАЖАТИЙ: Ловим клики по кнопкам "Вперед/Назад" со шторки и часов
                    override fun onPlayerCommandRequest(
                        session: MediaSession,
                        controller: MediaSession.ControllerInfo,
                        playerCommand: Int
                    ): Int {
                        if (playerCommand == Player.COMMAND_SEEK_TO_NEXT || playerCommand == Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM) {
                            Log.d(TAG, "==> Сигнал из шторки/часов: НАЖАТА КНОПКА ВПЕРЕД")
                            // Вызываем переключение следующего трека в вашем менеджере
                            audioPlayerManager.onNextTrackRequested?.invoke()
                            return MediaSession.Callback.RESULT_SUCCESS // Говорим системе, что мы сами всё обработали
                        }
                        if (playerCommand == Player.COMMAND_SEEK_TO_PREVIOUS || playerCommand == Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM) {
                            Log.d(TAG, "==> Сигнал из шторки/часов: НАЖАТА КНОПКА НАЗАД")
                            // Вызываем переключение предыдущего трека в вашем менеджере
                            audioPlayerManager.onPreviousTrackRequested?.invoke()
                            return MediaSession.Callback.RESULT_SUCCESS
                        }
                        return super.onPlayerCommandRequest(session, controller, playerCommand)
                    }
                })
                .build()


            // Принудительно регистрируем провайдер уведомлений и вешаем системную иконку,
            // чтобы Android не блокировал уведомление из-за отсутствия SmallIcon
            // Просто создаем стандартный провайдер без кастомных иконок в коде
            val notificationProvider = DefaultMediaNotificationProvider.Builder(this).build()

            setMediaNotificationProvider(notificationProvider)


            Log.d(TAG, "==> onCreate: MediaSession успешно инициализирован, провайдер уведомлений установлен")

        } catch (e: Exception) {
            Log.e(TAG, "!!! КРИТИЧЕСКАЯ ОШИБКА в onCreate сервиса: ${e.message}", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: "НЕТ ЭКШЕНА (Обычный старт)"
        Log.d(TAG, "==> onStartCommand: Получен пинок от приложения. Action = $action")
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        Log.d(TAG, "==> onGetSession: Система запросила сессию для пакета: ${controllerInfo.packageName}")
        return mediaSession
    }

    override fun onDestroy() {
        Log.w(TAG, "==> onDestroy: СЕРВИС УНИЧТОЖАЕТСЯ СИСТЕМОЙ!")
        try {
            audioPlayerManager.exoPlayer.removeListener(playerListener)
            mediaSession?.run {
                player.release()
                release()
            }
            mediaSession = null
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при очистке ресурсов в onDestroy: ${e.message}", e)
        }
        super.onDestroy()
    }
}
