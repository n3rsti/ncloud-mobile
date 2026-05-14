package com.example.ncloud.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun AppHeader(
    onMenuClick: () -> Unit,
    onSearchClick: () -> Unit,
    onLogout: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .background(AppTop)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onMenuClick) {
            Icon(
                imageVector = Icons.Filled.Menu,
                contentDescription = "Open menu",
                tint = AppMuted
            )
        }

        Spacer(Modifier.weight(1f))

        IconButton(onClick = onSearchClick) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = "Search",
                tint = AppMuted
            )
        }

        IconButton(onClick = onLogout) {
            Icon(
                imageVector = Icons.Filled.Logout,
                contentDescription = "Logout",
                tint = AppMuted
            )
        }
    }
}

@Composable
fun NcloudDrawer(
    username: String,
    isTrash: Boolean,
    onOpenDrive: () -> Unit,
    onOpenTrash: () -> Unit
) {
    ModalDrawerSheet(
        drawerContainerColor = AppTop,
        drawerContentColor = AppText,
        modifier = Modifier.width(290.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Cloud,
                contentDescription = null,
                tint = AppPurple
            )

            Spacer(Modifier.width(12.dp))

            Column {
                Text(
                    text = "Ncloud",
                    color = AppText,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = username,
                    color = AppMuted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        HorizontalDivider(color = AppPanel)

        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DrawerItem(
                label = "Drive",
                icon = Icons.Filled.Folder,
                selected = !isTrash,
                onClick = onOpenDrive
            )

            DrawerItem(
                label = "Trash",
                icon = Icons.Filled.Delete,
                selected = isTrash,
                onClick = onOpenTrash
            )
        }
    }
}

@Composable
fun DrawerItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppDrawerItemShape)
            .background(if (selected) AppPanel else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) AppText else AppMuted
        )

        Spacer(Modifier.width(14.dp))

        Text(
            text = label,
            color = if (selected) AppText else AppMuted,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}
