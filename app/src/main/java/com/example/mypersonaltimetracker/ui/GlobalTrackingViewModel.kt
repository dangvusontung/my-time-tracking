package com.example.mypersonaltimetracker.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mypersonaltimetracker.App
import com.example.mypersonaltimetracker.office.OfficeStatusService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant

data class GlobalTrackingUi(val loaded: Boolean = false, val inOffice: Boolean = false)

/** Activity-scoped tracking state, independent of the date or destination being viewed. */
class GlobalTrackingViewModel(private val app: App) : ViewModel() {
    private val repository = app.container.repository
    val state = repository.observeOpenSessions().map { GlobalTrackingUi(true, it.isNotEmpty()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GlobalTrackingUi())
    private val saving = MutableStateFlow(false)
    val busy = saving.asStateFlow()
    private val notifications = Channel<String>(Channel.BUFFERED)
    val messages = notifications.receiveAsFlow()

    fun track() {
        if (!state.value.loaded || saving.value) return
        val clockOut = state.value.inOffice
        saving.value = true
        viewModelScope.launch {
            try {
                // Explicit actions stay idempotent if another surface updates the status meanwhile.
                if (clockOut) repository.clockOut(Instant.now()) else repository.clockIn(Instant.now())
                runCatching { OfficeStatusService.sync(app) }.onFailure {
                    Log.w("GlobalTracking", "Cannot refresh tracking notification", it)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("GlobalTracking", "Cannot save tracking action", e)
                notifications.send("Không lưu được giờ vào/ra. Vui lòng thử lại.")
            } finally {
                saving.value = false
            }
        }
    }
}
