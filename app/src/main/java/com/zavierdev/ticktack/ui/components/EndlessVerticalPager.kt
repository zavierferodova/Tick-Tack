package com.zavierdev.ticktack.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalFoundationApi::class)
class EndlessPagerState(
    val pagerState: PagerState,
    val items: List<String>
) {
    val defaultPage = pagerState.currentPage
    val currentItem: String
        get() {
            return items[pagerState.settledPage % items.size]
        }

    suspend fun reset() {
        pagerState.animateScrollToPage(defaultPage)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun rememberEndlessPagerState(items: List<String>): EndlessPagerState {
    val pageCount = items.size * 5000
    val centerInt = pageCount / 2
    val centerPage = items.size * (centerInt / items.size)
    val pagerState = rememberPagerState(
        initialPage = centerPage
    ) {
        pageCount
    }
    return remember {
        EndlessPagerState(pagerState, items)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EndlessVerticalPage(
    state: EndlessPagerState,
    itemChanged: (String) -> Unit,
    size: Dp = 50.dp,
    sweepable: Boolean = true,
) {
    val pagerState = state.pagerState
    val items = state.items

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect {
            itemChanged(state.currentItem)
        }
    }

    VerticalPager(
        modifier = Modifier
            .size(size),
        userScrollEnabled = sweepable,
        state = pagerState
    ) { page ->
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = items[page % items.size],
                style = MaterialTheme.typography.headlineLarge
            )
        }
    }
}