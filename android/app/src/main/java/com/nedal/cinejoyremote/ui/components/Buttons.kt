package com.nedal.cinejoyremote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ---------------------------------------------------------------- icon button
// Compact icon action with a full-size touch target. The old per-screen
// buttons passed Modifier.size(32..44.dp) to IconButton, which shrank the tap
// area below the 48dp accessibility minimum along with the visual. Here the
// button itself is floored at 48dp (defaultMinSize) and the small chip is
// drawn *inside* it: the look is unchanged, the tap area is not. Used by
// download task actions and episode-row actions.
@Composable
fun SmallIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    visualSize: Dp = 32.dp,
    iconSize: Dp = 17.dp,
    background: Color? = MaterialTheme.colorScheme.surfaceContainerHighest,
    shape: Shape = RoundedCornerShape(10.dp),
) {
    IconButton(
        onClick = onClick,
        // 48dp minimum touch target; the chip visual is the inner Box.
        modifier = modifier.defaultMinSize(48.dp, 48.dp),
    ) {
        Box(
            Modifier
                .size(visualSize)
                .clip(shape)
                .let { if (background != null) it.background(background) else it },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}
