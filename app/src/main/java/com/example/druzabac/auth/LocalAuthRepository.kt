package com.example.druzabac.auth

import android.content.Context
import android.content.SharedPreferences
import com.example.druzabac.model.User
import com.google.firebase.Timestamp

class LocalAuthRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("druzabac_auth", Context.MODE_PRIVATE)

    val currentUser: User?
        get() {
            val id = prefs.getString("user_id", null) ?: return null
            return User(
                id = id,
                email = prefs.getString("user_email", "") ?: "",
                displayName = prefs.getString("user_display_name", "") ?: "",
                givenName = prefs.getString("user_given_name", "") ?: "",
                familyName = prefs.getString("user_family_name", "") ?: "",
                photoUrl = prefs.getString("user_photo_url", "") ?: "",
                username = prefs.getString("user_username", "") ?: "",
                birthday = prefs.getLong("user_birthday", 0)
                    .takeIf { it != 0L }
                    ?.let { Timestamp(java.util.Date(it)) },
                about = prefs.getString("user_about", "") ?: "",
                cityId = prefs.getString("user_city_id", "") ?: "",
                ownedGameIds = prefs.getStringSet("user_owned_game_ids", emptySet())!!.toList(),
                friendsIds = prefs.getStringSet("user_friends_ids", emptySet())!!.toList(),
                incomingFriendRequests = prefs.getStringSet("user_incoming_friend_requests", emptySet())!!.toList(),
                outgoingFriendRequests = prefs.getStringSet("user_outgoing_friend_requests", emptySet())!!.toList(),
                hostedEventsCount = prefs.getInt("user_hosted_events_count", 0),
                attendedEventsCount = prefs.getInt("user_attended_events_count", 0)
            )
        }

    fun cacheUser(user: User) {
        prefs.edit().apply {
            putString("user_id", user.id)
            putString("user_email", user.email)
            putString("user_display_name", user.displayName)
            putString("user_given_name", user.givenName)
            putString("user_family_name", user.familyName)
            putString("user_photo_url", user.photoUrl)
            putString("user_username", user.username)
            putString("user_city_id", user.cityId)
            putString("user_about", user.about)
            putStringSet("user_owned_game_ids", user.ownedGameIds.toSet())
            putStringSet("user_friends_ids", user.friendsIds.toSet())
            putStringSet("user_incoming_friend_requests", user.incomingFriendRequests.toSet())
            putStringSet("user_outgoing_friend_requests", user.outgoingFriendRequests.toSet())
            putInt("user_hosted_events_count", user.hostedEventsCount)
            putInt("user_attended_events_count", user.attendedEventsCount)
            user.birthday?.let { putLong("user_birthday", it.toDate().time) }
            apply()
        }
    }

    fun signOut() {
        prefs.edit().clear().apply()
    }
}
