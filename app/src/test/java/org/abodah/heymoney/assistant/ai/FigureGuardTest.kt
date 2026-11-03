package org.abodah.heymoney.assistant.ai

import io.kotest.matchers.shouldBe
import org.junit.Test

class FigureGuardTest {

    @Test
    fun `extracts numbers across FR and EN formats`() {
        FigureGuard.extractNumbers("1,234.56") shouldBe setOf(1234.56)
        FigureGuard.extractNumbers("1 234,56") shouldBe setOf(1234.56)
        FigureGuard.extractNumbers("58.49") shouldBe setOf(58.49)
        FigureGuard.extractNumbers("about 13% more") shouldBe setOf(13.0)
        FigureGuard.extractNumbers("no numbers here") shouldBe emptySet()
    }

    @Test
    fun `an answer copying the facts is grounded`() {
        val allowed = FigureGuard.extractNumbers(
            "EXPENSES THIS MONTH: 1245.30 CAD. DAILY ALLOWANCE: 58.49 CAD, 30 days left."
        ) + (0..31).map { it.toDouble() }
        FigureGuard.isGrounded("You spent 1245.30 CAD this month.", allowed) shouldBe true
        FigureGuard.isGrounded("About 58.49 CAD per day.", allowed) shouldBe true
    }

    @Test
    fun `human rounding of a fact is still grounded`() {
        val allowed = setOf(58.49, 1245.30)
        FigureGuard.isGrounded("About 58 per day.", allowed) shouldBe true
        FigureGuard.isGrounded("About 58.5 per day.", allowed) shouldBe true
        FigureGuard.isGrounded("Roughly 1245 spent.", allowed) shouldBe true
    }

    @Test
    fun `an invented figure is rejected`() {
        val allowed = setOf(58.49, 1245.30, 30.0)
        FigureGuard.isGrounded("You spent 999.99 CAD.", allowed) shouldBe false
        FigureGuard.isGrounded("About 75 per day.", allowed) shouldBe false
        FigureGuard.isGrounded("Your balance is 5000 CAD.", allowed) shouldBe false
    }
}
