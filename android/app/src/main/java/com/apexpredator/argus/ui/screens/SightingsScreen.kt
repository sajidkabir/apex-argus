package com.apexpredator.argus.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.apexpredator.argus.nostr.PublicSighting
import com.apexpredator.argus.ui.SightingsViewModel
import com.apexpredator.argus.ui.components.EmptyState
import com.apexpredator.argus.ui.theme.ApexTextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// The public Argus sightings channel: recent posts from every Argus
// user, plus a form to post your own. Posts are public: anyone
// reading the channel on any Nostr client can see them, under the
// username on your Argus identity (shared with Argus Chat). A photo
// rides along only as a link you already host somewhere public;
// Argus uploads nothing. Hiding a poster is local only: it keeps
// their posts off this phone and changes nothing for anyone else.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SightingsScreen(
    vm: SightingsViewModel = viewModel(),
    onBack: () -> Unit
) {
    val state by vm.ui.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Community sightings") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    "Public channel (#argus-sighting). Anything you post " +
                        "here is public to every reader of the channel, " +
                        "under your Argus username. Do not post anything " +
                        "private. Argus hosts no photos: a photo link " +
                        "must already be public somewhere else.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ApexTextSecondary
                )
            }
            if (state.identity == null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "Pick a username to join the channel",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                "This is the same Argus identity used by " +
                                    "Argus Chat. If you already set one " +
                                    "up in Chat, it appears here.",
                                style = MaterialTheme.typography.bodySmall,
                                color = ApexTextSecondary
                            )
                            OutlinedTextField(
                                value = state.usernameInput,
                                onValueChange = { vm.onUsernameChange(it) },
                                label = { Text("Username") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Button(onClick = { vm.createIdentity() }) {
                                Text("Create identity")
                            }
                        }
                    }
                }
            } else {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "Post a sighting as ${state.identity?.username}",
                                style = MaterialTheme.typography.titleMedium
                            )
                            OutlinedTextField(
                                value = state.aircraftInput,
                                onValueChange = { vm.onAircraftChange(it) },
                                label = { Text("Callsign or aircraft") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = state.locationInput,
                                onValueChange = { vm.onLocationChange(it) },
                                label = { Text("Airport or location") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = state.noteInput,
                                onValueChange = { vm.onNoteChange(it) },
                                label = { Text("Short note (optional)") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = state.photoInput,
                                onValueChange = { vm.onPhotoChange(it) },
                                label = { Text("Photo link (optional, already public)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            state.publishError?.let { msg ->
                                Text(
                                    msg,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            if (state.publishDone) {
                                Text(
                                    "Posted. It appears in the feed below " +
                                        "once the relays return it.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ApexTextSecondary
                                )
                            }
                            Button(
                                onClick = { vm.publish() },
                                enabled = !state.publishing
                            ) {
                                Text(if (state.publishing) "Posting..." else "Post publicly")
                            }
                        }
                    }
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Recent sightings", style = MaterialTheme.typography.titleMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (state.hiddenPosters.isNotEmpty()) {
                            TextButton(onClick = { vm.unhideAll() }) {
                                Text("Unhide all (${state.hiddenPosters.size})")
                            }
                        }
                        OutlinedButton(
                            onClick = { vm.refresh() },
                            enabled = !state.loading
                        ) {
                            Text("Refresh")
                        }
                    }
                }
            }
            if (state.loading && state.sightings.isEmpty()) {
                item {
                    CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                }
            }
            state.loadError?.let { msg ->
                item {
                    Text(
                        msg,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            if (!state.loading && state.visibleSightings.isEmpty() && state.loadError == null) {
                item {
                    EmptyState(
                        title = "No sightings yet",
                        hint = "Be the first to post, or tap Refresh " +
                            "to check the channel again."
                    )
                }
            }
            item {
                OutlinedButton(onClick = onBack) { Text("Back to Tools") }
            }
            items(state.visibleSightings, key = { it.id }) { sighting ->
                SightingRow(sighting = sighting, onHide = { vm.hidePoster(sighting.pubkeyHex) })
            }
        }
    }
}

@Composable
private fun SightingRow(sighting: PublicSighting, onHide: () -> Unit) {
    val fmt = SimpleDateFormat("dd MMM HH:mm", Locale.US)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    sighting.authorName,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    fmt.format(Date(sighting.createdAt * 1000)),
                    style = MaterialTheme.typography.bodySmall,
                    color = ApexTextSecondary
                )
            }
            Text(
                "${sighting.aircraft} at ${sighting.location}",
                style = MaterialTheme.typography.bodyMedium
            )
            if (sighting.note.isNotEmpty()) {
                Text(sighting.note, style = MaterialTheme.typography.bodyMedium)
            }
            sighting.photoUrl?.let { url ->
                Text(
                    url,
                    style = MaterialTheme.typography.bodySmall,
                    color = ApexTextSecondary
                )
            }
            TextButton(onClick = onHide) {
                Text("Hide posts from this user")
            }
        }
    }
}
