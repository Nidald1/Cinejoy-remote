package com.nedal.cinejoyremote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nedal.cinejoyremote.ui.theme.StatusColors

// ============================================================================
// Design v2 shell chrome.
//
// The old shell wore its branding like a jacket: a 34dp logo tile, the app
// name in 16sp bold, a two-line status chip and a logout icon stacked into a
// ~140dp header on every screen. This file replaces all of it with the slim
// cinematic chrome the v2 spec demands: a single 56dp row (small title, a
// bare status dot, contextual icons, an avatar) over a 64dp bottom bar that
// slides away while scrolling.
// ============================================================================

/** One contextual icon shown in the top bar (sort, filter, search, …). */
data class ChromeAction(
    val icon: ImageVector,
    val contentDescription: String,
    val onClick: () -> Unit,
)

/**
 * Screens publish their top-bar icons through this controller; the shell
 * renders whatever the visible screen registered. Ownership tokens keep a
 * screen that is *exiting* (Navigation Compose keeps it composed during the
 * transition) from wiping the actions the incoming screen just set.
 */
class TopBarController {
    var actions by mutableStateOf<List<ChromeAction>>(emptyList())
    var title by mutableStateOf("CineJoy Remote")
    internal var owner: Any? = null
}

val LocalTopBarController = androidx.compose.runtime.compositionLocalOf { TopBarController() }

/** Register this screen's title + top-bar icons. Call from screen bodies. */
@Composable
fun ProvideTopBar(title: String, actions: List<ChromeAction>) {
    val controller = LocalTopBarController.current
    val token = remember { Any() }
    SideEffect {
        controller.owner = token
        controller.title = title
        controller.actions = actions
    }
    DisposableEffect(Unit) {
        onDispose {
            if (controller.owner === token) {
                controller.actions = emptyList()
                controller.owner = null
            }
        }
    }
}

// ------------------------------------------------------------------ top bar

/**
 * Slim top bar — 56dp, no branding block, no text status chip. The PC state
 * is a bare dot (green = online, red = offline) exactly as the spec asks; the
 * avatar opens the profile sheet where sign-out and the PC picker live.
 */
@Composable
fun CineTopBar(
    controller: TopBarController,
    online: Boolean,
    avatarLetter: Char,
    onAvatarClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            // Draw below the status bar, never under it (edge-to-edge).
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(56.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            controller.title,
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        // Bare status dot — no "PC online" text. Semantics carry the meaning.
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (online) StatusColors.Success else MaterialTheme.colorScheme.error)
                .semantics { contentDescription = if (online) "PC online" else "PC offline" },
        )
        Spacer(Modifier.width(6.dp))
        controller.actions.forEach { action ->
            IconButton(onClick = action.onClick) {
                Icon(
                    action.icon,
                    contentDescription = action.contentDescription,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Spacer(Modifier.width(2.dp))
        IconButton(onClick = onAvatarClick) {
            Box(
                Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    avatarLetter.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

// --------------------------------------------------------------- bottom bar

data class TabSpec(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val badge: Int = 0,
)

/**
 * Slim bottom nav — 64dp tall including labels, 24dp icons, 11sp labels and
 * a small pill indicator under the active item. Translucent surface so
 * content scrolling beneath reads as one continuous plane.
 */
@Composable
fun SlimBottomNav(
    items: List<TabSpec>,
    current: String,
    onSelect: (String) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            // Sit above the gesture bar (edge-to-edge).
            .windowInsetsPadding(WindowInsets.navigationBars)
            .height(64.dp)
            .background(Color(0xF712151C)),
    ) {
        items.forEach { item ->
            val selected = current == item.route ||
                (item.route == "library" && current.startsWith("show/"))
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable { onSelect(item.route) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box {
                    Icon(
                        item.icon,
                        contentDescription = item.label,
                        modifier = Modifier.size(24.dp),
                        tint = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (item.badge > 0) {
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.error),
                        )
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    item.label,
                    fontSize = 11.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier
                        .width(if (selected) 18.dp else 0.dp)
                        .height(2.5.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}

// ------------------------------------------------------------ profile sheet

/**
 * Avatar sheet: who is signed in, which PC this phone controls (when more
 * than one exists), and sign-out behind a confirmation. This is where the
 * old top-level logout icon and the old name pill went.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSheet(
    email: String?,
    devices: List<Pair<String, Boolean>>, // name to online flag
    selectedDevice: String?,
    onSelectDevice: (String) -> Unit,
    onSignOut: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        (email?.firstOrNull() ?: '●').uppercaseChar().toString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        email ?: "Signed in",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "CineJoy Remote",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (devices.size > 1) {
                Spacer(Modifier.height(16.dp))
                Text(
                    "This phone controls",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                devices.forEach { (name, online) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSelectDevice(name) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (online) StatusColors.Success else MaterialTheme.colorScheme.error),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (name == selectedDevice) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onSignOut)
                    .padding(vertical = 12.dp),
            ) {
                Text(
                    "Sign out",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

// ------------------------------------------------------------- bottom sheet

/** Standard design-v2 bottom sheet: 24dp top radius, title, content rows. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CineSheet(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        Column(
            Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp),
            content = content,
        )
    }
}

/** One tappable row inside a CineSheet, with a check on the selected item. */
@Composable
fun SheetOption(
    label: String,
    selected: Boolean = false,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = when {
                danger -> MaterialTheme.colorScheme.error
                selected -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = "selected",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

// -------------------------------------------------------------- empty state

/** Vector-first empty state: small icon in a tonal circle, one line, action. */
@Composable
fun CineEmpty(
    icon: ImageVector,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier.padding(top = 48.dp, bottom = 24.dp).padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(12.dp))
            androidx.compose.material3.TextButton(onClick = onAction) {
                Text(actionLabel, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
