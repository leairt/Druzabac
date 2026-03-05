package com.example.druzabac.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.druzabac.auth.UserSession
import com.example.druzabac.db.CityDatabase
import com.example.druzabac.db.UserDatabase
import com.example.druzabac.model.City
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CityGateScreen(
    onEnabledCityComplete: () -> Unit,
    onBackToStart: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val cityDb = remember { CityDatabase() }
    val userDb = remember { UserDatabase() }

    val me = UserSession.userInfo

    var cities by remember { mutableStateOf<List<City>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    var showNotOnListMessage by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        scope.launch {
            isLoading = true
            cities = cityDb.getAllCities()
            isLoading = false
        }
    }

    if (me == null) {
        // Nema session user-a (ne bi trebalo da se desi ako je firebase user tu),
        // ali ako se desi, vrati na start.
        onBackToStart()
        return
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Choose your city") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (isLoading) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Column
            }

            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Prikaz samo enabled gradova
                items(cities.filter { it.enabled }) { city ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            scope.launch {
                                error = null
                                val createdOrLoaded = userDb.createUserIfMissing(
                                    user = me,
                                    cityId = city.id
                                )

                                if (createdOrLoaded != null) {
                                    UserSession.setUser(createdOrLoaded)
                                    onEnabledCityComplete()
                                } else {
                                    error = "Failed to create user. Try again."
                                }
                            }
                        }
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(city.name, style = MaterialTheme.typography.titleMedium)
                            Text(city.country, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(10.dp))
                }

                item {
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { showNotOnListMessage = true }
                    ) {
                        Text("My city is not on the list")
                    }
                }
            }

            if (showNotOnListMessage) {
                AlertDialog(
                    onDismissRequest = { showNotOnListMessage = false },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showNotOnListMessage = false
                                // Po zahtevu: vrati na StartScreen (i sign out)
                                onBackToStart()
                            }
                        ) { Text("OK") }
                    },
                    title = { Text("Coming soon") },
                    text = { Text("Your city will be added as soon as possible.") }
                )
            }
        }
    }
}
