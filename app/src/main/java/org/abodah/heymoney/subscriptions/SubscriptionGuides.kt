package org.abodah.heymoney.subscriptions

import androidx.annotation.StringRes
import com.ivy.ui.R

/**
 * Cancellation help for one service: steps (a string resource, EN/FR) and,
 * when the service has a stable account page, its URL.
 */
data class CancellationGuide(
    @StringRes val stepsRes: Int,
    val url: String?,
)

private data class KnownService(
    val keywords: List<String>,
    @StringRes val stepsRes: Int,
    val url: String?,
)

/**
 * Static, in-app cancellation guides. Matching is by keywords in the
 * subscription name, most specific first. No network call, no automation on
 * third-party accounts: the user cancels in the service, HeyMoney only shows
 * the way. Anything unknown falls back to the generic steps in the UI.
 */
object CancellationGuides {

    private val knownServices = listOf(
        KnownService(
            keywords = listOf("google one"),
            stepsRes = R.string.subs_cancel_google_one,
            url = "https://one.google.com/settings"
        ),
        KnownService(
            keywords = listOf("netflix"),
            stepsRes = R.string.subs_cancel_netflix,
            url = "https://www.netflix.com/account"
        ),
        KnownService(
            keywords = listOf("spotify"),
            stepsRes = R.string.subs_cancel_spotify,
            url = "https://www.spotify.com/account"
        ),
        KnownService(
            keywords = listOf("youtube", "yt premium"),
            stepsRes = R.string.subs_cancel_youtube,
            url = "https://www.youtube.com/paid_memberships"
        ),
        KnownService(
            keywords = listOf("prime video", "amazon prime", "prime membership"),
            stepsRes = R.string.subs_cancel_amazon_prime,
            url = "https://www.amazon.com/gp/primecentral"
        ),
        KnownService(
            keywords = listOf("disney"),
            stepsRes = R.string.subs_cancel_disney,
            url = "https://www.disneyplus.com/account"
        ),
        KnownService(
            keywords = listOf("microsoft", "office 365", "xbox game pass"),
            stepsRes = R.string.subs_cancel_microsoft,
            url = "https://account.microsoft.com/services"
        ),
        KnownService(
            keywords = listOf("adobe", "creative cloud", "acrobat"),
            stepsRes = R.string.subs_cancel_adobe,
            url = "https://account.adobe.com/plans"
        ),
        KnownService(
            keywords = listOf("dropbox"),
            stepsRes = R.string.subs_cancel_dropbox,
            url = "https://www.dropbox.com/account/plan"
        ),
        KnownService(
            keywords = listOf("canal+"),
            stepsRes = R.string.subs_cancel_canal,
            url = "https://www.canalplus.com"
        ),
        KnownService(
            keywords = listOf("apple", "icloud", "app store", "apple music", "apple tv"),
            stepsRes = R.string.subs_cancel_apple,
            url = null
        ),
        KnownService(
            keywords = listOf("google play", "play store"),
            stepsRes = R.string.subs_cancel_google,
            url = "https://play.google.com/store/account/subscriptions"
        ),
        KnownService(
            keywords = listOf(
                "orange", "bell", "rogers", "telus", "videotron", "vidéotron",
                "sfr", "free mobile", "bouygues", "fido", "koodo", "virgin plus"
            ),
            stepsRes = R.string.subs_cancel_telecom,
            url = null
        ),
    )

    fun find(subscriptionName: String): CancellationGuide? {
        val normalized = normalizeMerchantName(subscriptionName)
        val match = knownServices.firstOrNull { service ->
            service.keywords.any { normalized.contains(it) }
        } ?: return null
        return CancellationGuide(stepsRes = match.stepsRes, url = match.url)
    }
}

/** A ready-to-copy negotiation message (email or phone script). */
data class NegotiationTemplate(
    @StringRes val titleRes: Int,
    @StringRes val bodyRes: Int,
)

object NegotiationTemplates {

    val all: List<NegotiationTemplate> = listOf(
        NegotiationTemplate(R.string.subs_template_email_polite_title, R.string.subs_template_email_polite),
        NegotiationTemplate(R.string.subs_template_email_firm_title, R.string.subs_template_email_firm),
        NegotiationTemplate(R.string.subs_template_phone_title, R.string.subs_template_phone),
    )

    /** Fills the {provider}, {price} and {tenure} placeholders of a template body. */
    fun fill(body: String, provider: String, price: String, tenure: String): String =
        body.replace("{provider}", provider)
            .replace("{price}", price)
            .replace("{tenure}", tenure)
}
