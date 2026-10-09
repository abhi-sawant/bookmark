package com.bookmark.account.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bookmark.account.AccountUiState
import com.bookmark.core.ui.components.OutlinedField
import com.bookmark.core.ui.components.PrimaryButton
import com.bookmark.core.ui.components.TextActionButton
import com.bookmark.core.ui.theme.BookmarkTheme

/**
 * Requests a reset email; the actual reset happens server-side via the emailed
 * link (opened in a browser), per the approved plan -- no in-app reset form.
 */
@Composable
fun ForgotPasswordScreen(
    state: AccountUiState,
    onEmailChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onNavigateToLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(top = 64.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Reset password",
            style = BookmarkTheme.text.screenTitle,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Enter your account email and we'll send a link to reset your password.",
            style = BookmarkTheme.text.rowSubtitle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedField(
            label = "Email",
            value = state.email,
            onValueChange = onEmailChange,
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Done,
        )

        if (state.forgotPasswordMessage != null) {
            Text(
                text = state.forgotPasswordMessage,
                style = BookmarkTheme.text.rowSubtitle,
                color = MaterialTheme.colorScheme.primary,
            )
        } else if (state.error != null) {
            Text(
                text = state.error,
                style = BookmarkTheme.text.rowSubtitle,
                color = MaterialTheme.colorScheme.error,
            )
        }

        if (state.isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        } else {
            PrimaryButton(text = "Send reset link", onClick = onSubmit, modifier = Modifier.fillMaxWidth())
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            TextActionButton(text = "Back to sign in", onClick = onNavigateToLogin)
        }
    }
}
