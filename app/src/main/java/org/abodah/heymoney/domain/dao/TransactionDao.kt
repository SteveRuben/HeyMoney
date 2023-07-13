package org.abodah.heymoney.domain.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import org.abodah.heymoney.domain.model.Transaction

@Dao
interface TransactionDao {

    // used to insert new transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: Transaction)

    // used to update existing transaction
    @Update(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateTransaction(transaction: Transaction)

    // used to delete transaction
    @Delete
    suspend fun deleteTransaction(transaction: Transaction)

    // get all saved transaction list
    @Query("SELECT * FROM all_transactions ORDER by createdAt DESC")
    fun getAllTransactions(): Flow<List<Transaction>>

    // get all income or expense list by transaction type param
    @Query("SELECT * FROM all_transactions WHERE transactionType == :transactionType ORDER by createdAt DESC")
    fun getAllSingleTransaction(transactionType: String): Flow<List<Transaction>>

    // get single transaction by id
    @Query("SELECT * FROM all_transactions WHERE id = :id")
    fun getTransactionByID(id: Int): Flow<Transaction>

    // delete transaction by id
    @Query("DELETE FROM all_transactions WHERE id = :id")
    suspend fun deleteTransactionByID(id: Int)
}