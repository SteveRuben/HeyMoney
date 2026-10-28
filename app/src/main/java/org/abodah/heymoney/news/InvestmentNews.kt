package org.abodah.heymoney.news

/** A news item that may influence investment decisions or relate to upcoming IPOs. */
data class InvestmentNews(
    val title: String,
    val summary: String,
    val source: String,
    val url: String,
    val publishedAt: Long,
    // category label: Markets, IPO, Economy
    val type: String,
)
