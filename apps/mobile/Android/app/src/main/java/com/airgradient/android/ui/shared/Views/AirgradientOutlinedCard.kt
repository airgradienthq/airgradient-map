package com.airgradient.android.ui.shared.Views

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun AirgradientOutlinedCard(
    modifier: Modifier = Modifier,
    cardModifier: Modifier = Modifier.fillMaxWidth(),
    header: (@Composable () -> Unit)? = null,
    headerPadding: PaddingValues = PaddingValues(start = 4.dp, top = 4.dp, bottom = 8.dp),
    shape: Shape = RoundedCornerShape(12.dp),
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant,
    borderWidth: Dp = 1.dp,
    content: @Composable () -> Unit
) {
    Column(modifier = modifier) {
        header?.let {
            Box(modifier = Modifier.padding(headerPadding)) {
                it()
            }
        }

        Card(
            modifier = cardModifier,
            shape = shape,
            colors = CardDefaults.outlinedCardColors(
                containerColor = containerColor,
                contentColor = contentColor,
                disabledContainerColor = containerColor,
                disabledContentColor = contentColor
            ),
            border = BorderStroke(width = borderWidth, color = borderColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            content()
        }
    }
}

