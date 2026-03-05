package com.example.druzabac.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.druzabac.db.CityDatabase
import com.example.druzabac.model.City
import com.example.druzabac.model.User
import com.google.firebase.Timestamp
import kotlinx.coroutines.launch
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    user: User,
    onBack: () -> Unit,
    onSave: (User) -> Unit
) {
    val scope = rememberCoroutineScope()
    val cityDatabase = remember { CityDatabase() }

    var birthday by remember {
        mutableStateOf(
            user.birthday?.toDate()?.let {
                Calendar.getInstance().apply { time = it }.get(Calendar.YEAR).toString()
            } ?: ""
        )
    }
    var about by remember { mutableStateOf(user.about) }
    var selectedCityId by remember { mutableStateOf(user.cityId) }

    var cities by remember { mutableStateOf<List<City>>(emptyList()) }
    var selectedCity by remember { mutableStateOf<City?>(null) }
    var cityDropdownExpanded by remember { mutableStateOf(false) }

    var isSaving by remember { mutableStateOf(false) }

    // Load cities
    LaunchedEffect(Unit) {
        cities = cityDatabase.getAllCities()
        selectedCity = cities.find { it.id == user.cityId }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Profile") },
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
                        onClick = {
                            if (isSaving) return@TextButton
                            isSaving = true

                            scope.launch {
                                val birthdayTimestamp = birthday.toIntOrNull()?.let { year ->
                                    val calendar = Calendar.getInstance()
                                    calendar.set(year, 0, 1, 0, 0, 0)
                                    calendar.set(Calendar.MILLISECOND, 0)
                                    Timestamp(calendar.time)
                                }

                                val updatedUser = user.copy(
                                    // username se NE menja
                                    birthday = birthdayTimestamp,
                                    about = about.trim(),
                                    cityId = selectedCityId
                                )

                                onSave(updatedUser)
                                isSaving = false
                            }
                        },
                        enabled = !isSaving
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        } else {
                            Text("Save")
                        }
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Birthday field
            OutlinedTextField(
                value = birthday,
                onValueChange = {
                    val filtered = it.filter { char -> char.isDigit() }.take(4)
                    birthday = filtered
                },
                label = { Text("Birth Year") },
                placeholder = { Text("e.g. 1990") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                supportingText = {
                    if (birthday.isNotEmpty() && birthday.length == 4) {
                        val year = birthday.toIntOrNull()
                        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
                        if (year != null && year in 1900..currentYear) {
                            Text("Age: ${currentYear - year}")
                        } else {
                            Text("Please enter a valid year between 1900 and $currentYear")
                        }
                    } else {
                        Text("Optional")
                    }
                }
            )

            // About field
            OutlinedTextField(
                value = about,
                onValueChange = { about = it.take(500) },
                label = { Text("About") },
                placeholder = { Text("Tell us about yourself...") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 5,
                supportingText = { Text("${about.length}/500 characters") }
            )

            // City dropdown
            ExposedDropdownMenuBox(
                expanded = cityDropdownExpanded,
                onExpandedChange = { cityDropdownExpanded = !cityDropdownExpanded }
            ) {
                OutlinedTextField(
                    value = selectedCity?.let { "${it.name}, ${it.country}" } ?: "Select city",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("City") },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Dropdown"
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                )

                ExposedDropdownMenu(
                    expanded = cityDropdownExpanded,
                    onDismissRequest = { cityDropdownExpanded = false }
                ) {
                    cities.forEach { city ->
                        DropdownMenuItem(
                            text = { Text("${city.name}, ${city.country}") },
                            onClick = {
                                selectedCity = city
                                selectedCityId = city.id
                                cityDropdownExpanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier {
    return this.clickable(
        indication = null,
        interactionSource = remember { MutableInteractionSource() }
    ) { onClick() }
}
