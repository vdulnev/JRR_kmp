package com.example.jrr.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
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

    private val logger = Logger.withTag("LibraryViewModel")
    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    init {
        logger.d { "Initialized" }
        loadRoot()
    }

    private fun loadRoot() {
        browse("-1")
    }

    fun browse(id: String, name: String = "Library") {
        viewModelScope.launch {
            logger.i { "Browsing ID: $id ($name)" }
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val children = jRiverService.browseChildren(id)
                val tracks = jRiverService.browseFiles(id)
                
                val newStack = if (id == "-1") {
                    listOf(BrowseItem("-1", "Library"))
                } else {
                    _uiState.value.navigationStack + BrowseItem(id, name)
                }

                logger.d { "Browse successful. Found ${children.size} items and ${tracks.size} tracks. Stack depth: ${newStack.size}" }
                _uiState.value = _uiState.value.copy(
                    navigationStack = newStack,
                    children = children,
                    tracks = tracks,
                    isLoading = false
                )
            } catch (e: Exception) {
                logger.e(e) { "Browse failed for ID: $id" }
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun navigateBack() {
        val currentStack = _uiState.value.navigationStack
        if (currentStack.size > 1) {
            val newStack = currentStack.dropLast(1)
            val previousItem = newStack.last()
            
            logger.i { "Navigating back to: ${previousItem.name} (${previousItem.id})" }
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
                    logger.e(e) { "Reload after back navigation failed" }
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
            logger.d { "Searching for: $query" }
            try {
                val results = jRiverService.search(query)
                logger.d { "Search returned ${results.size} results" }
                _uiState.value = _uiState.value.copy(searchResults = results)
            } catch (e: Exception) {
                logger.w(e) { "Search failed for: $query" }
            }
        }
    }

    fun playTrack(track: Track) {
        logger.i { "Requesting playback of track: ${track.name}" }
        jRiverService.playTrack(track)
    }
}
