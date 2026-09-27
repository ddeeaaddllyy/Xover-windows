package com.xover.music.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring

/** Springs keep velocity when a hover, press, or tab transition is interrupted. */
internal fun <T> gentleMotion() = spring<T>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow,
)
