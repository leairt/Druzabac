package com.example.druzabac.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.example.druzabac.auth.UserSession
import com.example.druzabac.db.CityDatabase
import com.example.druzabac.db.EventDatabase
import com.example.druzabac.db.GameDatabase
import com.example.druzabac.db.UserDatabase
import com.example.druzabac.discord.DiscordBotApi
import com.example.druzabac.model.City
import com.example.druzabac.model.Event
import com.example.druzabac.model.Game
import com.example.druzabac.model.User
import com.example.druzabac.ui.components.ProfileImage
import com.example.druzabac.ui.theme.AppliedYellow
import com.example.druzabac.ui.theme.Turquoise
import com.example.druzabac.util.geocodeAddressWithFallback
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import java.text.SimpleDateFormat
import java.util.*

private const val HOME_TAG = "HOME_EVENTS"
private const val NEARBY_RADIUS_METERS = 25000f

private data class SimpleLatLng(val latitude: Double, val longitude: Double)

private enum class HomeTab { ALL_EVENTS, MY_EVENTS }
private enum class HomeViewMode { LIST, MAP }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val cityDatabase = remember { CityDatabase() }
    val eventDatabase = remember { EventDatabase() }

    // caches to avoid N+1
    val userCache = remember { mutableStateMapOf<String, User?>() }
    val gameCache = remember { mutableStateMapOf<String, Game?>() }
    val cityCenterCache = remember { mutableStateMapOf<String, SimpleLatLng?>() }

    // ✅ acceptedCount cache (key = eventId)
    val acceptedCountCache = remember { mutableStateMapOf<String, Int>() }
    val myApplicationStatusCache = remember { mutableStateMapOf<String, String?>() }
    val mapApplicationStatusCache = remember { mutableStateMapOf<String, String?>() }

    // Unseen pending application tracking for My Events badge
    val pendingAppIdsCache = remember { mutableStateMapOf<String, List<String>>() }
    val unseenCountByEvent = remember { mutableStateMapOf<String, Int>() }
    var totalUnseenCount by remember { mutableStateOf(0) }

    var tab by remember { mutableStateOf(HomeTab.ALL_EVENTS) }
    var viewMode by remember { mutableStateOf(HomeViewMode.LIST) }
    var showViewModeSheet by remember { mutableStateOf(false) }

    var cities by remember { mutableStateOf<List<City>>(emptyList()) }
    var selectedCity by remember { mutableStateOf<City?>(null) }
    var expanded by remember { mutableStateOf(false) }
    var isLoadingCities by remember { mutableStateOf(true) }

    // ✅ Single source of truth for "meId"
    val meId = remember {
        UserSession.userInfo?.id.orEmpty()
    }

    // paging
    val pageSize = 10L

    var cityEvents by remember { mutableStateOf<List<Event>>(emptyList()) }
    var cityLastDoc by remember { mutableStateOf<DocumentSnapshot?>(null) }
    var cityEndReached by remember { mutableStateOf(false) }
    var cityLoadingMore by remember { mutableStateOf(false) }

    var myEvents by remember { mutableStateOf<List<Event>>(emptyList()) }
    var myLastDoc by remember { mutableStateOf<DocumentSnapshot?>(null) }
    var myEndReached by remember { mutableStateOf(false) }
    var myLoadingMore by remember { mutableStateOf(false) }

    var showCreateEventScreen by remember { mutableStateOf(false) }
    var selectedEvent by remember { mutableStateOf<Event?>(null) }

    // Map state persistence — only kept when returning from overlay, reset when going to full details
    var savedMapZoom by remember { mutableStateOf<Double?>(null) }
    var savedMapCenter by remember { mutableStateOf<SimpleLatLng?>(null) }
    var preserveMapState by remember { mutableStateOf(false) }

    // Overlay event for map pin click
    var overlayEvent by remember { mutableStateOf<Event?>(null) }

    var isRefreshing by remember { mutableStateOf(false) }
    var myLocation by remember { mutableStateOf<SimpleLatLng?>(null) }
    var hasLocationPermission by remember { mutableStateOf(false) }
    val fusedLocationClient = remember(context) { LocationServices.getFusedLocationProviderClient(context) }

    fun loadMyLocation() {
        if (!hasLocationPermission) return
        try {
            fusedLocationClient
                .getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    CancellationTokenSource().token
                )
                .addOnSuccessListener { current ->
                    if (current != null) {
                        myLocation = SimpleLatLng(current.latitude, current.longitude)
                    } else {
                        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                            if (location != null) {
                                myLocation = SimpleLatLng(location.latitude, location.longitude)
                            }
                        }
                    }
                }
        } catch (_: SecurityException) {
            hasLocationPermission = false
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        hasLocationPermission =
            perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (hasLocationPermission) {
            loadMyLocation()
        } else {
            Toast.makeText(context, "Location permission is needed for nearby map view", Toast.LENGTH_SHORT).show()
        }
    }

    // Get current user's friend IDs for sorting
    val friendIds = remember { UserSession.userInfo?.friendsIds ?: emptyList() }

    // Function to sort events: non-grayed first, then friends' events, then by time
    fun sortEvents(events: List<Event>): List<Event> {
        return events.sortedWith(
            compareBy<Event> { event ->
                // First: grayed out events go to bottom (FINALIZED, CANCELLED, or full)
                val acceptedCount = acceptedCountCache[event.id] ?: 0
                val isGrayedOut = event.status == "FINALIZED" || event.status == "CANCELLED" ||
                    (event.maxPlayers - acceptedCount) <= 0
                if (isGrayedOut) 1 else 0
            }.thenBy { event ->
                // Second: non-friend events go after friend events
                if (friendIds.contains(event.hostId)) 0 else 1
            }.thenBy { event ->
                // Third: sort by dateTime (earliest first)
                event.dateTime.toDate().time
            }
        )
    }

    suspend fun refreshCityEvents() {
        val city = selectedCity ?: return

        cityEvents = emptyList()
        cityLastDoc = null
        cityEndReached = false

        // Run cleanup before loading events
        eventDatabase.cleanupEvents()

        val page = eventDatabase.getCityEventsFromNowPaged(
            cityId = city.id,
            pageSize = pageSize,
            lastDoc = null,
            excludeHostId = meId
        )

        page.items.forEachIndexed { index, event ->
            Log.d(
                HOME_TAG,
                "ITEM[$index] id=${event.id}, cityId=${event.cityId}, " +
                        "hostId=${event.hostId}, dateTime=${event.dateTime.toDate()}"
            )
        }

        // Sort events before displaying
        cityEvents = sortEvents(page.items)
        cityLastDoc = page.lastDoc
        cityEndReached = page.items.size < pageSize
    }

    suspend fun refreshMyEvents() {
        val city = selectedCity ?: return

        if (meId.isBlank()) {
            myEvents = emptyList()
            myLastDoc = null
            myEndReached = true
            return
        }

        myEvents = emptyList()
        myLastDoc = null
        myEndReached = false

        val page = eventDatabase.getHostingEventsFromNowPaged(meId, city.id, pageSize, null)
        // Sort events before displaying
        myEvents = sortEvents(page.items)
        myLastDoc = page.lastDoc
        if (page.items.size < pageSize) myEndReached = true
    }

    suspend fun refreshAllLists() {
        acceptedCountCache.clear()
        myApplicationStatusCache.clear()
        mapApplicationStatusCache.clear()
        refreshCityEvents()
        refreshMyEvents()
    }

    suspend fun refreshActiveTab() {
        acceptedCountCache.clear()
        myApplicationStatusCache.clear()
        mapApplicationStatusCache.clear()
        when (tab) {
            HomeTab.ALL_EVENTS -> refreshCityEvents()
            HomeTab.MY_EVENTS -> refreshMyEvents()
        }
    }

    suspend fun loadMoreActiveTab() {
        val city = selectedCity ?: return

        when (tab) {
            HomeTab.ALL_EVENTS -> {
                if (cityLoadingMore || cityEndReached) return
                val cursor = cityLastDoc ?: return

                cityLoadingMore = true
                val page = eventDatabase.getCityEventsFromNowPaged(city.id, pageSize, cursor, excludeHostId = meId)
                // Sort the combined list
                cityEvents = sortEvents(cityEvents + page.items)
                cityLastDoc = page.lastDoc
                if (page.items.isEmpty() || page.items.size < pageSize) cityEndReached = true
                cityLoadingMore = false
            }

            HomeTab.MY_EVENTS -> {
                if (myLoadingMore || myEndReached) return
                val cursor = myLastDoc ?: return
                if (meId.isBlank()) return

                myLoadingMore = true
                val page = eventDatabase.getHostingEventsFromNowPaged(meId, city.id, pageSize, cursor)
                // Sort the combined list
                myEvents = sortEvents(myEvents + page.items)
                myLastDoc = page.lastDoc
                if (page.items.isEmpty() || page.items.size < pageSize) myEndReached = true
                myLoadingMore = false
            }
        }
    }

    // Load cities (default city = user's cityId if possible)
    LaunchedEffect(Unit) {
        scope.launch {
            isLoadingCities = true
            cities = cityDatabase.getAllCities()

            val myCityId = UserSession.userInfo?.cityId
            selectedCity = cities.firstOrNull { it.id == myCityId } ?: cities.firstOrNull()

            isLoadingCities = false
        }
    }

    LaunchedEffect(Unit) {
        hasLocationPermission =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (hasLocationPermission) loadMyLocation()
    }

    // When city changes: refresh BOTH lists (so switching tabs is instant)
    LaunchedEffect(selectedCity?.id) {
        if (selectedCity != null) {
            scope.launch {
                userCache.clear()
                gameCache.clear()
                refreshAllLists()
            }
        }
    }

    LaunchedEffect(viewMode, tab, cityEvents.map { it.id }, myEvents.map { it.id }, meId) {
        if (viewMode != HomeViewMode.MAP || tab != HomeTab.ALL_EVENTS || meId.isBlank()) return@LaunchedEffect
        cityEvents.forEach { event ->
            if (!mapApplicationStatusCache.containsKey(event.id)) {
                mapApplicationStatusCache[event.id] = eventDatabase.getMyActiveApplication(event.id, meId)?.status
            }
        }
    }

    LaunchedEffect(selectedCity?.id) {
        val city = selectedCity ?: return@LaunchedEffect
        if (cityCenterCache.containsKey(city.id)) return@LaunchedEffect
        val center = geocodeAddressWithFallback(
            context = context,
            queries = listOf(
                "${city.name}, ${city.country}",
                city.name
            )
        )
        cityCenterCache[city.id] = center?.let { SimpleLatLng(it.latitude, it.longitude) }
    }

    // Load unseen pending application counts for My Events badge
    LaunchedEffect(myEvents.map { it.id }.joinToString()) {
        if (meId.isBlank()) return@LaunchedEffect
        val prefs = context.getSharedPreferences("druzabac_seen_apps", android.content.Context.MODE_PRIVATE)
        var total = 0
        myEvents.forEach { event ->
            val pendingIds = eventDatabase.getPendingApplicationIds(event.id)
            pendingAppIdsCache[event.id] = pendingIds
            val seenIds = prefs.getStringSet("seen_${event.id}", emptySet()) ?: emptySet()
            val unseen = pendingIds.count { it !in seenIds }
            unseenCountByEvent[event.id] = unseen
            total += unseen
        }
        totalUnseenCount = total
    }

    // When tab changes: refresh that tab
    LaunchedEffect(tab) {
        if (selectedCity != null) {
            scope.launch { refreshActiveTab() }
        }
    }

    // Navigation
    if (selectedEvent != null) {
        EventDetailsScreen(
            event = selectedEvent!!,
            onBack = {
                val eventId = selectedEvent!!.id
                selectedEvent = null
                // Refresh application status for this event so pin color updates
                mapApplicationStatusCache.remove(eventId)
                myApplicationStatusCache.remove(eventId)
                scope.launch {
                    if (meId.isNotBlank()) {
                        mapApplicationStatusCache[eventId] = eventDatabase.getMyActiveApplication(eventId, meId)?.status
                    }
                    // Refresh unseen pending app count for the viewed event
                    val pendingIds = eventDatabase.getPendingApplicationIds(eventId)
                    pendingAppIdsCache[eventId] = pendingIds
                    val prefs = context.getSharedPreferences("druzabac_seen_apps", android.content.Context.MODE_PRIVATE)
                    val seenIds = prefs.getStringSet("seen_$eventId", emptySet()) ?: emptySet()
                    unseenCountByEvent[eventId] = pendingIds.count { it !in seenIds }
                    totalUnseenCount = unseenCountByEvent.values.sum()
                }
                // Reset map state so map re-centers on user location
                if (!preserveMapState) {
                    savedMapZoom = null
                    savedMapCenter = null
                }
                preserveMapState = false
            }
        )
        return
    }

    if (showCreateEventScreen && selectedCity != null) {
        CreateEventScreen(
            selectedCity = selectedCity!!,
            onBack = { showCreateEventScreen = false },
            onSave = { newEvent ->
                scope.launch {
                    val eventId = eventDatabase.insertEvent(newEvent)
                    if (eventId != null) {
                        // Create PRIVATE Discord thread for the event in city channel
                        try {
                            val currentUser = UserSession.userInfo
                            val city = selectedCity // Use the currently selected city
                            val gdb = GameDatabase() // Create GameDatabase instance

                            if (currentUser != null && city != null) {
                                // Get game names
                                val gameNames = newEvent.gameIds.mapNotNull { gameId ->
                                    gameCache[gameId]?.name ?: gdb.getById(gameId)?.name
                                }

                                // Format date and time
                                val dateFormat = SimpleDateFormat("dd.MM", Locale.getDefault())
                                val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                                val eventDate = dateFormat.format(newEvent.dateTime.toDate())
                                val eventTime = timeFormat.format(newEvent.dateTime.toDate())

                                // Create PRIVATE event thread (bot, host, and accepted players only)
                                val threadId = DiscordBotApi.createPrivateEventThread(
                                    eventName = gameNames.joinToString(", "),
                                    eventDate = eventDate,
                                    eventTime = eventTime,
                                    games = gameNames,
                                    hostName = currentUser.username,
                                    hostDiscordId = currentUser.id,
                                    location = newEvent.location + (newEvent.district?.let { ", $it" } ?: ""),
                                    cityName = city.name
                                )

                                if (threadId != null) {
                                    // Update event with Discord thread ID
                                    eventDatabase.updateDiscordThreadId(eventId, threadId)
                                    Log.d("HomeScreen", "Private Discord thread created: $threadId")
                                } else {
                                    Log.w("HomeScreen", "Failed to create Discord thread")
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("HomeScreen", "Error creating Discord thread: ${e.message}", e)
                            // Don't fail event creation if Discord fails
                        }

                        Toast.makeText(context, "Event created!", Toast.LENGTH_SHORT).show()
                        userCache.clear()
                        gameCache.clear()
                        refreshAllLists()
                    } else {
                        Toast.makeText(context, "Failed to create event", Toast.LENGTH_SHORT).show()
                    }
                    showCreateEventScreen = false
                }
            }
        )
        return
    }

    val activeList = if (tab == HomeTab.ALL_EVENTS) cityEvents else myEvents
    val loadingMore = if (tab == HomeTab.ALL_EVENTS) cityLoadingMore else myLoadingMore
    val endReached = if (tab == HomeTab.ALL_EVENTS) cityEndReached else myEndReached

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateEventScreen = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Create Event")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(vertical = 8.dp)
        ) {
            // Top row: title + city picker
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Events",
                    style = MaterialTheme.typography.headlineMedium
                )

                Spacer(modifier = Modifier.weight(1f))

                if (isLoadingCities) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp))
                } else if (selectedCity != null) {
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        Row(
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .wrapContentWidth(Alignment.End),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${selectedCity!!.name}, ${selectedCity!!.country}")
                            Icon(Icons.Default.ArrowDropDown, contentDescription = "Dropdown")
                        }

                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            cities.forEach { city ->
                                DropdownMenuItem(
                                    text = { Text("${city.name}, ${city.country}") },
                                    onClick = {
                                        selectedCity = city
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            TabRow(
                selectedTabIndex = if (tab == HomeTab.ALL_EVENTS) 0 else 1,
                modifier = Modifier.padding(horizontal = 18.dp)
            ) {
                Tab(
                    selected = tab == HomeTab.ALL_EVENTS,
                    onClick = { tab = HomeTab.ALL_EVENTS },
                    text = { Text("All events") }
                )
                Tab(
                    selected = tab == HomeTab.MY_EVENTS,
                    onClick = { tab = HomeTab.MY_EVENTS },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("My events")

                            if (totalUnseenCount > 0) {
                                Badge {
                                    Text(totalUnseenCount.toString())
                                }
                            }
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (viewMode == HomeViewMode.MAP) {
                val nearbyEvents = remember(activeList, myLocation) {
                    if (myLocation == null) {
                        activeList.filter { it.latitude != null && it.longitude != null }
                    } else {
                        activeList.filter { event ->
                            isEventWithinRadius(
                                origin = myLocation!!,
                                latitude = event.latitude,
                                longitude = event.longitude,
                                radiusMeters = NEARBY_RADIUS_METERS
                            )
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = true)
                ) {
                    HomeEventsMap(
                        events = nearbyEvents,
                        myLocation = myLocation,
                        selectedCityName = selectedCity?.name,
                        selectedCityCenter = selectedCity?.id?.let { cityCenterCache[it] },
                        applicationStatusByEventId = mapApplicationStatusCache,
                        isAllEventsMap = tab == HomeTab.ALL_EVENTS,
                        hasLocationPermission = hasLocationPermission,
                        savedZoom = savedMapZoom,
                        savedCenter = savedMapCenter,
                        onMapMoved = { zoom, center ->
                            savedMapZoom = zoom
                            savedMapCenter = center
                        },
                        onRequestLocationPermission = {
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        },
                        onEventClick = { clicked ->
                            overlayEvent = clicked
                        }
                    )

                    // Half-screen overlay for event details
                    androidx.compose.animation.AnimatedVisibility(
                        visible = overlayEvent != null,
                        enter = fadeIn() + slideInVertically { -it },
                        exit = fadeOut() + slideOutVertically { -it }
                    ) {
                        overlayEvent?.let { event ->
                            MapEventOverlay(
                                event = event,
                                userCache = userCache,
                                gameCache = gameCache,
                                acceptedCountCache = acceptedCountCache,
                                myApplicationStatusCache = myApplicationStatusCache,
                                meId = meId,
                                shouldHighlightMyApplication = tab == HomeTab.ALL_EVENTS,
                                onViewDetails = {
                                    scope.launch {
                                        val full = eventDatabase.getById(event.id)
                                        preserveMapState = true
                                        selectedEvent = full ?: event
                                        overlayEvent = null
                                    }
                                },
                                onDismiss = { overlayEvent = null }
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(horizontal = 18.dp, vertical = 10.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
                        shape = RoundedCornerShape(14.dp),
                        tonalElevation = 2.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TopEventControlsRow(
                                showLegend = tab == HomeTab.ALL_EVENTS,
                                onChooseView = { showViewModeSheet = true }
                            )
                            Text(
                                text = if (myLocation != null) {
                                    "Nearby within ${NEARBY_RADIUS_METERS.toInt() / 1000} km"
                                } else {
                                    "Enable location for nearby events"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                TopEventControlsRow(
                    modifier = Modifier.padding(horizontal = 18.dp),
                    showLegend = tab == HomeTab.ALL_EVENTS,
                    onChooseView = { showViewModeSheet = true }
                )
                Spacer(modifier = Modifier.height(8.dp))

                SwipeRefresh(
                    state = rememberSwipeRefreshState(isRefreshing),
                    onRefresh = {
                        scope.launch {
                            isRefreshing = true
                            userCache.clear()
                            gameCache.clear()
                            refreshActiveTab()
                            isRefreshing = false
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = true)
                        .padding(horizontal = 18.dp)
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        if (activeList.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillParentMaxSize()
                                        .padding(top = 60.dp),
                                    contentAlignment = Alignment.TopCenter
                                ) {
                                    Text(
                                        text = when (tab) {
                                            HomeTab.ALL_EVENTS -> "No upcoming events in ${selectedCity?.name}"
                                            HomeTab.MY_EVENTS -> "You have no upcoming hosted events"
                                        },
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            itemsIndexed(activeList) { index, event ->
                                EventCard(
                                    event = event,
                                    userCache = userCache,
                                    gameCache = gameCache,
                                    acceptedCountCache = acceptedCountCache,
                                    myApplicationStatusCache = myApplicationStatusCache,
                                    meId = meId,
                                    shouldHighlightMyApplication = tab == HomeTab.ALL_EVENTS,
                                    isMyEvent = tab == HomeTab.MY_EVENTS,
                                    unseenAppCount = if (tab == HomeTab.MY_EVENTS) unseenCountByEvent[event.id] ?: 0 else 0,
                                    onClick = {
                                        scope.launch {
                                            val full = eventDatabase.getById(event.id)
                                            selectedEvent = full ?: event
                                            // Mark pending apps as seen for my events
                                            if (tab == HomeTab.MY_EVENTS) {
                                                val pendingIds = pendingAppIdsCache[event.id] ?: eventDatabase.getPendingApplicationIds(event.id)
                                                val prefs = context.getSharedPreferences("druzabac_seen_apps", android.content.Context.MODE_PRIVATE)
                                                val existing = (prefs.getStringSet("seen_${event.id}", emptySet()) ?: emptySet()).toMutableSet()
                                                existing.addAll(pendingIds)
                                                prefs.edit().putStringSet("seen_${event.id}", existing).apply()
                                                unseenCountByEvent[event.id] = 0
                                                totalUnseenCount = unseenCountByEvent.values.sum()
                                            }
                                        }
                                    }
                                )

                                if (index >= activeList.size - 3) {
                                    LaunchedEffect(activeList.size, tab, selectedCity?.id) {
                                        loadMoreActiveTab()
                                    }
                                }
                            }

                            item {
                                when {
                                    loadingMore -> {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                            contentAlignment = Alignment.Center
                                        ) { CircularProgressIndicator() }
                                    }
                                    endReached -> Spacer(modifier = Modifier.height(8.dp))
                                    else -> Spacer(modifier = Modifier.height(8.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showViewModeSheet) {
        ModalBottomSheet(
            onDismissRequest = { showViewModeSheet = false }
        ) {
            ViewModeSheetContent(
                currentMode = viewMode,
                onModeSelected = { mode ->
                    viewMode = mode
                    showViewModeSheet = false
                    if (mode == HomeViewMode.MAP) {
                        if (!hasLocationPermission) {
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        } else {
                            loadMyLocation()
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun EventCard(
    event: Event,
    userCache: MutableMap<String, User?>,
    gameCache: MutableMap<String, Game?>,
    acceptedCountCache: MutableMap<String, Int>,
    myApplicationStatusCache: MutableMap<String, String?>,
    meId: String,
    shouldHighlightMyApplication: Boolean,
    isMyEvent: Boolean = false,
    unseenAppCount: Int = 0,
    onClick: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val gameDatabase = remember { GameDatabase() }
    val userDatabase = remember { UserDatabase() }
    val eventDatabase = remember { EventDatabase() }

    var gameNames by remember { mutableStateOf<List<String>>(emptyList()) }
    var hostUser by remember { mutableStateOf<User?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    // ✅ acceptedCount (lazy) + cache
    val acceptedCount by produceState<Int?>(initialValue = acceptedCountCache[event.id]) {
        if (acceptedCountCache.containsKey(event.id)) return@produceState
        value = null
        value = fetchAcceptedCountForEvent(event.id, event.acceptedApplicationIds)
        acceptedCountCache[event.id] = value ?: 0
    }

    // Calculate spotsLeft - depends on acceptedCount state
    val spotsLeft = (event.maxPlayers - (acceptedCount ?: 0)).coerceAtLeast(0)
    val myApplicationStatus by produceState<String?>(
        initialValue = myApplicationStatusCache[event.id]
    ) {
        if (!shouldHighlightMyApplication || meId.isBlank()) {
            value = null
            return@produceState
        }
        if (myApplicationStatusCache.containsKey(event.id)) return@produceState
        val app = eventDatabase.getMyActiveApplication(event.id, meId)
        value = app?.status
        myApplicationStatusCache[event.id] = value
    }

    // Calculate isNew - simple calculation (30 minutes = 30 * 60 * 1000)
    val isNew = remember(event.createdAt) {
        val createdMs = event.createdAt.toDate().time
        val nowMs = System.currentTimeMillis()
        val thirtyMinutesMs = 30 * 60 * 1000L
        val result = (nowMs - createdMs) <= thirtyMinutesMs
        Log.d("EventCard", "Event ${event.id}: isNew=$result, createdMs=$createdMs, nowMs=$nowMs, diff=${(nowMs - createdMs) / 1000 / 60} min")
        result
    }

    // Debug log for spotsLeft
    Log.d("EventCard", "Event ${event.id}: spotsLeft=$spotsLeft, maxPlayers=${event.maxPlayers}, acceptedCount=$acceptedCount")

    LaunchedEffect(event.id, event.hostId, event.gameIds) {
        scope.launch {
            val cached = userCache[event.hostId]
            val needsRefetch = cached == null || cached.username.isBlank()

            hostUser = if (!needsRefetch) {
                cached
            } else {
                delay(80)
                val u = userDatabase.getById(event.hostId)
                userCache[event.hostId] = u
                u
            }

            val names = mutableListOf<String>()
            event.gameIds.forEach { gameId ->
                val cachedGame = gameCache[gameId]
                val g = if (cachedGame != null) cachedGame else {
                    val loaded = gameDatabase.getById(gameId)
                    gameCache[gameId] = loaded
                    loaded
                }
                names.add(g?.name ?: gameId)
            }
            gameNames = names
            isLoading = false
        }
    }

    // Check if event should be grayed out (finalized, cancelled, or full)
    val isGrayedOut = event.status == "FINALIZED" || event.status == "CANCELLED" || spotsLeft == 0
    val cardAlpha = if (isGrayedOut) 0.5f else 1f
    val borderColor = when {
        shouldHighlightMyApplication && myApplicationStatus == "ACCEPTED" -> MaterialTheme.colorScheme.primary
        shouldHighlightMyApplication && myApplicationStatus == "PENDING" -> AppliedYellow
        else -> null
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isGrayedOut) Modifier.alpha(cardAlpha) else Modifier)
            .then(
                if (borderColor != null) Modifier.border(
                    width = 2.dp,
                    color = borderColor,
                    shape = RoundedCornerShape(12.dp)
                ) else Modifier
            ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        onClick = onClick
    ) {
        Box {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Game names with badges inline
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Game names text - use weight to allow shrinking for badges
                Text(
                    text = if (isLoading) "Loading..." else gameNames.joinToString(", "),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f, fill = false),
                    maxLines = 2
                )

                // Badges directly after text (hidden for my own events)
                if (!isLoading && isNew && !isMyEvent) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "NEW",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // LAST SPOT badge (hidden for my own events)
                if (!isLoading && spotsLeft == 1 && !isMyEvent) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.error)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "LAST SPOT",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onError,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

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
                    Text(formatEventDate(event.dateTime.toDate()), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        formatEventTime(event.dateTime.toDate()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Host row with players info on right
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Host info on left
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ProfileImage(
                        imageUrl = hostUser?.photoUrl,
                        contentDescription = "Host photo",
                        size = 24.dp
                    )

                    val username = hostUser?.username?.takeIf { it.isNotBlank() }
                    if (username != null) {
                        Text(
                            text = username,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = "unknown",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Players info on right
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
                        text = "$spotsLeft",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        // Red badge for unseen pending applications on my events
        if (unseenAppCount > 0) {
            Badge(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
            ) {
                Text("$unseenAppCount")
            }
        }
        } // Box
    }
}

@Composable
private fun ApplicationStatusLegend() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        LegendItem(
            label = "Applied",
            color = AppliedYellow
        )
        LegendItem(
            label = "Accepted",
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun TopEventControlsRow(
    modifier: Modifier = Modifier,
    showLegend: Boolean,
    onChooseView: () -> Unit
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        FilledIconButton(
            onClick = onChooseView,
            modifier = Modifier.size(40.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Icon(
                imageVector = Icons.Default.Layers,
                contentDescription = "Choose view",
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }

        if (showLegend) {
            ApplicationStatusLegend()
        }
    }
}

@Composable
private fun ViewModeSheetContent(
    currentMode: HomeViewMode,
    onModeSelected: (HomeViewMode) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 220.dp, max = 320.dp)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Choose view",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        ViewModeOptionRow(
            label = "List",
            icon = { Icon(Icons.Default.FormatListBulleted, contentDescription = null) },
            selected = currentMode == HomeViewMode.LIST,
            onClick = { onModeSelected(HomeViewMode.LIST) }
        )

        ViewModeOptionRow(
            label = "Map",
            icon = { Icon(Icons.Default.Map, contentDescription = null) },
            selected = currentMode == HomeViewMode.MAP,
            onClick = { onModeSelected(HomeViewMode.MAP) }
        )

        Spacer(modifier = Modifier.height(6.dp))
    }
}

@Composable
private fun ViewModeOptionRow(
    label: String,
    icon: @Composable () -> Unit,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp)),
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surface,
        tonalElevation = if (selected) 1.dp else 0.dp,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                CompositionLocalProvider(
                    LocalContentColor provides if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                ) {
                    icon()
                }
            }

            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            )
        }
    }
}

@Composable
private fun LegendItem(label: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun MapEventOverlay(
    event: Event,
    userCache: MutableMap<String, User?>,
    gameCache: MutableMap<String, Game?>,
    acceptedCountCache: MutableMap<String, Int>,
    myApplicationStatusCache: MutableMap<String, String?>,
    meId: String,
    shouldHighlightMyApplication: Boolean,
    onViewDetails: () -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler { onDismiss() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onDismiss() }
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.5f)
                .align(Alignment.TopCenter)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { /* consume click to prevent dismissing */ },
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp,
            shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Top bar with back arrow
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Close"
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                }

                // Event card content
                EventCard(
                    event = event,
                    userCache = userCache,
                    gameCache = gameCache,
                    acceptedCountCache = acceptedCountCache,
                    myApplicationStatusCache = myApplicationStatusCache,
                    meId = meId,
                    shouldHighlightMyApplication = shouldHighlightMyApplication,
                    onClick = onViewDetails
                )
            }
        }
    }
}

@Composable
private fun HomeEventsMap(
    events: List<Event>,
    myLocation: SimpleLatLng?,
    selectedCityName: String?,
    selectedCityCenter: SimpleLatLng?,
    applicationStatusByEventId: Map<String, String?>,
    isAllEventsMap: Boolean,
    hasLocationPermission: Boolean,
    savedZoom: Double?,
    savedCenter: SimpleLatLng?,
    onMapMoved: (Double, SimpleLatLng) -> Unit,
    onRequestLocationPermission: () -> Unit,
    onEventClick: (Event) -> Unit
) {
    val context = LocalContext.current
    val defaultPoint = GeoPoint(44.8125, 20.4612)
    val myLocationPinColor = Color(0xFFE53935).toArgb() // red
    val acceptedPinColor = Color(0xFF2E7D32).toArgb() // green
    val appliedPinColor = AppliedYellow.toArgb() // yellow
    val otherPinColor = Turquoise.toArgb() // turquoise

    // Track whether we've already set the initial position
    val hasInitialized = remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().clipToBounds()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                Configuration.getInstance().load(
                    ctx,
                    ctx.getSharedPreferences("osmdroid", android.content.Context.MODE_PRIVATE)
                )
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)

                    // Restore saved state (returning from overlay details) or center on user
                    val initZoom = savedZoom ?: 15.0
                    val initCenter = savedCenter?.let { GeoPoint(it.latitude, it.longitude) }
                        ?: myLocation?.let { GeoPoint(it.latitude, it.longitude) }
                        ?: defaultPoint
                    controller.setZoom(initZoom)
                    controller.setCenter(initCenter)

                    isHorizontalMapRepetitionEnabled = false
                    isVerticalMapRepetitionEnabled = false
                    setScrollableAreaLimitLatitude(
                        MapView.getTileSystem().maxLatitude,
                        MapView.getTileSystem().minLatitude,
                        0
                    )
                    setScrollableAreaLimitLongitude(
                        MapView.getTileSystem().minLongitude,
                        MapView.getTileSystem().maxLongitude,
                        0
                    )
                    minZoomLevel = 3.0
                    maxZoomLevel = 19.0

                    // Save map state on scroll/zoom
                    addMapListener(object : org.osmdroid.events.MapListener {
                        override fun onScroll(event: org.osmdroid.events.ScrollEvent?): Boolean {
                            val center = mapCenter as GeoPoint
                            onMapMoved(zoomLevelDouble, SimpleLatLng(center.latitude, center.longitude))
                            return false
                        }
                        override fun onZoom(event: org.osmdroid.events.ZoomEvent?): Boolean {
                            val center = mapCenter as GeoPoint
                            onMapMoved(zoomLevelDouble, SimpleLatLng(center.latitude, center.longitude))
                            return false
                        }
                    })

                    hasInitialized.value = true
                }
            },
            update = { mapView ->
                mapView.overlays.clear()

                // Only reposition if we haven't initialized yet (first render without saved state)
                if (!hasInitialized.value) {
                    val target = selectedCityCenter?.let { GeoPoint(it.latitude, it.longitude) }
                        ?: myLocation?.let { GeoPoint(it.latitude, it.longitude) }
                        ?: events.firstOrNull()?.let { e ->
                            if (e.latitude != null && e.longitude != null) GeoPoint(e.latitude, e.longitude) else null
                        } ?: defaultPoint
                    mapView.controller.setCenter(target)
                    mapView.controller.setZoom(
                        when {
                            selectedCityCenter != null -> 14.0
                            myLocation != null -> 15.0
                            else -> 13.0
                        }
                    )
                }

                events.forEach { event ->
                    val lat = event.latitude
                    val lng = event.longitude
                    if (lat != null && lng != null) {
                        val status = applicationStatusByEventId[event.id]
                        val pinColor = when (status) {
                            "ACCEPTED" -> if (isAllEventsMap) acceptedPinColor else otherPinColor
                            "PENDING" -> if (isAllEventsMap) appliedPinColor else otherPinColor
                            else -> otherPinColor
                        }
                        val marker = Marker(mapView).apply {
                            position = GeoPoint(lat, lng)
                            title = event.location
                            icon = markerIcon(context, pinColor)
                            setOnMarkerClickListener { _, _ ->
                                onEventClick(event)
                                true
                            }
                        }
                        mapView.overlays.add(marker)
                    }
                }

                myLocation?.let {
                    val meMarker = Marker(mapView).apply {
                        position = GeoPoint(it.latitude, it.longitude)
                        title = "You are here"
                        icon = markerIcon(context, myLocationPinColor)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    }
                    mapView.overlays.add(meMarker)

                    // Accuracy-like highlight to make your location easier to spot.
                    val circle = Polygon().apply {
                        points = Polygon.pointsAsCircle(GeoPoint(it.latitude, it.longitude), 120.0)
                        fillPaint.color = Color(0x33E53935).toArgb()
                        outlinePaint.color = Color(0x99E53935).toArgb()
                        outlinePaint.strokeWidth = 2f
                    }
                    mapView.overlays.add(circle)
                }
                mapView.invalidate()
            }
        )

        if (!hasLocationPermission) {
            Card(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Allow location to show nearby events",
                        style = MaterialTheme.typography.bodySmall
                    )
                    TextButton(onClick = onRequestLocationPermission) {
                        Text("Enable")
                    }
                }
            }
        }
    }
}

private fun isEventWithinRadius(
    origin: SimpleLatLng,
    latitude: Double?,
    longitude: Double?,
    radiusMeters: Float
): Boolean {
    if (latitude == null || longitude == null) return false
    val results = FloatArray(1)
    Location.distanceBetween(origin.latitude, origin.longitude, latitude, longitude, results)
    return results[0] <= radiusMeters
}

private fun markerIcon(context: android.content.Context, color: Int) =
    ContextCompat.getDrawable(context, org.osmdroid.library.R.drawable.marker_default)?.mutate()?.also {
        DrawableCompat.setTint(it, color)
    }

/**
 * ✅ Accepted count = count UNIQUE users across acceptedApplicationIds
 */
private suspend fun fetchAcceptedCountForEvent(eventId: String, acceptedApplicationIds: List<String>): Int {
    if (acceptedApplicationIds.isEmpty()) return 0
    val db = FirebaseFirestore.getInstance()

    val unique = mutableSetOf<String>()
    for (appId in acceptedApplicationIds) {
        val snap = db.collection("events")
            .document(eventId)
            .collection("applications")
            .document(appId)
            .get()
            .await()

        if (snap.exists()) {
            val members = (snap.get("memberUserIds") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
            unique.addAll(members)
        }
    }
    return unique.size
}

private fun formatEventDate(date: Date): String {
    val calendar = Calendar.getInstance()
    val today = Calendar.getInstance()
    val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }

    calendar.time = date
    return when {
        isSameDay(calendar, today) -> "Today"
        isSameDay(calendar, tomorrow) -> "Tomorrow"
        else -> SimpleDateFormat("EEE, dd MMM", Locale.ENGLISH).format(date)
    }
}

private fun formatEventTime(date: Date): String {
    val timeFormat = SimpleDateFormat("HH:mm", Locale.ENGLISH)
    return timeFormat.format(date)
}

private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
            cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}
