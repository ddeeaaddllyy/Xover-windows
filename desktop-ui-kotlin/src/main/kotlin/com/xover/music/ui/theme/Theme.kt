package com.xover.music.ui

import androidx.compose.material.Typography
import androidx.compose.material.lightColors
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xover.music.domain.ConnectionStatus
import com.xover.music.domain.SessionViewState
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.max
import androidx.compose.ui.text.platform.Font as DesktopFont

internal val XoverFontFamily: FontFamily = FontFamily(
    DesktopFont("fonts/Xover-text.ttf", FontWeight.Normal),
    DesktopFont("fonts/Xover-text.ttf", FontWeight.Medium),
    DesktopFont("fonts/Xover-text.ttf", FontWeight.SemiBold),
    DesktopFont("fonts/Xover-text.ttf", FontWeight.Bold),
)

internal fun statusTitle(state: SessionViewState): String = when (state.connectionStatus()) {
    ConnectionStatus.DISCONNECTED -> "ready to connect"
    ConnectionStatus.HOSTING -> "hosting on VPN"
    ConnectionStatus.CONNECTING -> "connecting"
    ConnectionStatus.CONNECTED -> "synced session"
    ConnectionStatus.ERROR -> "needs attention"
}

internal fun statusColor(status: ConnectionStatus): Color = when (status) {
    ConnectionStatus.CONNECTED -> AccentColor
    ConnectionStatus.ERROR -> DangerColor
    ConnectionStatus.HOSTING,
    ConnectionStatus.CONNECTING -> WarmColor
    ConnectionStatus.DISCONNECTED -> SecondaryText
}

internal fun formatMillis(value: Long): String {
    val safeValue = max(0L, value)
    val totalSeconds = safeValue / 1_000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(minutes, seconds)
}

internal fun xoverColors() = lightColors(
    primary = AccentColor,
    secondary = WarmColor,
    background = Color.Transparent,
    surface = SurfaceColor,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = PrimaryText,
    onSurface = PrimaryText,
)

internal fun xoverTypography() = Typography(
    defaultFontFamily = XoverFontFamily,
    h1 = TextStyle(fontFamily = XoverFontFamily, fontWeight = FontWeight.Bold, fontSize = 96.sp),
    h2 = TextStyle(fontFamily = XoverFontFamily, fontWeight = FontWeight.Bold, fontSize = 60.sp),
    h3 = TextStyle(fontFamily = XoverFontFamily, fontWeight = FontWeight.Bold, fontSize = 48.sp),
    h4 = TextStyle(fontFamily = XoverFontFamily, fontWeight = FontWeight.Bold, fontSize = 34.sp),
    h5 = TextStyle(fontFamily = XoverFontFamily, fontWeight = FontWeight.Bold, fontSize = 24.sp),
    h6 = TextStyle(fontFamily = XoverFontFamily, fontWeight = FontWeight.Bold, fontSize = 20.sp),
    subtitle1 = TextStyle(fontFamily = XoverFontFamily, fontWeight = FontWeight.Medium, fontSize = 16.sp),
    subtitle2 = TextStyle(fontFamily = XoverFontFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp),
    body1 = TextStyle(fontFamily = XoverFontFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp),
    body2 = TextStyle(fontFamily = XoverFontFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp),
    button = TextStyle(fontFamily = XoverFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    caption = TextStyle(fontFamily = XoverFontFamily, fontWeight = FontWeight.Normal, fontSize = 12.sp),
    overline = TextStyle(fontFamily = XoverFontFamily, fontWeight = FontWeight.Medium, fontSize = 10.sp),
)

internal enum class XoverTab(val title: String) {
    SETUP("Setup"),
    PLAYER("Player"),
    STATUS("Status"),
    MORE("More"),
}

internal enum class SetupRole(val title: String) {
    HOST("Host"),
    CLIENT("Client"),
}

internal const val DefaultPort = 47321
internal const val AppIconResource = "icon/XoverIcon.png"

internal val ExpandedSize = DpSize(430.dp, 720.dp)
internal val CollapsedSize = DpSize(84.dp, 212.dp)
internal val ErrorWindowSize = DpSize(660.dp, 640.dp)
internal val ErrorTimestampFormatter = DateTimeFormatter
    .ofPattern("yyyy-MM-dd HH:mm:ss z")
    .withZone(ZoneId.systemDefault())

internal val SurfaceColor = Color(0xFFFFFFFF)
internal val SoftLayerColor = Color(0xFFF2F5F4)
internal val BorderColor = Color(0xFFDDE4E1)
internal val PrimaryText = Color(0xFF18201D)
internal val SecondaryText = Color(0xFF6E7A75)
internal val AccentColor = Color(0xFF45A996)
internal val WarmColor = Color(0xFFE1AA45)
internal val DangerColor = Color(0xFFD55D63)
internal val ControlGreen = Color(0xFF42C66B)
internal val ControlYellow = Color(0xFFE8B84F)
internal val ControlRed = Color(0xFFE85C5C)
