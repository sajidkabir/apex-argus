package com.apexpredator.argus.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.apexpredator.argus.nostr.ChatMessage
import com.apexpredator.argus.nostr.ChatRepository
import com.apexpredator.argus.nostr.Conversation
import com.apexpredator.argus.nostr.FoundProfile
import com.apexpredator.argus.nostr.Identity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ChatSetupState {
    data object Loading : ChatSetupState
    data object NoIdentity : ChatSetupState
    data class Ready(val identity: Identity) : ChatSetupState
}

data class ChatUiState(
    val setup: ChatSetupState = ChatSetupState.Loading,
    val conversations: List<Conversation> = emptyList(),
    val refreshing: Boolean = false,
    val refreshError: String? = null,
    val searchQuery: String = "",
    val searching: Boolean = false,
    val searchResults: List<FoundProfile> = emptyList(),
    val searchError: String? = null,
    val activePeerHex: String? = null,
    val activePeerName: String? = null,
    val activeThread: List<ChatMessage> = emptyList(),
    val sending: Boolean = false,
    val sendError: String? = null,
    val usernameInput: String = "",
    val nsecInput: String = "",
    val setupBusy: Boolean = false,
    val setupError: String? = null
)

// Backs the Argus Chat tab: identity setup, people search, conversation
// list, and the active thread. All relay and disk work runs on
// Dispatchers.IO; the UI observes the single ui StateFlow.
class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = ChatRepository(application)
    private val _ui = MutableStateFlow(ChatUiState())
    val ui: StateFlow<ChatUiState> = _ui.asStateFlow()
    private var searchJob: Job? = null

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val identity = repo.getIdentity()
            if (identity == null) {
                _ui.update { it.copy(setup = ChatSetupState.NoIdentity) }
            } else {
                _ui.update {
                    it.copy(
                        setup = ChatSetupState.Ready(identity),
                        conversations = repo.cachedConversations()
                    )
                }
                repo.ensureConnected()
                refresh()
            }
        }
    }

    fun onUsernameChange(value: String) {
        _ui.update { it.copy(usernameInput = value, setupError = null) }
    }

    fun onNsecChange(value: String) {
        _ui.update { it.copy(nsecInput = value, setupError = null) }
    }

    fun createIdentity() {
        val name = _ui.value.usernameInput.trim()
        if (name.isEmpty()) {
            _ui.update { it.copy(setupError = "Pick a username first.") }
            return
        }
        _ui.update { it.copy(setupBusy = true, setupError = null) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val identity = repo.getOrCreateIdentity(name)
                _ui.update {
                    it.copy(
                        setup = ChatSetupState.Ready(identity),
                        setupBusy = false,
                        conversations = repo.cachedConversations()
                    )
                }
                refresh()
            } catch (e: Exception) {
                _ui.update {
                    it.copy(
                        setupBusy = false,
                        setupError = "Could not create the identity. Try again."
                    )
                }
            }
        }
    }

    fun importIdentity() {
        val nsec = _ui.value.nsecInput.trim()
        if (!nsec.startsWith("nsec1")) {
            _ui.update {
                it.copy(setupError = "That does not look like an nsec key (it starts with nsec1).")
            }
            return
        }
        val name = _ui.value.usernameInput.trim().ifEmpty { "Argus user" }
        _ui.update { it.copy(setupBusy = true, setupError = null) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val identity = repo.importIdentity(nsec, name)
                _ui.update {
                    it.copy(
                        setup = ChatSetupState.Ready(identity),
                        setupBusy = false,
                        nsecInput = "",
                        conversations = repo.cachedConversations()
                    )
                }
                refresh()
            } catch (e: Exception) {
                _ui.update {
                    it.copy(
                        setupBusy = false,
                        setupError = "Could not import that key. Check it and try again."
                    )
                }
            }
        }
    }

    fun refresh() {
        if (_ui.value.refreshing) return
        _ui.update { it.copy(refreshing = true, refreshError = null) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val convos = repo.refreshConversations()
                _ui.update { state ->
                    val active = state.activePeerHex
                    state.copy(
                        conversations = convos,
                        refreshing = false,
                        activeThread = active?.let { hex ->
                            convos.firstOrNull { it.peerHex == hex }?.messages
                        } ?: state.activeThread
                    )
                }
            } catch (e: Exception) {
                _ui.update {
                    it.copy(
                        refreshing = false,
                        refreshError = "Refresh failed. Check your connection."
                    )
                }
            }
        }
    }

    fun onSearchChange(query: String) {
        _ui.update { it.copy(searchQuery = query, searchError = null) }
        searchJob?.cancel()
        val q = query.trim()
        if (q.length < 2) {
            _ui.update { it.copy(searchResults = emptyList(), searching = false) }
            return
        }
        // A pasted npub is an exact lookup, not a text search.
        if (q.startsWith("npub1")) {
            _ui.update { it.copy(searching = true) }
            searchJob = viewModelScope.launch(Dispatchers.IO) {
                try {
                    val found = repo.lookupNpub(q)
                    _ui.update {
                        it.copy(
                            searching = false,
                            searchResults = listOfNotNull(found),
                            searchError = if (found == null) {
                                "No profile found for that Argus ID."
                            } else {
                                null
                            }
                        )
                    }
                } catch (e: Exception) {
                    _ui.update {
                        it.copy(searching = false, searchError = "Lookup failed. Try again.")
                    }
                }
            }
            return
        }
        _ui.update { it.copy(searching = true) }
        searchJob = viewModelScope.launch(Dispatchers.IO) {
            delay(600)
            try {
                val results = repo.searchPeople(q)
                _ui.update {
                    it.copy(
                        searching = false,
                        searchResults = results,
                        searchError = if (results.isEmpty()) "No people found." else null
                    )
                }
            } catch (e: Exception) {
                _ui.update {
                    it.copy(searching = false, searchError = "Search failed. Try again.")
                }
            }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _ui.update {
            it.copy(
                searchQuery = "",
                searchResults = emptyList(),
                searchError = null,
                searching = false
            )
        }
    }

    fun openThread(peerHex: String, peerName: String?) {
        if (!peerName.isNullOrEmpty()) {
            viewModelScope.launch(Dispatchers.IO) { repo.cachePeerName(peerHex, peerName) }
        }
        val thread = _ui.value.conversations
            .firstOrNull { it.peerHex == peerHex }?.messages ?: emptyList()
        _ui.update {
            it.copy(
                activePeerHex = peerHex,
                activePeerName = peerName,
                activeThread = thread,
                sendError = null
            )
        }
    }

    fun closeThread() {
        _ui.update {
            it.copy(
                activePeerHex = null,
                activePeerName = null,
                activeThread = emptyList(),
                sendError = null
            )
        }
    }

    fun sendMessage(text: String) {
        val peerHex = _ui.value.activePeerHex ?: return
        val body = text.trim()
        if (body.isEmpty() || _ui.value.sending) return
        _ui.update { it.copy(sending = true, sendError = null) }
        viewModelScope.launch(Dispatchers.IO) {
            val ok = repo.sendDm(peerHex, body)
            if (ok) {
                val convos = repo.cachedConversations()
                _ui.update { state ->
                    state.copy(
                        sending = false,
                        conversations = convos,
                        activeThread = convos
                            .firstOrNull { it.peerHex == peerHex }?.messages
                            ?: state.activeThread
                    )
                }
            } else {
                _ui.update {
                    it.copy(
                        sending = false,
                        sendError = "Could not send. Check your connection and try again."
                    )
                }
            }
        }
    }
}
