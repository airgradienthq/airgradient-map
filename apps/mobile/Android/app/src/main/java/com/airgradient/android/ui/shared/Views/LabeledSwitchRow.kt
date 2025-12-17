package com.airgradient.android.ui.shared.Views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun LabeledSwitchRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    labelSpacing: Dp = 16.dp,
    switchReserve: Dp = 56.dp,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    colors: SwitchColors = SwitchDefaults.colors(),
    label: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = labelSpacing + switchReserve)
                .align(verticalAlignment.toAlignmentLine()),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start
        ) {
            label()
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = colors,
            modifier = Modifier.align(Alignment.CenterEnd)
        )
    }
}

private fun Alignment.Vertical.toAlignmentLine(): Alignment = when (this) {
    Alignment.Top -> Alignment.TopStart
    Alignment.CenterVertically -> Alignment.CenterStart
    Alignment.Bottom -> Alignment.BottomStart
    else -> Alignment.CenterStart
}
