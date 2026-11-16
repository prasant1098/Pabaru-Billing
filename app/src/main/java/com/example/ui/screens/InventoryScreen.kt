package com.example.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.engine.ExportEngine
import com.example.engine.FinancialEngine
import com.example.model.*
import com.example.ui.theme.PabaruTheme
import com.example.ui.theme.danger
import com.example.ui.theme.dangerContainer
import com.example.ui.theme.onDangerContainer
import com.example.ui.theme.onSuccessContainer
import com.example.ui.theme.success
import com.example.ui.theme.successContainer
import com.example.ui.theme.warning
import com.example.viewmodel.PabaruUiState
import com.example.viewmodel.PabaruViewModel
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToLong

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    uiState: PabaruUiState,
    viewModel: PabaruViewModel,
    initialFilterLowStock: Boolean = false,
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val tokens = PabaruTheme.tokens

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf("All") }
    var filterLowStockOnly by remember(initialFilterLowStock) { mutableStateOf(initialFilterLowStock) }
    var filterOutOfStockOnly by remember { mutableStateOf(false) }
    var showArchivedOnly by remember { mutableStateOf(false) }

    // Dialog state controllers
    var showAddEditDialog by remember { mutableStateOf(false) }
    var editingProduct by remember { mutableStateOf<ProductItem?>(null) }
    var showAdjustmentDialog by remember { mutableStateOf(false) }
    var preselectedAdjProduct by remember { mutableStateOf<ProductItem?>(null) }
    var showPurchaseDialog by remember { mutableStateOf(false) }
    var showTransferDialog by remember { mutableStateOf(false) }
    var showMovementsDialog by remember { mutableStateOf(false) }
    var movementProductFilter by remember { mutableStateOf<ProductItem?>(null) }
    var showValuationDialog by remember { mutableStateOf(false) }
    var productToDelete by remember { mutableStateOf<ProductItem?>(null) }

    // Calculate aggregated inventory KPIs
    val allActiveProducts = uiState.products.filter { !it.isArchived }
    val totalPurchaseValuationPaise = allActiveProducts.sumOf { (it.currentStockMilli * it.purchasePricePaise + 500L) / 1000L }
    val totalSaleValuationPaise = allActiveProducts.sumOf { (it.currentStockMilli * it.salePricePaise + 500L) / 1000L }
    val lowStockThresholdMilli = uiState.lowStockThreshold.toLong() * 1000L
    val lowStockCount = allActiveProducts.count {
        it.currentStockMilli > 0L && it.currentStockMilli <= maxOf(it.reorderLevelMilli, lowStockThresholdMilli)
    }
    val outOfStockCount = allActiveProducts.count { it.currentStockMilli <= 0L }
    val expiringBatchesCount = uiState.stockBatches.count { it.isExpiringSoon || it.isExpired }

    // Collect distinct categories
    val categories = remember(uiState.products) {
        listOf("All") + uiState.products.map { it.category.ifBlank { "General" } }.distinct().sorted()
    }

    // Filter products
    val displayedProducts = remember(
        uiState.products,
        searchQuery,
        selectedCategoryFilter,
        filterLowStockOnly,
        filterOutOfStockOnly,
        showArchivedOnly,
        uiState.lowStockThreshold
    ) {
        uiState.products.filter { prod ->
            val matchesArchived = if (showArchivedOnly) prod.isArchived else !prod.isArchived
            val matchesSearch = searchQuery.isBlank() ||
                    prod.name.contains(searchQuery, ignoreCase = true) ||
                    prod.sku.contains(searchQuery, ignoreCase = true) ||
                    prod.barcode.contains(searchQuery, ignoreCase = true) ||
                    prod.hsnCode.contains(searchQuery, ignoreCase = true)
            val matchesCategory = selectedCategoryFilter == "All" || prod.category.equals(selectedCategoryFilter, ignoreCase = true)
            val matchesLowStock = !filterLowStockOnly || (prod.currentStockMilli > 0L && prod.currentStockMilli <= maxOf(prod.reorderLevelMilli, lowStockThresholdMilli))
            val matchesOutOfStock = !filterOutOfStockOnly || (prod.currentStockMilli <= 0L)

            matchesArchived && matchesSearch && matchesCategory && matchesLowStock && matchesOutOfStock
        }
    }

    BackHandler {
        when {
            showAddEditDialog -> showAddEditDialog = false
            showAdjustmentDialog -> showAdjustmentDialog = false
            showPurchaseDialog -> showPurchaseDialog = false
            showTransferDialog -> showTransferDialog = false
            showMovementsDialog -> showMovementsDialog = false
            showValuationDialog -> showValuationDialog = false
            productToDelete != null -> productToDelete = null
            else -> onBack?.invoke()
        }
    }

    Scaffold(
        containerColor = tokens.bg,
        topBar = {
            Surface(
                color = tokens.headerBg,
                border = BorderStroke(0.5.dp, tokens.cardBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            if (onBack != null) {
                                IconButton(
                                    onClick = onBack,
                                    modifier = Modifier
                                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                        .testTag("inventory_back_button")
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = tokens.headerText)
                                }
                            }
                            Column {
                                Text(
                                    text = "Inventory & Stock Hub",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = tokens.headerText
                                )
                                Text(
                                    text = "${uiState.products.size} Items · Live FIFO & Valuation",
                                    fontSize = 11.sp,
                                    color = tokens.headerText.copy(alpha = 0.8f)
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { showValuationDialog = true },
                                modifier = Modifier
                                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                    .testTag("inventory_valuation_btn")
                            ) {
                                Icon(Icons.Outlined.Assessment, contentDescription = "Stock Valuation", tint = tokens.headerText)
                            }
                            IconButton(
                                onClick = {
                                    movementProductFilter = null
                                    showMovementsDialog = true
                                },
                                modifier = Modifier
                                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                    .testTag("inventory_movements_btn")
                            ) {
                                Icon(Icons.Outlined.History, contentDescription = "Stock Movements", tint = tokens.headerText)
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(vertical = 14.dp)
        ) {
            // ==========================================
            // 1. KPI VALUATION & ALERT SUMMARY CARDS
            // ==========================================
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = tokens.card),
                    border = BorderStroke(1.dp, tokens.cardBorder)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("TOTAL STOCK VALUATION", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = tokens.textMuted)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = FinancialEngine.formatPaise(totalPurchaseValuationPaise),
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Black,
                                    color = tokens.primary
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("RETAIL VALUE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = tokens.textMuted)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = FinancialEngine.formatPaise(totalSaleValuationPaise),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = tokens.accent
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Divider(color = tokens.cardBorder, thickness = 0.5.dp)
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Total SKUs
                            MetricPill(
                                label = "Total SKUs",
                                value = allActiveProducts.size.toString(),
                                icon = Icons.Outlined.Inventory2,
                                color = tokens.text
                            )
                            // Low Stock
                            MetricPill(
                                label = "Low Stock",
                                value = lowStockCount.toString(),
                                icon = Icons.Outlined.WarningAmber,
                                color = if (lowStockCount > 0) tokens.warning else tokens.textMuted,
                                isActive = filterLowStockOnly,
                                onClick = {
                                    filterLowStockOnly = !filterLowStockOnly
                                    filterOutOfStockOnly = false
                                }
                            )
                            // Out of Stock
                            MetricPill(
                                label = "Out of Stock",
                                value = outOfStockCount.toString(),
                                icon = Icons.Outlined.RemoveCircleOutline,
                                color = if (outOfStockCount > 0) tokens.danger else tokens.textMuted,
                                isActive = filterOutOfStockOnly,
                                onClick = {
                                    filterOutOfStockOnly = !filterOutOfStockOnly
                                    filterLowStockOnly = false
                                }
                            )
                            // Expiring Batches
                            if (expiringBatchesCount > 0) {
                                MetricPill(
                                    label = "Expiring",
                                    value = expiringBatchesCount.toString(),
                                    icon = Icons.Outlined.HourglassBottom,
                                    color = tokens.danger
                                )
                            }
                        }
                    }
                }
            }

            // ==========================================
            // 2. QUICK ACTION BAR
            // ==========================================
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            editingProduct = null
                            showAddEditDialog = true
                        },
                        modifier = Modifier
                            .weight(1.3f)
                            .heightIn(min = 48.dp)
                            .testTag("inventory_add_item_btn"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = tokens.primary)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("New Item", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = tokens.primaryText)
                    }

                    OutlinedButton(
                        onClick = { showPurchaseDialog = true },
                        modifier = Modifier
                            .weight(1.1f)
                            .heightIn(min = 48.dp)
                            .testTag("inventory_purchase_bill_btn"),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, tokens.cardBorder),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = tokens.text)
                    ) {
                        Icon(Icons.Outlined.LocalShipping, contentDescription = null, modifier = Modifier.size(16.dp), tint = tokens.accent)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Purchase", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }

                    OutlinedButton(
                        onClick = {
                            preselectedAdjProduct = null
                            showAdjustmentDialog = true
                        },
                        modifier = Modifier
                            .weight(1.1f)
                            .heightIn(min = 48.dp)
                            .testTag("inventory_adjust_btn"),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, tokens.cardBorder),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = tokens.text)
                    ) {
                        Icon(Icons.Outlined.Tune, contentDescription = null, modifier = Modifier.size(16.dp), tint = tokens.warning)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Adjust", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }

                    OutlinedButton(
                        onClick = { showTransferDialog = true },
                        modifier = Modifier
                            .weight(0.9f)
                            .heightIn(min = 48.dp)
                            .testTag("inventory_transfer_btn"),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, tokens.cardBorder),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = tokens.text)
                    ) {
                        Icon(Icons.Outlined.SwapHoriz, contentDescription = "Transfer stock", modifier = Modifier.size(18.dp), tint = tokens.link)
                    }
                }
            }

            // ==========================================
            // 3. SEARCH & CATEGORY CHIPS
            // ==========================================
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Search bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("inventory_search_input"),
                        placeholder = { Text("Search by item name, SKU, barcode, HSN...", fontSize = 13.sp, color = tokens.textMuted) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = tokens.textMuted) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { searchQuery = "" },
                                    modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                ) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear", tint = tokens.textMuted)
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = tokens.primary,
                            unfocusedBorderColor = tokens.cardBorder,
                            focusedTextColor = tokens.text,
                            unfocusedTextColor = tokens.text
                        )
                    )

                    // Category scroll chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        categories.forEach { cat ->
                            val isSelected = selectedCategoryFilter == cat
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedCategoryFilter = cat },
                                modifier = Modifier.heightIn(min = 48.dp),
                                label = { Text(cat, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = tokens.primary,
                                    selectedLabelColor = tokens.primaryText,
                                    containerColor = tokens.card,
                                    labelColor = tokens.textMuted
                                ),
                                border = BorderStroke(0.5.dp, if (isSelected) tokens.primary else tokens.cardBorder)
                            )
                        }

                        // Archive toggle chip
                        FilterChip(
                            selected = showArchivedOnly,
                            onClick = { showArchivedOnly = !showArchivedOnly },
                            modifier = Modifier.heightIn(min = 48.dp),
                            label = { Text("Archived", fontSize = 11.sp) },
                            leadingIcon = {
                                Icon(
                                    Icons.Outlined.Archive,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = tokens.primary,
                                selectedLabelColor = tokens.primaryText,
                                selectedLeadingIconColor = tokens.primaryText,
                                containerColor = tokens.card,
                                labelColor = tokens.textMuted
                            ),
                            border = BorderStroke(0.5.dp, if (showArchivedOnly) tokens.primary else tokens.cardBorder)
                        )
                    }
                }
            }

            // ==========================================
            // 4. PRODUCT CARDS LIST
            // ==========================================
            if (displayedProducts.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        colors = CardDefaults.cardColors(containerColor = tokens.card),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, tokens.cardBorder)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Outlined.Inventory2,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = tokens.textMuted
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = if (showArchivedOnly) "No archived products" else "No items match your filter",
                                fontWeight = FontWeight.Bold,
                                color = tokens.text,
                                fontSize = 15.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Try modifying your search or click + New Item to add products.",
                                fontSize = 12.sp,
                                color = tokens.textMuted
                            )
                        }
                    }
                }
            } else {
                items(displayedProducts, key = { it.id }) { product ->
                    ProductCard(
                        product = product,
                        batches = uiState.stockBatches.filter { it.productId == product.id },
                        lowStockThresholdMilli = lowStockThresholdMilli,
                        onEdit = {
                            editingProduct = product
                            showAddEditDialog = true
                        },
                        onAdjust = {
                            preselectedAdjProduct = product
                            showAdjustmentDialog = true
                        },
                        onViewMovements = {
                            movementProductFilter = product
                            showMovementsDialog = true
                        },
                        onToggleArchive = {
                            viewModel.toggleProductArchive(product.id, !product.isArchived)
                            val msg = if (product.isArchived) "Unarchived" else "Archived"
                            Toast.makeText(context, "$msg ${product.name}", Toast.LENGTH_SHORT).show()
                        },
                        onDelete = {
                            productToDelete = product
                        }
                    )
                }
            }
        }
    }

    // ==========================================
    // DIALOGS & FORMS
    // ==========================================

    if (showAddEditDialog) {
        ProductFormDialog(
            existing = editingProduct,
            onDismiss = { showAddEditDialog = false },
            onSave = { savedProduct ->
                if (editingProduct != null) {
                    viewModel.updateProduct(savedProduct)
                    Toast.makeText(context, "Updated ${savedProduct.name}", Toast.LENGTH_SHORT).show()
                } else {
                    viewModel.addProduct(savedProduct)
                    Toast.makeText(context, "Added ${savedProduct.name} to inventory", Toast.LENGTH_SHORT).show()
                }
                showAddEditDialog = false
            }
        )
    }

    if (showAdjustmentDialog) {
        StockAdjustmentDialog(
            products = allActiveProducts,
            locations = uiState.inventoryLocations,
            initialProduct = preselectedAdjProduct,
            onDismiss = { showAdjustmentDialog = false },
            onConfirm = { prodId, locId, deltaMilli, reason, remarks ->
                viewModel.recordStockAdjustmentMilli(prodId, locId, deltaMilli, reason, remarks)
                Toast.makeText(context, "Stock adjusted successfully", Toast.LENGTH_SHORT).show()
                showAdjustmentDialog = false
            }
        )
    }

    if (showPurchaseDialog) {
        PurchaseBillDialog(
            suppliers = uiState.parties.filter { it.isSupplier },
            allProducts = allActiveProducts,
            homeStateCode = uiState.profile.homeStateCode,
            onDismiss = { showPurchaseDialog = false },
            onSave = { supplierId, billNo, date, pmtMode, items, batches, remarks ->
                viewModel.recordPurchaseBill(supplierId, billNo, date, pmtMode, items, batches, remarks)
                Toast.makeText(context, "Purchase inward bill $billNo recorded", Toast.LENGTH_SHORT).show()
                showPurchaseDialog = false
            }
        )
    }

    if (showTransferDialog) {
        StockTransferDialog(
            products = allActiveProducts,
            locations = uiState.inventoryLocations,
            onDismiss = { showTransferDialog = false },
            onConfirm = { prodId, fromLoc, toLoc, qtyMilli, rem ->
                viewModel.transferStockLocationMilli(prodId, fromLoc, toLoc, qtyMilli, rem)
                Toast.makeText(context, "Stock transferred successfully", Toast.LENGTH_SHORT).show()
                showTransferDialog = false
            }
        )
    }

    if (showMovementsDialog) {
        StockMovementsDialog(
            movements = uiState.stockMovements,
            filterProduct = movementProductFilter,
            onDismiss = { showMovementsDialog = false }
        )
    }

    if (showValuationDialog) {
        StockValuationDialog(
            products = allActiveProducts,
            movements = uiState.stockMovements,
            totalPurchaseValuationPaise = totalPurchaseValuationPaise,
            totalSaleValuationPaise = totalSaleValuationPaise,
            onDismiss = { showValuationDialog = false }
        )
    }

    if (productToDelete != null) {
        AlertDialog(
            onDismissRequest = { productToDelete = null },
            containerColor = tokens.card,
            titleContentColor = tokens.text,
            textContentColor = tokens.textMuted,
            title = { Text("Delete Product?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to permanently delete '${productToDelete?.name}'? Historical invoice references will remain intact.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        productToDelete?.let { viewModel.deleteProduct(it.id) }
                        productToDelete = null
                        Toast.makeText(context, "Product deleted", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = tokens.danger)
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { productToDelete = null },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

// ==========================================
// COMPOSABLE SUBCOMPONENTS
// ==========================================

@Composable
private fun MetricPill(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    isActive: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    Surface(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clickable(enabled = onClick != null) { onClick?.invoke() },
        color = if (isActive) color.copy(alpha = 0.28f) else color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(if (isActive) 1.5.dp else 0.5.dp, if (isActive) color else color.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Column {
                Text(text = value, fontSize = 12.sp, fontWeight = FontWeight.Black, color = color)
                Text(text = label, fontSize = 9.sp, color = color.copy(alpha = 0.8f))
            }
        }
    }
}

@Composable
private fun ProductCard(
    product: ProductItem,
    batches: List<StockBatch>,
    lowStockThresholdMilli: Long = 0L,
    onEdit: () -> Unit,
    onAdjust: () -> Unit,
    onViewMovements: () -> Unit,
    onToggleArchive: () -> Unit,
    onDelete: () -> Unit
) {
    val tokens = PabaruTheme.tokens
    var showMenu by remember { mutableStateOf(false) }

    val effectiveReorderMilli = maxOf(product.reorderLevelMilli, lowStockThresholdMilli)
    val isOutOfStock = product.currentStockMilli <= 0L
    val isLowStock = !isOutOfStock && product.currentStockMilli <= effectiveReorderMilli

    val badgeColor = when {
        isOutOfStock -> tokens.danger
        isLowStock -> tokens.warning
        else -> tokens.success
    }

    val marginPercent = if (product.salePricePaise > 0L) {
        val profitPaise = product.salePricePaise - product.purchasePricePaise
        (profitPaise * 100) / product.salePricePaise
    } else 0L

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("product_card_${product.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = tokens.card),
        border = BorderStroke(1.dp, if (isLowStock || isOutOfStock) badgeColor.copy(alpha = 0.35f) else tokens.cardBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: Name & Quick Menu
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = product.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = tokens.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (product.category.isNotBlank()) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = tokens.primary.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = product.category,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = tokens.primary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "SKU: ${product.sku.ifBlank { "N/A" }} | HSN: ${product.hsnCode} | GST: ${GstSlabs.label(product.gstRateBps)}",
                        fontSize = 11.sp,
                        color = tokens.textMuted
                    )
                }

                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    ) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = tokens.textMuted)
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(tokens.card)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Edit Product", color = tokens.text) },
                            leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null, tint = tokens.text) },
                            onClick = {
                                showMenu = false
                                onEdit()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Adjust Stock", color = tokens.text) },
                            leadingIcon = { Icon(Icons.Outlined.Tune, contentDescription = null, tint = tokens.text) },
                            onClick = {
                                showMenu = false
                                onAdjust()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Movement Ledger", color = tokens.text) },
                            leadingIcon = { Icon(Icons.Outlined.History, contentDescription = null, tint = tokens.text) },
                            onClick = {
                                showMenu = false
                                onViewMovements()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(if (product.isArchived) "Unarchive" else "Archive", color = tokens.text) },
                            leadingIcon = { Icon(Icons.Outlined.Archive, contentDescription = null, tint = tokens.text) },
                            onClick = {
                                showMenu = false
                                onToggleArchive()
                            }
                        )
                        HorizontalDivider(color = tokens.cardBorder)
                        DropdownMenuItem(
                            text = { Text("Delete", color = tokens.danger) },
                            leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = tokens.danger) },
                            onClick = {
                                showMenu = false
                                onDelete()
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Body Row: Stock Pill & Pricing
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Stock status indicator
                Surface(
                    color = badgeColor.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(0.5.dp, badgeColor.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(badgeColor, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        val stockStr = FinancialEngine.formatQuantityMilli(product.currentStockMilli)
                        val reorderStr = FinancialEngine.formatQuantityMilli(effectiveReorderMilli)
                        val statusText = when {
                            isOutOfStock -> "Out of Stock (0 ${product.unit})"
                            isLowStock -> "$stockStr ${product.unit} (Low · Reorder ≤ $reorderStr)"
                            else -> "$stockStr ${product.unit} in stock"
                        }
                        Text(
                            text = statusText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = badgeColor
                        )
                    }
                }

                // Prices
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = FinancialEngine.formatPaise(product.salePricePaise),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        color = tokens.text
                    )
                    Text(
                        text = "Cost: ${FinancialEngine.formatPaise(product.purchasePricePaise)} (${marginPercent}% margin)",
                        fontSize = 10.sp,
                        color = tokens.textMuted
                    )
                }
            }

            // Batches preview if batch tracking enabled
            if (product.hasBatchTracking && batches.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.QrCode, contentDescription = null, tint = tokens.textMuted, modifier = Modifier.size(12.dp))
                    Text(
                        text = "${batches.size} Batches tracked (FIFO active):",
                        fontSize = 10.sp,
                        color = tokens.textMuted
                    )
                    batches.take(2).forEach { b ->
                        Surface(
                            color = if (b.isExpired) tokens.dangerContainer else tokens.secondaryBg,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "${b.batchNumber}: ${FinancialEngine.formatQuantityMilli(b.availableQuantityMilli)} ${product.unit}",
                                fontSize = 9.sp,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                color = if (b.isExpired) tokens.onDangerContainer else tokens.textMuted
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// PRODUCT ADD / EDIT DIALOG FORM
// ==========================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProductFormDialog(
    existing: ProductItem?,
    onDismiss: () -> Unit,
    onSave: (ProductItem) -> Unit
) {
    val tokens = PabaruTheme.tokens
    val context = LocalContext.current
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var category by remember { mutableStateOf(existing?.category ?: "General") }
    var sku by remember { mutableStateOf(existing?.sku ?: "") }
    var barcode by remember { mutableStateOf(existing?.barcode ?: "") }
    var unit by remember { mutableStateOf(existing?.unit ?: "pcs") }
    var hsnCode by remember { mutableStateOf(existing?.hsnCode ?: "") }
    var gstRateBps by remember { mutableIntStateOf(existing?.gstRateBps ?: 1800) }
    var salePriceRupees by remember {
        mutableStateOf(
            if (existing != null && existing.salePricePaise > 0L) {
                String.format(Locale.ENGLISH, "%.2f", existing.salePricePaise / 100.0).trimEnd('0').trimEnd('.')
            } else ""
        )
    }
    var purchasePriceRupees by remember {
        mutableStateOf(
            if (existing != null && existing.purchasePricePaise > 0L) {
                String.format(Locale.ENGLISH, "%.2f", existing.purchasePricePaise / 100.0).trimEnd('0').trimEnd('.')
            } else ""
        )
    }
    var openingStock by remember {
        mutableStateOf(existing?.let { FinancialEngine.formatQuantityMilli(it.openingStockMilli) } ?: "0")
    }
    var reorderLevel by remember {
        mutableStateOf(existing?.let { FinancialEngine.formatQuantityMilli(it.reorderLevelMilli) } ?: "5")
    }
    var hasBatchTracking by remember { mutableStateOf(existing?.hasBatchTracking ?: false) }
    var formError by remember { mutableStateOf<String?>(null) }

    val commonUnits = listOf("pcs", "kg", "g", "box", "meter", "litre", "pack", "pair")
    val gstSlabOptions = remember(existing) {
        (GstSlabs.STANDARD_BPS + GstSlabs.SPECIAL_BPS + listOfNotNull(existing?.gstRateBps)).distinct().sorted()
    }
    val unitAllowsDecimal = allowsDecimal(unit)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f),
            shape = RoundedCornerShape(20.dp),
            color = tokens.card,
            border = BorderStroke(1.dp, tokens.cardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (existing != null) "Edit Product" else "Add New Item",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = tokens.text
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = tokens.textMuted)
                    }
                }

                HorizontalDivider(color = tokens.cardBorder)
                Spacer(modifier = Modifier.height(12.dp))

                // Scrollable Form Fields
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (formError != null) {
                            item {
                                Surface(
                                    color = tokens.dangerContainer,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = formError!!,
                                        color = tokens.onDangerContainer,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }
                        }

                        item {
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it; formError = null },
                                label = { Text("Product / Item Name *") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp)
                            )
                        }

                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = category,
                                    onValueChange = { category = it },
                                    label = { Text("Category") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                OutlinedTextField(
                                    value = hsnCode,
                                    onValueChange = { hsnCode = it.filter { c -> c.isDigit() }.take(8); formError = null },
                                    label = { Text("HSN Code (4/6/8 digits)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp)
                                )
                            }
                        }

                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = sku,
                                    onValueChange = { sku = it },
                                    label = { Text("SKU") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                OutlinedTextField(
                                    value = barcode,
                                    onValueChange = { barcode = it },
                                    label = { Text("Barcode") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp)
                                )
                            }
                        }

                        item {
                            Text("Measurement Unit", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = tokens.textMuted)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                commonUnits.forEach { u ->
                                    FilterChip(
                                        selected = unit.equals(u, ignoreCase = true),
                                        onClick = { unit = u },
                                        modifier = Modifier.heightIn(min = 48.dp),
                                        label = { Text(u, fontSize = 11.sp) }
                                    )
                                }
                            }
                        }

                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = salePriceRupees,
                                    onValueChange = { salePriceRupees = FinancialEngine.sanitizeDecimalInput(it); formError = null },
                                    label = { Text("Selling Price (₹) *") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                OutlinedTextField(
                                    value = purchasePriceRupees,
                                    onValueChange = { purchasePriceRupees = FinancialEngine.sanitizeDecimalInput(it) },
                                    label = { Text("Purchase Cost (₹)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp)
                                )
                            }
                        }

                        item {
                            Text("GST Tax Rate (%)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = tokens.textMuted)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                gstSlabOptions.forEach { bps ->
                                    FilterChip(
                                        selected = gstRateBps == bps,
                                        onClick = { gstRateBps = bps },
                                        modifier = Modifier.heightIn(min = 48.dp),
                                        label = { Text(GstSlabs.label(bps), fontSize = 11.sp) }
                                    )
                                }
                            }
                        }

                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = openingStock,
                                    onValueChange = {
                                        openingStock = if (unitAllowsDecimal) FinancialEngine.sanitizeDecimalInput(it) else it.filter { c -> c.isDigit() }
                                    },
                                    label = { Text("Opening Stock") },
                                    keyboardOptions = KeyboardOptions(keyboardType = if (unitAllowsDecimal) KeyboardType.Decimal else KeyboardType.Number),
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                OutlinedTextField(
                                    value = reorderLevel,
                                    onValueChange = {
                                        reorderLevel = if (unitAllowsDecimal) FinancialEngine.sanitizeDecimalInput(it) else it.filter { c -> c.isDigit() }
                                    },
                                    label = { Text("Reorder Alert Level") },
                                    keyboardOptions = KeyboardOptions(keyboardType = if (unitAllowsDecimal) KeyboardType.Decimal else KeyboardType.Number),
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp)
                                )
                            }
                        }

                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .clickable { hasBatchTracking = !hasBatchTracking }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = hasBatchTracking,
                                    onCheckedChange = { hasBatchTracking = it }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text("Enable Batch & Expiry Tracking", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = tokens.text)
                                    Text("Enforces FIFO stock allocation during invoicing", fontSize = 11.sp, color = tokens.textMuted)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text("Cancel", color = tokens.textMuted)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (name.isBlank()) {
                                formError = "Product name is required."
                                return@Button
                            }
                            if (hsnCode.isNotBlank()) {
                                val hsnResult = FinancialEngine.validateHsn(hsnCode, allowBlank = true)
                                if (!hsnResult.isValid) {
                                    formError = hsnResult.message
                                    return@Button
                                }
                            }
                            val salePaise = FinancialEngine.parsePaiseFromRupees(salePriceRupees)
                            if (salePaise <= 0L) {
                                formError = "Please enter a valid positive selling price."
                                return@Button
                            }
                            val purchasePaise = FinancialEngine.parsePaiseFromRupees(purchasePriceRupees)
                            val opVal = openingStock.trim().toDoubleOrNull() ?: 0.0
                            val reorderVal = reorderLevel.trim().toDoubleOrNull() ?: 5.0
                            val opStockMilli = if (unitAllowsDecimal) (opVal * 1000.0).roundToLong().coerceAtLeast(0L) else opVal.roundToLong().coerceAtLeast(0L) * 1000L
                            val reorderMilli = if (unitAllowsDecimal) (reorderVal * 1000.0).roundToLong().coerceAtLeast(0L) else reorderVal.roundToLong().coerceAtLeast(0L) * 1000L

                            val currentStockMilliValue = existing?.currentStockMilli ?: opStockMilli

                            val newOrUpdated = ProductItem(
                                id = existing?.id ?: "PROD-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(4)}",
                                name = name.trim(),
                                barcode = barcode.trim(),
                                sku = sku.trim(),
                                hsnCode = hsnCode.trim().ifEmpty { "1006" },
                                gstRateBps = gstRateBps,
                                unit = unit,
                                purchasePricePaise = purchasePaise,
                                salePricePaise = salePaise,
                                currentStockMilli = currentStockMilliValue,
                                reorderLevelMilli = reorderMilli,
                                category = category.trim().ifEmpty { "General" },
                                openingStockMilli = opStockMilli,
                                hasBatchTracking = hasBatchTracking,
                                hasVariants = existing?.hasVariants ?: false,
                                isArchived = existing?.isArchived ?: false,
                                imageUrl = existing?.imageUrl
                            )
                            onSave(newOrUpdated)
                        },
                        modifier = Modifier.heightIn(min = 48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = tokens.primary)
                    ) {
                        Text(if (existing != null) "Update Item" else "Save Item", fontWeight = FontWeight.Bold, color = tokens.primaryText)
                    }
                }
            }
        }
    }
}

// ==========================================
// STOCK ADJUSTMENT DIALOG
// ==========================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StockAdjustmentDialog(
    products: List<ProductItem>,
    locations: List<InventoryLocation>,
    initialProduct: ProductItem?,
    onDismiss: () -> Unit,
    onConfirm: (productId: String, locationId: String, quantityDeltaMilli: Long, reason: StockAdjustmentReason, remarks: String) -> Unit
) {
    val tokens = PabaruTheme.tokens
    val context = LocalContext.current
    val availableLocations = remember(locations) {
        if (locations.isNotEmpty()) locations else listOf(InventoryLocation("LOC-MAIN", "Main Store", "MAIN"))
    }
    var selectedProduct by remember { mutableStateOf(initialProduct ?: products.firstOrNull()) }
    var selectedLocation by remember { mutableStateOf(availableLocations.firstOrNull()) }
    var isAddition by remember { mutableStateOf(false) } // false = decrease, true = increase
    var quantityText by remember { mutableStateOf("") }
    var selectedReason by remember { mutableStateOf(StockAdjustmentReason.COUNT_CORRECTION) }
    var remarks by remember { mutableStateOf("") }

    var productDropdownExpanded by remember { mutableStateOf(false) }
    var locationDropdownExpanded by remember { mutableStateOf(false) }
    var reasonDropdownExpanded by remember { mutableStateOf(false) }

    val unitAllowsDec = allowsDecimal(selectedProduct?.unit ?: "pcs")
    val parsedQtyMilli = remember(quantityText, unitAllowsDec) {
        val d = quantityText.trim().toDoubleOrNull() ?: 0.0
        if (d <= 0.0) 0L else if (unitAllowsDec) (d * 1000.0).roundToLong() else d.roundToLong() * 1000L
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.95f),
            shape = RoundedCornerShape(16.dp),
            color = tokens.card,
            border = BorderStroke(1.dp, tokens.cardBorder)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Stock Adjustment", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = tokens.text)
                Text("Record damage, theft, expiry or manual audit corrections", fontSize = 11.sp, color = tokens.textMuted)

                Spacer(modifier = Modifier.height(14.dp))

                // Product selector
                Text("Selected Item", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = tokens.textMuted)
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { productDropdownExpanded = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(selectedProduct?.name ?: if (products.isEmpty()) "No products available" else "Select Product", color = tokens.text)
                    }
                    DropdownMenu(
                        expanded = productDropdownExpanded,
                        onDismissRequest = { productDropdownExpanded = false },
                        modifier = Modifier.background(tokens.card)
                    ) {
                        products.forEach { prod ->
                            DropdownMenuItem(
                                text = { Text("${prod.name} (Stock: ${FinancialEngine.formatQuantityMilli(prod.currentStockMilli)} ${prod.unit})", color = tokens.text) },
                                onClick = {
                                    selectedProduct = prod
                                    productDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Location selector
                Text("Store / Warehouse Location", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = tokens.textMuted)
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { locationDropdownExpanded = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(selectedLocation?.name ?: "Main Store", color = tokens.text)
                    }
                    DropdownMenu(
                        expanded = locationDropdownExpanded,
                        onDismissRequest = { locationDropdownExpanded = false },
                        modifier = Modifier.background(tokens.card)
                    ) {
                        availableLocations.forEach { loc ->
                            DropdownMenuItem(
                                text = { Text("${loc.name} (${loc.code})", color = tokens.text) },
                                onClick = {
                                    selectedLocation = loc
                                    locationDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Action Type: Increase / Decrease
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = !isAddition,
                        onClick = { isAddition = false },
                        label = { Text("Decrease (-)", fontSize = 12.sp) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = tokens.dangerContainer,
                            selectedLabelColor = tokens.onDangerContainer
                        )
                    )
                    FilterChip(
                        selected = isAddition,
                        onClick = { isAddition = true },
                        label = { Text("Increase (+)", fontSize = 12.sp) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = tokens.successContainer,
                            selectedLabelColor = tokens.onSuccessContainer
                        )
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Quantity
                OutlinedTextField(
                    value = quantityText,
                    onValueChange = {
                        quantityText = if (unitAllowsDec) FinancialEngine.sanitizeDecimalInput(it) else it.filter { c -> c.isDigit() }
                    },
                    label = { Text("Quantity (${selectedProduct?.unit ?: "pcs"})") },
                    keyboardOptions = KeyboardOptions(keyboardType = if (unitAllowsDec) KeyboardType.Decimal else KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Reason dropdown
                Text("Adjustment Reason", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = tokens.textMuted)
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { reasonDropdownExpanded = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(selectedReason.displayName, color = tokens.text)
                    }
                    DropdownMenu(
                        expanded = reasonDropdownExpanded,
                        onDismissRequest = { reasonDropdownExpanded = false },
                        modifier = Modifier.background(tokens.card)
                    ) {
                        StockAdjustmentReason.values().forEach { r ->
                            DropdownMenuItem(
                                text = { Text(r.displayName, color = tokens.text) },
                                onClick = {
                                    selectedReason = r
                                    reasonDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Remarks
                OutlinedTextField(
                    value = remarks,
                    onValueChange = { remarks = it },
                    label = { Text("Remarks / Audit Note") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val prod = selectedProduct
                            if (prod == null || parsedQtyMilli <= 0L) {
                                Toast.makeText(context, "Select a product and enter a positive quantity", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            val deltaMilli = if (isAddition) parsedQtyMilli else -parsedQtyMilli
                            val locId = selectedLocation?.id ?: "LOC-MAIN"
                            onConfirm(prod.id, locId, deltaMilli, selectedReason, remarks)
                        },
                        enabled = selectedProduct != null && parsedQtyMilli > 0L,
                        modifier = Modifier.heightIn(min = 48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = tokens.primary)
                    ) {
                        Text("Confirm Adjustment", fontWeight = FontWeight.Bold, color = tokens.primaryText)
                    }
                }
            }
        }
    }
}

// ==========================================
// PURCHASE BILL INWARD DIALOG
// ==========================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PurchaseBillDialog(
    suppliers: List<Party>,
    allProducts: List<ProductItem>,
    homeStateCode: String,
    onDismiss: () -> Unit,
    onSave: (supplierId: String, billNo: String, date: String, pmtMode: PaymentMode, items: List<InvoiceLineItem>, batches: List<StockBatch>, remarks: String) -> Unit
) {
    val tokens = PabaruTheme.tokens
    val context = LocalContext.current
    var selectedSupplier by remember { mutableStateOf(suppliers.firstOrNull()) }
    var supplierDropdown by remember { mutableStateOf(false) }
    var billNumber by remember { mutableStateOf("PUR-${System.currentTimeMillis().toString().takeLast(4)}") }
    var billDate by remember { mutableStateOf(FinancialEngine.getTodayDate()) }
    var paymentMode by remember { mutableStateOf(PaymentMode.Credit) }
    var remarks by remember { mutableStateOf("") }

    // Line items for the purchase
    var selectedProduct by remember { mutableStateOf(allProducts.firstOrNull()) }
    var addQtyText by remember { mutableStateOf("10") }
    var addCostRupees by remember {
        mutableStateOf(
            allProducts.firstOrNull()?.let {
                String.format(Locale.ENGLISH, "%.2f", it.purchasePricePaise / 100.0).trimEnd('0').trimEnd('.')
            } ?: ""
        )
    }
    var batchNumberInput by remember { mutableStateOf("") }
    var expiryDateInput by remember { mutableStateOf("") }

    // Track each item together with its optional batch so removing a line item removes its batch cleanly
    val purchaseEntries = remember { mutableStateListOf<Pair<InvoiceLineItem, StockBatch?>>() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f),
            shape = RoundedCornerShape(20.dp),
            color = tokens.card,
            border = BorderStroke(1.dp, tokens.cardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                Text("Record Purchase Inward", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = tokens.text)
                Text("Receive supplier stock and update inventory balances", fontSize = 11.sp, color = tokens.textMuted)

                HorizontalDivider(color = tokens.cardBorder, modifier = Modifier.padding(vertical = 10.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Text("Supplier", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = tokens.textMuted)
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { supplierDropdown = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    selectedSupplier?.let { "${it.name}${if (it.stateCode.isNotBlank()) " (State ${it.stateCode})" else ""}" }
                                        ?: "Direct / Cash Supplier",
                                    color = tokens.text
                                )
                            }
                            DropdownMenu(
                                expanded = supplierDropdown,
                                onDismissRequest = { supplierDropdown = false },
                                modifier = Modifier.background(tokens.card)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Direct / Unregistered Supplier", color = tokens.textMuted) },
                                    onClick = {
                                        selectedSupplier = null
                                        supplierDropdown = false
                                    }
                                )
                                suppliers.forEach { s ->
                                    DropdownMenuItem(
                                        text = { Text("${s.name} ${if (s.gstin.isNotBlank()) "· ${s.gstin}" else ""}", color = tokens.text) },
                                        onClick = {
                                            selectedSupplier = s
                                            supplierDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = billNumber,
                                onValueChange = { billNumber = it },
                                label = { Text("Bill / Invoice No *") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp)
                            )
                            OutlinedTextField(
                                value = billDate,
                                onValueChange = { billDate = it },
                                label = { Text("Bill Date (YYYY-MM-DD)") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    }

                    item {
                        Text("Payment Mode", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = tokens.textMuted)
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(PaymentMode.Credit, PaymentMode.Cash, PaymentMode.Bank, PaymentMode.UPI).forEach { mode ->
                                FilterChip(
                                    selected = paymentMode == mode,
                                    onClick = { paymentMode = mode },
                                    modifier = Modifier.heightIn(min = 48.dp),
                                    label = { Text(mode.name, fontSize = 11.sp) }
                                )
                            }
                        }
                    }

                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = tokens.secondaryBg),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Add Items to Purchase Bill", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = tokens.text)
                                Spacer(modifier = Modifier.height(8.dp))

                                var prodDropdown by remember { mutableStateOf(false) }
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    OutlinedButton(
                                        onClick = { prodDropdown = true },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 48.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(selectedProduct?.name ?: if (allProducts.isEmpty()) "No items in inventory" else "Select Item", color = tokens.text)
                                    }
                                    DropdownMenu(
                                        expanded = prodDropdown,
                                        onDismissRequest = { prodDropdown = false },
                                        modifier = Modifier.background(tokens.card)
                                    ) {
                                        allProducts.forEach { p ->
                                            DropdownMenuItem(
                                                text = { Text("${p.name} (GST ${GstSlabs.label(p.gstRateBps)})", color = tokens.text) },
                                                onClick = {
                                                    selectedProduct = p
                                                    addCostRupees = if (p.purchasePricePaise > 0L) {
                                                        String.format(Locale.ENGLISH, "%.2f", p.purchasePricePaise / 100.0).trimEnd('0').trimEnd('.')
                                                    } else ""
                                                    prodDropdown = false
                                                }
                                            )
                                        }
                                    }
                                }

                                val unitAllowsDec = allowsDecimal(selectedProduct?.unit ?: "pcs")
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = addQtyText,
                                        onValueChange = {
                                            addQtyText = if (unitAllowsDec) FinancialEngine.sanitizeDecimalInput(it) else it.filter { c -> c.isDigit() }
                                        },
                                        label = { Text("Qty (${selectedProduct?.unit ?: "pcs"})") },
                                        keyboardOptions = KeyboardOptions(keyboardType = if (unitAllowsDec) KeyboardType.Decimal else KeyboardType.Number),
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = addCostRupees,
                                        onValueChange = { addCostRupees = FinancialEngine.sanitizeDecimalInput(it) },
                                        label = { Text("Unit Cost (₹)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                }

                                if (selectedProduct?.hasBatchTracking == true) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = batchNumberInput,
                                            onValueChange = { batchNumberInput = it },
                                            label = { Text("Batch No") },
                                            modifier = Modifier.weight(1f),
                                            singleLine = true
                                        )
                                        OutlinedTextField(
                                            value = expiryDateInput,
                                            onValueChange = { expiryDateInput = it },
                                            label = { Text("Expiry (YYYY-MM-DD)") },
                                            modifier = Modifier.weight(1f),
                                            singleLine = true
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        val prod = selectedProduct ?: return@Button
                                        val qtyDouble = addQtyText.trim().toDoubleOrNull() ?: 0.0
                                        val qtyMilli = if (allowsDecimal(prod.unit)) {
                                            (qtyDouble * 1000.0).roundToLong()
                                        } else {
                                            qtyDouble.roundToLong() * 1000L
                                        }
                                        val costPaise = FinancialEngine.parsePaiseFromRupees(addCostRupees)
                                        if (qtyMilli <= 0L || costPaise <= 0L) {
                                            Toast.makeText(context, "Enter a valid positive quantity and unit cost", Toast.LENGTH_SHORT).show()
                                            return@Button
                                        }

                                        val normalizedExpiry = if (expiryDateInput.isNotBlank()) {
                                            FinancialEngine.normalizeDate(expiryDateInput).ifEmpty {
                                                Toast.makeText(context, "Invalid expiry date (use YYYY-MM-DD)", Toast.LENGTH_SHORT).show()
                                                return@Button
                                            }
                                        } else ""

                                        val taxablePaise = FinancialEngine.lineTaxable(qtyMilli, costPaise)
                                        val supplierState = selectedSupplier?.stateCode?.trim().orEmpty()
                                        val isInterstate = supplierState.isNotEmpty() && homeStateCode.isNotBlank() && supplierState != homeStateCode.trim()
                                        val gst = FinancialEngine.calculateGstBps(taxablePaise, prod.gstRateBps, isInterstate)

                                        val lineItem = InvoiceLineItem(
                                            itemId = prod.id,
                                            name = prod.name,
                                            hsnCode = prod.hsnCode,
                                            quantityMilli = qtyMilli,
                                            unit = prod.unit,
                                            ratePaise = costPaise,
                                            gstRateBps = prod.gstRateBps,
                                            taxableAmountPaise = taxablePaise,
                                            cgstPaise = gst.cgstPaise,
                                            sgstPaise = gst.sgstPaise,
                                            igstPaise = gst.igstPaise,
                                            totalPaise = gst.totalPaise
                                        )

                                        if (prod.hasBatchTracking && batchNumberInput.isBlank()) {
                                            Toast.makeText(context, "Enter a batch number for batch-tracked item", Toast.LENGTH_SHORT).show()
                                            return@Button
                                        }

                                        val batch = if (prod.hasBatchTracking) {
                                            StockBatch(
                                                id = "BATCH-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(4)}",
                                                productId = prod.id,
                                                batchNumber = batchNumberInput.trim(),
                                                mfgDate = FinancialEngine.normalizeDate(billDate).ifEmpty { FinancialEngine.getTodayDate() },
                                                expiryDate = normalizedExpiry,
                                                purchasePricePaise = costPaise,
                                                initialQuantityMilli = qtyMilli,
                                                availableQuantityMilli = qtyMilli
                                            )
                                        } else null

                                        purchaseEntries.add(lineItem to batch)

                                        addQtyText = "1"
                                        batchNumberInput = ""
                                        expiryDateInput = ""
                                    },
                                    modifier = Modifier
                                        .align(Alignment.End)
                                        .heightIn(min = 48.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = tokens.accent)
                                ) {
                                    Text("+ Add to Bill", color = tokens.primaryText, fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    // Added items summary
                    if (purchaseEntries.isNotEmpty()) {
                        item {
                            Text("Bill Items (${purchaseEntries.size}):", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = tokens.text)
                        }
                        items(purchaseEntries) { entry ->
                            val itm = entry.first
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(tokens.secondaryBg, RoundedCornerShape(8.dp))
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(itm.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = tokens.text)
                                    val taxSum = itm.cgstPaise + itm.sgstPaise + itm.igstPaise
                                    Text(
                                        "${FinancialEngine.formatQuantityMilli(itm.quantityMilli)} ${itm.unit} x ${FinancialEngine.formatPaise(itm.ratePaise)} + GST ${FinancialEngine.formatPaise(taxSum)}",
                                        fontSize = 11.sp,
                                        color = tokens.textMuted
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(FinancialEngine.formatPaise(itm.totalPaise), fontWeight = FontWeight.Bold, color = tokens.accent)
                                    IconButton(
                                        onClick = { purchaseEntries.remove(entry) },
                                        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Remove", tint = tokens.danger, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                val totalBillPaise = purchaseEntries.sumOf { it.first.totalPaise }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("TOTAL AMOUNT (INCL. GST)", fontSize = 10.sp, color = tokens.textMuted)
                        Text(FinancialEngine.formatPaise(totalBillPaise), fontSize = 18.sp, fontWeight = FontWeight.Black, color = tokens.primary)
                    }

                    Row {
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) { Text("Cancel") }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (purchaseEntries.isEmpty() || billNumber.isBlank()) {
                                    Toast.makeText(context, "Add at least one item and bill number", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                val normDate = FinancialEngine.normalizeDate(billDate)
                                if (normDate.isEmpty()) {
                                    Toast.makeText(context, "Enter a valid bill date (YYYY-MM-DD)", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                val suppId = selectedSupplier?.id ?: "SUPP-DIRECT"
                                onSave(
                                    suppId,
                                    billNumber.trim(),
                                    normDate,
                                    paymentMode,
                                    purchaseEntries.map { it.first },
                                    purchaseEntries.mapNotNull { it.second },
                                    remarks
                                )
                            },
                            enabled = purchaseEntries.isNotEmpty() && billNumber.isNotBlank(),
                            modifier = Modifier.heightIn(min = 48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = tokens.primary)
                        ) {
                            Text("Save Inward", fontWeight = FontWeight.Bold, color = tokens.primaryText)
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// STOCK LOCATION TRANSFER DIALOG
// ==========================================

@Composable
private fun StockTransferDialog(
    products: List<ProductItem>,
    locations: List<InventoryLocation>,
    onDismiss: () -> Unit,
    onConfirm: (productId: String, fromLocationId: String, toLocationId: String, quantityMilli: Long, remarks: String) -> Unit
) {
    val tokens = PabaruTheme.tokens
    val context = LocalContext.current
    val locationOptions = remember(locations) {
        (locations + listOf(
            InventoryLocation("LOC-MAIN", "Main Store", "MAIN"),
            InventoryLocation("LOC-WAREHOUSE", "Warehouse", "WH")
        )).distinctBy { it.id }
    }

    var selectedProduct by remember { mutableStateOf(products.firstOrNull()) }
    var fromLoc by remember { mutableStateOf(locationOptions.first()) }
    var toLoc by remember { mutableStateOf(locationOptions.getOrNull(1) ?: locationOptions.first()) }
    var quantityText by remember { mutableStateOf("1") }
    var remarks by remember { mutableStateOf("") }

    var prodDropdown by remember { mutableStateOf(false) }
    var fromDropdown by remember { mutableStateOf(false) }
    var toDropdown by remember { mutableStateOf(false) }

    val unitAllowsDec = allowsDecimal(selectedProduct?.unit ?: "pcs")

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.95f),
            shape = RoundedCornerShape(16.dp),
            color = tokens.card,
            border = BorderStroke(1.dp, tokens.cardBorder)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Stock Location Transfer", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = tokens.text)
                Text("Move physical stock between warehouse and store outlets", fontSize = 11.sp, color = tokens.textMuted)

                Spacer(modifier = Modifier.height(14.dp))

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { prodDropdown = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(selectedProduct?.name ?: if (products.isEmpty()) "No products available" else "Select Product", color = tokens.text)
                    }
                    DropdownMenu(
                        expanded = prodDropdown,
                        onDismissRequest = { prodDropdown = false },
                        modifier = Modifier.background(tokens.card)
                    ) {
                        products.forEach { p ->
                            DropdownMenuItem(
                                text = { Text("${p.name} (Stock: ${FinancialEngine.formatQuantityMilli(p.currentStockMilli)} ${p.unit})", color = tokens.text) },
                                onClick = {
                                    selectedProduct = p
                                    prodDropdown = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedButton(
                            onClick = { fromDropdown = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("From: ${fromLoc.name}", color = tokens.text, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
                        }
                        DropdownMenu(
                            expanded = fromDropdown,
                            onDismissRequest = { fromDropdown = false },
                            modifier = Modifier.background(tokens.card)
                        ) {
                            locationOptions.forEach { loc ->
                                DropdownMenuItem(
                                    text = { Text("${loc.name} (${loc.code})", color = tokens.text) },
                                    onClick = {
                                        fromLoc = loc
                                        fromDropdown = false
                                    }
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedButton(
                            onClick = { toDropdown = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("To: ${toLoc.name}", color = tokens.text, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
                        }
                        DropdownMenu(
                            expanded = toDropdown,
                            onDismissRequest = { toDropdown = false },
                            modifier = Modifier.background(tokens.card)
                        ) {
                            locationOptions.forEach { loc ->
                                DropdownMenuItem(
                                    text = { Text("${loc.name} (${loc.code})", color = tokens.text) },
                                    onClick = {
                                        toLoc = loc
                                        toDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = quantityText,
                    onValueChange = {
                        quantityText = if (unitAllowsDec) FinancialEngine.sanitizeDecimalInput(it) else it.filter { c -> c.isDigit() }
                    },
                    label = { Text("Transfer Quantity (${selectedProduct?.unit ?: "pcs"})") },
                    keyboardOptions = KeyboardOptions(keyboardType = if (unitAllowsDec) KeyboardType.Decimal else KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = remarks,
                    onValueChange = { remarks = it },
                    label = { Text("Remarks") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val p = selectedProduct ?: return@Button
                            if (fromLoc.id == toLoc.id) {
                                Toast.makeText(context, "Source and destination locations must be different", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            val qtyDouble = quantityText.trim().toDoubleOrNull() ?: 0.0
                            val qtyMilli = if (allowsDecimal(p.unit)) (qtyDouble * 1000.0).roundToLong() else qtyDouble.roundToLong() * 1000L
                            if (qtyMilli <= 0L) {
                                Toast.makeText(context, "Enter a valid positive quantity", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (qtyMilli > p.currentStockMilli) {
                                Toast.makeText(
                                    context,
                                    "Cannot transfer ${FinancialEngine.formatQuantityMilli(qtyMilli)} ${p.unit}; only ${FinancialEngine.formatQuantityMilli(p.currentStockMilli)} ${p.unit} in stock",
                                    Toast.LENGTH_SHORT
                                ).show()
                                return@Button
                            }
                            onConfirm(p.id, fromLoc.id, toLoc.id, qtyMilli, remarks)
                        },
                        enabled = selectedProduct != null,
                        modifier = Modifier.heightIn(min = 48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = tokens.primary)
                    ) {
                        Text("Transfer Stock", fontWeight = FontWeight.Bold, color = tokens.primaryText)
                    }
                }
            }
        }
    }
}

// ==========================================
// STOCK MOVEMENTS & AUDIT LEDGER DIALOG
// ==========================================

@Composable
private fun StockMovementsDialog(
    movements: List<StockMovement>,
    filterProduct: ProductItem?,
    onDismiss: () -> Unit
) {
    val tokens = PabaruTheme.tokens
    val filtered = if (filterProduct != null) {
        movements.filter { it.productId == filterProduct.id }
    } else movements

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(20.dp),
            color = tokens.card,
            border = BorderStroke(1.dp, tokens.cardBorder)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (filterProduct != null) "Stock Log: ${filterProduct.name}" else "Global Stock Movement Audit",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = tokens.text
                        )
                        Text(
                            text = "${filtered.size} movements logged",
                            fontSize = 11.sp,
                            color = tokens.textMuted
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = tokens.textMuted)
                    }
                }

                HorizontalDivider(color = tokens.cardBorder, modifier = Modifier.padding(vertical = 10.dp))

                if (filtered.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No stock movements recorded yet.", color = tokens.textMuted, fontSize = 13.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filtered) { m ->
                            val isPositive = m.quantityDeltaMilli >= 0L
                            val deltaColor = if (isPositive) tokens.success else tokens.danger
                            val deltaText = if (m.quantityDeltaMilli >= 0L) {
                                "+${FinancialEngine.formatQuantityMilli(m.quantityDeltaMilli)}"
                            } else {
                                "-${FinancialEngine.formatQuantityMilli(-m.quantityDeltaMilli)}"
                            }

                            Card(
                                colors = CardDefaults.cardColors(containerColor = tokens.secondaryBg),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(m.productName, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = tokens.text)
                                        Text("${m.movementType} · ${m.timestamp}", fontSize = 10.sp, color = tokens.textMuted)
                                        if (m.remarks.isNotBlank()) {
                                            Text(m.remarks, fontSize = 10.sp, color = tokens.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = deltaText,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Black,
                                            color = deltaColor
                                        )
                                        Text(
                                            text = "Bal: ${FinancialEngine.formatQuantityMilli(m.balanceAfterMilli)}",
                                            fontSize = 10.sp,
                                            color = tokens.textMuted
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// STOCK VALUATION & EXPORT DIALOG
// ==========================================

@Composable
private fun StockValuationDialog(
    products: List<ProductItem>,
    movements: List<StockMovement>,
    totalPurchaseValuationPaise: Long,
    totalSaleValuationPaise: Long,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val tokens = PabaruTheme.tokens

    val potentialProfitPaise = totalSaleValuationPaise - totalPurchaseValuationPaise
    val grossMarginPercent = if (totalSaleValuationPaise > 0L) {
        (potentialProfitPaise * 100) / totalSaleValuationPaise
    } else 0L

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(20.dp),
            color = tokens.card,
            border = BorderStroke(1.dp, tokens.cardBorder)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Stock Valuation & Fast Movers", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = tokens.text)
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = tokens.textMuted)
                    }
                }

                HorizontalDivider(color = tokens.cardBorder, modifier = Modifier.padding(vertical = 10.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = tokens.secondaryBg),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text("FINANCIAL INVENTORY METRICS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = tokens.textMuted)
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Cost Valuation (FIFO / Cost)", fontSize = 12.sp, color = tokens.text)
                                    Text(FinancialEngine.formatPaise(totalPurchaseValuationPaise), fontWeight = FontWeight.Bold, color = tokens.primary)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Retail Market Valuation", fontSize = 12.sp, color = tokens.text)
                                    Text(FinancialEngine.formatPaise(totalSaleValuationPaise), fontWeight = FontWeight.Bold, color = tokens.accent)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Potential Gross Margin", fontSize = 12.sp, color = tokens.text)
                                    Text("${FinancialEngine.formatPaise(potentialProfitPaise)} ($grossMarginPercent%)", fontWeight = FontWeight.Bold, color = tokens.success)
                                }
                            }
                        }
                    }

                    item {
                        Text("Top Inventory Holdings by Value", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = tokens.text)
                    }

                    val sortedByValue = products.sortedByDescending { (it.currentStockMilli * it.purchasePricePaise + 500L) / 1000L }
                    items(sortedByValue.take(6)) { prod ->
                        val valuePaise = (prod.currentStockMilli * prod.purchasePricePaise + 500L) / 1000L
                        val stockDisplay = FinancialEngine.formatQuantityMilli(prod.currentStockMilli)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(prod.name, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = tokens.text)
                                Text("$stockDisplay ${prod.unit} @ ${FinancialEngine.formatPaise(prod.purchasePricePaise)}", fontSize = 10.sp, color = tokens.textMuted)
                            }
                            Text(FinancialEngine.formatPaise(valuePaise), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = tokens.text)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        if (products.isEmpty()) {
                            Toast.makeText(context, "No products available to export", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        fun csvEsc(s: String): String = "\"${s.replace("\"", "\"\"")}\""
                        // Export stock report to CSV via Share Intent
                        val csv = buildString {
                            append("Product Name,SKU,Barcode,Category,Unit,Current Stock,Reorder Level,Purchase Cost (Rs),Selling Price (Rs),Valuation (Rs)\n")
                            products.forEach { p ->
                                val costRs = String.format(Locale.ENGLISH, "%.2f", p.purchasePricePaise / 100.0)
                                val saleRs = String.format(Locale.ENGLISH, "%.2f", p.salePricePaise / 100.0)
                                val valPaise = (p.currentStockMilli * p.purchasePricePaise + 500L) / 1000L
                                val valRs = String.format(Locale.ENGLISH, "%.2f", valPaise / 100.0)
                                val stockQty = FinancialEngine.formatQuantityMilli(p.currentStockMilli)
                                val reorderQty = FinancialEngine.formatQuantityMilli(p.reorderLevelMilli)
                                append("${csvEsc(p.name)},${csvEsc(p.sku)},${csvEsc(p.barcode)},${csvEsc(p.category)},${csvEsc(p.unit)},$stockQty,$reorderQty,$costRs,$saleRs,$valRs\n")
                            }
                        }

                        ExportEngine.shareTextContentAsFile(
                            context = context,
                            fileName = "Pabaru_Stock_Report.csv",
                            mimeType = "text/csv",
                            content = csv,
                            subject = "Pabaru Stock Valuation Report",
                            summaryText = "Inventory valuation report (${products.size} items)",
                            chooserTitle = "Share Stock Valuation CSV"
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = tokens.primary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Outlined.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Export Stock CSV", fontWeight = FontWeight.Bold, color = tokens.primaryText)
                }
            }
        }
    }
}
