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
        private const val PREF_USER_ID = "user_id"
        private const val PREF_DEVICE_ID = "device_id"
        private const val PREF_PROFILE_KEY = "profile_key"
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

    private val gson: Gson = GsonBuilder()
        .registerTypeAdapter(ByteArray::class.java, object : com.google.gson.JsonSerializer<ByteArray>, com.google.gson.JsonDeserializer<ByteArray> {
            override fun serialize(src: ByteArray, typeOfSrc: java.lang.reflect.Type, context: com.google.gson.JsonSerializationContext): com.google.gson.JsonElement {
                return com.google.gson.JsonPrimitive(android.util.Base64.encodeToString(src, android.util.Base64.NO_WRAP))
            }
            override fun deserialize(json: com.google.gson.JsonElement, typeOfT: java.lang.reflect.Type, context: com.google.gson.JsonDeserializationContext): ByteArray {
                return android.util.Base64.decode(json.asString, android.util.Base64.NO_WRAP)
            }
        })
        .create()

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

    fun saveUserId(userId: String) {
        authPrefs.edit().putString(PREF_USER_ID, userId).apply()
    }

    fun getUserId(): String? {
        return authPrefs.getString(PREF_USER_ID, null)
    }

    fun saveDeviceId(deviceId: Int) {
        authPrefs.edit().putInt(PREF_DEVICE_ID, deviceId).apply()
    }

    fun getDeviceId(): Int {
        return authPrefs.getInt(PREF_DEVICE_ID, 1) // default to 1
    }

    fun saveProfileKey(keyBase64: String) {
        authPrefs.edit().putString(PREF_PROFILE_KEY, keyBase64).apply()
    }

    fun getProfileKey(): String? {
        return authPrefs.getString(PREF_PROFILE_KEY, null)
    }

    fun clearSessionToken() {
        authPrefs.edit()
            .remove(PREF_SESSION_TOKEN)
            .remove(PREF_USER_ID)
            .remove(PREF_DEVICE_ID)
            .remove(PREF_PROFILE_KEY)
            .apply()
    }
}
