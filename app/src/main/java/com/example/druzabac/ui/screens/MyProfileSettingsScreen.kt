package com.example.druzabac.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.druzabac.ui.theme.ThemeManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyProfileSettingsScreen(
    onBack: () -> Unit,
    onEditProfile: () -> Unit,
    onSignOut: () -> Unit
) {
    val isDarkTheme by ThemeManager.isDarkTheme

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Edit Profile option
            ListItem(
                headlineContent = {
                    Text(
                        text = "Edit Profile",
                        style = MaterialTheme.typography.bodyLarge
                    )
                },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Profile"
                    )
                },
                modifier = Modifier.clickable { onEditProfile() }
            )

            HorizontalDivider()

            // Theme toggle
            ListItem(
                headlineContent = {
                    Text(
                        text = if (isDarkTheme) "Dark Theme" else "Light Theme",
                        style = MaterialTheme.typography.bodyLarge
                    )
                },
                trailingContent = {
                    Switch(
                        checked = isDarkTheme,
                        onCheckedChange = { ThemeManager.toggleTheme() }
                    )
                }
            )

            HorizontalDivider()

            // Sign Out option
            ListItem(
                headlineContent = {
                    Text(
                        text = "Sign Out",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium
                    )
                },
                leadingContent = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                        contentDescription = "Sign Out",
                        tint = MaterialTheme.colorScheme.error
                    )
                },
                modifier = Modifier.clickable { onSignOut() }
            )
        }
    }
}

