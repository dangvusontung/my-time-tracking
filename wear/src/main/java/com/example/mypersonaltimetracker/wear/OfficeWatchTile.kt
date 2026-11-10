package com.example.mypersonaltimetracker.wear

import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material.CircularProgressIndicator
import androidx.wear.protolayout.material.ProgressIndicatorColors
import java.time.LocalDate
import java.time.DayOfWeek
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import kotlinx.coroutines.*
import java.time.Instant
import java.util.UUID

class OfficeWatchTile : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest) =
        CallbackToFutureAdapter.getFuture<TileBuilders.Tile> { result ->
            scope.launch {
                try {
                    val store = (application as WatchApp).store
                    val click = requestParams.currentState.lastClickableId
                    val parts = click.split(':')
                    if (parts.size == 2 && parts[0] == "in") store.shortcut(true, null, parts[1])
                    else if (parts.size == 3 && parts[0] == "out") store.shortcut(false, parts[1].toLongOrNull(), parts[2])
                    val local = store.state.value
                    val ui = todayUi(local, Instant.now())
                    val running = local.projected(System.currentTimeMillis()).firstOrNull { it.outAt == null }
                    val action = if (ui.needsSetup) ActionBuilders.LaunchAction.Builder().setAndroidActivity(
                        ActionBuilders.AndroidActivity.Builder().setPackageName(packageName).setClassName(TodayActivity::class.java.name).build()
                    ).build() else ActionBuilders.LoadAction.Builder().build()
                    val clickable = ModifiersBuilders.Clickable.Builder()
                        .setId(if (ui.needsSetup) "open" else if (running == null) "in:${UUID.randomUUID()}" else "out:${running.inAt}:${UUID.randomUUID()}")
                        .setOnClick(action).build()
                    val preferences = getSharedPreferences("tile_display", MODE_PRIVATE)
                    if (click == "period:day" || click == "period:week") {
                        preferences.edit().putBoolean("week", click == "period:week").commit()
                    }
                    val week = preferences.getBoolean("week", false)
                    val history = historyUi(local, store.history.value, Instant.now())
                    val monday = LocalDate.parse(history.today).with(DayOfWeek.MONDAY)
                    val total = if (week) (0..6).sumOf { history.days[monday.plusDays(it.toLong()).toString()]?.creditedMinutes ?: 0 }
                        else history.days[history.today]?.creditedMinutes ?: 0
                    val target = if (week) history.targets[monday.toString()] ?: history.weeklyTarget else local.snapshot?.target ?: 480
                    val fraction = if (target > 0) total.toFloat() / target else 0f
                    val ringColor = if (target > 0 && total >= target) 0xff16834a.toInt() else 0xff50d890.toInt()
                    fun tab(label: String, isWeek: Boolean) = LayoutElementBuilders.Box.Builder()
                        .setWidth(dp(52f)).setHeight(dp(28f))
                        .setModifiers(ModifiersBuilders.Modifiers.Builder().setClickable(
                            ModifiersBuilders.Clickable.Builder().setId(if (isWeek) "period:week" else "period:day")
                                .setOnClick(ActionBuilders.LoadAction.Builder().build()).build()).build())
                        .addContent(LayoutElementBuilders.Text.Builder().setText(label).setFontStyle(
                            LayoutElementBuilders.FontStyle.Builder().setSize(sp(13f))
                                .setColor(argb(if (week == isWeek) ringColor else 0xff888888.toInt())).build()).build()).build()
                    val buttonColor = if (ui.inOffice) 0xffff6b6b.toInt() else 0xff50d890.toInt()
                    val button = LayoutElementBuilders.Box.Builder()
                        .setWidth(dp(112f)).setHeight(dp(36f))
                        .setModifiers(ModifiersBuilders.Modifiers.Builder()
                            .setClickable(clickable)
                            .setSemantics(ModifiersBuilders.Semantics.Builder()
                                .setContentDescription(if (ui.needsSetup) "Mở ứng dụng" else if (ui.inOffice) "Ra ngoài" else "Vào làm").build())
                            .setBackground(ModifiersBuilders.Background.Builder().setColor(argb(buttonColor))
                                .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(18f)).build()).build())
                            .build())
                        .addContent(LayoutElementBuilders.Text.Builder()
                            .setText(if (ui.needsSetup) "Mở ứng dụng" else if (ui.inOffice) "Ra ngoài" else "Vào làm")
                            .setFontStyle(LayoutElementBuilders.FontStyle.Builder().setSize(sp(14f))
                                .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD).setColor(argb(0xff102019.toInt())).build())
                            .build())
                        .build()
                    val diameter = minOf(requestParams.deviceConfiguration.screenWidthDp, requestParams.deviceConfiguration.screenHeightDp) - 12f
                    val content = LayoutElementBuilders.Column.Builder()
                        .addContent(LayoutElementBuilders.Row.Builder().addContent(tab("Ngày", false)).addContent(tab("Tuần", true)).build())
                        .addContent(text(if (ui.needsSetup) "Chưa đồng bộ" else watchMinutes(total), 27f))
                        .addContent(text("/ ${watchMinutes(target)} · ${(fraction * 100).toInt()}%", 12f))
                        .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(6f)).build())
                        .addContent(text(if (ui.inOffice) "Đang ở văn phòng" else "Ngoài văn phòng", 12f))
                        .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(8f)).build())
                        .addContent(button)
                        .apply { history.fridayLeave?.let { addContent(text("Đủ 40h tuần: $it", 11f)) } }
                        .build()
                    val layout = LayoutElementBuilders.Box.Builder()
                        .setWidth(androidx.wear.protolayout.DimensionBuilders.expand())
                        .setHeight(androidx.wear.protolayout.DimensionBuilders.expand())
                        .addContent(LayoutElementBuilders.Box.Builder().setWidth(dp(diameter)).setHeight(dp(diameter))
                            .addContent(CircularProgressIndicator.Builder().setProgress(fraction.coerceIn(0f, 1f))
                                .setStartAngle(0f).setEndAngle(360f).setStrokeWidth(4f).setOuterMarginApplied(false)
                                .setContentDescription("${if (week) "Tuần" else "Ngày"}: ${watchMinutes(total)} trên ${watchMinutes(target)}")
                                .setCircularProgressIndicatorColors(ProgressIndicatorColors(ringColor, 0xff777d84.toInt())).build())
                            .build())
                        .addContent(content)
                        .build()
                    result.set(TileBuilders.Tile.Builder().setResourcesVersion("1").setFreshnessIntervalMillis(60_000)
                        .setTileTimeline(TimelineBuilders.Timeline.Builder().addTimelineEntry(TimelineBuilders.TimelineEntry.Builder()
                            .setLayout(LayoutElementBuilders.Layout.Builder().setRoot(layout).build()).build()).build()).build())
                } catch (e: Exception) { result.setException(e) }
            }
            "OfficeWatchTile"
        }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest) =
        CallbackToFutureAdapter.getFuture<ResourceBuilders.Resources> { result ->
            result.set(ResourceBuilders.Resources.Builder().setVersion("1").build())
            "OfficeWatchTileResources"
        }

    private fun text(value: String, size: Float) = LayoutElementBuilders.Text.Builder().setText(value)
        .setFontStyle(LayoutElementBuilders.FontStyle.Builder().setSize(sp(size)).setColor(argb(0xffeeeeee.toInt())).build()).build()

    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
