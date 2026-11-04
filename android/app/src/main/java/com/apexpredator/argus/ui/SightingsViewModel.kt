package com.apexpredator.argus.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.apexpredator.argus.data.PrefsStore
import com.apexpredator.argus.nostr.ChatRepository
import com.apexpredator.argus.nostr.Identity
import com.apexpredator.argus.nostr.PublicSighting
import com.apexpredator.argus.nostr.SightingsRepository
import com.apexpredator.argus.nostr.validateSightingInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SightingsUiState(
    val identity: Identity? = null,
    val sightings: List<PublicSighting> = emptyList(),
    val hiddenPosters: Set<String> = emptySet(),
    val loading: Boolean = false,
    val loadError: String? = null,
    val usernameInput: String = "",
    val aircraftInput: String = "",
    val locationInput: String = "",
    val noteInput: String = "",
    val photoInput: String = "",
    val publishing: Boolean = false,
    val publishError: String? = null,
    val publishDone: Boolean = false
) {
    // Hiding is local only: hidden posters' posts stay on the relays,
    // they just do not render on this phone.
    val visibleSightings: List<PublicSighting>
        get() = sightings.filterNot { it.pubkeyHex.lowercase() in hiddenPosters }
}

// Backs the public Sightings screen: the shared Argus channel feed,
// the publish form, and local hide-by-poster. Relay work runs on
// Dispatchers.IO; the UI observes the single ui StateFlow.
class SightingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = SightingsRepository()
    private val chatRepo = ChatRepository(application)
    private val prefs = PrefsStore(application)
    private val _ui = MutableStateFlow(SightingsUiState())
    val ui: StateFlow<SightingsUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _ui.update { it.copy(identity = chatRepo.getIdentity()) }
            prefs.hiddenSightingPosters.collect { hidden ->
                _ui.update { it.copy(hiddenPosters = hidden) }
            }
        }
        refresh()
    }

    fun onUsernameChange(value: String) {
        _ui.update { it.copy(usernameInput = value) }
    }

    fun onAircraftChange(value: String) {
        _ui.update { it.copy(aircraftInput = value, publishError = null, publishDone = false) }
    }

    fun onLocationChange(value: String) {
        _ui.update { it.copy(locationInput = value, publishError = null, publishDone = false) }
    }

    fun onNoteChange(value: String) {
        _ui.update { it.copy(noteInput = value, publishError = null, publishDone = false) }
    }

    fun onPhotoChange(value: String) {
        _ui.update { it.copy(photoInput = value, publishError = null, publishDone = false) }
    }

    fun createIdentity() {
        val name = _ui.value.usernameInput.trim()
        if (name.isEmpty()) {
            _ui.update { it.copy(publishError = "Pick a username first.") }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val identity = chatRepo.getOrCreateIdentity(name)
                _ui.update { it.copy(identity = identity, publishError = null) }
            } catch (_: Exception) {
                _ui.update { it.copy(publishError = "Could not create the identity. Try again.") }
            }
        }
    }

    fun refresh() {
        if (_ui.value.loading) return
        _ui.update { it.copy(loading = true, loadError = null) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val sightings = repo.fetchSightings()
                _ui.update { it.copy(sightings = sightings, loading = false) }
            } catch (_: Exception) {
                _ui.update {
                    it.copy(
                        loading = false,
                        loadError = "Could not load sightings. Check your connection."
                    )
                }
            }
        }
    }

    fun publish() {
        val state = _ui.value
        val identity = state.identity
        if (identity == null) {
            _ui.update { it.copy(publishError = "Set a username first, above.") }
            return
        }
        val error = validateSightingInput(
            state.aircraftInput, state.locationInput, state.photoInput
        )
        if (error != null) {
            _ui.update { it.copy(publishError = error) }
            return
        }
        if (state.publishing) return
        _ui.update { it.copy(publishing = true, publishError = null, publishDone = false) }
        viewModelScope.launch(Dispatchers.IO) {
            val ok = repo.publishSighting(
                identity,
                state.aircraftInput.trim(),
                state.locationInput.trim(),
                state.noteInput.trim(),
                state.photoInput.trim()
            )
            _ui.update {
                it.copy(
                    publishing = false,
                    publishDone = ok,
                    publishError = if (ok) null else
                        "Could not publish. Check your connection and try again.",
                    aircraftInput = if (ok) "" else it.aircraftInput,
                    locationInput = if (ok) "" else it.locationInput,
                    noteInput = if (ok) "" else it.noteInput,
                    photoInput = if (ok) "" else it.photoInput
                )
            }
            if (ok) refresh()
        }
    }

    fun hidePoster(pubkeyHex: String) {
        viewModelScope.launch(Dispatchers.IO) {
            prefs.toggleHiddenSightingPoster(pubkeyHex)
        }
    }

    fun unhideAll() {
        viewModelScope.launch(Dispatchers.IO) {
            prefs.clearHiddenSightingPosters()
        }
    }
}
