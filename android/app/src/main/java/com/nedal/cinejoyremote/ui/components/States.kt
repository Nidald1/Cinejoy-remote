package com.nedal.cinejoyremote.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nedal.cinejoyremote.ui.theme.Radius
import com.nedal.cinejoyremote.ui.theme.Spacing

// ---------------------------------------------------------------- empty state
// Emoji + title + message (+ optional action), used by Downloads, Library
// and Notifications so "nothing here" always looks the same.
@Composable
fun EmptyState(
    emoji: String,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier.padding(horizontal = Spacing.xl, vertical = Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(emoji, style = MaterialTheme.typography.displayMedium)
        Spacer(Modifier.height(Spacing.sm))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(Spacing.xs))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Spacer(Modifier.height(Spacing.lg))
            action()
        }
    }
}

// ---------------------------------------------------------------- error state
// One failure block: message in the error colour + optional Retry.
@Composable
fun ErrorState(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
        if (onRetry != null) {
            OutlinedButton(onClick = onRetry) { Text("Retry") }
        }
    }
}

// ---------------------------------------------------------------- skeletons
// Gently pulsing placeholder block — loading that keeps the layout's shape.
@Composable
fun SkeletonBox(
    modifier: Modifier = Modifier,
    shape: Shape = androidx.compose.foundation.shape.RoundedCornerShape(Radius.sm),
) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.40f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeletonAlpha",
    )
    Box(
        modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = alpha)),
    )
}

// Title-page skeleton: backdrop, poster + text lines, then episode rows —
// shown while a show's mirror rows first arrive (replaces a bare spinner).
@Composable
fun DetailSkeleton(modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.listGap)) {
        SkeletonBox(
            Modifier.fillMaxWidth().height(300.dp),
            shape = RectangleShape,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            SkeletonBox(Modifier.width(110.dp).height(165.dp))
            Column(
                Modifier.weight(1f).padding(top = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                SkeletonBox(Modifier.fillMaxWidth().height(24.dp))
                SkeletonBox(Modifier.width(140.dp).height(14.dp))
                SkeletonBox(Modifier.fillMaxWidth().height(14.dp))
                SkeletonBox(Modifier.width(180.dp).height(14.dp))
            }
        }
        Spacer(Modifier.height(Spacing.xs))
        repeat(3) {
            SkeletonBox(
                Modifier.fillMaxWidth().height(88.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(Radius.md),
            )
        }
    }
}
