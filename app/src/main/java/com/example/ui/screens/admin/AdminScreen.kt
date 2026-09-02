package com.example.ui.screens.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.IconMapper
import com.example.ui.components.PremiumGradientBackground
import com.example.ui.theme.ElectricVioletLight
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.EmeraldLight
import com.example.ui.viewmodel.BillingViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    viewModel: BillingViewModel,
    onNavigateBack: () -> Unit
) {
    val categories by viewModel.categories.collectAsState()
    var showCategoryEditorDialog by remember { mutableStateOf(false) }

    PremiumGradientBackground {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "Admin Category Backend",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color.White)
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier.testTag("admin_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0x99090D22)
                    ),
                    modifier = Modifier.testTag("admin_top_bar")
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = {
                        viewModel.clearAdminCategoryState()
                        showCategoryEditorDialog = true
                    },
                    containerColor = EmeraldGreen,
                    contentColor = Color.White,
                    modifier = Modifier
                        .testTag("admin_add_category_fab")
                        .padding(bottom = 16.dp, end = 8.dp),
                    shape = CircleShape
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Add Category", modifier = Modifier.size(28.dp))
                }
            },
            containerColor = Color.Transparent
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                // Info Section
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0x1F10B981)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0x2210B981), RoundedCornerShape(12.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Info",
                            tint = EmeraldLight,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Add, modify or delete business category offerings here. Changes dynamically sync and populate in the signup registry dropdown list.",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Heading
                Text(
                    text = "Dynamic Offerings (${categories.size})",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // List
                if (categories.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No business categories stored.", color = Color(0xFF94A3B8))
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .testTag("admin_categories_list"),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        items(categories) { category ->
                            CategoryItemRow(
                                category = category,
                                onEdit = {
                                    viewModel.startEditingCategory(category)
                                    showCategoryEditorDialog = true
                                },
                                onDelete = {
                                    viewModel.deleteCategory(category)
                                },
                                onToggleStatus = {
                                    viewModel.toggleCategoryStatus(category)
                                }
                            )
                        }
                    }
                }
            }
        }

        // --- Category Create / Edit Dialog ---
        if (showCategoryEditorDialog) {
            AlertDialog(
                onDismissRequest = { showCategoryEditorDialog = false },
                title = {
                    Text(
                        text = if (viewModel.editingCategory != null) "Edit Category" else "Add New Category",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = viewModel.adminCategoryName,
                            onValueChange = { viewModel.adminCategoryName = it },
                            label = { Text("Category Name (e.g. Pharmacy)", color = Color(0xFF94A3B8)) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = EmeraldGreen,
                                unfocusedBorderColor = Color(0x22FFFFFF),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("admin_category_name_input")
                        )

                        OutlinedTextField(
                            value = viewModel.adminCategoryDescription,
                            onValueChange = { viewModel.adminCategoryDescription = it },
                            label = { Text("Short Description", color = Color(0xFF94A3B8)) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = EmeraldGreen,
                                unfocusedBorderColor = Color(0x22FFFFFF),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            singleLine = false,
                            minLines = 2,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("admin_category_desc_input")
                        )

                        // Icon Selector row
                        Text("Select Visual Symbol:", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(IconMapper.availableIcons) { (iconKey, iconVector) ->
                                val isSelected = viewModel.adminCategoryIcon == iconKey
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) EmeraldGreen else Color(0x22FFFFFF))
                                        .clickable { viewModel.adminCategoryIcon = iconKey },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = iconVector,
                                        contentDescription = iconKey,
                                        tint = if (isSelected) Color.White else Color(0xFF94A3B8),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.saveCategory()
                            showCategoryEditorDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                        modifier = Modifier.testTag("admin_category_save_button")
                    ) {
                        Text("Save Changes", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCategoryEditorDialog = false }) {
                        Text("Cancel", color = Color(0xFFEC4899))
                    }
                },
                containerColor = Color(0xFF131B3E),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.border(1.dp, Color(0x11FFFFFF), RoundedCornerShape(20.dp))
            )
        }
    }
}

@Composable
fun CategoryItemRow(
    category: com.example.data.db.CategoryEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleStatus: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0x1F1E295D)),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0x11FFFFFF), RoundedCornerShape(14.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Circular icon frame
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(Color(0x228B5CF6), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = IconMapper.getIconByName(category.iconName),
                        contentDescription = category.name,
                        tint = ElectricVioletLight,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = category.name,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        // Status Badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (category.isEnabled) Color(0x3310B981) else Color(0x33EF4444))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (category.isEnabled) "Active" else "Disabled",
                                color = if (category.isEnabled) EmeraldLight else Color(0xFFF87171),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    Text(
                        text = category.description,
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Switch to enable/disable
                Switch(
                    checked = category.isEnabled,
                    onCheckedChange = { onToggleStatus() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = EmeraldGreen,
                        checkedTrackColor = Color(0x4410B981),
                        uncheckedThumbColor = Color(0xFF94A3B8),
                        uncheckedTrackColor = Color(0x22FFFFFF)
                    ),
                    modifier = Modifier
                        .scale(0.8f)
                        .testTag("category_toggle_${category.name.lowercase()}")
                )

                IconButton(onClick = onEdit, modifier = Modifier.testTag("category_edit_${category.name.lowercase()}")) {
                    Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit", tint = EmeraldLight)
                }
                IconButton(onClick = onDelete, modifier = Modifier.testTag("category_delete_${category.name.lowercase()}")) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEC4899))
                }
            }
        }
    }
}
