package com.example.ui.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.FirebaseManager
import com.example.data.subscription.AppSessionManager
import com.example.data.subscription.PaymentGatewayConfig
import com.example.data.subscription.SubscriptionInfo
import com.example.data.subscription.SubscriptionManager
import com.example.data.subscription.SubscriptionModel
import com.example.data.subscription.SubscriptionRepository
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

sealed class SubscriptionNavEvent {
    object NavigateToDashboard : SubscriptionNavEvent()
    data class ShowToast(val message: String) : SubscriptionNavEvent()
}

data class SubscriptionUiState(
    val showTrialPlan: Boolean = true,
    val hasUsedTrial: Boolean = false,
    val isProUser: Boolean = false,
    val subscriptionStatus: String = "FREE",
    val planType: String = "FREE",
    val planName: String = "Free Plan",
    val daysLeft: Long = 0L,
    val displayBadgeTitle: String = "Free Plan",
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class SubscriptionViewModel : ViewModel() {

    companion object {
        private const val TAG = "SubscriptionViewModel"
    }

    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    val subscriptionState: StateFlow<SubscriptionInfo> = SubscriptionManager.subscriptionState

    private val _uiState = MutableStateFlow(
        SubscriptionUiState(
            showTrialPlan = !SubscriptionManager.subscriptionState.value.hasUsedTrial,
            hasUsedTrial = SubscriptionManager.subscriptionState.value.hasUsedTrial,
            isProUser = SubscriptionManager.subscriptionState.value.isProUser,
            subscriptionStatus = SubscriptionManager.subscriptionState.value.status,
            planType = SubscriptionManager.subscriptionState.value.planType,
            planName = SubscriptionManager.subscriptionState.value.planName,
            daysLeft = SubscriptionManager.subscriptionState.value.daysLeft,
            displayBadgeTitle = SubscriptionManager.subscriptionState.value.displayBadgeTitle
        )
    )
    val uiState: StateFlow<SubscriptionUiState> = _uiState.asStateFlow()

    val isProUser: StateFlow<Boolean> = subscriptionState
        .map { it.isProUser }
        .distinctUntilChanged()
        .let { flow ->
            val initial = subscriptionState.value.isProUser
            val stateFlow = MutableStateFlow(initial)
            viewModelScope.launch {
                flow.collect { stateFlow.value = it }
            }
            stateFlow
        }

    val subscriptionTier: StateFlow<String> = subscriptionState
        .map { it.subscriptionTier }
        .distinctUntilChanged()
        .let { flow ->
            val initial = subscriptionState.value.subscriptionTier
            val stateFlow = MutableStateFlow(initial)
            viewModelScope.launch {
                flow.collect { stateFlow.value = it }
            }
            stateFlow
        }

    val planType: StateFlow<String> = subscriptionState
        .map { it.planType }
        .distinctUntilChanged()
        .let { flow ->
            val initial = subscriptionState.value.planType
            val stateFlow = MutableStateFlow(initial)
            viewModelScope.launch {
                flow.collect { stateFlow.value = it }
            }
            stateFlow
        }

    val hasUsedTrial: StateFlow<Boolean> = subscriptionState
        .map { it.hasUsedTrial }
        .distinctUntilChanged()
        .let { flow ->
            val initial = subscriptionState.value.hasUsedTrial
            val stateFlow = MutableStateFlow(initial)
            viewModelScope.launch {
                flow.collect { stateFlow.value = it }
            }
            stateFlow
        }

    private val _isSuccessDialogVisible = MutableStateFlow(false)
    val isSuccessDialogVisible: StateFlow<Boolean> = _isSuccessDialogVisible.asStateFlow()

    private val _navigationChannel = Channel<SubscriptionNavEvent>(Channel.BUFFERED)
    val navigationEvent: Flow<SubscriptionNavEvent> = _navigationChannel.receiveAsFlow()

    init {
        val currentAuthUser = FirebaseManager.auth?.currentUser
        val uid = currentAuthUser?.uid ?: ""
        if (uid.isNotBlank()) {
            checkTrialEligibility(uid)
        }

        viewModelScope.launch {
            subscriptionState.collect { info ->
                val usedTrial = info.hasUsedTrial || info.trialStartDate > 0L || info.subscriptionTier == "TRIAL_1_INR" || info.planType != "FREE"
                _uiState.update {
                    it.copy(
                        showTrialPlan = !usedTrial,
                        hasUsedTrial = usedTrial,
                        isProUser = info.isProUser,
                        subscriptionStatus = info.status,
                        planType = info.planType,
                        planName = info.planName,
                        daysLeft = info.daysLeft,
                        displayBadgeTitle = info.displayBadgeTitle
                    )
                }
            }
        }
    }

    /**
     * Verifies trial eligibility directly against the server-side Firestore records:
     * - users/{userId} (hasUsedTrial, trialStartDate, mandateId, subscriptionStatus)
     * - users/{userId}/subscription/current
     */
    fun checkTrialEligibility(userId: String) {
        if (userId.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val subModel = SubscriptionRepository.fetchSubscription(userId)
                val userDoc = firestore.collection("users").document(userId).get().await()
                val subDoc = firestore.collection("users").document(userId)
                    .collection("subscription").document("current").get().await()

                val hasAlreadyUsedTrial = (userDoc.getBoolean("hasUsedTrial") ?: false) ||
                        (subDoc.getBoolean("hasUsedTrial") ?: false) ||
                        ((userDoc.getLong("trialStartDate") ?: 0L) > 0L) ||
                        ((subDoc.getLong("trialStartDate") ?: 0L) > 0L) ||
                        (subDoc.getString("subscriptionTier") == "TRIAL_1_INR") ||
                        (subModel?.hasUsedTrial == true) ||
                        (subModel?.planType != null && subModel.planType != "FREE")

                val status = subModel?.status
                    ?: subDoc.getString("status")
                    ?: userDoc.getString("subscriptionStatus")
                    ?: userDoc.getString("status")
                    ?: "FREE"
                val isPro = subModel?.isProUser
                    ?: subDoc.getBoolean("isProUser")
                    ?: userDoc.getBoolean("isProUser")
                    ?: false

                val planT = subModel?.planType ?: "FREE"
                val planN = subModel?.planName ?: "Free Plan"
                val daysL = subModel?.daysLeft ?: 0L
                val badge = subModel?.displayBadgeTitle ?: "Free Plan"

                _uiState.update {
                    it.copy(
                        showTrialPlan = !hasAlreadyUsedTrial,
                        hasUsedTrial = hasAlreadyUsedTrial,
                        subscriptionStatus = status,
                        isProUser = isPro,
                        planType = planT,
                        planName = planN,
                        daysLeft = daysL,
                        displayBadgeTitle = badge,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking trial eligibility: ${e.localizedMessage}")
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun showSuccessDialog() {
        _isSuccessDialogVisible.value = true
    }

    fun dismissSuccessDialog() {
        _isSuccessDialogVisible.value = false
    }

    fun onSubscriptionSuccess(
        context: Context,
        userUid: String,
        paymentId: String,
        planType: String? = null,
        amountPaid: Double? = null
    ) {
        viewModelScope.launch {
            _isSuccessDialogVisible.value = true
            PaymentGatewayConfig.handlePaymentSuccess(
                context = context,
                userUid = userUid,
                razorpayPaymentId = paymentId,
                planType = planType,
                amountPaid = amountPaid,
                onComplete = {
                    checkTrialEligibility(userUid)
                }
            )
        }
    }

    fun cancelSubscription(
        context: Context,
        userUid: String,
        onComplete: (Boolean, String) -> Unit
    ) {
        SubscriptionManager.cancelSubscription(
            context = context,
            userUid = userUid,
            onComplete = onComplete
        )
    }

    fun refreshSubscription(context: Context, userUid: String) {
        SubscriptionManager.init(context, userUid)
        AppSessionManager.verifyAndEnforceSubscriptionLock(context, userUid)
        checkTrialEligibility(userUid)
    }

    /**
     * Restores subscription status from Firestore upon fresh install/login or via manual Restore button.
     */
    fun restoreSubscription(
        context: Context,
        mobileNumberOrUid: String = "",
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val currentAuthUser = FirebaseManager.auth?.currentUser
                val uid = currentAuthUser?.uid ?: mobileNumberOrUid
                val phone = currentAuthUser?.phoneNumber ?: mobileNumberOrUid

                val restored = SubscriptionRepository.restoreSubscriptionFromFirestore(
                    context = context,
                    userId = uid,
                    mobileNumber = phone
                )

                _uiState.update { it.copy(isLoading = false) }

                if (restored != null && com.example.util.AuthGuard.isSubscriptionValid(restored)) {
                    AppSessionManager.verifyAndEnforceSubscriptionLock(context, uid)
                    onResult(true, "Active subscription (${restored.planName}) restored successfully!")
                } else {
                    onResult(false, "No active paid subscription found for this account.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in restoreSubscription: ${e.localizedMessage}")
                _uiState.update { it.copy(isLoading = false) }
                onResult(false, "Failed to restore: ${e.localizedMessage}")
            }
        }
    }

    fun triggerDashboardNavigation() {
        viewModelScope.launch {
            _navigationChannel.send(SubscriptionNavEvent.NavigateToDashboard)
        }
    }
}
