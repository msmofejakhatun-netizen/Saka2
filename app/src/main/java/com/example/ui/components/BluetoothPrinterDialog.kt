package com.example.ui.components

import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.InvoiceEntity
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.EmeraldLight
import com.example.ui.theme.GoldYellow
import com.example.util.BluetoothPermissionHandler
import com.example.util.PrinterManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BluetoothPrinterDialog(
    invoice: InvoiceEntity? = null,
    businessName: String,
    upiId: String = "merchant@upi",
    isGstModeInitial: Boolean = true,
    onGstModeToggle: ((Boolean) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val pairedPrinters by PrinterManager.pairedPrinters.collectAsState()
    val discoveredPrinters by PrinterManager.discoveredPrinters.collectAsState()
    val isScanning by PrinterManager.isScanning.collectAsState()

    var selectedPrinterAddress by remember { mutableStateOf<String?>(null) }
    var selectedPrinterName by remember { mutableStateOf<String?>(null) }
    var paperWidthMm by remember { mutableIntStateOf(58) }
    var isGstMode by remember { mutableStateOf(isGstModeInitial) }
    var printStatus by remember { mutableStateOf<String?>(null) }
    var isPrinting by remember { mutableStateOf(false) }

    var hasBtPermission by remember {
        mutableStateOf(BluetoothPermissionHandler.hasPermissions(context))
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasBtPermission = BluetoothPermissionHandler.hasPermissions(context)
        if (hasBtPermission) {
            PrinterManager.loadPairedDevices(context)
            if (selectedPrinterAddress == null) {
                val first = PrinterManager.pairedPrinters.value.firstOrNull()
                if (first != null) {
                    selectedPrinterAddress = first.address
                    selectedPrinterName = first.name
                }
            }
        } else {
            printStatus = "⚠️ Bluetooth permissions required to connect to printer"
        }
    }

    // Load saved preferences
    LaunchedEffect(Unit) {
        val saved = PrinterManager.getSavedConfig(context)
        if (saved != null) {
            selectedPrinterAddress = saved.address
            selectedPrinterName = saved.name
            paperWidthMm = saved.paperWidthMm
            isGstMode = saved.isGstMode
        }

        if (!hasBtPermission) {
            permissionLauncher.launch(BluetoothPermissionHandler.getRequiredPermissions())
        } else {
            val list = PrinterManager.loadPairedDevices(context)
            if (selectedPrinterAddress == null && list.isNotEmpty()) {
                selectedPrinterAddress = list.first().address
                selectedPrinterName = list.first().name
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            PrinterManager.stopDiscovery(context)
        }
    }

    AlertDialog(
        onDismissRequest = {
            PrinterManager.stopDiscovery(context)
            onDismiss()
        },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0x2210B981), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.Default.Print, contentDescription = "Printer", tint = EmeraldGreen, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("Bluetooth Thermal Print", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("POS-58 / MTP-2 ESC/POS Engine", color = Color(0xFF94A3B8), fontSize = 11.sp)
                    }
                }

                IconButton(
                    onClick = {
                        PrinterManager.stopDiscovery(context)
                        onDismiss()
                    },
                    modifier = Modifier.testTag("bluetooth_printer_close_btn")
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 1. Paper Width & GST Settings Row
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0x1F1E295D)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(12.dp))
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isGstMode) "🔴 GST Tax Invoice Mode" else "🟢 Simple Cash Memo (Non-GST)",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = if (isGstMode) "Prints GSTIN & tax breakdown" else "Prints clean estimate without tax",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 9.sp
                                )
                            }
                            Switch(
                                checked = isGstMode,
                                onCheckedChange = {
                                    isGstMode = it
                                    onGstModeToggle?.invoke(it)
                                    selectedPrinterAddress?.let { addr ->
                                        PrinterManager.saveConfig(context, addr, selectedPrinterName ?: "Thermal Printer", paperWidthMm, it)
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = EmeraldGreen,
                                    checkedTrackColor = Color(0x4410B981)
                                ),
                                modifier = Modifier.testTag("printer_dialog_gst_toggle")
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = paperWidthMm == 58,
                                onClick = {
                                    paperWidthMm = 58
                                    selectedPrinterAddress?.let { addr ->
                                        PrinterManager.saveConfig(context, addr, selectedPrinterName ?: "Thermal Printer", 58, isGstMode)
                                    }
                                },
                                label = { Text("58mm (2-inch POS)", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = EmeraldGreen,
                                    selectedLabelColor = Color.White
                                ),
                                modifier = Modifier.weight(1f).testTag("printer_paper_58mm")
                            )
                            FilterChip(
                                selected = paperWidthMm == 80,
                                onClick = {
                                    paperWidthMm = 80
                                    selectedPrinterAddress?.let { addr ->
                                        PrinterManager.saveConfig(context, addr, selectedPrinterName ?: "Thermal Printer", 80, isGstMode)
                                    }
                                },
                                label = { Text("80mm (3-inch Wide)", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = EmeraldGreen,
                                    selectedLabelColor = Color.White
                                ),
                                modifier = Modifier.weight(1f).testTag("printer_paper_80mm")
                            )
                        }
                    }
                }

                // 2. Paired & Discovered Printers Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Select Thermal Printer", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(
                            onClick = {
                                if (isScanning) {
                                    PrinterManager.stopDiscovery(context)
                                } else {
                                    if (!hasBtPermission) {
                                        permissionLauncher.launch(BluetoothPermissionHandler.getRequiredPermissions())
                                    } else {
                                        PrinterManager.startDiscovery(context)
                                    }
                                }
                            },
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.height(28.dp).testTag("dialog_scan_nearby_btn")
                        ) {
                            Text(if (isScanning) "Stop Scan" else "Scan Nearby", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }

                        IconButton(
                            onClick = {
                                PrinterManager.loadPairedDevices(context)
                            },
                            modifier = Modifier.size(28.dp).testTag("refresh_printers_btn")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = EmeraldLight, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                // Combined Printer List (Paired + Discovered)
                val allPrinters = remember(pairedPrinters, discoveredPrinters) {
                    val list = mutableListOf<PrinterManager.PrinterDevice>()
                    list.addAll(pairedPrinters)
                    discoveredPrinters.forEach { discovered ->
                        if (list.none { it.address == discovered.address }) {
                            list.add(discovered)
                        }
                    }
                    list
                }

                if (allPrinters.isEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0x22EF4444)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().border(1.dp, Color(0x44EF4444), RoundedCornerShape(10.dp))
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("⚠️ No Bluetooth printers found", color = Color(0xFFF87171), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                "1. Ensure printer is ON & in pairing mode.\n2. Tap 'Scan Nearby' above to discover devices.\n3. Or pair via Android Bluetooth Settings (PIN: 0000/1234).",
                                color = Color.White,
                                fontSize = 10.sp
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.height(130.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(allPrinters) { printer ->
                            val isSelected = printer.address == selectedPrinterAddress
                            Card(
                                onClick = {
                                    selectedPrinterAddress = printer.address
                                    selectedPrinterName = printer.name
                                    PrinterManager.saveConfig(
                                        context = context,
                                        deviceAddress = printer.address,
                                        deviceName = printer.name,
                                        paperWidthMm = paperWidthMm,
                                        isGstMode = isGstMode
                                    )
                                },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) Color(0x3310B981) else Color(0x11FFFFFF)
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        1.dp,
                                        if (isSelected) EmeraldGreen else Color(0x22FFFFFF),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .testTag("printer_device_${printer.address}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = if (isSelected) Icons.Default.BluetoothConnected else if (printer.isLikelyThermalPrinter) Icons.Default.Print else Icons.Default.Bluetooth,
                                            contentDescription = null,
                                            tint = if (isSelected) EmeraldLight else Color(0xFF94A3B8),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(printer.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                                if (printer.isLikelyThermalPrinter) {
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Surface(color = Color(0x3310B981), shape = RoundedCornerShape(3.dp)) {
                                                        Text("POS", color = EmeraldLight, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 3.dp))
                                                    }
                                                }
                                            }
                                            Text(printer.address, color = Color(0xFF94A3B8), fontSize = 9.sp)
                                        }
                                    }

                                    if (isSelected) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = "Selected", tint = EmeraldGreen, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                // Status Message Box
                printStatus?.let { msg ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0x2210B981)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().border(1.dp, Color(0x4410B981), RoundedCornerShape(8.dp))
                    ) {
                        Text(
                            text = msg,
                            color = EmeraldLight,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(6.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Test Print Button
                OutlinedButton(
                    onClick = {
                        val targetAddr = selectedPrinterAddress ?: allPrintersList(pairedPrinters, discoveredPrinters).firstOrNull()?.address
                        if (targetAddr == null) {
                            printStatus = "Please select a printer first"
                        } else {
                            isPrinting = true
                            scope.launch {
                                PrinterManager.printTestPage(
                                    context = context,
                                    deviceAddress = targetAddr,
                                    businessName = businessName,
                                    paperWidthMm = paperWidthMm,
                                    onStatus = { printStatus = it }
                                )
                                isPrinting = false
                            }
                        }
                    },
                    enabled = !isPrinting && (selectedPrinterAddress != null || pairedPrinters.isNotEmpty() || discoveredPrinters.isNotEmpty()),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f).testTag("test_print_btn")
                ) {
                    Text("Test Print", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                // Print Invoice Button
                if (invoice != null) {
                    Button(
                        onClick = {
                            val targetAddr = selectedPrinterAddress ?: allPrintersList(pairedPrinters, discoveredPrinters).firstOrNull()?.address
                            if (targetAddr == null) {
                                printStatus = "Please select a Bluetooth printer"
                            } else {
                                isPrinting = true
                                scope.launch {
                                    PrinterManager.printInvoiceReceipt(
                                        context = context,
                                        deviceAddress = targetAddr,
                                        invoice = invoice,
                                        businessName = businessName,
                                        upiId = upiId,
                                        paperWidthMm = paperWidthMm,
                                        isGstMode = isGstMode,
                                        onStatus = { printStatus = it }
                                    )
                                    isPrinting = false
                                }
                            }
                        },
                        enabled = !isPrinting && (selectedPrinterAddress != null || pairedPrinters.isNotEmpty() || discoveredPrinters.isNotEmpty()),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1.3f).testTag("print_invoice_receipt_btn")
                    ) {
                        if (isPrinting) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Printing...", fontSize = 11.sp)
                        } else {
                            Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Print Thermal Bill", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        containerColor = Color(0xFF0F172A),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.border(1.dp, Color(0x3310B981), RoundedCornerShape(20.dp))
    )
}

private fun allPrintersList(
    paired: List<PrinterManager.PrinterDevice>,
    discovered: List<PrinterManager.PrinterDevice>
): List<PrinterManager.PrinterDevice> {
    val list = mutableListOf<PrinterManager.PrinterDevice>()
    list.addAll(paired)
    discovered.forEach { d ->
        if (list.none { it.address == d.address }) list.add(d)
    }
    return list
}
