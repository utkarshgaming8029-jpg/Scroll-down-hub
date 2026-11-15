package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "scrolldown_preferences")

data class AppPreferences(
    val isDarkTheme: Boolean = true,
    val languageCode: String = "en",
    val notifyLikes: Boolean = true,
    val notifyComments: Boolean = true,
    val notifyFollows: Boolean = true,
    val notifyMessages: Boolean = true,
    val notifyAdmin: Boolean = true
)

class SessionDataStore(private val context: Context) {
    private val darkThemeKey = booleanPreferencesKey("dark_theme")
    private val languageKey = stringPreferencesKey("language_code")
    private val notifyLikesKey = booleanPreferencesKey("notify_likes")
    private val notifyCommentsKey = booleanPreferencesKey("notify_comments")
    private val notifyFollowsKey = booleanPreferencesKey("notify_follows")
    private val notifyMessagesKey = booleanPreferencesKey("notify_messages")
    private val notifyAdminKey = booleanPreferencesKey("notify_admin")

    val preferences: Flow<AppPreferences> = context.dataStore.data.map { prefs ->
        AppPreferences(
            isDarkTheme = prefs[darkThemeKey] ?: true,
            languageCode = prefs[languageKey] ?: "en",
            notifyLikes = prefs[notifyLikesKey] ?: true,
            notifyComments = prefs[notifyCommentsKey] ?: true,
            notifyFollows = prefs[notifyFollowsKey] ?: true,
            notifyMessages = prefs[notifyMessagesKey] ?: true,
            notifyAdmin = prefs[notifyAdminKey] ?: true
        )
    }

    suspend fun setDarkTheme(isDark: Boolean) {
        context.dataStore.edit { it[darkThemeKey] = isDark }
    }

    suspend fun setLanguageCode(code: String) {
        context.dataStore.edit { it[languageKey] = code }
    }

    suspend fun setNotificationSettings(
        likes: Boolean? = null,
        comments: Boolean? = null,
        follows: Boolean? = null,
        messages: Boolean? = null,
        admin: Boolean? = null
    ) {
        context.dataStore.edit { prefs ->
            likes?.let { prefs[notifyLikesKey] = it }
            comments?.let { prefs[notifyCommentsKey] = it }
            follows?.let { prefs[notifyFollowsKey] = it }
            messages?.let { prefs[notifyMessagesKey] = it }
            admin?.let { prefs[notifyAdminKey] = it }
        }
    }
}
