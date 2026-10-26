package com.invictus.warrior

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Invictus v0.1.4 — design tokens. Premium, martial, imperial. */
object Inv {
    val Background       = Color(0xFF240608)
    val DeepBackground   = Color(0xFF1A0405)
    val Card             = Color(0xFF2F2D36)
    val CardElevated     = Color(0xFF3A3743)
    val NavBackground    = Color(0xFF2A0A0C)
    val NavIndicator     = Color(0xFF3D1518)
    val Gold             = Color(0xFFD4B36A)
    val GoldMuted        = Color(0xFFB89249)
    val GoldHighlight    = Color(0xFFE3C98B)
    val TextPrimary      = Color(0xFFF3EEE7)
    val TextSecondary    = Color(0xFFD3C5B3)
    val TextTertiary     = Color(0xFFA89D93)
    val Border           = Color(0xFF4B4654)
    val GoldBorder       = Color(0xFF6A5530)
    val Error            = Color(0xFFCF6679)
    val Success          = Color(0xFF6AAF7C)

    val CardShape   = RoundedCornerShape(20.dp)
    val ButtonShape = RoundedCornerShape(18.dp)
    val ScreenPad   = 20.dp
    val CardGap     = 16.dp
    val CardPad     = 18.dp
}

/* ── Reusable components ─────────────────────────────────────── */

@Composable
fun InvCard(
    modifier: Modifier = Modifier,
    elevated: Boolean = false,
    goldBorder: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (elevated) Inv.CardElevated else Inv.Card
        ),
        shape = Inv.CardShape,
        border = BorderStroke(1.dp, if (goldBorder) Inv.GoldBorder else Inv.Border)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(Inv.CardPad),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

@Composable
fun InvButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = Inv.ButtonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Inv.Gold,
            contentColor = Inv.DeepBackground,
            disabledContainerColor = Inv.GoldMuted.copy(alpha = 0.38f),
            disabledContentColor = Inv.TextTertiary
        )
    ) { Text(text, fontWeight = FontWeight.SemiBold) }
}

@Composable
fun InvOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = Inv.ButtonShape,
        border = BorderStroke(1.dp, if (enabled) Inv.Gold else Inv.Border),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Inv.Gold)
    ) { Text(text, fontWeight = FontWeight.SemiBold) }
}

@Composable
fun CardLabel(text: String) {
    Text(
        text.uppercase(),
        color = Inv.Gold,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp
    )
}

@Composable
fun BigValue(text: String, modifier: Modifier = Modifier) {
    Text(text, color = Inv.TextPrimary, fontSize = 34.sp, fontWeight = FontWeight.Bold, modifier = modifier)
}

@Composable
fun ScreenTitle(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, color = Inv.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        if (subtitle != null) Text(subtitle, color = Inv.TextSecondary, fontSize = 14.sp)
    }
}

@Composable
fun InvField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    numeric: Boolean = false,
    decimal: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier,
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = when {
                decimal -> KeyboardType.Decimal
                numeric -> KeyboardType.Number
                else -> KeyboardType.Text
            }
        ),
        shape = Inv.CardShape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Inv.Gold,
            unfocusedBorderColor = Inv.Border,
            focusedLabelColor = Inv.Gold,
            unfocusedLabelColor = Inv.TextTertiary,
            cursorColor = Inv.Gold,
            focusedTextColor = Inv.TextPrimary,
            unfocusedTextColor = Inv.TextPrimary
        )
    )
}
