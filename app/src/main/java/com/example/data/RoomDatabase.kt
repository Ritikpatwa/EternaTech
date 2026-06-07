package com.example.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

// --- Entities ---

@Entity(tableName = "search_comparisons")
data class SearchComparison(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val topic: String,
    val comparisonType: String, // SHOPPING, CAB, FOOD, TRAVEL
    val timestamp: Long = System.currentTimeMillis(),
    val jsonData: String
)

@Entity(tableName = "tracked_orders")
data class TrackedOrder(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val platform: String,
    val productName: String,
    val price: Double,
    val status: String, // Ordered, In Transit, Out for Delivery, Delivered, Cancelled
    val deliveryDate: String,
    val orderId: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "spending_records")
data class SpendingRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val category: String, // Shopping, Food, Cab, Travel, Subscription
    val amount: Double,
    val description: String,
    val dateStr: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "coin_transactions")
data class CoinTransaction(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val amount: Int,
    val description: String,
    val isEarned: Boolean, // true = earned, false = redeemed
    val timestamp: Long = System.currentTimeMillis()
)

// --- DAOs ---

@Dao
interface SearchComparisonDao {
    @Query("SELECT * FROM search_comparisons ORDER BY timestamp DESC")
    fun getAllComparisons(): Flow<List<SearchComparison>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComparison(comparison: SearchComparison)

    @Query("DELETE FROM search_comparisons")
    suspend fun clearAll()
}

@Dao
interface TrackedOrderDao {
    @Query("SELECT * FROM tracked_orders ORDER BY timestamp DESC")
    fun getAllOrders(): Flow<List<TrackedOrder>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrder(order: TrackedOrder)

    @Update
    suspend fun updateOrder(order: TrackedOrder)

    @Query("DELETE FROM tracked_orders WHERE id = :id")
    suspend fun deleteOrderById(id: Int)
}

@Dao
interface SpendingRecordDao {
    @Query("SELECT * FROM spending_records ORDER BY timestamp DESC")
    fun getAllSpending(): Flow<List<SpendingRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSpending(spending: SpendingRecord)

    @Query("DELETE FROM spending_records WHERE id = :id")
    suspend fun deleteSpendingById(id: Int)
}

@Dao
interface CoinTransactionDao {
    @Query("SELECT * FROM coin_transactions ORDER BY timestamp DESC")
    fun getAllTransactions(): Flow<List<CoinTransaction>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: CoinTransaction)

    @Query("SELECT SUM(CASE WHEN isEarned = 1 THEN amount ELSE -amount END) FROM coin_transactions")
    fun getCoinsBalanceFlow(): Flow<Int?>
}

// --- App Database ---

@Database(
    entities = [
        SearchComparison::class,
        TrackedOrder::class,
        SpendingRecord::class,
        CoinTransaction::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun searchComparisonDao(): SearchComparisonDao
    abstract fun trackedOrderDao(): TrackedOrderDao
    abstract fun spendingRecordDao(): SpendingRecordDao
    abstract fun coinTransactionDao(): CoinTransactionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "eterna_price_calculator_db"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

// --- Unified Repository Pattern ---

class AppRepository(private val db: AppDatabase) {
    val searchComparisons: Flow<List<SearchComparison>> = db.searchComparisonDao().getAllComparisons()
    val trackedOrders: Flow<List<TrackedOrder>> = db.trackedOrderDao().getAllOrders()
    val spendingRecords: Flow<List<SpendingRecord>> = db.spendingRecordDao().getAllSpending()
    val coinTransactions: Flow<List<CoinTransaction>> = db.coinTransactionDao().getAllTransactions()
    val coinsBalance: Flow<Int?> = db.coinTransactionDao().getCoinsBalanceFlow()

    suspend fun insertComparison(item: SearchComparison) {
        db.searchComparisonDao().insertComparison(item)
    }

    suspend fun clearComparisons() {
        db.searchComparisonDao().clearAll()
    }

    suspend fun insertOrder(order: TrackedOrder) {
        db.trackedOrderDao().insertOrder(order)
    }

    suspend fun updateOrder(order: TrackedOrder) {
        db.trackedOrderDao().updateOrder(order)
    }

    suspend fun deleteOrder(orderId: Int) {
        db.trackedOrderDao().deleteOrderById(orderId)
    }

    suspend fun insertSpending(record: SpendingRecord) {
        db.spendingRecordDao().insertSpending(record)
    }

    suspend fun deleteSpending(spendingId: Int) {
        db.spendingRecordDao().deleteSpendingById(spendingId)
    }

    suspend fun insertCoinTransaction(transaction: CoinTransaction) {
        db.coinTransactionDao().insertTransaction(transaction)
    }
}
