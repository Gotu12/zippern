package com.zipper.datingapp.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.zipper.datingapp.R
import kotlinx.coroutines.tasks.await

class AuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    suspend fun signInAnonymously() = auth.signInAnonymously().await()

    suspend fun signOutAndClearSession(context: Context) {
        auth.signOut()
        revokeGoogleSession(context)
        clearEncryptedSession(context)
    }

    private suspend fun revokeGoogleSession(context: Context) {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        val client = GoogleSignIn.getClient(context.applicationContext, gso)
        runCatching { client.signOut().await() }
        runCatching { client.revokeAccess().await() }
    }

    private fun clearEncryptedSession(context: Context) {
        runCatching {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val securePrefs = EncryptedSharedPreferences.create(
                context,
                SESSION_PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            securePrefs.edit().clear().apply()
        }
        // Best-effort cleanup in case app previously used non-encrypted prefs.
        context.getSharedPreferences(SESSION_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }

    companion object {
        private const val SESSION_PREFS_NAME = "secure_session"
    }
}
