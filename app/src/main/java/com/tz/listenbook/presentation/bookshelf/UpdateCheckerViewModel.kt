package com.tz.listenbook.presentation.bookshelf

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tz.listenbook.data.remote.github.GitHubUpdateChecker
import com.tz.listenbook.data.remote.github.ReleaseInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UpdateUiState(
    val isChecking: Boolean = false,
    val updateInfo: ReleaseInfo? = null
)

@HiltViewModel
class UpdateCheckerViewModel @Inject constructor(
    private val updateChecker: GitHubUpdateChecker
) : ViewModel() {

    private val _uiState = MutableStateFlow(UpdateUiState())
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    fun checkForUpdate() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isChecking = true)
            val info = updateChecker.checkForUpdate()
            _uiState.value = _uiState.value.copy(isChecking = false, updateInfo = info)
        }
    }

    fun clearUpdateInfo() {
        _uiState.value = _uiState.value.copy(updateInfo = null)
    }
}
