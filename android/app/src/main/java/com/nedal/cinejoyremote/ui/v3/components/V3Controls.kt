package com.nedal.cinejoyremote.ui.v3.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.nedal.cinejoyremote.ui.v3.V3Space
import com.nedal.cinejoyremote.ui.v3.V3Type
import com.nedal.cinejoyremote.ui.v3.v3

/** Rounded filter pill (spec mockups: "Filters", "All", "Date added"). */
@Composable
fun V3Pill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
) {
    val c = v3
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(V3RadiusChip))
            .background(if (selected) c.accent.copy(alpha = 0.18f) else c.raised)
            .clickable(onClick = onClick)
            .padding(horizontal = V3Space.md, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            Icon(
                leadingIcon,
                contentDescription = null,
                tint = if (selected) c.accent else c.textSecondary,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            label,
            style = V3Type.caption12,
            color = when {
                selected -> c.accent
                else -> c.textSecondary
            },
        )
    }
}

private val V3RadiusChip = 14.dp

/**
 * Design-system bottom sheet: title + rows (optional checkmark for the
 * selected one, danger tint for destructive). Backdrop and handle per spec.
 *
 * [dismissable] = false vetoes gesture dismissal (backdrop tap, drag-down)
 * while the sheet's job is still running: the sheet snaps back instead of
 * closing, so a stray touch can't throw away an in-flight wait (e.g. the
 * source picker's up-to-120s Cinejoy capture) and force a reload. Back press
 * still closes it — the system back path ignores confirmValueChange — which
 * keeps a deliberate escape hatch.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun V3Sheet(
    title: String,
    onDismiss: () -> Unit,
    dismissable: Boolean = true,
    content: @Composable () -> Unit,
) {
    val c = v3
    // Hoisted so the lambda instance never changes: rememberModalBottomSheetState
    // keys its saveable state on it, and a fresh instance per recomposition would
    // re-init the sheet as Hidden mid-flight (re-animating on Loading -> Ready).
    // The lambda reads the live flag through the State, not a captured Boolean.
    val dismissableNow = rememberUpdatedState(dismissable)
    val confirmValueChange = remember<(SheetValue) -> Boolean> {
        { dismissableNow.value || it != SheetValue.Hidden }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
            confirmValueChange = confirmValueChange,
        ),
        containerColor = c.sheet,
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp, bottom = 4.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(c.divider),
            )
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = V3Space.gutter)
                .padding(bottom = 16.dp),
        ) {
            Text(title, style = V3Type.sectionTitle, color = c.text)
            Spacer(Modifier.height(V3Space.md))
            content()
        }
    }
}

/** One tappable row inside a [V3Sheet]. */
@Composable
fun SheetRow(
    label: String,
    onClick: () -> Unit,
    selected: Boolean = false,
    danger: Boolean = false,
) {
    val c = v3
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp, horizontal = 8.dp),
    ) {
        Text(
            label,
            style = V3Type.body14,
            color = if (danger) c.error else c.text,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = "Selected",
                tint = c.accent,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** 24dp glyph in a 48dp touch target — top-bar and card actions. */
@Composable
fun V3IconAction(
    icon: ImageVector,
    desc: String,
    modifier: Modifier = Modifier,
    tint: androidx.compose.ui.graphics.Color? = null,
    onClick: () -> Unit,
) {
    val c = v3
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = desc,
            tint = tint ?: c.textSecondary,
            modifier = Modifier.size(22.dp),
        )
    }
}

/** Small tinted icon chip used inside cards (28dp glyph, 48dp target). */
@Composable
fun V3CardIconAction(
    icon: ImageVector,
    desc: String,
    tint: Color,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.12f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = desc, tint = tint, modifier = Modifier.size(20.dp))
    }
}

/** Section label used above grouped rows (caption, tertiary). */
@Composable
fun V3SectionLabel(label: String, modifier: Modifier = Modifier) {
    Text(label, style = V3Type.caption, color = v3.textTertiary, modifier = modifier)
}

/** Hairline divider pulled to full gutter width. */
@Composable
fun V3Divider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(v3.divider))
}
