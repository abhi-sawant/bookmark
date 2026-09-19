package com.bookmark.account

import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bookmark.account.data.AuthState
import com.bookmark.account.data.ApiException
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Shared form state for Login/SignUp/ForgotPassword -- one screen's worth at a time. */
data class AccountUiState(
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val forgotPasswordMessage: String? = null,
)

private const val MIN_PASSWORD_LENGTH = 8

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
) : ViewModel() {

    val authState: StateFlow<AuthState> = accountRepository.authState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AuthState.SignedOut)

    private val _uiState = MutableStateFlow(AccountUiState())
    val uiState: StateFlow<AccountUiState> = _uiState.asStateFlow()

    fun onEmailChange(value: String) = _uiState.update { it.copy(email = value, error = null) }
    fun onPasswordChange(value: String) = _uiState.update { it.copy(password = value, error = null) }
    fun onConfirmPasswordChange(value: String) =
        _uiState.update { it.copy(confirmPassword = value, error = null) }

    fun register(onSuccess: () -> Unit) {
        val state = _uiState.value
        val validationError = validate(state, requireConfirm = true)
        if (validationError != null) {
            _uiState.update { it.copy(error = validationError) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            accountRepository.register(state.email.trim(), state.password)
                .onSuccess {
                    _uiState.update { it.copy(isLoading = false) }
                    onSuccess()
                }
                .onFailure { throwable ->
                    _uiState.update { it.copy(isLoading = false, error = throwable.userMessage()) }
                }
        }
    }

    fun login(onSuccess: () -> Unit) {
        val state = _uiState.value
        val validationError = validate(state, requireConfirm = false)
        if (validationError != null) {
            _uiState.update { it.copy(error = validationError) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            accountRepository.login(state.email.trim(), state.password)
                .onSuccess {
                    _uiState.update { it.copy(isLoading = false) }
                    onSuccess()
                }
                .onFailure { throwable ->
                    _uiState.update { it.copy(isLoading = false, error = throwable.userMessage()) }
                }
        }
    }

    fun forgotPassword() {
        val email = _uiState.value.email.trim()
        if (!isValidEmail(email)) {
            _uiState.update { it.copy(error = "Enter a valid email address.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            accountRepository.forgotPassword(email)
                .onSuccess { message ->
                    _uiState.update { it.copy(isLoading = false, forgotPasswordMessage = message) }
                }
                .onFailure { throwable ->
                    _uiState.update { it.copy(isLoading = false, error = throwable.userMessage()) }
                }
        }
    }

    fun logout() = viewModelScope.launch {
        accountRepository.logout()
    }

    fun clearError() = _uiState.update { it.copy(error = null) }

    fun clearForgotPasswordMessage() = _uiState.update { it.copy(forgotPasswordMessage = null) }

    /** Resets the form -- called when navigating away from an auth screen. */
    fun resetForm() {
        _uiState.value = AccountUiState()
    }

    private fun validate(state: AccountUiState, requireConfirm: Boolean): String? {
        if (!isValidEmail(state.email.trim())) return "Enter a valid email address."
        if (state.password.length < MIN_PASSWORD_LENGTH) {
            return "Password must be at least $MIN_PASSWORD_LENGTH characters."
        }
        if (requireConfirm && state.password != state.confirmPassword) {
            return "Passwords don't match."
        }
        return null
    }

    private fun isValidEmail(email: String): Boolean =
        email.isNotBlank() && Patterns.EMAIL_ADDRESS.matcher(email).matches()

    private fun Throwable.userMessage(): String = when (this) {
        is ApiException -> message ?: "Something went wrong. Try again."
        else -> "Couldn't reach the server. Check your connection and try again."
    }
}
