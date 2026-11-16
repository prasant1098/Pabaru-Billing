package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.ExportEngine
import com.example.engine.FinancialEngine
import com.example.model.UpiQrCode
import com.example.ui.theme.PabaruTheme
import com.example.ui.theme.success
import java.util.Locale

fun copyToClipboard(context: Context, text: String, label: String = "Text") {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, "$label copied", Toast.LENGTH_SHORT).show()
}

/**
 * Show large scannable UPI QR Bottom Sheet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShowQrBottomSheet(
    qr: UpiQrCode,
    bankName: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val tokens = PabaruTheme.tokens
    var optionalAmount by remember { mutableStateOf("") }
    val amountPaise = FinancialEngine.parsePaiseFromRupees(optionalAmount)

    val upiUri = remember(qr.upiId, qr.label, amountPaise) {
        val base = "upi://pay?pa=${Uri.encode(qr.upiId)}&pn=${Uri.encode(qr.label)}&cu=INR"
        if (amountPaise > 0L) {
            val formattedAmt = String.format(Locale.ENGLISH, "%.2f", amountPaise / 100.0)
            "$base&am=$formattedAmt"
        } else {
            base
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = tokens.card,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(tokens.cardBorder)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = qr.label,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = tokens.text
            )
            Text(
                text = "Linked to $bankName",
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                color = tokens.textMuted
            )

            // High-contrast QR Preview Card (black on white for scannability)
            Surface(
                modifier = Modifier
                    .size(200.dp)
                    .padding(8.dp),
                shape = RoundedCornerShape(12.dp),
                color = Color.White,
                border = BorderStroke(1.dp, tokens.cardBorder)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.QrCode2,
                            contentDescription = "UPI QR Code",
                            tint = Color.Black,
                            modifier = Modifier.size(140.dp)
                        )
                        Text(
                            text = if (amountPaise > 0L) FinancialEngine.formatPaise(amountPaise) else "SCAN AND PAY",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.Black
                        )
                    }
                }
            }

            // UPI ID with Copy
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clickable { copyToClipboard(context, qr.upiId, "UPI ID") }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text(
                    text = qr.upiId,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = tokens.success
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = "Copy UPI ID",
                    tint = tokens.textMuted,
                    modifier = Modifier.size(16.dp)
                )
            }

            // Optional Amount Field
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                color = tokens.secondaryBg,
                border = BorderStroke(0.5.dp, tokens.cardBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("₹", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = tokens.text)
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (optionalAmount.isEmpty()) {
                            Text("Optional amount", fontSize = 13.sp, color = tokens.textMuted)
                        }
                        BasicTextField(
                            value = optionalAmount,
                            onValueChange = { input -> optionalAmount = FinancialEngine.sanitizeDecimalInput(input) },
                            singleLine = true,
                            cursorBrush = SolidColor(tokens.primary),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            textStyle = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = tokens.text),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Copy UPI Link & Share Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clickable { copyToClipboard(context, upiUri, "UPI payment link") },
                    shape = RoundedCornerShape(12.dp),
                    color = tokens.secondaryBg,
                    border = BorderStroke(0.5.dp, tokens.cardBorder)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy UPI Link", tint = tokens.text, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copy Link", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = tokens.text)
                    }
                }

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clickable {
                            val shareText = buildString {
                                append("Pay ${qr.label}")
                                if (amountPaise > 0L) append(" (${FinancialEngine.formatPaise(amountPaise)})")
                                append("\nUPI ID: ${qr.upiId}")
                                append("\nLink: $upiUri")
                            }
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "UPI Payment Request - ${qr.label}")
                                putExtra(Intent.EXTRA_TEXT, shareText)
                            }
                            ExportEngine.launchChooserSafely(context, intent, "Share UPI Payment Details")
                        },
                    shape = RoundedCornerShape(12.dp),
                    color = tokens.primary
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.Share, contentDescription = "Share", tint = tokens.primaryText, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = tokens.primaryText)
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}
