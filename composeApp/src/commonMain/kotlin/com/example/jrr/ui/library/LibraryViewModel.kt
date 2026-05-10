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

sealed class LibraryState {
    data object Loading : LibraryState()
    data class Browsing(val items: List<BrowseItem>, val path: List<BrowseItem>) : LibraryState()
    data class Files(val tracks: List<Track>, val path: List<BrowseItem>) : LibraryState()
    data class SearchResults(val tracks: List<Track>, val query: String) : LibraryState()
    data class Error(val message: String) : LibraryState()
}

class LibraryViewModel(
    private val jRiverService: JRiverService
) : ViewModel() {

    private val _uiState = MutableStateFlow<LibraryState>(LibraryState.Loading)
    val uiState: StateFlow<LibraryState> = _uiState.asStateFlow()

    private val browseStack = mutableListOf<BrowseItem>()

    init {
        loadRoot()
    }

    fun loadRoot() {
        browseStack.clear()
        browse("-1", "Library")
    }

    fun browse(id: String, name: String) {
        val newItem = BrowseItem(id, name)
        if (id != "-1") browseStack.add(newItem)
        
        viewModelScope.launch {
            _uiState.value = LibraryState.Loading
            val children = jRiverService.browseChildren(id)
            if (children.isNotEmpty()) {
                _uiState.value = LibraryState.Browsing(children, browseStack.toList())
            } else {
                // If no children, try fetching files
                val files = jRiverService.browseFiles(id)
                _uiState.value = LibraryState.Files(files, browseStack.toList())
            }
        }
    }

    fun navigateBack() {
        if (browseStack.isEmpty()) return
        
        browseStack.removeAt(browseStack.size - 1)
        val parent = browseStack.lastOrNull()
        val parentId = parent?.id ?: "-1"
        
        viewModelScope.launch {
            _uiState.value = LibraryState.Loading
            val children = jRiverService.browseChildren(parentId)
            if (children.isNotEmpty()) {
                _uiState.value = LibraryState.Browsing(children, browseStack.toList())
            } else {
                val files = jRiverService.browseFiles(parentId)
                _uiState.value = LibraryState.Files(files, browseStack.toList())
            }
        }
    }

    fun search(query: String) {
        if (query.isBlank()) {
            loadRoot()
            return
        }
        
        viewModelScope.launch {
            _uiState.value = LibraryState.Loading
            val results = jRiverService.search("[Media Type]=Audio ([Name] contains $query OR [Artist] contains $query OR [Album] contains $query)")
            _uiState.value = LibraryState.SearchResults(results, query)
        }
    }
}
