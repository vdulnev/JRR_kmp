package com.example.jrr.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.jrr.domain.model.BrowseItem
import com.example.jrr.domain.model.Track
import com.example.jrr.service.JRiverService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

data class LibraryUiState(
    val navigationStack: List<BrowseItem> = emptyList(),
    val children: List<BrowseItem> = emptyList(),
    val tracks: List<Track> = emptyList(),
    val searchResults: List<Track> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)

@KoinViewModel
class LibraryViewModel(
    private val jRiverService: JRiverService
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    init {
        loadRoot()
    }

    private fun loadRoot() {
        browse("-1")
    }

    fun browse(id: String, name: String = "Library") {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val children = jRiverService.browseChildren(id)
                val tracks = jRiverService.browseFiles(id)
                
                val newStack = if (id == "-1") {
                    listOf(BrowseItem("-1", "Library"))
                } else {
                    _uiState.value.navigationStack + BrowseItem(id, name)
                }

                _uiState.value = _uiState.value.copy(
                    navigationStack = newStack,
                    children = children,
                    tracks = tracks,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun navigateBack() {
        val currentStack = _uiState.value.navigationStack
        if (currentStack.size > 1) {
            val newStack = currentStack.dropLast(1)
            val previousItem = newStack.last()
            
            // We need to reload the previous level
            // This is a bit inefficient as we reload instead of caching, but works for now
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)
                try {
                    val children = jRiverService.browseChildren(previousItem.id)
                    val tracks = jRiverService.browseFiles(previousItem.id)
                    _uiState.value = _uiState.value.copy(
                        navigationStack = newStack,
                        children = children,
                        tracks = tracks,
                        isLoading = false
                    )
                } catch (e: Exception) {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
                }
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        if (query.length >= 3) {
            search(query)
        } else {
            _uiState.value = _uiState.value.copy(searchResults = emptyList())
        }
    }

    fun search(query: String) {
        viewModelScope.launch {
            try {
                val results = jRiverService.search(query)
                _uiState.value = _uiState.value.copy(searchResults = results)
            } catch (e: Exception) {
                // Ignore search errors for now
            }
        }
    }

    fun playTrack(track: Track) {
        jRiverService.playTrack(track)
    }
}
