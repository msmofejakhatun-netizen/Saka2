package com.example.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.EmeraldLight
import com.example.ui.theme.GoldYellow
import com.example.ui.theme.ElectricViolet
import com.example.util.OcrParsedProduct
import com.example.util.OcrTextParser
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrLabelScannerDialog(
    title: String = "Scan Box Label (OCR)",
    onOcrResultExtracted: (OcrParsedProduct) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        if (!isGranted) {
            Toast.makeText(
                context,
                "Camera permission required for label scanning. You can also pick a photo from gallery.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var isProcessing by remember { mutableStateOf(false) }
    var scannedRawText by remember { mutableStateOf("") }
    var parsedProduct by remember { mutableStateOf<OcrParsedProduct?>(null) }
    var cameraControlState by remember { mutableStateOf<CameraControl?>(null) }
    var isTorchOn by remember { mutableStateOf(false) }

    // Text Recognizer Client
    val textRecognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    // Image Picker Launcher (for uploaded label photos)
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                isProcessing = true
                val image = InputImage.fromFilePath(context, uri)
                textRecognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        val raw = visionText.text
                        scannedRawText = raw
                        val parsed = OcrTextParser.parseRecognizedText(raw)
                        parsedProduct = parsed
                        isProcessing = false
                        Toast.makeText(context, "OCR Scanned Packaging Label Successfully!", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        isProcessing = false
                        Toast.makeText(context, "OCR Failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    }
            } catch (e: Exception) {
                isProcessing = false
                Log.e("OcrScanner", "Error processing image uri: ${e.localizedMessage}")
            }
        }
    }

    // Function to load a pre-set sample packaging text for instant testing/demo in emulator
    fun loadPresetSampleText(sampleName: String) {
        val sampleText = when (sampleName) {
            "DOLO" -> """
                DOLO 650 Tablets
                MICRO LABS LIMITED
                Composition: Paracetamol IP 650mg
                B.NO.: DL2049A
                MFG.DATE: 08/2025
                EXP.DATE: 07/2028
                M.R.P. Rs.34.00 (INCL OF ALL TAXES)
                15 Tablets per strip
            """.trimIndent()
            "AUGMENTIN" -> """
                AUGMENTIN 625 DUO
                GlaxoSmithKline Pharmaceuticals
                Contains: Amoxicillin 500mg + Clavulanic Acid 125mg
                BATCH NO: AGM9812
                EXP: 11/2027
                MRP Rs. 201.50
                10 Tablets Box
            """.trimIndent()
            else -> """
                MAGGI 2-Minute Masala Noodles
                Nestle India Ltd
                Net Wt: 70g
                BATCH: N24018
                EXP: 05/2027
                MRP ₹14.00
            """.trimIndent()
        }

        scannedRawText = sampleText
        parsedProduct = OcrTextParser.parseRecognizedText(sampleText)
        Toast.makeText(context, "Loaded $sampleName Box Label for OCR", Toast.LENGTH_SHORT).show()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
                .statusBarsPadding()
                .navigationBarsPadding(),
            color = Color(0xFF0F172A),
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
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
                                imageVector = Icons.Default.DocumentScanner,
                                contentDescription = "OCR Scan",
                                tint = EmeraldGreen,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = title,
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "ML Kit Vision Text Recognition Reader",
                                color = EmeraldLight,
                                fontSize = 11.sp
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("ocr_scanner_close_button")
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Camera View / OCR Container
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black)
                        .border(2.dp, if (parsedProduct != null) EmeraldGreen else Color(0x33FFFFFF), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (hasCameraPermission) {
                        AndroidView(
                            factory = { ctx ->
                                val previewView = PreviewView(ctx).apply {
                                    layoutParams = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                    scaleType = PreviewView.ScaleType.FILL_CENTER
                                }

                                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                                cameraProviderFuture.addListener({
                                    val cameraProvider = cameraProviderFuture.get()
                                    val preview = Preview.Builder().build().also {
                                        it.setSurfaceProvider(previewView.surfaceProvider)
                                    }

                                    val imageAnalysis = ImageAnalysis.Builder()
                                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                        .build()

                                    val cameraExecutor = Executors.newSingleThreadExecutor()

                                    imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                                        val mediaImage = imageProxy.image
                                        if (mediaImage != null && !isProcessing) {
                                            val image = InputImage.fromMediaImage(
                                                mediaImage,
                                                imageProxy.imageInfo.rotationDegrees
                                            )
                                            textRecognizer.process(image)
                                                .addOnSuccessListener { visionText ->
                                                    val raw = visionText.text
                                                    if (raw.isNotBlank() && raw.length > 15) {
                                                        scannedRawText = raw
                                                        val parsed = OcrTextParser.parseRecognizedText(raw)
                                                        if (parsed.name.isNotBlank() || parsed.batchNumber.isNotBlank() || parsed.expiryDate.isNotBlank()) {
                                                            parsedProduct = parsed
                                                        }
                                                    }
                                                }
                                                .addOnFailureListener { e ->
                                                    Log.e("OcrScanner", "Scan failure: ${e.localizedMessage}")
                                                }
                                                .addOnCompleteListener {
                                                    imageProxy.close()
                                                }
                                        } else {
                                            imageProxy.close()
                                        }
                                    }

                                    try {
                                        cameraProvider.unbindAll()
                                        val camera = cameraProvider.bindToLifecycle(
                                            lifecycleOwner,
                                            CameraSelector.DEFAULT_BACK_CAMERA,
                                            preview,
                                            imageAnalysis
                                        )
                                        cameraControlState = camera.cameraControl
                                    } catch (exc: Exception) {
                                        Log.e("OcrScanner", "Camera binding failed", exc)
                                    }
                                }, ContextCompat.getMainExecutor(ctx))

                                previewView
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        // Target Box Overlay
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .height(130.dp)
                                .border(2.dp, EmeraldGreen, RoundedCornerShape(12.dp))
                        ) {
                            Text(
                                text = "Align Packaging Label / Medicine Box Here",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 6.dp)
                                    .background(Color(0xAA000000), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        // Torch Button
                        IconButton(
                            onClick = {
                                isTorchOn = !isTorchOn
                                cameraControlState?.enableTorch(isTorchOn)
                            },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .background(Color(0x88000000), CircleShape)
                        ) {
                            Icon(
                                imageVector = if (isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                contentDescription = "Torch",
                                tint = if (isTorchOn) Color.Yellow else Color.White
                            )
                        }
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Camera view disabled. Use Gallery photo or Preset Sample.", color = Color.White, fontSize = 12.sp, textAlign = TextAlign.Center)
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Grant Permission", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Action Bar: Gallery Upload & Preset Sample Boxes
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { galleryLauncher.launch("image/*") },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFFFFF)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .testTag("ocr_upload_gallery_button")
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null, tint = EmeraldLight, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Pick Image", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    // Demo Presets Dropdown
                    Button(
                        onClick = { loadPresetSampleText("DOLO") },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0x333B82F6)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3B82F6)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .height(38.dp)
                            .testTag("ocr_preset_dolo_button")
                    ) {
                        Text("💊 Sample Medicine Label", color = Color(0xFF60A5FA), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Parsed OCR Output Card
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .background(Color(0x221E293B), RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "EXTRACTED PACKAGING METADATA",
                            color = GoldYellow,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )

                        if (parsedProduct != null) {
                            Text(
                                text = "✨ Ready to Auto-Fill",
                                color = EmeraldGreen,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    HorizontalDivider(color = Color(0x22FFFFFF))

                    val p = parsedProduct
                    if (p != null) {
                        OcrMetaRow("Product Name", p.name.ifEmpty { "Not detected" }, isBold = true)
                        OcrMetaRow("Batch Number", p.batchNumber.ifEmpty { "Not detected" })
                        OcrMetaRow("Expiry Date", p.expiryDate.ifEmpty { "Not detected" })
                        OcrMetaRow("MRP / Sale Price", if (p.mrp != null) "₹${p.mrp}" else "Not detected")
                        OcrMetaRow("Manufacturer", p.manufacturer.ifEmpty { "Not detected" })
                        OcrMetaRow("Salt Composition", p.saltComposition.ifEmpty { "Not detected" })
                        OcrMetaRow("Pack Unit Config", p.packConfig.ifEmpty { "Not detected" })

                        if (scannedRawText.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Raw Text: ${scannedRawText.take(120)}...",
                                color = Color(0xFF64748B),
                                fontSize = 9.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Point camera at medicine box label or tap 'Sample Medicine Label' above.",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Confirm & Apply Button
                Button(
                    onClick = {
                        val finalResult = parsedProduct ?: OcrParsedProduct()
                        onOcrResultExtracted(finalResult)
                        onDismiss()
                    },
                    enabled = parsedProduct != null,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EmeraldGreen,
                        disabledContainerColor = Color(0x3310B981)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("ocr_apply_autofill_button")
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Auto-Fill Product Fields", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun OcrMetaRow(label: String, value: String, isBold: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color(0xFF94A3B8), fontSize = 11.sp)
        Text(
            text = value,
            color = if (value.contains("Not detected")) Color(0xFF64748B) else Color.White,
            fontSize = 12.sp,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
