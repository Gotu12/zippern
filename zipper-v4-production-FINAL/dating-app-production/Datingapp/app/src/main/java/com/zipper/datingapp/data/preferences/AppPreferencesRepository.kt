package com.zipper.datingapp.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.zipper.datingapp.data.AppLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeModePreference {
    LIGHT,
    DARK,
    SYSTEM
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "zipper_app_prefs")

class AppPreferencesRepository(context: Context) {
    private val dataStore = context.applicationContext.dataStore

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val APP_LANGUAGE = stringPreferencesKey("app_language")
        /** One-time primer: camera, mic, coarse location, POST_NOTIFICATIONS (API 33+). */
        val RUNTIME_PERMISSIONS_ONBOARDING_COMPLETE = booleanPreferencesKey("runtime_permissions_onboarding_complete")
    }

    val themeMode: Flow<ThemeModePreference> = dataStore.data.map { prefs ->
        when (prefs[Keys.THEME_MODE]) {
            "light" -> ThemeModePreference.LIGHT
            "dark" -> ThemeModePreference.DARK
            "system" -> ThemeModePreference.SYSTEM
            else -> ThemeModePreference.DARK
        }
    }

    val appLanguageCode: Flow<String> = dataStore.data.map { prefs ->
        prefs[Keys.APP_LANGUAGE] ?: AppLanguage.ENGLISH.code
    }

    val runtimePermissionsOnboardingComplete: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.RUNTIME_PERMISSIONS_ONBOARDING_COMPLETE] == true
    }

    suspend fun setThemeMode(mode: ThemeModePreference) {
        dataStore.edit { prefs ->
            prefs[Keys.THEME_MODE] = when (mode) {
                ThemeModePreference.LIGHT -> "light"
                ThemeModePreference.DARK -> "dark"
                ThemeModePreference.SYSTEM -> "system"
            }
        }
    }

    suspend fun setLanguageCode(code: String) {
        dataStore.edit { prefs ->
            prefs[Keys.APP_LANGUAGE] = code
        }
    }

    suspend fun setRuntimePermissionsOnboardingComplete(complete: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.RUNTIME_PERMISSIONS_ONBOARDING_COMPLETE] = complete
        }
    }
}
