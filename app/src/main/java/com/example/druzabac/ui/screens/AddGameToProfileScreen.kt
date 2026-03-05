package com.example.druzabac.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*

import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.druzabac.db.GameDatabase
import com.example.druzabac.model.Game
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddGameToProfileScreen(
    currentGames: List<String>,
    onBack: () -> Unit,
    onSave: (List<String>) -> Unit
) {
    val scope = rememberCoroutineScope()
    val gameDatabase = remember { GameDatabase() }

    var selectedGames by remember { mutableStateOf(currentGames) }
    var currentGameInput by remember { mutableStateOf("") }
    var gameSuggestions by remember { mutableStateOf<List<Game>>(emptyList()) }
    var showSuggestions by remember { mutableStateOf(false) }

    // Search for game suggestions
    LaunchedEffect(currentGameInput) {
        if (currentGameInput.isNotBlank() && currentGameInput.length >= 2) {
            scope.launch {
                gameSuggestions = gameDatabase.searchByName(currentGameInput)
                    .filter { !selectedGames.contains(it.id) }
                showSuggestions = true
            }
        } else {
            showSuggestions = false
            gameSuggestions = emptyList()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manage Games") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = { onSave(selectedGames) }
                    ) {
                        Text("Save")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Current games count
            Text(
                text = "Selected Games: ${selectedGames.size}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            // Selected games display
            if (selectedGames.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Your Games:",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            selectedGames.forEach { gameId ->
                                FilterChip(
                                    onClick = {
                                        selectedGames = selectedGames - gameId
                                    },
                                    label = { Text(gameId) },
                                    selected = true,
                                    trailingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Remove",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Add new game section
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Add New Game:",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Input field for adding games
                OutlinedTextField(
                    value = currentGameInput,
                    onValueChange = { currentGameInput = it },
                    label = { Text("Search for a game") },
                    placeholder = { Text("Start typing game name...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Game suggestions dropdown
                if (showSuggestions && gameSuggestions.isNotEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp)
                        ) {
                            items(gameSuggestions.size) { index ->
                                val game = gameSuggestions[index]
                                Text(
                                    text = game.name,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (!selectedGames.contains(game.id)) {
                                                selectedGames = selectedGames + game.id
                                                currentGameInput = ""
                                                showSuggestions = false
                                            }
                                        }
                                        .padding(16.dp),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                if (index < gameSuggestions.size - 1) {
                                    HorizontalDivider()
                                }
                            }
                        }
                    }
                } else if (showSuggestions && currentGameInput.isNotBlank() && currentGameInput.length >= 2) {
                    // Option to add custom game name if no suggestions
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Text(
                            text = "Add \"$currentGameInput\" as custom game",
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (!selectedGames.contains(currentGameInput)) {
                                        selectedGames = selectedGames + currentGameInput
                                        currentGameInput = ""
                                        showSuggestions = false
                                    }
                                }
                                .padding(16.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Help text
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Tips:",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "• Start typing to search for existing games in our database",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "• If a game isn't found, you can add it as a custom game",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "• Tap on selected games to remove them",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

