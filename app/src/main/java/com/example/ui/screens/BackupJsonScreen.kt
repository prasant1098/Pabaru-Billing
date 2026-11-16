package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.example.engine.ExportEngine
import com.example.ui.theme.PabaruTheme
import com.example.viewmodel.PabaruUiState
import com.example.viewmodel.PabaruViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupJsonScreen(
    uiState: PabaruUiState,
    viewModel: PabaruViewModel,
    onBack: () -> Unit
) {
    val tokens = PabaruTheme.tokens
    val context = LocalContext.current
    var backupPassphrase by remember { mutableStateOf("") }
    var generatedEnvelope by remember { mutableStateOf<String?>(null) }
    var restoreInputText by remember { mutableStateOf("") }
    var restorePassphrase by remember { mutableStateOf("") }

    BackHandler { onBack() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = tokens.text)
            }
            Spacer(modifier = Modifier.width(4.dp))
            Column {
                Text(
                    "JSON Backup & Offline Portability",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = tokens.text
                )
                Text(
                    "Encrypted export envelope, cross-device restore & recovery phrase",
                    fontSize = 11.sp,
                    color = tokens.textMuted
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Backup Card
        Card(
            colors = CardDefaults.cardColors(containerColor = tokens.card),
            border = BorderStroke(1.dp, tokens.cardBorder),
            shape = RoundedCornerShape(14.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "One-Click Encrypted JSON Envelope",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = tokens.text
                    )
                    Surface(
                        color = tokens.accentTint,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            "AES-256 GCM",
                            color = tokens.accent,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    "Exports ${uiState.parties.size} parties, ${uiState.products.size} items, ${uiState.transactions.size} transactions & ${uiState.auditLogs.size} SHA-256 audit blocks using PBKDF2-derived AES-256-GCM.",
                    fontSize = 11.sp,
                    color = tokens.textMuted
                )
                HorizontalDivider(color = tokens.cardBorder)

                OutlinedTextField(
                    value = backupPassphrase,
                    onValueChange = { backupPassphrase = it },
                    label = { Text("Encryption Passphrase (min 6 chars) *") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = {
                        if (backupPassphrase.trim().length < 6) {
                            Toast.makeText(context, "Enter a passphrase of at least 6 characters", Toast.LENGTH_SHORT).show()
                        } else {
                            generatedEnvelope = viewModel.exportBackupJson(backupPassphrase.trim())
                            Toast.makeText(context, "Encrypted AES-256-GCM backup generated!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = tokens.primary),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = tokens.primaryText, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Generate Encrypted Backup", color = tokens.primaryText, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                val envelope = generatedEnvelope
                if (envelope != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = tokens.secondaryBg),
                        border = BorderStroke(1.dp, tokens.cardBorder),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = envelope.take(600) + if (envelope.length > 600) "\n... (${envelope.length} bytes encrypted)" else "",
                            color = tokens.accent,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(12.dp)
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Pabaru Encrypted Backup", envelope))
                                Toast.makeText(context, "Encrypted backup JSON copied!", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = tokens.secondaryBg),
                            border = BorderStroke(1.dp, tokens.cardBorder),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, tint = tokens.text, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Copy JSON", fontSize = 11.sp, color = tokens.text)
                        }

                        Button(
                            onClick = {
                                ExportEngine.shareTextContentAsFile(
                                    context = context,
                                    fileName = "Pabaru_Backup_${uiState.profile.storeCode.ifBlank { "store" }}.json",
                                    mimeType = "application/json",
                                    content = envelope,
                                    subject = "Pabaru Encrypted Ledger Backup (${uiState.profile.storeCode})",
                                    summaryText = "AES-256-GCM encrypted backup for ${uiState.profile.businessName}",
                                    chooserTitle = "Share Encrypted Backup JSON"
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = tokens.primary),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = tokens.primaryText, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Share Backup", fontSize = 11.sp, color = tokens.primaryText, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Restore Card
        Card(
            colors = CardDefaults.cardColors(containerColor = tokens.card),
            border = BorderStroke(1.dp, tokens.cardBorder),
            shape = RoundedCornerShape(14.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Restore Ledger from Encrypted JSON",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = tokens.text
                )
                Text(
                    "Paste an encrypted Pabaru AES-256-GCM JSON backup envelope and enter its passphrase to restore all records.",
                    fontSize = 11.sp,
                    color = tokens.textMuted
                )
                OutlinedTextField(
                    value = restoreInputText,
                    onValueChange = { restoreInputText = it },
                    placeholder = { Text("Paste encrypted JSON backup contents here...") },
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = restorePassphrase,
                    onValueChange = { restorePassphrase = it },
                    label = { Text("Decryption Passphrase *") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        val text = restoreInputText.trim()
                        val pass = restorePassphrase.trim()
                        if (text.isBlank() || pass.isBlank()) {
                            Toast.makeText(context, "Paste backup JSON and enter its passphrase", Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.restoreEncryptedBackup(text, pass)
                                .onSuccess { msg ->
                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                    restoreInputText = ""
                                    restorePassphrase = ""
                                }
                                .onFailure { err ->
                                    Toast.makeText(context, "Restore failed: ${err.message}", Toast.LENGTH_LONG).show()
                                }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = tokens.primary),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) {
                    Text("Decrypt, Verify & Restore Ledger", color = tokens.primaryText, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 12-Word Recovery Phrase
        Card(
            colors = CardDefaults.cardColors(containerColor = tokens.card),
            border = BorderStroke(1.dp, tokens.cardBorder),
            shape = RoundedCornerShape(14.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Key, contentDescription = null, tint = tokens.accent)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("12-Word Master Recovery Phrase", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = tokens.text)
                }
                Text(
                    "Displayed during business registration or PIN setup. Store offline in a safe place.",
                    fontSize = 11.sp,
                    color = tokens.textMuted
                )

                val phrase = uiState.recoveryPhrase
                Card(
                    colors = CardDefaults.cardColors(containerColor = tokens.secondaryBg),
                    border = BorderStroke(1.dp, tokens.cardBorder),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        phrase ?: "Set or reset your Counter Security PIN to generate a fresh 12-word recovery phrase.",
                        fontWeight = if (phrase != null) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 12.sp,
                        fontFamily = if (phrase != null) FontFamily.Monospace else FontFamily.Default,
                        color = if (phrase != null) tokens.accent else tokens.textMuted,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        }
    }
}
