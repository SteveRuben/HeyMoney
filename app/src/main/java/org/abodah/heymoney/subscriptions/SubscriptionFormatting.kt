package org.abodah.heymoney.subscriptions

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ivy.ui.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** "15.99 CAD" — same plain style as the bank-alert cards. */
fun formatMoney(amount: Double, currency: String?): String =
    "%,.2f %s".format(Locale.getDefault(), amount, currency.orEmpty()).trim()

@Composable
fun frequencyLabel(frequency: SubscriptionFrequency): String = stringResource(
    when (frequency) {
        SubscriptionFrequency.WEEKLY -> R.string.subs_freq_weekly
        SubscriptionFrequency.BIWEEKLY -> R.string.subs_freq_biweekly
        SubscriptionFrequency.MONTHLY -> R.string.subs_freq_monthly
        SubscriptionFrequency.QUARTERLY -> R.string.subs_freq_quarterly
        SubscriptionFrequency.SEMIANNUAL -> R.string.subs_freq_semiannual
        SubscriptionFrequency.YEARLY -> R.string.subs_freq_yearly
    }
)

fun formatDate(date: LocalDate): String = date.format(
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault())
)

/** Localized "customer for …" phrase for the negotiation templates. */
@Composable
fun tenureText(firstChargeDate: LocalDate?): String {
    val months = tenureMonths(firstChargeDate) ?: return stringResource(R.string.subs_tenure_default)
    return when {
        months >= 24 -> stringResource(R.string.subs_tenure_years, (months / 12).toInt())
        months >= 12 -> stringResource(R.string.subs_tenure_one_year)
        else -> stringResource(R.string.subs_tenure_months, months.toInt())
    }
}
