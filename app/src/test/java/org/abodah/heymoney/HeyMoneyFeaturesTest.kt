package org.abodah.heymoney

import io.kotest.matchers.shouldBe
import org.abodah.heymoney.alerts.BankAlert
import org.abodah.heymoney.alerts.BankAlertParser
import org.abodah.heymoney.alerts.CategoryHints
import org.abodah.heymoney.share.ShareBatch
import org.abodah.heymoney.share.ShareCodec
import org.abodah.heymoney.share.ShareException
import org.abodah.heymoney.share.SharedTransaction
import org.junit.Assert.assertThrows
import org.junit.Test

class HeyMoneyFeaturesTest {

    @Test
    fun `parses an expense SMS`() {
        val alert = BankAlertParser.parse(
            BankAlert.Source.SMS, "MYBANK",
            "Achat de 5 000 FCFA chez Carrefour. Solde disponible 120 000 FCFA"
        )!!
        alert.amount shouldBe 5000.0
        alert.currency shouldBe "XAF"
        alert.isIncome shouldBe false
        alert.merchant shouldBe "Carrefour"
    }

    @Test
    fun `parses an income notification`() {
        val alert = BankAlertParser.parse(
            BankAlert.Source.NOTIFICATION, "Bank",
            "You received 1,250.50 EUR salary deposit"
        )!!
        alert.amount shouldBe 1250.5
        alert.currency shouldBe "EUR"
        alert.isIncome shouldBe true
    }

    @Test
    fun `ignores text that is not a transaction`() {
        BankAlertParser.looksLikeBankAlert("Happy birthday, see you at 8!") shouldBe false
        BankAlertParser.parse(BankAlert.Source.SMS, "x", "Your code is 483920") shouldBe null
    }

    @Test
    fun `amount formats are understood`() {
        BankAlertParser.parseAmount("1.234,56") shouldBe 1234.56
        BankAlertParser.parseAmount("1,234.56") shouldBe 1234.56
        BankAlertParser.parseAmount("12,50") shouldBe 12.5
        BankAlertParser.parseAmount("5 000") shouldBe 5000.0
    }

    @Test
    fun `category hints match wording to a category name`() {
        val categories = listOf("Groceries" to 1, "Transport" to 2)
        CategoryHints.match("Achat chez Carrefour", categories) shouldBe 1
        CategoryHints.match("Uber trip 12 EUR", categories) shouldBe 2
        CategoryHints.match("Mystery payment", categories) shouldBe null
    }

    private val batch = ShareBatch(
        sender = "Ana",
        exportedAt = 1L,
        items = listOf(
            SharedTransaction("Lunch", 12.5, "EUR", false, "Food", 1_700_000_000_000L),
            SharedTransaction("Salary", 900.0, "EUR", true, "", 1_700_000_100_000L),
        )
    )

    @Test
    fun `share link round trips with the right code`() {
        val code = ShareCodec.generateCode()
        val link = ShareCodec.encode(batch, code)
        val decoded = ShareCodec.decode(link, code.lowercase())
        decoded.items shouldBe batch.items
    }

    @Test
    fun `share link refuses a wrong code`() {
        val link = ShareCodec.encode(batch, ShareCodec.generateCode())
        val error = assertThrows(ShareException::class.java) {
            ShareCodec.decode(link, "HM-AAAA-BBBB-CCCC")
        }
        error.reason shouldBe ShareException.Reason.BAD_CODE
    }

    @Test
    fun `qr payload carries link and code`() {
        val code = ShareCodec.generateCode()
        val link = ShareCodec.encode(batch, code)
        val parsed = ShareCodec.parseQrPayload(ShareCodec.qrPayload(link, code))!!
        parsed.second shouldBe link
        ShareCodec.normalizeCode(parsed.first) shouldBe ShareCodec.normalizeCode(code)
    }
}
