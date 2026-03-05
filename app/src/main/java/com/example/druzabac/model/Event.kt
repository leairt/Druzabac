package com.example.druzabac.model

import com.google.firebase.Timestamp

/**
 * Event document: /events/{eventId}
 *
 * - acceptedApplicationIds: list of accepted application doc IDs (subcollection)
 * - accepted users are derived by summing memberUserIds sizes from accepted applications
 */
data class Event(
    val id: String = "",
    val gameIds: List<String> = emptyList(),
    val cityId: String = "",
    val district: String? = null,
    val location: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val dateTime: Timestamp = Timestamp.now(),
    val players: String = "",
    val hostId: String = "",

    val createdAt: Timestamp = Timestamp.now(),
    val status: String = "OPEN", // OPEN | FINALIZED | CANCELLED

    val minPlayers: Int = 1,
    val maxPlayers: Int = 1,

    val acceptedApplicationIds: List<String> = emptyList(),

    val discordThreadId: String? = null // Discord thread ID for this event
)
