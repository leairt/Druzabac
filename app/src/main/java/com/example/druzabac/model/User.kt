package com.example.druzabac.model

import com.google.firebase.Timestamp

data class User(
    val id: String,
    val email: String,
    val displayName: String,
    val givenName: String,
    val familyName: String,
    val photoUrl: String,
    val username: String = "",
    val birthday: Timestamp? = null,
    val about: String = "",
    val ownedGameIds: List<String> = emptyList(),
    val friendsIds: List<String> = emptyList(),
    val incomingFriendRequests: List<String> = emptyList(),
    val outgoingFriendRequests: List<String> = emptyList(),
    val hostedEventsCount: Int = 0,
    val attendedEventsCount: Int = 0,
    val cityId: String = ""
)