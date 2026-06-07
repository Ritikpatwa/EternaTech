package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.api.GeminiClient
import com.example.api.MockGenerator
import com.example.data.AppDatabase
import com.example.data.AppRepository
import com.example.data.CoinTransaction
import com.example.data.SearchComparison
import com.example.data.SpendingRecord
import com.example.data.TrackedOrder
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = AppRepository(db)

    // Reactive streams from Database
    val previousComparisons = repository.searchComparisons.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    val trackedOrders = repository.trackedOrders.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    val spendingRecords = repository.spendingRecords.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    val coinsBalance = repository.coinsBalance.map { it ?: 0 }.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), 0
    )

    val coinTransactions = repository.coinTransactions.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    // UI state for Live Compare queries
    private val _compareLoading = MutableStateFlow(false)
    val compareLoading: StateFlow<Boolean> = _compareLoading.asStateFlow()

    private val _activeComparison = MutableStateFlow<JSONObject?>(null)
    val activeComparison: StateFlow<JSONObject?> = _activeComparison.asStateFlow()

    private val _compareError = MutableStateFlow<String?>(null)
    val compareError: StateFlow<String?> = _compareError.asStateFlow()

    // UI state for AI Link pasting
    private val _linkLoading = MutableStateFlow(false)
    val linkLoading: StateFlow<Boolean> = _linkLoading.asStateFlow()

    private val _activeLinkAnalysis = MutableStateFlow<JSONObject?>(null)
    val activeLinkAnalysis: StateFlow<JSONObject?> = _activeLinkAnalysis.asStateFlow()

    // UI state for AI Spending Insights feedback
    private val _insightsLoading = MutableStateFlow(false)
    val insightsLoading: StateFlow<Boolean> = _insightsLoading.asStateFlow()

    private val _spendingInsightsText = MutableStateFlow("")
    val spendingInsightsText: StateFlow<String> = _spendingInsightsText.asStateFlow()

    // API connectivity test states
    private val _apiConnectionActive = MutableStateFlow<Boolean?>(null)
    val apiConnectionActive: StateFlow<Boolean?> = _apiConnectionActive.asStateFlow()

    private val _testingConnection = MutableStateFlow(false)
    val testingConnection: StateFlow<Boolean> = _testingConnection.asStateFlow()

    fun checkApiConnection() {
        viewModelScope.launch {
            _testingConnection.value = true
            val success = GeminiClient.testGeminiConnection()
            _apiConnectionActive.value = success
            _testingConnection.value = false
        }
    }

    init {
        // Seed default database values if empty
        viewModelScope.launch {
            repository.coinTransactions.first().let { list ->
                if (list.isEmpty()) {
                    // Start user with 100 Welcome Coins!
                    repository.insertCoinTransaction(
                        CoinTransaction(
                            amount = 100,
                            description = "Welcome Eterna Tech Bonus 🎉",
                            isEarned = true
                        )
                    )
                    // Seed some default spending items
                    repository.insertSpending(SpendingRecord(category = "Shopping", amount = 12000.0, description = "Flipkart Electronics", dateStr = "Jun 5"))
                    repository.insertSpending(SpendingRecord(category = "Food", amount = 2400.0, description = "Swiggy Meals", dateStr = "Jun 3"))
                    repository.insertSpending(SpendingRecord(category = "Cab", amount = 1450.0, description = "Uber Jaipur Journey", dateStr = "May 28"))
                    repository.insertSpending(SpendingRecord(category = "Subscription", amount = 699.0, description = "Streaming Service", dateStr = "May 15"))

                    // Seed standard dummy orders
                    repository.insertOrder(
                        TrackedOrder(
                            platform = "Amazon",
                            productName = "Spigen iPhone Cover Case",
                            price = 1499.0,
                            status = "In Transit",
                            deliveryDate = "Tomorrow, 5 PM",
                            orderId = "AMM-902-8812"
                        )
                    )
                }
            }
        }
    }

    /**
     * Run Universal Price Comparison Search (Shopping, Cab, Food, Travel)
     */
    fun performComparison(query: String, type: String) {
        if (query.trim().isEmpty()) return
        viewModelScope.launch {
            _compareLoading.value = true
            _compareError.value = null
            _activeComparison.value = null

            try {
                val resultJson = when (type) {
                    "SHOPPING" -> GeminiClient.compareProducts(query)
                    "CAB" -> {
                        val split = query.split(" to ")
                        if (split.size >= 2) {
                            GeminiClient.compareCabs(split[0].trim(), split[1].trim())
                        } else {
                            GeminiClient.compareCabs(query, "$query Airport")
                        }
                    }
                    "FOOD" -> {
                        // Food comparison fallback estimator
                        withContext(kotlinx.coroutines.Dispatchers.IO) {
                            val raw = GeminiClient.queryGemini(
                                "Dish: $query",
                                "Respond in JSON. Compare Zomato and Swiggy pricing for pizza, burger or generic food item: $query in Indian Rupees. Return structured response JSON matching MockGenerator.fallbackFood format."
                            )
                            if (raw != null) JSONObject(raw) else MockGenerator.fallbackFood(query)
                        }
                    }
                    "TRAVEL" -> {
                        // Flight & Hotel comparison fallback estimator
                        val split = query.split(" to ")
                        val fromCity = if (split.isNotEmpty()) split[0].trim() else "Delhi"
                        val toCity = if (split.size >= 2) split[1].trim() else "Mumbai"
                        withContext(kotlinx.coroutines.Dispatchers.IO) {
                            val raw = GeminiClient.queryGemini(
                                "Route: $fromCity to $toCity",
                                "Respond in JSON. Compare travel platforms (MakeMyTrip, Goibibo, Yatra) flights for: $fromCity to $toCity. Return JSON matching MockGenerator.fallbackTravel format."
                            )
                            if (raw != null) JSONObject(raw) else MockGenerator.fallbackTravel(fromCity, toCity)
                        }
                    }
                    else -> GeminiClient.compareProducts(query)
                }

                _activeComparison.value = resultJson

                // Save search to history Room database
                repository.insertComparison(
                    SearchComparison(
                        topic = query,
                        comparisonType = type,
                        jsonData = resultJson.toString()
                    )
                )

                // Earn reward coins for comparing!
                val rewardAmount = (10..30).random()
                repository.insertCoinTransaction(
                    CoinTransaction(
                        amount = rewardAmount,
                        description = "Cashback for '$query' comparison search 🪙",
                        isEarned = true
                    )
                )

            } catch (e: Exception) {
                _compareError.value = "Failed to run comparison: ${e.message}"
            } finally {
                _compareLoading.value = false
            }
        }
    }

    /**
     * AI link analyser for pasted urls
     */
    fun analyzeLink(url: String) {
        if (url.trim().isEmpty()) return
        viewModelScope.launch {
            _linkLoading.value = true
            _activeLinkAnalysis.value = null

            try {
                val jsonResult = GeminiClient.analyzeProductLink(url)
                _activeLinkAnalysis.value = jsonResult

                // Award 15 Eterna Coins for AI research
                repository.insertCoinTransaction(
                    CoinTransaction(
                        amount = 15,
                        description = "AI Link analysis bonus",
                        isEarned = true
                    )
                )
            } catch (e: Exception) {
                // Ignore or handle
            } finally {
                _linkLoading.value = false
            }
        }
    }

    /**
     * Spending insights compiled through Ritik AI assistant
     */
    fun generateSpendingInsights() {
        viewModelScope.launch {
            _insightsLoading.value = true
            _spendingInsightsText.value = ""

            try {
                val spends = spendingRecords.value
                if (spends.isEmpty()) {
                    _spendingInsightsText.value = "No spend found. Insert spend logs below to let Ritik AI analyze your shopping budget!"
                    return@launch
                }

                val sb = StringBuilder("Spending Records:\n")
                spends.forEach { record ->
                    sb.append("- ${record.category}: ₹${record.amount} for ${record.description}\n")
                }

                val aiReportText = GeminiClient.analyzeSpendingLogs(sb.toString())
                _spendingInsightsText.value = aiReportText
            } catch (e: Exception) {
                _spendingInsightsText.value = "Error compiling insights: ${e.message}"
            } finally {
                _insightsLoading.value = false
            }
        }
    }

    /**
     * Manage user spending logs
     */
    fun addSpending(category: String, amount: Double, desc: String, date: String) {
        viewModelScope.launch {
            repository.insertSpending(
                SpendingRecord(
                    category = category,
                    amount = amount,
                    description = desc,
                    dateStr = date
                )
            )
            // Generate insight advice automatically
            generateSpendingInsights()
        }
    }

    fun deleteSpending(id: Int) {
        viewModelScope.launch {
            repository.deleteSpending(id)
            generateSpendingInsights()
        }
    }

    /**
     * Manage Order tracking items
     */
    fun createTrackedOrder(platform: String, product: String, price: Double, dateStr: String, idStr: String) {
        viewModelScope.launch {
            repository.insertOrder(
                TrackedOrder(
                    platform = platform,
                    productName = product,
                    price = price,
                    status = "Ordered",
                    deliveryDate = dateStr,
                    orderId = idStr
                )
            )
        }
    }

    fun updateOrderStatus(order: TrackedOrder, nextStatus: String) {
        viewModelScope.launch {
            repository.updateOrder(order.copy(status = nextStatus))
        }
    }

    fun removeOrder(orderId: Int) {
        viewModelScope.launch {
            repository.deleteOrder(orderId)
        }
    }

    /**
     * spin the wheel cashback simulated helper
     */
    fun spinWinCashback() {
        viewModelScope.launch {
            val amountWon = listOf(10, 25, 50, 100, 200).random()
            repository.insertCoinTransaction(
                CoinTransaction(
                    amount = amountWon,
                    description = "Spin-the-wheel daily prize! 🎡",
                    isEarned = true
                )
            )
        }
    }

    fun clearAllComparisons() {
        viewModelScope.launch {
            repository.clearComparisons()
        }
    }

    /**
     * Generate comparative txt report string for direct download/copy clipboard
     */
    fun generateReportData(): String {
        val totalSpent = spendingRecords.value.sumOf { it.amount }
        val balance = coinsBalance.value
        val sb = StringBuilder()
        sb.append("====================================================\n")
        sb.append("         ETERNA PRICE CALCULATOR REPORT             \n")
        sb.append("        Developed by Eterna Tech (Founder: Ritik)   \n")
        sb.append("====================================================\n\n")
        sb.append("1. USER WALLET COINS DETAILED SUMMARY:\n")
        sb.append(" - Available Eterna Coins Balance: $balance COINS\n")
        sb.append(" - Total Transactions Saved: ${coinTransactions.value.size}\n\n")
        sb.append("2. USER CURRENT SPENDING SUMMARY:\n")
        sb.append(" - Net Evaluated Monthly Spending: ₹$totalSpent\n")
        spendingRecords.value.forEach { record ->
            sb.append("   * [${record.category}] - ₹${record.amount} (Desc: ${record.description})\n")
        }
        sb.append("\n3. UNIVERSAL LOGGED ACTIVE PRICE RESEARCH:\n")
        previousComparisons.value.take(5).forEach { comp ->
            sb.append(" - [${comp.comparisonType}] Log: '${comp.topic}' searched at standard log.\n")
        }
        sb.append("\n====================================================\n")
        sb.append("Report Compiled Successfully. Find Cheapest, Save Smart!")
        return sb.toString()
    }
}
