package org.abodah.heymoney

import io.kotest.matchers.shouldBe
import org.abodah.heymoney.receipts.ReceiptParser
import org.junit.Test

class ReceiptParserTest {

    private val supermarket = """
        CARREFOUR MARKET
        Tel 01 23 45 67 89
        12/03/2025 14:32
        Lait 1L            1,20
        Pain               2,50
        SOUS-TOTAL         3,70
        TVA 5,5%           0,20
        TOTAL TTC         23,70 EUR
        Espèces           25,00
        Rendu              1,30
    """.trimIndent()

    @Test
    fun `reads total merchant currency and date from a supermarket receipt`() {
        val alert = ReceiptParser.parse(supermarket)!!
        alert.amount shouldBe 23.7
        alert.merchant shouldBe "CARREFOUR MARKET"
        alert.currency shouldBe "EUR"
        alert.isIncome shouldBe false
    }

    @Test
    fun `thousands separators are handled`() {
        val alert = ReceiptParser.parse("Boulangerie Dupont\nBaguette 1 200\nTOTAL 12 500 FCFA")!!
        alert.amount shouldBe 12500.0
        alert.currency shouldBe "XAF"
    }

    @Test
    fun `falls back to the largest amount without a total line`() {
        ReceiptParser.parse("Cafe du coin\nCafe 2.50\nGateau 4.00")!!.amount shouldBe 4.0
    }

    @Test
    fun `date formats`() {
        (ReceiptParser.findDate("le 05/11/2024") != null) shouldBe true
        (ReceiptParser.findDate("2024-11-05") != null) shouldBe true
        ReceiptParser.findDate("no date here") shouldBe null
        ReceiptParser.findDate("31/02/2024") shouldBe null
    }

    @Test
    fun `text without any amount gives nothing`() {
        ReceiptParser.parse("Thank you for your visit") shouldBe null
        ReceiptParser.parse("") shouldBe null
    }
}
