package com.nedal.cinejoyremote.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nedal.cinejoyremote.domain.Device
import com.nedal.cinejoyremote.ui.theme.Spacing

// PC selector shown at the top of Downloads and Library: one row of
// device buttons, the selected one filled. Shared so both screens match.
@Composable
fun DeviceRow(devices: List<Device>, selected: String?, onSelect: (String) -> Unit) {
    if (devices.isEmpty()) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                "No PC seen yet — open the PC app and sign in with the same account.",
                Modifier.padding(Spacing.md),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(Spacing.sm))
        return
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        for (d in devices) {
            if (d.deviceId == selected) {
                Button(
                    onClick = {},
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = Spacing.lg,
                        vertical = 0.dp,
                    ),
                    modifier = Modifier.height(40.dp),
                ) { Text(d.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            } else {
                OutlinedButton(
                    onClick = { onSelect(d.deviceId) },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = Spacing.lg,
                        vertical = 0.dp,
                    ),
                    modifier = Modifier.height(40.dp),
                ) { Text(d.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
    Spacer(Modifier.height(Spacing.sm))
}
