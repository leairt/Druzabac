package com.example.druzabac.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAddAlt1
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.druzabac.auth.UserSession
import com.example.druzabac.db.CityDatabase
import com.example.druzabac.db.EventDatabase
import com.example.druzabac.db.GameDatabase
import com.example.druzabac.db.UserDatabase
import com.example.druzabac.model.City
import com.example.druzabac.model.Event
import com.example.druzabac.model.EventApplication
import com.example.druzabac.model.Game
import com.example.druzabac.model.User
import com.example.druzabac.ui.components.ProfileImage
import com.example.druzabac.util.openInGoogleMaps
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailsScreen(
    event: Event,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val cityDatabase = remember { CityDatabase() }
    val gameDatabase = remember { GameDatabase() }
    val userDatabase = remember { UserDatabase() }
    val eventDatabase = remember { EventDatabase() }

    val me = UserSession.userInfo
    val myId = me?.id.orEmpty()

    var eventState by remember { mutableStateOf(event) }
    // Derived state - recalculates when myId or eventState changes
    val isHost by remember {
        derivedStateOf { myId.isNotBlank() && myId == eventState.hostId }
    }

    var isLoading by remember { mutableStateOf(true) }
    var errorText by remember { mutableStateOf<String?>(null) }

    var city by remember { mutableStateOf<City?>(null) }
    var games by remember { mutableStateOf<List<Game>>(emptyList()) }
    var host by remember { mutableStateOf<User?>(null) }

    var acceptedCount by remember { mutableStateOf(0) }
    val spotsLeft = (eventState.maxPlayers - acceptedCount).coerceAtLeast(0)

    var pendingApplications by remember { mutableStateOf<List<EventApplication>>(emptyList()) }
    var myActiveApplication by remember { mutableStateOf<EventApplication?>(null) }

    var selectedGameId by remember { mutableStateOf<String?>(null) }
    var showHostProfile by remember { mutableStateOf(false) }
    var showGroupApplyDialog by remember { mutableStateOf(false) }
    var showEditEvent by remember { mutableStateOf(false) }
    var acceptedUsers by remember { mutableStateOf<List<User>>(emptyList()) }
    var selectedUserId by remember { mutableStateOf<String?>(null) }

    suspend fun refreshAll() {
        errorText = null

        // Always refresh the event doc too (acceptedApplicationIds changes on accept/cancel)
        val freshEvent = eventDatabase.getById(eventState.id)
        if (freshEvent != null) eventState = freshEvent

        acceptedCount = eventDatabase.getAcceptedPlayersCount(
            eventId = eventState.id,
            acceptedApplicationIds = eventState.acceptedApplicationIds
        )

        // Load accepted users
        val users = mutableListOf<User>()
        eventState.acceptedApplicationIds.forEach { appId ->
            try {
                val app = eventDatabase.getApplicationById(eventState.id, appId)
                app?.memberUserIds?.forEach { uid ->
                    val user = userDatabase.getById(uid)
                    if (user != null && users.none { it.id == user.id }) {
                        users.add(user)
                    }
                }
            } catch (e: Exception) {
                // Ignore individual app errors
            }
        }
        acceptedUsers = users

        // Check if current user is host (use fresh eventState)
        val amIHost = myId.isNotBlank() && myId == eventState.hostId
        android.util.Log.d("EventDetails", "refreshAll: myId=$myId, hostId=${eventState.hostId}, amIHost=$amIHost")

        if (amIHost) {
            val apps = eventDatabase.getPendingApplications(eventState.id)
            android.util.Log.d("EventDetails", "Loaded ${apps.size} pending applications")
            pendingApplications = apps.sortedBy { it.createdAt.toDate().time }
        } else if (myId.isNotBlank()) {
            myActiveApplication = eventDatabase.getMyActiveApplication(eventState.id, myId)
        } else {
            myActiveApplication = null
        }
    }

    LaunchedEffect(event.id) {
        isLoading = true
        try {
            // Related data
            city = cityDatabase.getAllCities().find { it.id == event.cityId }

            val loadedGames = mutableListOf<Game>()
            event.gameIds.forEach { gameId ->
                val game = gameDatabase.getById(gameId)
                loadedGames.add(game ?: Game(id = gameId, name = gameId, imageUrl = null))
            }
            games = loadedGames

            host = userDatabase.getById(event.hostId)

            // Derived state
            refreshAll()
        } catch (e: Exception) {
            errorText = e.message ?: "Something went wrong"
        } finally {
            isLoading = false
        }
    }

    // Navigation sub-screens (same behavior as your previous file)
    if (selectedGameId != null) {
        GameDetailsScreen(
            gameId = selectedGameId!!,
            onBack = { selectedGameId = null }
        )
        return
    }

    if (showEditEvent && city != null) {
        EditEventScreen(
            event = eventState,
            selectedCity = city!!,
            onBack = { showEditEvent = false },
            onSave = { updatedEvent ->
                scope.launch {
                    try {
                        val success = eventDatabase.updateEvent(updatedEvent)
                        if (success) {
                            eventState = updatedEvent
                            showEditEvent = false
                            refreshAll()
                        } else {
                            errorText = "Failed to save changes"
                        }
                    } catch (e: Exception) {
                        errorText = "Failed: ${e.message}"
                    }
                }
            }
        )
        return
    }

    if (showHostProfile && host != null) {
        ProfileScreen(
            userId = host?.id ?: "",
            onBack = { showHostProfile = false }
        )
        return
    }

    if (selectedUserId != null) {
        ProfileScreen(
            userId = selectedUserId!!,
            onBack = { selectedUserId = null }
        )
        return
    }

    if (showGroupApplyDialog && me != null) {
        GroupApplyDialog(
            myFriendsIds = me.friendsIds,
            hostId = eventState.hostId,
            spotsLeft = spotsLeft,
            userDatabase = userDatabase,
            onDismiss = { showGroupApplyDialog = false },
            onApply = { friendsIds ->
                scope.launch {
                    try {
                        if (myId.isBlank()) {
                            errorText = "You must be logged in."
                            return@launch
                        }
                        val members = (listOf(myId) + friendsIds).distinct()

                        val res = eventDatabase.applyToEvent(
                            eventId = eventState.id,
                            createdByUserId = myId,
                            memberUserIds = members
                        )

                        if (res == null) {
                            errorText = "Failed to apply."
                        } else {
                            showGroupApplyDialog = false
                            refreshAll()
                        }
                    } catch (e: Exception) {
                        errorText = e.message ?: "Failed to apply."
                    }
                }
            }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Event details") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (isHost && eventState.status == "OPEN") {
                        IconButton(onClick = { showEditEvent = true }) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit event",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(
                            onClick = {
                                scope.launch {
                                    try {
                                        errorText = null
                                        eventDatabase.updateStatus(eventState.id, "CANCELLED")
                                        refreshAll()
                                    } catch (e: Exception) {
                                        errorText = e.message ?: "Failed to cancel event."
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cancel event",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        val scroll = rememberScrollState()

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            errorText?.let {
                AssistChip(
                    onClick = { errorText = null },
                    label = { Text(it) }
                )
            }

            // Main info
            Text(
                text = games.joinToString(", ") { it.name }.ifBlank { "Event" },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )

            // Date and Time with icons
            val dateFormat = remember { SimpleDateFormat("EEE, dd MMM", Locale.getDefault()) }
            val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // Calendar + date
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarToday,
                        contentDescription = "Date",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = dateFormat.format(eventState.dateTime.toDate()),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                // Clock + time
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = "Time",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = timeFormat.format(eventState.dateTime.toDate()),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            // Location with icon
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = "Location",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Column {
                    Text(
                        text = eventState.location,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable {
                            val label = buildString {
                                append(eventState.location)
                                eventState.district?.takeIf { it.isNotBlank() }?.let { append(", $it") }
                                city?.name?.takeIf { it.isNotBlank() }?.let { append(", $it") }
                            }
                            openInGoogleMaps(
                                context = context,
                                latitude = eventState.latitude,
                                longitude = eventState.longitude,
                                label = label
                            )
                        }
                    )
                    eventState.district?.takeIf { it.isNotBlank() }?.let { d ->
                        Text(
                            text = d,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    city?.let { c ->
                        Text(
                            text = c.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Host
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = host != null && !isHost) { showHostProfile = true }
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ProfileImage(
                        imageUrl = host?.photoUrl,
                        contentDescription = "Host photo",
                        size = 44.dp
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Hosted by " + (host?.displayName ?: "Host"),
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (isHost) "You are hosting" else host?.username ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Discord thread button (only for host or accepted participants)
            if (eventState.discordThreadId != null && (isHost || myActiveApplication?.status == "ACCEPTED")) {
                Button(
                    onClick = {
                        // Open Discord thread
                        com.example.druzabac.util.DiscordUtils.openDiscordThread(
                            context,
                            com.example.druzabac.discord.DiscordBotApi.GUILD_ID,
                            eventState.discordThreadId!!
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF5865F2) // Discord blue color (fixed hex)
                    )
                ) {
                    Icon(
                        painter = painterResource(id = com.example.druzabac.R.drawable.discord_black_icon),
                        contentDescription = "Discord",
                        modifier = Modifier.size(20.dp),
                        tint = Color.White // White tint for visibility on blue background
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Join Event Thread on Discord")
                }
            }

            // Players - full width with accepted users
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Players", fontWeight = FontWeight.SemiBold)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "$acceptedCount / ${eventState.maxPlayers}",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (spotsLeft > 0) {
                                Text(
                                    text = "($spotsLeft left)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (acceptedUsers.isNotEmpty()) {
                        HorizontalDivider()
                        acceptedUsers.forEach { user ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedUserId = user.id }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                ProfileImage(
                                    imageUrl = user.photoUrl,
                                    contentDescription = user.displayName,
                                    size = 36.dp
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = user.displayName,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = user.username,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                // Host can remove accepted players
                                if (isHost && eventState.status == "OPEN") {
                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                try {
                                                    errorText = null
                                                    // Find the application containing this user and cancel it
                                                    eventState.acceptedApplicationIds.forEach { appId ->
                                                        val app = eventDatabase.getApplicationById(eventState.id, appId)
                                                        if (app != null && app.memberUserIds.contains(user.id)) {
                                                            eventDatabase.cancelApplication(eventState.id, appId)
                                                        }
                                                    }
                                                    refreshAll()
                                                } catch (e: Exception) {
                                                    errorText = e.message ?: "Failed to remove player."
                                                }
                                            }
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Remove player",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Games
            if (games.isNotEmpty()) {
                Text("Games", fontWeight = FontWeight.SemiBold)

                when (games.size) {
                    1 -> {
                        // Single game - full width
                        val g = games.first()
                        ElevatedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedGameId = g.id }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = g.imageUrl,
                                    contentDescription = g.name,
                                    modifier = Modifier
                                        .size(100.dp),
                                    contentScale = ContentScale.Crop,
                                    error = painterResource(id = com.example.druzabac.R.drawable.logo),
                                    placeholder = painterResource(id = com.example.druzabac.R.drawable.logo)
                                )
                                Text(
                                    text = g.name,
                                    modifier = Modifier.padding(16.dp),
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }
                    }
                    2 -> {
                        // Two games - side by side, evenly distributed, equal height
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            games.forEach { g ->
                                ElevatedCard(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clickable { selectedGameId = g.id }
                                ) {
                                    Column(modifier = Modifier.fillMaxHeight()) {
                                        AsyncImage(
                                            model = g.imageUrl,
                                            contentDescription = g.name,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(100.dp),
                                            contentScale = ContentScale.Crop,
                                            error = painterResource(id = com.example.druzabac.R.drawable.logo),
                                            placeholder = painterResource(id = com.example.druzabac.R.drawable.logo)
                                        )
                                        Text(
                                            text = g.name,
                                            modifier = Modifier
                                                .padding(8.dp)
                                                .weight(1f),
                                            fontWeight = FontWeight.SemiBold,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 2
                                        )
                                    }
                                }
                            }
                        }
                    }
                    else -> {
                        // 3+ games - scrollable, smaller cards, equal height
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            games.forEach { g ->
                                ElevatedCard(
                                    modifier = Modifier
                                        .width(140.dp)
                                        .fillMaxHeight()
                                        .clickable { selectedGameId = g.id }
                                ) {
                                    Column(modifier = Modifier.fillMaxHeight()) {
                                        AsyncImage(
                                            model = g.imageUrl,
                                            contentDescription = g.name,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(90.dp),
                                            contentScale = ContentScale.Crop,
                                            error = painterResource(id = com.example.druzabac.R.drawable.logo),
                                            placeholder = painterResource(id = com.example.druzabac.R.drawable.logo)
                                        )
                                        Text(
                                            text = g.name,
                                            modifier = Modifier
                                                .padding(8.dp)
                                                .weight(1f),
                                            fontWeight = FontWeight.SemiBold,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 2
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Finalize Event button (for host, when min players reached)
            if (isHost && eventState.status == "OPEN" && acceptedCount >= eventState.minPlayers) {
                Button(
                    onClick = {
                        scope.launch {
                            try {
                                errorText = null
                                // Update event status to FINALIZED
                                eventDatabase.updateStatus(eventState.id, "FINALIZED")

                                // Increment hostedEventsCount for host
                                userDatabase.incrementHostedEventsCount(eventState.hostId)

                                // Increment attendedEventsCount for all accepted users
                                val acceptedUserIds = acceptedUsers.map { it.id }
                                if (acceptedUserIds.isNotEmpty()) {
                                    userDatabase.incrementAttendedEventsCountForUsers(acceptedUserIds)
                                }

                                refreshAll()
                            } catch (e: Exception) {
                                errorText = e.message ?: "Failed to finalize event."
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Finalize Event")
                }
            }

            // Show status info for finalized/cancelled/full events
            if (eventState.status == "FINALIZED") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Text(
                        text = "This event has been finalized",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (eventState.status == "CANCELLED") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                    )
                ) {
                    Text(
                        text = "This event has been cancelled",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            } else if (spotsLeft == 0 && !isHost) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Text(
                        text = "This event is full",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider()

            // Debug log
            android.util.Log.d("EventDetails", "UI: isHost=$isHost, myId=$myId, hostId=${eventState.hostId}, pendingApps=${pendingApplications.size}")

            // ACTIONS - only show if event is open and not full (for non-hosts)
            if (!isHost && eventState.status == "OPEN") {
                // Participant view - full width
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Your application", fontWeight = FontWeight.SemiBold)

                        if (myId.isBlank()) {
                            Text("You must be logged in to apply.", style = MaterialTheme.typography.bodyMedium)
                        } else {
                            val app = myActiveApplication
                            if (app == null) {
                                Text("No active application.", style = MaterialTheme.typography.bodyMedium)

                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                try {
                                                    errorText = null
                                                    val res = eventDatabase.applyToEvent(
                                                        eventId = eventState.id,
                                                        createdByUserId = myId,
                                                        memberUserIds = listOf(myId)
                                                    )
                                                    if (res == null) errorText = "Failed to apply."
                                                    refreshAll()
                                                } catch (e: Exception) {
                                                    errorText = e.message ?: "Failed to apply."
                                                }
                                            }
                                        },
                                        enabled = eventState.status == "OPEN" && spotsLeft >= 1
                                    ) {
                                        Icon(Icons.Default.PersonAddAlt1, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Apply")
                                    }

                                    OutlinedButton(
                                        onClick = { showGroupApplyDialog = true },
                                        enabled = eventState.status == "OPEN" && spotsLeft >= 2 && me != null && me.friendsIds.isNotEmpty()
                                    ) {
                                        Icon(Icons.Default.Group, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Apply with friends")
                                    }
                                }

                                if (spotsLeft == 0) {
                                    Text("No spots left.", style = MaterialTheme.typography.bodySmall)
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = app.status,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = when(app.status) {
                                                "ACCEPTED" -> MaterialTheme.colorScheme.primary
                                                "PENDING" -> MaterialTheme.colorScheme.tertiary
                                                else -> MaterialTheme.colorScheme.onSurface
                                            }
                                        )
                                        if (app.memberUserIds.size > 1) {
                                            Text(
                                                text = "(${app.memberUserIds.size} people)",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    val canCancel = app.status == "PENDING" || app.status == "ACCEPTED"
                                    OutlinedButton(
                                        onClick = {
                                            scope.launch {
                                                try {
                                                    errorText = null
                                                    eventDatabase.cancelApplication(eventState.id, app.id)
                                                    refreshAll()
                                                } catch (e: Exception) {
                                                    errorText = e.message ?: "Failed to cancel."
                                                }
                                            }
                                        },
                                        enabled = canCancel
                                    ) {
                                        Text("Cancel")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Host view - Pending applications (only when event is open)
            if (isHost && eventState.status == "OPEN") {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Pending applications", fontWeight = FontWeight.SemiBold)

                        if (pendingApplications.isEmpty()) {
                            Text("No pending applications.", style = MaterialTheme.typography.bodyMedium)
                        } else {
                            pendingApplications.forEach { app ->
                                HostApplicationRow(
                                    application = app,
                                    userDatabase = userDatabase,
                                    onUserClick = { userId -> selectedUserId = userId },
                                    onAccept = {
                                        scope.launch {
                                            try {
                                                errorText = null
                                                val ok = eventDatabase.acceptApplication(eventState.id, app.id)
                                                if (!ok) {
                                                    errorText = "Couldn't accept (maybe no capacity)."
                                                } else {
                                                    // Add accepted users to PRIVATE Discord thread
                                                    if (eventState.discordThreadId != null) {
                                                        try {
                                                            android.util.Log.d("EventDetails", "Adding ${app.memberUserIds.size} users to PRIVATE thread ${eventState.discordThreadId}")
                                                            app.memberUserIds.forEach { userId ->
                                                                val added = com.example.druzabac.discord.DiscordBotApi.addUserToThread(
                                                                    eventState.discordThreadId!!,
                                                                    userId
                                                                )
                                                                android.util.Log.d("EventDetails", "User $userId added to PRIVATE thread: $added")
                                                            }

                                                            // Tag users in thread with welcome message
                                                            val userTags = app.memberUserIds.joinToString(" ") { "<@$it>" }
                                                            val welcomeMsg = "✅ Welcome to the event! $userTags"
                                                            com.example.druzabac.discord.DiscordBotApi.sendMessage(
                                                                eventState.discordThreadId!!,
                                                                welcomeMsg
                                                            )
                                                        } catch (e: Exception) {
                                                            android.util.Log.e("EventDetails", "Failed to add users to Discord thread: ${e.message}", e)
                                                        }
                                                    } else {
                                                        android.util.Log.w("EventDetails", "No Discord thread ID for this event")
                                                    }
                                                }
                                                refreshAll()
                                            } catch (e: Exception) {
                                                errorText = e.message ?: "Failed to accept."
                                            }
                                        }
                                    },
                                    onDecline = {
                                        scope.launch {
                                            try {
                                                errorText = null
                                                eventDatabase.declineApplication(eventState.id, app.id)
                                                refreshAll()
                                            } catch (e: Exception) {
                                                errorText = e.message ?: "Failed to decline."
                                            }
                                        }
                                    }
                                )
                                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun HostApplicationRow(
    application: EventApplication,
    userDatabase: UserDatabase,
    onUserClick: (String) -> Unit,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    var members by remember(application.id) { mutableStateOf<List<User>>(emptyList()) }

    LaunchedEffect(application.id) {
        val loaded = mutableListOf<User>()
        application.memberUserIds.forEach { uid ->
            val u = userDatabase.getById(uid)
            if (u != null) loaded.add(u)
        }
        members = loaded
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Members info on left
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (members.isEmpty()) {
                Text("Loading...", style = MaterialTheme.typography.bodySmall)
            } else {
                // Show member avatars
                members.take(3).forEach { user ->
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .clickable { onUserClick(user.id) }
                    ) {
                        ProfileImage(
                            imageUrl = user.photoUrl,
                            contentDescription = user.displayName,
                            size = 32.dp
                        )
                    }
                }
                // Names
                Column {
                    Text(
                        text = members.joinToString(", ") { it.displayName },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                    if (application.type == "GROUP") {
                        Text(
                            text = "${members.size} people",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Buttons on right
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onAccept,
                modifier = Modifier.height(36.dp),
                contentPadding = PaddingValues(horizontal = 12.dp)
            ) { Text("Accept") }
            OutlinedButton(
                onClick = onDecline,
                modifier = Modifier.height(36.dp),
                contentPadding = PaddingValues(horizontal = 12.dp)
            ) { Text("Decline") }
        }
    }
}

@Composable
private fun GroupApplyDialog(
    myFriendsIds: List<String>,
    hostId: String,
    spotsLeft: Int,
    userDatabase: UserDatabase,
    onDismiss: () -> Unit,
    onApply: (List<String>) -> Unit
) {
    var selected by remember { mutableStateOf(setOf<String>()) }
    var friends by remember { mutableStateOf<List<User>>(emptyList()) }

    LaunchedEffect(myFriendsIds, hostId) {
        val loaded = mutableListOf<User>()
        myFriendsIds.forEach { id ->
            // Filter out the host - they cannot be added to a group application
            if (id != hostId) {
                val u = userDatabase.getById(id)
                if (u != null) loaded.add(u)
            }
        }
        friends = loaded
    }

    val maxSelectable = (spotsLeft - 1).coerceAtLeast(0) // minus me
    val canApply = selected.isNotEmpty() && selected.size <= maxSelectable

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Apply with friends") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Select friends to apply together. Spots left: $spotsLeft")
                if (maxSelectable <= 0) {
                    Text("Not enough spots for a group.", style = MaterialTheme.typography.bodySmall)
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    friends.forEach { u ->
                        val isChecked = selected.contains(u.id)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selected = if (isChecked) selected - u.id else selected + u.id
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            ProfileImage(
                                imageUrl = u.photoUrl,
                                contentDescription = u.displayName,
                                size = 36.dp
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(u.displayName, fontWeight = FontWeight.SemiBold)
                                Text("@${u.username}", style = MaterialTheme.typography.bodySmall)
                            }
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = {
                                    selected = if (isChecked) selected - u.id else selected + u.id
                                }
                            )
                        }
                    }
                }

                if (selected.size > maxSelectable) {
                    Text(
                        "Too many selected for remaining spots.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onApply(selected.toList()) },
                enabled = canApply
            ) { Text("Apply") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}


