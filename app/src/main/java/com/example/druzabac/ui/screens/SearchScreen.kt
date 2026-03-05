package com.example.druzabac.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.druzabac.R
import com.example.druzabac.auth.UserSession
import com.example.druzabac.db.EventDatabase
import com.example.druzabac.db.GameDatabase
import com.example.druzabac.db.UserDatabase
import com.example.druzabac.model.Event
import com.example.druzabac.model.Game
import com.example.druzabac.model.User
import com.example.druzabac.ui.components.ProfileImage
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen() {
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    val gameDatabase = remember { GameDatabase() }
    val userDatabase = remember { UserDatabase() }
    val eventDatabase = remember { EventDatabase() }

    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(0) } // 0 = All, 1 = Users, 2 = Events, 3 = Games

    var users by remember { mutableStateOf<List<User>>(emptyList()) }
    var games by remember { mutableStateOf<List<Game>>(emptyList()) }
    var events by remember { mutableStateOf<List<Event>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    var selectedUserId by remember { mutableStateOf<String?>(null) }
    var selectedGameId by remember { mutableStateOf<String?>(null) }
    var selectedEvent by remember { mutableStateOf<Event?>(null) }

    val currentUserId = UserSession.userInfo?.id

    // Search function
    fun performSearch() {
        if (searchQuery.isBlank()) {
            users = emptyList()
            games = emptyList()
            events = emptyList()
            return
        }

        scope.launch {
            isLoading = true

            // Search users
            if (selectedTab == 0 || selectedTab == 1) {
                users = userDatabase.searchUsers(searchQuery)
            } else {
                users = emptyList()
            }

            // Search events
            if (selectedTab == 0 || selectedTab == 2) {
                // First search games by name to find matching game IDs
                val matchingGames = gameDatabase.searchByName(searchQuery)
                val matchingGameIds = matchingGames.map { it.id }.toSet()

                // Fetch upcoming events
                val allUpcoming = eventDatabase.searchUpcomingEvents(100)
                val queryLower = searchQuery.lowercase()

                // Filter events by matching game IDs or location
                events = allUpcoming.filter { event ->
                    event.gameIds.any { it in matchingGameIds } ||
                        event.location.lowercase().contains(queryLower) ||
                        event.district?.lowercase()?.contains(queryLower) == true
                }
            } else {
                events = emptyList()
            }

            // Search games
            if (selectedTab == 0 || selectedTab == 3) {
                games = gameDatabase.searchByName(searchQuery)
            } else {
                games = emptyList()
            }

            isLoading = false
        }
    }

    // Trigger search when query or tab changes
    LaunchedEffect(searchQuery, selectedTab) {
        performSearch()
    }

    // Navigate to user profile
    if (selectedUserId != null) {
        if (selectedUserId == currentUserId) {
            MyProfileScreen(
                onNavigateToMyProfile = { selectedUserId = null },
                onSignOut = {}
            )
        } else {
            ProfileScreen(
                userId = selectedUserId!!,
                onBack = { selectedUserId = null }
            )
        }
        return
    }

    // Navigate to game details
    if (selectedGameId != null) {
        GameDetailsScreen(
            gameId = selectedGameId!!,
            onBack = { selectedGameId = null }
        )
        return
    }

    // Navigate to event details
    if (selectedEvent != null) {
        EventDetailsScreen(
            event = selectedEvent!!,
            onBack = { selectedEvent = null }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Search") }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Search field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search users, events, and games...") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search"
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear"
                            )
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        focusManager.clearFocus()
                        performSearch()
                    }
                ),
                shape = RoundedCornerShape(24.dp)
            )

            // Filter tabs
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                modifier = Modifier.padding(horizontal = 16.dp),
                edgePadding = 0.dp
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("All") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Users") }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Events") }
                )
                Tab(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    text = { Text("Games") }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Results
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (searchQuery.isBlank()) {
                // Empty state - no search yet
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Text(
                            text = "Search for users, events, and games",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (users.isEmpty() && games.isEmpty() && events.isEmpty()) {
                // No results
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SearchOff,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Text(
                            text = "No results found for \"$searchQuery\"",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                // Show results
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Users section
                    if (users.isNotEmpty()) {
                        item {
                            Text(
                                text = "Users",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }

                        items(users) { user ->
                            UserSearchCard(
                                user = user,
                                onClick = { selectedUserId = user.id }
                            )
                        }
                    }

                    // Events section
                    if (events.isNotEmpty()) {
                        item {
                            Text(
                                text = "Events",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(
                                    top = if (users.isNotEmpty()) 16.dp else 0.dp,
                                    bottom = 8.dp
                                )
                            )
                        }

                        items(events) { event ->
                            EventSearchCard(
                                event = event,
                                onClick = { selectedEvent = event }
                            )
                        }
                    }

                    // Games section
                    if (games.isNotEmpty()) {
                        item {
                            Text(
                                text = "Games",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(
                                    top = if (users.isNotEmpty() || events.isNotEmpty()) 16.dp else 0.dp,
                                    bottom = 8.dp
                                )
                            )
                        }

                        items(games) { game ->
                            GameSearchCard(
                                game = game,
                                onClick = { selectedGameId = game.id }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UserSearchCard(
    user: User,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ProfileImage(
                imageUrl = user.photoUrl,
                contentDescription = user.displayName,
                size = 48.dp
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = user.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "@${user.username}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun EventSearchCard(
    event: Event,
    onClick: () -> Unit
) {
    val gameDatabase = remember { GameDatabase() }
    val userDatabase = remember { UserDatabase() }
    val eventDatabase = remember { EventDatabase() }

    var gameNames by remember { mutableStateOf<List<String>>(emptyList()) }
    var hostUser by remember { mutableStateOf<User?>(null) }
    var acceptedCount by remember { mutableStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }

    val spotsLeft = (event.maxPlayers - acceptedCount).coerceAtLeast(0)

    LaunchedEffect(event.id) {
        // Load game names
        val names = mutableListOf<String>()
        event.gameIds.forEach { gameId ->
            val game = gameDatabase.getById(gameId)
            names.add(game?.name ?: gameId)
        }
        gameNames = names

        // Load host
        hostUser = userDatabase.getById(event.hostId)

        // Load accepted count
        acceptedCount = eventDatabase.getAcceptedPlayersCount(event.id, event.acceptedApplicationIds)

        isLoading = false
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Game names
            Text(
                text = if (isLoading) "Loading..." else gameNames.joinToString(", "),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2
            )

            // Location + Date
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(event.location, style = MaterialTheme.typography.bodyMedium)
                    event.district?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = formatSearchEventDate(event.dateTime.toDate()),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = formatSearchEventTime(event.dateTime.toDate()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Host + spots
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ProfileImage(
                        imageUrl = hostUser?.photoUrl,
                        contentDescription = "Host",
                        size = 24.dp
                    )
                    Text(
                        text = hostUser?.username ?: "...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Players",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "$spotsLeft spots left",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun GameSearchCard(
    game: Game,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AsyncImage(
                model = game.imageUrl,
                contentDescription = game.name,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop,
                error = painterResource(id = R.drawable.logo),
                placeholder = painterResource(id = R.drawable.logo)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = game.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Icon(
                imageVector = HexagonIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

private fun formatSearchEventDate(date: Date): String {
    val calendar = Calendar.getInstance()
    val today = Calendar.getInstance()
    val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }

    calendar.time = date
    return when {
        isSameSearchDay(calendar, today) -> "Today"
        isSameSearchDay(calendar, tomorrow) -> "Tomorrow"
        else -> SimpleDateFormat("EEE, dd MMM", Locale.ENGLISH).format(date)
    }
}

private fun formatSearchEventTime(date: Date): String {
    return SimpleDateFormat("HH:mm", Locale.ENGLISH).format(date)
}

private fun isSameSearchDay(cal1: Calendar, cal2: Calendar): Boolean {
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
            cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}
