package com.privatemessenger.utils

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
        val destination = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            "CryptoSub-v$version.apk"
        )
        if (destination.exists()) destination.delete()

        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("CryptoSub Update")
            .setDescription("Downloading v$version…")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationUri(Uri.fromFile(destination))

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = downloadManager.enqueue(request)

        val onComplete = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    installApk(context, destination)
                    context.unregisterReceiver(this)
                }
            }
        }
        ContextCompat.registerReceiver(
            context,
            onComplete,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_EXPORTED
        )
    }

    private fun installApk(context: Context, apkFile: File) {
        if (!apkFile.exists()) return
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start install intent", e)
        }
    }
}
