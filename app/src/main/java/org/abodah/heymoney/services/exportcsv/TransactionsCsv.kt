package org.abodah.heymoney.services.exportcsv

import com.opencsv.bean.CsvBindByName
import org.abodah.heymoney.domain.model.Transaction


data class TransactionsCSV(
    @CsvBindByName(column = "title")
    val title: String,
    @CsvBindByName(column = "amount")
    val amount: Double,
    @CsvBindByName(column = "transactionType")
    val transactionType: String,
    @CsvBindByName(column = "tag")
    val tag: String,
    @CsvBindByName(column = "date")
    val date: Long,
    @CsvBindByName(column = "note")
    val note: String,
    @CsvBindByName(column = "createdAt")
    val createdAtDate: Long
)

fun List<Transaction>.toCsv() = map {
    TransactionsCSV(
        title = it.title,
        amount = it.amount,
        transactionType = it.transactionType,
        tag = it.tag,
        date = it.date,
        note = it.note,
        createdAtDate = it.createdAt,
    )
}
