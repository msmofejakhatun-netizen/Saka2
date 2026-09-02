package com.example.data.firebase

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import java.util.Locale

object FirebaseManager {
    private const val TAG = "FirebaseManager"
    
    var isFirebaseAvailable: Boolean = false
        private set

    val auth: FirebaseAuth?
        get() = if (isFirebaseAvailable) FirebaseAuth.getInstance() else null

    val firestore: FirebaseFirestore?
        get() = if (isFirebaseAvailable) FirebaseFirestore.getInstance() else null

    fun initialize(context: Context) {
        try {
            // Attempt to retrieve default FirebaseApp
            val app = FirebaseApp.initializeApp(context)
            if (app != null) {
                isFirebaseAvailable = true
                Log.i(TAG, "Firebase successfully initialized.")
            } else {
                isFirebaseAvailable = false
                Log.w(TAG, "FirebaseApp returned null during initialization.")
            }
        } catch (e: Exception) {
            isFirebaseAvailable = false
            Log.w(TAG, "Firebase initialization failed: ${e.localizedMessage}. Running in Offline Room-fallback mode.")
        }
    }

    // Helper to normalize mobile number/email to a valid email format for Firebase Auth
    fun normalizeToEmail(input: String): String {
        return if (input.contains("@")) {
            input.trim().lowercase(Locale.US)
        } else {
            val digits = input.filter { it.isDigit() }
            if (digits.length >= 7) {
                "phone_$digits@billingapp.com"
            } else {
                "${input.trim().lowercase(Locale.US)}@billingapp.com"
            }
        }
    }
}
