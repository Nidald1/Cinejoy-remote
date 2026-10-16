package com.nedal.cinejoyremote.ui.v3.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween as tweenAlias
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nedal.cinejoyremote.ui.v3.V3Limits
import com.nedal.cinejoyremote.ui.v3.V3Space
import com.nedal.cinejoyremote.ui.v3.V3Type
import com.nedal.cinejoyremote.ui.v3.v3

/* ------------------------------------------------------------------ TopBar */

/**
 * 56dp slim top bar: title 22sp bold on the left; plain icon actions, a small
 * connection dot and the circular avatar on the right. No container shapes
 * behind icons (spec §2).
 */
@Composable
fun V3TopBar(
    title: String,
    online: Boolean,
    avatarLetter: Char,
    onAvatar: () -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    val c = v3
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(V3Limits.TOP_BAR_DP.dp)
            .padding(horizontal = V3Space.gutter),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(V3Space.sm))
        }
        Text(
            title,
            style = V3Type.screenTitle,
            color = c.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        actions()
        // Bare status dot — semantics carry "PC online/offline" for a11y.
        Box(
            Modifier
                .padding(horizontal = V3Space.md)
                .size(10.dp)
                .clip(CircleShape)
                .background(if (online) c.success else c.error)
                .semantics { contentDescription = if (online) "PC online" else "PC offline" },
        )
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(c.accent)
                .clickable(onClick = onAvatar),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                avatarLetter.toString(),
                color = Color.White,
                style = V3Type.itemTitle,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/* ------------------------------------------------------------- UnderlineTabs */

data class V3Tab(val key: String, val label: String, val badge: Int = 0)

/** 40dp underline tabs: selected = active-blue text + 3dp rounded underline. */
@Composable
fun UnderlineTabs(
    tabs: List<V3Tab>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = v3
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(V3Limits.TABS_DP.dp)
            .padding(horizontal = V3Space.gutter),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(V3Space.xl),
    ) {
        for (tab in tabs) {
            val active = tab.key == selected
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onSelect(tab.key) }
                    .padding(bottom = 6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        tab.label,
                        style = V3Type.chip,
                        color = if (active) c.text else c.textTertiary,
                    )
                    if (tab.badge > 0) {
                        Spacer(Modifier.width(6.dp))
                        CountBadge(tab.badge, selected = active)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier
                        .width(28.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (active) c.accent else Color.Transparent),
                )
            }
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
}

/** Small count pill: blue when its tab is selected, dark gray otherwise. */
@Composable
fun CountBadge(count: Int, selected: Boolean) {
    val c = v3
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) c.accent else c.raised)
            .padding(horizontal = 7.dp, vertical = 1.dp),
    ) {
        Text(
            count.toString(),
            style = V3Type.caption.copy(fontWeight = FontWeight.Bold),
            color = if (selected) Color.White else c.textSecondary,
        )
    }
}

/* ---------------------------------------------------------------- BottomNav */

data class NavSpec(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val badge: Int = 0,
    val dot: Boolean = false,
)

/**
 * Opaque dark bottom nav: 64dp touch row + gesture inset = 80dp total.
 * 24dp icons, 11sp labels; active item gets blue icon+label plus a 3dp line
 * pinned to the bar's top edge. Downloads carries a red count badge,
 * Notifications a red dot.
 */
@Composable
fun V3BottomNav(
    items: List<NavSpec>,
    current: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = v3
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(c.background),
    ) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
        Row(
            Modifier
                .fillMaxWidth()
                .height(V3Limits.BOTTOM_TOUCH_DP.dp),
            verticalAlignment = Alignment.Top,
        ) {
            for (item in items) {
                val active = item.route == current
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onSelect(item.route) }
                        .padding(top = 4.dp),
                ) {
                    // 3dp active indicator on the bar's top edge.
                    Box(
                        Modifier
                            .width(26.dp)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (active) c.accent else Color.Transparent),
                    )
                    Spacer(Modifier.height(5.dp))
                    Box {
                        Icon(
                            item.icon,
                            contentDescription = item.label,
                            tint = if (active) c.accent else c.textSecondary,
                            modifier = Modifier.size(24.dp),
                        )
                        if (item.badge > 0) {
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 10.dp, y = (-5).dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(c.badge)
                                    .padding(horizontal = 5.dp),
                            ) {
                                Text(
                                    item.badge.toString(),
                                    style = V3Type.caption.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                                    color = Color.White,
                                )
                            }
                        }
                        if (item.dot) {
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 13.dp, y = (-4).dp)
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(c.badge),
                            )
                        }
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        item.label,
                        style = V3Type.tabLabel,
                        color = if (active) c.accent else c.textSecondary,
                        maxLines = 1,
                    )
                }
            }
        }
        // Gesture inset so the total height lands at 80dp on gesture nav.
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        Box(Modifier.fillMaxWidth().height(16.dp)) // floor: keeps 64+16=80dp min
    }
}

/* -------------------------------------------------- slide-in/out helper for bars */

@Composable
fun BarVisibility(visible: Boolean, fromBottom: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = if (fromBottom) expandVertically(tweenAlias(180)) + fadeIn(tweenAlias(180))
        else expandVertically(tweenAlias(180)) + fadeIn(tweenAlias(180)),
        exit = if (fromBottom) shrinkVertically(tweenAlias(160)) + fadeOut(tweenAlias(160))
        else shrinkVertically(tweenAlias(160)) + fadeOut(tweenAlias(160)),
    ) { content() }
}

/* ---------------------------------------------------------------- states */

/** Centered empty state: tinted circle icon, title, one line, actions slot. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actions: (@Composable () -> Unit)? = null,
) {
    val c = v3
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(c.accent.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(84.dp)
                    .clip(CircleShape)
                    .background(c.accent.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = c.accent, modifier = Modifier.size(36.dp))
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(title, style = V3Type.sectionTitle.copy(fontSize = 22.sp), color = c.text)
        Spacer(Modifier.height(10.dp))
        Text(
            body,
            style = V3Type.body14,
            color = c.textSecondary,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlignCenter,
        )
        if (actions != null) {
            Spacer(Modifier.height(20.dp))
            actions()
        }
    }
}

private val TextAlignCenter = androidx.compose.ui.text.style.TextAlign.Center

/** Error state: red-tinted icon, message, Retry button. */
@Composable
fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val c = v3
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            androidx.compose.material.icons.Icons.Rounded.Warning,
            null,
            tint = c.error,
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.height(14.dp))
        Text("Something went wrong", style = V3Type.sectionTitle, color = c.text)
        Spacer(Modifier.height(6.dp))
        Text(
            message,
            style = V3Type.body,
            color = c.textSecondary,
            textAlign = TextAlignCenter,
        )
        Spacer(Modifier.height(16.dp))
        Box(
            Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(c.accent)
                .clickable(onClick = onRetry)
                .padding(horizontal = 24.dp, vertical = 10.dp),
        ) {
            Text("Retry", color = Color.White, style = V3Type.chip)
        }
    }
}

/** Pulsing skeleton block — keeps layout shape while loading. */
@Composable
fun Skeleton(modifier: Modifier = Modifier, corner: androidx.compose.ui.unit.Dp = 12.dp) {
    val c = v3
    val t = rememberInfiniteTransition(label = "sk")
    val alpha by t.animateFloat(
        initialValue = 0.35f, targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "sk-a",
    )
    Box(
        modifier
            .clip(RoundedCornerShape(corner))
            .background(
                Brush.linearGradient(
                    listOf(c.raised.copy(alpha = alpha), c.surface.copy(alpha = alpha)),
                ),
            ),
    )
}
