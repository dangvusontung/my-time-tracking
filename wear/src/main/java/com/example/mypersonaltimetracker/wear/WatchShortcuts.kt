package com.example.mypersonaltimetracker.wear

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.graphics.drawable.Icon
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.data.*
import androidx.wear.watchface.complications.datasource.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.UUID

fun refreshShortcuts(context: Context) {
    TileService.getUpdater(context).requestUpdate(OfficeWatchTile::class.java)
    ComplicationDataSourceUpdateRequester.create(context,
        ComponentName(context, OfficeComplication::class.java)).requestUpdateAll()
}

private fun tapIntent(context: Context): PendingIntent {
    val store = (context.applicationContext as WatchApp).store
    val state = store.state.value
    val running = state.projected(System.currentTimeMillis()).firstOrNull { it.outAt == null }
    val requestId = UUID.randomUUID().toString()
    val intent = if (state.snapshot == null) Intent(context, TodayActivity::class.java) else
        Intent(context, QuickOfficeAction::class.java).apply {
            putExtra("command", if (running == null) "in" else "out")
            putExtra("sessionStart", running?.inAt ?: -1L)
            putExtra("requestId", requestId)
            data = Uri.parse("tracker://office/$requestId")
        }
    return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
}

/** The complication's PendingIntent can launch this private activity; other apps cannot. */
class QuickOfficeAction : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Đang lưu…") } } }
        if (savedInstanceState != null) { finish(); return }
        val command = intent.getStringExtra("command")
        val requestId = intent.getStringExtra("requestId") ?: run { finish(); return }
        if (command != "in" && command != "out") { finish(); return }
        val app = application as WatchApp
        app.actions.launch {
            val start = command == "in"
            val expected = intent.getLongExtra("sessionStart", -1).takeIf { it >= 0 }
            val saved = app.store.shortcut(start, expected, requestId)
            if (!saved) refreshShortcuts(app)
            withContext(Dispatchers.Main) {
                Toast.makeText(this@QuickOfficeAction,
                    if (saved) (if (start) "Đã vào làm" else "Đã ra ngoài") else "Trạng thái đã đổi. Mở ứng dụng để kiểm tra.",
                    Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
}

class OfficeComplication : SuspendingComplicationDataSourceService() {
    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData = withContext(Dispatchers.IO) {
        val store = (application as WatchApp).store
        val ui = todayUi(store.state.value, Instant.now())
        data(request.complicationType, if (ui.needsSetup) "--" else ui.credited.replace(" ", ""),
            if (ui.needsSetup) "Mở ứng dụng" else if (ui.inOffice) "Ra" else "Vào", tapIntent(this@OfficeComplication))
    }
    override fun getPreviewData(type: ComplicationType): ComplicationData = data(type, "6h30", "Ra", null)

    private fun data(type: ComplicationType, text: String, title: String, tap: PendingIntent?): ComplicationData {
        val description = PlainComplicationText.Builder("$title · $text").build()
        val icon = Icon.createWithResource(this, R.drawable.ic_shortcut)
        return when (type) {
            ComplicationType.SMALL_IMAGE -> SmallImageComplicationData.Builder(
                SmallImage.Builder(icon, SmallImageType.ICON).build(), description).setTapAction(tap).build()
            ComplicationType.MONOCHROMATIC_IMAGE -> MonochromaticImageComplicationData.Builder(
                MonochromaticImage.Builder(icon).build(), description).setTapAction(tap).build()
            else -> ShortTextComplicationData.Builder(PlainComplicationText.Builder(text).build(), description)
                .setTitle(PlainComplicationText.Builder(title).build()).setTapAction(tap).build()
        }
    }
}
