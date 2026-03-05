package com.example.druzabac.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.druzabac.model.User

object UserSession {

    var userInfo: User? by mutableStateOf(null)
        private set

    private var onUserChanged: ((User?) -> Unit)? = null

    fun init(onUserChanged: (User?) -> Unit) {
        this.onUserChanged = onUserChanged
    }

    fun setUser(user: User) {
        userInfo = user
        onUserChanged?.invoke(user)
    }

    fun clear() {
        userInfo = null
        onUserChanged?.invoke(null)
    }
}
