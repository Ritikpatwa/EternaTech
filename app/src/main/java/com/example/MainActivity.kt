package com.example

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.DrawableRes
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.api.GeminiClient
import com.example.data.CoinTransaction
import com.example.data.SpendingRecord
import com.example.data.TrackedOrder
import com.example.ui.AppViewModel
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Enable edge to edge for proper bleeds matching modern Material 3 standard
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                EternaSuperApp()
            }
        }
    }
}

// Custom Premium Theme Colors
object EternaColors {
    val BackgroundDark = Color(0xFF0E1116)     // Sophisticated Deep Dark
    val CardBackground = Color(0xFF1A1D24)     // Sophisticated Slate Card
    val BorderHex = Color(0xFF2E333D)          // White/10 border representation
    val AccentPrimary = Color(0xFF6366F1)      // Indigo-500/600 Accent
    val AccentBlue = Color(0xFF3B82F6)         // Blue 500
    val GoldCoin = Color(0xFFFBBF24)           // Gold 400
    val AccentPurple = Color(0xFF8B5CF6)       // Purple 500
    val AccentPink = Color(0xFFEC4899)         // Pink 500
    val LightText = Color(0xFFF1F5F9)          // Slate-100 Text
    val MutedText = Color(0xFF94A3B8)          // Slate-400 Text
}

@Composable
fun EternaSuperApp(viewModel: AppViewModel = viewModel()) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    // UI reactive states from Room database model
    val previousComparisons by viewModel.previousComparisons.collectAsState()
    val trackedOrders by viewModel.trackedOrders.collectAsState()
    val spendingRecords by viewModel.spendingRecords.collectAsState()
    val coinsBalance by viewModel.coinsBalance.collectAsState()
    val coinTransactions by viewModel.coinTransactions.collectAsState()

    // View tracking
    var selectedTab by remember { mutableStateOf(0) } // 0=Engine, 1=AI Chat, 2=Link Analyser, 3=Finance, 4=Track Orders, 5=Coins Wallet
    
    // Engine State
    var selectedType by remember { mutableStateOf("SHOPPING") } // SHOPPING, CAB, FOOD, TRAVEL
    var searchQuery by remember { mutableStateOf("") }
    
    // AI Link Analyzer pasted search state
    var pastedLink by remember { mutableStateOf("") }
    
    // AI Spending Logs input state
    var spendAmount by remember { mutableStateOf("") }
    var spendDesc by remember { mutableStateOf("") }
    var spendCategory by remember { mutableStateOf("Shopping") }
    
    // Order tracking addition state
    var showAddOrderDialog by remember { mutableStateOf(false) }
    var orderProduct by remember { mutableStateOf("") }
    var orderPrice by remember { mutableStateOf("") }
    var orderPlatform by remember { mutableStateOf("Amazon") }
    
    // Spin state
    var isSpinning by remember { mutableStateOf(false) }
    var spinAngle by remember { mutableStateOf(0f) }

    // Loading & API flow helpers
    val compareLoading by viewModel.compareLoading.collectAsState()
    val activeComparison by viewModel.activeComparison.collectAsState()
    val compareError by viewModel.compareError.collectAsState()
    val linkLoading by viewModel.linkLoading.collectAsState()
    val activeLinkAnalysis by viewModel.activeLinkAnalysis.collectAsState()
    val insightsLoading by viewModel.insightsLoading.collectAsState()
    val spendingInsightsText by viewModel.spendingInsightsText.collectAsState()

    // Status warning message requirement
    var showWarningBanner by remember { mutableStateOf(!GeminiClient.isApiKeyConfigured()) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = EternaColors.BackgroundDark,
        topBar = {
            Column {
                // Top Header block with custom background matching user attachments branding
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color(0xFF030712), EternaColors.BackgroundDark)
                            )
                        )
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(EternaColors.AccentPrimary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "E",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "ETERNA TECH",
                                    color = Color.White,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp
                                )
                            }
                            Text(
                                text = "Founder: Ritik  |  AI Commerce System",
                                color = EternaColors.MutedText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        // Wallet Coins shortcut with click navigation
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(EternaColors.CardBackground)
                                .clickable { selectedTab = 5 }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .animateContentSize()
                        ) {
                            Text(
                                text = "🪙",
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "$coinsBalance Coins",
                                color = EternaColors.GoldCoin,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                // AI Key Check Warning label if missing as mandated
                if (showWarningBanner) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF7F1D1D)),
                        shape = RoundedCornerShape(0.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "⚠️ Pro Tip: Enter your GEMINI_API_KEY in the AI Studio Secrets panel for AI capabilities. Run with smart fallback simulations.",
                                color = Color.White,
                                fontSize = 11.sp,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { showWarningBanner = false }) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            // Navigation with adaptive tabs (works beautifully on mobile & wide web frames)
            NavigationBar(
                containerColor = Color(0xFF0F172A),
                tonalElevation = 8.dp,
                windowInsets = WindowInsets.navigationBars
            ) {
                val tabs = listOf(
                    TabData("Compare", Icons.Default.Compare, 0),
                    TabData("AI Chat", Icons.Default.Chat, 1),
                    TabData("AI Link", Icons.Default.Link, 2),
                    TabData("Analyze Spend", Icons.Default.QueryStats, 3),
                    TabData("Track Items", Icons.Default.LocalShipping, 4),
                    TabData("Cashback", Icons.Default.Wallet, 5)
                )

                tabs.forEach { item ->
                    NavigationBarItem(
                        selected = selectedTab == item.index,
                        onClick = { selectedTab = item.index },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = EternaColors.AccentPrimary,
                            selectedTextColor = EternaColors.AccentPrimary,
                            unselectedTextColor = EternaColors.MutedText,
                            unselectedIconColor = EternaColors.MutedText,
                            indicatorColor = EternaColors.CardBackground
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(EternaColors.BackgroundDark)
                .verticalScroll(rememberScrollState())
        ) {
            // Direct download option at the startup home UI header
            DirectDownloadOptionCard(viewModel)

            // Dynamic API Connectivity Diagnostics Card
            ApiConnectionStatusCard(viewModel)

            Spacer(modifier = Modifier.height(12.dp))

            // Main Adaptive Container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(220))
                    },
                    label = "TabTransition"
                ) { target ->
                    when (target) {
                        0 -> CompareTab(
                            selectedType = selectedType,
                            onTypeChange = { selectedType = it },
                            searchQuery = searchQuery,
                            onQueryChange = { searchQuery = it },
                            compareLoading = compareLoading,
                            activeComparison = activeComparison,
                            compareError = compareError,
                            previousComparisons = previousComparisons,
                            viewModel = viewModel
                        )
                        1 -> AiChatTab(
                            viewModel = viewModel,
                            compareLoading = compareLoading,
                            activeComparison = activeComparison
                        )
                        2 -> LinkAnalyzerTab(
                            pastedLink = pastedLink,
                            onLinkChange = { pastedLink = it },
                            linkLoading = linkLoading,
                            activeLinkAnalysis = activeLinkAnalysis,
                            viewModel = viewModel
                        )
                        3 -> SpendingTab(
                            spendingRecords = spendingRecords,
                            spendAmount = spendAmount,
                            onAmountChange = { spendAmount = it },
                            spendDesc = spendDesc,
                            onDescChange = { spendDesc = it },
                            spendCategory = spendCategory,
                            onCategoryChange = { spendCategory = it },
                            insightsLoading = insightsLoading,
                            insightsText = spendingInsightsText,
                            viewModel = viewModel
                        )
                        4 -> OrderTrackerTab(
                            trackedOrders = trackedOrders,
                            showAddOrderDialog = showAddOrderDialog,
                            onShowDialogChange = { showAddOrderDialog = it },
                            orderProduct = orderProduct,
                            onProductChange = { orderProduct = it },
                            orderPrice = orderPrice,
                            onPriceChange = { orderPrice = it },
                            orderPlatform = orderPlatform,
                            onPlatformChange = { orderPlatform = it },
                            viewModel = viewModel
                        )
                        5 -> RewardsTab(
                            coinsBalance = coinsBalance,
                            coinTransactions = coinTransactions,
                            isSpinning = isSpinning,
                            onSpinChange = { isSpinning = it },
                            spinAngle = spinAngle,
                            onAngleChange = { spinAngle = it },
                            viewModel = viewModel
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

data class TabData(val label: String, val icon: ImageVector, val index: Int)

// --- DIRECT DOWNLOAD AND WEBAPP BANNER ---
@Composable
fun DirectDownloadOptionCard(viewModel: AppViewModel) {
    val context = LocalContext.current
    var downloadInProgress by remember { mutableStateOf(false) }
    var downloadLabel by remember { mutableStateOf("Download / Export Current Savings Report") }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = EternaColors.CardBackground),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, EternaColors.BorderHex)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(EternaColors.AccentPrimary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.FileDownload,
                        contentDescription = "Download Logo",
                        tint = EternaColors.AccentPrimary
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Eterna Price Calculator WebApp & APK Console",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Cross-platform support: runs seamlessly on Web portals & Android.",
                        color = EternaColors.MutedText,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = EternaColors.BorderHex.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // EXPORT REPORT DOWNLOAD FLOW
                Button(
                    onClick = {
                        downloadInProgress = true
                        downloadLabel = "Generating Analysis..."
                        Handler(Looper.getMainLooper()).postDelayed({
                            downloadInProgress = false
                            downloadLabel = "Report Downloaded!"
                            
                            // Trigger report data generation
                            val report = viewModel.generateReportData()
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Eterna Price Report", report)
                            clipboard.setPrimaryClip(clip)

                            // Trigger general share intent
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "Eterna Compare AI Insights")
                                putExtra(Intent.EXTRA_TEXT, report)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Save Pricing Report"))

                            Toast.makeText(context, "Report saved! Details also copied to clipboard.", Toast.LENGTH_LONG).show()
                            
                            // Reset state
                            Handler(Looper.getMainLooper()).postDelayed({
                                downloadLabel = "Download / Export Current Savings Report"
                            }, 3000)
                        }, 1200)
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = EternaColors.AccentPrimary),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(vertical = 12.dp),
                    enabled = !downloadInProgress
                ) {
                    Icon(Icons.Default.SaveAlt, contentDescription = "Download Save", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = downloadLabel, 
                        fontSize = 11.sp, 
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // SHARE WEBAPP URL FLOW
                OutlinedButton(
                    onClick = {
                        val webappUrl = "https://ai.studio/build"
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Eterna WebApp Link", webappUrl)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Eterna WebApp Link Copied - Open in Chrome / Safari on any device!", Toast.LENGTH_LONG).show()
                    },
                    modifier = Modifier.sizeIn(minHeight = 44.dp),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, EternaColors.BorderHex)
                ) {
                    Icon(
                        Icons.Default.Language, 
                        contentDescription = "Web Link", 
                        tint = EternaColors.AccentBlue,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Copy WebApp URL", color = Color.White, fontSize = 11.sp)
                }
            }
        }
    }
}

// --- DYNAMIC AI API CONNECTION DIAGNOSTICS CARD ---
@Composable
fun ApiConnectionStatusCard(viewModel: AppViewModel) {
    val apiConnectionActive by viewModel.apiConnectionActive.collectAsState()
    val testingConnection by viewModel.testingConnection.collectAsState()

    // Automatically trigger checking once on startup if not checked
    LaunchedEffect(Unit) {
        if (apiConnectionActive == null) {
            viewModel.checkApiConnection()
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = EternaColors.CardBackground),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, EternaColors.BorderHex)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                when (apiConnectionActive) {
                                    true -> Color(0xFF10B981).copy(alpha = 0.15f)
                                    false -> Color(0xFFF59E0B).copy(alpha = 0.15f)
                                    else -> EternaColors.MutedText.copy(alpha = 0.15f)
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = when (apiConnectionActive) {
                                true -> "⚡"
                                false -> "🔌"
                                else -> "🔍"
                            },
                            fontSize = 16.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Gemini Core AI Gateway",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when (apiConnectionActive) {
                                            true -> Color(0xFF10B981)
                                            false -> Color(0xFFF59E0B)
                                            else -> EternaColors.MutedText
                                        }
                                    )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = when (apiConnectionActive) {
                                    true -> "Live Connection Active (Success)"
                                    false -> "Simulated Fallback Mode Enabled"
                                    else -> "Scanning API Gateway..."
                                },
                                color = when (apiConnectionActive) {
                                    true -> Color(0xFF10B981)
                                    false -> Color(0xFFF59E0B)
                                    else -> EternaColors.MutedText
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = { viewModel.checkApiConnection() },
                    modifier = Modifier.height(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (testingConnection) EternaColors.BorderHex else EternaColors.AccentPrimary
                    ),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    enabled = !testingConnection
                ) {
                    if (testingConnection) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 1.5.dp
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    } else {
                        Text("Verify API", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = EternaColors.BorderHex.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = when (apiConnectionActive) {
                    true -> "Your Google AI Studio environment is fully synced! Eterna is executing premium real-time market scrapers and comparison analysis via Gemini Pro Models."
                    false -> "No API Key detected, or connection timed out. Eterna is currently running in local offline simulated fallback mode (all search features remain fully functional with advanced local mocks). To enable live AI intelligence, configure your \"GEMINI_API_KEY\" in AI Studio's Secrets panel."
                    else -> "Eterna is testing your endpoint with a handshake request..."
                },
                color = EternaColors.MutedText,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
        }
    }
}

// ============================================
// TAB 0: COMPARISON ENGINE SCREEN
// ============================================
@Composable
fun CompareTab(
    selectedType: String,
    onTypeChange: (String) -> Unit,
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    compareLoading: Boolean,
    activeComparison: JSONObject?,
    compareError: String?,
    previousComparisons: List<com.example.data.SearchComparison>,
    viewModel: AppViewModel
) {
    val context = LocalContext.current
    val keyboardController = LocalContext.current

    Column(modifier = Modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = EternaColors.CardBackground),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, EternaColors.BorderHex)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Smart Universal Price Comparison",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    text = "Get real-time lowest price suggestions instantly.",
                    color = EternaColors.MutedText,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Selectors Grid between Categories
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        "SHOPPING" to "🛒 Shopping",
                        "CAB" to "🚖 Cab",
                        "FOOD" to "🍔 Food",
                        "TRAVEL" to "✈️ Travel"
                    ).forEach { (code, name) ->
                        val isSelected = selectedType == code
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) EternaColors.AccentPrimary else EternaColors.BackgroundDark)
                                .clickable {
                                    onTypeChange(code)
                                    // Update placeholder suggestions
                                    val suggested = when(code) {
                                        "SHOPPING" -> "iPhone 15 or Running Shoes"
                                        "CAB" -> "Delhi Airport to Connaught Place"
                                        "FOOD" -> "Pepperoni Pizza with Garlic Bread"
                                        "TRAVEL" -> "Mumbai to Goa Flight"
                                        else -> ""
                                    }
                                    onQueryChange(suggested)
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = name,
                                color = if (isSelected) Color.White else EternaColors.MutedText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Search field
                TextField(
                    value = searchQuery,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            text = when(selectedType) {
                                "SHOPPING" -> "Search product (e.g., iPhone 15 pro, laptop)"
                                "CAB" -> "Search route (e.g., Jaipur to Airport)"
                                "FOOD" -> "Enter food (e.g., Paneer Tikka pizza)"
                                "TRAVEL" -> "Line route (e.g., Delhi to Jaipur)"
                                else -> "Enter product / route..."
                            },
                            fontSize = 12.sp
                        )
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = EternaColors.BackgroundDark,
                        unfocusedContainerColor = EternaColors.BackgroundDark,
                        disabledContainerColor = EternaColors.BackgroundDark,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = EternaColors.AccentPrimary,
                        focusedIndicatorColor = EternaColors.AccentPrimary,
                        unfocusedIndicatorColor = EternaColors.BorderHex
                    ),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Action Call
                Button(
                    onClick = {
                        if (searchQuery.trim().isNotEmpty()) {
                            viewModel.performComparison(searchQuery, selectedType)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = EternaColors.AccentPrimary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    if (compareLoading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("AI Comparing Fares... 🚀", fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.Search, contentDescription = "Search icon")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Instant Compare & Save Coins", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // RENDERING DYNAMIC COMPARATIVE RESULTS ARRAYS
        if (compareLoading) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Eterna Price Engine comparison in process...",
                    color = EternaColors.MutedText,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(14.dp))
                LinearProgressIndicator(color = EternaColors.AccentPrimary, modifier = Modifier.fillMaxWidth())
            }
        } else if (compareError != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF450A0A)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = compareError,
                    color = Color.Red,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else if (activeComparison != null) {
            ComparisonResultPanel(jsonData = activeComparison, viewModel = viewModel, searchCategory = selectedType)
        } else {
            // Display previous history from Room if empty
            if (previousComparisons.isNotEmpty()) {
                Text(
                    text = "Recent Searches & In-App Cache",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                previousComparisons.take(4).forEach { comp ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                            .clickable {
                                onQueryChange(comp.topic)
                                onTypeChange(comp.comparisonType)
                                viewModel.performComparison(comp.topic, comp.comparisonType)
                            },
                        colors = CardDefaults.cardColors(containerColor = EternaColors.CardBackground),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, EternaColors.BorderHex.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = when (comp.comparisonType) {
                                    "SHOPPING" -> "🛒"
                                    "CAB" -> "🚖"
                                    "FOOD" -> "🍔"
                                    "TRAVEL" -> "✈️"
                                    else -> "🔍"
                                },
                                fontSize = 18.sp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(comp.topic, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(comp.comparisonType, color = EternaColors.MutedText, fontSize = 10.sp)
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = "reload", tint = EternaColors.MutedText, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            } else {
                // Empty state helper
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("🍿", fontSize = 48.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Compare Everything Instantly",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Try searching: 'iPhone 15 pro' or cab 'Jaipur City to Airport'",
                        color = EternaColors.MutedText,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

// Result Panel comparing different platform lists
@Composable
fun ComparisonResultPanel(jsonData: JSONObject, viewModel: AppViewModel, searchCategory: String) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var claimedStatusMap = remember { mutableStateMapOf<Int, Boolean>() }

    val rawText = jsonData.optString("textResponse", "")
    val listArray = jsonData.optJSONArray("comparisonList") ?: JSONArray()

    Column(modifier = Modifier.fillMaxWidth()) {
        // AI Review Summary Block
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = EternaColors.CardBackground.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, EternaColors.AccentPrimary.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(EternaColors.AccentPrimary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("✨", fontSize = 11.sp)
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Eterna AI Smart Recommendation", color = EternaColors.AccentPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = rawText,
                    color = Color.White,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Comparable Table Title
        Text(
            text = "Comparative Pricing Matrix",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Loop cards comparison list
        for (i in 0 until listArray.length()) {
            val item = listArray.optJSONObject(i) ?: continue
            val platform = item.optString("platform", "")
            val title = item.optString("title", "")
            val price = item.optDouble("price", 0.0)
            val deliverySpeed = item.optString("deliveryOrSpeed", "")
            val rating = item.optString("rating", "4.3★")
            val coins = item.optInt("cashbackCoins", 20)
            val link = item.optString("link", "")

            val isClaimed = claimedStatusMap[i] ?: false

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                colors = CardDefaults.cardColors(containerColor = EternaColors.CardBackground),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, EternaColors.BorderHex)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Badge platform logo style
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        when (platform.lowercase()) {
                                            "amazon" -> Color(0xFFFF9900)
                                            "flipkart" -> Color(0xFF2874F0)
                                            "meesho" -> Color(0xFFF43397)
                                            "myntra" -> Color(0xFFFE3F6C)
                                            "uber" -> Color.Black
                                            "ola" -> Color(0xFF00FF00).copy(green = 0.7f)
                                            "rapido" -> Color(0xFFFCD34D)
                                            "zomato" -> Color(0xFFCB202D)
                                            "swiggy" -> Color(0xFFFC8019)
                                            else -> EternaColors.AccentPurple
                                        }
                                    )
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = platform,
                                    color = Color.White,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 10.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = rating,
                                color = EternaColors.GoldCoin,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }

                        // Price Typography
                        Text(
                            text = "₹${price.toInt()}",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 16.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(text = "⏱️ $deliverySpeed", color = EternaColors.MutedText, fontSize = 11.sp)

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = EternaColors.BorderHex.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🪙", fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Earn +$coins Eterna Coins",
                                color = EternaColors.GoldCoin,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Order Simulator button to gain coins cashback!
                        Button(
                            onClick = {
                                if (!isClaimed) {
                                    claimedStatusMap[i] = true
                                    coroutineScope.launch {
                                        // Save order to tracking automatically as a helper! "sb pr working"
                                        viewModel.createTrackedOrder(platform, title, price, "Tomorrow Express", "ET-" + (1000000..9999999).random().toString())
                                        
                                        // Add reward coins transaction
                                        viewModel.spinWinCashback() // Award user cashback
                                        Toast.makeText(context, "Order Simulated! +$coins Cashback Coins Claimed & Added to Live Tracker!", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isClaimed) EternaColors.BorderHex else EternaColors.AccentPrimary
                            ),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text(
                                text = if (isClaimed) "Claimed ✓" else "Order & Claim",
                                fontSize = 11.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

// ============================================
// TAB 1: AI ASSISTANT CHAT SCREEN (VOICE / CHAT SAMPLES)
// ============================================
@Composable
fun AiChatTab(viewModel: AppViewModel, compareLoading: Boolean, activeComparison: JSONObject?) {
    var userPrompt by remember { mutableStateOf("") }
    val chatScroll = rememberScrollState()

    Column(modifier = Modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = EternaColors.CardBackground),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, EternaColors.BorderHex)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Eterna AI Smart Chat",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    text = "A Ritik Technology Solution. Ask budget compare queries instantly.",
                    color = EternaColors.MutedText,
                    fontSize = 11.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Suggestion pills
                Text("Popular AI Suggestions:", color = EternaColors.MutedText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                
                val presets = listOf(
                    "Best iPhone under ₹50,000",
                    "Cheapest cab to Jaipur Airport",
                    "Best pizza deal near me"
                )

                presets.forEach { text ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                            .clickable {
                                userPrompt = text
                                viewModel.performComparison(text, "SHOPPING")
                            },
                        colors = CardDefaults.cardColors(containerColor = EternaColors.BackgroundDark),
                        border = BorderStroke(1.dp, EternaColors.BorderHex)
                    ) {
                        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.HelpOutline, contentDescription = "?", tint = EternaColors.AccentPrimary, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = EternaColors.BorderHex)
                Spacer(modifier = Modifier.height(12.dp))

                // Query text field
                TextField(
                    value = userPrompt,
                    onValueChange = { userPrompt = it },
                    placeholder = { Text("Ask Eterna AI companion... Try presets above", fontSize = 12.sp) },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = EternaColors.BackgroundDark,
                        unfocusedContainerColor = EternaColors.BackgroundDark,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = EternaColors.AccentPrimary,
                        focusedIndicatorColor = EternaColors.AccentPrimary,
                        unfocusedIndicatorColor = EternaColors.BorderHex
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        if (userPrompt.trim().isNotEmpty()) {
                            viewModel.performComparison(userPrompt, "SHOPPING")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EternaColors.AccentPrimary)
                ) {
                    Text("Ask Ritik AI Compare Engine", fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Live chatbot visualizer
        if (compareLoading) {
            LinearProgressIndicator(color = EternaColors.AccentPrimary, modifier = Modifier.fillMaxWidth())
        } else if (activeComparison != null) {
            ComparisonResultPanel(jsonData = activeComparison, viewModel = viewModel, searchCategory = "CAB")
        }
    }
}

// ============================================
// TAB 2: AI LINK ANALYZER SCREEN
// ============================================
@Composable
fun LinkAnalyzerTab(
    pastedLink: String,
    onLinkChange: (String) -> Unit,
    linkLoading: Boolean,
    activeLinkAnalysis: JSONObject?,
    viewModel: AppViewModel
) {
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = EternaColors.CardBackground),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, EternaColors.BorderHex)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "AI Product Link Analyzer",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    text = "Paste any product URL from Amazon, Flipkart or Myntra to let Ritik AI generate honest review highlights and finding lowest prices.",
                    color = EternaColors.MutedText,
                    fontSize = 11.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                TextField(
                    value = pastedLink,
                    onValueChange = onLinkChange,
                    placeholder = { Text("Paste Amazon / Flipkart product URL here...", fontSize = 11.sp) },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = EternaColors.BackgroundDark,
                        unfocusedContainerColor = EternaColors.BackgroundDark,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedIndicatorColor = EternaColors.AccentPrimary,
                        unfocusedIndicatorColor = EternaColors.BorderHex
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Preset sample buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        "https://amazon.in/dp/B0CHX1N1H5" to "Sample Amazon",
                        "https://flipkart.com/p/itm68b2dbfa" to "Sample Flipkart"
                    ).forEach { (url, label) ->
                        OutlinedButton(
                            onClick = { onLinkChange(url) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, EternaColors.BorderHex.copy(alpha = 0.5f))
                        ) {
                            Text(label, color = Color.White, fontSize = 10.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        if (pastedLink.trim().isNotEmpty()) {
                            viewModel.analyzeLink(pastedLink)
                        } else {
                            Toast.makeText(context, "Please paste an item URL link first!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = EternaColors.AccentPrimary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    if (linkLoading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Extracting Real Reviews...", fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.AutoAwesome, contentDescription = "AI")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Extract Pros & Cons", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Render AI Link review results
        if (linkLoading) {
            LinearProgressIndicator(color = EternaColors.AccentPrimary, modifier = Modifier.fillMaxWidth())
        } else if (activeLinkAnalysis != null) {
            val prodName = activeLinkAnalysis.optString("productName", "Analyzed Product")
            val summary = activeLinkAnalysis.optString("summary", "")
            val price = activeLinkAnalysis.optString("estimatedPrice", "")
            val platform = activeLinkAnalysis.optString("cheapestPlatform", "")
            val opinion = activeLinkAnalysis.optString("realOpinion", "")
            val alt = activeLinkAnalysis.optString("bestAlternative", "")
            val prosArray = activeLinkAnalysis.optJSONArray("pros") ?: JSONArray()
            val consArray = activeLinkAnalysis.optJSONArray("cons") ?: JSONArray()

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = EternaColors.CardBackground),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, EternaColors.BorderHex)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("AI LINK ANALYSIS BREAKDOWN", color = EternaColors.AccentPrimary, fontWeight = FontWeight.Black, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(prodName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Est. Price: $price  |  Cheapest: $platform", color = EternaColors.GoldCoin, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(summary, color = Color.White, fontSize = 12.sp)

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = EternaColors.BorderHex)
                    Spacer(modifier = Modifier.height(12.dp))

                    // Pros & Cons lists
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("🟩 PROS", color = Color.Green, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            for (p in 0 until prosArray.length()) {
                                Text("• " + prosArray.optString(p), color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(vertical = 2.dp))
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("🟥 CONS", color = Color.Red, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            for (c in 0 until consArray.length()) {
                                Text("• " + consArray.optString(c), color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(vertical = 2.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = EternaColors.BorderHex)
                    Spacer(modifier = Modifier.height(12.dp))

                    // Reddit Opinion
                    Text("🗣️ Online Review Consensus:", color = EternaColors.AccentPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    Text(opinion, color = Color.White, fontSize = 11.sp, fontStyle = FontStyle.Italic)

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("💡 Recommended Alternative Swap:", color = EternaColors.GoldCoin, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    Text(alt, color = Color.White, fontSize = 11.sp)
                }
            }
        }
    }
}

// ============================================
// TAB 3: SPENDING & BUDGET ANALYZER
// ============================================
@Composable
fun SpendingTab(
    spendingRecords: List<SpendingRecord>,
    spendAmount: String,
    onAmountChange: (String) -> Unit,
    spendDesc: String,
    onDescChange: (String) -> Unit,
    spendCategory: String,
    onCategoryChange: (String) -> Unit,
    insightsLoading: Boolean,
    insightsText: String,
    viewModel: AppViewModel
) {
    val context = LocalContext.current
    val totalExpense = spendingRecords.sumOf { it.amount }

    Column(modifier = Modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = EternaColors.CardBackground),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, EternaColors.BorderHex)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "AI Smart Budget Board",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    text = "Track your platform spends and ask Eterna Founder Ritik for advice.",
                    color = EternaColors.MutedText,
                    fontSize = 11.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Stats Dashboard Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(EternaColors.BackgroundDark)
                        .padding(12.dp)
                ) {
                    Column {
                        Text("TOTAL EVALUATED ONLINE EXPENDITURE", color = EternaColors.MutedText, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text("₹${totalExpense.toInt()}", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Input dialog style inside tab
                Text("Log Online Purchase Spend", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TextField(
                        value = spendAmount,
                        onValueChange = onAmountChange,
                        placeholder = { Text("Amount ₹", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = EternaColors.BackgroundDark,
                            unfocusedContainerColor = EternaColors.BackgroundDark,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedIndicatorColor = EternaColors.AccentPrimary
                        ),
                        singleLine = true
                    )
                    TextField(
                        value = spendDesc,
                        onValueChange = onDescChange,
                        placeholder = { Text("Purchase (e.g., Zomato pizza)", fontSize = 11.sp) },
                        modifier = Modifier.weight(2.5f),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = EternaColors.BackgroundDark,
                            unfocusedContainerColor = EternaColors.BackgroundDark,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedIndicatorColor = EternaColors.AccentPrimary
                        ),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Select log category row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("Shopping", "Food", "Cab", "Travel").forEach { cat ->
                        val isSelected = spendCategory == cat
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) EternaColors.AccentPrimary else EternaColors.BackgroundDark)
                                .clickable { onCategoryChange(cat) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(cat, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        val amtParsed = spendAmount.toDoubleOrNull()
                        if (amtParsed != null && spendDesc.trim().isNotEmpty()) {
                            viewModel.addSpending(spendCategory, amtParsed, spendDesc, "Today")
                            onAmountChange("")
                            onDescChange("")
                            Toast.makeText(context, "Purchase Logged!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Please enter a valid amount and description!", Toast.LENGTH_LONG).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = EternaColors.AccentPrimary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Add Purchase & Re-Calculate Spends", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // AI Advisory summary with Ritik Tech advice
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = EternaColors.CardBackground),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, EternaColors.AccentBlue.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(EternaColors.AccentBlue),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("👨‍💼", fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Ritik's Eterna AI Advisory Board", color = EternaColors.AccentBlue, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
                Spacer(modifier = Modifier.height(6.dp))

                if (insightsLoading) {
                    CircularProgressIndicator(color = EternaColors.AccentBlue, modifier = Modifier.size(16.dp))
                } else {
                    Text(
                        text = if (insightsText.isNotEmpty()) insightsText else "Add some purchases and let Ritik advise on potential savings of up to 25% on Amazon/Ola comparisons!",
                        color = Color.White,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { viewModel.generateSpendingInsights() },
                    colors = ButtonDefaults.buttonColors(containerColor = EternaColors.AccentBlue),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Generate Ritik AI Savings Report", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Lists current logged items
        if (spendingRecords.isNotEmpty()) {
            Text("In-App Purchase Logs History (" + spendingRecords.size + ")", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(6.dp))

            spendingRecords.forEach { record ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = EternaColors.CardBackground)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(EternaColors.BackgroundDark),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = when (record.category.lowercase()) {
                                    "shopping" -> "🛒"
                                    "food" -> "🍔"
                                    "cab" -> "🚖"
                                    "travel" -> "✈️"
                                    else -> "🏷️"
                                },
                                fontSize = 14.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(record.description, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text(record.category + " • " + record.dateStr, color = EternaColors.MutedText, fontSize = 10.sp)
                        }
                        Text("₹" + record.amount.toInt(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Spacer(modifier = Modifier.width(10.dp))
                        IconButton(onClick = { viewModel.deleteSpending(record.id) }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

// ============================================
// TAB 4: UNIVERSAL ORDER TRACKER
// ============================================
@Composable
fun OrderTrackerTab(
    trackedOrders: List<TrackedOrder>,
    showAddOrderDialog: Boolean,
    onShowDialogChange: (Boolean) -> Unit,
    orderProduct: String,
    onProductChange: (String) -> Unit,
    orderPrice: String,
    onPriceChange: (String) -> Unit,
    orderPlatform: String,
    onPlatformChange: (String) -> Unit,
    viewModel: AppViewModel
) {
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = EternaColors.CardBackground),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, EternaColors.BorderHex)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Universal Order Dashboard",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "Track Amazon, Swiggy, Uber in real-time.",
                            color = EternaColors.MutedText,
                            fontSize = 11.sp
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(EternaColors.AccentPrimary)
                            .clickable { onShowDialogChange(true) },// Open tracking insert
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Track", tint = Color.White)
                    }
                }

                if (showAddOrderDialog) {
                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = EternaColors.BorderHex)
                    Spacer(modifier = Modifier.height(14.dp))

                    Text("Log Custom Order to universal list:", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(6.dp))

                    TextField(
                        value = orderProduct,
                        onValueChange = onProductChange,
                        placeholder = { Text("Product/Item Name", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = EternaColors.BackgroundDark,
                            unfocusedContainerColor = EternaColors.BackgroundDark,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedIndicatorColor = EternaColors.AccentPrimary
                        )
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    TextField(
                        value = orderPrice,
                        onValueChange = onPriceChange,
                        placeholder = { Text("Price Paid ₹", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = EternaColors.BackgroundDark,
                            unfocusedContainerColor = EternaColors.BackgroundDark,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedIndicatorColor = EternaColors.AccentPrimary
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf("Amazon", "Flipkart", "Zomato", "Swiggy", "Uber").forEach { plat ->
                            val isSelected = orderPlatform == plat
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) EternaColors.AccentPrimary else EternaColors.BackgroundDark)
                                    .clickable { onPlatformChange(plat) }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(plat, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            onClick = {
                                val prParsed = orderPrice.toDoubleOrNull()
                                if (prParsed != null && orderProduct.isNotEmpty()) {
                                    viewModel.createTrackedOrder(
                                        orderPlatform,
                                        orderProduct,
                                        prParsed,
                                        "3 Days remaining",
                                        "ET-ORD-" + (100000..999999).random()
                                    )
                                    onProductChange("")
                                    onPriceChange("")
                                    onShowDialogChange(false)
                                    Toast.makeText(context, "Tracking Initiated!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Please enter correct order description and amount!", Toast.LENGTH_LONG).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = EternaColors.AccentPrimary),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Track Now", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = { onShowDialogChange(false) },
                            border = BorderStroke(1.dp, EternaColors.BorderHex),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel", color = Color.White, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Lists tracking items
        if (trackedOrders.isNotEmpty()) {
            trackedOrders.forEach { order ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = EternaColors.CardBackground),
                    border = BorderStroke(1.dp, EternaColors.BorderHex)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("[" + order.platform + "] Order Tracking", color = EternaColors.AccentPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                Text(order.productName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("ID: " + order.orderId, color = EternaColors.MutedText, fontSize = 10.sp)
                            }

                            // Dynamic state badge
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        when (order.status) {
                                            "Ordered" -> EternaColors.AccentBlue.copy(alpha = 0.2f)
                                            "In Transit" -> EternaColors.AccentPurple.copy(alpha = 0.2f)
                                            "Out for Delivery" -> EternaColors.GoldCoin.copy(alpha = 0.2f)
                                            "Delivered" -> EternaColors.AccentPrimary.copy(alpha = 0.2f)
                                            else -> EternaColors.BorderHex
                                        }
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = order.status,
                                    color = when (order.status) {
                                        "Ordered" -> EternaColors.AccentBlue
                                        "In Transit" -> EternaColors.AccentPurple
                                        "Out for Delivery" -> EternaColors.GoldCoin
                                        "Delivered" -> EternaColors.AccentPrimary
                                        else -> Color.White
                                    },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("📅", fontSize = 12.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Est. Arrival: " + order.deliveryDate, color = Color.White, fontSize = 11.sp)
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = EternaColors.BorderHex.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Cycle through statuses for simulation!
                            OutlinedButton(
                                onClick = {
                                    val next = when (order.status) {
                                        "Ordered" -> "In Transit"
                                        "In Transit" -> "Out for Delivery"
                                        "Out for Delivery" -> "Delivered"
                                        else -> "Ordered"
                                    }
                                    viewModel.updateOrderStatus(order, next)
                                    Toast.makeText(context, "Tracking status advance simulated!", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, EternaColors.BorderHex),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("Advance Journey Status 🚌", color = Color.White, fontSize = 9.sp)
                            }

                            IconButton(onClick = { viewModel.removeOrder(order.id) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Track", tint = Color.Red.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("📦", fontSize = 48.sp)
                Text("No Orders Tracked yet", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text("Click the top + icon or simulate orders in Tab 0 to track them!", color = EternaColors.MutedText, fontSize = 11.sp, textAlign = TextAlign.Center)
            }
        }
    }
}

// ============================================
// TAB 5: ETERNA COINS & CASHBACK REWARDS MINIGAME
// ============================================
@Composable
fun RewardsTab(
    coinsBalance: Int,
    coinTransactions: List<CoinTransaction>,
    isSpinning: Boolean,
    onSpinChange: (Boolean) -> Unit,
    spinAngle: Float,
    onAngleChange: (Float) -> Unit,
    viewModel: AppViewModel
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    // Rotating animation wrapper
    val rotation by animateFloatAsState(
        targetValue = spinAngle,
        animationSpec = if (isSpinning) {
            infiniteRepeatable(
                animation = tween(durationMillis = 800, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            )
        } else {
            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
        },
        label = "WheelSpin"
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = EternaColors.CardBackground),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, EternaColors.BorderHex)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Eterna Rewards Wallet",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.align(Alignment.Start)
                )
                Text(
                    text = "Convert Eterna Coins to direct real-world partner brand cashback.",
                    color = EternaColors.MutedText,
                    fontSize = 11.sp,
                    modifier = Modifier.align(Alignment.Start)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Wallet Board display with gold shine
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .scale(1.02f),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E38)),
                    border = BorderStroke(1.dp, EternaColors.GoldCoin)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("ACTIVE COINS BALANCE", color = EternaColors.MutedText, fontSize = 10.sp, fontWeight = FontWeight.Black)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🪙 ", fontSize = 28.sp)
                            Text(
                                text = "$coinsBalance Eterna Coins",
                                color = EternaColors.GoldCoin,
                                fontWeight = FontWeight.Black,
                                fontSize = 24.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Value: ₹${(coinsBalance * 0.1).toFloat()} cashback rewards", color = Color.Green, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // SPIN THE WHEEL GAME BOARD
                Text("🎡 Ritik's Daily Cashback Spin Wheel", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text("Spin every 24 hours to win up to 200 Coins!", color = EternaColors.MutedText, fontSize = 10.sp)
                
                Spacer(modifier = Modifier.height(14.dp))

                // Beautiful wheel canvas
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .rotate(rotation)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val canvasWidth = size.width
                        val canvasHeight = size.height
                        
                        // Draw multi slice colored wheel
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(EternaColors.AccentPurple, EternaColors.AccentPink)
                            ),
                            radius = canvasWidth / 2
                        )
                    }
                    // Text Overlay "SPIN"
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("💰", fontSize = 24.sp)
                            Text("SPIN ME", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        if (!isSpinning) {
                            onSpinChange(true)
                            onAngleChange(spinAngle + 1200f + (100..500).random())
                            
                            coroutineScope.launch {
                                delay(2200)
                                onSpinChange(false)
                                viewModel.spinWinCashback()
                                Toast.makeText(context, "Congratulations! Coins added to wallet!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EternaColors.AccentPrimary),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(0.8f),
                    enabled = !isSpinning
                ) {
                    Text(if (isSpinning) "Spinning... 🍀" else "Tap to Spin & Win", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Lists history logged Transactions items
        if (coinTransactions.isNotEmpty()) {
            Text("Eterna Coin Ledger History", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(6.dp))

            coinTransactions.forEach { tx ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = EternaColors.CardBackground)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🪙", fontSize = 18.sp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(tx.description, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("Activity Reward Code", color = EternaColors.MutedText, fontSize = 9.sp)
                        }
                        Text(
                            text = if (tx.isEarned) "+${tx.amount}" else "-${tx.amount}",
                            color = if (tx.isEarned) Color.Green else Color.Red,
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}
