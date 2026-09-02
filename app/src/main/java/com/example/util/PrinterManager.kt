package com.example.util

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import com.example.data.db.InvoiceEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Universal Bluetooth Thermal Printer Manager supporting POS-58, MTP-2, 80mm ESC/POS printers,
 * paired device lookup, runtime scanning, and robust RFCOMM socket communication.
 */
object PrinterManager {

    private const val TAG = "PrinterManager"
    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private const val PREFS_NAME = "smartpos_printer_prefs"
    private const val KEY_SAVED_PRINTER_ADDR = "saved_printer_address"
    private const val KEY_SAVED_PRINTER_NAME = "saved_printer_name"
    private const val KEY_SAVED_PAPER_WIDTH = "saved_printer_paper_width"
    private const val KEY_SAVED_GST_MODE = "saved_printer_gst_mode"

    // ESC/POS Commands
    private val INIT_PRINTER = byteArrayOf(0x1B, 0x40)
    private val ALIGN_LEFT = byteArrayOf(0x1B, 0x61, 0x00)
    private val ALIGN_CENTER = byteArrayOf(0x1B, 0x61, 0x01)
    private val ALIGN_RIGHT = byteArrayOf(0x1B, 0x61, 0x02)
    private val BOLD_ON = byteArrayOf(0x1B, 0x45, 0x01)
    private val BOLD_OFF = byteArrayOf(0x1B, 0x45, 0x00)
    private val DOUBLE_SIZE = byteArrayOf(0x1D, 0x21, 0x11)
    private val NORMAL_SIZE = byteArrayOf(0x1D, 0x21, 0x00)
    private val FEED_LINE = byteArrayOf(0x0A)
    private val CUT_PAPER = byteArrayOf(0x1D, 0x56, 0x41, 0x10)

    data class PrinterDevice(
        val name: String,
        val address: String,
        val isPaired: Boolean,
        val isLikelyThermalPrinter: Boolean,
        val deviceClassDescription: String = "Thermal Printer"
    )

    data class SavedPrinterConfig(
        val name: String,
        val address: String,
        val paperWidthMm: Int,
        val isGstMode: Boolean
    )

    // State flows for dynamic UI binding
    private val _pairedPrinters = MutableStateFlow<List<PrinterDevice>>(emptyList())
    val pairedPrinters: StateFlow<List<PrinterDevice>> = _pairedPrinters.asStateFlow()

    private val _discoveredPrinters = MutableStateFlow<List<PrinterDevice>>(emptyList())
    val discoveredPrinters: StateFlow<List<PrinterDevice>> = _discoveredPrinters.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _lastStatusMessage = MutableStateFlow<String?>(null)
    val lastStatusMessage: StateFlow<String?> = _lastStatusMessage.asStateFlow()

    private var discoveryReceiver: BroadcastReceiver? = null

    /**
     * Common thermal printer brand and model naming patterns for intelligent categorization.
     */
    private val PRINTER_KEYWORDS = listOf(
        "POS", "MTP", "RP", "XP", "PRINTER", "THERMAL", "MPT", "RPP", "QS",
        "58", "80", "BT-", "INNER", "EPOS", "RECEIPT", "GOOJPRT", "ZEBRA",
        "XPRINTER", "NETUM", "HOIN", "SUNMI", "D1", "V2", "MINIPRINTER",
        "BLUE_TOOTH", "ESC", "P25", "P58", "P80", "Q2", "ZJ", "PT-", "BLUETOOTH",
        "BILL", "PRINT", "CT-", "SEWOO", "BIXOLON", "EPSON", "EVERYCOM"
    )

    /**
     * Determines whether a Bluetooth device is likely a thermal printer.
     */
    @SuppressLint("MissingPermission")
    fun isLikelyThermalPrinter(device: BluetoothDevice): Boolean {
        return try {
            val name = (device.name ?: "").uppercase(Locale.ROOT)
            val btClass = device.bluetoothClass

            val matchesName = PRINTER_KEYWORDS.any { keyword -> name.contains(keyword) }
            val isImagingClass = btClass?.majorDeviceClass == BluetoothClass.Device.Major.IMAGING
            val isUncategorized = btClass?.majorDeviceClass == BluetoothClass.Device.Major.UNCATEGORIZED
            val hasRenderService = btClass?.hasService(BluetoothClass.Service.RENDER) == true

            matchesName || isImagingClass || (isUncategorized && name.isNotEmpty()) || hasRenderService
        } catch (e: Exception) {
            true // Fallback to permissive inclusion
        }
    }

    /**
     * Fetches paired Bluetooth devices filtered with smart categorization.
     */
    @SuppressLint("MissingPermission")
    fun loadPairedDevices(context: Context): List<PrinterDevice> {
        val adapter = BluetoothPermissionHandler.getBluetoothAdapter(context) ?: run {
            _pairedPrinters.value = emptyList()
            return emptyList()
        }

        if (!BluetoothPermissionHandler.hasConnectPermission(context) || !adapter.isEnabled) {
            _pairedPrinters.value = emptyList()
            return emptyList()
        }

        return try {
            val bonded = adapter.bondedDevices ?: emptySet()
            val list = bonded.map { device ->
                val name = device.name ?: "Unknown Bluetooth Device"
                val isPrinter = isLikelyThermalPrinter(device)
                PrinterDevice(
                    name = name,
                    address = device.address,
                    isPaired = true,
                    isLikelyThermalPrinter = isPrinter,
                    deviceClassDescription = if (isPrinter) "Thermal Printer" else "Bluetooth Device"
                )
            }.sortedWith(
                compareByDescending<PrinterDevice> { it.isLikelyThermalPrinter }
                    .thenBy { it.name }
            )

            _pairedPrinters.value = list
            list
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException loading paired devices: ${e.message}")
            _pairedPrinters.value = emptyList()
            emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error loading paired devices: ${e.message}")
            _pairedPrinters.value = emptyList()
            emptyList()
        }
    }

    /**
     * Starts active Bluetooth Discovery to find nearby un-paired and pairing-ready thermal printers.
     */
    @SuppressLint("MissingPermission")
    fun startDiscovery(context: Context) {
        val adapter = BluetoothPermissionHandler.getBluetoothAdapter(context) ?: return
        if (!BluetoothPermissionHandler.hasPermissions(context) || !adapter.isEnabled) {
            _lastStatusMessage.value = "Bluetooth is disabled or permissions not granted"
            return
        }

        stopDiscovery(context)
        _discoveredPrinters.value = emptyList()
        _isScanning.value = true

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        }

        discoveryReceiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                when (intent?.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        }

                        device?.let { dev ->
                            try {
                                val devName = dev.name ?: "Unknown Device"
                                val devAddr = dev.address ?: return@let
                                val isPrinter = isLikelyThermalPrinter(dev)

                                val currentList = _discoveredPrinters.value.toMutableList()
                                if (currentList.none { it.address == devAddr }) {
                                    val isPaired = dev.bondState == BluetoothDevice.BOND_BONDED
                                    currentList.add(
                                        PrinterDevice(
                                            name = devName,
                                            address = devAddr,
                                            isPaired = isPaired,
                                            isLikelyThermalPrinter = isPrinter,
                                            deviceClassDescription = if (isPrinter) "Thermal Printer" else "Bluetooth Device"
                                        )
                                    )
                                    _discoveredPrinters.value = currentList.sortedWith(
                                        compareByDescending<PrinterDevice> { it.isLikelyThermalPrinter }
                                            .thenBy { it.name }
                                    )
                                }
                            } catch (e: SecurityException) {
                                Log.e(TAG, "SecurityException in ACTION_FOUND: ${e.message}")
                            }
                        }
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        _isScanning.value = false
                    }
                    BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                        loadPairedDevices(context)
                    }
                }
            }
        }

        try {
            context.registerReceiver(discoveryReceiver, filter)
            if (adapter.isDiscovering) {
                adapter.cancelDiscovery()
            }
            adapter.startDiscovery()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Bluetooth discovery: ${e.message}")
            _isScanning.value = false
        }
    }

    /**
     * Stops active Bluetooth discovery and unregisters the receiver.
     */
    @SuppressLint("MissingPermission")
    fun stopDiscovery(context: Context) {
        val adapter = BluetoothPermissionHandler.getBluetoothAdapter(context)
        try {
            if (adapter != null && BluetoothPermissionHandler.hasConnectPermission(context) && adapter.isDiscovering) {
                adapter.cancelDiscovery()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling discovery: ${e.message}")
        }

        discoveryReceiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (_: Exception) {}
            discoveryReceiver = null
        }
        _isScanning.value = false
    }

    /**
     * Connects to a Bluetooth Thermal Printer using standard SPP RFCOMM UUID with reflection fallback.
     */
    @SuppressLint("MissingPermission")
    suspend fun openSocket(
        context: Context,
        deviceAddress: String
    ): BluetoothSocket? = withContext(Dispatchers.IO) {
        val adapter = BluetoothPermissionHandler.getBluetoothAdapter(context) ?: return@withContext null
        if (!adapter.isEnabled) return@withContext null

        try {
            if (BluetoothPermissionHandler.hasConnectPermission(context) && adapter.isDiscovering) {
                adapter.cancelDiscovery()
            }
        } catch (_: Exception) {}

        val device = try {
            adapter.getRemoteDevice(deviceAddress)
        } catch (e: Exception) {
            Log.e(TAG, "Invalid Bluetooth address $deviceAddress: ${e.message}")
            return@withContext null
        }

        var socket: BluetoothSocket? = null

        // 1. Standard RFCOMM SDP Connection
        try {
            socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            socket.connect()
            Log.d(TAG, "Connected via standard RFCOMM SPP socket")
            return@withContext socket
        } catch (e: Exception) {
            Log.w(TAG, "Standard RFCOMM connection failed (${e.message}), attempting fallback reflection socket...")
            try {
                socket?.close()
            } catch (_: Exception) {}
        }

        // 2. Reflection Fallback (Channel 1) for POS thermal printers with non-standard SDP profiles
        try {
            val method = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
            socket = method.invoke(device, 1) as BluetoothSocket
            socket.connect()
            Log.d(TAG, "Connected via reflection fallback socket (channel 1)")
            return@withContext socket
        } catch (e: Exception) {
            Log.e(TAG, "Fallback RFCOMM connection failed: ${e.message}")
            try {
                socket?.close()
            } catch (_: Exception) {}
            return@withContext null
        }
    }

    /**
     * Prints an instant Test Slip to verify ESC/POS connection, character alignment, and paper feeding.
     */
    suspend fun printTestPage(
        context: Context,
        deviceAddress: String,
        businessName: String = "Smart POS Kirana Store",
        paperWidthMm: Int = 58,
        onStatus: (String) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        withContext(Dispatchers.Main) { onStatus("Connecting to printer ($deviceAddress)...") }

        val socket = openSocket(context, deviceAddress)
        if (socket == null) {
            withContext(Dispatchers.Main) { onStatus("Connection Failed! Ensure printer is powered ON and paired.") }
            return@withContext false
        }

        var outputStream: OutputStream? = null
        try {
            outputStream = socket.outputStream
            val lineLen = if (paperWidthMm == 80) 48 else 32

            val baos = ByteArrayOutputStream()
            baos.write(INIT_PRINTER)
            baos.write(ALIGN_CENTER)
            baos.write(DOUBLE_SIZE)
            baos.write(BOLD_ON)
            baos.write("${businessName.uppercase(Locale.ROOT)}\n".toByteArray(Charsets.UTF_8))
            baos.write(NORMAL_SIZE)
            baos.write(BOLD_OFF)
            baos.write("THERMAL PRINTER TEST OK\n".toByteArray(Charsets.UTF_8))
            baos.write("${"=".repeat(lineLen)}\n".toByteArray(Charsets.UTF_8))
            baos.write(ALIGN_LEFT)
            baos.write("Paper Mode : ${paperWidthMm}mm ($lineLen columns)\n".toByteArray(Charsets.UTF_8))
            baos.write("Protocol   : ESC/POS Direct Socket\n".toByteArray(Charsets.UTF_8))
            baos.write("Timestamp  : ${SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())}\n".toByteArray(Charsets.UTF_8))
            baos.write("Status     : Connected & Ready\n".toByteArray(Charsets.UTF_8))
            baos.write("${"-".repeat(lineLen)}\n".toByteArray(Charsets.UTF_8))
            baos.write(ALIGN_CENTER)
            baos.write(BOLD_ON)
            baos.write("Align Left  |  Center  |  Right\n".toByteArray(Charsets.UTF_8))
            baos.write(BOLD_OFF)
            baos.write("${"=".repeat(lineLen)}\n".toByteArray(Charsets.UTF_8))
            baos.write(ALIGN_CENTER)
            baos.write("Smart POS - Fast Billing Engine\n".toByteArray(Charsets.UTF_8))
            baos.write(byteArrayOf(0x1B, 0x64, 0x03)) // Feed 3 lines
            baos.write(CUT_PAPER)

            withContext(Dispatchers.Main) { onStatus("Sending test receipt data...") }
            outputStream.write(baos.toByteArray())
            outputStream.flush()

            withContext(Dispatchers.Main) { onStatus("✅ Test Print Success! Paper feed verified.") }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error during test print: ${e.message}", e)
            withContext(Dispatchers.Main) { onStatus("Print Error: ${e.localizedMessage ?: "Transmission failed"}") }
            false
        } finally {
            try {
                outputStream?.close()
                socket.close()
            } catch (_: Exception) {}
        }
    }

    /**
     * Prints complete sale invoice with items, GST/Estimate calculation, and UPI QR code.
     */
    suspend fun printInvoiceReceipt(
        context: Context,
        deviceAddress: String,
        invoice: InvoiceEntity,
        businessName: String,
        upiId: String = "merchant@upi",
        paperWidthMm: Int = 58,
        isGstMode: Boolean = true,
        onStatus: (String) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        withContext(Dispatchers.Main) { onStatus("Connecting to $deviceAddress...") }

        val socket = openSocket(context, deviceAddress)
        if (socket == null) {
            withContext(Dispatchers.Main) { onStatus("Failed to connect to printer. Check Bluetooth.") }
            return@withContext false
        }

        var outputStream: OutputStream? = null
        try {
            outputStream = socket.outputStream
            withContext(Dispatchers.Main) { onStatus("Formatting invoice ESC/POS data...") }

            val bytes = ReceiptPrintHelper.printReceiptBytes(
                invoice = invoice,
                businessName = businessName,
                upiId = upiId,
                paperWidthMm = paperWidthMm,
                isGstMode = isGstMode
            )

            withContext(Dispatchers.Main) { onStatus("Printing invoice...") }
            outputStream.write(bytes)
            outputStream.flush()

            withContext(Dispatchers.Main) { onStatus("✅ Bill printed successfully!") }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error printing receipt: ${e.message}", e)
            withContext(Dispatchers.Main) { onStatus("Print Failed: ${e.localizedMessage}") }
            false
        } finally {
            try {
                outputStream?.close()
                socket.close()
            } catch (_: Exception) {}
        }
    }

    // Persistence Helpers
    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun saveConfig(
        context: Context,
        deviceAddress: String,
        deviceName: String,
        paperWidthMm: Int = 58,
        isGstMode: Boolean = true
    ) {
        getPrefs(context).edit()
            .putString(KEY_SAVED_PRINTER_ADDR, deviceAddress)
            .putString(KEY_SAVED_PRINTER_NAME, deviceName)
            .putInt(KEY_SAVED_PAPER_WIDTH, paperWidthMm)
            .putBoolean(KEY_SAVED_GST_MODE, isGstMode)
            .apply()
    }

    fun getSavedConfig(context: Context): SavedPrinterConfig? {
        val prefs = getPrefs(context)
        val addr = prefs.getString(KEY_SAVED_PRINTER_ADDR, null) ?: return null
        val name = prefs.getString(KEY_SAVED_PRINTER_NAME, "Saved Thermal Printer") ?: "Saved Thermal Printer"
        val width = prefs.getInt(KEY_SAVED_PAPER_WIDTH, 58)
        val isGst = prefs.getBoolean(KEY_SAVED_GST_MODE, true)
        return SavedPrinterConfig(name = name, address = addr, paperWidthMm = width, isGstMode = isGst)
    }

    fun getSavedPaperWidth(context: Context): Int {
        return getPrefs(context).getInt(KEY_SAVED_PAPER_WIDTH, 58)
    }

    fun savePaperWidth(context: Context, width: Int) {
        getPrefs(context).edit().putInt(KEY_SAVED_PAPER_WIDTH, width).apply()
    }
}
