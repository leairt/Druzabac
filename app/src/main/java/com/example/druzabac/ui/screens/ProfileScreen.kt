package com.example.druzabac.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.druzabac.auth.UserSession
import com.example.druzabac.db.CityDatabase
import com.example.druzabac.db.GameDatabase
import com.example.druzabac.db.UserDatabase
import com.example.druzabac.model.City
import com.example.druzabac.model.Game
import com.example.druzabac.model.User
import com.example.druzabac.ui.components.ProfileImage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    userId: String,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val userDatabase = remember { UserDatabase() }
    val gameDatabase = remember { GameDatabase() }
    val cityDatabase = remember { CityDatabase() }

    // Current user iz session-a (NE MENJA SE direktno!)
    val currentUser = UserSession.userInfo

    // Profil koji gledamo
    var viewedUser by remember { mutableStateOf<User?>(null) }
    var userCity by remember { mutableStateOf<City?>(null) }

    var ownedGames by remember { mutableStateOf<List<Game>>(emptyList()) }
    var selectedGameId by remember { mutableStateOf<String?>(null) }

    var showGamesScreen by remember { mutableStateOf(false) }
    var showFriendsScreen by remember { mutableStateOf(false) }
    var showEnlargedPhoto by remember { mutableStateOf(false) }

    var isLoading by remember { mutableStateOf(true) }

    // Ucitaj profil + igre + grad
    LaunchedEffect(userId) {
        scope.launch {
            try {
                isLoading = true
                val targetUser = userDatabase.getById(userId)
                viewedUser = targetUser

                // Load city
                if (targetUser?.cityId?.isNotBlank() == true) {
                    val cities = cityDatabase.getAllCities()
                    userCity = cities.find { it.id == targetUser.cityId }
                }

                val games = mutableListOf<Game>()
                targetUser?.ownedGameIds?.forEach { gameId ->
                    val game = gameDatabase.getById(gameId)
                    if (game != null) {
                        games.add(game)
                    } else {
                        games.add(Game(id = gameId, name = gameId, imageUrl = null))
                    }
                }
                ownedGames = games
            } catch (_: Exception) {
                viewedUser = null
                ownedGames = emptyList()
            } finally {
                isLoading = false
            }
        }
    }

    // Details za game
    if (selectedGameId != null) {
        GameDetailsScreen(
            gameId = selectedGameId!!,
            onBack = { selectedGameId = null }
        )
        return
    }

    when {
        showGamesScreen -> {
            viewedUser?.let {
                GamesScreen(
                    user = it,
                    onBack = { showGamesScreen = false }
                )
            } ?: run { showGamesScreen = false }
            return
        }

        showFriendsScreen -> {
            viewedUser?.let {
                FriendsScreen(
                    user = it,
                    onBack = { showFriendsScreen = false }
                )
            } ?: run { showFriendsScreen = false }
            return
        }
    }

    // Main profile screen
    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (viewedUser == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("User not found")
        }
        return
    }

    val u = viewedUser!!

    // Lokalni UI state za friend status (da se menja odmah na klik)
    var areFriends by remember(u.id, currentUser?.id) { mutableStateOf(false) }
    var iSentRequest by remember(u.id, currentUser?.id) { mutableStateOf(false) }
    var iHaveIncoming by remember(u.id, currentUser?.id) { mutableStateOf(false) }

    // Kad se promeni profil koji gledamo ili currentUser, recalculuj status
    LaunchedEffect(u.id, currentUser?.id) {
        if (currentUser != null) {
            areFriends = currentUser.friendsIds.contains(u.id)
            iSentRequest = currentUser.outgoingFriendRequests.contains(u.id)
            iHaveIncoming = currentUser.incomingFriendRequests.contains(u.id)
        } else {
            areFriends = false
            iSentRequest = false
            iHaveIncoming = false
        }
    }

    // Enlarged photo dialog (view only for other users)
    if (showEnlargedPhoto && u.photoUrl.isNotEmpty()) {
        Dialog(
            onDismissRequest = { showEnlargedPhoto = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f))
                    .clickable { showEnlargedPhoto = false },
                contentAlignment = Alignment.Center
            ) {
                ProfileImage(
                    imageUrl = u.photoUrl,
                    contentDescription = "Enlarged profile photo",
                    size = 280.dp
                )
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back"
                )
            }

            Text(
                text = "@${u.username.ifEmpty { "username" }}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.width(40.dp))
        }

        // Photo + stats
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 30.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Profile Picture - clickable to enlarge
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .clickable { showEnlargedPhoto = true }
            ) {
                ProfileImage(
                    imageUrl = u.photoUrl,
                    contentDescription = "Profile Picture",
                    size = 100.dp
                )
            }

            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatBlock(
                    number = "${ownedGames.size}",
                    label = "games",
                    onClick = { showGamesScreen = true }
                )

                StatBlock(
                    number = "${u.friendsIds.size}",
                    label = "friends",
                    onClick = { showFriendsScreen = true }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Full name with friend action button inline
        if (currentUser != null && currentUser.id != u.id) {
            val meId = currentUser.id
            val otherId = u.id

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${u.givenName} ${u.familyName}".trim(),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )

                // Friend action button
                when {
                    areFriends -> {
                        Button(
                            onClick = {
                                // Optimistic UI: odmah prikazi da vise niste friends
                                areFriends = false

                                scope.launch {
                                    userDatabase.removeFriend(meId, otherId)

                                    // refresh session user
                                    userDatabase.getById(meId)?.let { UserSession.setUser(it) }

                                    // refresh viewed user
                                    viewedUser = userDatabase.getById(otherId)
                                }
                            }
                        ) { Text("Unfriend") }
                    }

                    iHaveIncoming -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    // Optimistic UI
                                    iHaveIncoming = false
                                    areFriends = true

                                    scope.launch {
                                        userDatabase.acceptFriendRequest(meId, otherId)

                                        userDatabase.getById(meId)?.let { UserSession.setUser(it) }
                                        viewedUser = userDatabase.getById(otherId)
                                    }
                                }
                            ) { Text("Accept") }

                            OutlinedButton(
                                onClick = {
                                    // Optimistic UI
                                    iHaveIncoming = false

                                    scope.launch {
                                        userDatabase.declineFriendRequest(meId, otherId)

                                        userDatabase.getById(meId)?.let { UserSession.setUser(it) }
                                        viewedUser = userDatabase.getById(otherId)
                                    }
                                }
                            ) { Text("Decline") }
                        }
                    }

                    else -> {
                        // Ovo je TOGGLE dugme:
                        // - ako nije poslato -> šalje i postaje "Request sent"
                        // - ako je poslato -> cancel i vraća na "Add friend"
                        Button(
                            onClick = {
                                val wasSent = iSentRequest

                                // Optimistic UI toggle odmah
                                iSentRequest = !iSentRequest

                                scope.launch {
                                    if (!wasSent) {
                                        userDatabase.sendFriendRequest(meId, otherId)
                                    } else {
                                        userDatabase.cancelFriendRequest(meId, otherId)
                                    }

                                    userDatabase.getById(meId)?.let { UserSession.setUser(it) }
                                    viewedUser = userDatabase.getById(otherId)
                                }
                            }
                        ) {
                            Text(if (iSentRequest) "Request sent" else "Add friend")
                        }
                    }
                }
            }
        } else {
            // If viewing own profile or not logged in, just show the name
            Text(
                text = "${u.givenName} ${u.familyName}".trim(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))


        // About me
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "About me",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                // Age (ako postoji birthday)
                u.birthday?.let { birthday ->
                    val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
                    val birthYear = java.util.Calendar.getInstance().apply {
                        time = birthday.toDate()
                    }.get(java.util.Calendar.YEAR)
                    val age = currentYear - birthYear

                    Text(
                        text = "$age years old",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                u.about.takeIf { it.isNotEmpty() }?.let { about ->
                    Text(
                        text = about,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                // Show actual city
                userCity?.let { city ->
                    Text(
                        text = "${city.name}, ${city.country}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Events Statistics
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Events Statistics",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${u.hostedEventsCount}",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Hosted",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${u.attendedEventsCount}",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Attended",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GamesScreen(
    user: User,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val gameDatabase = remember { GameDatabase() }

    var games by remember { mutableStateOf<List<Game>>(emptyList()) }
    var selectedGameId by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(user) {
        scope.launch {
            val loadedGames = mutableListOf<Game>()
            user.ownedGameIds.forEach { gameId ->
                val game = gameDatabase.getById(gameId)
                if (game != null) {
                    loadedGames.add(game)
                } else {
                    loadedGames.add(Game(id = gameId, name = gameId, imageUrl = null))
                }
            }
            games = loadedGames
            isLoading = false
        }
    }

    if (selectedGameId != null) {
        GameDetailsScreen(
            gameId = selectedGameId!!,
            onBack = { selectedGameId = null }
        )
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Games") },
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
            } else if (games.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No games :(",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item { Spacer(modifier = Modifier.height(8.dp)) }

                    items(games.size) { index ->
                        GameCard(
                            game = games[index],
                            onClick = { selectedGameId = games[index].id }
                        )
                    }

                    item { Spacer(modifier = Modifier.height(8.dp)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(
    user: User,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val userDatabase = remember { UserDatabase() }

    var friends by remember { mutableStateOf<List<User>>(emptyList()) }
    var selectedUserId by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(user) {
        scope.launch {
            val loaded = mutableListOf<User>()
            user.friendsIds.forEach { friendId ->
                val friend = userDatabase.getById(friendId)
                if (friend != null) loaded.add(friend)
            }
            friends = loaded
            isLoading = false
        }
    }

    if (selectedUserId != null) {
        ProfileScreen(
            userId = selectedUserId!!,
            onBack = { selectedUserId = null }
        )
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Friends") },
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
            } else if (friends.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No friends yet :(",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item { Spacer(modifier = Modifier.height(8.dp)) }

                    items(friends.size) { index ->
                        UserCard(
                            user = friends[index],
                            onClick = { selectedUserId = friends[index].id }
                        )
                    }

                    item { Spacer(modifier = Modifier.height(8.dp)) }
                }
            }
        }
    }
}
