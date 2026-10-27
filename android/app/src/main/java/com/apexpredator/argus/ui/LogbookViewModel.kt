package com.apexpredator.argus.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.apexpredator.argus.data.LogbookStore
import com.apexpredator.argus.data.Sighting
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// Brain of the Logbook screen: the locally stored sightings, newest
// first, plus delete and clear. Nothing here touches the network.
class LogbookViewModel(application: Application) : AndroidViewModel(application) {

    private val store = LogbookStore(application)

    val entries: StateFlow<List<Sighting>> =
        store.entries.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList()
        )

    fun delete(entry: Sighting) {
        viewModelScope.launch { store.delete(entry.id) }
    }

    fun clearAll() {
        viewModelScope.launch { store.clear() }
    }
}
