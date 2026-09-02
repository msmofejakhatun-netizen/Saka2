package com.example.ui.navigation

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Signup : Screen("signup")
    object Dashboard : Screen("dashboard")
    object Admin : Screen("admin")
    object ProfileSetup : Screen("profile_setup")
    object Products : Screen("products")
    object CreateBill : Screen("create_bill")
    object History : Screen("history")
    object Udhar : Screen("udhar")
    object Paywall : Screen("paywall")
}
