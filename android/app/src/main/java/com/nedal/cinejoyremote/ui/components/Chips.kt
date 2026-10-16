package com.nedal.cinejoyremote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// Small pill for metadata (year, genre, "12 ep", rating) — one definition
// for every screen (library tiles, title headers, genres).
@Composable
fun MetaChip(text: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
        )
    }
}

// 8dp dot for episode/download states — colour by state, one mapping.
@Composable
fun StateDot(state: String, modifier: Modifier = Modifier) {
    val color: Color = when (state) {
        "done" -> MaterialTheme.colorScheme.primary
        "partial" -> MaterialTheme.colorScheme.tertiary
        "downloading" -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.outline
    }
    Box(
        modifier
            .size(8.dp)
            .clip(RoundedCornerShape(50))
            .background(color),
    )
}
