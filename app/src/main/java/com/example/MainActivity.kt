package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material3.*
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.data.db.AppDatabase
import com.example.data.firebase.FirebaseManager
import com.example.data.repository.BillingRepository
import com.example.data.subscription.AppSessionManager
import com.example.data.subscription.PaymentGatewayConfig
import com.example.data.subscription.SessionAccessState
import com.example.ui.navigation.Screen
import com.example.ui.screens.admin.AdminScreen
import com.example.ui.screens.dashboard.DashboardScreen
import com.example.ui.screens.login.LoginScreen
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.EmeraldLight
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.BillingViewModel
import com.example.ui.viewmodel.BillingViewModelFactory
import com.razorpay.Checkout
import com.razorpay.PaymentData
import com.razorpay.PaymentResultWithDataListener
import kotlinx.coroutines.flow.collectLatest

class MainActivity : ComponentActivity(), com.razorpay.PaymentResultWithDataListener {
    private var navControllerRef: androidx.navigation.NavHostController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Handle initial notification intent if app opened from push notification
        handleNotificationIntent(intent)

        // Preload Razorpay Checkout SDK
        try {
            com.razorpay.Checkout.preload(applicationContext)
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Razorpay preload failed: ${e.localizedMessage}")
        }

        // Initialize Firebase
        FirebaseManager.initialize(this)

        // Initialize Subscription Manager
        com.example.data.subscription.SubscriptionManager.init(this)

        // Schedule Background 3-Day Trial Expiry Tracker Worker
        com.example.worker.TrialTrackerWorker.schedule(this)

        // Initialize SQLite Room database & repository locally
        val database = AppDatabase.getDatabase(this)
        val repository = BillingRepository(
            userDao = database.userDao(),
            categoryDao = database.categoryDao(),
            invoiceDao = database.invoiceDao(),
            productDao = database.productDao(),
            customerDao = database.customerDao(),
            customerTransactionDao = database.customerTransactionDao()
        )

        setContent {
            MyApplicationTheme {
                val context = LocalContext.current
                val navController = rememberNavController()
                navControllerRef = navController

                // Instantiate the unified ViewModel using our Factory
                val viewModel: BillingViewModel = viewModel(
                    factory = BillingViewModelFactory(repository)
                )

                // Notification Permission Launcher (Android 13+)
                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    if (isGranted) {
                        android.util.Log.d("MainActivity", "POST_NOTIFICATIONS permission granted")
                    } else {
                        android.util.Log.w("MainActivity", "POST_NOTIFICATIONS permission denied")
                    }
                }

                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                    try {
                        com.onesignal.OneSignal.Notifications.requestPermission(false)
                    } catch (e: Exception) {
                        android.util.Log.d("MainActivity", "OneSignal requestPermission: ${e.localizedMessage}")
                    }
                }

                // OneSignal Notification Deep Linking Handler
                val deepLinkRoute by SmartPOSApplication.deepLinkRoute.collectAsState()
                LaunchedEffect(deepLinkRoute) {
                    deepLinkRoute?.let { targetRoute ->
                        if (targetRoute.isNotBlank()) {
                            android.util.Log.d("MainActivity", "Executing Deep Link Navigation to: $targetRoute")
                            try {
                                navController.navigate(targetRoute) {
                                    launchSingleTop = true
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("MainActivity", "Deep Link navigation error: ${e.localizedMessage}")
                            }
                            SmartPOSApplication.consumeDeepLinkRoute()
                        }
                    }
                }

                val currentUser by viewModel.currentUser.collectAsState()
                val subscriptionState by viewModel.subscriptionState.collectAsState()
                val fbUser = com.example.data.firebase.FirebaseManager.auth?.currentUser

                // Initial Cloud Sync State: wait for Firestore subscription and profile to restore on launch
                var isInitialSyncing by remember { mutableStateOf(fbUser != null) }

                LaunchedEffect(Unit) {
                    if (fbUser != null) {
                        try {
                            // 1. Restore subscription directly from Firestore
                            com.example.data.subscription.SubscriptionRepository.restoreSubscriptionFromFirestore(
                                context = context,
                                userId = fbUser.uid,
                                mobileNumber = fbUser.phoneNumber ?: ""
                            )
                            // 2. Load merchant profile
                            viewModel.loadUserProfile(fbUser.uid)
                            // 3. Enforce session check
                            AppSessionManager.verifyAndEnforceSubscriptionLock(
                                context = context,
                                userUid = fbUser.phoneNumber ?: fbUser.uid
                            )
                        } catch (e: Exception) {
                            android.util.Log.e("MainActivity", "Initial cloud sync error: ${e.localizedMessage}")
                        } finally {
                            isInitialSyncing = false
                        }
                    } else {
                        isInitialSyncing = false
                    }
                }

                // Strict Route Guard logic via centralized AuthGuard
                val isLoggedIn = (fbUser != null) || (currentUser != null)
                val isSubscriptionValid = com.example.util.AuthGuard.isSubscriptionValid(subscriptionState)

                val startDestination = remember(isInitialSyncing, isLoggedIn, isSubscriptionValid) {
                    when {
                        !isLoggedIn -> Screen.Login.route
                        isLoggedIn && !isSubscriptionValid -> Screen.Paywall.route
                        else -> Screen.Dashboard.route // Direct to dashboard if logged in & valid subscription
                    }
                }

                val lifecycleOwner = LocalLifecycleOwner.current
                var sessionAccessState by remember {
                    mutableStateOf<SessionAccessState>(SessionAccessState.Granted)
                }

                // Subscription State Verification & Auto-Lock on App Launch and Foreground Resume
                DisposableEffect(lifecycleOwner, currentUser, subscriptionState, isInitialSyncing) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (!isInitialSyncing && (event == Lifecycle.Event.ON_RESUME || event == Lifecycle.Event.ON_START)) {
                            if (currentUser != null || fbUser != null) {
                                val state = AppSessionManager.verifyAndEnforceSubscriptionLock(
                                    context = context,
                                    userUid = currentUser?.mobileNumber ?: fbUser?.uid ?: ""
                                )
                                sessionAccessState = state
                                if (state is SessionAccessState.Locked) {
                                    val currentRoute = navController.currentBackStackEntry?.destination?.route
                                    if (currentRoute != Screen.Paywall.route && currentRoute != Screen.Login.route && currentRoute != Screen.ProfileSetup.route && currentRoute != Screen.Signup.route) {
                                        navController.navigate(Screen.Paywall.route) {
                                            popUpTo(0) { inclusive = true }
                                            launchSingleTop = true
                                        }
                                    }
                                }
                            }
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }

                // Forceful Navigation Route Interceptor for subscription enforcement
                DisposableEffect(navController, subscriptionState, isInitialSyncing) {
                    val listener = androidx.navigation.NavController.OnDestinationChangedListener { _, destination, _ ->
                        if (!isInitialSyncing) {
                            val user = viewModel.currentUser.value
                            val fb = com.example.data.firebase.FirebaseManager.auth?.currentUser
                            if (user != null || fb != null) {
                                val valid = com.example.util.AuthGuard.isSubscriptionValid(viewModel.subscriptionState.value)
                                if (!valid &&
                                    destination.route != Screen.Paywall.route &&
                                    destination.route != Screen.Login.route &&
                                    destination.route != Screen.ProfileSetup.route &&
                                    destination.route != Screen.Signup.route
                                ) {
                                    navController.navigate(Screen.Paywall.route) {
                                        popUpTo(0) { inclusive = true }
                                        launchSingleTop = true
                                    }
                                }
                            }
                        }
                    }
                    navController.addOnDestinationChangedListener(listener)
                    onDispose {
                        navController.removeOnDestinationChangedListener(listener)
                    }
                }

                // RemoteConfig App Update Checker (config/app_settings)
                var appUpdateInfo by remember { mutableStateOf<com.example.update.AppUpdateInfo?>(null) }
                var showUpdateDialog by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    try {
                        val updateInfo = com.example.service.RemoteConfigService.checkForAppUpdate(com.example.BuildConfig.VERSION_CODE)
                        if (updateInfo.isUpdateAvailable && updateInfo.latestVersionCode > com.example.BuildConfig.VERSION_CODE) {
                            appUpdateInfo = updateInfo
                            showUpdateDialog = true
                        } else {
                            appUpdateInfo = updateInfo
                            showUpdateDialog = false
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("MainActivity", "Remote update checker error: ${e.localizedMessage}")
                    }
                }

                // Reactive notification dispatch (Toasts)
                LaunchedEffect(key1 = true) {
                    viewModel.toastMessage.collectLatest { message ->
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                }

                val currentUpdateInfo = appUpdateInfo
                if (showUpdateDialog && currentUpdateInfo != null) {
                    com.example.ui.components.AppUpdateDialog(
                        updateInfo = currentUpdateInfo,
                        onUpdateNow = {
                            com.example.service.RemoteConfigService.openPlayStore(
                                context = context,
                                customUrl = currentUpdateInfo.downloadUrl
                            )
                            if (!currentUpdateInfo.isForceUpdate) {
                                showUpdateDialog = false
                            }
                        },
                        onLater = {
                            if (!currentUpdateInfo.isForceUpdate) {
                                showUpdateDialog = false
                            }
                        }
                    )
                }

                if (isInitialSyncing) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF0D1333)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PointOfSale,
                                contentDescription = null,
                                tint = EmeraldGreen,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "SmartPOS",
                                color = Color.White,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            CircularProgressIndicator(
                                color = EmeraldLight,
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.5.dp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Synchronizing profile & subscription...",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp
                            )
                        }
                    }
                } else {
                    NavHost(
                        navController = navController,
                        startDestination = startDestination,
                        modifier = Modifier.fillMaxSize()
                    ) {
                    composable(Screen.Login.route) {
                        LoginScreen(
                            viewModel = viewModel,
                            onNavigateToDashboard = {
                                val userUid = viewModel.currentUser.value?.mobileNumber ?: ""
                                val state = AppSessionManager.verifyAndEnforceSubscriptionLock(
                                    context = context,
                                    userUid = userUid
                                )
                                if (state is SessionAccessState.Locked) {
                                    navController.navigate(Screen.Paywall.route) {
                                        popUpTo(Screen.Login.route) { inclusive = true }
                                    }
                                } else {
                                    navController.navigate(Screen.Dashboard.route) {
                                        popUpTo(Screen.Login.route) { inclusive = true }
                                    }
                                }
                            },
                            onNavigate = { route ->
                                val userUid = viewModel.currentUser.value?.mobileNumber ?: ""
                                val targetRoute = if (route == Screen.Dashboard.route) {
                                    val state = AppSessionManager.verifyAndEnforceSubscriptionLock(
                                        context = context,
                                        userUid = userUid
                                    )
                                    if (state is SessionAccessState.Locked) Screen.Paywall.route else Screen.Dashboard.route
                                } else route

                                navController.navigate(targetRoute) {
                                    if (targetRoute == Screen.Dashboard.route || targetRoute == Screen.Paywall.route || targetRoute == Screen.ProfileSetup.route) {
                                        popUpTo(Screen.Login.route) { inclusive = true }
                                    }
                                }
                            }
                        )
                    }

                    composable(Screen.Signup.route) {
                        // Redirect obsolete signup route to our unified, high-security phone & Google login
                        LoginScreen(
                            viewModel = viewModel,
                            onNavigateToDashboard = {
                                val userUid = viewModel.currentUser.value?.mobileNumber ?: ""
                                val state = AppSessionManager.verifyAndEnforceSubscriptionLock(
                                    context = context,
                                    userUid = userUid
                                )
                                if (state is SessionAccessState.Locked) {
                                    navController.navigate(Screen.Paywall.route) {
                                        popUpTo(Screen.Login.route) { inclusive = true }
                                    }
                                } else {
                                    navController.navigate(Screen.Dashboard.route) {
                                        popUpTo(Screen.Login.route) { inclusive = true }
                                    }
                                }
                            },
                            onNavigate = { route ->
                                val userUid = viewModel.currentUser.value?.mobileNumber ?: ""
                                val targetRoute = if (route == Screen.Dashboard.route) {
                                    val state = AppSessionManager.verifyAndEnforceSubscriptionLock(
                                        context = context,
                                        userUid = userUid
                                    )
                                    if (state is SessionAccessState.Locked) Screen.Paywall.route else Screen.Dashboard.route
                                } else route

                                navController.navigate(targetRoute) {
                                    if (targetRoute == Screen.Dashboard.route || targetRoute == Screen.Paywall.route || targetRoute == Screen.ProfileSetup.route) {
                                        popUpTo(Screen.Login.route) { inclusive = true }
                                    }
                                }
                            }
                        )
                    }

                    composable(Screen.ProfileSetup.route) {
                        com.example.ui.screens.profile.ProfileSetupScreen(
                            viewModel = viewModel,
                            onSetupSuccess = {
                                navController.navigate(Screen.Paywall.route) {
                                    popUpTo(Screen.ProfileSetup.route) { inclusive = true }
                                }
                            }
                        )
                    }

                    composable(Screen.Dashboard.route) {
                        LaunchedEffect(Unit) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            }
                            val uid = viewModel.currentUser.value?.mobileNumber
                                ?: com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
                            com.example.service.MyFirebaseMessagingService.syncFcmTokenToFirestore(uid)
                        }

                        DashboardScreen(
                            viewModel = viewModel,
                            initialTab = com.example.ui.screens.dashboard.BottomTab.HOME,
                            onLogout = {
                                viewModel.logout(context = context) {
                                    navController.navigate(Screen.Login.route) {
                                        popUpTo(0) { inclusive = true }
                                    }
                                }
                            }
                        )
                    }

                    composable(Screen.Admin.route) {
                        DashboardScreen(
                            viewModel = viewModel,
                            initialTab = com.example.ui.screens.dashboard.BottomTab.HOME,
                            onLogout = {
                                viewModel.logout(context = context) {
                                    navController.navigate(Screen.Login.route) {
                                        popUpTo(0) { inclusive = true }
                                    }
                                }
                            }
                        )
                    }

                    composable(Screen.Udhar.route) {
                        DashboardScreen(
                            viewModel = viewModel,
                            initialTab = com.example.ui.screens.dashboard.BottomTab.UDHAR,
                            onLogout = {
                                viewModel.logout(context = context) {
                                    navController.navigate(Screen.Login.route) {
                                        popUpTo(0) { inclusive = true }
                                    }
                                }
                            }
                        )
                    }

                    composable(Screen.Products.route) {
                        DashboardScreen(
                            viewModel = viewModel,
                            initialTab = com.example.ui.screens.dashboard.BottomTab.INVENTORY,
                            onLogout = {
                                viewModel.logout(context = context) {
                                    navController.navigate(Screen.Login.route) {
                                        popUpTo(0) { inclusive = true }
                                    }
                                }
                            }
                        )
                    }

                    composable(Screen.CreateBill.route) {
                        DashboardScreen(
                            viewModel = viewModel,
                            initialTab = com.example.ui.screens.dashboard.BottomTab.POS,
                            onLogout = {
                                viewModel.logout(context = context) {
                                    navController.navigate(Screen.Login.route) {
                                        popUpTo(0) { inclusive = true }
                                    }
                                }
                            }
                        )
                    }

                    composable(Screen.History.route) {
                        DashboardScreen(
                            viewModel = viewModel,
                            initialTab = com.example.ui.screens.dashboard.BottomTab.HISTORY,
                            onLogout = {
                                viewModel.logout(context = context) {
                                    navController.navigate(Screen.Login.route) {
                                        popUpTo(0) { inclusive = true }
                                    }
                                }
                            }
                        )
                    }

                    composable(Screen.Paywall.route) {
                        val userUid = currentUser?.mobileNumber ?: fbUser?.uid ?: ""
                        val accessState = AppSessionManager.verifyAndEnforceSubscriptionLock(
                            context = context,
                            userUid = userUid
                        )
                        val isSubscriptionValid = com.example.util.AuthGuard.isSubscriptionValid(viewModel.subscriptionState.value)
                        val isLocked = accessState is SessionAccessState.Locked || !isSubscriptionValid
                        val isMandatory = isLocked || (navController.previousBackStackEntry?.destination?.route == Screen.ProfileSetup.route)
                        val lockReason = (accessState as? SessionAccessState.Locked)?.reason
                            ?: "Subscription Expired. Complete payment of ₹79 to unlock all features."

                        val navigateToDashboard = {
                            navController.navigate(Screen.Dashboard.route) {
                                popUpTo(Screen.Paywall.route) { inclusive = true }
                                launchSingleTop = true
                            }
                        }

                        com.example.ui.screens.paywall.PaywallScreen(
                            viewModel = viewModel,
                            onBack = {
                                val isSubValid = com.example.util.AuthGuard.isSubscriptionValid(viewModel.subscriptionState.value)
                                if (isSubValid) {
                                    navigateToDashboard()
                                } else if (!isMandatory) {
                                    if (navController.previousBackStackEntry != null) {
                                        navController.popBackStack()
                                    } else {
                                        navigateToDashboard()
                                    }
                                }
                            },
                            onNavigateToDashboard = navigateToDashboard,
                            isMandatory = isMandatory,
                            lockReason = lockReason
                        )
                    }
                }
            }
        }
    }
}

    /**
     * Razorpay Payment Result Callback on Payment Success.
     * Updates user Firestore profile (isProUser = true, subscriptionStatus = "ACTIVE"),
     * persists state locally in SharedPreferences, shows Toast, and navigates to Dashboard.
     */
    override fun onPaymentSuccess(razorpayPaymentId: String?, paymentData: PaymentData?) {
        val paymentId = razorpayPaymentId ?: paymentData?.paymentId ?: "pay_success"
        val mandateId = paymentData?.orderId
            ?.takeIf { it.isNotBlank() }
            ?: "MND-RZP-$paymentId"

        val userUid = FirebaseManager.auth?.currentUser?.uid
            ?: FirebaseManager.auth?.currentUser?.phoneNumber
            ?: ""

        PaymentGatewayConfig.handlePaymentSuccess(
            context = this,
            userUid = userUid,
            razorpayPaymentId = mandateId,
            paymentData = paymentData,
            onComplete = {
                runOnUiThread {
                    Toast.makeText(this, "Subscription Activated! 🎉", Toast.LENGTH_LONG).show()
                    navControllerRef?.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Paywall.route) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
        )
    }

    /**
     * Razorpay Payment Result Callback on Payment Error.
     * Displays a clear error message Toast allowing the user to retry.
     */
    override fun onPaymentError(code: Int, response: String?, paymentData: PaymentData?) {
        val errorMsg = response ?: "Payment cancelled or authorization failed"
        Toast.makeText(this, "Payment Error ($code): $errorMsg", Toast.LENGTH_LONG).show()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: android.content.Intent?) {
        val extras = intent?.extras ?: return
        val rawRoute = extras.getString("screen_route")
            ?: extras.getString("route")
            ?: extras.getString("target_screen")
            ?: extras.getString("screen")

        if (!rawRoute.isNullOrBlank()) {
            val resolvedRoute = SmartPOSApplication.mapRoute(rawRoute)
            SmartPOSApplication.setDeepLinkRoute(resolvedRoute)
        }
    }
}
