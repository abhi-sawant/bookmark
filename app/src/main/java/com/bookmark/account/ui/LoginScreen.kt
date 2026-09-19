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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.bookmark.account.AccountUiState
import com.bookmark.core.ui.components.OutlinedField
import com.bookmark.core.ui.components.PrimaryButton
import com.bookmark.core.ui.components.TextActionButton
import com.bookmark.core.ui.theme.BookmarkTheme

/**
 * Signed-out entry point for sync (spec: Settings' "Sign in to sync across
 * devices" row). Plain M3 form built from this app's own shared field/button
 * components rather than `OutlinedTextField`, matching the rest of the app.
 */
@Composable
fun LoginScreen(
    state: AccountUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onLogin: () -> Unit,
    onNavigateToSignUp: () -> Unit,
    onNavigateToForgotPassword: () -> Unit,
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
            text = "Sign in",
            style = BookmarkTheme.text.screenTitle,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Sync your bookmarks and categories across every device.",
            style = BookmarkTheme.text.rowSubtitle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedField(
            label = "Email",
            value = state.email,
            onValueChange = onEmailChange,
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Next,
        )
        OutlinedField(
            label = "Password",
            value = state.password,
            onValueChange = onPasswordChange,
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
            visualTransformation = PasswordVisualTransformation(),
        )

        if (state.error != null) {
            Text(
                text = state.error,
                style = BookmarkTheme.text.rowSubtitle,
                color = MaterialTheme.colorScheme.error,
            )
        }

        if (state.isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        } else {
            PrimaryButton(text = "Sign in", onClick = onLogin, modifier = Modifier.fillMaxWidth())
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            TextActionButton(text = "Forgot password?", onClick = onNavigateToForgotPassword)
            TextActionButton(text = "Create an account", onClick = onNavigateToSignUp)
        }
    }
}
