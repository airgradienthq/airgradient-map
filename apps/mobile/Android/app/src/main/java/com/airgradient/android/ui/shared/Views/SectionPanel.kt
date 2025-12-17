package com.airgradient.android.ui.shared.Views

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.airgradient.android.R
import com.airgradient.android.ui.shared.Utils.openExternalUrl
import java.util.Locale

/**
 * Header configuration for reusable section panels.
 */
data class SectionHeader(
    val title: String,
    val subtitle: String? = null,
    val mascotAssetName: String? = null,
    val accentColor: String? = null
)

/**
 * Optional info action for section panels.
 */
data class InfoAction(
    val iconName: String,
    val accessibilityLabel: String,
    val actionUrl: String? = null,
    val actionKey: String? = null
)

/**
 * Wrapper configuration for a reusable section panel.
 */
data class SectionPanelConfig(
    val header: SectionHeader,
    val infoAction: InfoAction? = null
)

@Composable
fun SectionPanel(
    modifier: Modifier = Modifier,
    config: SectionPanelConfig,
    onInfoAction: ((InfoAction) -> Boolean)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val context = LocalContext.current
    val infoAction = config.infoAction?.takeIf { it.hasAction() }
    val shouldShowInfoAction = infoAction != null && (
        !infoAction.actionUrl.isNullOrBlank() ||
            (onInfoAction != null && !infoAction.actionKey.isNullOrBlank())
    )

    val infoActionHandler: ((InfoAction) -> Unit)? = if (shouldShowInfoAction && infoAction != null) handler@{ action ->
        if (onInfoAction?.invoke(action) == true) {
            return@handler
        }

        if (!action.actionUrl.isNullOrBlank()) {
            openExternalUrl(
                context = context,
                url = action.actionUrl,
                errorMessage = context.getString(R.string.section_panel_info_action_error)
            )
        }
    } else {
        null
    }

    AirgradientOutlinedCard(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            HeroPanelHeader(
                header = config.header,
                infoAction = if (shouldShowInfoAction) infoAction else null,
                onInfoActionClick = infoActionHandler
            )
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun HeroPanelHeader(
    header: SectionHeader,
    infoAction: InfoAction?,
    onInfoActionClick: ((InfoAction) -> Unit)?
) {
    val accentColor = resolveAccentColor(header.accentColor)
    val mascotPainter = rememberMascotPainter(header.mascotAssetName)

    if (infoAction != null && onInfoActionClick != null) {
        val iconPainter = rememberIconPainter(infoAction.iconName) ?: painterResource(id = R.drawable.ic_info)
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.TopEnd
        ) {
            IconButton(onClick = { onInfoActionClick(infoAction) }) {
                Icon(
                    painter = iconPainter,
                    contentDescription = infoAction.accessibilityLabel,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }

    if (mascotPainter != null) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(155.dp)
                .background(color = accentColor.copy(alpha = 0.5f), shape = CircleShape)
        ) {
            Image(
                painter = mascotPainter,
                contentDescription = null,
                modifier = Modifier.size(132.dp)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
    }

    Text(
        text = header.title,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurface
    )

    header.subtitle?.takeIf { it.isNotBlank() }?.let { subtitle ->
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun resolveAccentColor(accentColor: String?): Color {
    val theme = MaterialTheme.colorScheme
    val sanitized = accentColor?.trim()?.lowercase(Locale.ROOT)?.replace("-", "_") ?: return theme.primary

    val tokenColor = when (sanitized) {
        "accent", "primary" -> theme.primary
        "secondary" -> theme.secondary
        "tertiary" -> theme.tertiary
        "error", "danger" -> theme.error
        "surface" -> theme.surface
        "surface_variant" -> theme.surfaceVariant
        "inverse", "inverse_primary" -> theme.inversePrimary
        "info" -> theme.primary
        "warning" -> Color(0xFFFFA000)
        "success" -> Color(0xFF2E7D32)
        else -> null
    }

    if (tokenColor != null) {
        return tokenColor
    }

    return try {
        Color(android.graphics.Color.parseColor(accentColor))
    } catch (_: IllegalArgumentException) {
        theme.primary
    }
}

@Composable
private fun rememberMascotPainter(mascotAssetName: String?): Painter? {
    val sanitizedName = sanitizeResourceName(mascotAssetName) ?: return null
    val context = LocalContext.current
    val resourceId = remember(sanitizedName) {
        context.resources.getIdentifier(sanitizedName, "drawable", context.packageName)
    }

    if (resourceId == 0) return null

    return painterResource(id = resourceId)
}

@Composable
private fun rememberIconPainter(iconName: String): Painter? {
    val sanitizedName = sanitizeResourceName(iconName) ?: return null
    val context = LocalContext.current
    val resourceId = remember(sanitizedName) {
        context.resources.getIdentifier(sanitizedName, "drawable", context.packageName)
    }

    if (resourceId == 0) return null

    return painterResource(id = resourceId)
}

private fun sanitizeResourceName(rawName: String?): String? {
    if (rawName.isNullOrBlank()) return null
    val normalized = rawName
        .trim()
        .lowercase(Locale.ROOT)
        .replace("-", "_")
        .replace(" ", "_")
    val cleaned = normalized.replace(Regex("[^a-z0-9_]+"), "_").replace(Regex("_+"), "_").trim('_')
    return cleaned.takeIf { it.isNotEmpty() }
}

private fun InfoAction.hasAction(): Boolean {
    return !actionUrl.isNullOrBlank() || !actionKey.isNullOrBlank()
}
