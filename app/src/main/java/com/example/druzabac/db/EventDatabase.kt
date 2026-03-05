package com.example.druzabac.db

import android.util.Log
import com.example.druzabac.model.Event
import com.example.druzabac.model.EventApplication
import com.google.firebase.Timestamp
import com.google.firebase.firestore.*
import kotlinx.coroutines.tasks.await

class EventDatabase {
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val eventsCollection = firestore.collection("events")

    private val userDatabase = UserDatabase()

    companion object {
        private const val TAG = "EventDatabase"
    }

    // -----------------------------
    // Events
    // -----------------------------

    suspend fun insertEvent(event: Event): String? {
        return try {
            val data = hashMapOf(
                "gameIds" to event.gameIds,
                "cityId" to event.cityId,
                "district" to event.district,
                "location" to event.location,
                "latitude" to event.latitude,
                "longitude" to event.longitude,
                "dateTime" to event.dateTime,
                "players" to event.players,
                "hostId" to event.hostId,
                "createdAt" to event.createdAt,
                "status" to event.status,
                "minPlayers" to event.minPlayers,
                "maxPlayers" to event.maxPlayers,
                "acceptedApplicationIds" to event.acceptedApplicationIds,
                "discordThreadId" to event.discordThreadId
            )
            val ref = eventsCollection.add(data).await()
            ref.id
        } catch (e: Exception) {
            Log.e(TAG, "insertEvent error: ${e.message}", e)
            null
        }
    }

    suspend fun updateStatus(eventId: String, status: String): Boolean {
        return try {
            // If cancelling, get event and attendees first for notifications
            val event = if (status == "CANCELLED") getById(eventId) else null
            val acceptedApps = if (status == "CANCELLED" && event != null) {
                getAcceptedApplications(eventId)
            } else emptyList()

            eventsCollection.document(eventId).update("status", status).await()

            true
        } catch (e: Exception) {
            Log.e(TAG, "updateStatus error: ${e.message}", e)
            false
        }
    }

    suspend fun updateEvent(updatedEvent: Event): Boolean {
        return try {
            val data = hashMapOf<String, Any?>(
                "location" to updatedEvent.location,
                "latitude" to updatedEvent.latitude,
                "longitude" to updatedEvent.longitude,
                "district" to updatedEvent.district,
                "dateTime" to updatedEvent.dateTime,
                "players" to updatedEvent.players,
                "minPlayers" to updatedEvent.minPlayers,
                "maxPlayers" to updatedEvent.maxPlayers
            )
            eventsCollection.document(updatedEvent.id).update(data).await()
            Log.d(TAG, "Event ${updatedEvent.id} updated successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "updateEvent error: ${e.message}", e)
            false
        }
    }

    suspend fun updateDiscordThreadId(eventId: String, threadId: String?): Boolean {
        return try {
            eventsCollection.document(eventId).update("discordThreadId", threadId).await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "updateDiscordThreadId error: ${e.message}", e)
            false
        }
    }

    suspend fun getById(eventId: String): Event? {
        return try {
            if (eventId.isBlank()) return null
            val doc = eventsCollection.document(eventId).get().await()
            if (!doc.exists()) return null
            doc.toEvent()
        } catch (e: Exception) {
            Log.e(TAG, "getById error: ${e.message}", e)
            null
        }
    }

    suspend fun getEventsByCityFromNow(cityId: String): List<Event> {
        return try {
            val now = Timestamp.now()
            val qs = eventsCollection
                .whereEqualTo("cityId", cityId)
                .whereGreaterThanOrEqualTo("dateTime", now)
                .orderBy("dateTime", Query.Direction.ASCENDING)
                .get()
                .await()

            qs.documents.mapNotNull { it.toEventOrNull() }
        } catch (e: Exception) {
            Log.e(TAG, "getEventsByCityFromNow error: ${e.message}", e)
            emptyList()
        }
    }

    data class PagedResult<T>(
        val items: List<T>,
        val lastDoc: DocumentSnapshot?
    )

    suspend fun getCityEventsFromNowPaged(
        cityId: String,
        pageSize: Long,
        lastDoc: DocumentSnapshot? = null,
        excludeHostId: String = ""
    ): PagedResult<Event> {
        return try {
            val now = Timestamp.now()

            var q: Query = eventsCollection
                .whereEqualTo("cityId", cityId)
                .whereGreaterThanOrEqualTo("dateTime", now)
                .orderBy("dateTime", Query.Direction.ASCENDING)
                .limit(pageSize)

            if (lastDoc != null) q = q.startAfter(lastDoc)

            val snap = q.get().await()
            // Filter out events where the logged-in user is the host (for "All Events" tab)
            val events = snap.documents
                .mapNotNull { it.toEventOrNull() }
                .filter { excludeHostId.isBlank() || it.hostId != excludeHostId }
            val cursor = snap.documents.lastOrNull()

            PagedResult(events, cursor)
        } catch (e: Exception) {
            Log.e(TAG, "getCityEventsFromNowPaged error: ${e.message}", e)
            PagedResult(emptyList(), null)
        }
    }

    /**
     * ✅ FIX: više ne filtriramo cityId u samom query-ju (to često traži composite index),
     * nego filtriramo u kodu posle fetch-a.
     *
     * I dalje ćeš želeti index za hostId+dateTime u nekim slučajevima,
     * ali ovo uklanja najproblematičniji cityId+hostId+dateTime kombinovani query.
     */
    suspend fun getHostingEventsFromNowPaged(
        hostId: String,
        cityId: String,
        pageSize: Long,
        lastDoc: DocumentSnapshot? = null
    ): PagedResult<Event> {
        return try {
            val now = Timestamp.now()

            var q: Query = eventsCollection
                .whereEqualTo("hostId", hostId)
                .whereGreaterThanOrEqualTo("dateTime", now)
                .orderBy("dateTime", Query.Direction.ASCENDING)
                .limit(pageSize)

            if (lastDoc != null) q = q.startAfter(lastDoc)

            val snap = q.get().await()

            // filter by cityId in code
            val events = snap.documents
                .mapNotNull { it.toEventOrNull() }
                .filter { it.cityId == cityId }

            val cursor = snap.documents.lastOrNull()
            PagedResult(events, cursor)
        } catch (e: Exception) {
            Log.e(TAG, "getHostingEventsFromNowPaged error: ${e.message}", e)
            PagedResult(emptyList(), null)
        }
    }

    // -----------------------------
    // Applications (subcollection)
    // path: /events/{eventId}/applications/{applicationId}
    // -----------------------------

    private fun appsCol(eventId: String) =
        eventsCollection.document(eventId).collection("applications")

    suspend fun getPendingApplications(eventId: String): List<EventApplication> {
        return try {
            Log.d(TAG, "getPendingApplications: fetching for eventId=$eventId")
            // Simplified query without orderBy to avoid needing composite index
            val qs = appsCol(eventId)
                .whereEqualTo("status", "PENDING")
                .get()
                .await()

            val apps = qs.documents.mapNotNull { it.toApplicationOrNull() }
            Log.d(TAG, "getPendingApplications: found ${apps.size} pending applications")
            // Sort in code instead of Firestore
            apps.sortedBy { it.createdAt.toDate().time }
        } catch (e: Exception) {
            Log.e(TAG, "getPendingApplications error: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun getMyActiveApplication(eventId: String, userId: String): EventApplication? {
        return try {
            // Fetch all applications where user is a member
            val qs = appsCol(eventId)
                .whereArrayContains("memberUserIds", userId)
                .get()
                .await()

            // Filter in code to find PENDING or ACCEPTED
            qs.documents
                .mapNotNull { it.toApplicationOrNull() }
                .firstOrNull { it.status == "PENDING" || it.status == "ACCEPTED" }
        } catch (e: Exception) {
            Log.e(TAG, "getMyActiveApplication error: ${e.message}", e)
            null
        }
    }

    suspend fun getApplicationById(eventId: String, applicationId: String): EventApplication? {
        return try {
            val doc = appsCol(eventId).document(applicationId).get().await()
            if (!doc.exists()) return null
            doc.toApplicationOrNull()
        } catch (e: Exception) {
            Log.e(TAG, "getApplicationById error: ${e.message}", e)
            null
        }
    }

    suspend fun getAcceptedApplications(eventId: String): List<EventApplication> {
        return try {
            val qs = appsCol(eventId)
                .whereEqualTo("status", "ACCEPTED")
                .get()
                .await()
            qs.documents.mapNotNull { it.toApplicationOrNull() }
        } catch (e: Exception) {
            Log.e(TAG, "getAcceptedApplications error: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun getAcceptedPlayersCount(eventId: String, acceptedApplicationIds: List<String>): Int {
        val ids = acceptedApplicationIds.distinct()
        if (ids.isEmpty()) return 0

        return try {
            val uniqueUsers = mutableSetOf<String>()

            // Firestore whereIn limit = 10
            ids.chunked(10).forEach { chunk ->
                val qs = appsCol(eventId)
                    .whereIn(FieldPath.documentId(), chunk)
                    .get()
                    .await()

                qs.documents.forEach { snap ->
                    val members =
                        (snap.get("memberUserIds") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
                    uniqueUsers.addAll(members)
                }
            }

            uniqueUsers.size
        } catch (e: Exception) {
            Log.e(TAG, "getAcceptedPlayersCount error: ${e.message}", e)
            0
        }
    }

    /**
     * Fetch all upcoming OPEN events (no city filter), used for search
     */
    suspend fun searchUpcomingEvents(limit: Long = 100): List<Event> {
        return try {
            val now = Timestamp.now()
            val snap = eventsCollection
                .whereGreaterThanOrEqualTo("dateTime", now)
                .orderBy("dateTime", Query.Direction.ASCENDING)
                .limit(limit)
                .get()
                .await()
            snap.documents.mapNotNull { it.toEventOrNull() }
                .filter { it.status == "OPEN" }
        } catch (e: Exception) {
            Log.e(TAG, "searchUpcomingEvents error: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Get IDs of pending applications for an event (used for unseen badge tracking)
     */
    suspend fun getPendingApplicationIds(eventId: String): List<String> {
        return try {
            val qs = appsCol(eventId)
                .whereEqualTo("status", "PENDING")
                .get()
                .await()
            qs.documents.map { it.id }
        } catch (e: Exception) {
            Log.e(TAG, "getPendingApplicationIds error: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun applyToEvent(
        eventId: String,
        createdByUserId: String,
        memberUserIds: List<String>
    ): String? {
        val cleanMembers = memberUserIds.distinct().filter { it.isNotBlank() }
        if (cleanMembers.isEmpty()) return null

        val eventRef = eventsCollection.document(eventId)
        val appsRef = appsCol(eventId).document() // new id

        return try {
            firestore.runTransaction { tx ->
                val eventSnap = tx.get(eventRef)
                if (!eventSnap.exists()) throw IllegalStateException("Event not found")

                val status = eventSnap.getString("status") ?: "OPEN"
                if (status != "OPEN") throw IllegalStateException("Event is not open")

                // Check if host is trying to apply (directly or as part of a group)
                val hostId = eventSnap.getString("hostId") ?: ""
                if (cleanMembers.contains(hostId)) {
                    throw IllegalStateException("Host cannot apply to their own event")
                }

                val maxPlayers = (eventSnap.getLong("maxPlayers") ?: 1L).toInt()
                val acceptedAppIds = (eventSnap.get("acceptedApplicationIds") as? List<String>) ?: emptyList()

                // capacity check (read accepted apps)
                var acceptedPlayers = 0
                acceptedAppIds.forEach { appId ->
                    val appSnap = tx.get(appsCol(eventId).document(appId))
                    val members =
                        (appSnap.get("memberUserIds") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
                    acceptedPlayers += members.size
                    if (members.any { cleanMembers.contains(it) }) {
                        throw IllegalStateException("Someone already accepted")
                    }
                }

                val free = maxPlayers - acceptedPlayers
                if (cleanMembers.size > free) throw IllegalStateException("Not enough spots")

                val type = if (cleanMembers.size == 1) "SINGLE" else "GROUP"
                val appData = hashMapOf(
                    "type" to type,
                    "createdByUserId" to createdByUserId,
                    "memberUserIds" to cleanMembers,
                    "status" to "PENDING",
                    "createdAt" to Timestamp.now()
                )

                tx.set(appsRef, appData)
                null
            }.await()

            appsRef.id
        } catch (e: Exception) {
            Log.e(TAG, "applyToEvent error: ${e.message}", e)
            null
        }
    }

    suspend fun acceptApplication(eventId: String, applicationId: String): Boolean {
        val eventRef = eventsCollection.document(eventId)
        val appRef = appsCol(eventId).document(applicationId)

        return try {
            firestore.runTransaction { tx ->
                val eventSnap = tx.get(eventRef)
                val appSnap = tx.get(appRef)

                if (!eventSnap.exists() || !appSnap.exists()) throw IllegalStateException("Missing docs")

                val eventStatus = eventSnap.getString("status") ?: "OPEN"
                if (eventStatus != "OPEN") throw IllegalStateException("Event not open")

                val appStatus = appSnap.getString("status") ?: "PENDING"
                if (appStatus != "PENDING") throw IllegalStateException("Not pending")

                val maxPlayers = (eventSnap.getLong("maxPlayers") ?: 1L).toInt()
                val acceptedAppIds = (eventSnap.get("acceptedApplicationIds") as? List<String>) ?: emptyList()

                val appMembers =
                    (appSnap.get("memberUserIds") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()

                var acceptedPlayers = 0
                acceptedAppIds.forEach { id ->
                    val s = tx.get(appsCol(eventId).document(id))
                    val members =
                        (s.get("memberUserIds") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
                    acceptedPlayers += members.size
                }

                val free = maxPlayers - acceptedPlayers
                if (appMembers.size > free) throw IllegalStateException("No capacity")

                tx.update(appRef, "status", "ACCEPTED")
                tx.update(eventRef, "acceptedApplicationIds", FieldValue.arrayUnion(applicationId))
            }.await()


            true
        } catch (e: Exception) {
            Log.e(TAG, "acceptApplication error: ${e.message}", e)
            false
        }
    }

    suspend fun declineApplication(eventId: String, applicationId: String): Boolean {
        return try {
            // Get application data before declining for notification
            val application = getApplicationById(eventId, applicationId)
            val event = getById(eventId)

            appsCol(eventId).document(applicationId)
                .update("status", "DECLINED")
                .await()


            true
        } catch (e: Exception) {
            Log.e(TAG, "declineApplication error: ${e.message}", e)
            false
        }
    }

    suspend fun cancelApplication(eventId: String, applicationId: String): Boolean {
        val eventRef = eventsCollection.document(eventId)
        val appRef = appsCol(eventId).document(applicationId)

        // Get data before cancelling for notification
        val application = getApplicationById(eventId, applicationId)
        val event = getById(eventId)
        val wasAccepted = application?.status == "ACCEPTED"

        return try {
            firestore.runTransaction { tx ->
                val eventSnap = tx.get(eventRef)
                val appSnap = tx.get(appRef)
                if (!eventSnap.exists() || !appSnap.exists()) throw IllegalStateException("Missing docs")

                val acceptedAppIds = (eventSnap.get("acceptedApplicationIds") as? List<String>) ?: emptyList()
                val appStatus = appSnap.getString("status") ?: "PENDING"

                tx.update(appRef, "status", "CANCELLED")

                if (appStatus == "ACCEPTED" && acceptedAppIds.contains(applicationId)) {
                    tx.update(eventRef, "acceptedApplicationIds", FieldValue.arrayRemove(applicationId))
                }
            }.await()


            true
        } catch (e: Exception) {
            Log.e(TAG, "cancelApplication error: ${e.message}", e)
            false
        }
    }

    // -----------------------------
    // Firestore mappers
    // -----------------------------

    private fun DocumentSnapshot.toEventOrNull(): Event? {
        return try {
            toEvent()
        } catch (e: Exception) {
            Log.e(TAG, "parse Event $id error: ${e.message}", e)
            null
        }
    }

    private fun DocumentSnapshot.toEvent(): Event {
        return Event(
            id = id,
            gameIds = (get("gameIds") as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
            cityId = getString("cityId") ?: "",
            district = getString("district"),
            location = getString("location") ?: "",
            latitude = getDouble("latitude"),
            longitude = getDouble("longitude"),
            dateTime = getTimestamp("dateTime") ?: Timestamp.now(),
            players = getString("players") ?: "",
            hostId = getString("hostId") ?: "",
            createdAt = getTimestamp("createdAt") ?: Timestamp.now(),
            status = getString("status") ?: "OPEN",
            minPlayers = (getLong("minPlayers") ?: 1L).toInt(),
            maxPlayers = (getLong("maxPlayers") ?: 1L).toInt(),
            acceptedApplicationIds =
                (get("acceptedApplicationIds") as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
            discordThreadId = getString("discordThreadId")
        )
    }

    private fun DocumentSnapshot.toApplicationOrNull(): EventApplication? {
        return try {
            EventApplication(
                id = id,
                type = getString("type") ?: "SINGLE",
                createdByUserId = getString("createdByUserId") ?: "",
                memberUserIds = (get("memberUserIds") as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                status = getString("status") ?: "PENDING",
                createdAt = getTimestamp("createdAt") ?: Timestamp.now()
            )
        } catch (e: Exception) {
            Log.e(TAG, "parse Application $id error: ${e.message}", e)
            null
        }
    }

    // -----------------------------
    // Cleanup functions
    // -----------------------------

    /**
     * Delete all cancelled events from the database
     */
    suspend fun deleteCancelledEvents(): Int {
        return try {
            val snap = eventsCollection
                .whereEqualTo("status", "CANCELLED")
                .get()
                .await()

            var deletedCount = 0
            snap.documents.forEach { doc ->
                doc.reference.delete().await()
                deletedCount++
            }
            Log.d(TAG, "Deleted $deletedCount cancelled events")
            deletedCount
        } catch (e: Exception) {
            Log.e(TAG, "deleteCancelledEvents error: ${e.message}", e)
            0
        }
    }

    /**
     * Delete all events that have already passed (dateTime < now)
     * Note: Discord thread is preserved as per requirement
     */
    suspend fun deletePastEvents(): Int {
        return try {
            val now = Timestamp.now()
            val snap = eventsCollection
                .whereLessThan("dateTime", now)
                .get()
                .await()

            var deletedCount = 0
            snap.documents.forEach { doc ->
                doc.reference.delete().await()
                deletedCount++
            }
            Log.d(TAG, "Deleted $deletedCount past events")
            deletedCount
        } catch (e: Exception) {
            Log.e(TAG, "deletePastEvents error: ${e.message}", e)
            0
        }
    }

    /**
     * Run full cleanup: delete cancelled and past events
     */
    suspend fun cleanupEvents(): Pair<Int, Int> {
        val cancelledCount = deleteCancelledEvents()
        val pastCount = deletePastEvents()
        return Pair(cancelledCount, pastCount)
    }

    /**
     * Get active events count grouped by city for a specific game
     * Returns Map<cityId, count> of OPEN events from now onwards that contain the game
     */
    suspend fun getActiveEventsCountByGameAndCity(gameId: String): Map<String, Int> {
        return try {
            Log.d(TAG, "getActiveEventsCountByGameAndCity called for game: $gameId")

            val snap = eventsCollection
                .whereEqualTo("status", "OPEN")
                .get()
                .await()

            Log.d(TAG, "Found ${snap.documents.size} OPEN events total")

            val allEvents = snap.documents.mapNotNull { it.toEventOrNull() }
            Log.d(TAG, "Parsed ${allEvents.size} events successfully")

            val filteredByGame = allEvents.filter { it.gameIds.contains(gameId) }
            Log.d(TAG, "Found ${filteredByGame.size} events with game $gameId")

            val result = filteredByGame.groupingBy { it.cityId }.eachCount()
            Log.d(TAG, "Result map: $result")

            result
        } catch (e: Exception) {
            Log.e(TAG, "getActiveEventsCountByGameAndCity error: ${e.message}", e)
            e.printStackTrace()
            emptyMap()
        }
    }
}
