package com.nedal.cinejoyremote.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nedal.cinejoyremote.domain.AuthRepository
import com.nedal.cinejoyremote.domain.AuthState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AuthUiState(
    val busy: Boolean = false,
    val error: String? = null,
    val pendingConfirm: Boolean = false,
)

class AuthViewModel(
    private val auth: AuthRepository = ServiceLocator.authRepository,
) : ViewModel() {

    val authState: StateFlow<AuthState> =
        auth.authState.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AuthState.Loading)

    private val _ui = kotlinx.coroutines.flow.MutableStateFlow(AuthUiState())
    val ui: StateFlow<AuthUiState> = _ui

    fun signIn(email: String, password: String) = viewModelScope.launch {
        _ui.value = AuthUiState(busy = true)
        val result = ServiceLocator.signIn(email, password)
        _ui.value = AuthUiState(error = result.exceptionOrNull()?.message)
    }

    fun signUp(email: String, password: String) = viewModelScope.launch {
        _ui.value = AuthUiState(busy = true)
        val result = ServiceLocator.signUp(email, password)
        _ui.value = AuthUiState(
            error = result.exceptionOrNull()?.message,
            pendingConfirm = result.getOrNull() == true,
        )
    }

    fun signOut() = viewModelScope.launch { auth.signOut() }

    fun clearError() {
        _ui.value = _ui.value.copy(error = null, pendingConfirm = false)
    }
}
