package com.example

import android.app.Application
import android.util.Log
import com.onesignal.OneSignal
import com.onesignal.notifications.INotificationClickEvent
import com.onesignal.notifications.INotificationClickListener
import com.example.ui.navigation.Screen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

class SmartPOSApplication : Application() {

    companion object {
        private const val TAG = "SmartPOSApplication"
        const val ONESIGNAL_APP_ID = "d6171499-98f7-4dbb-af43-c07e7587423d"

        lateinit var instance: SmartPOSApplication
            private set

        private val _deepLinkRoute = MutableStateFlow<String?>(null)
        val deepLinkRoute = _deepLinkRoute.asStateFlow()

        fun setDeepLinkRoute(route: String?) {
            _deepLinkRoute.value = route
        }

        fun consumeDeepLinkRoute(): String? {
            val route = _deepLinkRoute.value
            _deepLinkRoute.value = null
            return route
        }

        fun mapRoute(rawRoute: String?): String {
            if (rawRoute.isNullOrBlank()) return Screen.Dashboard.route
            return when (rawRoute.lowercase().trim()) {
                "subscription", "paywall", "pro", "premium" -> Screen.Paywall.route
                "inventory", "products", "stock" -> Screen.Products.route
                "history", "transactions", "invoices" -> Screen.History.route
                "udhar", "khata", "credit" -> Screen.Udhar.route
                "create_bill", "pos", "billing", "bill" -> Screen.CreateBill.route
                "profile", "profile_setup", "settings" -> Screen.ProfileSetup.route
                "admin", "manage" -> Screen.Admin.route
                "login", "auth" -> Screen.Login.route
                "dashboard", "home" -> Screen.Dashboard.route
                else -> {
                    // If route matches one of the known Screen routes directly
                    when (rawRoute) {
                        Screen.Dashboard.route -> Screen.Dashboard.route
                        Screen.Paywall.route -> Screen.Paywall.route
                        Screen.Products.route -> Screen.Products.route
                        Screen.History.route -> Screen.History.route
                        Screen.Udhar.route -> Screen.Udhar.route
                        Screen.CreateBill.route -> Screen.CreateBill.route
                        Screen.ProfileSetup.route -> Screen.ProfileSetup.route
                        Screen.Admin.route -> Screen.Admin.route
                        Screen.Login.route -> Screen.Login.route
                        else -> Screen.Dashboard.route
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        initOneSignal()
        com.example.worker.TrialTrackerWorker.schedule(this)
    }

    private fun initOneSignal() {
        try {
            // Verbose Logging for Debugging in development
            com.onesignal.debug.LogLevel.VERBOSE
            OneSignal.Debug.logLevel = com.onesignal.debug.LogLevel.VERBOSE

            // Initialize OneSignal with App Context and Application ID
            OneSignal.initWithContext(this, ONESIGNAL_APP_ID)
            Log.d(TAG, "OneSignal initialized successfully with ID: $ONESIGNAL_APP_ID")

            // Attach notification click listener for deep linking
            OneSignal.Notifications.addClickListener(object : INotificationClickListener {
                override fun onClick(event: INotificationClickEvent) {
                    try {
                        val notification = event.notification
                        val additionalData: JSONObject? = notification.additionalData
                        Log.d(TAG, "Notification clicked! Title: ${notification.title}, Data: $additionalData")

                        if (additionalData != null) {
                            val routeKey = when {
                                additionalData.has("screen_route") -> additionalData.optString("screen_route")
                                additionalData.has("route") -> additionalData.optString("route")
                                additionalData.has("target_screen") -> additionalData.optString("target_screen")
                                additionalData.has("screen") -> additionalData.optString("screen")
                                else -> null
                            }

                            if (!routeKey.isNullOrBlank()) {
                                val resolvedRoute = mapRoute(routeKey)
                                Log.d(TAG, "Resolved deep link route: $resolvedRoute (from $routeKey)")
                                _deepLinkRoute.value = resolvedRoute
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error handling notification click: ${e.localizedMessage}")
                    }
                }
            })

            // Prompt notification permission asynchronously on startup
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    OneSignal.Notifications.requestPermission(false)
                } catch (e: Exception) {
                    Log.d(TAG, "OneSignal requestPermission error: ${e.localizedMessage}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize OneSignal: ${e.localizedMessage}")
        }
    }
}
