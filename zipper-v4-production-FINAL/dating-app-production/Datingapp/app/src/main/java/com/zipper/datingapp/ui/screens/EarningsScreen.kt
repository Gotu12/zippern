package com.zipper.datingapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import com.zipper.datingapp.ui.DatingUiState
import com.zipper.datingapp.ui.theme.AppResponsiveContentMaxWidth
import com.zipper.datingapp.ui.theme.LocalWindowWidthClass
import com.zipper.datingapp.ui.theme.defaultHorizontalContentPadding
import java.util.*

@Composable
fun EarningsScreen(uiState: DatingUiState) {
    val widthClass = LocalWindowWidthClass.current
    val hPad = widthClass.defaultHorizontalContentPadding()
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = AppResponsiveContentMaxWidth)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(hPad)
        ) {
        Text(
            text = "My Earnings",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "Total Balance", style = MaterialTheme.typography.labelLarge)
                Text(
                    text = "₹${String.format(Locale.getDefault(), "%.2f", uiState.totalEarnings)}",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { /* Handle withdrawal */ },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.AccountBalanceWallet, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Withdraw to Bank")
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = "Breakdown",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            EarningStatItem(
                icon = Icons.Default.LiveTv,
                label = "Live",
                amount = "₹${String.format(Locale.getDefault(), "%.2f", uiState.totalEarnings * 0.4)}",
                color = Color(0xFF4ADE80)
            )
            EarningStatItem(
                icon = Icons.Default.Call,
                label = "Calls",
                amount = "₹${String.format(Locale.getDefault(), "%.2f", uiState.totalEarnings * 0.3)}",
                color = Color(0xFF4FC3F7)
            )
            EarningStatItem(
                icon = Icons.Default.CardGiftcard,
                label = "Gifts",
                amount = "₹${String.format(Locale.getDefault(), "%.2f", uiState.totalEarnings * 0.3)}",
                color = Color(0xFFFBBF24)
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = "Platform Policy",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "You keep 70% of all coins earned. Platform fee is 30%.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        }
    }
}

@Composable
fun EarningStatItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    amount: String,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = CircleShape,
            color = color.copy(alpha = 0.2f),
            modifier = Modifier.size(56.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = color)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = label, style = MaterialTheme.typography.labelMedium)
        Text(text = amount, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
    }
}
