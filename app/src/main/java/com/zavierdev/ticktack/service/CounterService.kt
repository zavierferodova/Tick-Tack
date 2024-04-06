package com.zavierdev.ticktack.service

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.Parcelable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.NotificationCompat
import com.zavierdev.ticktack.MainActivity
import com.zavierdev.ticktack.MainActivity.Companion.EXTRA_COUNTER_SERVICE_DATA
import com.zavierdev.ticktack.R
import com.zavierdev.ticktack.service.CounterService.Companion.ACTION_START
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize

@Parcelize
data class CounterServiceInitData(
    val hours: Int,
    val minutes: Int,
    val seconds: Int
) : Parcelable

@Parcelize
data class CounterServiceData(
    val state: CounterState,
    val firstTotalSeconds: Int,
    val hours: Int,
    val minutes: Int,
    val seconds: Int
) : Parcelable

enum class CounterState {
    START, PAUSE, COMPLETED, CANCELED
}

object CounterServiceCommands {
    fun start(context: Context, initData: CounterServiceInitData) {
        val intent = Intent(context, CounterService::class.java)
        intent.action = ACTION_START
        intent.putExtra(CounterService.EXTRA_INIT_DATA, initData)
        context.startService(intent)
    }

    fun pause(context: Context) {
        val intent = Intent(context, CounterService::class.java)
        intent.action = CounterService.ACTION_PAUSE
        context.startService(intent)
    }

    fun resume(context: Context) {
        val intent = Intent(context, CounterService::class.java)
        intent.action = CounterService.ACTION_RESUME
        context.startService(intent)
    }

    fun reset(context: Context) {
        val intent = Intent(context, CounterService::class.java)
        intent.action = CounterService.ACTION_RESET
        context.startService(intent)
    }

    fun stop(context: Context) {
        val intent = Intent(context, CounterService::class.java)
        intent.action = CounterService.ACTION_STOP
        context.startService(intent)
    }

    fun broadcast(context: Context) {
        val intent = Intent(context, CounterService::class.java)
        intent.action = CounterService.ACTION_BROADCAST
        context.startService(intent)
    }
}

class CounterService : Service() {
    companion object {
        const val NOTIFICATION_CHANNEL_ID = "COUNTER_NOTIFICATION_ID"
        const val NOTIFICATION_CHANNEL_NAME = "COUNTER_NOTIFICATION"
        const val NOTIFICATION_ID = 10
        const val COUNTER_SERVICE_BROADCAST = "COUNTER_SERVICE_BROADCAST"
        const val EXTRA_INIT_DATA = "COUNTER_SERVICE_EXTRA_START_DATA"
        const val ACTION_BROADCAST = "COUNTER_SERVICE_ACTION_BROADCAST"
        const val ACTION_START = "COUNTER_SERVICE_ACTION_START"
        const val ACTION_PAUSE = "COUNTER_SERVICE_ACTION_PAUSE"
        const val ACTION_RESUME = "COUNTER_SERVICE_ACTION_RESUME"
        const val ACTION_RESET = "COUNTER_SERVICE_ACTION_RESET"
        const val ACTION_STOP = "COUNTER_SERVICE_ACTION_STOP"
    }

    private lateinit var notificationManager: NotificationManager
    private lateinit var notificationBuilder: NotificationCompat.Builder
    private lateinit var mediaPlayer: MediaPlayer
    private val binder = LocalBinder()

    private var scope: CoroutineScope = CoroutineScope(Job())
    private val counterState = mutableStateOf(CounterState.CANCELED)
    private val firstTotalSeconds = mutableIntStateOf(0)
    private val seconds = mutableIntStateOf(0)
    private val minutes = mutableIntStateOf(0)
    private val hours = mutableIntStateOf(0)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            when (intent.action) {
                ACTION_START -> {
                    val initData =
                        intent.getParcelableExtra<CounterServiceInitData>(
                            EXTRA_INIT_DATA,
                        )
                    if (initData != null) {
                        startForegroundService(initData.hours, initData.minutes, initData.seconds)
                    }
                }

                ACTION_PAUSE -> {
                    pauseForegroundService()
                }

                ACTION_RESUME -> {
                    resumeForegroundService()
                }

                ACTION_RESET -> {
                    stopForegroundService()
                }

                ACTION_STOP -> {
                    stopForegroundService()
                }

                ACTION_BROADCAST -> {
                    broadcastDataToActivity()
                }
            }
        }

        return super.onStartCommand(intent, flags, startId)
    }

    override fun onBind(intent: Intent?): IBinder = binder

    inner class LocalBinder : Binder() {
        val service: CounterService
            get() = this@CounterService
    }

    @SuppressLint("ForegroundServiceType")
    private fun startForegroundService(hours: Int, minutes: Int, seconds: Int) {
        this.hours.intValue = hours
        this.minutes.intValue = minutes
        this.seconds.intValue = seconds
        provideNotification()
        provideRingtone()
        idleCounter()
        startForeground(NOTIFICATION_ID, notificationBuilder.build())
        updateNotification(this.hours.intValue, this.minutes.intValue, this.seconds.intValue)
    }

    private fun pauseForegroundService() {
        pauseCounter()
    }

    private fun resumeForegroundService() {
        idleCounter()
    }

    private fun stopForegroundService() {
        resetCounter()
        notificationManager.cancel(NOTIFICATION_ID)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun automaticStopForegroundService() {
        if (counterState.value == CounterState.COMPLETED) {
            scope.launch {
                var secondsRemaining = 60 * 2 // 2 minutes
                while (secondsRemaining > 0) {
                    delay(1000) // Delay for 1 second
                    secondsRemaining--
                }
                stopForegroundService()
            }
        }
    }

    private fun provideNotification() {
        if (!this::notificationManager.isInitialized) {
            notificationManager =
                this.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            createNotificationChannel()
        }

        if (!this::notificationBuilder.isInitialized) {
            val intentActivity = Intent(this, MainActivity::class.java)
            val pendingIntentActivity = PendingIntent.getActivity(
                this,
                0,
                intentActivity,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val intentService = Intent(this, CounterService::class.java)
            intentService.action = ACTION_STOP
            val pendingIntentService = PendingIntent.getService(
                this,
                1,
                intentService,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            notificationBuilder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Tick Tack sedang berjalan")
                .setContentIntent(pendingIntentActivity)
                .addAction(0, "Hentikan", pendingIntentService)
                .setOngoing(true)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun provideRingtone() {
        if (this::mediaPlayer.isInitialized) {
            mediaPlayer.release()
        }

        val notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        mediaPlayer = MediaPlayer()
        mediaPlayer.setDataSource(this, notification)
        mediaPlayer.setAudioAttributes(audioAttributes)
        mediaPlayer.isLooping = true
        mediaPlayer.setVolume(
            audioManager.getStreamVolume(AudioManager.STREAM_ALARM).toFloat(),
            audioManager.getStreamVolume(AudioManager.STREAM_ALARM).toFloat()
        )
        mediaPlayer.prepare()
    }

    private fun generateCounterServiceData(): CounterServiceData {
        return CounterServiceData(
            counterState.value,
            firstTotalSeconds.intValue,
            hours.intValue,
            minutes.intValue,
            seconds.intValue
        )
    }

    @SuppressLint("LaunchActivityFromNotification")
    private fun updateNotification(hours: Int, minutes: Int, seconds: Int) {
        val hour: String = if (hours < 10) "0$hours" else hours.toString()
        val minute: String = if (minutes < 10) "0$minutes" else minutes.toString()
        val second: String = if (seconds < 10) "0$seconds" else seconds.toString()

        notificationManager.notify(
            NOTIFICATION_ID,
            notificationBuilder.setContentText("$hour:$minute:$second")
                .build()
        )
    }

    private fun idleCounter() {
        if (counterState.value in listOf(CounterState.PAUSE, CounterState.CANCELED)) {
            scope.launch {
                counterState.value = CounterState.START
                var totalSeconds =
                    (hours.intValue * 60 * 60) + (minutes.intValue * 60) + seconds.intValue
                if (firstTotalSeconds.intValue == 0) {
                    firstTotalSeconds.intValue = totalSeconds
                }
                var isCompleted = false

                while (true) {
                    delay(970)
                    totalSeconds--

                    hours.intValue = totalSeconds / 3600
                    minutes.intValue = (totalSeconds % 3600) / 60
                    seconds.intValue = totalSeconds % 60

                    if (counterState.value == CounterState.PAUSE) {
                        break
                    }

                    // Done counting action
                    if (totalSeconds == 0) {
                        isCompleted = true
                        updateNotification(hours.intValue, minutes.intValue, seconds.intValue)
                        broadcastDataToActivity()
                        break
                    }

                    // Update every tick
                    updateNotification(hours.intValue, minutes.intValue, seconds.intValue)
                    broadcastDataToActivity()
                }

                // Completed action
                if (isCompleted) {
                    firstTotalSeconds.intValue = 0
                    counterState.value = CounterState.COMPLETED
                    mediaPlayer.start()
                    automaticStopForegroundService()
                    broadcastDataToActivity()
                    while (true) {
                        updateNotification(hours.intValue, minutes.intValue, seconds.intValue)
                        broadcastDataToActivity()
                        if (counterState.value == CounterState.CANCELED) {
                            break
                        }
                    }
                }
            }
        }
    }

    private fun pauseCounter() {
        counterState.value = CounterState.PAUSE
        scope.cancel()
        scope = CoroutineScope(Job())
        broadcastDataToActivity()
    }

    private fun resetCounter() {
        mediaPlayer.stop()
        mediaPlayer.release()
        scope.cancel()
        scope = CoroutineScope(Job())
        counterState.value = CounterState.CANCELED
        firstTotalSeconds.intValue = 0
        this.hours.intValue = 0
        this.minutes.intValue = 0
        this.seconds.intValue = 0
        updateNotification(hours.intValue, minutes.intValue, seconds.intValue)
        broadcastDataToActivity()
    }

    private fun broadcastDataToActivity() {
        val intent = Intent(COUNTER_SERVICE_BROADCAST)
        intent.putExtra(EXTRA_COUNTER_SERVICE_DATA, generateCounterServiceData())
        sendBroadcast(intent)
    }
}