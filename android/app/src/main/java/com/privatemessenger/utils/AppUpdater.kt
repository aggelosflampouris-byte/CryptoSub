package com.privatemessenger.utils

import android.content.Context
import android.content.Intent
import android.widget.Toast
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.gson.Gson
import com.privatemessenger.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

data class GitHubRelease(
    val tag_name: String,
    val assets: List<GitHubAsset>
)

data class GitHubAsset(
    val name: String,
    val browser_download_url: String
)

data class VersionJson(
    val version: String,
    val build: Int
)

object AppUpdater {

    private const val TAG = "AppUpdater"
    private val RELEASE_API  = "https://api.github.com/repos/${BuildConfig.GITHUB_OWNER}/${BuildConfig.GITHUB_REPO}/releases/latest"
    private val VERSION_JSON = "https://github.com/${BuildConfig.GITHUB_OWNER}/${BuildConfig.GITHUB_REPO}/releases/latest/download/version-android.json"
    // Use the static download URL for the APK instead of hitting the API which gets rate-limited
    private val APK_DOWNLOAD_URL = "https://github.com/${BuildConfig.GITHUB_OWNER}/${BuildConfig.GITHUB_REPO}/releases/latest/download/app-debug.apk"
    
    private val client = OkHttpClient()
    private val gson = Gson()

    data class UpdateInfo(
        val isUpdateAvailable: Boolean,
        val latestVersion: String,
        val downloadUrl: String?
    )

    suspend fun checkForUpdate(): UpdateInfo = withContext(Dispatchers.IO) {
        try {
            // 1. Fetch version-android.json published alongside the APK
            val versionRequest = Request.Builder()
                .url(VERSION_JSON)
                .header("Accept", "application/octet-stream")
                .header("Cache-Control", "no-cache")
                .build()

            val versionResponse = client.newCall(versionRequest).execute()
            val remoteVersion: VersionJson? = if (versionResponse.isSuccessful) {
                val body = versionResponse.body?.string() ?: ""
                gson.fromJson(body, VersionJson::class.java)
            } else null

            // 2. Compare build numbers (most reliable for rolling releases)
            val remoteBuild = remoteVersion?.build ?: 0
            val localBuild  = BuildConfig.VERSION_CODE
            val remoteVersionName = remoteVersion?.version ?: "Latest"

            val isUpdateAvailable = remoteBuild > localBuild

            Log.d(TAG, "Local build=$localBuild  Remote build=$remoteBuild  update=$isUpdateAvailable")

            UpdateInfo(
                isUpdateAvailable = isUpdateAvailable,
                latestVersion = remoteVersionName,
                downloadUrl = if (isUpdateAvailable) APK_DOWNLOAD_URL else null
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for updates", e)
            UpdateInfo(false, "", null)
        }
    }

    fun downloadAndInstallUpdate(context: Context, url: String, version: String) {
        try {
            Toast.makeText(context, "Opening browser to download v$version...", Toast.LENGTH_SHORT).show()
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open browser for update", e)
            Toast.makeText(context, "Failed to start download", Toast.LENGTH_SHORT).show()
        }
    }
}
