package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.ProductEntity
import com.example.ui.theme.*
import com.example.util.KiranaUnitUtils
import com.example.util.LooseInputType
import com.example.util.PharmacyUtils
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LooseQuantityDialog(
    product: ProductEntity,
    initialQuantity: Double = 1.0,
    onDismiss: () -> Unit,
    onConfirm: (quantity: Double) -> Unit
) {
    val isPharmacy = PharmacyUtils.isPharmacyProduct(product) || product.unit.equals("Strip", ignoreCase = true) || product.packUnitConfig.isNotBlank()
    val isVolume = KiranaUnitUtils.isVolumeUnit(product.unit)
    val isLoose = KiranaUnitUtils.isLooseUnit(product.unit, product)

    val packSize = remember(product) { PharmacyUtils.getPackSize(product) }
    val perTabletPrice = remember(product) { PharmacyUtils.getPerTabletUnitPrice(product) }

    val primaryUnitLabel = KiranaUnitUtils.getPrimaryUnitLabel(product.unit)
    val secondaryUnitLabel = KiranaUnitUtils.getSecondaryUnitLabel(product.unit)

    var inputType by remember {
        mutableStateOf(
            if (isPharmacy) LooseInputType.PHARMACY_LOOSE_TABLETS else LooseInputType.DUAL
        )
    }

    // Standard Dual fields state
    val (initPrimary, initSecondary) = remember(initialQuantity) {
        KiranaUnitUtils.extractDualValues(initialQuantity, product.unit)
    }
    var primaryInput by remember { mutableStateOf(if (initPrimary > 0) initPrimary.toString() else "0") }
    var secondaryInput by remember { mutableStateOf(if (initSecondary > 0) initSecondary.toString() else "0") }

    // Decimal input state
    var decimalInput by remember {
        mutableStateOf(if (initialQuantity > 0) String.format(Locale.US, "%.3f", initialQuantity).trimEnd('0').trimEnd('.') else "1.0")
    }

    // Pharmacy specific states
    var pharmacyStripsInput by remember(initialQuantity) {
        mutableStateOf(
            if (isPharmacy) {
                val fullStrips = (initialQuantity.toInt())
                if (fullStrips > 0) fullStrips.toString() else "0"
            } else "0"
        )
    }

    var pharmacyTabletsInput by remember(initialQuantity) {
        mutableStateOf(
            if (isPharmacy) {
                val looseTabs = Math.round((initialQuantity % 1.0) * packSize).toInt()
                if (looseTabs > 0) looseTabs.toString() else if (initialQuantity == 0.0) "0" else "3"
            } else "0"
        )
    }

    // Calculated total quantity in base unit (e.g. Strips or Kg/Ltr)
    val currentQuantity by remember {
        derivedStateOf {
            if (isPharmacy) {
                when (inputType) {
                    LooseInputType.PHARMACY_FULL_STRIP -> {
                        val strips = pharmacyStripsInput.toDoubleOrNull() ?: 0.0
                        strips.coerceAtLeast(0.0)
                    }
                    LooseInputType.PHARMACY_LOOSE_TABLETS -> {
                        val tabs = pharmacyTabletsInput.toIntOrNull() ?: 0
                        if (packSize > 0) (tabs.toDouble() / packSize.toDouble()).coerceAtLeast(0.0) else 0.0
                    }
                    else -> {
                        val strips = pharmacyStripsInput.toDoubleOrNull() ?: 0.0
                        val tabs = pharmacyTabletsInput.toIntOrNull() ?: 0
                        val tabFraction = if (packSize > 0) tabs.toDouble() / packSize.toDouble() else 0.0
                        (strips + tabFraction).coerceAtLeast(0.0)
                    }
                }
            } else if (inputType == LooseInputType.DUAL) {
                val p = primaryInput.toIntOrNull() ?: 0
                val s = secondaryInput.toIntOrNull() ?: 0
                KiranaUnitUtils.computeQuantityFromInput(product.unit, LooseInputType.DUAL, 0.0, p, s)
            } else {
                val d = decimalInput.toDoubleOrNull() ?: 0.0
                KiranaUnitUtils.computeQuantityFromInput(product.unit, LooseInputType.DECIMAL, d, 0, 0)
            }
        }
    }

    val currentTotalAmount by remember(currentQuantity, product.salePrice, isPharmacy) {
        derivedStateOf {
            if (isPharmacy) {
                (currentQuantity * product.salePrice).coerceAtLeast(0.0)
            } else {
                KiranaUnitUtils.calculateExactPrice(product.salePrice, currentQuantity)
            }
        }
    }

    val isExceedingStock = currentQuantity > product.stockQuantity

    // Preset options
    val standardPresets = remember(isVolume) {
        if (isVolume) {
            listOf(
                Triple("250 ml", 0, 250),
                Triple("500 ml", 0, 500),
                Triple("750 ml", 0, 750),
                Triple("1 Ltr", 1, 0),
                Triple("1.5 Ltr", 1, 500),
                Triple("2 Ltr", 2, 0)
            )
        } else {
            listOf(
                Triple("250 gm", 0, 250),
                Triple("500 gm", 0, 500),
                Triple("750 gm", 0, 750),
                Triple("1 kg", 1, 0),
                Triple("1.5 kg", 1, 500),
                Triple("2 kg", 2, 0)
            )
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = if (isPharmacy) Icons.Default.Medication else Icons.Default.Scale,
                    contentDescription = null,
                    tint = if (isPharmacy) EmeraldLight else GoldYellow,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = product.name,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        ),
                        maxLines = 1
                    )
                    Text(
                        text = if (isPharmacy) {
                            "Strip Price: ₹${String.format(Locale.US, "%.2f", product.salePrice)}  •  ₹${String.format(Locale.US, "%.2f", perTabletPrice)}/Tab ($packSize Tabs)"
                        } else {
                            "Price: ₹${String.format(Locale.US, "%.2f", product.salePrice)} / ${product.unit}  •  Stock: ${KiranaUnitUtils.formatQuantityWithUnit(product.stockQuantity, product.unit, product)}"
                        },
                        style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8))
                    )
                }
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Pharmacy Dual Mode Segmented Controls
                if (isPharmacy) {
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SegmentedButton(
                            selected = inputType == LooseInputType.PHARMACY_LOOSE_TABLETS,
                            onClick = { inputType = LooseInputType.PHARMACY_LOOSE_TABLETS },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = EmeraldGreen,
                                activeContentColor = Color.White,
                                inactiveContainerColor = Color(0x22FFFFFF),
                                inactiveContentColor = Color(0xFF94A3B8)
                            )
                        ) {
                            Text("Loose Tablets", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }

                        SegmentedButton(
                            selected = inputType == LooseInputType.PHARMACY_FULL_STRIP,
                            onClick = { inputType = LooseInputType.PHARMACY_FULL_STRIP },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = EmeraldGreen,
                                activeContentColor = Color.White,
                                inactiveContainerColor = Color(0x22FFFFFF),
                                inactiveContentColor = Color(0xFF94A3B8)
                            )
                        ) {
                            Text("Full Strip", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }

                        SegmentedButton(
                            selected = inputType == LooseInputType.DUAL,
                            onClick = { inputType = LooseInputType.DUAL },
                            shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = EmeraldGreen,
                                activeContentColor = Color.White,
                                inactiveContainerColor = Color(0x22FFFFFF),
                                inactiveContentColor = Color(0xFF94A3B8)
                            )
                        ) {
                            Text("Strip + Tabs", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Pharmacy Input Controls based on toggle
                    when (inputType) {
                        LooseInputType.PHARMACY_LOOSE_TABLETS -> {
                            OutlinedTextField(
                                value = pharmacyTabletsInput,
                                onValueChange = { pharmacyTabletsInput = it.filter { c -> c.isDigit() } },
                                label = { Text("Loose Tablets / Units", color = Color(0xFF94A3B8), fontSize = 12.sp) },
                                placeholder = { Text("e.g. 3, 5, 10") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = EmeraldGreen,
                                    unfocusedBorderColor = Color(0x33FFFFFF),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("pharmacy_loose_tablets_input")
                            )
                        }
                        LooseInputType.PHARMACY_FULL_STRIP -> {
                            OutlinedTextField(
                                value = pharmacyStripsInput,
                                onValueChange = { pharmacyStripsInput = it.filter { c -> c.isDigit() } },
                                label = { Text("Full Strips / Packs", color = Color(0xFF94A3B8), fontSize = 12.sp) },
                                placeholder = { Text("e.g. 1, 2") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = EmeraldGreen,
                                    unfocusedBorderColor = Color(0x33FFFFFF),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("pharmacy_full_strips_input")
                            )
                        }
                        else -> {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = pharmacyStripsInput,
                                    onValueChange = { pharmacyStripsInput = it.filter { c -> c.isDigit() } },
                                    label = { Text("Strips", color = Color(0xFF94A3B8), fontSize = 12.sp) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = EmeraldGreen,
                                        unfocusedBorderColor = Color(0x33FFFFFF),
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    ),
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("pharmacy_dual_strips_input")
                                )

                                OutlinedTextField(
                                    value = pharmacyTabletsInput,
                                    onValueChange = { pharmacyTabletsInput = it.filter { c -> c.isDigit() } },
                                    label = { Text("Loose Tablets", color = Color(0xFF94A3B8), fontSize = 12.sp) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = EmeraldGreen,
                                        unfocusedBorderColor = Color(0x33FFFFFF),
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    ),
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("pharmacy_dual_tablets_input")
                                )
                            }
                        }
                    }

                    // Pharmacy Presets
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Quick Pharmacy Presets:", color = Color(0xFF94A3B8), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val pharmPresets = listOf(
                                "1 Tab" to { inputType = LooseInputType.PHARMACY_LOOSE_TABLETS; pharmacyTabletsInput = "1" },
                                "3 Tabs" to { inputType = LooseInputType.PHARMACY_LOOSE_TABLETS; pharmacyTabletsInput = "3" },
                                "5 Tabs" to { inputType = LooseInputType.PHARMACY_LOOSE_TABLETS; pharmacyTabletsInput = "${packSize / 2}" },
                                "1 Strip" to { inputType = LooseInputType.PHARMACY_FULL_STRIP; pharmacyStripsInput = "1" },
                                "2 Strips" to { inputType = LooseInputType.PHARMACY_FULL_STRIP; pharmacyStripsInput = "2" }
                            )
                            pharmPresets.forEach { (lbl, onClickAction) ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0x2210B981))
                                        .clickable { onClickAction() }
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Text(lbl, color = EmeraldLight, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                } else {
                    // Standard Kirana Loose Unit Controls (Kg/Gm/Ltr/Ml)
                    if (isLoose) {
                        SingleChoiceSegmentedButtonRow(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            SegmentedButton(
                                selected = inputType == LooseInputType.DUAL,
                                onClick = {
                                    inputType = LooseInputType.DUAL
                                    val d = decimalInput.toDoubleOrNull() ?: 1.0
                                    val (p, s) = KiranaUnitUtils.extractDualValues(d, product.unit)
                                    primaryInput = p.toString()
                                    secondaryInput = s.toString()
                                },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                                colors = SegmentedButtonDefaults.colors(
                                    activeContainerColor = EmeraldGreen,
                                    activeContentColor = Color.White,
                                    inactiveContainerColor = Color(0x22FFFFFF),
                                    inactiveContentColor = Color(0xFF94A3B8)
                                )
                            ) {
                                Text("Dual ($primaryUnitLabel / $secondaryUnitLabel)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            SegmentedButton(
                                selected = inputType == LooseInputType.DECIMAL,
                                onClick = {
                                    inputType = LooseInputType.DECIMAL
                                    val p = primaryInput.toIntOrNull() ?: 0
                                    val s = secondaryInput.toIntOrNull() ?: 0
                                    val computed = KiranaUnitUtils.computeQuantityFromInput(product.unit, LooseInputType.DUAL, 0.0, p, s)
                                    decimalInput = String.format(Locale.US, "%.3f", computed).trimEnd('0').trimEnd('.')
                                },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                                colors = SegmentedButtonDefaults.colors(
                                    activeContainerColor = EmeraldGreen,
                                    activeContentColor = Color.White,
                                    inactiveContainerColor = Color(0x22FFFFFF),
                                    inactiveContentColor = Color(0xFF94A3B8)
                                )
                            ) {
                                Text("Decimal Input", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    if (inputType == LooseInputType.DUAL && isLoose) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = primaryInput,
                                onValueChange = { primaryInput = it.filter { char -> char.isDigit() } },
                                label = { Text(primaryUnitLabel, color = Color(0xFF94A3B8), fontSize = 12.sp) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = EmeraldGreen,
                                    unfocusedBorderColor = Color(0x33FFFFFF),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("loose_qty_primary_input")
                            )

                            OutlinedTextField(
                                value = secondaryInput,
                                onValueChange = { secondaryInput = it.filter { char -> char.isDigit() } },
                                label = { Text(secondaryUnitLabel, color = Color(0xFF94A3B8), fontSize = 12.sp) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = EmeraldGreen,
                                    unfocusedBorderColor = Color(0x33FFFFFF),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("loose_qty_secondary_input")
                            )
                        }
                    } else {
                        OutlinedTextField(
                            value = decimalInput,
                            onValueChange = { decimalInput = it },
                            label = { Text("Quantity in ${product.unit} (e.g. 1.25)", color = Color(0xFF94A3B8), fontSize = 12.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = EmeraldGreen,
                                unfocusedBorderColor = Color(0x33FFFFFF),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("loose_qty_decimal_input")
                        )
                    }

                    if (isLoose) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Quick Quantity Presets:", color = Color(0xFF94A3B8), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                standardPresets.take(4).forEach { (label, pVal, sVal) ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0x2210B981))
                                            .clickable {
                                                if (inputType == LooseInputType.DUAL) {
                                                    primaryInput = pVal.toString()
                                                    secondaryInput = sVal.toString()
                                                } else {
                                                    val comp = pVal + (sVal / 1000.0)
                                                    decimalInput = String.format(Locale.US, "%.3f", comp).trimEnd('0').trimEnd('.')
                                                }
                                            }
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text(label, color = EmeraldLight, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                // Automatic Price Calculation Display Engine
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0x221E295D)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, if (isExceedingStock) AccentPink else EmeraldGreen, RoundedCornerShape(12.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Selected Unit Label:", color = Color(0xFF94A3B8), fontSize = 11.sp)
                            Text(
                                KiranaUnitUtils.formatQuantityWithUnit(currentQuantity, product.unit, product),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Price Calculation Formula:", color = Color(0xFF94A3B8), fontSize = 11.sp)
                            Text(
                                if (isPharmacy) {
                                    val tabsCount = Math.round(currentQuantity * packSize).toInt()
                                    "$tabsCount Tabs × ₹${String.format(Locale.US, "%.2f", perTabletPrice)}/Tab"
                                } else {
                                    "₹${String.format(Locale.US, "%.2f", product.salePrice)} × ${String.format(Locale.US, "%.3f", currentQuantity).trimEnd('0').trimEnd('.')}"
                                },
                                color = GoldYellow,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        HorizontalDivider(color = Color(0x22FFFFFF), modifier = Modifier.padding(vertical = 4.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Calculated Total Price:", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(
                                "₹${String.format(Locale.US, "%.2f", currentTotalAmount)}",
                                color = EmeraldGreen,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp
                            )
                        }

                        if (isExceedingStock) {
                            Text(
                                text = "⚠ Exceeds available stock (${KiranaUnitUtils.formatQuantityWithUnit(product.stockQuantity, product.unit, product)})",
                                color = AccentPink,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (currentQuantity > 0 && !isExceedingStock) {
                        onConfirm(currentQuantity)
                    }
                },
                enabled = currentQuantity > 0 && !isExceedingStock,
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                modifier = Modifier.testTag("loose_qty_confirm_button")
            ) {
                Icon(Icons.Default.ShoppingBag, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add to Bill", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("loose_qty_cancel_button")
            ) {
                Text("Cancel", color = Color(0xFF94A3B8))
            }
        },
        containerColor = Color(0xFF0F172A),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.border(1.dp, Color(0x3310B981), RoundedCornerShape(20.dp))
    )
}
