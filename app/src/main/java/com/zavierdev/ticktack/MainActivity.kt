package com.zavierdev.ticktack

import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.compose.ConstraintLayout
import com.zavierdev.ticktack.helper.rememberBoundLocalService
import com.zavierdev.ticktack.service.CounterService
import com.zavierdev.ticktack.service.CounterState
import com.zavierdev.ticktack.ui.components.EndlessVerticalPage
import com.zavierdev.ticktack.ui.components.rememberEndlessPagerState
import com.zavierdev.ticktack.ui.theme.TimerTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MainScreen()
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainScreen() {
    val mapTime = fun(value: Int): String {
        var str = value.toString()
        if (value < 10) {
            str = "0$str"
        }
        return str
    }
    val hourList = (0..23).toList().map(mapTime)
    val minuteList = (0..59).toList().map(mapTime)

    val context = LocalContext.current
    val counterService =
        rememberBoundLocalService<CounterService, CounterService.LocalBinder> { service }
    val countDownScope = rememberCoroutineScope()
    val secondPagerState = rememberEndlessPagerState(items = minuteList)
    val minutePagerState = rememberEndlessPagerState(items = minuteList)
    val hourPagerState = rememberEndlessPagerState(items = hourList)

    var countProgress by remember { mutableFloatStateOf(1f) }
    var timerState by remember { mutableStateOf(CounterState.CANCELED) }

    var seconds by remember { mutableIntStateOf(0) }
    var minutes by remember { mutableIntStateOf(0) }
    var hours by remember { mutableIntStateOf(0) }

    val synchronizeCounterDisplay = fun(hour: Int, minute: Int, second: Int) {
        countDownScope.launch {
            val sPagerState = secondPagerState.pagerState
            val mPagerState = minutePagerState.pagerState
            val hPagerState = hourPagerState.pagerState

            val secondIndex = secondPagerState.defaultPage - (secondPagerState.items.size) + second
            sPagerState.animateScrollToPage(secondIndex, animationSpec = tween(0))
            val minuteIndex = minutePagerState.defaultPage - (minutePagerState.items.size) + minute
            mPagerState.animateScrollToPage(minuteIndex, animationSpec = tween(0))
            val hourIndex = hourPagerState.defaultPage - (hourPagerState.items.size) + hour
            hPagerState.animateScrollToPage(hourIndex, animationSpec = tween(0))
        }
    }

    LaunchedEffect(counterService) {
        counterService?.addObserver {
            countDownScope.launch {
                timerState = it.state
                when (it.state) {
                    CounterState.START -> {
                        val totalSeconds = (hours * 60 * 60) + (minutes * 60) + seconds - 1
                        synchronizeCounterDisplay(it.hours, it.minutes, it.seconds)
                        countProgress =
                            (totalSeconds.toFloat() / it.firstTotalSeconds.toFloat() * 100) / 100
                    }

                    CounterState.PAUSE -> {
                        // pass
                    }

                    CounterState.COMPLETED -> {
                        countProgress = 0f
                    }

                    CounterState.CANCELED -> {
                        synchronizeCounterDisplay(it.hours, it.minutes, it.seconds)
                        countProgress = 1f
                    }
                }
            }
        }
    }

    val startCountDown = {
        val totalSeconds = (hours * 60 * 60) + (minutes * 60) + seconds
        if (totalSeconds < 5) {
            Toast.makeText(context, "Waktu tidak boleh kurang dari 5 detik", Toast.LENGTH_SHORT)
                .show()
        } else {
            counterService?.startForegroundService(hours, minutes, seconds)
        }
    }

    val resumeCountDown = {
        counterService?.resumeForegroundService()
    }

    val pauseCountDown = {
        counterService?.pauseForegroundService()
    }

    val resetCountDown = {
        counterService?.resetForegroundService()
    }

    val stopCountDown = {
        counterService?.stopForegroundService()
    }

    TimerTheme {
        // A surface container using the 'background' color from the theme
        Surface(
            modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "Tick Tack", style = TextStyle(
                        fontSize = 35.sp,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Medium
                    ), modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 30.dp)
                )
                ConstraintLayout(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    val (progress, timer) = createRefs()
                    CircularProgressIndicator(progress = countProgress,
                        modifier = Modifier
                            .size(300.dp)
                            .constrainAs(progress) {
                                top.linkTo(parent.top)
                                start.linkTo(parent.start)
                                bottom.linkTo(parent.bottom)
                                end.linkTo(parent.end)
                            })
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .constrainAs(timer) {
                                top.linkTo(progress.top)
                                start.linkTo(progress.start)
                                bottom.linkTo(progress.bottom)
                                end.linkTo(progress.end, margin = 20.dp)
                            },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        EndlessVerticalPage(sweepable = timerState == CounterState.CANCELED,
                            state = hourPagerState,
                            size = 80.dp,
                            itemChanged = {
                                hours = it.toInt()
                            })
                        Text(
                            text = "j", style = TextStyle(
                                fontSize = 22.sp, fontWeight = FontWeight.Normal
                            )
                        )
                        EndlessVerticalPage(
                            sweepable = timerState == CounterState.CANCELED,
                            state = minutePagerState,
                            size = 80.dp,
                            itemChanged = {
                                minutes = it.toInt()
                            })
                        Text(
                            text = "m", style = TextStyle(
                                fontSize = 22.sp, fontWeight = FontWeight.Normal
                            )
                        )
                        EndlessVerticalPage(
                            sweepable = timerState == CounterState.CANCELED,
                            state = secondPagerState,
                            size = 80.dp,
                            itemChanged = {
                                seconds = it.toInt()
                            })
                        Text(
                            text = "d", style = TextStyle(
                                fontSize = 22.sp, fontWeight = FontWeight.Normal
                            )
                        )
                    }
                }
                ConstraintLayout(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 50.dp)
                ) {
                    val (repeatBtn, fab) = createRefs()
                    IconButton(enabled = timerState == CounterState.PAUSE,
                        modifier = Modifier
                            .size(60.dp)
                            .constrainAs(repeatBtn) {
                                top.linkTo(parent.top)
                                start.linkTo(parent.start)
                                end.linkTo(fab.start)
                                bottom.linkTo(parent.bottom)
                            },
                        onClick = {
                            resetCountDown()
                        }) {
                        Icon(
                            Icons.Filled.Refresh, "Refresh Icon"
                        )
                    }
                    SmallFloatingActionButton(modifier = Modifier
                        .size(60.dp)
                        .constrainAs(fab) {
                            top.linkTo(parent.top)
                            start.linkTo(parent.start)
                            end.linkTo(parent.end)
                            bottom.linkTo(parent.bottom)
                        }, onClick = {
                        if (timerState == CounterState.COMPLETED) {
                            stopCountDown()
                        } else if (timerState == CounterState.PAUSE) {
                            resumeCountDown()
                        } else if (timerState != CounterState.START) {
                            startCountDown()
                        } else {
                            pauseCountDown()
                        }
                    }, shape = CircleShape
                    ) {
                        if (timerState == CounterState.COMPLETED) {
                            Icon(Icons.Filled.Stop, "Stop icon")
                        } else if (timerState != CounterState.START) {
                            Icon(Icons.Filled.PlayArrow, "Play icon")
                        } else {
                            Icon(Icons.Filled.Pause, "Pause icon")
                        }
                    }
                }
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    MainScreen()
}