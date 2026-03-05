package com.example.druzabac.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.druzabac.model.City
import com.example.druzabac.model.Event
import com.example.druzabac.util.geocodeAddress
import com.google.firebase.Timestamp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditEventScreen(
    event: Event,
    selectedCity: City,
    onBack: () -> Unit,
    onSave: (Event) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var location by remember { mutableStateOf(event.location) }
    var district by remember { mutableStateOf(event.district ?: "") }
    var players by remember { mutableStateOf(event.players) }
    var selectedDate by remember { mutableStateOf<Long?>(event.dateTime.toDate().time) }
    var selectedTime by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var minPlayers by remember { mutableStateOf(event.minPlayers.toString()) }
    var maxPlayers by remember { mutableStateOf(event.maxPlayers.toString()) }
    var isSaving by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val cal = Calendar.getInstance()
        cal.time = event.dateTime.toDate()
        selectedTime = Pair(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
    }

    val locationOk = location.isNotBlank()
    val minOk = minPlayers.toIntOrNull() != null && minPlayers.toInt() >= 1
    val maxOk = maxPlayers.toIntOrNull() != null && maxPlayers.toInt() >= 1
    val datesOk = selectedDate != null && selectedTime != null
    val canSave = locationOk && minOk && maxOk && datesOk && minPlayers.toInt() <= maxPlayers.toInt()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit event") },
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

            Text("Location", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = location,
                onValueChange = { location = it },
                label = { Text("Location") },
                modifier = Modifier.fillMaxWidth()
            )

            if (selectedCity.districts.isNotEmpty()) {
                Text("District", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = district,
                    onValueChange = { district = it },
                    label = { Text("District") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Text("Date & Time", style = MaterialTheme.typography.titleMedium)

            OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                Text(if (selectedDate != null) SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(selectedDate!!)) else "Pick date")
            }

            OutlinedButton(onClick = { showTimePicker = true }, modifier = Modifier.fillMaxWidth()) {
                Text(if (selectedTime != null) String.format(Locale.getDefault(), "%02d:%02d", selectedTime!!.first, selectedTime!!.second) else "Pick time")
            }

            if (showDatePicker) {
                val state = rememberDatePickerState(initialSelectedDateMillis = selectedDate ?: System.currentTimeMillis())
                DatePickerDialog(
                    onDismissRequest = { showDatePicker = false },
                    confirmButton = {
                        Button(onClick = { state.selectedDateMillis?.let { selectedDate = it }; showDatePicker = false }) { Text("OK") }
                    },
                    dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
                ) { DatePicker(state) }
            }

            if (showTimePicker && selectedTime != null) {
                val state = rememberTimePickerState(initialHour = selectedTime!!.first, initialMinute = selectedTime!!.second)
                AlertDialog(
                    onDismissRequest = { showTimePicker = false },
                    title = { Text("Pick time") },
                    text = { TimePicker(state) },
                    confirmButton = { Button(onClick = { selectedTime = Pair(state.hour, state.minute); showTimePicker = false }) { Text("OK") } },
                    dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("Cancel") } }
                )
            }

            Text("Player Limits", style = MaterialTheme.typography.titleMedium)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = minPlayers, onValueChange = { minPlayers = it }, label = { Text("Min") }, modifier = Modifier.weight(1f), singleLine = true)
                OutlinedTextField(value = maxPlayers, onValueChange = { maxPlayers = it }, label = { Text("Max") }, modifier = Modifier.weight(1f), singleLine = true)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Button(
                    onClick = {
                        if (isSaving) return@Button
                        scope.launch {
                            try {
                                isSaving = true
                                val fullAddress = listOfNotNull(
                                    location.trim().takeIf { it.isNotBlank() },
                                    district.trim().takeIf { it.isNotBlank() },
                                    selectedCity.name.takeIf { it.isNotBlank() },
                                    selectedCity.country.takeIf { it.isNotBlank() }
                                ).joinToString(", ")

                                val geoPoint = geocodeAddress(context, fullAddress)
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
                                val (hour, minute) = selectedTime!!
                                cal.set(Calendar.HOUR_OF_DAY, hour)
                                cal.set(Calendar.MINUTE, minute)
                                onSave(event.copy(
                                    location = location,
                                    district = district.takeIf { it.isNotBlank() },
                                    latitude = geoPoint.latitude,
                                    longitude = geoPoint.longitude,
                                    players = players,
                                    dateTime = Timestamp(cal.time),
                                    minPlayers = minPlayers.toInt(),
                                    maxPlayers = maxPlayers.toInt()
                                ))
                            } finally {
                                isSaving = false
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = canSave && !isSaving
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("Save")
                    }
                }
            }
        }
    }
}
