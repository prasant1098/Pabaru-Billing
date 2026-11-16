package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ManagedUserEntity
import com.example.engine.FinancialEngine
import com.example.ui.theme.PabaruTheme
import com.example.ui.theme.danger
import com.example.ui.theme.info
import com.example.ui.theme.success
import com.example.ui.theme.warning

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuperAdminScreen(
    managedUsers: List<ManagedUserEntity>,
    onToggleUserStatus: (storeCode: String, isActive: Boolean) -> Unit,
    onUpdateUserBenefits: (
        storeCode: String,
        isGst: Boolean,
        isRecon: Boolean,
        isInvoice: Boolean,
        isInv: Boolean,
        isCma: Boolean,
        isStaff: Boolean,
        isExport: Boolean,
        isOverride: Boolean
    ) -> Unit,
    onAddNewUser: (ManagedUserEntity) -> Unit,
    onDeleteUser: (storeCode: String) -> Unit,
    onImpersonateUser: (storeCode: String) -> Unit,
    onExitSuperAdmin: () -> Unit,
    syncStatus: String = "All permissions persisted locally",
    lastSyncTime: Long = System.currentTimeMillis(),
    isSyncing: Boolean = false,
    onTriggerSync: () -> Unit = {},
    onBulkToggleUserStatus: (storeCodes: List<String>, isActive: Boolean) -> Unit = { _, _ -> },
    onBulkUpdateUserBenefits: (
        storeCodes: List<String>,
        isGst: Boolean,
        isRecon: Boolean,
        isInvoice: Boolean,
        isInv: Boolean,
        isCma: Boolean,
        isStaff: Boolean,
        isExport: Boolean,
        isOverride: Boolean
    ) -> Unit = { _, _, _, _, _, _, _, _, _ -> }
) {
    val context = LocalContext.current
    val tokens = PabaruTheme.tokens
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("ALL") } // ALL, ACTIVE, SUSPENDED
    var showAddUserDialog by remember { mutableStateOf(false) }
    var showBulkPermissionsDialog by remember { mutableStateOf(false) }
    var isBulkMode by remember { mutableStateOf(false) }
    var selectedStoreCodes by remember { mutableStateOf(setOf<String>()) }

    BackHandler {
        if (isBulkMode) {
            isBulkMode = false
            selectedStoreCodes = emptySet()
        } else {
            onExitSuperAdmin()
        }
    }

    val filteredUsers = managedUsers.filter { user ->
        val matchesFilter = when (selectedFilter) {
            "ACTIVE" -> user.isActive
            "SUSPENDED" -> !user.isActive
            else -> true
        }
        val q = searchQuery.trim().lowercase()
        val matchesQuery = q.isEmpty() ||
            user.storeCode.lowercase().contains(q) ||
            user.businessName.lowercase().contains(q) ||
            user.ownerName.lowercase().contains(q) ||
            user.phone.lowercase().contains(q) ||
            user.email.lowercase().contains(q)

        matchesFilter && matchesQuery
    }

    val totalUsers = managedUsers.size
    val activeCount = managedUsers.count { it.isActive }
    val suspendedCount = managedUsers.count { !it.isActive }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.bg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp)
                .align(Alignment.TopCenter)
        ) {
            // Top Bar
            Surface(
                color = tokens.headerBg,
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(0.5.dp, tokens.cardBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        IconButton(
                            onClick = onExitSuperAdmin,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(tokens.card)
                                .testTag("super_admin_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Exit Super Admin",
                                tint = tokens.text
                            )
                        }

                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Super Admin Console",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = tokens.headerText
                                )
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = tokens.warning.copy(alpha = 0.2f),
                                    border = BorderStroke(0.5.dp, tokens.warning)
                                ) {
                                    Text(
                                        text = "MASTER CONSOLE",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = tokens.warning,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Local Database & Server Auth Management",
                                fontSize = 11.5.sp,
                                color = tokens.headerText.copy(alpha = 0.75f)
                            )
                        }
                    }

                    Button(
                        onClick = { showAddUserDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = tokens.success),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("add_store_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = tokens.primaryText,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Add Store",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = tokens.primaryText
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .testTag("super_admin_lazy_column"),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(top = 14.dp, bottom = 48.dp)
            ) {
                // Live Offline-First Synchronization Status Card
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = tokens.card,
                        border = BorderStroke(1.dp, if (isSyncing) tokens.info else tokens.cardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("super_admin_live_sync_card")
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(if (isSyncing) tokens.info else tokens.success)
                                    )
                                    Column {
                                        Text(
                                            text = "Offline-First Sync Engine",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = tokens.text
                                        )
                                        Text(
                                            text = syncStatus,
                                            fontSize = 11.5.sp,
                                            color = if (isSyncing) tokens.info else tokens.success
                                        )
                                    }
                                }

                                Button(
                                    onClick = {
                                        onTriggerSync()
                                        Toast.makeText(context, "Local permissions synchronized", Toast.LENGTH_SHORT).show()
                                    },
                                    enabled = !isSyncing,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = tokens.info.copy(alpha = 0.2f),
                                        contentColor = tokens.info
                                    ),
                                    border = BorderStroke(1.dp, tokens.info),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("super_admin_sync_now_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Sync,
                                        contentDescription = "Sync Now",
                                        tint = tokens.info,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isSyncing) "Syncing..." else "Sync Now",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider(color = tokens.cardBorder.copy(alpha = 0.5f), thickness = 0.5.dp)
                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.Storage, contentDescription = null, tint = tokens.textMuted, modifier = Modifier.size(13.dp))
                                    Text(
                                        text = "Room SQLite: pabaru_billing.db",
                                        fontSize = 10.5.sp,
                                        color = tokens.textMuted
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.CloudDone, contentDescription = null, tint = tokens.textMuted, modifier = Modifier.size(13.dp))
                                    Text(
                                        text = "0 Pending Cloud Syncs",
                                        fontSize = 10.5.sp,
                                        color = tokens.textMuted
                                    )
                                }
                            }
                        }
                    }
                }
                // Overview & Stats Bar
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = tokens.card,
                        border = BorderStroke(1.dp, tokens.cardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(tokens.success)
                                    )
                                    Text(
                                        text = "Architecture: Complete Local Database (Room)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = tokens.success
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = tokens.info.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "Server Auth Ready",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = tokens.info,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                StatCard(
                                    title = "Total Stores",
                                    value = totalUsers.toString(),
                                    color = tokens.info,
                                    modifier = Modifier.weight(1f)
                                )
                                StatCard(
                                    title = "Active Users",
                                    value = activeCount.toString(),
                                    color = tokens.success,
                                    modifier = Modifier.weight(1f)
                                )
                                StatCard(
                                    title = "Suspended",
                                    value = suspendedCount.toString(),
                                    color = if (suspendedCount > 0) tokens.danger else tokens.textMuted,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                // Search & Filter Row
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search by Store Code, Name, or Phone...", fontSize = 13.sp, color = tokens.textMuted) },
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = null, tint = tokens.textMuted, modifier = Modifier.size(20.dp))
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(
                                        onClick = { searchQuery = "" },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = tokens.textMuted, modifier = Modifier.size(18.dp))
                                    }
                                }
                            },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = tokens.card,
                                unfocusedContainerColor = tokens.card,
                                focusedBorderColor = tokens.info,
                                unfocusedBorderColor = tokens.cardBorder,
                                focusedTextColor = tokens.text,
                                unfocusedTextColor = tokens.text
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("super_admin_search_field")
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilterChipItem(
                                label = "All Stores ($totalUsers)",
                                selected = selectedFilter == "ALL",
                                onClick = { selectedFilter = "ALL" }
                            )
                            FilterChipItem(
                                label = "Active ($activeCount)",
                                selected = selectedFilter == "ACTIVE",
                                onClick = { selectedFilter = "ACTIVE" }
                            )
                            FilterChipItem(
                                label = "Suspended ($suspendedCount)",
                                selected = selectedFilter == "SUSPENDED",
                                onClick = { selectedFilter = "SUSPENDED" }
                            )
                        }

                        // Bulk-Selection Mode Toolbar Toggle & Select All
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilledTonalButton(
                                onClick = {
                                    isBulkMode = !isBulkMode
                                    if (!isBulkMode) {
                                        selectedStoreCodes = emptySet()
                                    }
                                },
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = if (isBulkMode) tokens.info.copy(alpha = 0.25f) else tokens.card,
                                    contentColor = if (isBulkMode) tokens.info else tokens.textMuted
                                ),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(0.8.dp, if (isBulkMode) tokens.info else tokens.cardBorder),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("toggle_bulk_mode_button")
                            ) {
                                Icon(
                                    imageVector = if (isBulkMode) Icons.Default.Checklist else Icons.Outlined.Checklist,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isBulkMode) "Exit Bulk Mode" else "Select Multiple",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            if (isBulkMode) {
                                val allSelected = filteredUsers.isNotEmpty() && filteredUsers.all { selectedStoreCodes.contains(it.storeCode) }
                                TextButton(
                                    onClick = {
                                        selectedStoreCodes = if (allSelected) {
                                            emptySet()
                                        } else {
                                            filteredUsers.map { it.storeCode }.toSet()
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("bulk_select_all_button")
                                ) {
                                    Text(
                                        text = if (allSelected) "Deselect All" else "Select All (${filteredUsers.size})",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = tokens.info
                                    )
                                }
                            }
                        }
                    }
                }

                if (filteredUsers.isEmpty()) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = tokens.card,
                            border = BorderStroke(1.dp, tokens.cardBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(32.dp)
                                    .fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.PersonOff,
                                    contentDescription = null,
                                    tint = tokens.textMuted,
                                    modifier = Modifier.size(40.dp)
                                )
                                Text(
                                    text = "No matching store records found",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = tokens.text
                                )
                                Text(
                                    text = "Try clearing search filter or tap '+ Add Store' to register a new tenant.",
                                    fontSize = 12.sp,
                                    color = tokens.textMuted
                                )
                            }
                        }
                    }
                } else {
                    items(filteredUsers, key = { it.storeCode }) { user ->
                        val isSelected = selectedStoreCodes.contains(user.storeCode)
                        ManagedUserCard(
                            user = user,
                            isBulkMode = isBulkMode,
                            isSelected = isSelected,
                            onToggleSelect = {
                                selectedStoreCodes = if (isSelected) {
                                    selectedStoreCodes - user.storeCode
                                } else {
                                    selectedStoreCodes + user.storeCode
                                }
                            },
                            onToggleStatus = { isActive ->
                                onToggleUserStatus(user.storeCode, isActive)
                                val msg = if (isActive) "Activated store ${user.storeCode}" else "Suspended store ${user.storeCode}"
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            },
                            onUpdateBenefits = { isGst, isRecon, isInvoice, isInv, isCma, isStaff, isExport, isOverride ->
                                onUpdateUserBenefits(user.storeCode, isGst, isRecon, isInvoice, isInv, isCma, isStaff, isExport, isOverride)
                                Toast.makeText(context, "Permissions updated for ${user.storeCode}", Toast.LENGTH_SHORT).show()
                            },
                            onDelete = {
                                onDeleteUser(user.storeCode)
                                Toast.makeText(context, "Removed store ${user.storeCode}", Toast.LENGTH_SHORT).show()
                            },
                            onImpersonate = {
                                onImpersonateUser(user.storeCode)
                            }
                        )
                    }
                }
            }
        }

        // Floating Bottom Bulk Action Bar
        AnimatedVisibility(
            visible = isBulkMode && selectedStoreCodes.isNotEmpty(),
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .widthIn(max = 680.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = tokens.card,
                border = BorderStroke(1.dp, tokens.info.copy(alpha = 0.6f)),
                shadowElevation = 12.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("bulk_action_bar")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "${selectedStoreCodes.size} Selected",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = tokens.info
                        )
                        Text(
                            text = "Bulk actions",
                            fontSize = 11.sp,
                            color = tokens.textMuted
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Bulk Activate Button
                        Button(
                            onClick = {
                                if (selectedStoreCodes.isNotEmpty()) {
                                    val count = selectedStoreCodes.size
                                    onBulkToggleUserStatus(selectedStoreCodes.toList(), true)
                                    Toast.makeText(context, "Activated $count store(s)", Toast.LENGTH_SHORT).show()
                                    selectedStoreCodes = emptySet()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = tokens.success,
                                contentColor = tokens.primaryText
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("bulk_activate_button")
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Activate", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }

                        // Bulk Suspend Button
                        Button(
                            onClick = {
                                if (selectedStoreCodes.isNotEmpty()) {
                                    val count = selectedStoreCodes.size
                                    onBulkToggleUserStatus(selectedStoreCodes.toList(), false)
                                    Toast.makeText(context, "Suspended $count store(s)", Toast.LENGTH_SHORT).show()
                                    selectedStoreCodes = emptySet()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = tokens.danger,
                                contentColor = tokens.primaryText
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("bulk_suspend_button")
                        ) {
                            Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Suspend", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }

                        // Bulk Permissions Button
                        Button(
                            onClick = {
                                showBulkPermissionsDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = tokens.warning,
                                contentColor = tokens.primaryText
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("bulk_permissions_button")
                        ) {
                            Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Permissions", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }

                        // Clear Selection Button
                        IconButton(
                            onClick = {
                                selectedStoreCodes = emptySet()
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("bulk_clear_button")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Clear Selection", tint = tokens.textMuted, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }

    if (showBulkPermissionsDialog) {
        BulkPermissionsDialog(
            selectedCount = selectedStoreCodes.size,
            onDismiss = { showBulkPermissionsDialog = false },
            onApply = { isGst, isRecon, isInvoice, isInv, isCma, isStaff, isExport, isOverride ->
                val count = selectedStoreCodes.size
                onBulkUpdateUserBenefits(
                    selectedStoreCodes.toList(),
                    isGst, isRecon, isInvoice, isInv, isCma, isStaff, isExport, isOverride
                )
                Toast.makeText(context, "Updated permissions for $count stores", Toast.LENGTH_SHORT).show()
                showBulkPermissionsDialog = false
                selectedStoreCodes = emptySet()
            }
        )
    }

    if (showAddUserDialog) {
        AddNewStoreDialog(
            onDismiss = { showAddUserDialog = false },
            onConfirm = { newUser ->
                onAddNewUser(newUser)
                showAddUserDialog = false
                Toast.makeText(context, "Store ${newUser.storeCode} registered successfully", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    val tokens = PabaruTheme.tokens
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.08f),
        border = BorderStroke(0.5.dp, color.copy(alpha = 0.3f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(text = title, fontSize = 11.sp, color = tokens.textMuted)
            Text(text = value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
private fun FilterChipItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val tokens = PabaruTheme.tokens
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (selected) tokens.info.copy(alpha = 0.2f) else tokens.card,
        border = BorderStroke(0.5.dp, if (selected) tokens.info else tokens.cardBorder),
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clickable { onClick() }
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text(
                text = label,
                fontSize = 11.5.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) tokens.info else tokens.textMuted
            )
        }
    }
}

@Composable
private fun ManagedUserCard(
    user: ManagedUserEntity,
    isBulkMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelect: () -> Unit = {},
    onToggleStatus: (Boolean) -> Unit,
    onUpdateBenefits: (Boolean, Boolean, Boolean, Boolean, Boolean, Boolean, Boolean, Boolean) -> Unit,
    onDelete: () -> Unit,
    onImpersonate: () -> Unit
) {
    val tokens = PabaruTheme.tokens
    var expandedBenefits by remember { mutableStateOf(false) }

    var gstUnlocked by remember(user.isGstUnlocked) { mutableStateOf(user.isGstUnlocked) }
    var reconUnlocked by remember(user.isBankReconUnlocked) { mutableStateOf(user.isBankReconUnlocked) }
    var invoiceUnlocked by remember(user.isInvoiceUnlocked) { mutableStateOf(user.isInvoiceUnlocked) }
    var invUnlocked by remember(user.isInventoryUnlocked) { mutableStateOf(user.isInventoryUnlocked) }
    var cmaUnlocked by remember(user.isCmaUnlocked) { mutableStateOf(user.isCmaUnlocked) }
    var staffUnlocked by remember(user.isStaffUnlocked) { mutableStateOf(user.isStaffUnlocked) }
    var exportUnlocked by remember(user.isExportUnlocked) { mutableStateOf(user.isExportUnlocked) }
    var overrideUnlocked by remember(user.isManagerOverrideUnlocked) { mutableStateOf(user.isManagerOverrideUnlocked) }

    val activeModuleCount = listOf(
        gstUnlocked, reconUnlocked, invoiceUnlocked, invUnlocked,
        cmaUnlocked, staffUnlocked, exportUnlocked, overrideUnlocked
    ).count { it }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = tokens.card,
        border = BorderStroke(
            width = if (isSelected) 1.8.dp else if (user.isActive) 1.dp else 1.2.dp,
            color = if (isSelected) tokens.info else if (user.isActive) tokens.cardBorder else tokens.danger.copy(alpha = 0.5f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = isBulkMode) { onToggleSelect() }
            .testTag("managed_user_card_${user.storeCode}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header: Store Code, Business Name, Status Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    if (isBulkMode) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { onToggleSelect() },
                            colors = CheckboxDefaults.colors(
                                checkedColor = tokens.info,
                                uncheckedColor = tokens.textMuted,
                                checkmarkColor = tokens.primaryText
                            ),
                            modifier = Modifier.testTag("user_select_checkbox_${user.storeCode}")
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = tokens.warning.copy(alpha = 0.15f),
                        border = BorderStroke(0.5.dp, tokens.warning)
                    ) {
                        Text(
                            text = user.storeCode,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = tokens.warning,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    Column {
                        Text(
                            text = user.businessName,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = tokens.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${user.ownerName} · ${user.phone}",
                            fontSize = 11.sp,
                            color = tokens.textMuted
                        )
                    }
                }

                // Activation / Suspension Toggle Switch
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (user.isActive) tokens.success.copy(alpha = 0.15f) else tokens.danger.copy(alpha = 0.15f),
                        border = BorderStroke(0.5.dp, if (user.isActive) tokens.success else tokens.danger)
                    ) {
                        Text(
                            text = if (user.isActive) "ACTIVE" else "SUSPENDED",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (user.isActive) tokens.success else tokens.danger,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Switch(
                        checked = user.isActive,
                        onCheckedChange = { onToggleStatus(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = tokens.success,
                            checkedTrackColor = tokens.success.copy(alpha = 0.3f),
                            uncheckedThumbColor = tokens.danger,
                            uncheckedTrackColor = tokens.danger.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier.testTag("toggle_status_${user.storeCode}")
                    )
                }
            }

            // Contact / Credentials Info Bar
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = tokens.bg.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Email, contentDescription = null, tint = tokens.textMuted, modifier = Modifier.size(13.dp))
                        Text(text = user.email.ifEmpty { "No email set" }, fontSize = 11.sp, color = tokens.textMuted)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Schedule, contentDescription = null, tint = tokens.textMuted, modifier = Modifier.size(13.dp))
                        Text(text = "Created ${user.createdDate}", fontSize = 11.sp, color = tokens.textMuted)
                    }
                }
            }

            // Feature Access Permissions Title & Counter
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Feature Access Permissions (1-Tap Toggle):",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = tokens.textMuted
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = tokens.success.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "$activeModuleCount/8 Active",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = tokens.success,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // Interactive Module Chips (2x4 Grid with Direct One-Tap Toggling)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // Row 1: GST & Bank Recon
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    InteractivePermissionChip(
                        label = "GST Filing",
                        unlocked = gstUnlocked,
                        icon = Icons.Default.ReceiptLong,
                        activeColor = tokens.warning,
                        onClick = {
                            val next = !gstUnlocked
                            gstUnlocked = next
                            onUpdateBenefits(next, reconUnlocked, invoiceUnlocked, invUnlocked, cmaUnlocked, staffUnlocked, exportUnlocked, overrideUnlocked)
                        },
                        testTagId = "chip_permission_gst_${user.storeCode}",
                        modifier = Modifier.weight(1f)
                    )
                    InteractivePermissionChip(
                        label = "Bank Recon",
                        unlocked = reconUnlocked,
                        icon = Icons.Default.AccountBalance,
                        activeColor = tokens.info,
                        onClick = {
                            val next = !reconUnlocked
                            reconUnlocked = next
                            onUpdateBenefits(gstUnlocked, next, invoiceUnlocked, invUnlocked, cmaUnlocked, staffUnlocked, exportUnlocked, overrideUnlocked)
                        },
                        testTagId = "chip_permission_recon_${user.storeCode}",
                        modifier = Modifier.weight(1f)
                    )
                }

                // Row 2: Invoicing & Inventory
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    InteractivePermissionChip(
                        label = "GST Invoicing",
                        unlocked = invoiceUnlocked,
                        icon = Icons.Default.Description,
                        activeColor = tokens.success,
                        onClick = {
                            val next = !invoiceUnlocked
                            invoiceUnlocked = next
                            onUpdateBenefits(gstUnlocked, reconUnlocked, next, invUnlocked, cmaUnlocked, staffUnlocked, exportUnlocked, overrideUnlocked)
                        },
                        testTagId = "chip_permission_invoicing_${user.storeCode}",
                        modifier = Modifier.weight(1f)
                    )
                    InteractivePermissionChip(
                        label = "Inventory",
                        unlocked = invUnlocked,
                        icon = Icons.Default.Inventory2,
                        activeColor = tokens.accent,
                        onClick = {
                            val next = !invUnlocked
                            invUnlocked = next
                            onUpdateBenefits(gstUnlocked, reconUnlocked, invoiceUnlocked, next, cmaUnlocked, staffUnlocked, exportUnlocked, overrideUnlocked)
                        },
                        testTagId = "chip_permission_inventory_${user.storeCode}",
                        modifier = Modifier.weight(1f)
                    )
                }

                // Row 3: CMA Banking & Staff Operator PINs
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    InteractivePermissionChip(
                        label = "CMA Banking",
                        unlocked = cmaUnlocked,
                        icon = Icons.Default.TrendingUp,
                        activeColor = tokens.link,
                        onClick = {
                            val next = !cmaUnlocked
                            cmaUnlocked = next
                            onUpdateBenefits(gstUnlocked, reconUnlocked, invoiceUnlocked, invUnlocked, next, staffUnlocked, exportUnlocked, overrideUnlocked)
                        },
                        testTagId = "chip_permission_cma_${user.storeCode}",
                        modifier = Modifier.weight(1f)
                    )
                    InteractivePermissionChip(
                        label = "Staff PINs",
                        unlocked = staffUnlocked,
                        icon = Icons.Default.Badge,
                        activeColor = tokens.warning,
                        onClick = {
                            val next = !staffUnlocked
                            staffUnlocked = next
                            onUpdateBenefits(gstUnlocked, reconUnlocked, invoiceUnlocked, invUnlocked, cmaUnlocked, next, exportUnlocked, overrideUnlocked)
                        },
                        testTagId = "chip_permission_staff_${user.storeCode}",
                        modifier = Modifier.weight(1f)
                    )
                }

                // Row 4: Audit Export & Manager Override
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    InteractivePermissionChip(
                        label = "Audit Export",
                        unlocked = exportUnlocked,
                        icon = Icons.Default.FileDownload,
                        activeColor = tokens.success,
                        onClick = {
                            val next = !exportUnlocked
                            exportUnlocked = next
                            onUpdateBenefits(gstUnlocked, reconUnlocked, invoiceUnlocked, invUnlocked, cmaUnlocked, staffUnlocked, next, overrideUnlocked)
                        },
                        testTagId = "chip_permission_export_${user.storeCode}",
                        modifier = Modifier.weight(1f)
                    )
                    InteractivePermissionChip(
                        label = "Manager Override",
                        unlocked = overrideUnlocked,
                        icon = Icons.Default.Security,
                        activeColor = tokens.danger,
                        onClick = {
                            val next = !overrideUnlocked
                            overrideUnlocked = next
                            onUpdateBenefits(gstUnlocked, reconUnlocked, invoiceUnlocked, invUnlocked, cmaUnlocked, staffUnlocked, exportUnlocked, next)
                        },
                        testTagId = "chip_permission_override_${user.storeCode}",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Expandable Detailed Descriptions Accordion
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = { expandedBenefits = !expandedBenefits },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("toggle_benefits_expand_${user.storeCode}")
                ) {
                    Text(
                        text = if (expandedBenefits) "Detailed Descriptions ▲" else "Detailed Descriptions ▼",
                        fontSize = 11.sp,
                        color = tokens.info
                    )
                }
            }

            // Expanded Module Permissions Panel
            if (expandedBenefits) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = tokens.bg,
                    border = BorderStroke(0.5.dp, tokens.cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Granular Module Permission Sliders:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = tokens.text
                        )

                        ModulePermissionRow(
                            title = "GST Filing Assistant",
                            description = "Automated GSTR-1, 3B, Rule 88A set-off, JSON schemas",
                            unlocked = gstUnlocked,
                            onToggle = {
                                gstUnlocked = it
                                onUpdateBenefits(it, reconUnlocked, invoiceUnlocked, invUnlocked, cmaUnlocked, staffUnlocked, exportUnlocked, overrideUnlocked)
                            }
                        )

                        HorizontalDivider(color = tokens.cardBorder.copy(alpha = 0.5f), thickness = 0.5.dp)

                        ModulePermissionRow(
                            title = "Automated Bank Reconciliation",
                            description = "Bank feeds matching, discrepancy resolution & ledger sync",
                            unlocked = reconUnlocked,
                            onToggle = {
                                reconUnlocked = it
                                onUpdateBenefits(gstUnlocked, it, invoiceUnlocked, invUnlocked, cmaUnlocked, staffUnlocked, exportUnlocked, overrideUnlocked)
                            }
                        )

                        HorizontalDivider(color = tokens.cardBorder.copy(alpha = 0.5f), thickness = 0.5.dp)

                        ModulePermissionRow(
                            title = "GST Invoicing & E-Way Bills",
                            description = "B2B/B2C itemized invoices, credit/debit notes & quotes",
                            unlocked = invoiceUnlocked,
                            onToggle = {
                                invoiceUnlocked = it
                                onUpdateBenefits(gstUnlocked, reconUnlocked, it, invUnlocked, cmaUnlocked, staffUnlocked, exportUnlocked, overrideUnlocked)
                            }
                        )

                        HorizontalDivider(color = tokens.cardBorder.copy(alpha = 0.5f), thickness = 0.5.dp)

                        ModulePermissionRow(
                            title = "Stock & Inventory Management",
                            description = "Real-time stock ledger, batch alerts, reorder thresholds",
                            unlocked = invUnlocked,
                            onToggle = {
                                invUnlocked = it
                                onUpdateBenefits(gstUnlocked, reconUnlocked, invoiceUnlocked, it, cmaUnlocked, staffUnlocked, exportUnlocked, overrideUnlocked)
                            }
                        )

                        HorizontalDivider(color = tokens.cardBorder.copy(alpha = 0.5f), thickness = 0.5.dp)

                        ModulePermissionRow(
                            title = "CMA Banking & Credit Reports",
                            description = "Bank loan project reports, MPBF, DSCR, and ratio analyses",
                            unlocked = cmaUnlocked,
                            onToggle = {
                                cmaUnlocked = it
                                onUpdateBenefits(gstUnlocked, reconUnlocked, invoiceUnlocked, invUnlocked, it, staffUnlocked, exportUnlocked, overrideUnlocked)
                            }
                        )

                        HorizontalDivider(color = tokens.cardBorder.copy(alpha = 0.5f), thickness = 0.5.dp)

                        ModulePermissionRow(
                            title = "Staff Security & Access Allocation",
                            description = "Multi-device operator PINs and granular screen allocations",
                            unlocked = staffUnlocked,
                            onToggle = {
                                staffUnlocked = it
                                onUpdateBenefits(gstUnlocked, reconUnlocked, invoiceUnlocked, invUnlocked, cmaUnlocked, it, exportUnlocked, overrideUnlocked)
                            }
                        )

                        HorizontalDivider(color = tokens.cardBorder.copy(alpha = 0.5f), thickness = 0.5.dp)

                        ModulePermissionRow(
                            title = "Financial & Audit Data Export",
                            description = "Secure PDF, Excel, and JSON backups for external CAs and audits",
                            unlocked = exportUnlocked,
                            onToggle = {
                                exportUnlocked = it
                                onUpdateBenefits(gstUnlocked, reconUnlocked, invoiceUnlocked, invUnlocked, cmaUnlocked, staffUnlocked, it, overrideUnlocked)
                            }
                        )

                        HorizontalDivider(color = tokens.cardBorder.copy(alpha = 0.5f), thickness = 0.5.dp)

                        ModulePermissionRow(
                            title = "Manager Security Override",
                            description = "Authorizes transaction backdating, critical deletions, and master voids",
                            unlocked = overrideUnlocked,
                            onToggle = {
                                overrideUnlocked = it
                                onUpdateBenefits(gstUnlocked, reconUnlocked, invoiceUnlocked, invUnlocked, cmaUnlocked, staffUnlocked, exportUnlocked, it)
                            }
                        )
                    }
                }
            }

            // Tenant Actions: Login / Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onImpersonate,
                    border = BorderStroke(0.5.dp, tokens.info),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Default.Login, contentDescription = null, tint = tokens.info, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Sign In As Tenant", fontSize = 11.5.sp, color = tokens.info)
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = "Delete store",
                        tint = tokens.danger.copy(alpha = 0.8f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun InteractivePermissionChip(
    label: String,
    unlocked: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    activeColor: Color,
    onClick: () -> Unit,
    testTagId: String,
    modifier: Modifier = Modifier
) {
    val tokens = PabaruTheme.tokens
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (unlocked) activeColor.copy(alpha = 0.15f) else tokens.bg.copy(alpha = 0.7f),
        border = BorderStroke(
            width = if (unlocked) 1.dp else 0.7.dp,
            color = if (unlocked) activeColor.copy(alpha = 0.8f) else tokens.cardBorder
        ),
        modifier = modifier
            .testTag(testTagId)
            .heightIn(min = 48.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f, fill = false)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (unlocked) activeColor else tokens.textMuted.copy(alpha = 0.6f),
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = label,
                    fontSize = 10.5.sp,
                    fontWeight = if (unlocked) FontWeight.Bold else FontWeight.Normal,
                    color = if (unlocked) tokens.text else tokens.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (unlocked) {
                Surface(
                    shape = CircleShape,
                    color = activeColor,
                    modifier = Modifier.size(14.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Active",
                            tint = tokens.primaryText,
                            modifier = Modifier.size(10.dp)
                        )
                    }
                }
            } else {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Locked",
                    tint = tokens.textMuted.copy(alpha = 0.4f),
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

@Composable
private fun ModulePermissionRow(
    title: String,
    description: String,
    unlocked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val tokens = PabaruTheme.tokens
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(text = title, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = tokens.text)
            Text(text = description, fontSize = 10.5.sp, color = tokens.textMuted, lineHeight = 14.sp)
        }

        Switch(
            checked = unlocked,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = tokens.success,
                checkedTrackColor = tokens.success.copy(alpha = 0.3f),
                uncheckedThumbColor = tokens.textMuted,
                uncheckedTrackColor = tokens.bg
            )
        )
    }
}

@Composable
private fun AddNewStoreDialog(
    onDismiss: () -> Unit,
    onConfirm: (ManagedUserEntity) -> Unit
) {
    val tokens = PabaruTheme.tokens
    val randCode = remember { "TL-${(1000..9999).random()}" }
    var storeCode by remember { mutableStateOf(randCode) }
    var businessName by remember { mutableStateOf("") }
    var ownerName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }

    var gstUnlocked by remember { mutableStateOf(false) }
    var reconUnlocked by remember { mutableStateOf(false) }
    var invoiceUnlocked by remember { mutableStateOf(true) }
    var invUnlocked by remember { mutableStateOf(true) }
    var cmaUnlocked by remember { mutableStateOf(true) }
    var staffUnlocked by remember { mutableStateOf(true) }
    var exportUnlocked by remember { mutableStateOf(true) }
    var overrideUnlocked by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Provision New Business Store",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = tokens.text
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (errorMessage != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = tokens.danger.copy(alpha = 0.15f),
                        border = BorderStroke(0.5.dp, tokens.danger)
                    ) {
                        Text(
                            text = errorMessage ?: "",
                            fontSize = 12.sp,
                            color = tokens.danger,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }

                OutlinedTextField(
                    value = storeCode,
                    onValueChange = { storeCode = it.uppercase() },
                    label = { Text("Store Code *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = businessName,
                    onValueChange = { businessName = it },
                    label = { Text("Business / Store Name *") },
                    placeholder = { Text("e.g. Mahalakshmi Provision Stores") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = ownerName,
                    onValueChange = { ownerName = it },
                    label = { Text("Signatory / Owner Name *") },
                    placeholder = { Text("e.g. Ramesh Patel") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it.filter { c -> c.isDigit() }.take(10) },
                    label = { Text("Mobile Number (Server Auth) *") },
                    placeholder = { Text("e.g. 9820011223") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email Address") },
                    placeholder = { Text("e.g. ramesh@pabaru.in") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Initial Module Allocations:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = tokens.text,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("GST Filing Assistant (PRO)", fontSize = 12.sp, color = tokens.textMuted)
                    Switch(checked = gstUnlocked, onCheckedChange = { gstUnlocked = it })
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Bank Reconciliation (PRO)", fontSize = 12.sp, color = tokens.textMuted)
                    Switch(checked = reconUnlocked, onCheckedChange = { reconUnlocked = it })
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Financial Data Export", fontSize = 12.sp, color = tokens.textMuted)
                    Switch(checked = exportUnlocked, onCheckedChange = { exportUnlocked = it })
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Manager Security Override", fontSize = 12.sp, color = tokens.textMuted)
                    Switch(checked = overrideUnlocked, onCheckedChange = { overrideUnlocked = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleanCode = storeCode.trim().uppercase()
                    val cleanBusiness = businessName.trim()
                    val cleanOwner = ownerName.trim()
                    val cleanPhone = phone.filter { it.isDigit() }.takeLast(10)
                    val cleanEmail = email.trim()

                    if (cleanCode.isBlank() || cleanBusiness.isBlank() || cleanOwner.isBlank() || phone.isBlank()) {
                        errorMessage = "Please fill in all mandatory fields (*)."
                        return@Button
                    }
                    if (cleanPhone.length < 10) {
                        errorMessage = "Please enter a valid 10-digit mobile number."
                        return@Button
                    }
                    val entity = ManagedUserEntity(
                        storeCode = cleanCode,
                        businessName = cleanBusiness,
                        ownerName = cleanOwner,
                        phone = cleanPhone,
                        email = cleanEmail,
                        isActive = true,
                        isGstUnlocked = gstUnlocked,
                        isBankReconUnlocked = reconUnlocked,
                        isInvoiceUnlocked = invoiceUnlocked,
                        isInventoryUnlocked = invUnlocked,
                        isCmaUnlocked = cmaUnlocked,
                        isStaffUnlocked = staffUnlocked,
                        isExportUnlocked = exportUnlocked,
                        isManagerOverrideUnlocked = overrideUnlocked,
                        createdDate = FinancialEngine.getTodayDate(),
                        lastLoginTimestamp = System.currentTimeMillis()
                    )
                    onConfirm(entity)
                },
                modifier = Modifier.heightIn(min = 48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = tokens.success)
            ) {
                Text("Provision Store", color = tokens.primaryText, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Cancel", color = tokens.textMuted)
            }
        },
        containerColor = tokens.card
    )
}

@Composable
private fun BulkPermissionsDialog(
    selectedCount: Int,
    onDismiss: () -> Unit,
    onApply: (
        isGst: Boolean,
        isRecon: Boolean,
        isInvoice: Boolean,
        isInv: Boolean,
        isCma: Boolean,
        isStaff: Boolean,
        isExport: Boolean,
        isOverride: Boolean
    ) -> Unit
) {
    val tokens = PabaruTheme.tokens
    var gstUnlocked by remember { mutableStateOf(true) }
    var reconUnlocked by remember { mutableStateOf(true) }
    var invoiceUnlocked by remember { mutableStateOf(true) }
    var invUnlocked by remember { mutableStateOf(true) }
    var cmaUnlocked by remember { mutableStateOf(true) }
    var staffUnlocked by remember { mutableStateOf(true) }
    var exportUnlocked by remember { mutableStateOf(true) }
    var overrideUnlocked by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "Bulk Feature Permissions",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = tokens.text
                )
                Text(
                    text = "Applying to $selectedCount selected stores",
                    fontSize = 12.sp,
                    color = tokens.info
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Quick Presets
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Module Allocation Toggles:",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = tokens.textMuted
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(
                            onClick = {
                                gstUnlocked = true
                                reconUnlocked = true
                                invoiceUnlocked = true
                                invUnlocked = true
                                cmaUnlocked = true
                                staffUnlocked = true
                                exportUnlocked = true
                                overrideUnlocked = true
                            },
                            modifier = Modifier.heightIn(min = 48.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("Enable All", fontSize = 11.sp, color = tokens.info)
                        }
                        TextButton(
                            onClick = {
                                gstUnlocked = false
                                reconUnlocked = false
                                invoiceUnlocked = false
                                invUnlocked = false
                                cmaUnlocked = false
                                staffUnlocked = false
                                exportUnlocked = false
                                overrideUnlocked = false
                            },
                            modifier = Modifier.heightIn(min = 48.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("Disable All", fontSize = 11.sp, color = tokens.danger)
                        }
                    }
                }

                HorizontalDivider(color = tokens.cardBorder, thickness = 0.5.dp)

                BulkPermissionToggleRow("GST Filing Assistant (PRO)", gstUnlocked) { gstUnlocked = it }
                BulkPermissionToggleRow("Automated Bank Reconciliation", reconUnlocked) { reconUnlocked = it }
                BulkPermissionToggleRow("GST Invoicing & E-Way Bills", invoiceUnlocked) { invoiceUnlocked = it }
                BulkPermissionToggleRow("Inventory & Multi-Stock Control", invUnlocked) { invUnlocked = it }
                BulkPermissionToggleRow("CMA Projections & Banking", cmaUnlocked) { cmaUnlocked = it }
                BulkPermissionToggleRow("Staff PINs & Access Control", staffUnlocked) { staffUnlocked = it }
                BulkPermissionToggleRow("Financial Data Export", exportUnlocked) { exportUnlocked = it }
                BulkPermissionToggleRow("Manager Security Override", overrideUnlocked) { overrideUnlocked = it }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onApply(
                        gstUnlocked,
                        reconUnlocked,
                        invoiceUnlocked,
                        invUnlocked,
                        cmaUnlocked,
                        staffUnlocked,
                        exportUnlocked,
                        overrideUnlocked
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = tokens.warning),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("apply_bulk_permissions_button")
            ) {
                Text("Apply to $selectedCount Stores", color = tokens.primaryText, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Cancel", color = tokens.textMuted)
            }
        },
        containerColor = tokens.card
    )
}

@Composable
private fun BulkPermissionToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val tokens = PabaruTheme.tokens
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, fontSize = 12.sp, color = tokens.text)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = tokens.success,
                checkedTrackColor = tokens.success.copy(alpha = 0.3f),
                uncheckedThumbColor = tokens.textMuted,
                uncheckedTrackColor = tokens.bg
            )
        )
    }
}
