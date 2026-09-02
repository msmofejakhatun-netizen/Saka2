package com.example.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.example.BuildConfig
import com.example.data.firebase.FirebaseManager
import com.example.update.AppUpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class AppRemoteConfig(
    val latestVersionCode: Int = BuildConfig.VERSION_CODE,
    val latestVersionName: String = "1.0.0",
    val isForceUpdate: Boolean = false,
    val minSupportedVersionCode: Int = 1,
    val updateTitle: String = "New Update Available 🚀",
    val updateMessage: String = "A new version of Smart POS is available with enhanced billing, GST reporting, and performance improvements.",
    val releaseNotes: List<String> = emptyList(),
    val playStoreUrl: String = "https://play.google.com/store/apps/details?id=" + BuildConfig.APPLICATION_ID,
    val fileSizeMb: String = "14.2 MB",
    val maintenanceMode: Boolean = false,
    val supportWhatsapp: String = "+919876543210"
)

object RemoteConfigService {

    private const val TAG = "RemoteConfigService"
    private const val CONFIG_COLLECTION = "config"
    private const val APP_SETTINGS_DOC = "app_settings"
    private const val APP_UPDATE_DOC = "app_update"

    /**
     * Fetches remote configuration from Firestore doc `config/app_settings`.
     * If not found, falls back gracefully to `config/app_update` or offline defaults.
     */
    suspend fun fetchRemoteConfig(): AppRemoteConfig = withContext(Dispatchers.IO) {
        if (!FirebaseManager.isFirebaseAvailable) {
            Log.d(TAG, "Firebase unavailable, returning default config")
            return@withContext AppRemoteConfig()
        }

        try {
            val firestore = FirebaseManager.firestore ?: return@withContext AppRemoteConfig()

            // Primary source: config/app_settings as requested
            var doc = firestore.collection(CONFIG_COLLECTION).document(APP_SETTINGS_DOC).get().await()

            // Fallback source: config/app_update if app_settings is empty
            if (!doc.exists()) {
                doc = firestore.collection(CONFIG_COLLECTION).document(APP_UPDATE_DOC).get().await()
            }

            if (doc.exists()) {
                val latestCode = (doc.getLong("latest_version_code")
                    ?: doc.getLong("latestVersionCode")
                    ?: BuildConfig.VERSION_CODE.toLong()).toInt()

                val latestName = doc.getString("latest_version_name")
                    ?: doc.getString("latestVersionName")
                    ?: "1.0.0"

                val isForce = doc.getBoolean("is_force_update")
                    ?: doc.getBoolean("isForceUpdate")
                    ?: false

                val minSupported = (doc.getLong("min_supported_version_code")
                    ?: doc.getLong("minSupportedVersionCode")
                    ?: 1L).toInt()

                val title = doc.getString("update_title")
                    ?: doc.getString("title")
                    ?: (if (isForce) "Mandatory App Update" else "New Version Available 🚀")

                val message = doc.getString("update_message")
                    ?: doc.getString("message")
                    ?: "We've added great new features to enhance your Smart POS experience."

                val notesRaw = doc.get("release_notes") ?: doc.get("releaseNotes")
                val releaseNotes = when (notesRaw) {
                    is List<*> -> notesRaw.mapNotNull { it?.toString() }
                    is String -> notesRaw.split("\n").filter { it.isNotBlank() }
                    else -> listOf(
                        "Loose Tablet & Strip Billing for Pharmacies 💊",
                        "Razorpay & PhonePe UPI Autopay Gateway Integration 💳",
                        "GST B2B Billing & HSN Code Search Engine 🧾",
                        "OneSignal Instant Push Notifications & Deep Linking 🔔",
                        "Performance Enhancements & Bug Fixes ⚡"
                    )
                }

                val playStoreUrl = doc.getString("play_store_url")
                    ?: doc.getString("download_url")
                    ?: doc.getString("downloadUrl")
                    ?: ("https://play.google.com/store/apps/details?id=" + BuildConfig.APPLICATION_ID)

                val fileSize = doc.getString("file_size_mb")
                    ?: doc.getString("fileSizeMb")
                    ?: "14.2 MB"

                val maintenance = doc.getBoolean("maintenance_mode") ?: false
                val whatsapp = doc.getString("support_whatsapp") ?: "+919876543210"

                Log.d(TAG, "Fetched remote config: latestCode=$latestCode, force=$isForce")

                return@withContext AppRemoteConfig(
                    latestVersionCode = latestCode,
                    latestVersionName = latestName,
                    isForceUpdate = isForce,
                    minSupportedVersionCode = minSupported,
                    updateTitle = title,
                    updateMessage = message,
                    releaseNotes = releaseNotes,
                    playStoreUrl = playStoreUrl,
                    fileSizeMb = fileSize,
                    maintenanceMode = maintenance,
                    supportWhatsapp = whatsapp
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch remote config from Firestore: ${e.localizedMessage}")
        }

        return@withContext AppRemoteConfig()
    }

    /**
     * Checks if an update is available comparing against current BuildConfig.VERSION_CODE.
     */
    suspend fun checkForAppUpdate(currentVersionCode: Int = BuildConfig.VERSION_CODE): AppUpdateInfo = withContext(Dispatchers.IO) {
        val config = fetchRemoteConfig()

        val isAvailable = config.latestVersionCode > currentVersionCode || currentVersionCode < config.minSupportedVersionCode
        val isForce = config.isForceUpdate || currentVersionCode < config.minSupportedVersionCode

        return@withContext AppUpdateInfo(
            isUpdateAvailable = isAvailable,
            latestVersionCode = config.latestVersionCode,
            latestVersionName = config.latestVersionName,
            currentVersionCode = currentVersionCode,
            currentVersionName = BuildConfig.VERSION_NAME,
            isForceUpdate = isForce,
            releaseNotes = config.releaseNotes,
            downloadUrl = config.playStoreUrl,
            fileSizeMb = config.fileSizeMb,
            minSupportedVersionCode = config.minSupportedVersionCode
        )
    }

    /**
     * Opens the Google Play Store or web link via Android Intent.
     */
    fun openPlayStore(context: Context, customUrl: String? = null) {
        val packageName = context.packageName
        try {
            val playStoreIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(playStoreIntent)
        } catch (e: Exception) {
            try {
                val fallbackUrl = customUrl?.takeIf { it.isNotBlank() }
                    ?: "https://play.google.com/store/apps/details?id=$packageName"
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(fallbackUrl)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(webIntent)
            } catch (ex: Exception) {
                Log.e(TAG, "Cannot launch store or browser: ${ex.localizedMessage}")
            }
        }
    }
}
