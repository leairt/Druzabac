package com.example.druzabac.db

import android.util.Log
import com.example.druzabac.model.User
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import java.util.Locale
import kotlin.random.Random

class UserDatabase {
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val usersCollection = firestore.collection("users")

    companion object {
        private const val TAG = "UserDatabase"
    }

    // ---------- Helpers: map <-> model ----------

    private fun docToUser(documentId: String, data: Map<String, Any?>): User {
        return User(
            id = documentId,
            email = data["email"] as? String ?: "",
            displayName = data["displayName"] as? String ?: "",
            givenName = data["givenName"] as? String ?: "",
            familyName = data["familyName"] as? String ?: "",
            photoUrl = data["photoUrl"] as? String ?: "",
            username = data["username"] as? String ?: "",
            birthday = (data["birthday"] as? com.google.firebase.Timestamp),
            about = data["about"] as? String ?: "",
            cityId = data["cityId"] as? String ?: "",
            ownedGameIds = (data["ownedGameIds"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
            friendsIds = (data["friendsIds"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
            incomingFriendRequests = (data["incomingFriendRequests"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
            outgoingFriendRequests = (data["outgoingFriendRequests"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
            hostedEventsCount = (data["hostedEventsCount"] as? Long)?.toInt() ?: 0,
            attendedEventsCount = (data["attendedEventsCount"] as? Long)?.toInt() ?: 0
        )
    }

    private fun userToMap(user: User): HashMap<String, Any?> {
        return hashMapOf(
            "email" to user.email,
            "displayName" to user.displayName,
            "givenName" to user.givenName,
            "familyName" to user.familyName,
            "photoUrl" to user.photoUrl,
            "username" to user.username,
            "birthday" to user.birthday,
            "about" to user.about,
            "cityId" to user.cityId,
            "ownedGameIds" to user.ownedGameIds,
            "friendsIds" to user.friendsIds,
            "incomingFriendRequests" to user.incomingFriendRequests,
            "outgoingFriendRequests" to user.outgoingFriendRequests,
            "hostedEventsCount" to user.hostedEventsCount,
            "attendedEventsCount" to user.attendedEventsCount
        )
    }

    // ---------- Username generation (unique) ----------

    private fun normalizeUsernameBase(email: String): String {
        val base = email.substringBefore("@").lowercase(Locale.ROOT)
        // dozvoli samo slova, brojeve, _ i .
        val cleaned = base.replace(Regex("[^a-z0-9._]"), "")
        return cleaned.ifBlank { "player" }.take(20)
    }

    private suspend fun usernameExists(username: String): Boolean {
        return try {
            val snap = usersCollection
                .whereEqualTo("username", username)
                .limit(1)
                .get()
                .await()
            !snap.isEmpty
        } catch (e: Exception) {
            // ako Firestore baci izuzetak, ne blokiramo user flow, ali logujemo
            Log.e(TAG, "Error checking username existence: ${e.message}", e)
            false
        }
    }



    private suspend fun generateUniqueUsernameFromEmail(email: String): String {
        val base = normalizeUsernameBase(email)

        if (!usernameExists(base)) return base

        // base1..base30
        for (i in 1..30) {
            val candidate = (base + i.toString()).take(20)
            if (!usernameExists(candidate)) return candidate
        }

        // fallback random
        while (true) {
            val candidate = (base + Random.nextInt(1000, 9999)).take(20)
            if (!usernameExists(candidate)) return candidate
        }
    }

    /**
     * Ako user doc postoji ali username fali/prazan, generise i upise unique username.
     * Vraca finalni username (postojeci ili nov) ili null ako ne moze.
     */
    suspend fun ensureUsername(uid: String, email: String?): String? {
        if (email.isNullOrBlank()) return null

        return try {
            val docRef = usersCollection.document(uid)
            val snap = docRef.get().await()
            if (!snap.exists()) return null

            val current = snap.getString("username")?.trim()
            if (!current.isNullOrBlank()) return current

            val newUsername = generateUniqueUsernameFromEmail(email)
            docRef.update("username", newUsername).await()
            newUsername
        } catch (e: Exception) {
            Log.e(TAG, "Error ensuring username: ${e.message}", e)
            null
        }
    }

    // ---------- Basic reads ----------



    suspend fun getById(userId: String): User? {
        return try {
            val document = usersCollection.document(userId).get().await()
            if (document.exists()) {
                val data = document.data ?: emptyMap()
                docToUser(document.id, data)
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "Error getting user by ID: ${e.message}", e)
            null
        }
    }

    /**
     * Search users by username or display name (case-insensitive prefix search)
     */
    suspend fun searchUsers(query: String, limit: Int = 20): List<User> {
        if (query.isBlank()) return emptyList()

        val searchQuery = query.lowercase().trim()

        return try {
            // Firestore doesn't support case-insensitive search natively,
            // so we fetch more and filter client-side
            val snapshot = usersCollection
                .limit(100)
                .get()
                .await()

            snapshot.documents
                .mapNotNull { doc ->
                    doc.data?.let { docToUser(doc.id, it) }
                }
                .filter { user ->
                    user.username.lowercase().contains(searchQuery) ||
                    user.displayName.lowercase().contains(searchQuery)
                }
                .take(limit)
        } catch (e: Exception) {
            Log.e(TAG, "Error searching users: ${e.message}", e)
            emptyList()
        }
    }

    // ---------- Create / Update ----------


    suspend fun updateUser(user: User): Boolean {
        return try {
            if (user.id.isBlank()) return false
            val userMap = userToMap(user)
            usersCollection.document(user.id).set(userMap, SetOptions.merge()).await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error updating user: ${e.message}", e)
            false
        }
    }

    /**
     * Najbitnije za tvoj flow:
     * - user.id mora biti UID iz Firebase Auth
     * - ako doc ne postoji: kreira ga
     * - ako postoji: osvezi cityId, i ensure-uj username
     */
    suspend fun createUserIfMissing(user: User, cityId: String): User? {
        return try {
            if (user.id.isBlank()) {
                Log.e(TAG, "createUserIfMissing: user.id is blank (must be Firebase UID)")
                return null
            }

            val docRef = usersCollection.document(user.id)
            val snapshot = docRef.get().await()

            if (!snapshot.exists()) {
                val username = if (user.username.isNotBlank()) user.username
                else if (user.email.isNotBlank()) generateUniqueUsernameFromEmail(user.email)
                else ""

                val userMap = userToMap(
                    user.copy(
                        username = username,
                        cityId = cityId,
                        ownedGameIds = user.ownedGameIds.ifEmpty { emptyList() },
                        friendsIds = user.friendsIds.ifEmpty { emptyList() },
                        incomingFriendRequests = user.incomingFriendRequests.ifEmpty { emptyList() },
                        outgoingFriendRequests = user.outgoingFriendRequests.ifEmpty { emptyList() }
                    )
                )
                docRef.set(userMap, SetOptions.merge()).await()
            } else {
                // osvezi cityId
                docRef.set(mapOf("cityId" to cityId), SetOptions.merge()).await()
                // ensure username ako fali
                ensureUsername(user.id, user.email)
            }

            getById(user.id)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating user if missing: ${e.message}", e)
            null
        }
    }

    // ---------- Friend requests / friends ----------

    suspend fun sendFriendRequest(fromUserId: String, toUserId: String) {
        if (fromUserId == toUserId) return

        val fromRef = usersCollection.document(fromUserId)
        val toRef = usersCollection.document(toUserId)

        fromRef.update("outgoingFriendRequests", FieldValue.arrayUnion(toUserId)).await()
        toRef.update("incomingFriendRequests", FieldValue.arrayUnion(fromUserId)).await()

    }

    suspend fun cancelFriendRequest(fromUserId: String, toUserId: String) {
        val fromRef = usersCollection.document(fromUserId)
        val toRef = usersCollection.document(toUserId)

        fromRef.update("outgoingFriendRequests", FieldValue.arrayRemove(toUserId)).await()
        toRef.update("incomingFriendRequests", FieldValue.arrayRemove(fromUserId)).await()
    }

    suspend fun acceptFriendRequest(currentUserId: String, otherUserId: String) {
        val meRef = usersCollection.document(currentUserId)
        val otherRef = usersCollection.document(otherUserId)

        meRef.update("incomingFriendRequests", FieldValue.arrayRemove(otherUserId)).await()
        otherRef.update("outgoingFriendRequests", FieldValue.arrayRemove(currentUserId)).await()

        meRef.update("friendsIds", FieldValue.arrayUnion(otherUserId)).await()
        otherRef.update("friendsIds", FieldValue.arrayUnion(currentUserId)).await()


    }

    suspend fun declineFriendRequest(currentUserId: String, otherUserId: String) {
        val meRef = usersCollection.document(currentUserId)
        val otherRef = usersCollection.document(otherUserId)

        meRef.update("incomingFriendRequests", FieldValue.arrayRemove(otherUserId)).await()
        otherRef.update("outgoingFriendRequests", FieldValue.arrayRemove(currentUserId)).await()


    }

    suspend fun removeFriend(userId: String, friendId: String) {
        val meRef = usersCollection.document(userId)
        val friendRef = usersCollection.document(friendId)

        meRef.update("friendsIds", FieldValue.arrayRemove(friendId)).await()
        friendRef.update("friendsIds", FieldValue.arrayRemove(userId)).await()
    }

    // ---------- Event stats ----------

    /**
     * Increment hostedEventsCount for a user
     */
    suspend fun incrementHostedEventsCount(userId: String): Boolean {
        return try {
            usersCollection.document(userId)
                .update("hostedEventsCount", FieldValue.increment(1))
                .await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error incrementing hostedEventsCount: ${e.message}", e)
            false
        }
    }

    /**
     * Increment attendedEventsCount for multiple users
     */
    suspend fun incrementAttendedEventsCountForUsers(userIds: List<String>): Boolean {
        return try {
            val batch = firestore.batch()
            userIds.forEach { userId ->
                val docRef = usersCollection.document(userId)
                batch.update(docRef, "attendedEventsCount", FieldValue.increment(1))
            }
            batch.commit().await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error batch incrementing attendedEventsCount: ${e.message}", e)
            false
        }
    }
}
