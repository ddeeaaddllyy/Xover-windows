package com.xover.music.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween

/** Short, bounded transitions; controls settle without wobbling or changing layout. */
internal fun <T> gentleMotion() = tween<T>(durationMillis = 190, easing = FastOutSlowInEasing)
