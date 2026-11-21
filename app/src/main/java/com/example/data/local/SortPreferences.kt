package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ui.viewmodel.SortMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.sortDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "narayani_sort_preferences"
)

class SortPreferences(context: Context) {
    private val appContext = context.applicationContext ?: context

    val sortModeFlow: Flow<SortMode> = appContext.sortDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            parseSortMode(preferences[SORT_MODE_KEY])
        }

    suspend fun saveSortMode(mode: SortMode) {
        appContext.sortDataStore.edit { preferences ->
            preferences[SORT_MODE_KEY] = mode.name
        }
    }

    companion object {
        private val SORT_MODE_KEY = stringPreferencesKey("document_sort_mode")

        fun parseSortMode(raw: String?): SortMode {
            return SortMode.entries.firstOrNull { it.name == raw } ?: SortMode.DATE_DESC
        }
    }
}
