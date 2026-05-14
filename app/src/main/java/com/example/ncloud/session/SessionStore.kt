package com.example.ncloud.session

import android.content.Context
import com.example.ncloud.models.AuthSession

class SessionStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("ncloud", Context.MODE_PRIVATE)

    fun loadBaseUrl(): String {
        return prefs.getString("base_url", "http://10.0.2.2:8080")
            ?: "http://10.0.2.2:8080"
    }

    fun saveBaseUrl(baseUrl: String) {
        prefs.edit()
            .putString("base_url", baseUrl)
            .apply()
    }

    fun load(): AuthSession? {
        val username = prefs.getString("username", null) ?: return null
        val accessToken = prefs.getString("access_token", null) ?: return null
        val refreshToken = prefs.getString("refresh_token", null) ?: return null
        val trashAccessKey = prefs.getString("trash_access_key", null) ?: ""

        return AuthSession(
            username = username,
            accessToken = accessToken,
            refreshToken = refreshToken,
            trashAccessKey = trashAccessKey
        )
    }

    fun save(session: AuthSession) {
        prefs.edit()
            .putString("username", session.username)
            .putString("access_token", session.accessToken)
            .putString("refresh_token", session.refreshToken)
            .putString("trash_access_key", session.trashAccessKey)
            .apply()
    }

    fun clear() {
        prefs.edit()
            .remove("username")
            .remove("access_token")
            .remove("refresh_token")
            .remove("trash_access_key")
            .apply()
    }
}
