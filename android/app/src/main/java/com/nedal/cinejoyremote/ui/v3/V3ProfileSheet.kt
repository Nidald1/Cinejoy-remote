package com.nedal.cinejoyremote.ui.v3

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
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Profile sheet behind the top-bar avatar: account email, the PC picker with
 * per-device online dots, and Sign out behind a confirmation dialog
 * (spec §3.1 — logout is not a top-level button).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun V3ProfileSheet(
    email: String?,
    devices: List<Triple<String, Boolean, Boolean>>, // name, online, selected
    onSelectDevice: (String) -> Unit,
    onSignOut: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = v3
    var confirm by remember { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
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
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(c.accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        (email?.firstOrNull() ?: '●').uppercaseChar().toString(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(email ?: "Signed in", style = V3Type.itemTitle, color = c.text)
                    Text("CineJoy account", style = V3Type.caption, color = c.textTertiary)
                }
            }

            Spacer(Modifier.height(10.dp))
            Text("PC", style = V3Type.caption, color = c.textTertiary)
            if (devices.isEmpty()) {
                Text(
                    "No PC paired yet — open the CineJoy app on your computer and sign in.",
                    style = V3Type.body,
                    color = c.textSecondary,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            for ((name, online, selected) in devices) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelectDevice(name) }
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                ) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (online) c.success else c.error),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(name, style = V3Type.body14, color = c.text, modifier = Modifier.weight(1f))
                    if (selected) {
                        Icon(
                            androidx.compose.material.icons.Icons.Rounded.Check,
                            contentDescription = "Selected",
                            tint = c.accent,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { confirm = true }
                    .padding(vertical = 14.dp, horizontal = 8.dp),
            ) {
                Icon(
                    androidx.compose.material.icons.Icons.Rounded.Logout,
                    contentDescription = null,
                    tint = c.error,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text("Sign out", style = V3Type.body14, color = c.error)
            }
        }
    }

    if (confirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirm = false },
            containerColor = c.sheet,
            title = { Text("Sign out?", color = c.text) },
            text = { Text("You will need the account email and password to sign back in.", color = c.textSecondary) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    confirm = false; onSignOut()
                }) { Text("Sign out", color = c.error) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirm = false }) {
                    Text("Cancel", color = c.textSecondary)
                }
            },
        )
    }
}
