package com.zipper.datingapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zipper.datingapp.ui.DatingUiState
import com.zipper.datingapp.ui.theme.AppResponsiveContentMaxWidth
import com.zipper.datingapp.ui.theme.LocalWindowWidthClass
import com.zipper.datingapp.ui.theme.defaultHorizontalContentPadding

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BeansCenterScreen(uiState: DatingUiState, onBack: () -> Unit) {
    val widthClass = LocalWindowWidthClass.current
    val hPad = widthClass.defaultHorizontalContentPadding()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8F8FA))
    ) {
        TopAppBar(
            title = { Text("Beans Center", fontWeight = FontWeight.Bold) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFFF8F8FA))
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = AppResponsiveContentMaxWidth)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(hPad)
            ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFF7043))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "Total Beans Balance", style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.8f))
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${uiState.beans}",
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("🟡", fontSize = 32.sp)
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { /* Handle withdrawal */ },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFFFF7043)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.AccountBalanceWallet, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Withdraw to Bank", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Text(
                text = "Earnings Breakdown",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.DarkGray
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                BeansEarningStatItem(
                    icon = Icons.Default.LiveTv,
                    label = "Live Streaming",
                    amount = "${(uiState.beans * 0.4).toInt()} 🟡",
                    color = Color(0xFF4ADE80)
                )
                BeansEarningStatItem(
                    icon = Icons.Default.Call,
                    label = "Voice/Video Calls",
                    amount = "${(uiState.beans * 0.3).toInt()} 🟡",
                    color = Color(0xFF4FC3F7)
                )
                BeansEarningStatItem(
                    icon = Icons.Default.CardGiftcard,
                    label = "Received Gifts",
                    amount = "${(uiState.beans * 0.3).toInt()} 🟡",
                    color = Color(0xFFFFC107)
                )
            }
            
            Spacer(modifier = Modifier.height(48.dp))
            
            Surface(
                color = Color(0xFFE3F2FD),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Platform Policy",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1565C0)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "100 Beans = 1 USD. Minimum withdrawal is 5000 Beans. You keep 70% of all Diamonds received as gifts, which are converted to Beans automatically.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF1976D2).copy(alpha = 0.8f),
                        lineHeight = 20.sp
                    )
                }
            }
            }
        }
    }
}

@Composable
fun BeansEarningStatItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    amount: String,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = CircleShape,
            color = color.copy(alpha = 0.2f),
            modifier = Modifier.size(64.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(32.dp))
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = Color.Gray)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = amount, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.DarkGray)
    }
}
