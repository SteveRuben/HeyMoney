package org.abodah.heymoney.palettes

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivy.base.palettes.CardPaletteColors
import com.ivy.base.palettes.CardPalettes
import com.ivy.base.palettes.PaletteRole
import com.ivy.navigation.navigation
import com.ivy.navigation.screenScopedViewModel
import com.ivy.ui.R

private val CardInk = Color(0xFF1D1B18)
private val CardBorder = Color(0xFF1A1A1A)

/**
 * "Palettes de couleurs" (Steve, 3 Nov 2026): pick a preset palette for the
 * stacked cards, or customise each card role (total / accounts / eSIM) with
 * swatches. The mini stack at the top previews every choice live; the store
 * applies it instantly to the Accounts tab and the eSIM screens.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardPalettesScreen(viewModel: CardPalettesViewModel = screenScopedViewModel()) {
    val nav = navigation()
    val selection by viewModel.selection.collectAsState()
    val colors by viewModel.colors.collectAsState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.palettes_title)) },
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SectionTitle(stringResource(R.string.palettes_preview))
                Spacer(Modifier.height(8.dp))
                MiniStack(colors = colors)
            }

            item {
                Spacer(Modifier.height(8.dp))
                SectionTitle(stringResource(R.string.palettes_presets))
            }

            CardPalettes.PRESET_IDS.forEach { presetId ->
                item {
                    PresetRow(
                        presetId = presetId,
                        selected = !selection.isCustom && selection.presetId == presetId
                    ) {
                        viewModel.selectPreset(presetId)
                    }
                }
            }

            if (selection.isCustom) {
                item {
                    PresetRow(
                        presetId = CardPalettes.PRESET_CUSTOM,
                        selected = true,
                        colors = colors
                    ) { /* already active */ }
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                SectionTitle(stringResource(R.string.palettes_customise))
            }

            PaletteRole.entries.forEach { role ->
                item {
                    RoleSwatches(
                        role = role,
                        currentColor = viewModel.roleColor(selection, role),
                        onPick = { viewModel.pickColor(role, it) }
                    )
                }
            }

            item {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.palettes_applied_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { viewModel.reset() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.palettes_reset))
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = TextStyle(
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp,
            color = CardInk
        )
    )
}

/** Miniature live preview of the stacked cards with the current palette. */
@Composable
private fun MiniStack(colors: CardPaletteColors) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Total balance card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(colors.totalGradientStart),
                            Color(colors.totalGradientEnd)
                        )
                    ),
                    shape = RoundedCornerShape(20.dp)
                )
                .border(2.dp, CardBorder, RoundedCornerShape(20.dp))
                .padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            Text(
                text = stringResource(R.string.accounts_total_label),
                style = TextStyle(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = Color(colors.totalText).copy(alpha = 0.85f)
                )
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "12 480,50 $",
                style = TextStyle(
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(colors.totalText)
                )
            )
        }
        // Two account cards
        listOf(0, 1).forEach { index ->
            val tint = colors.accountTints.getOrElse(index) { colors.totalGradientEnd }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(tint), RoundedCornerShape(20.dp))
                    .border(2.dp, CardBorder, RoundedCornerShape(20.dp))
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color.White, CircleShape)
                        .border(1.5.dp, CardBorder, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (index == 0) "D" else "T",
                        style = TextStyle(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = CardInk
                        )
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.palettes_sample_account),
                    style = TextStyle(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = CardInk
                    ),
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = if (index == 0) "8 240,10 $" else "2 960,40 $",
                    style = TextStyle(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = CardInk
                    )
                )
            }
        }
        // eSIM card
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color(colors.esimBackground), RoundedCornerShape(20.dp))
                .border(2.dp, CardBorder, RoundedCornerShape(20.dp))
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.esim_title),
                style = TextStyle(
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(colors.esimText)
                ),
                modifier = Modifier.weight(1f)
            )
            Text(
                text = stringResource(R.string.esim_stay_connected),
                style = TextStyle(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(colors.esimText)
                )
            )
        }
    }
}

@Composable
private fun PresetRow(
    presetId: String,
    selected: Boolean,
    colors: CardPaletteColors? = null,
    onClick: () -> Unit
) {
    val presetColors = colors ?: CardPalettes.preset(presetId)
    val nameRes = when (presetId) {
        CardPalettes.PRESET_CORAL -> R.string.palettes_preset_coral
        CardPalettes.PRESET_GREEN -> R.string.palettes_preset_green
        CardPalettes.PRESET_VIOLET -> R.string.palettes_preset_violet
        CardPalettes.PRESET_SAND -> R.string.palettes_preset_sand
        CardPalettes.PRESET_CUSTOM -> R.string.palettes_preset_custom
        else -> R.string.palettes_preset_blue
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(nameRes),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        ColorDot(presetColors.totalGradientEnd)
        Spacer(Modifier.width(6.dp))
        ColorDot(
            presetColors.accountTints.getOrElse(1) { presetColors.totalGradientStart }
        )
        Spacer(Modifier.width(6.dp))
        ColorDot(presetColors.esimBackground)
        if (selected) {
            Spacer(Modifier.width(10.dp))
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun ColorDot(argb: Int) {
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(Color(argb), CircleShape)
            .border(1.5.dp, CardBorder, CircleShape)
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RoleSwatches(
    role: PaletteRole,
    currentColor: Int,
    onPick: (Int) -> Unit
) {
    val labelRes = when (role) {
        PaletteRole.TOTAL -> R.string.palettes_role_total
        PaletteRole.ACCOUNTS -> R.string.palettes_role_accounts
        PaletteRole.ESIM -> R.string.palettes_role_esim
    }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(labelRes),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            ColorDot(currentColor)
        }
        Spacer(Modifier.height(10.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CardPalettes.SWATCHES.forEach { swatch ->
                val isCurrent = swatch == currentColor
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color(swatch), CircleShape)
                        .border(
                            width = if (isCurrent) 3.dp else 1.5.dp,
                            color = if (isCurrent) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                CardBorder
                            },
                            shape = CircleShape
                        )
                        .clickable { onPick(swatch) },
                    contentAlignment = Alignment.Center
                ) {
                    if (isCurrent) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = Color(CardPalettes.readableText(swatch)),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
