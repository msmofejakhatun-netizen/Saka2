package com.example.ui.screens.login

import android.app.Activity
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCustomException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.example.ui.components.GlassmorphicCard
import com.example.ui.components.PremiumGradientBackground
import com.example.ui.components.PremiumLoadingState
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.EmeraldLight
import com.example.ui.viewmodel.BillingViewModel
import com.example.util.WebUtils
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    viewModel: BillingViewModel,
    onNavigateToDashboard: () -> Unit,
    onNavigate: (route: String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var mobileNumberInput by remember { mutableStateOf("") }
    var otpInput by remember { mutableStateOf("") }

    val webClientId = remember(context) {
        try {
            context.getString(R.string.default_web_client_id)
        } catch (e: Exception) {
            "968984077515-smartposclientid.apps.googleusercontent.com"
        }
    }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                val idToken = account?.idToken
                if (!idToken.isNullOrEmpty()) {
                    viewModel.signInWithGoogle(
                        idToken = idToken,
                        email = account.email ?: "googleuser@gmail.com",
                        displayName = account.displayName ?: "Google User",
                        onNavigate = onNavigate
                    )
                } else {
                    viewModel.authError = "Unable to retrieve Google account credentials."
                }
            } catch (e: ApiException) {
                Log.e("GoogleAuth", "Legacy Google Sign-In failed code ${e.statusCode}: ${e.message}")
                if (e.statusCode == 12500 || e.statusCode == 12501) {
                    viewModel.authError = "Google Sign-In cancelled by user."
                } else {
                    viewModel.authError = "Google Sign-In error (${e.statusCode}): Please verify Google account settings on device."
                }
            }
        } else {
            Log.w("GoogleAuth", "Legacy Google Sign-In cancelled with result code: ${result.resultCode}")
            viewModel.authError = "Google Sign-In was cancelled or no account selected."
        }
    }

    PremiumGradientBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(scrollState)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Screen Header Back Arrow if OTP sent
            if (viewModel.isOtpSent) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    IconButton(
                        onClick = {
                            viewModel.isOtpSent = false
                            otpInput = ""
                        },
                        modifier = Modifier.testTag("login_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                }
            }

            // Visual Banner Frame
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .testTag("login_hero_card"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Image(
                        painter = painterResource(id = R.drawable.img_billing_hero),
                        contentDescription = "Billing Hero Banner",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color(0xBB090D22),
                                        Color(0xFF090D22)
                                    )
                                )
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Heading Title
            Text(
                text = if (viewModel.isOtpSent) "Verify OTP" else "Secure Access",
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = 0.5.sp
                ),
                modifier = Modifier.testTag("login_screen_title")
            )

            Text(
                text = if (viewModel.isOtpSent) {
                    "Enter the 6-digit verification code sent to ${viewModel.authMobile}"
                } else {
                    "Log in or Register with Phone OTP or Google Account"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF94A3B8),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
            )

            // Glassmorphic Form Card
            GlassmorphicCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("login_form_card")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Validation Errors
                    AnimatedVisibility(
                        visible = viewModel.authError != null,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        viewModel.authError?.let { error ->
                            Text(
                                text = error,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier
                                    .padding(bottom = 16.dp)
                                    .testTag("login_error_text")
                            )
                        }
                    }

                    if (!viewModel.isOtpSent) {
                        // --- SCREEN 1: Mobile Input & Google Sign-In ---
                        
                        // Mobile Field with Prefix label
                        OutlinedTextField(
                            value = mobileNumberInput,
                            onValueChange = { input ->
                                if (input.all { it.isDigit() } && input.length <= 10) {
                                    mobileNumberInput = input
                                }
                            },
                            label = { Text("Mobile Number", color = Color(0xFF94A3B8)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Phone,
                                    contentDescription = "Phone Icon",
                                    tint = EmeraldGreen
                                )
                            },
                            prefix = {
                                Text(
                                    text = "+91 ",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = EmeraldGreen,
                                unfocusedBorderColor = Color(0x33FFFFFF),
                                focusedLabelColor = EmeraldGreen,
                                unfocusedLabelColor = Color(0xFF94A3B8),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedContainerColor = Color(0x0AFFFFFF),
                                unfocusedContainerColor = Color(0x05FFFFFF)
                            ),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("login_mobile_input")
                        )

                        // Subtitle / helper text below input field
                        Text(
                            text = "A 6-digit verification OTP will be sent to your mobile number via SMS",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp, start = 4.dp)
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // Get OTP Button
                        val currentActivity = context.findActivity() ?: (context as? Activity)
                        Button(
                            onClick = {
                                if (currentActivity != null) {
                                    viewModel.sendOtp(
                                        mobileNumber = mobileNumberInput,
                                        activity = currentActivity
                                    )
                                } else if (context is Activity) {
                                    viewModel.sendOtp(
                                        mobileNumber = mobileNumberInput,
                                        activity = context
                                    )
                                }
                            },
                            enabled = mobileNumberInput.length == 10 && !viewModel.isSendingOtp,
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                            contentPadding = PaddingValues(),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("login_submit_button")
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.horizontalGradient(
                                            colors = listOf(
                                                Color(0xFF8B5CF6), // Electric Violet
                                                Color(0xFF10B981)  // Emerald Green
                                            )
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (viewModel.isSendingOtp) {
                                    PremiumLoadingState(text = "Sending OTP...")
                                } else {
                                    Text(
                                        text = "GET OTP",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        letterSpacing = 1.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // OR divider
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            HorizontalDivider(
                                modifier = Modifier.weight(1f),
                                color = Color(0x22FFFFFF)
                            )
                            Text(
                                text = "OR",
                                color = Color(0xFF64748B),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            HorizontalDivider(
                                modifier = Modifier.weight(1f),
                                color = Color(0x22FFFFFF)
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Official Google Sign-In Button
                        OutlinedButton(
                            onClick = {
                                coroutineScope.launch {
                                    viewModel.authError = null
                                    val credentialManager = CredentialManager.create(context)
                                    val googleIdOption = GetGoogleIdOption.Builder()
                                        .setFilterByAuthorizedAccounts(false)
                                        .setServerClientId(webClientId)
                                        .setAutoSelectEnabled(false)
                                        .build()

                                    val request = GetCredentialRequest.Builder()
                                        .addCredentialOption(googleIdOption)
                                        .build()

                                    try {
                                        val result = credentialManager.getCredential(context, request)
                                        val credential = result.credential
                                        
                                        if (credential is GoogleIdTokenCredential) {
                                            viewModel.signInWithGoogle(
                                                idToken = credential.idToken,
                                                email = credential.id,
                                                displayName = credential.displayName ?: "Google User",
                                                onNavigate = onNavigate
                                            )
                                        } else {
                                            Log.w("GoogleAuth", "Credential format unrecognized. Triggering legacy Google Sign-In client.")
                                            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                                                .requestIdToken(webClientId)
                                                .requestEmail()
                                                .build()
                                            val client = GoogleSignIn.getClient(context, gso)
                                            googleSignInLauncher.launch(client.signInIntent)
                                        }
                                    } catch (e: Exception) {
                                        Log.w("GoogleAuth", "CredentialManager exception (${e.javaClass.simpleName}): ${e.localizedMessage}. Launching Legacy GoogleSignIn fallback.")
                                        when (e) {
                                            is NoCredentialException, is GetCredentialException, is GetCredentialCustomException -> {
                                                try {
                                                    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                                                        .requestIdToken(webClientId)
                                                        .requestEmail()
                                                        .build()
                                                    val client = GoogleSignIn.getClient(context, gso)
                                                    googleSignInLauncher.launch(client.signInIntent)
                                                } catch (fallbackEx: Exception) {
                                                    Log.e("GoogleAuth", "Legacy GoogleSignIn fallback failed: ${fallbackEx.localizedMessage}")
                                                    viewModel.authError = "No Google accounts found on this device. Please add a Google account in system settings or sign in using Phone OTP."
                                                }
                                            }
                                            else -> {
                                                try {
                                                    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                                                        .requestIdToken(webClientId)
                                                        .requestEmail()
                                                        .build()
                                                    val client = GoogleSignIn.getClient(context, gso)
                                                    googleSignInLauncher.launch(client.signInIntent)
                                                } catch (fallbackEx: Exception) {
                                                    Log.e("GoogleAuth", "Fallback failed: ${fallbackEx.localizedMessage}")
                                                    viewModel.authError = "Google Sign-In failed: No credentials available on device."
                                                }
                                            }
                                        }
                                    }
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0x33FFFFFF)),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0x05FFFFFF)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("login_google_button")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_google_logo),
                                    contentDescription = "Google Logo",
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Continue with Google",
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Legal Consent Text
                        LegalConsentText()

                    } else {
                        // --- SCREEN 2: OTP Verification ---
                        
                        OutlinedTextField(
                            value = otpInput,
                            onValueChange = { input ->
                                if (input.all { it.isDigit() } && input.length <= 6) {
                                    otpInput = input
                                }
                            },
                            label = { Text("Enter 6-Digit OTP", color = Color(0xFF94A3B8)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "OTP Icon",
                                    tint = EmeraldGreen
                                )
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = EmeraldGreen,
                                unfocusedBorderColor = Color(0x33FFFFFF),
                                focusedLabelColor = EmeraldGreen,
                                unfocusedLabelColor = Color(0xFF94A3B8),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedContainerColor = Color(0x0AFFFFFF),
                                unfocusedContainerColor = Color(0x05FFFFFF)
                            ),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("login_otp_input")
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // Verify & Proceed Button
                        Button(
                            onClick = {
                                viewModel.verifyOtp(
                                    code = otpInput,
                                    onNavigate = onNavigate
                                )
                            },
                            enabled = otpInput.length == 6 && !viewModel.isVerifyingOtp,
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                            contentPadding = PaddingValues(),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("login_verify_button")
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.horizontalGradient(
                                            colors = listOf(
                                                Color(0xFF8B5CF6), // Electric Violet
                                                Color(0xFF10B981)  // Emerald Green
                                            )
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (viewModel.isVerifyingOtp) {
                                    PremiumLoadingState(text = "Verifying...")
                                } else {
                                    Text(
                                        text = "VERIFY OTP",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        letterSpacing = 1.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Resend Section
                        if (viewModel.timerSeconds > 0) {
                            Text(
                                text = "Resend OTP in ${viewModel.timerSeconds}s",
                                color = Color(0xFF64748B),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        } else {
                            val currentActivity = context.findActivity() ?: (context as? Activity)
                            Text(
                                text = "Resend OTP",
                                color = EmeraldGreen,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                modifier = Modifier
                                    .clickable {
                                        if (currentActivity != null) {
                                            viewModel.sendOtp(
                                                mobileNumber = mobileNumberInput,
                                                activity = currentActivity
                                            )
                                        } else if (context is Activity) {
                                            viewModel.sendOtp(
                                                mobileNumber = mobileNumberInput,
                                                activity = context
                                            )
                                        }
                                    }
                                    .padding(8.dp)
                                    .testTag("login_resend_otp_button")
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Change Mobile Link
                        Text(
                            text = "Change Mobile Number",
                            color = Color(0xFF94A3B8),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .clickable {
                                    viewModel.isOtpSent = false
                                    otpInput = ""
                                }
                                .padding(vertical = 4.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Legal Consent Text
                        LegalConsentText()
                    }
                }
            }
        }
    }
}

@Composable
fun LegalConsentText(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val termsTag = "TERMS"
    val privacyTag = "PRIVACY"

    val annotatedString = buildAnnotatedString {
        withStyle(style = SpanStyle(color = Color(0xFF94A3B8), fontSize = 12.sp)) {
            append("By continuing, you agree to our ")
        }
        pushStringAnnotation(tag = termsTag, annotation = WebUtils.TERMS_URL)
        withStyle(
            style = SpanStyle(
                color = Color(0xFF2DD4BF),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                textDecoration = TextDecoration.Underline
            )
        ) {
            append("Terms & Conditions")
        }
        pop()
        withStyle(style = SpanStyle(color = Color(0xFF94A3B8), fontSize = 12.sp)) {
            append(" and ")
        }
        pushStringAnnotation(tag = privacyTag, annotation = WebUtils.PRIVACY_URL)
        withStyle(
            style = SpanStyle(
                color = Color(0xFF2DD4BF),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                textDecoration = TextDecoration.Underline
            )
        ) {
            append("Privacy Policy")
        }
        pop()
    }

    ClickableText(
        text = annotatedString,
        style = TextStyle(
            textAlign = TextAlign.Center,
            fontSize = 12.sp,
            lineHeight = 16.sp
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .testTag("login_legal_consent_text"),
        onClick = { offset ->
            annotatedString.getStringAnnotations(tag = termsTag, start = offset, end = offset).firstOrNull()?.let {
                WebUtils.openWebUrl(context, it.item)
            }
            annotatedString.getStringAnnotations(tag = privacyTag, start = offset, end = offset).firstOrNull()?.let {
                WebUtils.openWebUrl(context, it.item)
            }
        }
    )
}

private fun android.content.Context.findActivity(): Activity? {
    var ctx: android.content.Context? = this
    while (ctx is android.content.ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
