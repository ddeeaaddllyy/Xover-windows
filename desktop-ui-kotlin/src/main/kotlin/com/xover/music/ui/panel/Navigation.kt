package com.xover.music.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xover.music.domain.SessionViewState
import java.awt.Window as AwtWindow
@Composable
internal fun TopBar(
    state: SessionViewState,
    awtWindow: AwtWindow,
    onCollapse: () -> Unit,
    onMinimize: () -> Unit,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowDrag(awtWindow),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(AccentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                StatusDot(state.connectionStatus())
            }
            Column {
                Text("Xover", style = MaterialTheme.typography.h6, fontWeight = FontWeight.Bold)
                Text(statusTitle(state), color = SecondaryText, style = MaterialTheme.typography.caption)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WindowControlButton("<", ControlGreen, onCollapse, shakeOnHover = true)
            WindowControlButton("-", ControlYellow, onMinimize, shakeOnHover = true)
            WindowControlButton("x", ControlRed, onClose, shakeOnHover = true)
        }
    }
}

@OptIn(ExperimentalStdlibApi::class)
@Composable
internal fun TabStrip(
    selectedTab: XoverTab,
    onTabSelected: (XoverTab) -> Unit,
) {
    val tabs = XoverTab.entries
    val tabSpacing = 5.dp
    val selectedIndex = tabs.indexOf(selectedTab).coerceAtLeast(0)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SoftLayerColor)
            .padding(5.dp),
    ) {
        val indicatorWidth = (maxWidth - tabSpacing * (tabs.size - 1)) / tabs.size
        val indicatorOffset by animateDpAsState(
            targetValue = (indicatorWidth + tabSpacing) * selectedIndex,
            animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
            label = "tabIndicatorOffset",
        )

        Box(
            modifier = Modifier
                .offset(x = indicatorOffset)
                .width(indicatorWidth)
                .height(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceColor),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(tabSpacing),
        ) {
            tabs.forEach { tab ->
                TabButton(
                    tab = tab,
                    selected = tab == selectedTab,
                    onClick = { onTabSelected(tab) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
internal fun TabButton(
    tab: XoverTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val foreground by animateColorAsState(if (selected) PrimaryText else SecondaryText, label = "tabForeground")
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val lift by animateDpAsState(
        targetValue = if (hovered && !selected) (-1).dp else 0.dp,
        animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
        label = "tabButtonLift",
    )

    Box(
        modifier = modifier
            .height(38.dp)
            .offset(y = lift)
            .clip(RoundedCornerShape(12.dp))
            .hoverable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(tab.title, color = foreground, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

