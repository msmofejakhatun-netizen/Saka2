package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast

object WebUtils {
    private const val TAG = "WebUtils"

    const val TERMS_URL = "https://smartpos-ashen.vercel.app/terms"
    const val PRIVACY_URL = "https://smartpos-ashen.vercel.app/privacy"

    /**
     * Opens a web URL safely in the user's default browser or custom tabs.
     */
    fun openWebUrl(context: Context, url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open web URL: $url", e)
            Toast.makeText(context, "Unable to open browser: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }
}
