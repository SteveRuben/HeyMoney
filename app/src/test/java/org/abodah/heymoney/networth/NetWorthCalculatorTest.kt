package org.abodah.heymoney.networth

import com.ivy.base.model.LoanRecordType
import com.ivy.base.model.TransactionType
import com.ivy.data.db.entity.AccountEntity
import com.ivy.data.db.entity.LoanEntity
import com.ivy.data.db.entity.LoanRecordEntity
import com.ivy.data.db.entity.TransactionEntity
import com.ivy.data.model.LoanType
import io.kotest.matchers.shouldBe
import org.junit.Test
import java.time.Instant
import java.util.UUID

class NetWorthCalculatorTest {

    private val calculator = NetWorthCalculator()
    private val baseCurrency = "CAD"

    private fun account(
        name: String,
        currency: String? = baseCurrency,
        includeInBalance: Boolean = true,
    ): AccountEntity = AccountEntity(
        name = name,
        currency = currency,
        color = 0,
        includeInBalance = includeInBalance,
    )

    private fun transaction(
        account: AccountEntity,
        type: TransactionType,
        amount: Double,
        toAccount: AccountEntity? = null,
        toAmount: Double? = null,
        loanId: UUID? = null,
    ): TransactionEntity = TransactionEntity(
        accountId = account.id,
        type = type,
        amount = amount,
        toAccountId = toAccount?.id,
        toAmount = toAmount,
        loanId = loanId,
    )

    private fun loan(
        name: String,
        amount: Double,
        type: LoanType,
        account: AccountEntity? = null,
    ): LoanEntity = LoanEntity(name = name, amount = amount, type = type, accountId = account?.id)

    private fun record(
        loan: LoanEntity,
        amount: Double,
        type: LoanRecordType = LoanRecordType.DECREASE,
        interest: Boolean = false,
        convertedAmount: Double? = null,
    ): LoanRecordEntity = LoanRecordEntity(
        loanId = loan.id,
        amount = amount,
        dateTime = Instant.parse("2026-06-01T12:00:00Z"),
        interest = interest,
        convertedAmount = convertedAmount,
        loanRecordType = type,
    )

    @Test
    fun `account balance follows income expense and transfers`() {
        val a = account("A")
        val b = account("B")
        val transactions = listOf(
            transaction(a, TransactionType.INCOME, 1000.0),
            transaction(a, TransactionType.EXPENSE, 250.0),
            transaction(a, TransactionType.TRANSFER, 100.0, toAccount = b, toAmount = 95.0),
            transaction(b, TransactionType.EXPENSE, 30.0),
        )

        accountBalance(a.id, transactions) shouldBe 650.0
        // The receiving side counts what actually arrived (toAmount), not what left.
        accountBalance(b.id, transactions) shouldBe 65.0
    }

    @Test
    fun `loan remaining uses records like the loans screen`() {
        val loan = loan("Car", 1000.0, LoanType.BORROW)
        val records = listOf(
            record(loan, 400.0),
            record(loan, 50.0, interest = true),
            record(loan, 200.0, type = LoanRecordType.INCREASE),
        )

        // Interest does not touch the principal; an increase grows what is owed.
        loanRemaining(loan, records) shouldBe 800.0
    }

    @Test
    fun `loan remaining prefers the converted amount and never goes below zero`() {
        val loan = loan("Trip", 300.0, LoanType.LEND)
        val records = listOf(
            record(loan, 100.0, convertedAmount = 140.0),
            record(loan, 500.0),
        )

        loanRemaining(loan, records) shouldBe 0.0
    }

    @Test
    fun `net worth adds lent and subtracts borrowed per currency`() {
        val cad = account("Chequing")
        val eur = account("Euro account", currency = "EUR")
        val lent = loan("Loan to Sam", 500.0, LoanType.LEND)
        val borrowed = loan("Card debt", 900.0, LoanType.BORROW)
        val transactions = listOf(
            transaction(cad, TransactionType.INCOME, 2000.0),
            transaction(eur, TransactionType.INCOME, 300.0),
        )
        val records = listOf(record(lent, 100.0), record(borrowed, 300.0))

        val result = calculator.calculate(
            accounts = listOf(cad, eur),
            transactions = transactions,
            loans = listOf(lent, borrowed),
            records = records,
            baseCurrency = baseCurrency,
        )

        result.map { it.currency } shouldBe listOf("CAD", "EUR")
        val cadWorth = result.first()
        cadWorth.accountsTotal shouldBe 2000.0
        cadWorth.lentTotal shouldBe 400.0
        cadWorth.borrowedTotal shouldBe 600.0
        cadWorth.netWorth shouldBe 1800.0
        result[1].netWorth shouldBe 300.0
    }

    @Test
    fun `borrowed cash in the account is not counted twice`() {
        // The loan's cash leg is in the balance (income of 1000, repayment of
        // 400 out): the balance holds exactly the 600 still owed, and the net
        // worth removes it — the borrowed money is not wealth.
        val account = account("Chequing")
        val loan = loan("Bank loan", 1000.0, LoanType.BORROW, account = account)
        val transactions = listOf(
            transaction(account, TransactionType.INCOME, 1000.0, loanId = loan.id),
            transaction(account, TransactionType.EXPENSE, 400.0),
        )
        val records = listOf(record(loan, 400.0))

        val result = calculator.calculate(
            accounts = listOf(account),
            transactions = transactions,
            loans = listOf(loan),
            records = records,
            baseCurrency = baseCurrency,
        )

        val worth = result.single()
        worth.accountsTotal shouldBe 600.0
        worth.borrowed.single().linkedToAccount shouldBe true
        worth.netWorth shouldBe 0.0
    }

    @Test
    fun `accounts excluded from the balance and paid off loans do not compose`() {
        val visible = account("Visible")
        val hidden = account("Hidden", includeInBalance = false)
        val paidOff = loan("Old loan", 100.0, LoanType.BORROW)
        val transactions = listOf(
            transaction(visible, TransactionType.INCOME, 50.0),
            transaction(hidden, TransactionType.INCOME, 999.0),
        )

        val result = calculator.calculate(
            accounts = listOf(visible, hidden),
            transactions = transactions,
            loans = listOf(paidOff),
            records = listOf(record(paidOff, 100.0)),
            baseCurrency = baseCurrency,
        )

        val worth = result.single()
        worth.accounts.map { it.name } shouldBe listOf("Visible")
        worth.borrowed shouldBe emptyList()
        worth.netWorth shouldBe 50.0
    }

    @Test
    fun `an account without currency falls back to the base currency`() {
        val account = account("Cash", currency = null)
        val transactions = listOf(transaction(account, TransactionType.INCOME, 75.0))

        val result = calculator.calculate(
            accounts = listOf(account),
            transactions = transactions,
            loans = emptyList(),
            records = emptyList(),
            baseCurrency = baseCurrency,
        )

        result.single().currency shouldBe baseCurrency
        result.single().netWorth shouldBe 75.0
    }
}
