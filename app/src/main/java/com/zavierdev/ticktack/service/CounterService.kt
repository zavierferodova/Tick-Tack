package com.zavierdev.ticktack.service

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.NotificationCompat
import com.zavierdev.ticktack.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class CounterState {
    START, PAUSE, COMPLETED, CANCELED
}

data class CounterServiceData(
    val state: CounterState, val hours: Int, val minutes: Int, val seconds: Int
)

fun interface CounterServiceObserver {
    fun update(counterServiceData: CounterServiceData)
}

@RequiresApi(Build.VERSION_CODES.O)
class CounterService : Service() {
    companion object {
        const val NOTIFICATION_CHANNEL_ID = "COUNTER_NOTIFICATION_ID"
        const val NOTIFICATION_CHANNEL_NAME = "COUNTER_NOTIFICATION"
        const val NOTIFICATION_ID = 10
    }

    private lateinit var notificationManager: NotificationManager
    private lateinit var notificationBuilder: NotificationCompat.Builder
    private lateinit var ringtone: Ringtone
    private val binder = LocalBinder()

    private var scope: CoroutineScope = CoroutineScope(Job())
    private var observers = mutableListOf<CounterServiceObserver>()
    private var counterState = mutableStateOf(CounterState.CANCELED)
    private var seconds = mutableIntStateOf(0)
    private var minutes = mutableIntStateOf(0)
    private var hours = mutableIntStateOf(0)

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        stopForegroundService()
        super.onDestroy()
    }

    inner class LocalBinder : Binder() {
        val service: CounterService
            get() = this@CounterService
    }

    @SuppressLint("ForegroundServiceType")
    fun startForegroundService(hours: Int, minutes: Int, seconds: Int) {
        this.hours.intValue = hours
        this.minutes.intValue = minutes
        this.seconds.intValue = seconds
        provideNotification()
        provideRingtone()
        idleCounter()
        startForeground(NOTIFICATION_ID, notificationBuilder.build())
        updateNotification(this.hours.intValue, this.minutes.intValue, this.seconds.intValue)
    }

    fun pauseForegroundService() {
        pauseCounter()
    }

    fun resumeForegroundService() {
        idleCounter()
    }

    fun resetForegroundService() {
        resetCounter()
    }

    fun stopForegroundService() {
        resetCounter()
        notificationManager.cancel(NOTIFICATION_ID)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun provideNotification() {
        if (!this::notificationManager.isInitialized) {
            notificationManager =
                this.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            createNotificationChannel()
        }

        if (!this::notificationBuilder.isInitialized) {
            notificationBuilder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentTitle("Tick Tack sedang berjalan")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
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
        if (!this::ringtone.isInitialized) {
            val notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ringtone = RingtoneManager.getRingtone(this, notification);
        }
    }

    private fun updateNotification(hours: Int, minutes: Int, seconds: Int) {
        val hour: String = if (hours < 10) "0$hours" else hours.toString()
        val minute: String = if (minutes < 10) "0$minutes" else minutes.toString()
        val second: String = if (seconds < 10) "0$seconds" else seconds.toString()

        notificationManager.notify(
            NOTIFICATION_ID,
            notificationBuilder
                .setContentText("$hour:$minute:$second")
                .setOngoing(true)
                .build()
        )
    }

    private fun idleCounter() {
        scope.launch {
            counterState.value = CounterState.START
            var totalSeconds =
                (hours.intValue * 60 * 60) + (minutes.intValue * 60) + seconds.intValue
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
                    notifyObservers()
                    break
                }

                // Update every tick
                updateNotification(hours.intValue, minutes.intValue, seconds.intValue)
                notifyObservers()
            }

            // Completed action
            if (isCompleted) {
                counterState.value = CounterState.COMPLETED
                ringtone.play()
                notifyObservers()
                while (true) {
                    updateNotification(hours.intValue, minutes.intValue, seconds.intValue)
                    if (counterState.value == CounterState.CANCELED) {
                        break
                    }
                }
            }
        }
    }

    private fun pauseCounter() {
        counterState.value = CounterState.PAUSE
        scope.cancel()
        scope = CoroutineScope(Job())
        notifyObservers()
    }

    private fun resetCounter() {
        ringtone.stop()
        scope.cancel()
        scope = CoroutineScope(Job())
        counterState.value = CounterState.CANCELED
        this.hours.intValue = 0
        this.minutes.intValue = 0
        this.seconds.intValue = 0
        updateNotification(hours.intValue, minutes.intValue, seconds.intValue)
        notifyObservers()
    }

    fun addObserver(observer: CounterServiceObserver) {
        observers.add(observer)
    }

    fun removeObserver(observer: CounterServiceObserver) {
        observers.remove(observer)
    }

    private fun notifyObservers() {
        observers.forEach {
            it.update(
                CounterServiceData(
                    counterState.value, hours.intValue, minutes.intValue, seconds.intValue
                )
            )
        }
    }
}