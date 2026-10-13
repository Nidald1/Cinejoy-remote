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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nedal.cinejoyremote.presentation.DevicesViewModel
import com.nedal.cinejoyremote.presentation.EventsViewModel
import com.nedal.cinejoyremote.presentation.LibraryViewModel

@Composable
fun LibraryScreen(devicesVm: DevicesViewModel, vm: LibraryViewModel) {
    val devices by devicesVm.devices.collectAsState()
    val selected by devicesVm.selectedDeviceId.collectAsState()
    val shows by vm.shows.collectAsState()
    val error by vm.error.collectAsState()
    var url by remember { mutableStateOf("") }

    LaunchedEffect(devices) { devicesVm.autoSelect(devices) }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        DeviceRow(devices, selected, onSelect = devicesVm::select)
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = vm::clearError) { Text("dismiss") }
        }
        if (selected != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("cinejoy.pk watch link") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = {
                        vm.command(selected!!, "catalog-add", mapOf("url" to url.trim()))
                        url = ""
                    },
                    enabled = url.isNotBlank(),
                ) {
                    Text("Add")
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(shows, key = { it.showId }) { show ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(show.title, style = MaterialTheme.typography.titleSmall)
                        Text(
                            "${show.type} · ${show.episodes} episode(s)${show.year?.let { " · $it" } ?: ""}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                onClick = {
                                    selected?.let { vm.command(it, "queue-missing", mapOf("showId" to show.showId)) }
                                },
                            ) {
                                Text("Queue missing")
                            }
                            TextButton(
                                onClick = {
                                    selected?.let { vm.command(it, "catalog-remove", mapOf("showId" to show.showId)) }
                                },
                            ) {
                                Text("Remove")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EventsScreen(vm: EventsViewModel) {
    val events by vm.events.collectAsState()
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(events, key = { it.id }) { event ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(event.title, style = MaterialTheme.typography.titleSmall)
                    if (event.body.isNotBlank()) Text(event.body, style = MaterialTheme.typography.bodySmall)
                    Text(event.createdAt, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
