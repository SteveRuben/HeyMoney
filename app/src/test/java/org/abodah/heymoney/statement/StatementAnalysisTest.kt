package org.abodah.heymoney.statement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CsvStatementParserTest {

    @Test
    fun `english statement - signed amount column, quoted merchant with comma`() {
        val csv = """
            Date,Description,Amount,Category
            2026-01-05,"COFFEE SHOP, DOWNTOWN",-4.50,Food
            2026-01-06,PAYROLL ACME INC,2500.00,Income
            2026-01-07,NETFLIX.COM,-15.99,Entertainment
        """.trimIndent()

        val parsed = CsvStatementParser.parse(csv)

        assertEquals(3, parsed.lines.size)
        assertEquals(0, parsed.skippedRows)
        assertEquals(LocalDate.of(2026, 1, 5), parsed.lines[0].date)
        assertEquals("COFFEE SHOP, DOWNTOWN", parsed.lines[0].title)
        assertEquals(-4.50, parsed.lines[0].amount, 0.001)
        assertEquals("Food", parsed.lines[0].category)
        assertEquals(2500.00, parsed.lines[1].amount, 0.001)
    }

    @Test
    fun `french statement - semicolons, debit credit columns, decimal commas`() {
        val csv = """
            Date;Libellé;Débit;Crédit
            05/01/2026;FRAIS MENSUELS;5,00;
            06/01/2026;VIREMENT SALAIRE;;2 500,00
            07/01/2026;NETFLIX;15,99;
        """.trimIndent()

        val parsed = CsvStatementParser.parse(csv)

        assertEquals(3, parsed.lines.size)
        assertEquals(LocalDate.of(2026, 1, 5), parsed.lines[0].date)
        assertEquals(-5.00, parsed.lines[0].amount, 0.001)
        assertEquals(2500.00, parsed.lines[1].amount, 0.001)
        assertEquals(-15.99, parsed.lines[2].amount, 0.001)
    }

    @Test
    fun `amount formats across banks`() {
        assertEquals(1234.56, CsvStatementParser.parseAmount("1,234.56")!!, 0.001)
        assertEquals(1234.56, CsvStatementParser.parseAmount("1.234,56")!!, 0.001)
        assertEquals(1234.56, CsvStatementParser.parseAmount("1 234,56")!!, 0.001)
        assertEquals(-45.00, CsvStatementParser.parseAmount("(45.00)")!!, 0.001)
        assertEquals(-45.00, CsvStatementParser.parseAmount("45.00-")!!, 0.001)
        assertEquals(1234.0, CsvStatementParser.parseAmount("1,234")!!, 0.001)
        assertEquals(12.34, CsvStatementParser.parseAmount("12,34")!!, 0.001)
        assertEquals(-1234.56, CsvStatementParser.parseAmount("-$1,234.56")!!, 0.001)
        assertEquals(0.50, CsvStatementParser.parseAmount("0,50")!!, 0.001)
        assertNull(CsvStatementParser.parseAmount(""))
        assertNull(CsvStatementParser.parseAmount("abc"))
    }

    @Test
    fun `date formats - iso, day first, month first, month names`() {
        assertEquals(LocalDate.of(2026, 1, 15), CsvStatementParser.parseDate("2026-01-15"))
        assertEquals(LocalDate.of(2026, 1, 15), CsvStatementParser.parseDate("15/01/2026"))
        assertEquals(LocalDate.of(2026, 1, 15), CsvStatementParser.parseDate("01/15/2026"))
        assertEquals(LocalDate.of(2026, 1, 15), CsvStatementParser.parseDate("Jan 15, 2026"))
        assertEquals(LocalDate.of(2026, 1, 15), CsvStatementParser.parseDate("15 Jan 2026"))
        assertNull(CsvStatementParser.parseDate("not a date"))
        assertNull(CsvStatementParser.parseDate(""))
    }

    @Test
    fun `rows without a readable amount are skipped and counted`() {
        val csv = """
            Date,Description,Amount
            2026-01-05,BROKEN ROW,abc
            2026-01-06,OK ROW,-9.99
        """.trimIndent()

        val parsed = CsvStatementParser.parse(csv)

        assertEquals(1, parsed.lines.size)
        assertEquals(1, parsed.skippedRows)
    }

    @Test
    fun `a file without a recognisable header yields no lines`() {
        val parsed = CsvStatementParser.parse("hello,world\nfoo,bar")
        assertTrue(parsed.lines.isEmpty())
    }
}

class StatementAnalyzerTest {

    private fun line(
        title: String,
        amount: Double,
        date: LocalDate? = null,
        category: String? = null,
    ) = StatementLine(date = date, title = title, amount = amount, category = category)

    @Test
    fun `totals, net and period`() {
        val analysis = StatementAnalyzer.analyse(
            listOf(
                line("SALARY", 2000.0, LocalDate.of(2026, 1, 1)),
                line("RENT", -800.0, LocalDate.of(2026, 1, 3)),
                line("COFFEE", -5.0, LocalDate.of(2026, 1, 31)),
            )
        )

        assertEquals(3, analysis.transactionCount)
        assertEquals(2000.0, analysis.totalIn, 0.001)
        assertEquals(805.0, analysis.totalOut, 0.001)
        assertEquals(1195.0, analysis.net, 0.001)
        assertEquals(LocalDate.of(2026, 1, 1), analysis.periodStart)
        assertEquals(LocalDate.of(2026, 1, 31), analysis.periodEnd)
    }

    @Test
    fun `fees and interest charges count, interest earned does not`() {
        val analysis = StatementAnalyzer.analyse(
            listOf(
                line("MONTHLY ACCOUNT FEE", -5.0),
                line("INTEREST CHARGED", -12.30),
                line("FRAIS DE TENUE DE COMPTE", -7.0),
                line("INTEREST PAID", 2.0),
                line("GROCERIES", -50.0),
            )
        )

        assertEquals(3, analysis.feesCount)
        assertEquals(24.30, analysis.feesTotal, 0.001)
    }

    @Test
    fun `top merchants merge wording variants and sum charges`() {
        val analysis = StatementAnalyzer.analyse(
            listOf(
                line("NETFLIX.COM", -15.99),
                line("Netflix.com", -15.99),
                line("NETFLIX.COM", -15.99),
                line("SPOTIFY", -9.99),
            )
        )

        assertEquals(2, analysis.topMerchants.size)
        val top = analysis.topMerchants.first()
        assertEquals(3, top.count)
        assertEquals(47.97, top.total, 0.001)
    }

    @Test
    fun `categories group with a bucket for uncategorised lines`() {
        val analysis = StatementAnalyzer.analyse(
            listOf(
                line("SHOP A", -30.0, category = "Food"),
                line("SHOP B", -20.0, category = "Food"),
                line("PAY", 100.0, category = "Income"),
                line("MYSTERY", -10.0),
            )
        )

        val food = analysis.byCategory.first { it.category == "Food" }
        assertEquals(50.0, food.totalOut, 0.001)
        val uncategorised = analysis.byCategory.first { it.category == null }
        assertEquals(10.0, uncategorised.totalOut, 0.001)
    }

    @Test
    fun `largest operations are ranked by absolute amount`() {
        val analysis = StatementAnalyzer.analyse(
            listOf(
                line("SMALL", -5.0),
                line("BIG IN", 3000.0),
                line("BIG OUT", -1200.0),
            )
        )

        assertEquals(listOf("BIG IN", "BIG OUT", "SMALL"), analysis.largest.map { it.title })
    }
}
