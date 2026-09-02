package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.*
import com.example.update.AppUpdateInfo
import com.example.update.AppUpdateManagerHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppUpdateDialog(
    updateInfo: AppUpdateInfo,
    onUpdateNow: () -> Unit,
    onLater: () -> Unit
) {
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0f) }

    // Collect download state from helper if active
    val globalDownloading by AppUpdateManagerHelper.isDownloading.collectAsState()
    val globalProgress by AppUpdateManagerHelper.downloadProgress.collectAsState()

    val activeDownloading = isDownloading || globalDownloading
    val activeProgress = if (globalDownloading) globalProgress else downloadProgress

    Dialog(
        onDismissRequest = {
            // If it's a force update, block dismissal
            if (!updateInfo.isForceUpdate && !activeDownloading) {
                onLater()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = !updateInfo.isForceUpdate && !activeDownloading,
            dismissOnClickOutside = !updateInfo.isForceUpdate && !activeDownloading,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .padding(16.dp)
                .testTag("update_dialog_container"),
            shape = RoundedCornerShape(28.dp),
            color = Color(0xFF0F172A),
            tonalElevation = 16.dp,
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = if (updateInfo.isForceUpdate) listOf(RoseRed, GoldYellow) else listOf(ElectricViolet, EmeraldGreen)
                )
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Badge
                Surface(
                    color = if (updateInfo.isForceUpdate) Color(0x33EF4444) else Color(0x3310B981),
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (updateInfo.isForceUpdate) RoseRed else EmeraldGreen
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (updateInfo.isForceUpdate) Icons.Default.Warning else Icons.Default.NewReleases,
                            contentDescription = null,
                            tint = if (updateInfo.isForceUpdate) RoseRed else EmeraldGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (updateInfo.isForceUpdate) "MANDATORY UPDATE REQUIRED" else "NEW VERSION AVAILABLE 🚀",
                            color = if (updateInfo.isForceUpdate) RoseRed else EmeraldGreen,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // App Icon / Rocket Visual
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = if (updateInfo.isForceUpdate) listOf(Color(0xFFEF4444), Color(0xFFF59E0B)) else listOf(ElectricViolet, EmeraldGreen)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SystemUpdate,
                        contentDescription = "Update Icon",
                        tint = Color.White,
                        modifier = Modifier.size(38.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Smart POS v${updateInfo.latestVersionName}",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 22.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Current: v${updateInfo.currentVersionName} (Build ${updateInfo.currentVersionCode}) → New: v${updateInfo.latestVersionName} (Build ${updateInfo.latestVersionCode})",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Release Notes Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0x221E293B)),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = GoldYellow,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "What's New in this Update:",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        val notes = if (updateInfo.releaseNotes.isNotEmpty()) updateInfo.releaseNotes else listOf(
                            "Loose Tablet & Strip Billing for Pharmacies 💊",
                            "Razorpay & PhonePe UPI Autopay Gateway Integration 💳",
                            "GST B2B Billing & HSN Code Search Engine 🧾",
                            "Performance Enhancements & Bug Fixes ⚡"
                        )

                        notes.forEach { note ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = EmeraldGreen,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .padding(top = 2.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = note,
                                    color = Color(0xFFE2E8F0),
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Text(
                                text = "Download Size: ${updateInfo.fileSizeMb}",
                                color = Color(0xFF64748B),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Downloading Progress UI
                if (activeDownloading) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Downloading Update...", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text("${(activeProgress * 100).toInt()}%", color = EmeraldLight, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { activeProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = EmeraldGreen,
                            trackColor = Color(0x33FFFFFF)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Please keep the app open while we prepare the installer.",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    // Action Buttons
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                isDownloading = true
                                onUpdateNow()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("update_now_button"),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (updateInfo.isForceUpdate) RoseRed else EmeraldGreen
                            )
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Download, contentDescription = null, tint = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (updateInfo.isForceUpdate) "UPDATE NOW (MANDATORY)" else "UPDATE NOW",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }
                        }

                        // Show "Later" / "Remind Me Later" only if isForceUpdate is FALSE
                        if (!updateInfo.isForceUpdate) {
                            OutlinedButton(
                                onClick = onLater,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("update_later_button"),
                                shape = RoundedCornerShape(16.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x44FFFFFF)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                            ) {
                                Text("Remind Me Later", color = Color(0xFF94A3B8), fontSize = 14.sp)
                            }
                        } else {
                            Text(
                                text = "⚠️ This version includes critical security & database updates required to continue using Smart POS.",
                                color = RoseRed,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
