package com.example.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE mobileNumber = :mobile LIMIT 1")
    suspend fun getUserByMobile(mobile: String): UserEntity?

    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    suspend fun getUserById(id: Int): UserEntity?

    @Query("SELECT * FROM users")
    suspend fun getAllUsers(): List<UserEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity): Long

    @Update
    suspend fun updateUser(user: UserEntity)

    @Query("SELECT COUNT(*) FROM users")
    suspend fun getUserCount(): Int

    @Query("DELETE FROM users")
    suspend fun clearAllUsers()
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY name ASC")
    fun getAllCategories(): Flow<List<CategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: CategoryEntity): Long

    @Update
    suspend fun updateCategory(category: CategoryEntity)

    @Delete
    suspend fun deleteCategory(category: CategoryEntity)

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun getCategoryCount(): Int
}

@Dao
interface InvoiceDao {
    @Query("SELECT * FROM invoices ORDER BY timestamp DESC")
    fun getAllInvoices(): Flow<List<InvoiceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvoice(invoice: InvoiceEntity): Long

    @Query("SELECT SUM(amount) FROM invoices WHERE status = 'Paid'")
    fun getTotalSales(): Flow<Double?>

    @Query("SELECT COUNT(*) FROM invoices")
    fun getInvoicesCount(): Flow<Int>

    @Query("DELETE FROM invoices")
    suspend fun clearAllInvoices()
}

@Dao
interface ProductDao {
    @Query("SELECT * FROM products ORDER BY name ASC")
    fun getAllProducts(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products ORDER BY name ASC")
    suspend fun getAllProductsList(): List<ProductEntity>

    @Query("SELECT * FROM products WHERE id = :id LIMIT 1")
    suspend fun getProductById(id: Int): ProductEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: ProductEntity): Long

    @Update
    suspend fun updateProduct(product: ProductEntity)

    @Delete
    suspend fun deleteProduct(product: ProductEntity)

    @Query("DELETE FROM products WHERE id = :id")
    suspend fun deleteProductById(id: Int)

    @Query("SELECT COUNT(*) FROM products")
    suspend fun getProductCount(): Int

    @Query("DELETE FROM products")
    suspend fun clearAllProducts()
}

@Dao
interface CustomerDao {
    @Query("SELECT * FROM customers ORDER BY lastTransactionTimestamp DESC")
    fun getAllCustomers(): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE mobileNumber = :mobile LIMIT 1")
    suspend fun getCustomerByMobile(mobile: String): CustomerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomer(customer: CustomerEntity): Long

    @Update
    suspend fun updateCustomer(customer: CustomerEntity)

    @Delete
    suspend fun deleteCustomer(customer: CustomerEntity)

    @Query("UPDATE customers SET reminderScheduledDate = :date, reminderStatus = :status WHERE mobileNumber = :mobile")
    suspend fun updateReminderSchedule(mobile: String, date: Long, status: String)

    @Query("UPDATE customers SET reminderStatus = :status WHERE mobileNumber = :mobile")
    suspend fun updateReminderStatus(mobile: String, status: String)

    @Query("SELECT COUNT(*) FROM customers")
    suspend fun getCustomerCount(): Int

    @Query("DELETE FROM customers")
    suspend fun clearAllCustomers()
}

@Dao
interface CustomerTransactionDao {
    @Query("SELECT * FROM customer_transactions WHERE customerMobile = :mobile ORDER BY timestamp DESC")
    fun getTransactionsForCustomer(mobile: String): Flow<List<CustomerTransactionEntity>>

    @Query("SELECT * FROM customer_transactions WHERE customerMobile = :mobile ORDER BY timestamp DESC")
    suspend fun getTransactionsForCustomerSync(mobile: String): List<CustomerTransactionEntity>

    @Query("SELECT * FROM customer_transactions WHERE (invoiceId IS NOT NULL AND invoiceId != '') AND (invoiceId = :id1 OR invoiceId = :id2 OR invoiceId = :id3 OR invoiceId = :id4) LIMIT 1")
    suspend fun getTransactionByInvoiceId(id1: String, id2: String = id1, id3: String = id1, id4: String = id1): CustomerTransactionEntity?

    @Query("DELETE FROM customer_transactions WHERE (invoiceId IS NOT NULL AND invoiceId != '') AND (invoiceId = :id1 OR invoiceId = :id2 OR invoiceId = :id3 OR invoiceId = :id4)")
    suspend fun deleteTransactionByInvoiceId(id1: String, id2: String = id1, id3: String = id1, id4: String = id1)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: CustomerTransactionEntity): Long

    @Query("SELECT COUNT(*) FROM customer_transactions")
    suspend fun getTransactionCount(): Int

    @Query("DELETE FROM customer_transactions")
    suspend fun clearAllTransactions()
}
