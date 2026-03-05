package com.example.druzabac.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.druzabac.auth.UserSession
import com.example.druzabac.db.GameDatabase
import com.example.druzabac.model.City
import com.example.druzabac.model.Event
import com.example.druzabac.model.Game
import com.example.druzabac.util.GeocodeSuggestion
import com.example.druzabac.util.geocodeAddress
import com.example.druzabac.util.geocodeAddressWithFallback
import com.example.druzabac.util.searchGeocodeSuggestionsMulti
import com.google.firebase.Timestamp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreateEventScreen(
    selectedCity: City,
    onBack: () -> Unit,
    onSave: (Event) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val gameDatabase = remember { GameDatabase() }

    val maxGames = 5

    var currentGameInput by remember { mutableStateOf("") }
    var selectedGames by remember { mutableStateOf<Map<String, String>>(emptyMap()) } // id -> name
    var gameSuggestions by remember { mutableStateOf<List<Game>>(emptyList()) }
    var showSuggestions by remember { mutableStateOf(false) }

    var location by remember { mutableStateOf("") }
    var selectedLocationSuggestion by remember { mutableStateOf<GeocodeSuggestion?>(null) }
    var locationSuggestions by remember { mutableStateOf<List<GeocodeSuggestion>>(emptyList()) }
    var showLocationSuggestions by remember { mutableStateOf(false) }
    var isLoadingLocationSuggestions by remember { mutableStateOf(false) }
    var district by remember { mutableStateOf("") }
    var players by remember { mutableStateOf("") }

    var selectedDate by remember { mutableStateOf<Long?>(null) }
    var selectedTime by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var isGeocoding by remember { mutableStateOf(false) }

    // --- Validations ---
    val needsDistrict = selectedCity.districts.isNotEmpty()

    fun isPlayersValid(value: String): Boolean {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return false

        val numberRegex = Regex("^\\d+$")
        val rangeRegex = Regex("^\\d+\\s*-\\s*\\d+$")

        return when {
            numberRegex.matches(trimmed) -> trimmed.toIntOrNull()?.let { it >= 1 } ?: false
            rangeRegex.matches(trimmed) -> {
                val parts = trimmed.split("-").map { it.trim() }
                val a = parts.getOrNull(0)?.toIntOrNull()
                val b = parts.getOrNull(1)?.toIntOrNull()
                a != null && b != null && a >= 1 && b >= 1 && a <= b
            }
            else -> false
        }
    }

    fun parseMinMaxPlayers(value: String): Pair<Int, Int> {
        val trimmed = value.trim()
        val numberRegex = Regex("^\\d+$")
        val rangeRegex = Regex("^\\d+\\s*-\\s*\\d+$")

        return when {
            numberRegex.matches(trimmed) -> {
                val n = trimmed.toInt()
                n to n
            }
            rangeRegex.matches(trimmed) -> {
                val parts = trimmed.split("-").map { it.trim() }
                val a = parts[0].toInt()
                val b = parts[1].toInt()
                a to b
            }
            else -> 1 to 1
        }
    }

    fun isDateTimeValid(): Boolean {
        if (selectedDate == null || selectedTime == null) return false
        val cal = Calendar.getInstance()
        cal.timeInMillis = selectedDate!!
        cal.set(Calendar.HOUR_OF_DAY, selectedTime!!.first)
        cal.set(Calendar.MINUTE, selectedTime!!.second)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)

        val chosen = cal.timeInMillis
        val nowPlus30 = System.currentTimeMillis() + 30 * 60 * 1000
        return chosen >= nowPlus30
    }

    val gamesOk = selectedGames.isNotEmpty() && selectedGames.size <= maxGames
    val locationOk = location.trim().length >= 3
    val districtOk = if (needsDistrict) district.trim().isNotBlank() else true
    val playersOk = isPlayersValid(players)
    val dateTimeOk = isDateTimeValid()

    val canSave = gamesOk && locationOk && districtOk && playersOk && dateTimeOk

    // Search games
    LaunchedEffect(currentGameInput) {
        if (currentGameInput.isNotBlank() && currentGameInput.length >= 2 && selectedGames.size < maxGames) {
            scope.launch {
                gameSuggestions = gameDatabase.searchByName(currentGameInput)
                showSuggestions = true
            }
        } else {
            gameSuggestions = emptyList()
            showSuggestions = false
        }
    }

    LaunchedEffect(location, district, selectedCity.id) {
        val rawQuery = location.trim()
        if (rawQuery.length < 3) {
            locationSuggestions = emptyList()
            showLocationSuggestions = false
            isLoadingLocationSuggestions = false
            return@LaunchedEffect
        }
        if (needsDistrict && district.isBlank()) {
            locationSuggestions = emptyList()
            showLocationSuggestions = false
            isLoadingLocationSuggestions = false
            return@LaunchedEffect
        }
        if (selectedLocationSuggestion?.title == rawQuery) {
            locationSuggestions = emptyList()
            showLocationSuggestions = false
            isLoadingLocationSuggestions = false
            return@LaunchedEffect
        } else {
            selectedLocationSuggestion = null
        }
        delay(300)
        try {
            isLoadingLocationSuggestions = true
            val queries = buildList {
                add(rawQuery)
                add("$rawQuery, ${selectedCity.name}")
                add("$rawQuery, ${selectedCity.country}")
                add("$rawQuery, ${selectedCity.name}, ${selectedCity.country}")
                if (district.isNotBlank()) {
                    add("$rawQuery, ${district.trim()}")
                    add("$rawQuery, ${district.trim()}, ${selectedCity.name}")
                    add("$rawQuery, ${district.trim()}, ${selectedCity.name}, ${selectedCity.country}")
                }
            }
            locationSuggestions = searchGeocodeSuggestionsMulti(context, queries, maxResults = 8)
                .filter { suggestion: GeocodeSuggestion ->
                    val haystack = normalizeForMatch(
                        listOfNotNull(
                            suggestion.title,
                            suggestion.subtitle,
                            suggestion.fullAddress
                        ).joinToString(" ")
                    )
                    val cityMatches = cityMatchVariants(selectedCity.name).any { variant ->
                        variant.split(" ")
                            .filter { it.isNotBlank() }
                            .all { token -> haystack.contains(token) }
                    }
                    val districtMatches = if (district.isBlank()) {
                        true
                    } else {
                        cityMatchVariants(district).any { variant ->
                            variant.split(" ")
                                .filter { it.isNotBlank() }
                                .all { token -> haystack.contains(token) }
                        }
                    }
                    cityMatches && districtMatches
                }
            showLocationSuggestions = true
        } finally {
            isLoadingLocationSuggestions = false
        }
    }

    // DatePicker
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedDate = datePickerState.selectedDateMillis
                        showDatePicker = false
                    }
                ) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // TimePicker
    if (showTimePicker) {
        val timePickerState = rememberTimePickerState()
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedTime = Pair(timePickerState.hour, timePickerState.minute)
                        showTimePicker = false
                    }
                ) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("Cancel") } },
            text = { TimePicker(state = timePickerState) }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create event") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            AssistChip(onClick = { }, label = { Text("${selectedCity.name}, ${selectedCity.country}") })

            Text("Games (1–$maxGames)", style = MaterialTheme.typography.titleMedium)

            if (selectedGames.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    selectedGames.forEach { (gameId, gameName) ->
                        AssistChip(
                            onClick = { },
                            label = { Text(gameName) },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Remove",
                                    modifier = Modifier.clickable { selectedGames = selectedGames - gameId }
                                )
                            }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = currentGameInput,
                onValueChange = { currentGameInput = it },
                label = { Text("Add game") },
                placeholder = { Text("Start typing…") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = selectedGames.size < maxGames,
                supportingText = { if (selectedGames.size >= maxGames) Text("Maximum $maxGames games reached") }
            )

            if (showSuggestions && selectedGames.size < maxGames) {
                val suggestions = gameSuggestions.take(6)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column {
                        if (suggestions.isNotEmpty()) {
                            suggestions.forEachIndexed { index, game ->
                                Text(
                                    text = game.name,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (selectedGames.size < maxGames) {
                                                selectedGames = selectedGames + (game.id to game.name)
                                                currentGameInput = ""
                                                showSuggestions = false
                                            }
                                        }
                                        .padding(14.dp)
                                )
                                if (index != suggestions.lastIndex) HorizontalDivider()
                            }
                        } else if (currentGameInput.isNotBlank() && currentGameInput.length >= 2) {
                            Text(
                                text = "Add \"$currentGameInput\" as custom game",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (selectedGames.size < maxGames) {
                                            val custom = currentGameInput.trim()
                                            selectedGames = selectedGames + (custom to custom)
                                            currentGameInput = ""
                                            showSuggestions = false
                                        }
                                    }
                                    .padding(14.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            if (needsDistrict) {
                var showDistrictSuggestions by remember { mutableStateOf(false) }

                OutlinedTextField(
                    value = district,
                    onValueChange = {
                        district = it
                        showDistrictSuggestions = it.isNotBlank()
                    },
                    label = { Text("District") },
                    placeholder = { Text("Choose district…") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = !districtOk,
                    supportingText = { if (!districtOk) Text("District is required for this city") }
                )

                if (showDistrictSuggestions) {
                    val filtered = selectedCity.districts
                        .filter { it.contains(district, ignoreCase = true) }
                        .take(6)

                    if (filtered.isNotEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                        ) {
                            Column {
                                filtered.forEachIndexed { index, name ->
                                    Text(
                                        text = name,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                district = name
                                                showDistrictSuggestions = false
                                            }
                                            .padding(14.dp)
                                    )
                                    if (index != filtered.lastIndex) HorizontalDivider()
                                }
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = location,
                onValueChange = {
                    location = it
                    showLocationSuggestions = true
                },
                label = { Text("Location") },
                placeholder = { Text("e.g. Starbucks, Kafeterija, Knez Mihailova 12…") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = !locationOk && location.isNotBlank(),
                supportingText = {
                    when {
                        needsDistrict && district.isBlank() -> Text("Choose district first to get location suggestions")
                        location.isBlank() -> Text("Type a place name, café, or address")
                        location.trim().length < 3 -> Text("Type at least 3 characters")
                        selectedLocationSuggestion != null -> Text("✓ Location verified")
                        else -> Text("Select a suggestion or continue typing")
                    }
                }
            )
            if (isLoadingLocationSuggestions) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                )
            }
            if (showLocationSuggestions && locationSuggestions.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column {
                        locationSuggestions.forEachIndexed { index, suggestion ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        location = suggestion.title
                                        selectedLocationSuggestion = suggestion
                                        showLocationSuggestions = false
                                    }
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = suggestion.title,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                suggestion.subtitle?.takeIf { it.isNotBlank() }?.let { subtitle ->
                                    Text(
                                        text = subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (index != locationSuggestions.lastIndex) HorizontalDivider()
                        }
                    }
                }
            }
            if (showLocationSuggestions && !isLoadingLocationSuggestions && location.trim().length >= 2 && locationSuggestions.isEmpty()) {
                Text(
                    text = "No places found. Try a different name or a more specific address.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text("Date & time", style = MaterialTheme.typography.titleMedium)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = if (selectedDate != null) {
                            SimpleDateFormat("dd/MM/yyyy", Locale.US).format(Date(selectedDate!!))
                        } else "Select date"
                    )
                }

                OutlinedButton(
                    onClick = { showTimePicker = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = if (selectedTime != null) {
                            String.format(Locale.US, "%02d:%02d", selectedTime!!.first, selectedTime!!.second)
                        } else "Select time"
                    )
                }
            }

            if (!dateTimeOk && (selectedDate != null || selectedTime != null)) {
                Text(
                    text = "Choose a time at least 30 minutes from now",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            OutlinedTextField(
                value = players,
                onValueChange = { players = it },
                label = { Text("Players") },
                placeholder = { Text("e.g. 3 or 2-4") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = !playersOk && players.isNotBlank(),
                supportingText = {
                    if (!playersOk && players.isNotBlank()) Text("Use a number (>=1) or a range like 2-4")
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.weight(1f)
                ) { Text("Cancel") }

                Button(
                    onClick = {
                        if (!canSave || isGeocoding) return@Button
                        scope.launch {
                            // ✅ use UserSession UID as hostId
                            val uid = UserSession.userInfo?.id.orEmpty()

                            if (uid.isBlank()) {
                                Toast.makeText(context, "You are not signed in.", Toast.LENGTH_SHORT).show()
                                return@launch
                            }

                            try {
                                isGeocoding = true
                                val pickedSuggestion = selectedLocationSuggestion
                                val geoPoint = if (pickedSuggestion != null) {
                                    com.example.druzabac.util.GeoPoint(
                                        pickedSuggestion.latitude,
                                        pickedSuggestion.longitude
                                    )
                                } else {
                                    val fullAddress = listOfNotNull(
                                        location.trim().takeIf { it.isNotBlank() },
                                        district.trim().takeIf { it.isNotBlank() },
                                        selectedCity.name.takeIf { it.isNotBlank() },
                                        selectedCity.country.takeIf { it.isNotBlank() }
                                    ).joinToString(", ")
                                    geocodeAddressWithFallback(
                                        context = context,
                                        queries = listOf(
                                            fullAddress,
                                            location.trim(),
                                            "${location.trim()}, ${selectedCity.name}",
                                            "${location.trim()}, ${selectedCity.country}"
                                        )
                                    ) ?: geocodeAddress(context, fullAddress)
                                }
                                if (geoPoint == null) {
                                    Toast.makeText(
                                        context,
                                        "Address not found on map. Please enter a more precise location.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    return@launch
                                }

                                val cal = Calendar.getInstance()
                                cal.timeInMillis = selectedDate!!
                                cal.set(Calendar.HOUR_OF_DAY, selectedTime!!.first)
                                cal.set(Calendar.MINUTE, selectedTime!!.second)
                                cal.set(Calendar.SECOND, 0)
                                cal.set(Calendar.MILLISECOND, 0)

                                val eventTime = Timestamp(cal.time)
                                val (minP, maxP) = parseMinMaxPlayers(players)

                                val newEvent = Event(
                                    id = "",
                                    gameIds = selectedGames.keys.toList(),
                                    cityId = selectedCity.id,
                                    district = if (needsDistrict) district.trim() else district.trim().ifBlank { null },
                                    location = selectedLocationSuggestion?.title ?: location.trim(),
                                    latitude = geoPoint.latitude,
                                    longitude = geoPoint.longitude,
                                    dateTime = eventTime,
                                    players = players.trim(),
                                    hostId = uid,
                                    createdAt = Timestamp.now(),
                                    status = "OPEN",
                                    minPlayers = minP,
                                    maxPlayers = maxP,
                                    acceptedApplicationIds = emptyList()
                                )

                                onSave(newEvent)
                            } catch (_: Exception) {
                                Toast.makeText(context, "Error creating event", Toast.LENGTH_SHORT).show()
                            } finally {
                                isGeocoding = false
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = canSave && !isGeocoding
                ) {
                    if (isGeocoding) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("Create")
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

private fun normalizeForMatch(value: String): String {
    return Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace("\\p{M}".toRegex(), "")
        .replace("[^\\p{Alnum}\\s]".toRegex(), " ")
        .lowercase(Locale.ROOT)
        .replace("\\s+".toRegex(), " ")
        .trim()
}

private fun cityMatchVariants(value: String): List<String> {
    val normalized = normalizeForMatch(value)
    val variants = linkedSetOf(normalized)
    if (normalized.contains("belgrade")) variants += normalized.replace("belgrade", "beograd")
    if (normalized.contains("beograd")) variants += normalized.replace("beograd", "belgrade")
    if (normalized.contains("serbia")) variants += normalized.replace("serbia", "srbija")
    if (normalized.contains("srbija")) variants += normalized.replace("srbija", "serbia")
    return variants.filter { it.isNotBlank() }
}
