package com.example.druzabac.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.druzabac.auth.DiscordApi
import com.example.druzabac.auth.DiscordAuthManager
import com.example.druzabac.auth.UserSession
import com.example.druzabac.db.CityDatabase
import com.example.druzabac.db.GameDatabase
import com.example.druzabac.db.UserDatabase
import com.example.druzabac.model.City
import com.example.druzabac.model.Game
import com.example.druzabac.ui.components.ProfileImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyProfileScreen(onNavigateToMyProfile: () -> Unit, onSignOut: () -> Unit) {
    val scope = rememberCoroutineScope()
    val user = UserSession.userInfo

    val gameDatabase = remember { GameDatabase() }
    val userDatabase = remember { UserDatabase() }
    val cityDatabase = remember { CityDatabase() }

    var selectedGameId by remember { mutableStateOf<String?>(null) }
    var showEditProfile by remember { mutableStateOf(false) }
    var showGamesScreen by remember { mutableStateOf(false) }
    var showFriendsScreen by remember { mutableStateOf(false) }
    var showSettingsScreen by remember { mutableStateOf(false) }
    var showEnlargedPhoto by remember { mutableStateOf(false) }

    var ownedGames by remember { mutableStateOf<List<Game>>(emptyList()) }
    var userCity by remember { mutableStateOf<City?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    // Load user's city and owned games
    LaunchedEffect(user) {
        scope.launch {
            user?.let { currentUser ->
                // Load city if user has cityId
                if (currentUser.cityId.isNotEmpty()) {
                    val cities = cityDatabase.getAllCities()
                    userCity = cities.find { it.id == currentUser.cityId }
                }

                // Load owned games
                val games = mutableListOf<Game>()
                currentUser.ownedGameIds.forEach { gameId ->
                    val game = gameDatabase.getById(gameId)
                    if (game != null) {
                        games.add(game)
                    } else {
                        // Custom game
                        games.add(Game(id = gameId, name = gameId, imageUrl = null))
                    }
                }
                ownedGames = games
                isLoading = false
            }
        }
    }

    // Show game details screen
    if (selectedGameId != null) {
        GameDetailsScreen(
            gameId = selectedGameId!!,
            onBack = { selectedGameId = null }
        )
    } else if (showSettingsScreen) {
        MyProfileSettingsScreen(
            onBack = { showSettingsScreen = false },
            onEditProfile = {
                showSettingsScreen = false
                showEditProfile = true
            },
            onSignOut = {
                UserSession.clear()
                onSignOut()
            }
        )
    } else if (showEditProfile && user != null) {
        EditProfileScreen(
            user = user,
            onBack = { showEditProfile = false },
            onSave = { updatedUser ->
                scope.launch {
                    val success = userDatabase.updateUser(updatedUser)
                    if (success) {
                        UserSession.setUser(updatedUser)
                        showEditProfile = false
                    }
                }
            }
        )
    } else if (showGamesScreen) {
        MyGamesScreen(
            onBack = { showGamesScreen = false }
        )
    } else if (showFriendsScreen) {
        MyFriendsScreen(
            onBack = { showFriendsScreen = false },
            onNavigateToMyProfile = onNavigateToMyProfile
        )
    } else {
        // Enlarged photo dialog with edit options for own profile
        var showPhotoOptions by remember { mutableStateOf(false) }
        var isUploadingPhoto by remember { mutableStateOf(false) }
        val context = LocalContext.current

        suspend fun bitmapToBase64DataUrl(originalBitmap: Bitmap): String? = withContext(Dispatchers.IO) {
            try {
                val size = minOf(originalBitmap.width, originalBitmap.height)
                val xOffset = (originalBitmap.width - size) / 2
                val yOffset = (originalBitmap.height - size) / 2
                val croppedBitmap = Bitmap.createBitmap(originalBitmap, xOffset, yOffset, size, size)
                val targetSize = 200
                val scaledBitmap = Bitmap.createScaledBitmap(croppedBitmap, targetSize, targetSize, true)
                val outputStream = ByteArrayOutputStream()
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
                val compressedBytes = outputStream.toByteArray()
                if (croppedBitmap != originalBitmap) croppedBitmap.recycle()
                if (scaledBitmap != croppedBitmap) scaledBitmap.recycle()
                val base64 = Base64.encodeToString(compressedBytes, Base64.NO_WRAP)
                "data:image/jpeg;base64,$base64"
            } catch (e: Exception) {
                android.util.Log.e("MyProfileScreen", "Image processing error: ${e.message}", e)
                null
            }
        }

        suspend fun uploadBase64Photo(base64Url: String?) {
            if (base64Url != null && user != null) {
                val updated = user.copy(photoUrl = base64Url)
                val success = userDatabase.updateUser(updated)
                if (success) {
                    UserSession.setUser(updated)
                    android.widget.Toast.makeText(context, "Photo updated!", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    android.widget.Toast.makeText(context, "Failed to save photo", android.widget.Toast.LENGTH_SHORT).show()
                }
            } else {
                android.widget.Toast.makeText(context, "Failed to read image", android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        // Image picker launcher with Base64 encoding (no Firebase Storage needed)
        val imagePickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            if (uri != null && user != null) {
                isUploadingPhoto = true
                showPhotoOptions = false

                scope.launch {
                    try {
                        val base64Url = withContext(Dispatchers.IO) {
                            val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext null
                            val bytes = inputStream.readBytes()
                            inputStream.close()
                            val originalBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@withContext null
                            val encoded = bitmapToBase64DataUrl(originalBitmap)
                            originalBitmap.recycle()
                            encoded
                        }
                        uploadBase64Photo(base64Url)
                    } catch (e: Exception) {
                        android.util.Log.e("MyProfileScreen", "Upload failed: ${e.message}", e)
                        android.widget.Toast.makeText(context, "Failed: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                    } finally {
                        isUploadingPhoto = false
                        showEnlargedPhoto = false
                    }
                }
            }
        }

        val cameraLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.TakePicturePreview()
        ) { bitmap: Bitmap? ->
            if (bitmap != null && user != null) {
                isUploadingPhoto = true
                showPhotoOptions = false

                scope.launch {
                    try {
                        val base64Url = bitmapToBase64DataUrl(bitmap)
                        uploadBase64Photo(base64Url)
                    } catch (e: Exception) {
                        android.util.Log.e("MyProfileScreen", "Camera upload failed: ${e.message}", e)
                        android.widget.Toast.makeText(context, "Failed: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                    } finally {
                        isUploadingPhoto = false
                        showEnlargedPhoto = false
                    }
                }
            }
        }

        val cameraPermissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                try {
                    cameraLauncher.launch(null)
                } catch (e: Exception) {
                    android.util.Log.e("MyProfileScreen", "Failed to launch camera: ${e.message}", e)
                    android.widget.Toast.makeText(context, "Camera is unavailable on this device", android.widget.Toast.LENGTH_SHORT).show()
                }
            } else {
                android.widget.Toast.makeText(context, "Camera permission is required", android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        fun openCamera() {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                try {
                    cameraLauncher.launch(null)
                } catch (e: Exception) {
                    android.util.Log.e("MyProfileScreen", "Failed to launch camera: ${e.message}", e)
                    android.widget.Toast.makeText(context, "Camera is unavailable on this device", android.widget.Toast.LENGTH_SHORT).show()
                }
            } else {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }

        if (showEnlargedPhoto && user != null) {
            Dialog(
                onDismissRequest = {
                    showEnlargedPhoto = false
                    showPhotoOptions = false
                },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.85f))
                        .clickable {
                            if (!showPhotoOptions) {
                                showEnlargedPhoto = false
                            }
                            showPhotoOptions = false
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // Photo container with edit button
                    Box(contentAlignment = Alignment.BottomEnd) {
                        // Enlarged photo or loading
                        if (isUploadingPhoto) {
                            Box(
                                modifier = Modifier
                                    .size(280.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        } else {
                            ProfileImage(
                                imageUrl = user.photoUrl,
                                contentDescription = "Enlarged profile photo",
                                size = 280.dp
                            )
                        }

                        // Edit button (pencil icon) with dropdown anchored to it
                        Box(
                            modifier = Modifier
                                .offset(x = (-20).dp, y = (-20).dp)
                        ) {
                            // Pencil button
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                                    .clickable { showPhotoOptions = !showPhotoOptions },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit photo",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            // Dropdown menu anchored to the pencil button
                            DropdownMenu(
                                expanded = showPhotoOptions,
                                onDismissRequest = { showPhotoOptions = false },
                                offset = DpOffset(x = 0.dp, y = 4.dp)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Choose from gallery") },
                                    onClick = {
                                        showPhotoOptions = false
                                        imagePickerLauncher.launch("image/*")
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Take a picture") },
                                    onClick = {
                                        showPhotoOptions = false
                                        openCamera()
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.PhotoCamera, contentDescription = null)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Import from Discord") },
                                    onClick = {
                                        showPhotoOptions = false
                                        isUploadingPhoto = true
                                        scope.launch {
                                            try {
                                                // Get Discord avatar URL using Discord user ID
                                                // The user.id is the Discord user ID
                                                val discordAvatarUrl = "https://cdn.discordapp.com/avatars/${user.id}/${user.id}.png"

                                                // Actually, we need to fetch fresh data from Discord API
                                                // For now, use the DiscordAuthManager to get the access token and fetch user info
                                                val discordAuthManager = DiscordAuthManager(context)
                                                val accessToken = discordAuthManager.getAccessToken()

                                                if (accessToken != null) {
                                                    val discordUser = DiscordApi.getUserInfo(accessToken)
                                                    if (discordUser != null) {
                                                        val avatarUrl = discordUser.getAvatarUrl()
                                                        val updated = user.copy(photoUrl = avatarUrl)
                                                        val success = userDatabase.updateUser(updated)
                                                        if (success) {
                                                            UserSession.setUser(updated)
                                                            android.widget.Toast.makeText(context, "Discord photo imported!", android.widget.Toast.LENGTH_SHORT).show()
                                                        } else {
                                                            android.widget.Toast.makeText(context, "Failed to save photo", android.widget.Toast.LENGTH_SHORT).show()
                                                        }
                                                    } else {
                                                        android.widget.Toast.makeText(context, "Could not fetch Discord profile", android.widget.Toast.LENGTH_SHORT).show()
                                                    }
                                                } else {
                                                    android.widget.Toast.makeText(context, "Discord session expired, please re-login", android.widget.Toast.LENGTH_SHORT).show()
                                                }
                                            } catch (e: Exception) {
                                                android.util.Log.e("MyProfileScreen", "Failed to import Discord photo: ${e.message}", e)
                                                android.widget.Toast.makeText(context, "Failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                            } finally {
                                                isUploadingPhoto = false
                                                showEnlargedPhoto = false
                                            }
                                        }
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Download, contentDescription = null)
                                    }
                                )
                                if (user.photoUrl.isNotEmpty()) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                "Delete",
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        },
                                        onClick = {
                                            showPhotoOptions = false
                                            scope.launch {
                                                val updated = user.copy(photoUrl = "")
                                                val success = userDatabase.updateUser(updated)
                                                if (success) {
                                                    UserSession.setUser(updated)
                                                    showEnlargedPhoto = false
                                                }
                                            }
                                        },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Main profile screen
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (user == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("Please log in to view your profile")
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                // Top bar with username and settings icon
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(modifier = Modifier.width(40.dp)) // Balance the settings icon

                    Text(
                        text = "@${user.username.ifEmpty { "username" }}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    IconButton(onClick = { showSettingsScreen = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings"
                        )
                    }
                }

                // Profile picture and stats row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 30.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Profile Picture - clickable to enlarge with edit options
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .clickable { showEnlargedPhoto = true }
                    ) {
                        ProfileImage(
                            imageUrl = user.photoUrl,
                            contentDescription = "Profile Picture",
                            size = 100.dp
                        )
                    }

                    // Stats blocks
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        // Games stats
                        StatBlock(
                            number = "${ownedGames.size}",
                            label = "games",
                            onClick = { showGamesScreen = true }
                        )

                        // Friends stats (UMESTO followers/following)
                        StatBlock(
                            number = "${user.friendsIds.size}",
                            label = "friends",
                            onClick = { showFriendsScreen = true },
                            badgeCount = user.incomingFriendRequests.size
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // User full name
                Text(
                    text = "${user.givenName} ${user.familyName}".trim(),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))


                // About me section
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

                        // Age
                        if (user.birthday != null) {
                            val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
                            val birthYear = java.util.Calendar.getInstance().apply {
                                time = user.birthday.toDate()
                            }.get(java.util.Calendar.YEAR)
                            val age = currentYear - birthYear

                            Text(
                                text = "$age years old",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // About text
                        if (user.about.isNotEmpty()) {
                            Text(
                                text = user.about,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        // Location - show actual city
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

                // Events Statistics Card
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
                            // Hosted events
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "${user.hostedEventsCount}",
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

                            // Attended events
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "${user.attendedEventsCount}",
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
    }
}

@Composable
fun StatBlock(
    number: String,
    label: String,
    onClick: () -> Unit = {},
    badgeCount: Int = 0
) {
    Box(
        modifier = Modifier.clickable { onClick() }
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = number,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Red badge for friend requests
        if (badgeCount > 0) {
            Badge(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 8.dp, y = (-4).dp)
            ) {
                Text(
                    text = if (badgeCount > 99) "99+" else badgeCount.toString(),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}




@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyGamesScreen(
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
     val gameDatabase = remember { GameDatabase() }
    val user = UserSession.userInfo

    var games by remember { mutableStateOf<List<Game>>(emptyList()) }
    var selectedGameId by remember { mutableStateOf<String?>(null) }
    var showAddGameDialog by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var refreshTrigger by remember { mutableStateOf(0) }

    // Load games - triggered on user change or refresh
    LaunchedEffect(user, refreshTrigger) {
        scope.launch {
            user?.let { currentUser ->
                val loadedGames = mutableListOf<Game>()
                currentUser.ownedGameIds.forEach { gameId ->
                    val game = gameDatabase.getById(gameId)
                    if (game != null) {
                        loadedGames.add(game)
                    } else {
                        // Custom game
                        loadedGames.add(Game(id = gameId, name = gameId, imageUrl = null))
                    }
                }
                games = loadedGames
                isLoading = false
            }
        }
    }

    // Show game details screen
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
                    },
                    actions = {
                        IconButton(onClick = { showAddGameDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add Game"
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

    // Show add game dialog
    if (showAddGameDialog) {
        AddGameDialog(
            onDismiss = { showAddGameDialog = false },
            onGameAdded = { _ ->
                showAddGameDialog = false
                refreshTrigger++ // Trigger refresh
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddGameDialog(
    onDismiss: () -> Unit,
    onGameAdded: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val gameDatabase = remember { GameDatabase() }
    val userDatabase = remember { UserDatabase() }
    val user = UserSession.userInfo

    var gameName by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<Game>>(emptyList()) }
    var selectedGameId by remember { mutableStateOf<String?>(null) }
    var showDropdown by remember { mutableStateOf(false) }
    var isCustomGame by remember { mutableStateOf(false) }

    // Search games as user types
    LaunchedEffect(gameName) {
        if (gameName.isNotBlank()) {
            scope.launch {
                val results = gameDatabase.searchByName(gameName)
                searchResults = results
                showDropdown = results.isNotEmpty()

                // Check if exact match exists
                val exactMatch = results.find { it.name.equals(gameName, ignoreCase = true) }
                if (exactMatch != null) {
                    selectedGameId = exactMatch.id
                    isCustomGame = false
                } else {
                    selectedGameId = null
                    isCustomGame = gameName.isNotBlank()
                }
            }
        } else {
            searchResults = emptyList()
            showDropdown = false
            selectedGameId = null
            isCustomGame = false
        }
    }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Add Game",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                // Game name input with autocomplete
                Column {
                    OutlinedTextField(
                        value = gameName,
                        onValueChange = {
                            gameName = it
                            showDropdown = true
                        },
                        label = { Text("Game Name") },
                        placeholder = { Text("Start typing game name...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    // Dropdown with search results
                    if (showDropdown && searchResults.isNotEmpty()) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                        ) {
                            androidx.compose.foundation.lazy.LazyColumn {
                                items(searchResults.size) { index ->
                                    val game = searchResults[index]
                                    Text(
                                        text = game.name,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                gameName = game.name
                                                selectedGameId = game.id
                                                isCustomGame = false
                                                showDropdown = false
                                            }
                                            .padding(12.dp),
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    if (index < searchResults.size - 1) {
                                        HorizontalDivider()
                                    }
                                }
                            }
                        }
                    }

                    // Show custom game indicator
                    if (isCustomGame && gameName.isNotBlank()) {
                        Text(
                            text = "Custom game: \"$gameName\" will be added",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }


                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            if (gameName.isNotBlank() && user != null) {
                                scope.launch {
                                    // Get the game ID to add
                                    val gameIdToAdd = if (isCustomGame) {
                                        gameName // Use custom game name as ID
                                    } else {
                                        selectedGameId ?: gameName // Use selected game ID or name
                                    }

                                    // Add game to user's collection
                                    val updatedGameIds = user.ownedGameIds.toMutableList()
                                    if (!updatedGameIds.contains(gameIdToAdd)) {
                                        updatedGameIds.add(gameIdToAdd)

                                        val updatedUser = user.copy(ownedGameIds = updatedGameIds)
                                        val success = userDatabase.updateUser(updatedUser)

                                        if (success) {
                                            UserSession.setUser(updatedUser)
                                            onGameAdded(gameIdToAdd)
                                        }
                                    } else {
                                        onGameAdded(gameIdToAdd)
                                    }
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = gameName.isNotBlank()
                    ) {
                        Text("Add")
                    }
                }
            }
        }
    }
}

@Composable
fun GameCard(game: Game, onClick: () -> Unit = {}) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Game image - square with rounded corners
            if (game.imageUrl != null) {
                AsyncImage(
                    model = game.imageUrl,
                    contentDescription = game.name,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = game.name,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Game name
            Text(
                text = game.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyFriendsScreen(
    onBack: () -> Unit,
    onNavigateToMyProfile: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val userDatabase = remember { UserDatabase() }

    var friends by remember { mutableStateOf<List<com.example.druzabac.model.User>>(emptyList()) }
    var incoming by remember { mutableStateOf<List<com.example.druzabac.model.User>>(emptyList()) }

    var selectedUserId by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    // Helper: refresh session user + lists
    fun refreshAll() {
        val currentMe = UserSession.userInfo ?: return
        scope.launch {
            isLoading = true

            // refresh me from DB -> session
            userDatabase.getById(currentMe.id)?.let { refreshedMe ->
                UserSession.setUser(refreshedMe)
            }

            val updatedMe = UserSession.userInfo
            if (updatedMe == null) {
                incoming = emptyList()
                friends = emptyList()
                isLoading = false
                return@launch
            }

            // load incoming users
            val loadedIncoming = mutableListOf<com.example.druzabac.model.User>()
            updatedMe.incomingFriendRequests.forEach { uid ->
                userDatabase.getById(uid)?.let { loadedIncoming.add(it) }
            }
            incoming = loadedIncoming

            // load friends users
            val loadedFriends = mutableListOf<com.example.druzabac.model.User>()
            updatedMe.friendsIds.forEach { uid ->
                userDatabase.getById(uid)?.let { loadedFriends.add(it) }
            }
            friends = loadedFriends

            isLoading = false
        }
    }

    val me = UserSession.userInfo

    LaunchedEffect(me) {
        if (me != null) refreshAll() else isLoading = false
    }

    // Navigate to selected user's profile
    if (selectedUserId != null) {
        if (selectedUserId == UserSession.userInfo?.id) {
            onNavigateToMyProfile()
            selectedUserId = null
        } else {
            ProfileScreen(
                userId = selectedUserId!!,
                onBack = { selectedUserId = null }
            )
        }
        return
    }

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
            return@Scaffold
        }

        val currentMe = UserSession.userInfo
        if (currentMe == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) { Text("Please log in") }
            return@Scaffold
        }

        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }

            // ===== Incoming requests =====
            item {
                Text(
                    text = "Friend requests",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (incoming.isEmpty()) {
                item {
                    Text(
                        text = "No new requests",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(incoming.size) { index ->
                    val u = incoming[index]
                    FriendRequestRow(
                        user = u,
                        onAccept = {
                            scope.launch {
                                userDatabase.acceptFriendRequest(currentMe.id, u.id)
                                refreshAll()
                            }
                        },
                        onDecline = {
                            scope.launch {
                                userDatabase.declineFriendRequest(currentMe.id, u.id)
                                refreshAll()
                            }
                        },
                        onOpenProfile = { selectedUserId = u.id }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }


            // ===== Friends list =====
            item {
                Text(
                    text = "Friends",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (friends.isEmpty()) {
                item {
                    Text(
                        text = "No friends yet :(",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(friends.size) { index ->
                    val u = friends[index]
                    UserCard(
                        user = u,
                        onClick = { selectedUserId = u.id }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }
}


@Composable
fun UserCard(user: com.example.druzabac.model.User, onClick: () -> Unit = {}) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // User profile picture - circular
            ProfileImage(
                imageUrl = user.photoUrl,
                contentDescription = user.username,
                size = 48.dp
            )

            // Username
            Text(
                text = "@${user.username}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun FriendRequestRow(
    user: com.example.druzabac.model.User,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onOpenProfile: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Reuse tvoj UserCard izgled, ali ovde pravimo “row” sa dugmadima
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onOpenProfile() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ProfileImage(
                    imageUrl = user.photoUrl,
                    contentDescription = user.username,
                    size = 42.dp
                )

                Text(
                    text = "@${user.username}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
            }

            Button(onClick = onAccept) { Text("Accept") }
            OutlinedButton(onClick = onDecline) { Text("Decline") }
        }
    }
}

@Composable
private fun SentRequestRow(
    user: com.example.druzabac.model.User,
    onCancel: () -> Unit,
    onOpenProfile: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onOpenProfile() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ProfileImage(
                    imageUrl = user.photoUrl,
                    contentDescription = user.username,
                    size = 42.dp
                )

                Text(
                    text = "@${user.username}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
            }

            OutlinedButton(onClick = onCancel) { Text("Cancel") }
        }
    }
}
