package com.example.data.repository

import android.util.Log
import com.example.data.db.UserDao
import com.example.data.db.UserEntity
import com.example.data.firebase.FirebaseManager
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class UserProfileData(
    val uid: String = "",
    val fullName: String = "",
    val businessName: String = "",
    val businessCategory: String = "",
    val upiId: String = "merchant@upi",
    val merchantName: String = "",
    val mobileNumber: String = "",
    val email: String = "",
    val role: String = "user"
)

class UserRepository(
    private val userDao: UserDao? = null
) {
    companion object {
        private const val TAG = "UserRepository"
    }

    private val firestore get() = FirebaseManager.firestore
    private val auth get() = FirebaseManager.auth

    suspend fun getUserProfile(userId: String? = null): UserProfileData? = withContext(Dispatchers.IO) {
        val targetUid = userId ?: auth?.currentUser?.uid
        if (targetUid.isNullOrEmpty()) {
            return@withContext null
        }

        try {
            if (FirebaseManager.isFirebaseAvailable && firestore != null) {
                val doc = firestore!!.collection("users").document(targetUid).get().await()
                if (doc != null && doc.exists()) {
                    val fullName = doc.getString("fullName")
                        ?: doc.getString("displayName")
                        ?: doc.getString("name")
                        ?: ""
                    val businessName = doc.getString("businessName")
                        ?: doc.getString("shopName")
                        ?: ""
                    val businessCategory = doc.getString("businessCategory")
                        ?: doc.getString("category")
                        ?: doc.getString("selectedCategory")
                        ?: ""
                    val upiId = doc.getString("upiId")
                        ?: doc.getString("merchantUpi")
                        ?: doc.getString("vpa")
                        ?: "merchant@upi"
                    val merchantName = doc.getString("merchantName")
                        ?: businessName
                    val mobile = doc.getString("mobileNumber")
                        ?: doc.getString("phoneNumber")
                        ?: doc.getString("mobile")
                        ?: ""
                    val email = doc.getString("email") ?: ""

                    return@withContext UserProfileData(
                        uid = targetUid,
                        fullName = fullName,
                        businessName = businessName,
                        businessCategory = businessCategory,
                        upiId = upiId.ifBlank { "merchant@upi" },
                        merchantName = merchantName,
                        mobileNumber = mobile,
                        email = email,
                        role = doc.getString("role") ?: "user"
                    )
                }
            }

            // Fallback to local Room DAO
            if (userDao != null) {
                val localUser = userDao.getUserById(targetUid.hashCode())
                    ?: userDao.getAllUsers().firstOrNull()
                if (localUser != null) {
                    return@withContext UserProfileData(
                        uid = targetUid,
                        fullName = localUser.fullName,
                        businessName = localUser.businessName,
                        businessCategory = localUser.category,
                        upiId = localUser.upiId.ifBlank { "merchant@upi" },
                        merchantName = localUser.merchantName,
                        mobileNumber = localUser.mobileNumber
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading user profile: ${e.localizedMessage}")
        }
        null
    }

    suspend fun saveUserProfile(
        userId: String? = null,
        fullName: String,
        businessName: String,
        businessCategory: String,
        upiId: String,
        merchantName: String = businessName,
        mobileNumber: String = ""
    ): Result<UserProfileData> = withContext(Dispatchers.IO) {
        val targetUid = userId ?: auth?.currentUser?.uid ?: ""
        if (targetUid.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("User is not authenticated."))
        }

        val profileData = hashMapOf<String, Any>(
            "uid" to targetUid,
            "fullName" to fullName,
            "displayName" to fullName,
            "businessName" to businessName,
            "businessCategory" to businessCategory,
            "category" to businessCategory,
            "upiId" to upiId.ifBlank { "merchant@upi" },
            "merchantName" to merchantName.ifBlank { businessName },
            "updatedAt" to System.currentTimeMillis()
        )
        if (mobileNumber.isNotBlank()) {
            profileData["mobileNumber"] = mobileNumber
            profileData["phoneNumber"] = mobileNumber
        }

        try {
            if (FirebaseManager.isFirebaseAvailable && firestore != null) {
                firestore!!.collection("users")
                    .document(targetUid)
                    .set(profileData, SetOptions.merge())
                    .await()
            }

            // Save to local Room DAO
            if (userDao != null) {
                val resolvedUser = UserEntity(
                    id = targetUid.hashCode(),
                    fullName = fullName,
                    businessName = businessName,
                    mobileNumber = mobileNumber,
                    passwordHash = "",
                    category = businessCategory,
                    upiId = upiId.ifBlank { "merchant@upi" },
                    merchantName = merchantName.ifBlank { businessName }
                )
                userDao.insertUser(resolvedUser)
            }

            val savedProfile = UserProfileData(
                uid = targetUid,
                fullName = fullName,
                businessName = businessName,
                businessCategory = businessCategory,
                upiId = upiId.ifBlank { "merchant@upi" },
                merchantName = merchantName.ifBlank { businessName },
                mobileNumber = mobileNumber
            )
            Result.success(savedProfile)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving user profile: ${e.localizedMessage}")
            // Even if offline, save locally
            if (userDao != null) {
                val resolvedUser = UserEntity(
                    id = targetUid.hashCode(),
                    fullName = fullName,
                    businessName = businessName,
                    mobileNumber = mobileNumber,
                    passwordHash = "",
                    category = businessCategory,
                    upiId = upiId.ifBlank { "merchant@upi" },
                    merchantName = merchantName.ifBlank { businessName }
                )
                userDao.insertUser(resolvedUser)
            }
            Result.failure(e)
        }
    }
}
