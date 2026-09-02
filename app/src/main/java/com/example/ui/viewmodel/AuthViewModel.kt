package com.example.ui.viewmodel

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.AuthRepository
import com.example.data.repository.BillingRepository
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class AuthViewModel(
    private val authRepository: AuthRepository = AuthRepository()
) : ViewModel() {

    companion object {
        private const val TAG = "AuthViewModel"
    }

    var isSendingOtp by mutableStateOf(false)
        private set

    var isOtpSent by mutableStateOf(false)
        internal set

    var isVerifyingOtp by mutableStateOf(false)
        private set

    var authError by mutableStateOf<String?>(null)
        internal set

    var timerSeconds by mutableIntStateOf(0)
        private set

    var authMobile by mutableStateOf("")
        private set

    private var verificationId: String = ""
    private var resendToken: PhoneAuthProvider.ForceResendingToken? = null
    private var timerJob: Job? = null

    private val _toastMessage = MutableSharedFlow<String>()
    val toastMessage: SharedFlow<String> = _toastMessage.asSharedFlow()

    val currentUser: FirebaseUser?
        get() = authRepository.currentUser

    /**
     * Initiates Firebase Phone Number verification.
     */
    fun sendPhoneOtp(
        mobileNumber: String,
        activity: Activity,
        onAutoVerified: ((String) -> Unit)? = null
    ) {
        val cleanDigits = mobileNumber.replace("\\D".toRegex(), "")
        if (cleanDigits.length < 10) {
            authError = "Please enter a valid 10-digit mobile number."
            return
        }

        val formattedPhone = if (cleanDigits.length == 10) "+91$cleanDigits" else "+$cleanDigits"
        authMobile = formattedPhone
        authError = null
        isSendingOtp = true

        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                Log.d(TAG, "Phone auto-verification completed successfully")
                isSendingOtp = false
                isVerifyingOtp = true

                viewModelScope.launch {
                    val result = authRepository.signInWithPhoneCredential(credential)
                    isVerifyingOtp = false
                    result.onSuccess { authResult ->
                        val userId = authResult.user?.uid ?: ""
                        val phone = authResult.user?.phoneNumber ?: authMobile
                        try {
                            com.example.data.subscription.SubscriptionRepository.restoreSubscriptionFromFirestore(
                                context = com.example.SmartPOSApplication.instance,
                                userId = userId,
                                mobileNumber = phone
                            )
                            com.example.data.subscription.AppSessionManager.verifyAndEnforceSubscriptionLock(
                                context = com.example.SmartPOSApplication.instance,
                                userUid = phone.ifBlank { userId }
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "Error restoring sub on auto-verify: ${e.localizedMessage}")
                        }
                        _toastMessage.emit("Phone number verified automatically!")
                        resetAuthState()
                        onAutoVerified?.invoke(userId)
                    }.onFailure { error ->
                        authError = "Auto-verification sign-in failed: ${error.localizedMessage}"
                    }
                }
            }

            override fun onVerificationFailed(exception: FirebaseException) {
                Log.e(TAG, "Phone verification failed: ${exception.localizedMessage}")
                isSendingOtp = false
                isVerifyingOtp = false
                authError = when (exception) {
                    is FirebaseAuthInvalidCredentialsException -> "Invalid phone number format or credentials."
                    is FirebaseTooManyRequestsException -> "SMS quota exceeded or too many requests. Please try again later."
                    else -> exception.localizedMessage ?: "Verification failed. Please check network/App Check."
                }
            }

            override fun onCodeSent(
                verificationId: String,
                token: PhoneAuthProvider.ForceResendingToken
            ) {
                Log.d(TAG, "OTP code sent successfully: $verificationId")
                this@AuthViewModel.verificationId = verificationId
                this@AuthViewModel.resendToken = token
                isSendingOtp = false
                isOtpSent = true
                startResendTimer(60)

                viewModelScope.launch {
                    _toastMessage.emit("OTP sent successfully to $formattedPhone")
                }
            }
        }

        authRepository.verifyPhoneNumber(
            activity = activity,
            phoneNumber = formattedPhone,
            callbacks = callbacks,
            resendToken = resendToken
        )
    }

    /**
     * Verifies the 6-digit OTP code with Firebase.
     */
    fun verifyPhoneOtp(
        userEnteredOtp: String,
        onSuccess: (String) -> Unit
    ) {
        val trimmedOtp = userEnteredOtp.trim()
        if (trimmedOtp.length != 6) {
            authError = "Please enter a valid 6-digit OTP."
            return
        }

        if (verificationId.isBlank()) {
            authError = "No active verification session. Please request OTP again."
            return
        }

        authError = null
        isVerifyingOtp = true

        viewModelScope.launch {
            val result = authRepository.verifyOtp(verificationId, trimmedOtp)
            isVerifyingOtp = false
            result.onSuccess { authResult ->
                val userId = authResult.user?.uid ?: ""
                val phone = authResult.user?.phoneNumber ?: authMobile
                try {
                    com.example.data.subscription.SubscriptionRepository.restoreSubscriptionFromFirestore(
                        context = com.example.SmartPOSApplication.instance,
                        userId = userId,
                        mobileNumber = phone
                    )
                    com.example.data.subscription.AppSessionManager.verifyAndEnforceSubscriptionLock(
                        context = com.example.SmartPOSApplication.instance,
                        userUid = phone.ifBlank { userId }
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error restoring sub in verifyPhoneOtp: ${e.localizedMessage}")
                }
                _toastMessage.emit("Authentication successful!")
                resetAuthState()
                onSuccess(userId)
            }.onFailure { error ->
                authError = when (error) {
                    is FirebaseAuthInvalidCredentialsException -> "Invalid or expired OTP code. Please try again."
                    else -> error.localizedMessage ?: "OTP verification failed."
                }
            }
        }
    }

    /**
     * Sign in with Google ID Token via Firebase GoogleAuthProvider.
     */
    fun signInWithGoogle(idToken: String, onSuccess: () -> Unit) {
        authError = null
        isVerifyingOtp = true

        viewModelScope.launch {
            val result = authRepository.signInWithGoogle(idToken)
            isVerifyingOtp = false
            result.onSuccess { authResult ->
                val user = authResult.user
                if (user != null) {
                    try {
                        com.example.data.subscription.SubscriptionRepository.restoreSubscriptionFromFirestore(
                            context = com.example.SmartPOSApplication.instance,
                            userId = user.uid,
                            mobileNumber = user.email ?: user.phoneNumber ?: ""
                        )
                        com.example.data.subscription.AppSessionManager.verifyAndEnforceSubscriptionLock(
                            context = com.example.SmartPOSApplication.instance,
                            userUid = user.email ?: user.uid
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Error restoring sub in signInWithGoogle: ${e.localizedMessage}")
                    }
                }
                _toastMessage.emit("Google Sign-In successful!")
                resetAuthState()
                onSuccess()
            }.onFailure { error ->
                authError = "Google Sign-In failed: ${error.localizedMessage}"
            }
        }
    }

    fun startResendTimer(seconds: Int = 60) {
        timerJob?.cancel()
        timerSeconds = seconds
        timerJob = viewModelScope.launch {
            while (timerSeconds > 0) {
                delay(1000)
                timerSeconds--
            }
        }
    }

    fun resetAuthState() {
        authMobile = ""
        verificationId = ""
        resendToken = null
        isOtpSent = false
        isVerifyingOtp = false
        isSendingOtp = false
        authError = null
        timerJob?.cancel()
        timerSeconds = 0
    }

    fun signOut(context: Context? = null, billingRepository: BillingRepository? = null, onSuccess: () -> Unit) {
        viewModelScope.launch {
            authRepository.signOut(context, billingRepository)
            resetAuthState()
            onSuccess()
        }
    }
}
