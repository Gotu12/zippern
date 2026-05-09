package com.zipper.datingapp.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.zipper.datingapp.data.CoinPackage
import com.zipper.datingapp.data.ResellAgent
import com.zipper.datingapp.data.coinPackages
import com.zipper.datingapp.ui.DatingUiState
import com.zipper.datingapp.ui.paddingWithNavigationBars
import com.zipper.datingapp.ui.theme.AppResponsiveContentMaxWidth
import com.zipper.datingapp.ui.theme.LocalWindowWidthClass
import com.zipper.datingapp.ui.theme.defaultHorizontalContentPadding

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiamondShopScreen(uiState: DatingUiState, onBack: () -> Unit, @Suppress("UNUSED_PARAMETER") onBuyDiamonds: (CoinPackage) -> Unit) {
    var showResellDialog by remember { mutableStateOf(false) }
    BackHandler {
        if (showResellDialog) showResellDialog = false else onBack()
    }
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val widthClass = LocalWindowWidthClass.current
    val hPad = widthClass.defaultHorizontalContentPadding()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background)
    ) {
        TopAppBar(
            title = { Text("Diamond Shop", fontWeight = FontWeight.Bold) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                TextButton(
                    onClick = { showResellDialog = true },
                    modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                ) {
                    Icon(Icons.Default.SupportAgent, contentDescription = null, tint = scheme.primary)
                    Spacer(Modifier.width(4.dp))
                    Text("Reselling", color = scheme.primary, fontWeight = FontWeight.Bold)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = scheme.background,
                titleContentColor = scheme.onBackground,
                navigationIconContentColor = scheme.onBackground
            )
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
                    .padding(start = hPad, top = 16.dp, end = hPad)
            ) {
            // Balance Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = scheme.surfaceContainer,
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    modifier = Modifier.padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Current Balance", color = scheme.onSurface.copy(alpha = 0.6f), fontSize = 14.sp)
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${uiState.coinBalance}", color = scheme.onSurface, fontSize = 28.sp, fontWeight = FontWeight.Black)
                            Spacer(Modifier.width(8.dp))
                            Text("💎", fontSize = 24.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
            Text("Select Package", color = scheme.onSurface, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Contact resellers for special offers", color = scheme.onSurface.copy(alpha = 0.5f), fontSize = 12.sp)
            Spacer(Modifier.height(16.dp))

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 120.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = paddingWithNavigationBars(bottomExtra = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(coinPackages) { pkg ->
                    DiamondPackageCard(pkg = pkg, onClick = {
                        android.widget.Toast.makeText(context, "Please contact Reseller to buy diamonds", android.widget.Toast.LENGTH_LONG).show()
                    })
                }
            }
            }
        }
    }

    if (showResellDialog) {
        AlertDialog(
            onDismissRequest = { showResellDialog = false },
            title = { Text("Diamond Resellers", color = scheme.onSurface) },
            containerColor = scheme.surfaceContainer,
            text = {
                Column {
                    Text("Contact official agents to buy/resell diamonds.", color = scheme.onSurface.copy(alpha = 0.7f), fontSize = 14.sp)
                    Spacer(Modifier.height(16.dp))
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 400.dp),
                        contentPadding = paddingWithNavigationBars()
                    ) {
                        items(uiState.resellAgents) { agent ->
                            ResellerItem(agent = agent, onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(agent.whatsappLink))
                                context.startActivity(intent)
                            })
                            HorizontalDivider(color = scheme.outline.copy(alpha = 0.2f))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showResellDialog = false },
                    modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                ) {
                    Text("Close", color = scheme.primary)
                }
            }
        )
    }
}

@Composable
fun ResellerItem(agent: ResellAgent, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(agent.flag, fontSize = 24.sp, modifier = Modifier.padding(end = 16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                agent.country,
                color = scheme.onSurface,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                agent.resellerName,
                color = scheme.onSurface.copy(alpha = 0.5f),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = "WhatsApp",
            color = scheme.primary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

@Composable
fun DiamondPackageCard(pkg: CoinPackage, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val total = pkg.totalDiamonds
    val bonusGold = Color(0xFFFFC107)
    val bonusGreen = Color(0xFF69F0AE)
    Surface(
        onClick = onClick,
        color = scheme.surfaceContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .aspectRatio(0.88f)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (pkg.isPopular) {
                Surface(
                    color = scheme.primary,
                    shape = RoundedCornerShape(bottomStart = 8.dp),
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Text(
                        "HOT",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        color = scheme.onPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Column(
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 14.dp)
                    .fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("💎", fontSize = 26.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = total.toString(),
                    color = scheme.onSurface,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
                Text(
                    text = "Diamonds",
                    color = scheme.onSurface.copy(alpha = 0.65f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
                if (pkg.bonus > 0) {
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        color = bonusGold.copy(alpha = 0.22f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, bonusGreen.copy(alpha = 0.85f))
                    ) {
                        Text(
                            text = "Bonus +${pkg.bonus}",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            color = bonusGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                Surface(
                    color = scheme.primaryContainer.copy(alpha = 0.95f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "₹${pkg.priceInr}",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        color = scheme.onPrimaryContainer,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
    }
}

/** In-call overlay: full diamond shop UI without leaving WebRTC (dialog over the call). */
@Composable
fun DiamondShopPopup(
    uiState: DatingUiState,
    onDismiss: () -> Unit,
    onBuyDiamonds: (CoinPackage) -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.92f),
            shape = RoundedCornerShape(20.dp),
            shadowElevation = 8.dp
        ) {
            DiamondShopScreen(
                uiState = uiState,
                onBack = onDismiss,
                onBuyDiamonds = onBuyDiamonds
            )
        }
    }
}
