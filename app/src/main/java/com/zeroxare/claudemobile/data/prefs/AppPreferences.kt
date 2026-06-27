package com.zeroxare.claudemobile.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "claude_mobile_prefs")

class AppPreferences(private val context: Context) {

    companion object {
        private val API_KEY = stringPreferencesKey("anthropic_api_key")
        private val FONT_SIZE = stringPreferencesKey("terminal_font_size")
        private val MODEL = stringPreferencesKey("claude_model")
    }

    val apiKey: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[API_KEY] ?: ""
    }

    val fontSize: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[FONT_SIZE] ?: "14"
    }

    val model: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[MODEL] ?: "claude-sonnet-4-6"
    }

    suspend fun setApiKey(key: String) {
        context.dataStore.edit { prefs ->
            prefs[API_KEY] = key
        }
    }

    suspend fun setFontSize(size: String) {
        context.dataStore.edit { prefs ->
            prefs[FONT_SIZE] = size
        }
    }

    suspend fun setModel(model: String) {
        context.dataStore.edit { prefs ->
            prefs[MODEL] = model
        }
    }
}
