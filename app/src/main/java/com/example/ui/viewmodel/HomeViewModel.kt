package com.example.ui.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {

    companion object {
        private const val TAG = "HomeViewModel"
        const val PREFS_NAME = "smart_pos_prefs"
        const val KEY_HAS_SEEN_WELCOME_DIALOG = "has_seen_welcome_dialog"
    }

    private val _showWelcomeDialog = MutableStateFlow(false)
    val showWelcomeDialog: StateFlow<Boolean> = _showWelcomeDialog.asStateFlow()

    private val _hasSeenWelcomeDialog = MutableStateFlow(true)
    val hasSeenWelcomeDialog: StateFlow<Boolean> = _hasSeenWelcomeDialog.asStateFlow()

    /**
     * Checks local preferences for has_seen_welcome_dialog.
     * If not seen (first login after signup), activates the Welcome & 3-Day Trial Dialog.
     */
    fun checkWelcomeStatus(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val hasSeen = prefs.getBoolean(KEY_HAS_SEEN_WELCOME_DIALOG, false)
            _hasSeenWelcomeDialog.value = hasSeen
            _showWelcomeDialog.value = !hasSeen
            Log.d(TAG, "Checked welcome dialog status: hasSeen=$hasSeen, showDialog=${!hasSeen}")
        } catch (e: Exception) {
            Log.e(TAG, "Error checking welcome status: ${e.localizedMessage}")
        }
    }

    /**
     * Dismisses the welcome dialog and persists has_seen_welcome_dialog = true in SharedPreferences.
     */
    fun dismissWelcomeDialog(context: Context, onStartBilling: (() -> Unit)? = null) {
        viewModelScope.launch {
            try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit().putBoolean(KEY_HAS_SEEN_WELCOME_DIALOG, true).apply()
                _hasSeenWelcomeDialog.value = true
                _showWelcomeDialog.value = false
                Log.d(TAG, "Welcome dialog dismissed and marked as seen in preferences")
                onStartBilling?.invoke()
            } catch (e: Exception) {
                Log.e(TAG, "Error dismissing welcome dialog: ${e.localizedMessage}")
                _showWelcomeDialog.value = false
                onStartBilling?.invoke()
            }
        }
    }
}
