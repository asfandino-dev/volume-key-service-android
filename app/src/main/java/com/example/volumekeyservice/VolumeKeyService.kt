package com.example.volumekeyservice

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityEvent
import android.view.KeyEvent
import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class VolumeKeyService : AccessibilityService() {

    private var lastUpClickTime: Long = 0
    private var lastDownClickTime: Long = 0
    private val DOUBLE_CLICK_DELAY: Long = 300

    private val volumeHandler = Handler(Looper.getMainLooper())
    private var pendingVolumeRunnable: Runnable? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onServiceConnected() {

        super.onServiceConnected()

        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        }
        this.serviceInfo = info

        // Ejecutamos nuestra función propia para pintar la tarjeta en la barra superior
        startForegroundServiceWithNotification()

        // Encendemos el candado de la CPU
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "VolumeKeyService::CpuWakeLock").apply {
            acquire()
        }
    }

    private fun startForegroundServiceWithNotification() {
        val channelId = "volume_control_channel"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Control de Volumen Multimedia",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channelId)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        // AQUÍ ES DONDE SE CONFIGURA LA NOTIFICACIÓN
        val notification = builder
            .setContentTitle("Control de Música Activo")
            .setContentText("Doble clic cambia canción.")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true) // 1. Primero se le da la propiedad al diseñador
            .build()          // 2. Luego se manufactura el objeto

        startForeground(1, notification) // 3. Se lanza al sistema

        // Le dice a Android: "Arranca este servicio en primer plano usando la notificación que armamos"
        startForeground(1, notification)
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        val action = event.action
        val repeatCount = event.repeatCount // Capturamos las repeticiones del hardware

        if (action == KeyEvent.ACTION_DOWN) {
            val currentTime = System.currentTimeMillis()
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

            // SI EL CONTADOR DE REPETICIÓN ES MAYOR A 0, ES UNA PULSACIÓN LARGA EN PROGRESO
            if (repeatCount > 0) {
                // Cancelamos cualquier acción de doble clic pendiente
                pendingVolumeRunnable?.let { volumeHandler.removeCallbacks(it) }

                // Retornamos false para que Android suba o baje el volumen continuamente de forma nativa
                return false
            }

            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    if (currentTime - lastUpClickTime < DOUBLE_CLICK_DELAY) {
                        pendingVolumeRunnable?.let { volumeHandler.removeCallbacks(it) }
                        triggerMediaKey(audioManager, KeyEvent.KEYCODE_MEDIA_NEXT)
                        lastUpClickTime = 0
                        return true
                    }

                    lastUpClickTime = currentTime
                    pendingVolumeRunnable = Runnable {
                        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                    }
                    volumeHandler.postDelayed(pendingVolumeRunnable!!, DOUBLE_CLICK_DELAY)
                    return true
                }

                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    if (currentTime - lastDownClickTime < DOUBLE_CLICK_DELAY) {
                        pendingVolumeRunnable?.let { volumeHandler.removeCallbacks(it) }
                        triggerMediaKey(audioManager, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                        lastDownClickTime = 0
                        return true
                    }

                    lastDownClickTime = currentTime
                    pendingVolumeRunnable = Runnable {
                        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                    }
                    volumeHandler.postDelayed(pendingVolumeRunnable!!, DOUBLE_CLICK_DELAY)
                    return true
                }
            }
        }
        return super.onKeyEvent(event)
    }

    private fun triggerMediaKey(audioManager: AudioManager, mediaKeyCode: Int) {
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, mediaKeyCode))
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, mediaKeyCode))
    }

    override fun onDestroy() {
        super.onDestroy()
        if (wakeLock?.isHeld == true) { wakeLock?.release() }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
}