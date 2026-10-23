package com.apexpredator.argus.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.apexpredator.argus.nostr.ChatMessage
import com.apexpredator.argus.nostr.Conversation
import com.apexpredator.argus.nostr.FoundProfile
import com.apexpredator.argus.nostr.Identity
import com.apexpredator.argus.ui.ChatSetupState
import com.apexpredator.argus.ui.ChatUiState
import com.apexpredator.argus.ui.ChatViewModel
import com.apexpredator.argus.ui.theme.ApexTextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
private val dayFormat = SimpleDateFormat("d MMM", Locale.getDefault())

private fun formatTime(tsSeconds: Long): String =
    timeFormat.format(Date(tsSeconds * 1000))

private fun formatListTime(tsSeconds: Long): String {
    val date = Date(tsSeconds * 1000)
    return if (System.currentTimeMillis() - tsSeconds * 1000 < 24 * 3600 * 1000) {
        timeFormat.format(date)
    } else {
        dayFormat.format(date)
    }
}

private fun truncateNpub(npub: String): String =
    if (npub.length <= 20) npub else npub.take(12) + "..." + npub.takeLast(6)

// Copy glyph drawn with Canvas: material-icons-core (the only icon
// dependency) does not include ContentCopy, so the copy button draws
// its own two-sheet icon.
@Composable
private fun CopyIcon() {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    val paper = MaterialTheme.colorScheme.surface
    Canvas(modifier = Modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        val stroke = w * 0.075f
        val radius = CornerRadius(w * 0.09f, w * 0.09f)
        // Back sheet, offset down and right.
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.36f, h * 0.28f),
            size = Size(w * 0.48f, h * 0.56f),
            cornerRadius = radius,
            style = Stroke(width = stroke)
        )
        // Front sheet, offset up and left, filled to cover the overlap.
        drawRoundRect(
            color = paper,
            topLeft = Offset(w * 0.16f, h * 0.16f),
            size = Size(w * 0.48f, h * 0.56f),
            cornerRadius = radius
        )
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.16f, h * 0.16f),
            size = Size(w * 0.48f, h * 0.56f),
            cornerRadius = radius,
            style = Stroke(width = stroke)
        )
    }
}

// The Chat tab: Nostr-based direct messaging. Three modes: identity
// setup (first run), the conversation list with people search, and the
// message thread.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(vm: ChatViewModel = viewModel()) {
    val state by vm.ui.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Argus Chat") },
                navigationIcon = {
                    if (state.activePeerHex != null) {
                        IconButton(onClick = { vm.closeThread() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    }
                },
                actions = {
                    if (state.setup is ChatSetupState.Ready && state.activePeerHex == null) {
                        IconButton(
                            onClick = { vm.refresh() },
                            enabled = !state.refreshing
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (val setup = state.setup) {
                ChatSetupState.Loading -> LoadingView()
                ChatSetupState.NoIdentity -> SetupView(state, vm)
                is ChatSetupState.Ready -> if (state.activePeerHex != null) {
                    ThreadView(state, vm)
                } else {
                    MainChatView(state, vm, setup.identity)
                }
            }
        }
    }
}

@Composable
private fun LoadingView() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun SetupView(state: ChatUiState, vm: ChatViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Create your Argus ID",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Argus Chat runs on Nostr: no accounts, no servers, no " +
                "phone number. Your secret key is generated on this " +
                "device and never leaves it. Pick a username to start.",
            style = MaterialTheme.typography.bodyMedium,
            color = ApexTextSecondary
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = state.usernameInput,
            onValueChange = { vm.onUsernameChange(it) },
            label = { Text("Username") },
            singleLine = true,
            enabled = !state.setupBusy,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = { vm.createIdentity() },
            enabled = !state.setupBusy && state.usernameInput.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (state.setupBusy) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .width(20.dp)
                        .height(20.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text("Create my Argus ID")
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "Already have a Nostr key? Import it below.",
            style = MaterialTheme.typography.bodySmall,
            color = ApexTextSecondary
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = state.nsecInput,
            onValueChange = { vm.onNsecChange(it) },
            label = { Text("nsec key (optional)") },
            singleLine = true,
            enabled = !state.setupBusy,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = { vm.importIdentity() },
            enabled = !state.setupBusy && state.nsecInput.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Import key")
        }
        if (state.setupError != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                state.setupError,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun MainChatView(state: ChatUiState, vm: ChatViewModel, identity: Identity) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(2000)
            copied = false
        }
    }
    Column(modifier = Modifier.fillMaxSize()) {
        // Profile header.
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.Person,
                    contentDescription = "Profile",
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        identity.username,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        "Argus ID: " + truncateNpub(identity.npub),
                        style = MaterialTheme.typography.bodySmall,
                        color = ApexTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (copied) {
                        Text(
                            "Copied to clipboard",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                IconButton(onClick = {
                    clipboard.setText(AnnotatedString(identity.npub))
                    copied = true
                }) {
                    CopyIcon()
                }
            }
        }
        // People search.
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = { vm.onSearchChange(it) },
            label = { Text("Find people by username or Argus ID") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )
        if (state.searchQuery.trim().length >= 2) {
            Box(modifier = Modifier.weight(1f)) {
                SearchResults(state, vm)
            }
        } else {
            Box(modifier = Modifier.weight(1f)) {
                ConversationList(state, vm)
            }
        }
        // Honest encryption note.
        Text(
            "Direct messages use NIP-04 encryption. Relays see " +
                "message metadata, never the content.",
            style = MaterialTheme.typography.labelSmall,
            color = ApexTextSecondary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun SearchResults(state: ChatUiState, vm: ChatViewModel) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (state.searching) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return
        }
        if (state.searchError != null && state.searchResults.isEmpty()) {
            Text(
                state.searchError,
                style = MaterialTheme.typography.bodyMedium,
                color = ApexTextSecondary,
                modifier = Modifier.padding(24.dp)
            )
            return
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(state.searchResults, key = { it.pubkeyHex }) { profile ->
                FoundProfileRow(profile) {
                    vm.clearSearch()
                    vm.openThread(profile.pubkeyHex, profile.name.ifEmpty { null })
                }
            }
        }
    }
}

@Composable
private fun FoundProfileRow(profile: FoundProfile, onOpen: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Person,
                contentDescription = null,
                tint = ApexTextSecondary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    profile.name.ifEmpty { "Unnamed" },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (profile.about.isNotEmpty()) {
                    Text(
                        profile.about,
                        style = MaterialTheme.typography.bodySmall,
                        color = ApexTextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (profile.npub.isNotEmpty()) {
                    Text(
                        truncateNpub(profile.npub),
                        style = MaterialTheme.typography.labelSmall,
                        color = ApexTextSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun ConversationList(state: ChatUiState, vm: ChatViewModel) {
    if (state.refreshing && state.conversations.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }
    Column(modifier = Modifier.fillMaxSize()) {
        if (state.refreshError != null) {
            Text(
                state.refreshError,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
        if (state.conversations.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No conversations yet. Search for someone above " +
                        "to start chatting.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ApexTextSecondary
                )
            }
            return
        }
        Text(
            "Conversations",
            style = MaterialTheme.typography.titleSmall,
            color = ApexTextSecondary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(state.conversations, key = { it.peerHex }) { convo ->
                ConversationRow(convo) {
                    vm.openThread(convo.peerHex, convo.peerName)
                }
            }
        }
    }
}

@Composable
private fun ConversationRow(convo: Conversation, onOpen: () -> Unit) {
    val last = convo.messages.lastOrNull()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    convo.peerName?.ifEmpty { null }
                        ?: truncateNpub(convo.peerNpub.ifEmpty { convo.peerHex }),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (last != null) {
                    Text(
                        (if (last.dir == "out") "You: " else "") + last.text,
                        style = MaterialTheme.typography.bodySmall,
                        color = ApexTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (last != null) {
                Text(
                    formatListTime(last.ts),
                    style = MaterialTheme.typography.labelSmall,
                    color = ApexTextSecondary
                )
            }
        }
    }
}

@Composable
private fun ThreadView(state: ChatUiState, vm: ChatViewModel) {
    val focusManager = LocalFocusManager.current
    var draft by remember(state.activePeerHex) { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(state.activeThread.size) {
        if (state.activeThread.isNotEmpty()) {
            listState.animateScrollToItem(state.activeThread.size - 1)
        }
    }
    Column(modifier = Modifier.fillMaxSize()) {
        // Peer header.
        Text(
            state.activePeerName?.ifEmpty { null } ?: "Chat",
            style = MaterialTheme.typography.titleSmall,
            color = ApexTextSecondary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        if (state.activeThread.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No messages yet. Say hello.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ApexTextSecondary
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(state.activeThread, key = { it.ts.toString() + it.dir + it.text.hashCode() }) { msg ->
                    MessageBubble(msg)
                }
            }
        }
        if (state.sendError != null) {
            Text(
                state.sendError,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = { Text("Message") },
                modifier = Modifier.weight(1f),
                maxLines = 4,
                enabled = !state.sending,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    if (draft.isNotBlank()) {
                        vm.sendMessage(draft)
                        draft = ""
                        focusManager.clearFocus()
                    }
                })
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = {
                    if (draft.isNotBlank()) {
                        vm.sendMessage(draft)
                        draft = ""
                        focusManager.clearFocus()
                    }
                },
                enabled = !state.sending && draft.isNotBlank()
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun MessageBubble(msg: ChatMessage) {
    val isOut = msg.dir == "out"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isOut) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (isOut) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            modifier = Modifier
                .widthIn(max = 280.dp)
                .padding(vertical = 2.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(
                    msg.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isOut) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Text(
                    formatTime(msg.ts),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isOut) {
                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                    } else {
                        ApexTextSecondary
                    }
                )
            }
        }
    }
}
