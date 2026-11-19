
package com.asena.assistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.speech.tts.TextToSpeech
import java.util.Locale
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.SpeechService
import org.vosk.android.RecognitionListener
import java.io.File

class AsenaService : Service(), RecognitionListener {

    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var speechService: SpeechService? = null
    private var textToSpeech: TextToSpeech? = null
    private var ttsReady = false

    private val channelId = "asena_listener"
    private val notificationId = 1001

    override fun onCreate() {
    super.onCreate()
    createNotificationChannel()

    textToSpeech = TextToSpeech(this) { status ->
        if (status == TextToSpeech.SUCCESS) {
            val result = textToSpeech?.setLanguage(Locale("tr", "TR"))
            ttsReady = result != TextToSpeech.LANG_MISSING_DATA &&
                result != TextToSpeech.LANG_NOT_SUPPORTED
        }
    }
    }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        val notification = createNotification("ASENA hazırlanıyor...")

        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(
                    notificationId,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(notificationId, notification)
            }
        } catch (e: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (speechService != null) {
            return START_NOT_STICKY
        }

        Thread {
            try {
                val modelDirectory = File(filesDir, "model")

                if (!File(modelDirectory, "uuid").exists()) {
                    copyAssets("model", modelDirectory)
                }

                val loadedModel = Model(modelDirectory.absolutePath)
model = loadedModel

val loadedRecognizer = Recognizer(loadedModel, 16000.0f)
recognizer = loadedRecognizer

val service = SpeechService(loadedRecognizer, 16000.0f)
speechService = service
service.startListening(this)
                updateNotification("ASENA çevrimdışı dinlemede")

            } catch (e: Exception) {
                updateNotification("ASENA başlatılamadı")
                stopSelf()
            }
        }.start()

        return START_NOT_STICKY
    }

    private fun copyAssets(
        assetPath: String,
        destination: File
    ) {
        val files = assets.list(assetPath) ?: emptyArray()

        if (files.isNotEmpty()) {
            if (!destination.exists()) {
                destination.mkdirs()
            }

            for (name in files) {
                copyAssets(
                    "$assetPath/$name",
                    File(destination, name)
                )
            }
        } else {
            destination.parentFile?.mkdirs()

            assets.open(assetPath).use { input ->
                destination.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                channelId,
                "ASENA sesli asistan",
                NotificationManager.IMPORTANCE_LOW
            )

            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun createNotification(message: String): Notification {
        return if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, channelId)
                .setContentTitle("ASENA")
                .setContentText(message)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("ASENA")
                .setContentText(message)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setOngoing(true)
                .build()
        }
    }

    private fun updateNotification(message: String) {
        val manager = getSystemService(
            NotificationManager::class.java
        )

        manager.notify(
            notificationId,
            createNotification(message)
        )
    }

    override fun onResult(hypothesis: String?) {
        checkWakeWord(hypothesis, false)
    }

    override fun onPartialResult(hypothesis: String?) {
        checkWakeWord(hypothesis, true)
    }

    override fun onFinalResult(hypothesis: String?) {
        checkWakeWord(hypothesis, false)
    }

    private fun checkWakeWord(
        result: String?,
        partial: Boolean
    ) {
        if (result.isNullOrBlank()) return

        try {
            val json = JSONObject(result)

            val recognizedText = if (partial) {
                json.optString("partial")
            } else {
                json.optString("text")
            }

            val text = recognizedText
                .lowercase()
                .replace("ı", "i")

            if (text.contains("asena")) {
                updateNotification("ASENA çağrıldı")
            }

        } catch (_: Exception) {
        }
    }

    override fun onError(exception: Exception?) {
        updateNotification("Ses tanıma hatası")
    }

    override fun onTimeout() {
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        speechService?.cancel()
        speechService?.shutdown()
        recognizer?.close()
        model?.close()

        speechService = null
        recognizer = null
        model = null

        super.onDestroy()
    }
}
