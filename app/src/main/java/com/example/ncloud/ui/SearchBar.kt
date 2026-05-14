package com.example.ncloud.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = {
            Text(text = "Search files and directories", color = AppMuted)
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = AppMuted
            )
        },
        trailingIcon = {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close search",
                    tint = AppMuted
                )
            }
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}
