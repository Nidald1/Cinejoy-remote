package com.nedal.cinejoyremote.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nedal.cinejoyremote.presentation.AuthViewModel

@Composable
fun AuthScreen(vm: AuthViewModel = viewModel()) {
    val ui by vm.ui.collectAsState()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val valid = email.contains("@") && password.length >= 6

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("CineJoy Remote", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text("Sign in with the same account you use on your PC.", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = email, onValueChange = { email = it; vm.clearError() },
            label = { Text("Email") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = password, onValueChange = { password = it; vm.clearError() },
            label = { Text("Password (6+ chars)") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
        )
        ui.error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        if (ui.pendingConfirm) {
            Spacer(Modifier.height(8.dp))
            Text("Account created — check your email for the confirmation link, then sign in.")
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { vm.signIn(email, password) },
            enabled = valid && !ui.busy, modifier = Modifier.fillMaxWidth(),
        ) { Text(if (ui.busy) "Working…" else "Sign in") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { vm.signUp(email, password) },
            enabled = valid && !ui.busy, modifier = Modifier.fillMaxWidth(),
        ) { Text("Create account") }
    }
}
