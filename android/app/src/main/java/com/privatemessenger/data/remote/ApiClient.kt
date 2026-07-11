package com.privatemessenger.data.remote

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.privatemessenger.data.remote.api.MessengerApi
import com.privatemessenger.data.remote.websocket.WebSocketManager
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Provides access to the REST API and WebSocket manager.
 * In a larger app, this would be managed by a DI framework like Hilt.
 */
class ApiClient(private val context: Context, private val baseUrl: String) {

    companion object {
        private const val PREFS_FILE = "pm_auth_prefs"
        private const val PREF_SESSION_TOKEN = "session_token"
    }

    private val authPrefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private val authInterceptor = Interceptor { chain ->
        val token = getSessionToken()
        val request = chain.request().newBuilder().apply {
            if (token != null) {
                addHeader("Authorization", "Bearer $token")
            }
        }.build()
        chain.proceed(request)
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .build()

    private val gson: Gson = GsonBuilder().create()

    private val retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()

    val api: MessengerApi = retrofit.create(MessengerApi::class.java)

    val webSocketManager = WebSocketManager(okHttpClient, gson, baseUrl)

    // --- Auth Token Management ---

    fun saveSessionToken(token: String) {
        authPrefs.edit().putString(PREF_SESSION_TOKEN, token).apply()
    }

    fun getSessionToken(): String? {
        return authPrefs.getString(PREF_SESSION_TOKEN, null)
    }

    fun clearSessionToken() {
        authPrefs.edit().remove(PREF_SESSION_TOKEN).apply()
    }
}
