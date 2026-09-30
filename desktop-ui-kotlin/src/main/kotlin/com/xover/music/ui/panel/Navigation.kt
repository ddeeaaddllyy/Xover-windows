package com.xover.music.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
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
import androidx.compose.ui.unit.sp
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
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f).windowDrag(awtWindow),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("xover", fontFamily = XoverFontFamily, fontSize = 22.sp, color = PrimaryText)
                    Text("/ 01", style = MaterialTheme.typography.overline, color = AccentColor)
                }
                Text("PRIVATE LISTENING", style = MaterialTheme.typography.overline, color = SecondaryText)
            }

        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WindowControlButton("<", ControlGreen, onCollapse)
            WindowControlButton("-", ControlYellow, onMinimize)
            WindowControlButton("x", ControlRed, onClose)
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
            .height(42.dp),
    ) {
        val indicatorWidth = (maxWidth - tabSpacing * (tabs.size - 1)) / tabs.size
        val indicatorOffset by animateDpAsState(
            targetValue = (indicatorWidth + tabSpacing) * selectedIndex,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
            label = "tabIndicatorOffset",
        )

        Box(
            modifier = Modifier
                .offset(x = indicatorOffset)
                .width(indicatorWidth)
                .offset(y = 39.dp)
                .height(2.dp)
                .background(AccentColor),
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
    val foreground by animateColorAsState(if (selected) AccentColor else SecondaryText, label = "tabForeground")
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val hoverColor by animateColorAsState(
        if (hovered && !selected) SoftLayerColor else androidx.compose.ui.graphics.Color.Transparent,
        animationSpec = gentleMotion(), label = "tabHover",
    )

    Box(
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(hoverColor)
            .hoverable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(tab.title, color = foreground, style = MaterialTheme.typography.body2, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}
