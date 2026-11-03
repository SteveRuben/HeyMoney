package org.abodah.heymoney.messages

import org.abodah.heymoney.subscriptions.SubscriptionFrequency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageSubscriptionMatcherTest {

    private val day = 86_400_000L
    private val base = 1_760_000_000_000L // fixed "recent" instant

    @Test
    fun `extracts amounts in the formats bills actually use`() {
        assertEquals(45.0, MessageSubscriptionMatcher.extractAmount("de 45,00 $")!!, 0.001)
        assertEquals(24.99, MessageSubscriptionMatcher.extractAmount("facture de 24,99€")!!, 0.001)
        assertEquals(15.99, MessageSubscriptionMatcher.extractAmount("payment of $15.99")!!, 0.001)
        assertEquals(10000.0, MessageSubscriptionMatcher.extractAmount("10 000 FCFA")!!, 0.001)
        assertEquals(1234.56, MessageSubscriptionMatcher.extractAmount("1,234.56 USD")!!, 0.001)
    }

    @Test
    fun `a monthly carrier bill is found with its rhythm`() {
        val messages = listOf(
            RawMessage("Bell", "Bell: votre facture de 62,50 $ est prête. Échéance le 12.", base),
            RawMessage("Bell", "Bell: votre facture de 62,50 $ est prête. Échéance le 12.", base - 30 * day),
            RawMessage("Bell", "Bell: votre facture de 64,00 $ est prête. Échéance le 12.", base - 61 * day),
        )
        val found = MessageSubscriptionMatcher.analyse(messages)
        assertEquals(1, found.size)
        val bell = found.single()
        assertEquals("Bell", bell.serviceName)
        assertEquals(3, bell.occurrences)
        assertEquals(SubscriptionFrequency.MONTHLY, bell.frequency)
        assertEquals(62.50, bell.amount!!, 0.001)
        assertEquals(base, bell.lastDateMillis)
    }

    @Test
    fun `an FR carrier bill in euros is found`() {
        val messages = listOf(
            RawMessage("Orange", "Votre facture Orange de 24,99 € est disponible.", base),
            RawMessage("Orange", "Votre facture Orange de 24,99 € est disponible.", base - 31 * day),
        )
        val found = MessageSubscriptionMatcher.analyse(messages)
        assertEquals(1, found.size)
        assertEquals("Orange", found.single().serviceName)
        assertEquals("EUR", found.single().currency)
        assertEquals(SubscriptionFrequency.MONTHLY, found.single().frequency)
    }

    @Test
    fun `a promo from a carrier is not a bill`() {
        val promo = RawMessage(
            "Orange",
            "Promo: -30% sur une sélection de téléphones ce week-end seulement!",
            base,
        )
        assertNull(MessageSubscriptionMatcher.chargeIn(promo))
    }

    @Test
    fun `a carrier message with an amount but no bill word is not counted`() {
        val topUp = RawMessage("MTN", "Recharge réussie: 1 000 FCFA ajoutés à votre compte.", base)
        // "recharge" is a bill word in the list, so this one IS a charge for
        // MTN — assert the opposite case with a neutral text:
        assertNotNull(MessageSubscriptionMatcher.chargeIn(topUp))
        val neutral = RawMessage("MTN", "Votre solde est de 2 500 FCFA. Merci.", base)
        assertNull(MessageSubscriptionMatcher.chargeIn(neutral))
    }

    @Test
    fun `a known service named in the text is found behind a generic sender`() {
        val message = RawMessage(
            "InfoSMS",
            "Netflix: votre abonnement de 15,99 € a été renouvelé. Merci.",
            base,
        )
        val charge = MessageSubscriptionMatcher.chargeIn(message)
        assertNotNull(charge)
        assertEquals("Netflix", charge!!.serviceName)
    }

    @Test
    fun `a brand-like sender with a subscription word is found generically`() {
        val message = RawMessage(
            "STREAMIO",
            "Your subscription payment of $9.99 was successful. Next renewal in 30 days.",
            base,
        )
        val charge = MessageSubscriptionMatcher.chargeIn(message)
        assertNotNull(charge)
        assertEquals("STREAMIO", charge!!.serviceName)
    }

    @Test
    fun `a plain phone number sender with no known service is ignored`() {
        val message = RawMessage(
            "+15145550123",
            "Ton abonnement de 20,00 $ est confirmé, à demain!",
            base,
        )
        assertNull(MessageSubscriptionMatcher.chargeIn(message))
    }

    @Test
    fun `irregular gaps give no frequency`() {
        val frequency = MessageSubscriptionMatcher.estimateFrequency(
            listOf(base, base - 3 * day, base - 90 * day, base - 95 * day)
        )
        assertNull(frequency)
    }

    @Test
    fun `weekly and yearly rhythms are recognised`() {
        assertEquals(
            SubscriptionFrequency.WEEKLY,
            MessageSubscriptionMatcher.estimateFrequency(
                listOf(base, base - 7 * day, base - 14 * day)
            )
        )
        assertEquals(
            SubscriptionFrequency.YEARLY,
            MessageSubscriptionMatcher.estimateFrequency(
                listOf(base, base - 365 * day)
            )
        )
    }

    @Test
    fun `a single found bill is listed without a frequency`() {
        val found = MessageSubscriptionMatcher.analyse(
            listOf(
                RawMessage("Rogers", "Rogers bill: $88.10 due on the 20th. Thank you.", base)
            )
        )
        assertEquals(1, found.size)
        assertNull(found.single().frequency)
        assertTrue(found.single().amount!! > 88.0)
    }

    @Test
    fun `an email style receipt resolves through the single message path`() {
        val receipt = MessageSubscriptionMatcher.chargeIn(
            RawMessage(
                sender = "Netflix",
                body = "Netflix Your subscription receipt: 15,99 € — payment successful",
                dateMillis = base,
            )
        )
        assertNotNull(receipt)
        assertEquals("Netflix", receipt!!.serviceName)
        assertEquals(15.99, receipt.amount!!, 0.001)
    }
}
