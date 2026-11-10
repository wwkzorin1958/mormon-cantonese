package org.kcmcelia.bookofmormon.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.progressDataStore: DataStore<Preferences> by preferencesDataStore(name = "progress")

data class Progress(
    val index: Int? = null,
    val positionMs: Long = 0L,
    val introSeen: Boolean = false,
)

class ProgressStore(private val context: Context) {
    private val keyIndex = intPreferencesKey("index")
    private val keyPosition = longPreferencesKey("position")
    private val keyIntro = booleanPreferencesKey("intro_seen")

    val progress: Flow<Progress> = context.progressDataStore.data.map { prefs ->
        Progress(
            index = prefs[keyIndex],
            positionMs = prefs[keyPosition] ?: 0L,
            introSeen = prefs[keyIntro] ?: false,
        )
    }

    suspend fun save(index: Int, positionMs: Long) {
        context.progressDataStore.edit { prefs ->
            prefs[keyIndex] = index
            prefs[keyPosition] = positionMs.coerceAtLeast(0L)
        }
    }

    suspend fun markIntroSeen() {
        context.progressDataStore.edit { it[keyIntro] = true }
    }
}
