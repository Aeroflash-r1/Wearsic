package com.wearsic.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wearsic.app.data.model.Track
import com.wearsic.app.data.repository.WearsicRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SearchUiState {
    data object Loading : SearchUiState
    data class Success(val tracks: List<Track>) : SearchUiState
    data class Error(val message: String) : SearchUiState
}

class SearchViewModel(
    private val repository: WearsicRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Loading)
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    fun search(query: String) {
        viewModelScope.launch {
            _uiState.value = SearchUiState.Loading
            val result = repository.search(query)
            _uiState.value = result.fold(
                onSuccess = { SearchUiState.Success(it) },
                onFailure = { SearchUiState.Error(it.message ?: "Unknown error") },
            )
        }
    }
}
