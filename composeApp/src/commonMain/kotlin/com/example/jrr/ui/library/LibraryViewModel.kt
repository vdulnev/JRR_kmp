package com.example.jrr.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import arrow.core.raise.either
import co.touchlab.kermit.Logger
import com.example.jrr.domain.model.BrowseItem
import com.example.jrr.domain.model.DomainError
import com.example.jrr.domain.model.Track
import com.example.jrr.service.JRiverService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

data class LibraryUiState(
    val navigationStack: List<BrowseItem> = emptyList(),
    val children: List<BrowseItem> = emptyList(),
    val tracks: List<Track> = emptyList(),
    val searchResults: List<Track> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val error: DomainError? = null,
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

    private fun loadRoot() = browse("-1")

    fun browse(id: String, name: String = "Library") {
        viewModelScope.launch {
            logger.i { "Browsing ID: $id ($name)" }
            _uiState.update { it.copy(isLoading = true, error = null) }
            either {
                val children = jRiverService.browseChildren(id).bind()
                val tracks =
                    if (children.isEmpty()) jRiverService.browseFiles(id).bind() else emptyList()
                val newStack = if (id == "-1") {
                    listOf(BrowseItem("-1", "Library"))
                } else {
                    _uiState.value.navigationStack + BrowseItem(id, name)
                }
                Triple(newStack, children, tracks)
            }.fold(
                ifLeft = { err ->
                    logger.e { "browse($id): ${err.message}" }
                    _uiState.update { it.copy(isLoading = false, error = err) }
                },
                ifRight = { (newStack, children, tracks) ->
                    logger.d { "Browse successful. Found ${children.size} items and ${tracks.size} tracks. Stack depth: ${newStack.size}" }
                    _uiState.update {
                        it.copy(
                            navigationStack = newStack,
                            children = children,
                            tracks = tracks,
                            isLoading = false,
                            error = null,
                        )
                    }
                },
            )
        }
    }

    fun navigateBack() {
        val currentStack = _uiState.value.navigationStack
        if (currentStack.size <= 1) return
        val newStack = currentStack.dropLast(1)
        val previousItem = newStack.last()

        logger.i { "Navigating back to: ${previousItem.name} (${previousItem.id})" }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            either {
                val children = jRiverService.browseChildren(previousItem.id).bind()
                val tracks = if (children.isEmpty()) {
                    jRiverService.browseFiles(previousItem.id).bind()
                } else emptyList()
                children to tracks
            }.fold(
                ifLeft = { err ->
                    logger.e { "navigateBack(${previousItem.id}): ${err.message}" }
                    _uiState.update { it.copy(isLoading = false, error = err) }
                },
                ifRight = { (children, tracks) ->
                    _uiState.update {
                        it.copy(
                            navigationStack = newStack,
                            children = children,
                            tracks = tracks,
                            isLoading = false,
                            error = null,
                        )
                    }
                },
            )
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        if (query.length >= 3) {
            search(query)
        } else {
            _uiState.update { it.copy(searchResults = emptyList()) }
        }
    }

    fun search(query: String) {
        viewModelScope.launch {
            logger.d { "Searching for: $query" }
            jRiverService.search(query).fold(
                ifLeft = { err -> logger.w { "Search failed for '$query': ${err.message}" } },
                ifRight = { results ->
                    logger.d { "Search returned ${results.size} results" }
                    _uiState.update { it.copy(searchResults = results) }
                },
            )
        }
    }

    fun playTrack(track: Track) {
        logger.i { "Requesting playback of track: ${track.name}" }
        jRiverService.playTrack(track)
    }
}
