package com.example.ncloud.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun AuthScreen(
    baseUrl: String,
    loading: Boolean,
    error: String?,
    onBaseUrlChange: (String) -> Unit,
    onSubmit: (String, String, Boolean) -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBg)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = AppPanel),
            shape = AppAuthShape,
            border = BorderStroke(1.dp, AppPurpleSoft),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Ncloud",
                    color = AppText,
                    style = MaterialTheme.typography.headlineMedium
                )

                Text(
                    text = "Sign in or create an account",
                    color = AppMuted,
                    style = MaterialTheme.typography.bodyMedium
                )

                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = onBaseUrlChange,
                    label = { Text("API base URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )

                if (error != null) {
                    Text(
                        text = error,
                        color = AppError,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        enabled = !loading,
                        onClick = { onSubmit(username, password, false) },
                        colors = ButtonDefaults.buttonColors(containerColor = AppPurple),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Login")
                    }

                    Button(
                        enabled = !loading,
                        onClick = { onSubmit(username, password, true) },
                        colors = ButtonDefaults.buttonColors(containerColor = AppBlue),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Register")
                    }
                }

                if (loading) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}
