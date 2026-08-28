package com.example.mypersonaltimetracker.tile

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.example.mypersonaltimetracker.App
import com.example.mypersonaltimetracker.office.OfficeStatusService
import com.example.mypersonaltimetracker.widget.updateAllWidgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant

/** Quick Settings tile: active while a session is open, tap toggles vào/ra office. */
class OfficeTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onStartListening() {
        super.onStartListening()
        scope.launch { refreshState() }
    }

    override fun onClick() {
        super.onClick()
        scope.launch {
            App.get(applicationContext).container.repository.toggle(Instant.now())
            OfficeStatusService.sync(applicationContext)
            refreshState()
            updateAllWidgets(applicationContext)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun refreshState() {
        val open = App.get(applicationContext).container.repository.observeOpenSessions().first()
        qsTile?.let {
            it.state = if (open.isNotEmpty()) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            it.updateTile()
        }
    }
}
