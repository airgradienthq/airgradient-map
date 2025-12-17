package com.airgradient.android.ui.search.Views

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.airgradient.android.R
import com.airgradient.android.data.local.RecentSearch
import com.airgradient.android.data.services.LocationType
import com.airgradient.android.data.services.SearchResult

@Composable
fun AirQualitySearchBar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    searchResults: List<SearchResult>,
    recentSearches: List<RecentSearch>,
    isSearching: Boolean,
    onLocationClick: (SearchResult) -> Unit,
    onRecentSearchClick: (RecentSearch) -> Unit,
    modifier: Modifier = Modifier
) {
    var showFullScreenSearch by remember { mutableStateOf(false) }

    // Compact search bar (when not focused)
    CompactSearchBar(
        onClick = { showFullScreenSearch = true },
        modifier = modifier
    )

    // Full-screen search dialog (renders on top of everything)
    if (showFullScreenSearch) {
        FullScreenSearchDialog(
            searchQuery = searchQuery,
            onSearchQueryChange = onSearchQueryChange,
            searchResults = searchResults,
            recentSearches = recentSearches,
            isSearching = isSearching,
            onLocationClick = { result ->
                onLocationClick(result)
                showFullScreenSearch = false
            },
            onRecentSearchClick = { recent ->
                onRecentSearchClick(recent)
                showFullScreenSearch = false
            },
            onDismiss = {
                showFullScreenSearch = false
                onSearchQueryChange("")
            }
        )
    }
}

@Composable
private fun CompactSearchBar(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(28.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = stringResource(R.string.cd_search_button),
                tint = Color(0xFF5F6368),
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Text(
                text = stringResource(R.string.search_placeholder),
                color = Color(0xFF5F6368),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )

            Image(
                painter = painterResource(id = R.drawable.mascot_book),
                contentDescription = stringResource(R.string.search_mascot_content_description),
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
private fun FullScreenSearchDialog(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    searchResults: List<SearchResult>,
    recentSearches: List<RecentSearch>,
    isSearching: Boolean,
    onLocationClick: (SearchResult) -> Unit,
    onRecentSearchClick: (RecentSearch) -> Unit,
    onDismiss: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // Use a full-screen dialog
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF202124))
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
            // Top search bar with back button
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF303134),
                tonalElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Back button
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back_button),
                            tint = Color(0xFFE8EAED)
                        )
                    }

                    // Search input
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = Color(0xFFE8EAED)
                        ),
                        decorationBox = { innerTextField ->
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.search_field_hint),
                                    color = Color(0xFF9AA0A6),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                            innerTextField()
                        }
                    )

                    // Voice search icon
                    IconButton(onClick = { /* Voice search */ }) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = stringResource(R.string.search_voice_search_content_description),
                            tint = Color(0xFF8AB4F8)
                        )
                    }
                }
            }

            // Search Results / Recent section
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                // Show results when searching or have results
                if (isSearching) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = Color(0xFF8AB4F8),
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                } else if (searchResults.isNotEmpty()) {
                    items(searchResults) { result ->
                        FullScreenSearchResultItem(
                            result = result,
                            onClick = { onLocationClick(result) }
                        )
                    }
                } else if (searchQuery.isNotEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.search_no_results_title),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF9AA0A6)
                            )
                        }
                    }
                } else {
                    // Show "Recent" section when no search
                    if (recentSearches.isNotEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.search_recent_section_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = Color(0xFFE8EAED),
                                modifier = Modifier.padding(16.dp)
                            )
                        }

                        items(recentSearches) { recentSearch ->
                            RecentSearchItem(
                                recentSearch = recentSearch,
                                onClick = { onRecentSearchClick(recentSearch) }
                            )
                        }
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun RecentSearchItem(
    recentSearch: RecentSearch,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon with circular background
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(Color(0xFF3C4043), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.History,
                contentDescription = null,
                tint = Color(0xFFE8EAED),
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = recentSearch.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Normal,
                color = Color(0xFFE8EAED)
            )

            recentSearch.subtitle?.let { subtitle ->
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF9AA0A6)
                )
            }
        }
    }
}

@Composable
private fun FullScreenSearchResultItem(
    result: SearchResult,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon with circular background
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(Color(0xFF3C4043), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = getIconForLocationType(result.type),
                contentDescription = null,
                tint = Color(0xFFE8EAED),
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = result.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Normal,
                color = Color(0xFFE8EAED)
            )

            result.subtitle?.let { subtitle ->
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF9AA0A6)
                )
            }
        }
    }
}

private fun getIconForLocationType(type: LocationType): ImageVector {
    return when (type) {
        LocationType.CITY -> Icons.Default.LocationCity
        LocationType.COUNTRY -> Icons.Default.Public
        LocationType.POI -> Icons.Default.LocationOn
        LocationType.SENSOR -> Icons.Default.Sensors
    }
}
