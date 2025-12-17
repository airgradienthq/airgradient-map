package com.airgradient.android.ui.locationdetail.Views

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.airgradient.android.data.models.OrganizationInfo
import com.airgradient.android.ui.shared.Views.AirgradientOutlinedCard
import com.airgradient.android.R
import com.airgradient.android.domain.models.FeaturedCommunityInfo

/**
 * Card displaying partner/organization attribution
 * Shows organization logo, name, and description
 */
@Composable
fun OrganizationAttributionCard(
    organization: OrganizationInfo,
    featuredCommunity: FeaturedCommunityInfo,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current
    
    AirgradientOutlinedCard(
        modifier = modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        containerColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            featuredCommunity.featuredImageUrl?.let { imageUrl ->
                AsyncImage(
                    model = imageUrl,
                    contentDescription = featuredCommunity.featuredImageAlt,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp)
                        .clip(RoundedCornerShape(16.dp))
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            Text(
                text = featuredCommunity.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            featuredCommunity.subtitle?.let { subtitle ->
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = featuredCommunity.content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            val learnMoreUrl = organization.websiteUrl?.takeIf { it.isNotBlank() }
                ?: featuredCommunity.externalUrl?.takeIf { it.isNotBlank() }

            learnMoreUrl?.let { url ->
                Spacer(modifier = Modifier.height(20.dp))
                TextButton(
                    onClick = { uriHandler.openUri(url) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.organization_learn_more, organization.name),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
