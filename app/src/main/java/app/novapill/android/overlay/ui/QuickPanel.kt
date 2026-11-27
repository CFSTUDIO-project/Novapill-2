package app.novapill.android.overlay.ui

import android.text.format.DateFormat
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.novapill.android.core.CardKind
import app.novapill.android.core.RecPhase
import app.novapill.android.core.StopwatchPhase
import app.novapill.android.core.StorageFormat
import app.novapill.android.core.TimeFormat
import app.novapill.android.core.TorchPending
import app.novapill.android.core.TorchStatus
import app.novapill.android.overlay.IslandActions
import app.novapill.android.overlay.IslandUi
import app.novapill.android.settings.TempUnits
import app.novapill.android.settings.TileKeys
import app.novapill.android.ui.components.NovaIcon
import app.novapill.android.ui.components.NovaIconView
import app.novapill.android.ui.components.pressable
import app.novapill.android.ui.theme.NovaColors
import app.novapill.android.ui.theme.NovaType
import app.novapill.android.util.BatteryInfo
import app.novapill.android.util.DeviceStatus
import app.novapill.android.util.NetworkKind
import app.novapill.android.util.RecordMethod
import app.novapill.android.util.SystemAppKind
import app.novapill.android.util.TorchError
import app.novapill.android.util.TorchState
import app.novapill.android.util.WeatherCodes
import app.novapill.android.util.WeatherKind
import app.novapill.android.util.WeatherState
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/*
 * The quick panel: a header with the real clock, date, weather and battery, two pages of tools ("Quick" and
 * "More"), and a thin status line. Every tile either does something real or says why it cannot; nothing in here
 * is decoration. The flashlight tile shows what Android reported, never what the last tap hoped for.
 */

private val TILE_HEIGHT = 52.dp

@Composable
internal fun ToolsPanel(ui: IslandUi, accent: Color, actions: IslandActions, topPadDp: Float) {
    val page = ui.tools.page
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(topPadDp.dp))
        QuickHeader(ui)
        Spacer(Modifier.height(6.dp))
        PageTabs(page) { actions.setToolsPage(it) }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().weight(1f)) {
            Crossfade(
                targetState = page,
                modifier = Modifier.fillMaxSize(),
                animationSpec = tween(if (ui.reducedMotion) 80 else 160),
                label = "quickPage",
            ) { p ->
                if (p == 0) QuickPage(ui, actions) else MorePage(ui, actions)
            }
        }
        Spacer(Modifier.height(6.dp))
        StatusStrip(ui, accent)
        Spacer(Modifier.height(12.dp))
    }
}

// ---------------------------------------------------------------------------------------------
// Header: clock, date, weather, battery
// ---------------------------------------------------------------------------------------------

@Composable
private fun QuickHeader(ui: IslandUi) {
    val clock = rememberClockReading()
    val t = ui.tools
    val s = ui.settings
    val date = remember(clock.hour24, clock.minute) { dateText(Locale.getDefault(), System.currentTimeMillis()) }
    Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(verticalArrangement = Arrangement.Center) {
            Row(verticalAlignment = Alignment.Bottom) {
                PillText(
                    TimeFormat.clock(clock.hour24, clock.minute, t.use24h),
                    NovaType.NumberBig.copy(fontSize = 28.sp, lineHeight = 32.sp),
                )
                if (!t.use24h) {
                    Spacer(Modifier.width(4.dp))
                    PillText(TimeFormat.meridiem(clock.hour24), NovaType.NumberSmall.copy(fontSize = 11.sp), color = NovaColors.Text3)
                }
            }
            if (s.panelShowDate) {
                PillText(date, NovaType.Caption.copy(fontSize = 11.sp, lineHeight = 13.sp), color = NovaColors.Text3)
            }
        }
        Spacer(Modifier.weight(1f))
        WeatherChip(ui)
        Spacer(Modifier.width(10.dp))
        BatteryChip(t.battery)
    }
}

private fun dateText(locale: Locale, nowMs: Long): String {
    val pattern = DateFormat.getBestDateTimePattern(locale, "EEEdMMM")
    return SimpleDateFormat(pattern, locale).format(Date(nowMs))
}

@Composable
private fun BatteryChip(battery: BatteryInfo) {
    if (!battery.known) return
    val low = battery.percent <= 15 && !battery.charging
    val tint = when {
        battery.charging -> NovaColors.Green
        low -> NovaColors.Danger
        else -> NovaColors.Text2
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        NovaIconView(if (battery.charging) NovaIcon.BOLT else NovaIcon.BATTERY, tint, size = 18.dp)
        Spacer(Modifier.width(3.dp))
        PillText("${battery.percent}%", NovaType.NumberMid.copy(fontSize = 13.sp), color = tint)
    }
}

/** Temperature with what is true about it: where, since when, and whether it is old. Never a made-up value. */
@Composable
private fun WeatherChip(ui: IslandUi) {
    val use24h = ui.tools.use24h
    val fahrenheit = TempUnits.useFahrenheit(ui.settings.weatherUnit, Locale.getDefault().country)
    when (val w = ui.tools.weather) {
        is WeatherState.Ready -> {
            val kind = WeatherCodes.kind(w.info.code, w.info.isDay)
            val tint = if (w.stale) NovaColors.Text3 else NovaColors.Text1
            WeatherCell(
                icon = weatherIcon(kind),
                tint = tint,
                value = WeatherCodes.formatTemp(w.info.tempC, fahrenheit),
                caption = weatherCaption(w.info.place, w.info.fetchedAtMs, w.stale, use24h),
            )
        }
        WeatherState.Loading -> WeatherCell(NovaIcon.CLOUD, NovaColors.Text3, "…", "Updating")
        is WeatherState.Unavailable -> WeatherCell(NovaIcon.CLOUD, NovaColors.Text4, "–", "No weather")
        WeatherState.Idle -> Unit
    }
}

@Composable
private fun WeatherCell(icon: NovaIcon, tint: Color, value: String, caption: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        NovaIconView(icon, tint, size = 22.dp)
        Spacer(Modifier.width(6.dp))
        Column(verticalArrangement = Arrangement.Center) {
            PillText(value, NovaType.NumberMid.copy(fontSize = 15.sp), color = tint)
            PillText(
                caption,
                NovaType.Caption.copy(fontSize = 10.sp, lineHeight = 12.sp),
                modifier = Modifier.widthIn(max = 92.dp),
                color = NovaColors.Text3,
            )
        }
    }
}

private fun weatherCaption(place: String, fetchedAtMs: Long, stale: Boolean, use24h: Boolean): String {
    val c = Calendar.getInstance().apply { timeInMillis = fetchedAtMs }
    val time = TimeFormat.clock(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), use24h)
    return if (stale) "Old · $time" else "$place · $time"
}

internal fun weatherIcon(kind: WeatherKind): NovaIcon = when (kind) {
    WeatherKind.CLEAR_DAY -> NovaIcon.SUN
    WeatherKind.CLEAR_NIGHT -> NovaIcon.MOON
    WeatherKind.PARTLY_CLOUDY -> NovaIcon.PARTLY
    WeatherKind.CLOUDY -> NovaIcon.CLOUD
    WeatherKind.FOG -> NovaIcon.FOG
    WeatherKind.DRIZZLE, WeatherKind.RAIN -> NovaIcon.RAIN
    WeatherKind.SNOW -> NovaIcon.SNOW
    WeatherKind.THUNDER -> NovaIcon.STORM
    WeatherKind.UNKNOWN -> NovaIcon.CLOUD
}

// ---------------------------------------------------------------------------------------------
// Page switch
// ---------------------------------------------------------------------------------------------

@Composable
private fun PageTabs(page: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(24.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TabChip("Quick", page == 0) { onSelect(0) }
        TabChip("More", page == 1) { onSelect(1) }
    }
}

@Composable
private fun TabChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .height(24.dp)
            .pressable(label = text, onClick = onClick)
            .clip(CircleShape)
            .background(if (selected) Color.White else NovaColors.Surface3)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        PillText(
            text,
            NovaType.Label.copy(fontSize = 11.sp, lineHeight = 13.sp),
            color = if (selected) Color.Black else NovaColors.Text2,
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Pages
// ---------------------------------------------------------------------------------------------

private class Tile(
    val icon: NovaIcon,
    val label: String,
    val description: String,
    val active: Boolean,
    val enabled: Boolean,
    val onClick: () -> Unit,
)

@Composable
private fun QuickPage(ui: IslandUi, actions: IslandActions) {
    val s = ui.settings
    val tiles = buildList {
        if (s.tileVisible(TileKeys.FLASH)) add(flashTile(ui.tools.torch, actions))
        if (s.tileVisible(TileKeys.STOPWATCH)) add(stopwatchTile(ui, actions))
        if (s.tileVisible(TileKeys.RECORD)) add(recordTile(ui, actions))
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (tiles.isNotEmpty()) TileRow(tiles)
        if (s.timerPresetsMin.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (minutes in s.timerPresetsMin) {
                    PillButton(
                        text = TimeFormat.presetLabel(minutes),
                        onClick = { actions.startTimer(minutes) },
                        modifier = Modifier.weight(1f),
                        height = 40.dp,
                        fontSize = 12,
                    )
                }
            }
        }
        val launchers = buildList {
            if (s.tileVisible(TileKeys.ALARMS)) add(Triple("Alarms", NovaIcon.ALARM, SystemAppKind.ALARMS))
            if (s.tileVisible(TileKeys.CALENDAR)) add(Triple("Calendar", NovaIcon.CALENDAR, SystemAppKind.CALENDAR))
            if (s.tileVisible(TileKeys.PHONE)) add(Triple("Phone", NovaIcon.PHONE, SystemAppKind.PHONE))
        }
        if (launchers.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((name, icon, kind) in launchers) {
                    PillButton(name, { actions.openSystemApp(kind) }, Modifier.weight(1f), icon, height = 40.dp, fontSize = 12)
                }
            }
        }
        if (s.shortcutApps.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().height(48.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for (pkg in s.shortcutApps) {
                    Box(
                        Modifier
                            .size(44.dp)
                            .pressable(label = "Open app", onClick = { actions.openApp(pkg) }),
                    ) {
                        AppIcon(pkg, 44.dp, 12.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun MorePage(ui: IslandUi, actions: IslandActions) {
    val s = ui.settings
    val t = ui.tools
    val tiles = buildList {
        if (s.tileVisible(TileKeys.INTERNET)) {
            add(Tile(NovaIcon.WIFI, "Internet", "Internet panel", false, true) { actions.openSystemApp(SystemAppKind.INTERNET) })
        }
        if (s.tileVisible(TileKeys.VOLUME)) {
            add(Tile(NovaIcon.VOLUME, "Volume", "Volume panel", false, true) { actions.openSystemApp(SystemAppKind.VOLUME) })
        }
        if (s.tileVisible(TileKeys.CAMERA)) {
            add(Tile(NovaIcon.CAMERA, "Camera", "Open camera", false, true) { actions.openSystemApp(SystemAppKind.CAMERA) })
        }
        if (s.tileVisible(TileKeys.CALCULATOR)) {
            add(Tile(NovaIcon.CALCULATOR, "Calculator", "Floating calculator", t.calculatorOpen, true) { actions.toggleCalculator() })
        }
        if (s.tileVisible(TileKeys.SETTINGS)) {
            add(Tile(NovaIcon.SLIDERS, "Settings", "Android settings", false, true) { actions.openSystemApp(SystemAppKind.SETTINGS) })
        }
        if (t.recordManualEnabled && s.tileVisible(TileKeys.MANUAL_TIMER)) {
            add(
                Tile(
                    icon = NovaIcon.TIMER,
                    label = "Rec timer",
                    description = "Manual recording timer",
                    active = ui.record?.method == RecordMethod.MANUAL,
                    enabled = true,
                ) { actions.recordStart() },
            )
        }
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (tiles.isEmpty()) {
            PillText("Every tool on this page is hidden in Settings.", NovaType.Caption, color = NovaColors.Text3, maxLines = 2)
        }
        for (row in tiles.chunked(3)) TileRow(row, columns = 3)
        PillText(
            "Internet and Volume open Android's own panels. Apps may not switch them directly.",
            NovaType.Caption.copy(fontSize = 10.sp, lineHeight = 12.sp),
            color = NovaColors.Text4,
            maxLines = 2,
        )
    }
}

@Composable
private fun TileRow(tiles: List<Tile>, columns: Int = tiles.size) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (tile in tiles) {
            TileCell(tile, Modifier.weight(1f))
        }
        // Keep the grid aligned when the last row is short.
        repeat((columns - tiles.size).coerceAtLeast(0)) { Spacer(Modifier.weight(1f)) }
    }
}

@Composable
private fun TileCell(tile: Tile, modifier: Modifier) {
    val bg = if (tile.active) Color.White else NovaColors.Surface3
    val fg = if (tile.active) Color.Black else NovaColors.Text1
    val alpha = if (tile.enabled) 1f else 0.45f
    Column(
        modifier
            .height(TILE_HEIGHT)
            .pressable(enabled = tile.enabled, label = tile.description, onClick = tile.onClick)
            .clip(RoundedCornerShape(16.dp))
            .background(bg.copy(alpha = alpha)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        NovaIconView(tile.icon, fg.copy(alpha = alpha), size = 20.dp)
        Spacer(Modifier.height(3.dp))
        PillText(tile.label, NovaType.Caption.copy(fontSize = 10.sp, lineHeight = 12.sp), color = fg.copy(alpha = alpha))
    }
}

// ---------------------------------------------------------------------------------------------
// Tiles whose label depends on real state
// ---------------------------------------------------------------------------------------------

/**
 * The label comes from [TorchState.status], which only changes when Android reports it. A command that is on
 * its way shows "Turning on" or "Turning off", never "On". If the phone never confirms, the tile says so.
 */
private fun flashTile(torch: TorchState, actions: IslandActions): Tile {
    val label = when {
        torch.status == TorchStatus.NO_FLASH -> "No flash"
        torch.pending == TorchPending.TO_ON -> "Turning on"
        torch.pending == TorchPending.TO_OFF -> "Turning off"
        torch.status == TorchStatus.BUSY -> "In use"
        torch.error == TorchError.NOT_CONFIRMED -> "Not confirmed"
        torch.error == TorchError.REFUSED || torch.error == TorchError.FAILED -> "Unavailable"
        torch.status == TorchStatus.ON -> "On"
        torch.status == TorchStatus.OFF -> "Off"
        else -> "Checking"
    }
    val ready = (torch.status == TorchStatus.ON || torch.status == TorchStatus.OFF) && torch.pending == TorchPending.NONE
    return Tile(
        icon = NovaIcon.FLASHLIGHT,
        label = label,
        description = "Flashlight, $label",
        active = torch.on,
        enabled = ready,
        onClick = { actions.toggleFlashlight() },
    )
}

private fun stopwatchTile(ui: IslandUi, actions: IslandActions): Tile = Tile(
    icon = NovaIcon.STOPWATCH,
    label = "Stopwatch",
    description = "Stopwatch",
    active = ui.stopwatch.phase != StopwatchPhase.NONE,
    enabled = true,
    onClick = { actions.stopwatchStart() },
)

private fun recordTile(ui: IslandUi, actions: IslandActions): Tile {
    val capture = ui.record?.capture
    val phase = capture?.phase ?: RecPhase.IDLE
    val waitingForSystem = phase == RecPhase.PREPARING && (capture?.countdownLeft ?: 0) == 0
    val label = when (phase) {
        RecPhase.RECORDING -> "Stop"
        RecPhase.PREPARING -> if (waitingForSystem) "Starting" else "Cancel"
        RecPhase.STOPPING -> "Saving"
        RecPhase.IDLE, RecPhase.SAVED, RecPhase.ERROR -> "Record"
    }
    return Tile(
        icon = if (phase == RecPhase.RECORDING) NovaIcon.STOP else NovaIcon.RECORD,
        label = label,
        description = "Screen recording, $label",
        active = ui.tools.recorderBusy,
        enabled = phase != RecPhase.STOPPING && !waitingForSystem,
        onClick = { actions.toggleScreenRecording() },
    )
}

// ---------------------------------------------------------------------------------------------
// Status line
// ---------------------------------------------------------------------------------------------

@Composable
private fun StatusStrip(ui: IslandUi, accent: Color) {
    Row(Modifier.fillMaxWidth().height(18.dp), verticalAlignment = Alignment.CenterVertically) {
        PillText(
            statusText(ui.tools.device),
            NovaType.Caption.copy(fontSize = 11.sp, lineHeight = 14.sp),
            modifier = Modifier.weight(1f),
            color = NovaColors.Text3,
        )
        if (ui.cards.size > 1) {
            CardDots(ui.cards.size, ui.cards.indexOf(CardKind.TOOLS).coerceAtLeast(0), accent)
        }
    }
}

private fun statusText(device: DeviceStatus): String {
    val net = when (device.network) {
        NetworkKind.WIFI -> "Wi-Fi"
        NetworkKind.CELLULAR -> "Mobile data"
        NetworkKind.ETHERNET -> "Ethernet"
        NetworkKind.OTHER -> "Connected"
        NetworkKind.OFFLINE -> "Offline"
        NetworkKind.UNKNOWN -> ""
    }
    val storage = if (device.freeBytes >= 0L) "${StorageFormat.bytes(device.freeBytes)} free" else ""
    return listOf(net, storage).filter { it.isNotEmpty() }.joinToString(" · ").ifEmpty { "Status unavailable" }
}
