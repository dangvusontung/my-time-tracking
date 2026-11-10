package com.example.mypersonaltimetracker.wear

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.wear.compose.material.*
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import java.time.Instant
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController

class TodayActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = (application as WatchApp).store
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    runCatching { store.sync() }.onFailure {
                        if (it is CancellationException) throw it
                        Log.w("WatchSync", "Foreground sync unavailable", it)
                    }
                    delay(60_000)
                }
            }
        }
        // There are no second-based values on this screen. Stop this clock when the UI is hidden.
        val minutes = flow {
            while (true) {
                val now = Instant.now()
                emit(now)
                delay(60_000L - now.toEpochMilli().mod(60_000L))
            }
        }
        val ui = combine(store.state, minutes, ::todayUi)
            .distinctUntilChanged().flowOn(Dispatchers.Default)
        val history = combine(store.state, store.history, minutes, ::historyUi)
            .distinctUntilChanged().flowOn(Dispatchers.Default)
        setContent {
            val current by ui.collectAsStateWithLifecycle(initialValue = TodayUi())
            val past by history.collectAsStateWithLifecycle(initialValue = HistoryUi())
            MaterialTheme {
                val nav = rememberSwipeDismissableNavController()
                SwipeDismissableNavHost(navController = nav, startDestination = "today") {
                    composable("today") {
                        TodayContent(current, past.days[past.today], past.fridayLeave, past.monthStats[past.today.take(7)],
                            onToggle = { (application as WatchApp).actions.launch { store.toggle() } },
                            onSync = store::schedule,
                            onWeek = { nav.navigate("week") }, onHistory = { nav.navigate("history") })
                    }
                    composable("week") { WeekContent(past, onDay = { nav.navigate("day/$it") }, onBack = { nav.popBackStack() }) }
                    composable("history") { HistoryContent(past, onDay = { nav.navigate("day/$it") }, onBack = { nav.popBackStack() }) }
                    composable("day/{date}") { entry ->
                        DayContent(entry.arguments?.getString("date") ?: past.today, past, onBack = { nav.popBackStack() })
                    }
                }
            }
        }
    }
}

@Composable
private fun TodayContent(state: TodayUi, day: HistoryDayUi?, fridayLeave: String?, month: HistoryMonthUi?, onToggle: () -> Unit, onSync: () -> Unit, onWeek: () -> Unit, onHistory: () -> Unit) {
    val listState = rememberScalingLazyListState(initialCenterItemIndex = 2)
    Scaffold(timeText = { TimeText() }) {
        Box(Modifier.fillMaxSize()) {
            ScalingLazyColumn(state = listState,
                modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)) {
                item { Text(state.date, style = MaterialTheme.typography.caption1) }
                if (state.needsSetup) {
                    item { Text("Mở Chấm công trên điện thoại đã ghép đôi để đồng bộ.", textAlign = TextAlign.Center) }
                } else {
                    item { Text(if (state.inOffice) "Đang ở văn phòng" else "Ngoài văn phòng", color = MaterialTheme.colors.primary) }
                    item { Text(state.credited, style = MaterialTheme.typography.display3) }
                    item { Text(state.target, style = MaterialTheme.typography.caption1, textAlign = TextAlign.Center) }
                    fridayLeave?.let { leave -> item {
                        Text("Đủ 40h tuần: $leave · ≥16:30", style = MaterialTheme.typography.caption1, textAlign = TextAlign.Center)
                    } }
                    state.remaining?.let { remaining ->
                        item { Text(remaining, color = MaterialTheme.colors.primary,
                            style = MaterialTheme.typography.caption1, textAlign = TextAlign.Center) }
                    }
                    item {
                        Chip(onClick = onToggle, enabled = !state.queueFull,
                            label = { Text(if (state.inOffice) "Ra ngoài" else "Vào làm") }, modifier = Modifier.fillMaxWidth())
                    }
                    state.since?.let { since -> item { Text(since, style = MaterialTheme.typography.caption1) } }
                    state.leave?.let { leave -> item { Text(leave, style = MaterialTheme.typography.caption1, textAlign = TextAlign.Center) } }
                    if (day != null) {
                        item { TimelineLegend() }
                        item {
                            val (start, end) = timelineBounds(day.timeline)
                            TimelineLine(day.timeline, start, end, labels = true)
                        }
                    }
                    day?.attendance?.let { span -> item {
                        Text("Vào đầu → ${if (span.running) "hiện tại" else "ra cuối"}: ${watchMinutes(span.minutes)}",
                            style = MaterialTheme.typography.caption1, textAlign = TextAlign.Center)
                    } }
                    month?.let { item {
                        Text("TB tháng: ${watchMinutes(it.average)}/ngày · ${it.days} ngày", style = MaterialTheme.typography.caption2, textAlign = TextAlign.Center)
                    } }
                    item { Text(state.sync, style = MaterialTheme.typography.caption2, textAlign = TextAlign.Center) }
                    if (state.queueFull) item { Text("Bộ nhớ chờ đã đầy. Hãy đồng bộ với điện thoại để tiếp tục.", textAlign = TextAlign.Center) }
                }
                state.error?.let { error -> item { Text(error, color = MaterialTheme.colors.error, textAlign = TextAlign.Center) } }
                item { Chip(onClick = onWeek, label = { Text("Tuần này") }, modifier = Modifier.fillMaxWidth(), colors = ChipDefaults.secondaryChipColors()) }
                item { Chip(onClick = onHistory, label = { Text("Lịch sử") }, modifier = Modifier.fillMaxWidth(), colors = ChipDefaults.secondaryChipColors()) }
                item { Chip(onClick = onSync, label = { Text("Đồng bộ điện thoại") }, colors = ChipDefaults.secondaryChipColors(), modifier = Modifier.fillMaxWidth()) }
            }
        }
    }
}
