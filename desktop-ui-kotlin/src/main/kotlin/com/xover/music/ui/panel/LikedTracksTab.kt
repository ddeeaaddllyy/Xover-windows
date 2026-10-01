package com.xover.music.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xover.music.domain.LikedTrack

@Composable
internal fun LikedTracksTab(
    tracks: List<LikedTrack>,
    canAdd: Boolean,
    likesEnabled: Boolean,
    loading: Boolean,
    failed: Boolean,
    onRetry: () -> Unit,
    onAdd: (String) -> Unit,
    onToggleLike: (String, String) -> Unit,
) {
    ScreenIntro("The keepers.", "Good finds, saved for another listen.")
    Section("Liked tracks · ${tracks.size}") {
        Text("Your saved tracks, on this device.", color = SecondaryText, style = MaterialTheme.typography.caption)
        when {
            failed -> {
                Text("Could not update liked tracks. Please retry.", color = DangerColor)
                SoftButton("Retry", onRetry, enabled = !loading)
            }
            loading -> Text("Saving / loading…", color = SecondaryText)
            tracks.isEmpty() -> Text("Tap a heart or right-click a track to save it here.", color = SecondaryText)
        }
        if (!canAdd) {
            Text("Ask the host for playlist editing access to add tracks.", color = SecondaryText, style = MaterialTheme.typography.caption)
        }
        tracks.forEach { track ->
            key(track.sourceUrl()) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .background(SurfaceColor, RoundedCornerShape(8.dp))
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(track.title(), color = PrimaryText, fontWeight = FontWeight.Medium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(track.sourceUrl(), color = SecondaryText, style = MaterialTheme.typography.caption,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    LikeButton(true, likesEnabled) { onToggleLike(track.sourceUrl(), track.title()) }
                    SoftButton("Add", { onAdd(track.sourceUrl()) }, Modifier.width(54.dp), enabled = canAdd)
                }
            }
        }
    }
}
