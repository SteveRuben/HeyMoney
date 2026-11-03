package org.abodah.heymoney.assistant.ai

import io.kotest.matchers.shouldBe
import org.junit.Test

class IntentParserTest {

    @Test
    fun `parses a clean intent`() {
        val parsed = IntentParser.parse(
            """{"intent":"spending","category":null,"web_query":null}"""
        )
        parsed?.intent shouldBe AssistantIntent.SPENDING
        parsed?.category shouldBe null
        parsed?.webQuery shouldBe null
    }

    @Test
    fun `parses category and web query`() {
        val parsed = IntentParser.parse(
            """{"intent":"category_spending","category":"Food","web_query":null}"""
        )
        parsed?.intent shouldBe AssistantIntent.CATEGORY_SPENDING
        parsed?.category shouldBe "Food"

        val web = IntentParser.parse(
            """{"intent":"web_info","category":null,"web_query":"EUR to CAD exchange rate"}"""
        )
        web?.intent shouldBe AssistantIntent.WEB_INFO
        web?.webQuery shouldBe "EUR to CAD exchange rate"
    }

    @Test
    fun `tolerates prose around the JSON`() {
        val parsed = IntentParser.parse(
            "Sure! Here is the classification: {\"intent\":\"balance\"} hope that helps"
        )
        parsed?.intent shouldBe AssistantIntent.BALANCE
    }

    @Test
    fun `rejects unknown intents and broken output`() {
        IntentParser.parse("""{"intent":"teleport"}""") shouldBe null
        IntentParser.parse("I do not know") shouldBe null
        IntentParser.parse("") shouldBe null
        IntentParser.parse("""{"category":"Food"}""") shouldBe null
    }
}
