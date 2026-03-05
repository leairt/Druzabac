package com.example.druzabac.model

import com.google.firebase.Timestamp

/**
 * Application document: /events/{eventId}/applications/{applicationId}
 */
data class EventApplication(
    val id: String = "",
    val type: String = "SINGLE", // SINGLE | GROUP
    val createdByUserId: String = "",
    val memberUserIds: List<String> = emptyList(),
    val status: String = "PENDING", // PENDING | ACCEPTED | DECLINED | CANCELLED
    val createdAt: Timestamp = Timestamp.now()
)
