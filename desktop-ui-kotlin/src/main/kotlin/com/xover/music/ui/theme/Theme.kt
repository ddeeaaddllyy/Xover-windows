package com.xover.music.ui

import androidx.compose.material.Typography
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.darkColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
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

internal fun xoverColors() = darkColors(
    primary = AccentColor,
    secondary = WarmColor,
    background = Color.Transparent,
    surface = SurfaceColor,
    onPrimary = InkColor,
    onSecondary = InkColor,
    onBackground = PrimaryText,
    onSurface = PrimaryText,
)

@Composable
internal fun XoverTheme(content: @Composable () -> Unit) {
    MaterialTheme(colors = xoverColors(), typography = xoverTypography()) {
        CompositionLocalProvider(LocalContentColor provides PrimaryText, content = content)
    }
}

@Composable
internal fun readableTextFieldColors() = TextFieldDefaults.outlinedTextFieldColors(
    textColor = PrimaryText,
    disabledTextColor = SecondaryText,
    placeholderColor = SecondaryText,
    disabledPlaceholderColor = SecondaryText,
    focusedLabelColor = AccentColor,
    unfocusedLabelColor = SecondaryText,
    disabledLabelColor = SecondaryText,
    cursorColor = AccentColor,
    focusedBorderColor = AccentColor,
    unfocusedBorderColor = BorderColor,
    disabledBorderColor = BorderColor,
)

internal fun xoverTypography() = Typography(
    defaultFontFamily = FontFamily.SansSerif,
    h4 = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Light, fontSize = 32.sp, letterSpacing = (-1).sp),
    h5 = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, letterSpacing = (-0.5).sp),
    h6 = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    subtitle1 = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 15.sp),
    subtitle2 = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 13.sp),
    body1 = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 21.sp),
    body2 = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp, lineHeight = 18.sp),
    button = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
    caption = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 11.sp, lineHeight = 16.sp),
    overline = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 10.sp, letterSpacing = 1.4.sp),
)

internal enum class XoverTab(val title: String) {
    SETUP("Session"),
    PLAYER("Deck"),
    LIKED("Library"),
    STATUS("Signal"),
    MORE("Settings"),
}

internal enum class SetupRole(val title: String) {
    HOST("Host"),
    CLIENT("Client"),
}

internal const val DefaultPort = 47321
internal const val AppIconResource = "icon/XoverIcon.png"

internal val ExpandedSize = DpSize(430.dp, 720.dp)
internal val CollapsedSize = DpSize(344.dp, 132.dp)
internal val ErrorWindowSize = DpSize(660.dp, 640.dp)
internal val ErrorTimestampFormatter = DateTimeFormatter
    .ofPattern("yyyy-MM-dd HH:mm:ss z")
    .withZone(ZoneId.systemDefault())

internal val SurfaceColor = Color(0xFF171A1C)
/** Keeps the panel opaque while the saved visibility value controls its shade. */
internal fun opaquePanelColor(visibility: Float): Color = lerp(
    Color(0xFF0E1012), SurfaceColor,
    ((visibility - 0.78f) / 0.20f).coerceIn(0f, 1f),
)
internal val SoftLayerColor = Color(0xFF222629)
internal val BorderColor = Color(0xFF343A3D)
internal val PrimaryText = Color(0xFFF0ECE3)
internal val SecondaryText = Color(0xFFBDC4C5)
internal val AccentColor = Color(0xFFE3B778)
internal val WarmColor = Color(0xFFE3B778)
internal val DangerColor = Color(0xFFE08B87)
internal val InkColor = Color(0xFF181B1D)
internal val ControlGreen = Color(0xFF9FB8A0)
internal val ControlYellow = AccentColor
internal val ControlRed = DangerColor
