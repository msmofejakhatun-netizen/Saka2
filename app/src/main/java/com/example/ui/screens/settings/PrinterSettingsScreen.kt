package com.example.ui.screens.settings

import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.DarkSlateNavy
import com.example.ui.theme.DeepNavy
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.EmeraldLight
import com.example.ui.theme.GoldYellow
import com.example.ui.theme.LightSlateNavy
import com.example.util.BluetoothPermissionHandler
import com.example.util.PrinterManager
import kotlinx.coroutines.launch

/**
 * Dedicated Thermal Printer Settings & Bluetooth Discovery Screen.
 * Resolves POS-58 / MTP-2 printer discovery, Android 12+ runtime permissions,
 * paired device inspection, dynamic background discovery, and ESC/POS test printing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrinterSettingsScreen(
    businessName: String = "Smart POS Store",
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val pairedDevices by PrinterManager.pairedPrinters.collectAsState()
    val discoveredDevices by PrinterManager.discoveredPrinters.collectAsState()
    val isScanning by PrinterManager.isScanning.collectAsState()

    var selectedAddress by remember { mutableStateOf<String?>(null) }
    var selectedName by remember { mutableStateOf<String?>(null) }
    var paperWidthMm by remember { mutableIntStateOf(58) }
    var isGstMode by remember { mutableStateOf(true) }

    var hasPermissions by remember { mutableStateOf(BluetoothPermissionHandler.hasPermissions(context)) }
    var isBluetoothOn by remember { mutableStateOf(BluetoothPermissionHandler.isBluetoothEnabled(context)) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isTestingPrint by remember { mutableStateOf(false) }

    // Load saved configurations from SharedPreferences
    LaunchedEffect(Unit) {
        val saved = PrinterManager.getSavedConfig(context)
        if (saved != null) {
            selectedAddress = saved.address
            selectedName = saved.name
            paperWidthMm = saved.paperWidthMm
            isGstMode = saved.isGstMode
        }
    }

    // Permission launcher for Bluetooth Scan, Connect, and Location
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val allGranted = result.values.all { it }
        hasPermissions = BluetoothPermissionHandler.hasPermissions(context)
        isBluetoothOn = BluetoothPermissionHandler.isBluetoothEnabled(context)
        if (hasPermissions) {
            PrinterManager.loadPairedDevices(context)
            if (selectedAddress == null) {
                val firstPaired = PrinterManager.pairedPrinters.value.firstOrNull()
                if (firstPaired != null) {
                    selectedAddress = firstPaired.address
                    selectedName = firstPaired.name
                }
            }
        } else {
            statusMessage = "Bluetooth & Location permissions are required to detect POS thermal printers."
        }
    }

    val enableBtLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        isBluetoothOn = BluetoothPermissionHandler.isBluetoothEnabled(context)
        if (isBluetoothOn) {
            PrinterManager.loadPairedDevices(context)
        }
    }

    // Initial load
    LaunchedEffect(Unit) {
        if (!hasPermissions) {
            permissionLauncher.launch(BluetoothPermissionHandler.getRequiredPermissions())
        } else {
            PrinterManager.loadPairedDevices(context)
        }
    }

    // Stop scan when leaving screen
    DisposableEffect(Unit) {
        onDispose {
            PrinterManager.stopDiscovery(context)
        }
    }

    // Animated scanner pulse
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Print,
                            contentDescription = "Thermal Printer",
                            tint = EmeraldGreen,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Thermal Printer Setup",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Bluetooth 58mm / 80mm ESC/POS Engine",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("printer_settings_back_btn")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            isBluetoothOn = BluetoothPermissionHandler.isBluetoothEnabled(context)
                            if (!hasPermissions) {
                                permissionLauncher.launch(BluetoothPermissionHandler.getRequiredPermissions())
                            } else {
                                PrinterManager.loadPairedDevices(context)
                                Toast.makeText(context, "Printers refreshed", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.testTag("refresh_printer_settings_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = EmeraldLight
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepNavy)
            )
        },
        containerColor = DeepNavy
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            // 1. Permission Warning Banner if missing
            if (!hasPermissions) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0x33EF4444)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0x88EF4444), RoundedCornerShape(12.dp))
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = "Permission Alert", tint = Color(0xFFF87171))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Bluetooth Permissions Required", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(
                                    "Android 12+ requires Bluetooth Scan & Connect permissions to communicate with thermal printers.",
                                    color = Color(0xFFCBD5E1),
                                    fontSize = 11.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = { permissionLauncher.launch(BluetoothPermissionHandler.getRequiredPermissions()) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.testTag("grant_bt_permissions_btn")
                                ) {
                                    Text("Grant Permissions", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // 2. Bluetooth Disabled Banner if off
            if (hasPermissions && !isBluetoothOn) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0x33F59E0B)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0x88F59E0B), RoundedCornerShape(12.dp))
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Bluetooth, contentDescription = "Bluetooth Off", tint = GoldYellow)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Bluetooth is Turned OFF", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(
                                    "Turn ON Bluetooth to discover POS-58, MTP-2, and wireless receipt printers.",
                                    color = Color(0xFFCBD5E1),
                                    fontSize = 11.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = { enableBtLauncher.launch(BluetoothPermissionHandler.getEnableBluetoothIntent()) },
                                    colors = ButtonDefaults.buttonColors(containerColor = GoldYellow),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.testTag("turn_on_bt_btn")
                                ) {
                                    Text("Turn ON Bluetooth", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // 3. Selected Active Printer Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = LightSlateNavy),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0x3310B981), RoundedCornerShape(14.dp))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(Color(0x2210B981), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.BluetoothConnected,
                                        contentDescription = "Active Printer",
                                        tint = EmeraldGreen,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Active Receipt Printer",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = selectedName ?: "No Printer Configured",
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            if (selectedAddress != null) {
                                Surface(
                                    color = Color(0x3310B981),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "CONFIGURED",
                                        color = EmeraldLight,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }

                        if (selectedAddress != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "MAC Address: $selectedAddress",
                                color = Color(0xFF64748B),
                                fontSize = 11.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Configuration Row: Width + Mode
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = paperWidthMm == 58,
                                onClick = {
                                    paperWidthMm = 58
                                    selectedAddress?.let { addr ->
                                        PrinterManager.saveConfig(context, addr, selectedName ?: "Thermal Printer", 58, isGstMode)
                                    }
                                },
                                label = { Text("58mm (2-inch POS)", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = EmeraldGreen,
                                    selectedLabelColor = Color.White
                                ),
                                modifier = Modifier.weight(1f).testTag("printer_chip_58mm")
                            )

                            FilterChip(
                                selected = paperWidthMm == 80,
                                onClick = {
                                    paperWidthMm = 80
                                    selectedAddress?.let { addr ->
                                        PrinterManager.saveConfig(context, addr, selectedName ?: "Thermal Printer", 80, isGstMode)
                                    }
                                },
                                label = { Text("80mm (3-inch Wide)", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = EmeraldGreen,
                                    selectedLabelColor = Color.White
                                ),
                                modifier = Modifier.weight(1f).testTag("printer_chip_80mm")
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Test Print Action Button
                        Button(
                            onClick = {
                                val targetAddr = selectedAddress
                                if (targetAddr.isNullOrBlank()) {
                                    statusMessage = "Please select or pair a printer below first"
                                } else {
                                    isTestingPrint = true
                                    coroutineScope.launch {
                                        PrinterManager.printTestPage(
                                            context = context,
                                            deviceAddress = targetAddr,
                                            businessName = businessName,
                                            paperWidthMm = paperWidthMm,
                                            onStatus = { statusMessage = it }
                                        )
                                        isTestingPrint = false
                                    }
                                }
                            },
                            enabled = !isTestingPrint && selectedAddress != null,
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("test_print_main_btn")
                        ) {
                            if (isTestingPrint) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Sending Test ESC/POS Commands...", fontSize = 12.sp)
                            } else {
                                Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Perform Test Print (Align & Feed)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Status Banner if any
            statusMessage?.let { msg ->
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0x2210B981)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0x4410B981), RoundedCornerShape(10.dp))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = EmeraldLight, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = msg,
                                color = EmeraldLight,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // 4. Paired Printers Section Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "PAIRED BLUETOOTH PRINTERS",
                            color = Color(0xFF64748B),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "${pairedDevices.size} paired devices found",
                            color = Color(0xFF94A3B8),
                            fontSize = 10.sp
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            if (!isScanning) {
                                if (!hasPermissions) {
                                    permissionLauncher.launch(BluetoothPermissionHandler.getRequiredPermissions())
                                } else {
                                    PrinterManager.startDiscovery(context)
                                }
                            } else {
                                PrinterManager.stopDiscovery(context)
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("scan_new_devices_btn")
                    ) {
                        if (isScanning) {
                            Icon(
                                Icons.Default.BluetoothSearching,
                                contentDescription = "Scanning",
                                tint = EmeraldLight,
                                modifier = Modifier
                                    .size(16.dp)
                                    .alpha(pulseAlpha)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Stop Scan", color = EmeraldLight, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.BluetoothSearching, contentDescription = "Scan", tint = EmeraldLight, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Scan Nearby", color = EmeraldLight, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Paired Printers List
            if (pairedDevices.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0x1F1E295D)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(12.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bluetooth,
                                contentDescription = null,
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No Paired Thermal Printers Found",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "1. Turn on POS-58 / MTP-2 printer.\n2. Open Android Bluetooth Settings & pair using PIN 0000 or 1234.\n3. Or tap 'Scan Nearby' below to discover ready devices.",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            } else {
                items(pairedDevices) { device ->
                    val isSelected = device.address == selectedAddress
                    PrinterDeviceCard(
                        device = device,
                        isSelected = isSelected,
                        onSelect = {
                            selectedAddress = device.address
                            selectedName = device.name
                            PrinterManager.saveConfig(
                                context = context,
                                deviceAddress = device.address,
                                deviceName = device.name,
                                paperWidthMm = paperWidthMm,
                                isGstMode = isGstMode
                            )
                            Toast.makeText(context, "Saved ${device.name} as active printer", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }

            // 5. Discovered / Nearby Bluetooth Devices Section
            item {
                AnimatedVisibility(visible = isScanning || discoveredDevices.isNotEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isScanning) {
                                    CircularProgressIndicator(
                                        color = EmeraldGreen,
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text(
                                    text = if (isScanning) "SCANNING NEARBY PRINTERS..." else "DISCOVERED NEARBY DEVICES",
                                    color = EmeraldLight,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                            }

                            Text(
                                text = "${discoveredDevices.size} detected",
                                color = Color(0xFF94A3B8),
                                fontSize = 10.sp
                            )
                        }

                        if (discoveredDevices.isEmpty() && isScanning) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0x1110B981)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, Color(0x3310B981), RoundedCornerShape(10.dp))
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.BluetoothSearching,
                                        contentDescription = null,
                                        tint = EmeraldLight,
                                        modifier = Modifier.size(20.dp).alpha(pulseAlpha)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "Listening for Bluetooth broadcasts from POS-58, MTP, RP printers...",
                                        color = Color(0xFFCBD5E1),
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }

                        discoveredDevices.forEach { device ->
                            val isSelected = device.address == selectedAddress
                            PrinterDeviceCard(
                                device = device,
                                isSelected = isSelected,
                                onSelect = {
                                    selectedAddress = device.address
                                    selectedName = device.name
                                    PrinterManager.saveConfig(
                                        context = context,
                                        deviceAddress = device.address,
                                        deviceName = device.name,
                                        paperWidthMm = paperWidthMm,
                                        isGstMode = isGstMode
                                    )
                                    Toast.makeText(context, "Selected ${device.name}", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun PrinterDeviceCard(
    device: PrinterManager.PrinterDevice,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Card(
        onClick = onSelect,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0x3310B981) else LightSlateNavy
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (isSelected) EmeraldGreen else Color(0x22FFFFFF),
                RoundedCornerShape(12.dp)
            )
            .testTag("printer_item_${device.address}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            if (device.isLikelyThermalPrinter) Color(0x2210B981) else Color(0x2264748B),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (device.isLikelyThermalPrinter) Icons.Default.Print else Icons.Default.Bluetooth,
                        contentDescription = null,
                        tint = if (device.isLikelyThermalPrinter) EmeraldLight else Color(0xFF94A3B8),
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = device.name,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        if (device.isLikelyThermalPrinter) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = Color(0x3310B981),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "POS THERMAL",
                                    color = EmeraldLight,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = device.address,
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                }
            }

            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = EmeraldGreen,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                OutlinedButton(
                    onClick = onSelect,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("Select", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
