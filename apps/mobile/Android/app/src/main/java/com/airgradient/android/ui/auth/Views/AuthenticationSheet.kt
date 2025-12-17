package com.airgradient.android.ui.auth.Views

import android.app.Activity
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airgradient.android.FeatureFlags
import com.airgradient.android.R
import com.airgradient.android.domain.models.auth.AuthState
import com.airgradient.android.ui.auth.GOOGLE_SIGN_IN_CLIENT_ID
import com.airgradient.android.ui.auth.ViewModels.AuthenticationEvent
import com.airgradient.android.ui.auth.ViewModels.AuthenticationViewModel
import com.airgradient.android.ui.shared.Views.AgBottomSheetDefaults
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException

private const val TAG = "AuthenticationSheet"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthenticationSheet(
    onDismissRequest: () -> Unit,
    viewModel: AuthenticationViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scrollState = rememberScrollState()
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val googleSignInClient = remember(context) {
        val signInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(GOOGLE_SIGN_IN_CLIENT_ID)
            .requestEmail()
            .build()
        GoogleSignIn.getClient(context, signInOptions)
    }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                val idToken = account?.idToken
                if (idToken != null) {
                    viewModel.signInWithGoogle(idToken)
                } else {
                    Log.e(TAG, "Google sign-in returned null ID token")
                    viewModel.onGoogleSignInFailed(R.string.auth_google_sign_in_failed)
                }
            } catch (error: ApiException) {
                Log.e(TAG, "Google sign-in failed", error)
                viewModel.onGoogleSignInFailed(R.string.auth_google_sign_in_failed)
            }
        } else {
            Log.e(TAG, "Google sign-in cancelled or failed with code: ${result.resultCode}")
            viewModel.onGoogleSignInFailed(R.string.auth_google_sign_in_failed)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                AuthenticationEvent.SignedIn,
                AuthenticationEvent.SignedOut -> onDismissRequest()
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            viewModel.cancelGoogleSignIn()
            viewModel.onSheetDismissed()
            onDismissRequest()
        },
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(AgBottomSheetDefaults.MAX_EXPANDED_HEIGHT_FRACTION)
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 24.dp)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.auth_title),
                style = MaterialTheme.typography.headlineSmall
            )

            when (authState) {
                is AuthState.Authenticated -> {
                    val user = (authState as AuthState.Authenticated).user
                    val displayName = user.name.ifBlank { user.email }
                    Text(
                        text = stringResource(
                            R.string.auth_signed_in_as,
                            displayName
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (user.name.isNotBlank()) {
                        Text(
                            text = user.email,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Button(
                        onClick = viewModel::signOut,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = stringResource(R.string.auth_sign_out))
                    }
                }
                AuthState.SignedOut -> {
                    SignInForm(
                        uiState = uiState,
                        isGoogleSignInInProgress = uiState.isGoogleSignInInProgress,
                        isGoogleSignInEnabled = FeatureFlags.GOOGLE_SIGN_IN_ENABLED,
                        onEmailChange = {
                            viewModel.updateEmail(it)
                            viewModel.clearError()
                        },
                        onPasswordChange = {
                            viewModel.updatePassword(it)
                            viewModel.clearError()
                        },
                        onTogglePasswordVisibility = viewModel::togglePasswordVisibility,
                        onSignIn = {
                            focusManager.clearFocus()
                            viewModel.signIn()
                        },
                        onGoogleButtonClick = {
                            if (!FeatureFlags.GOOGLE_SIGN_IN_ENABLED || uiState.isGoogleSignInInProgress) {
                                return@SignInForm
                            }
                            focusManager.clearFocus()
                            viewModel.beginGoogleSignIn()
                            googleSignInClient.signOut().addOnCompleteListener { task ->
                                if (!task.isSuccessful) {
                                    Log.e(TAG, "Failed to sign out before Google sign-in", task.exception)
                                    viewModel.onGoogleSignInFailed(R.string.auth_google_sign_in_unavailable)
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.auth_google_sign_in_unavailable),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    return@addOnCompleteListener
                                }

                                runCatching {
                                    googleSignInLauncher.launch(googleSignInClient.signInIntent)
                                }.onFailure {
                                    viewModel.onGoogleSignInFailed(R.string.auth_google_sign_in_unavailable)
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.auth_google_sign_in_unavailable),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        },
                        onCancel = {
                            viewModel.cancelGoogleSignIn()
                            viewModel.onSheetDismissed()
                            onDismissRequest()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SignInForm(
    uiState: com.airgradient.android.ui.auth.ViewModels.AuthenticationUiState,
    isGoogleSignInInProgress: Boolean,
    isGoogleSignInEnabled: Boolean,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onTogglePasswordVisibility: () -> Unit,
    onSignIn: () -> Unit,
    onGoogleButtonClick: () -> Unit,
    onCancel: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = stringResource(R.string.auth_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = uiState.email,
            onValueChange = onEmailChange,
            label = { Text(stringResource(R.string.auth_email_label)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !uiState.isLoading,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            )
        )

        OutlinedTextField(
            value = uiState.password,
            onValueChange = onPasswordChange,
            label = { Text(stringResource(R.string.auth_password_label)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !uiState.isLoading,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { if (uiState.isSignInEnabled) onSignIn() }),
            visualTransformation = if (uiState.passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                val icon = if (uiState.passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                IconButton(onClick = onTogglePasswordVisibility, enabled = !uiState.isLoading) {
                    Icon(imageVector = icon, contentDescription = null)
                }
            }
        )

        uiState.errorMessageRes?.let { resId ->
            Text(
                text = stringResource(resId),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }

        if (isGoogleSignInEnabled) {
            OutlinedButton(
                onClick = onGoogleButtonClick,
                enabled = !uiState.isLoading && !isGoogleSignInInProgress,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isGoogleSignInInProgress) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(18.dp),
                        strokeWidth = 2.dp
                    )
                }
                Text(text = stringResource(R.string.auth_sign_in_google))
            }
        }

        Button(
            onClick = onSignIn,
            enabled = uiState.isSignInEnabled,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(18.dp),
                    strokeWidth = 2.dp
                )
            }
            Text(text = stringResource(R.string.auth_sign_in))
        }

        TextButton(
            onClick = onCancel,
            enabled = !uiState.isLoading && !isGoogleSignInInProgress,
            modifier = Modifier.align(Alignment.End)
        ) {
            Text(text = stringResource(R.string.action_cancel))
        }
    }
}
