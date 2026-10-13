package com.nedal.cinejoyremote.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nedal.cinejoyremote.domain.Device
import com.nedal.cinejoyremote.domain.DownloadTask
import com.nedal.cinejoyremote.presentation.DevicesViewModel
import com.nedal.cinejoyremote.presentation.DownloadsViewModel

@Composable
fun DownloadsScreen(devicesVm: DevicesViewModel, vm: DownloadsViewModel) {
    val devices by devicesVm.devices.collectAsState()
    val selected by devicesVm.selectedDeviceId.collectAsState()
    val tasks by vm.tasks.collectAsState()
    val error by vm.error.collectAsState()

    LaunchedEffect(devices) { devicesVm.autoSelect(devices) }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        DeviceRow(devices, selected, onSelect = devicesVm::select)
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = vm::clearError) { Text("dismiss") }
        }
        if (selected != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.command(selected!!, "pause-all") }, modifier = Modifier.weight(1f)) {
                    Text("Pause all")
                }
                OutlinedButton(onClick = { vm.command(selected!!, "resume-all") }, modifier = Modifier.weight(1f)) {
                    Text("Resume all")
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.command(selected!!, "queue-missing") }, modifier = Modifier.weight(1f)) {
                    Text("Queue missing")
                }
                OutlinedButton(onClick = { vm.command(selected!!, "queue-clear") }, modifier = Modifier.weight(1f)) {
                    Text("Clear queue")
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        val active = tasks.filter { it.state in listOf("queued", "resolving", "downloading", "stopping") }
        val rest = tasks.filter { it.state !in listOf("queued", "resolving", "downloading", "stopping") }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(active + rest, key = { it.taskId }) { task ->
                TaskCard(task, onAction = { action -> selected?.let { vm.command(it, action, task.taskId) } })
            }
        }
    }
}

@Composable
fun DeviceRow(devices: List<Device>, selected: String?, onSelect: (String) -> Unit) {
    if (devices.isEmpty()) {
        Text("No PC online yet — open the PC app and sign in with the same account.")
        return
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (d in devices) {
            if (d.deviceId == selected) {
                Button(onClick = {}) { Text(d.name) }
            } else {
                OutlinedButton(onClick = { onSelect(d.deviceId) }) { Text(d.name) }
            }
        }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
fun TaskCard(task: DownloadTask, onAction: (String) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(task.title, style = MaterialTheme.typography.titleSmall)
            if (task.episodeLabel.isNotBlank() && task.episodeLabel != task.title) {
                Text(task.episodeLabel, style = MaterialTheme.typography.bodySmall)
            }
            Text(
                "${task.state}${task.percent?.let { " · ${"%.1f".format(it)}%" } ?: ""}" +
                    (task.speed?.let { " · ${formatBytes(it)}/s" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
            )
            task.percent?.let { LinearProgressIndicator(progress = { (it / 100).toFloat() }, modifier = Modifier.fillMaxWidth()) }
            task.lastErr?.takeIf { task.state == "error" }?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (task.state) {
                    "downloading", "queued", "resolving" -> TextButton(onClick = { onAction("pause") }) { Text("Pause") }
                    "paused" -> {
                        TextButton(onClick = { onAction("resume") }) { Text("Resume") }
                        TextButton(onClick = { onAction("forget") }) { Text("Remove") }
                    }
                    "error" -> TextButton(onClick = { onAction("retry") }) { Text("Retry") }
                    "done" -> TextButton(onClick = { onAction("forget") }) { Text("Clear") }
                }
            }
        }
    }
}

fun formatBytes(n: Long): String {
    if (n < 1024) return "$n B"
    val units = listOf("KB", "MB", "GB")
    var v = n / 1024.0
    var u = 0
    while (v >= 1024 && u < units.size - 1) { v /= 1024; u++ }
    return "%.1f %s".format(v, units[u])
}
