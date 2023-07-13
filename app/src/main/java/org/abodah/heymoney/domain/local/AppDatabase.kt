package org.abodah.heymoney.domain.local

import androidx.room.Database
import androidx.room.RoomDatabase
import org.abodah.heymoney.domain.dao.TransactionDao
import org.abodah.heymoney.domain.model.Transaction

@Database(
    entities = [Transaction::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun getTransactionDao(): TransactionDao
}
