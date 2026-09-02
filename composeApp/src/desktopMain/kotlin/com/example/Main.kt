package com.example

import androidx.compose.ui.Alignment
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import com.example.platform.DesktopPrinterManager
import com.example.platform.ThermalItem
import com.example.platform.ThermalPrintInvoice
import com.example.ui.screens.desktop.*
import com.example.ui.theme.SmartPOSTheme
import java.awt.Dimension
import java.util.Date
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

fun main() = application {
    val windowState = rememberWindowState(
        size = DpSize(1280.dp, 820.dp),
        position = WindowPosition(Alignment.Center)
    )

    val desktopScope = CoroutineScope(Dispatchers.Default)

    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = "SmartPOS Vyapar - Desktop Billing & Udhar Ledger",
        onKeyEvent = { keyEvent ->
            if (keyEvent.type == KeyEventType.KeyDown) {
                when (keyEvent.key) {
                    Key.F1 -> {
                        DesktopEventBus.emit(DesktopAction.FocusSearch)
                        true
                    }
                    Key.F2 -> {
                        DesktopEventBus.emit(DesktopAction.TriggerCheckout)
                        true
                    }
                    Key.F3 -> {
                        DesktopEventBus.emit(DesktopAction.OpenUdhar)
                        true
                    }
                    Key.F4 -> {
                        DesktopEventBus.emit(DesktopAction.PrintLastInvoice)
                        true
                    }
                    else -> false
                }
            } else {
                false
            }
        }
    ) {
        window.minimumSize = Dimension(1024, 700)

        SmartPOSTheme {
            POSDualPaneScreen(
                onCheckout = { phone, name, paymentMode, items, total ->
                    desktopScope.launch {
                        val invoiceNo = "INV-" + (System.currentTimeMillis() % 1000000)
                        val thermalInvoice = ThermalPrintInvoice(
                            storeName = "SMART KIRANA STORE",
                            invoiceNumber = invoiceNo,
                            date = Date(),
                            customerName = name,
                            customerPhone = phone,
                            items = items.map {
                                ThermalItem(
                                    name = it.name,
                                    quantity = it.quantity,
                                    unit = it.unit,
                                    unitPrice = it.unitPrice,
                                    subtotal = it.subtotal
                                )
                            },
                            grandTotal = total,
                            paymentMode = paymentMode
                        )

                        // 1. Direct ESC/POS Thermal Printing (Windows default printer)
                        DesktopPrinterManager.printToDefaultPrinter(thermalInvoice)

                        // 2. WhatsApp Gateway API Dispatch
                        if (phone.isNotBlank()) {
                            try {
                                val itemSummary = items.joinToString("\n") {
                                    "${it.name} x ${it.quantity} = ₹${it.subtotal}"
                                }
                                val url = "https://whatsappserver-84an.onrender.com/api/send-central-invoice"
                                println("Dispatching WhatsApp invoice to $phone for $invoiceNo")
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
            )
        }
    }
}
