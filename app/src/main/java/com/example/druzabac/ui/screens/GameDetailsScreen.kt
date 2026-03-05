package com.example.druzabac.ui.screens

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil.compose.AsyncImage
import com.example.druzabac.db.CityDatabase
import com.example.druzabac.db.EventDatabase
import com.example.druzabac.db.GameDatabase
import com.example.druzabac.model.Game
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameDetailsScreen(
    gameId: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val gameDatabase = remember { GameDatabase() }
    val eventDatabase = remember { EventDatabase() }
    val cityDatabase = remember { CityDatabase() }

    var game by remember { mutableStateOf<Game?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var eventCountsByCity by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var citiesMap by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var enabledCityIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    // Load game data and event counts
    LaunchedEffect(gameId) {
        scope.launch {
            game = gameDatabase.getById(gameId)
            android.util.Log.d("GameDetails", "Game loaded: ${game?.name}")
            eventCountsByCity = eventDatabase.getActiveEventsCountByGameAndCity(gameId)
            android.util.Log.d("GameDetails", "Event counts: $eventCountsByCity")
            val cities = cityDatabase.getAllCities()
            citiesMap = cities.associate { it.id to it.name }
            enabledCityIds = cities.filter { it.enabled }.map { it.id }.toSet()
            android.util.Log.d("GameDetails", "Enabled cities: $enabledCityIds")
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Game Details") },
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
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            game?.let { gameInfo ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Game image
                    gameInfo.imageUrl?.let { imageUrl ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                        ) {
                            AsyncImage(
                                model = imageUrl,
                                contentDescription = gameInfo.name,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }

                    // Game name
                    Text(
                        text = gameInfo.name,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    // BGG Link Button
                    gameInfo.bggUrl?.let { bggUrl ->
                        Button(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, bggUrl.toUri())
                                context.startActivity(intent)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.OpenInBrowser,
                                contentDescription = "View on BGG",
                                modifier = Modifier
                                    .size(20.dp)
                                    .padding(end = 8.dp)
                            )
                            Text("View on BoardGameGeek")
                        }
                    }

                    // Active events by city section
                    if (eventCountsByCity.isNotEmpty()) {
                        Text(
                            text = "Active Events by City",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 8.dp)
                        )

                        eventCountsByCity.entries
                            .filter { (cityId, _) -> enabledCityIds.contains(cityId) }
                            .sortedByDescending { it.value }
                            .forEach { (cityId, count) ->
                                val cityName = citiesMap[cityId] ?: "Unknown City"
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            // TODO: Open list of events in this city with this game
                                        },
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = cityName,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Badge(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        ) {
                                            Text(
                                                text = count.toString(),
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                    } else {
                        Text(
                            text = "No active events with this game",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } ?: run {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Game not found",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

