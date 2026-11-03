package org.abodah.heymoney.assistant.ai

import io.kotest.matchers.shouldBe
import org.junit.Test

class WebQuerySanitizerTest {

    @Test
    fun `keeps a generic topic intact`() {
        WebQuerySanitizer.sanitize("EUR to CAD exchange rate today", emptyList()) shouldBe
            "EUR to CAD exchange rate today"
    }

    @Test
    fun `removes personal terms`() {
        val cleaned = WebQuerySanitizer.sanitize(
            "price of my Desjardins account and Food spending",
            personalTerms = listOf("Desjardins", "Food")
        )
        cleaned shouldBe "price of my account and spending"
    }

    @Test
    fun `drops amounts and dates but keeps years and currency codes`() {
        val cleaned = WebQuerySanitizer.sanitize(
            "convert 1245.30 EUR to CAD on 2026-11-03",
            personalTerms = emptyList()
        )
        // the amount and the day/month fragments are gone; the year and
        // the two currency codes (generic by nature) survive
        cleaned.contains("1245") shouldBe false
        cleaned.contains("EUR") shouldBe true
        cleaned.contains("CAD") shouldBe true
        cleaned.contains("2026") shouldBe true
    }

    @Test
    fun `a query made only of personal data becomes empty`() {
        WebQuerySanitizer.sanitize(
            "Desjardins 1234.56",
            personalTerms = listOf("Desjardins")
        ) shouldBe ""
    }

    @Test
    fun `finds currency codes in order`() {
        WebQuerySanitizer.currencyCodesIn("convert EUR to CAD please") shouldBe
            listOf("EUR", "CAD")
        WebQuerySanitizer.currencyCodesIn("weather tomorrow") shouldBe emptyList()
    }
}
