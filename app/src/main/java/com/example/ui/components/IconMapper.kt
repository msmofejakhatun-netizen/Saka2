package com.example.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

object IconMapper {
    fun getIconByName(name: String): ImageVector {
        return when (name.lowercase()) {
            "shopping_basket", "shopping_bag" -> Icons.Default.ShoppingBasket
            "checkroom", "apparel" -> Icons.Default.Checkroom
            "devices", "electronics" -> Icons.Default.Devices
            "local_pharmacy", "pharmacy" -> Icons.Default.LocalPharmacy
            "bolt", "energy" -> Icons.Default.Bolt
            "shopping_cart", "grocery" -> Icons.Default.ShoppingCart
            "store", "business" -> Icons.Default.Store
            "restaurant", "food" -> Icons.Default.Restaurant
            "medical_services", "health" -> Icons.Default.MedicalServices
            "home", "hardware" -> Icons.Default.Home
            else -> Icons.Default.Category
        }
    }

    val availableIcons = listOf(
        "shopping_basket" to Icons.Default.ShoppingBasket,
        "checkroom" to Icons.Default.Checkroom,
        "devices" to Icons.Default.Devices,
        "local_pharmacy" to Icons.Default.LocalPharmacy,
        "bolt" to Icons.Default.Bolt,
        "shopping_cart" to Icons.Default.ShoppingCart,
        "store" to Icons.Default.Store,
        "restaurant" to Icons.Default.Restaurant,
        "medical_services" to Icons.Default.MedicalServices,
        "home" to Icons.Default.Home
    )
}
