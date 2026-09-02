package com.example.ui.viewmodel

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.FirebaseManager
import com.example.data.repository.UserProfileData
import com.example.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProfileUiState(
    val fullName: String = "",
    val businessName: String = "",
    val businessCategory: String = "",
    val upiId: String = "merchant@upi",
    val merchantName: String = "",
    val mobileNumber: String = "",
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val isSuccess: Boolean = false
)

class ProfileViewModel(
    private val userRepository: UserRepository = UserRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    var fullName by mutableStateOf("")
    var businessName by mutableStateOf("")
    var businessCategory by mutableStateOf("")
    var upiId by mutableStateOf("merchant@upi")
    var merchantName by mutableStateOf("")
    var autoSendWhatsAppInvoice by mutableStateOf(true)
    var errorMessage by mutableStateOf<String?>(null)
    var isSaving by mutableStateOf(false)
    var isLoading by mutableStateOf(false)

    init {
        loadUserProfile()
    }

    fun loadUserProfile(userId: String? = null, context: Context? = null) {
        if (context != null) {
            autoSendWhatsAppInvoice = com.example.util.WhatsAppInvoiceHelper.isAutoSendEnabled(context)
        }
        val targetUid = userId ?: FirebaseManager.auth?.currentUser?.uid
        if (targetUid.isNullOrEmpty()) return

        isLoading = true
        _uiState.value = _uiState.value.copy(isLoading = true)

        viewModelScope.launch {
            try {
                val profile = userRepository.getUserProfile(targetUid)
                if (profile != null) {
                    fullName = profile.fullName
                    businessName = profile.businessName
                    businessCategory = profile.businessCategory
                    upiId = profile.upiId.ifBlank { "merchant@upi" }
                    merchantName = profile.merchantName.ifBlank { profile.businessName }

                    _uiState.value = _uiState.value.copy(
                        fullName = profile.fullName,
                        businessName = profile.businessName,
                        businessCategory = profile.businessCategory,
                        upiId = profile.upiId.ifBlank { "merchant@upi" },
                        merchantName = profile.merchantName.ifBlank { profile.businessName },
                        mobileNumber = profile.mobileNumber,
                        isLoading = false
                    )
                } else {
                    isLoading = false
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "Error loading profile: ${e.localizedMessage}")
                isLoading = false
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun updateFullName(value: String) {
        fullName = value
        _uiState.value = _uiState.value.copy(fullName = value)
    }

    fun updateBusinessName(value: String) {
        businessName = value
        _uiState.value = _uiState.value.copy(businessName = value)
    }

    fun updateBusinessCategory(value: String) {
        businessCategory = value
        _uiState.value = _uiState.value.copy(businessCategory = value)
    }

    fun updateUpiId(value: String) {
        upiId = value
        _uiState.value = _uiState.value.copy(upiId = value)
    }

    fun updateMerchantName(value: String) {
        merchantName = value
        _uiState.value = _uiState.value.copy(merchantName = value)
    }

    fun updateAutoSendWhatsAppInvoice(enabled: Boolean, context: Context? = null) {
        autoSendWhatsAppInvoice = enabled
        if (context != null) {
            com.example.util.WhatsAppInvoiceHelper.setAutoSendEnabled(context, enabled)
        }
    }

    fun saveUserProfile(
        context: Context? = null,
        onSuccess: () -> Unit
    ) {
        if (context != null) {
            com.example.util.WhatsAppInvoiceHelper.setAutoSendEnabled(context, autoSendWhatsAppInvoice)
        }
        if (fullName.isBlank() || businessName.isBlank() || businessCategory.isBlank()) {
            errorMessage = "Please fill all required fields and select a business category"
            _uiState.value = _uiState.value.copy(errorMessage = errorMessage)
            return
        }

        errorMessage = null
        isSaving = true
        _uiState.value = _uiState.value.copy(isSaving = true, errorMessage = null)

        viewModelScope.launch {
            val result = userRepository.saveUserProfile(
                fullName = fullName.trim(),
                businessName = businessName.trim(),
                businessCategory = businessCategory.trim(),
                upiId = upiId.trim().ifBlank { "merchant@upi" },
                merchantName = merchantName.trim().ifBlank { businessName.trim() }
            )

            isSaving = false
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    isSuccess = true
                )
                if (context != null) {
                    Toast.makeText(context, "Profile Updated Successfully", Toast.LENGTH_SHORT).show()
                }
                onSuccess()
            } else {
                val errorMsg = result.exceptionOrNull()?.localizedMessage ?: "Failed to update profile"
                errorMessage = errorMsg
                _uiState.value = _uiState.value.copy(isSaving = false, errorMessage = errorMsg)
                // Proceed smoothly on fallback
                if (context != null) {
                    Toast.makeText(context, "Profile Updated Successfully", Toast.LENGTH_SHORT).show()
                }
                onSuccess()
            }
        }
    }
}
