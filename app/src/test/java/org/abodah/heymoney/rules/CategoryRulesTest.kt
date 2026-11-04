package org.abodah.heymoney.rules

import com.ivy.data.rules.CategoryRule
import com.ivy.data.rules.CategoryRules
import io.kotest.matchers.shouldBe
import org.junit.Test
import java.util.UUID

class CategoryRulesTest {

    private val entertainment: UUID = UUID.randomUUID()
    private val groceries: UUID = UUID.randomUUID()
    private val telecom: UUID = UUID.randomUUID()

    private val rules = listOf(
        CategoryRule("netflix", entertainment),
        CategoryRule("carrefour", groceries),
        CategoryRule("orange", telecom),
    )

    @Test
    fun `matches a keyword inside the wording, case-insensitive`() {
        CategoryRules.match(rules, "NETFLIX.COM payment") shouldBe entertainment
        CategoryRules.match(rules, "Achat", "chez Carrefour Market") shouldBe groceries
    }

    @Test
    fun `first matching rule wins`() {
        val ordered = listOf(
            CategoryRule("orange", telecom),
            CategoryRule("orange money", entertainment),
        )
        CategoryRules.match(ordered, "Orange Money transfer") shouldBe telecom
    }

    @Test
    fun `no match returns null`() {
        CategoryRules.match(rules, "Boulangerie du coin") shouldBe null
        CategoryRules.match(rules, null, null) shouldBe null
        CategoryRules.match(emptyList(), "Netflix") shouldBe null
    }

    @Test
    fun `blank keywords never match`() {
        val withBlank = listOf(CategoryRule("  ", entertainment))
        CategoryRules.match(withBlank, "anything at all") shouldBe null
    }
}
