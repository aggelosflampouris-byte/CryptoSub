package com.privatemessenger.data.remote

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.privatemessenger.data.remote.api.MessengerApi
import com.privatemessenger.data.remote.websocket.WebSocketManager
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Provides access to the REST API and WebSocket manager.
 */
class ApiClient(private val context: Context, private val baseUrl: String) {

    companion object {
        private const val PREFS_FILE = "pm_auth_prefs"
        private const val PREF_SESSION_TOKEN = "session_token"
    }

    // We use standard SharedPreferences for the session token.
    // The session token is an opaque random string validated by the server.
    // Encrypting it on the client side provides minimal security benefit
    // (an attacker with root can just extract it anyway) while causing
    // fatal crashes on many devices due to Android Crypto bugs.
    private val authPrefs by lazy {
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
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
