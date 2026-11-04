package org.abodah.heymoney.esim

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivy.base.palettes.CardPaletteColors
import com.ivy.base.palettes.CardPalettes
import com.ivy.navigation.navigation
import com.ivy.navigation.screenScopedViewModel
import com.ivy.ui.R
import java.text.NumberFormat
import java.util.Currency

private val CardInk = Color(0xFF1D1B18)
private val CardBorder = Color(0xFF1A1A1A)
private val CardShape = RoundedCornerShape(28.dp)

/**
 * eSIM shop — PREVIEW only. The catalogue is static and local, and the buy
 * button is an honest "Coming soon": no provider is connected yet, so no
 * purchase, payment or activation QR code exists. See the feasibility note
 * in mockups-lemfi/faisabilite-esim.md.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EsimScreen(viewModel: EsimViewModel = screenScopedViewModel()) {
    val nav = navigation()
    val palette by viewModel.palette.collectAsState()

    var selectedCountry by remember { mutableStateOf(EsimCatalog.countries.first()) }
    var selectedPlanIndex by remember { mutableIntStateOf(1) }
    val plans = selectedCountry.plans
    val selectedPlan = plans.getOrNull(selectedPlanIndex) ?: plans.first()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.esim_title)) },
                navigationIcon = {
                    IconButton(onClick = { nav.back() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { Banner(palette = palette) }

            item {
                SectionLabel(stringResource(R.string.esim_country_label))
                Spacer(Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    EsimCatalog.countries.forEach { country ->
                        CountryChip(
                            country = country,
                            selected = country.code == selectedCountry.code
                        ) {
                            selectedCountry = country
                            selectedPlanIndex =
                                country.plans.indexOfFirst { it.popular }
                                    .takeIf { it >= 0 } ?: 0
                        }
                    }
                }
            }

            item {
                SectionLabel(
                    stringResource(
                        R.string.esim_plans_label,
                        stringResource(selectedCountry.nameRes)
                    )
                )
            }

            plans.forEachIndexed { index, plan ->
                item {
                    PlanCard(
                        plan = plan,
                        palette = palette,
                        selected = index == selectedPlanIndex
                    ) {
                        selectedPlanIndex = index
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Filled.QrCode2,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = CardInk.copy(alpha = 0.7f)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.esim_install_note),
                        style = MaterialTheme.typography.bodyMedium,
                        color = CardInk.copy(alpha = 0.75f)
                    )
                }
            }

            item {
                Button(
                    onClick = { /* no provider connected: purchase disabled on purpose */ },
                    enabled = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        disabledContainerColor = Color(0xFF141414),
                        disabledContentColor = Color.White
                    )
                ) {
                    Text(
                        text = "${stringResource(R.string.esim_coming_soon)} · " +
                            formatPriceCad(selectedPlan.priceCad),
                        style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    )
                }
                Spacer(Modifier.height(12.dp))
                PreviewBanner()
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun Banner(palette: CardPaletteColors) {
    val textColor = Color(palette.esimText)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(Color(palette.esimBackground), CardShape)
            .border(2.dp, CardBorder, CardShape)
            .padding(horizontal = 24.dp, vertical = 22.dp)
    ) {
        Text(
            text = stringResource(R.string.esim_banner_title),
            style = TextStyle(
                fontSize = 23.sp,
                fontWeight = FontWeight.ExtraBold,
                color = textColor
            )
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.esim_banner_sub),
            style = MaterialTheme.typography.bodyLarge,
            color = textColor.copy(alpha = 0.8f)
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = TextStyle(
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.1.sp,
            color = CardInk
        )
    )
}

@Composable
private fun CountryChip(
    country: EsimCountry,
    selected: Boolean,
    onClick: () -> Unit
) {
    val background = if (selected) Color(0xFF141414) else Color.White
    val foreground = if (selected) Color.White else CardInk
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(background, RoundedCornerShape(50))
            .border(1.5.dp, CardBorder, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(Color(country.badgeColor), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = country.badge,
                style = TextStyle(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = stringResource(country.nameRes),
            style = TextStyle(
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = foreground
            )
        )
    }
}

@Composable
private fun PlanCard(
    plan: EsimPlan,
    palette: CardPaletteColors,
    selected: Boolean,
    onClick: () -> Unit
) {
    val background = if (selected) {
        Color(CardPalettes.mixWithWhite(palette.esimBackground, 0.78f))
    } else {
        Color.White
    }
    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(background, RoundedCornerShape(24.dp))
                .border(
                    width = if (selected) 2.5.dp else 1.5.dp,
                    color = CardBorder,
                    shape = RoundedCornerShape(24.dp)
                )
                .clickable(onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.esim_gb, plan.dataGb.toString()),
                style = TextStyle(
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = CardInk
                )
            )
            Spacer(Modifier.width(18.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.esim_days, plan.days),
                    style = TextStyle(
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = CardInk
                    )
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = plan.network,
                    style = MaterialTheme.typography.bodyMedium,
                    color = CardInk.copy(alpha = 0.55f)
                )
            }
            Text(
                text = formatPriceCad(plan.priceCad),
                style = TextStyle(
                    fontSize = 21.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = CardInk
                )
            )
        }
        if (plan.popular) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 18.dp)
                    .offset(y = (-10).dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFF141414), RoundedCornerShape(50))
                    .padding(horizontal = 12.dp, vertical = 5.dp)
            ) {
                Text(
                    text = stringResource(R.string.esim_popular),
                    style = TextStyle(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        color = Color.White
                    )
                )
            }
        }
    }
}

@Composable
private fun PreviewBanner() {
    val dashColor = Color(0xFFD9A62E)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFFFF3C4), RoundedCornerShape(16.dp))
            .drawBehind {
                drawRoundRect(
                    color = dashColor,
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(10f, 8f)
                        )
                    ),
                    cornerRadius = CornerRadius(16.dp.toPx())
                )
            }
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Text(
            text = stringResource(R.string.esim_preview_banner),
            style = TextStyle(
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF6B5410)
            ),
            textAlign = TextAlign.Center
        )
    }
}

private fun formatPriceCad(amountCad: Double): String {
    val format = NumberFormat.getCurrencyInstance()
    format.currency = Currency.getInstance("CAD")
    return format.format(amountCad)
}
