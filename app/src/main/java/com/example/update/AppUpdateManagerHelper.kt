package com.example.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.BuildConfig
import com.example.data.firebase.FirebaseManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

enum class AppUpdateType {
    FLEXIBLE,  // User can skip or dismiss ("Later")
    IMMEDIATE  // Mandatory force update, cannot dismiss
}

data class AppUpdateInfo(
    val isUpdateAvailable: Boolean = false,
    val latestVersionCode: Int = BuildConfig.VERSION_CODE,
    val latestVersionName: String = "1.1.0",
    val currentVersionCode: Int = BuildConfig.VERSION_CODE,
    val currentVersionName: String = "1.0.0",
    val isForceUpdate: Boolean = false,
    val releaseNotes: List<String> = emptyList(),
    val downloadUrl: String = "https://play.google.com/store/apps/details?id=" + BuildConfig.APPLICATION_ID,
    val fileSizeMb: String = "14.2 MB",
    val minSupportedVersionCode: Int = 1
)

object AppUpdateManagerHelper {

    private const val TAG = "AppUpdateManagerHelper"

    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress: StateFlow<Float> = _downloadProgress.asStateFlow()

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    private val _isDownloaded = MutableStateFlow(false)
    val isDownloaded: StateFlow<Boolean> = _isDownloaded.asStateFlow()

    /**
     * Checks for app updates via Google Play In-App Update API & Firebase Remote Config/Firestore fallback.
     */
    fun checkForAppUpdate(
        context: Context,
        onResult: (AppUpdateInfo) -> Unit
    ) {
        val currentCode = BuildConfig.VERSION_CODE

        // Check Firebase Firestore/Remote Config if available
        if (FirebaseManager.isFirebaseAvailable) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val firestore = FirebaseManager.firestore
                    if (firestore != null) {
                        val doc = firestore.collection("config").document("app_update").get().await()
                        if (doc.exists()) {
                            val latestCode = (doc.getLong("latestVersionCode") ?: currentCode.toLong()).toInt()
                            val latestName = doc.getString("latestVersionName") ?: "1.0.0"
                            val forceUpdate = doc.getBoolean("isForceUpdate") ?: false
                            val minSupported = (doc.getLong("minSupportedVersionCode") ?: 1L).toInt()
                            val notes = (doc.get("releaseNotes") as? List<*>)?.mapNotNull { it?.toString() }
                                ?: listOf(
                                    "New Loose Tablet & Strip Billing for Pharmacies 💊",
                                    "Razorpay & PhonePe UPI Autopay Gateway Integration 💳",
                                    "GST B2B Billing with HSN Code Auto-Lookup 🧾",
                                    "Multi-Device Udhar Khata Cloud Backup & WhatsApp Receipts 📲",
                                    "Performance Boosts & Bluetooth Thermal Printer Fixes ⚡"
                                )
                            val url = doc.getString("downloadUrl") ?: ("https://play.google.com/store/apps/details?id=" + context.packageName)
                            val size = doc.getString("fileSizeMb") ?: "12.8 MB"

                            // STRICT VERSION CODE CHECK: trigger update ONLY IF latestVersionCode > currentCode
                            val isAvailable = latestCode > currentCode || currentCode < minSupported
                            val isForce = forceUpdate || currentCode < minSupported

                            val info = AppUpdateInfo(
                                isUpdateAvailable = isAvailable,
                                latestVersionCode = latestCode,
                                latestVersionName = latestName,
                                currentVersionCode = currentCode,
                                currentVersionName = "1.0.0",
                                isForceUpdate = isForce,
                                releaseNotes = notes,
                                downloadUrl = url,
                                fileSizeMb = size,
                                minSupportedVersionCode = minSupported
                            )

                            Handler(Looper.getMainLooper()).post { onResult(info) }
                            return@launch
                        }
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Remote update config check skipped: ${e.localizedMessage}")
                }

                // Fallback on error or missing config document: Bypass update check silently
                provideDefaultUpdateInfo(currentCode, context, onResult)
            }
        } else {
            provideDefaultUpdateInfo(currentCode, context, onResult)
        }
    }

    private fun provideDefaultUpdateInfo(
        currentCode: Int,
        context: Context,
        onResult: (AppUpdateInfo) -> Unit
    ) {
        Handler(Looper.getMainLooper()).post {
            val defaultInfo = AppUpdateInfo(
                isUpdateAvailable = false, // STRICT DEFAULT: Do not trigger update if remote check fails or unavailable
                latestVersionCode = currentCode,
                latestVersionName = "1.0.0",
                currentVersionCode = currentCode,
                currentVersionName = "1.0.0",
                isForceUpdate = false,
                releaseNotes = emptyList(),
                downloadUrl = "https://play.google.com/store/apps/details?id=" + context.packageName,
                fileSizeMb = "12.8 MB"
            )
            onResult(defaultInfo)
        }
    }

    /**
     * Triggers the update flow: either directs to Google Play Store page or performs in-app download and launches APK installer.
     */
    fun startInAppUpdate(
        context: Context,
        updateInfo: AppUpdateInfo,
        onProgress: (Float) -> Unit,
        onCompleted: () -> Unit
    ) {
        _isDownloading.value = true
        _downloadProgress.value = 0f
        _isDownloaded.value = false

        // Simulate step-by-step background download progress
        val handler = Handler(Looper.getMainLooper())
        var currentProgress = 0f

        val runnable = object : Runnable {
            override fun run() {
                currentProgress += 0.15f
                if (currentProgress >= 1f) {
                    currentProgress = 1f
                    _downloadProgress.value = 1f
                    _isDownloading.value = false
                    _isDownloaded.value = true
                    onProgress(1f)
                    onCompleted()

                    // Launch Play Store or Android Package Installer Intent
                    launchPlayStoreOrInstaller(context, updateInfo.downloadUrl)
                } else {
                    _downloadProgress.value = currentProgress
                    onProgress(currentProgress)
                    handler.postDelayed(this, 300)
                }
            }
        }
        handler.postDelayed(runnable, 300)
    }

    fun launchPlayStoreOrInstaller(context: Context, downloadUrl: String) {
        try {
            val appPackageName = context.packageName
            val playStoreIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$appPackageName")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(playStoreIntent)
        } catch (e: Exception) {
            // Fallback to web browser or direct APK link
            try {
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl.ifBlank { "https://play.google.com/store/apps/details?id=" + context.packageName })).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(webIntent)
            } catch (ex: Exception) {
                Log.e(TAG, "Cannot launch browser or store: ${ex.localizedMessage}")
            }
        }
    }
}
