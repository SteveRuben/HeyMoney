package org.abodah.heymoney.esim

import androidx.annotation.StringRes

/**
 * Static, local eSIM preview catalogue. These are indicative preview prices
 * (in CAD) shown while no eSIM provider is connected — the feasibility note
 * (mockups-lemfi/faisabilite-esim.md) is explicit: real plans and prices will
 * come from the provider's API once Steve connects one, and real purchases
 * need the (parked) HeyMoney server for payment. Nothing here sells anything.
 */
data class EsimCountry(
    val code: String,
    @StringRes val nameRes: Int,
    /** Two-letter badge shown on the country chip, like the mockup. */
    val badge: String,
    /** ARGB colour of the badge circle on the country chip. */
    val badgeColor: Int,
    val plans: List<EsimPlan>
)

data class EsimPlan(
    val dataGb: Int,
    val days: Int,
    val network: String,
    val priceCad: Double,
    val popular: Boolean = false
)

object EsimCatalog {
    val countries: List<EsimCountry> = listOf(
        EsimCountry(
            code = "CM",
            nameRes = com.ivy.ui.R.string.esim_country_cm,
            badge = "CM",
            badgeColor = 0xFF1E9E4F.toInt(),
            plans = listOf(
                EsimPlan(dataGb = 1, days = 7, network = "MTN / Orange · 4G", priceCad = 4.50),
                EsimPlan(
                    dataGb = 3, days = 15, network = "MTN / Orange · 4G",
                    priceCad = 9.00, popular = true
                ),
                EsimPlan(dataGb = 5, days = 30, network = "MTN / Orange · 4G", priceCad = 13.50),
                EsimPlan(dataGb = 10, days = 30, network = "MTN / Orange · 4G", priceCad = 22.00)
            )
        ),
        EsimCountry(
            code = "CA",
            nameRes = com.ivy.ui.R.string.esim_country_ca,
            badge = "CA",
            badgeColor = 0xFFE03131.toInt(),
            plans = listOf(
                EsimPlan(dataGb = 1, days = 7, network = "Rogers / Bell · 5G", priceCad = 6.00),
                EsimPlan(
                    dataGb = 3, days = 15, network = "Rogers / Bell · 5G",
                    priceCad = 12.00, popular = true
                ),
                EsimPlan(dataGb = 5, days = 30, network = "Rogers / Bell · 5G", priceCad = 17.00),
                EsimPlan(dataGb = 10, days = 30, network = "Rogers / Bell · 5G", priceCad = 29.00)
            )
        ),
        EsimCountry(
            code = "FR",
            nameRes = com.ivy.ui.R.string.esim_country_fr,
            badge = "FR",
            badgeColor = 0xFF2B5EA7.toInt(),
            plans = listOf(
                EsimPlan(dataGb = 1, days = 7, network = "Orange / SFR · 5G", priceCad = 5.00),
                EsimPlan(
                    dataGb = 3, days = 15, network = "Orange / SFR · 5G",
                    priceCad = 10.50, popular = true
                ),
                EsimPlan(dataGb = 5, days = 30, network = "Orange / SFR · 5G", priceCad = 15.00),
                EsimPlan(dataGb = 10, days = 30, network = "Orange / SFR · 5G", priceCad = 25.00)
            )
        ),
        EsimCountry(
            code = "US",
            nameRes = com.ivy.ui.R.string.esim_country_us,
            badge = "US",
            badgeColor = 0xFF3B3B6D.toInt(),
            plans = listOf(
                EsimPlan(dataGb = 1, days = 7, network = "T-Mobile / AT&T · 5G", priceCad = 5.50),
                EsimPlan(
                    dataGb = 3, days = 15, network = "T-Mobile / AT&T · 5G",
                    priceCad = 11.00, popular = true
                ),
                EsimPlan(dataGb = 5, days = 30, network = "T-Mobile / AT&T · 5G", priceCad = 16.00),
                EsimPlan(dataGb = 10, days = 30, network = "T-Mobile / AT&T · 5G", priceCad = 27.00)
            )
        ),
        EsimCountry(
            code = "CI",
            nameRes = com.ivy.ui.R.string.esim_country_ci,
            badge = "CI",
            badgeColor = 0xFFF08C00.toInt(),
            plans = listOf(
                EsimPlan(dataGb = 1, days = 7, network = "Orange / MTN · 4G", priceCad = 4.00),
                EsimPlan(
                    dataGb = 3, days = 15, network = "Orange / MTN · 4G",
                    priceCad = 8.50, popular = true
                ),
                EsimPlan(dataGb = 5, days = 30, network = "Orange / MTN · 4G", priceCad = 12.50),
                EsimPlan(dataGb = 10, days = 30, network = "Orange / MTN · 4G", priceCad = 20.00)
            )
        )
    )
}
