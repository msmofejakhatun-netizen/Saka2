package com.example.util

import com.example.data.db.UserEntity
import java.util.Locale

enum class BusinessType {
    PHARMACY,
    KIRANA,
    GARMENTS,
    RESTAURANT,
    GENERAL
}

object BusinessCategoryUtils {

    fun getBusinessType(categoryName: String?): BusinessType {
        if (categoryName.isNullOrBlank()) return BusinessType.GENERAL
        val cat = categoryName.lowercase(Locale.ROOT)
        return when {
            cat.contains("pharmacy") || cat.contains("medical") || cat.contains("medicine") || cat.contains("pharma") -> BusinessType.PHARMACY
            cat.contains("kirana") || cat.contains("grocery") || cat.contains("supermarket") || cat.contains("provision") -> BusinessType.KIRANA
            cat.contains("garment") || cat.contains("clothing") || cat.contains("apparel") || cat.contains("fashion") || cat.contains("textile") -> BusinessType.GARMENTS
            cat.contains("restaurant") || cat.contains("cafe") || cat.contains("food") || cat.contains("eatery") || cat.contains("hotel") || cat.contains("dhaba") -> BusinessType.RESTAURANT
            else -> BusinessType.GENERAL
        }
    }

    fun getBusinessType(user: UserEntity?): BusinessType {
        return getBusinessType(user?.category)
    }

    // Category workflow checkers
    fun isPharmacy(user: UserEntity?): Boolean = getBusinessType(user) == BusinessType.PHARMACY
    fun isPharmacy(categoryName: String?): Boolean = getBusinessType(categoryName) == BusinessType.PHARMACY

    fun isKirana(user: UserEntity?): Boolean = getBusinessType(user) == BusinessType.KIRANA
    fun isKirana(categoryName: String?): Boolean = getBusinessType(categoryName) == BusinessType.KIRANA

    fun isGarments(user: UserEntity?): Boolean = getBusinessType(user) == BusinessType.GARMENTS
    fun isGarments(categoryName: String?): Boolean = getBusinessType(categoryName) == BusinessType.GARMENTS

    fun isRestaurant(user: UserEntity?): Boolean = getBusinessType(user) == BusinessType.RESTAURANT
    fun isRestaurant(categoryName: String?): Boolean = getBusinessType(categoryName) == BusinessType.RESTAURANT
}
