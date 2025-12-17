package com.airgradient.android.ui.shared.Views

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.TopAppBarDefaults.windowInsets as topAppBarInsets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgTopBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    centerTitle: Boolean = false,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(),
    scrollBehavior: TopAppBarScrollBehavior? = null,
    windowInsets: androidx.compose.foundation.layout.WindowInsets = topAppBarInsets
) {
    val titleContent: @Composable () -> Unit = {
        ProvideTextStyle(value = MaterialTheme.typography.titleLarge) {
            title()
        }
    }

    val navigationSlot = navigationIcon ?: {}

    if (centerTitle) {
        CenterAlignedTopAppBar(
            modifier = modifier,
            title = titleContent,
            navigationIcon = navigationSlot,
            actions = actions,
            colors = colors,
            scrollBehavior = scrollBehavior,
            windowInsets = windowInsets
        )
    } else {
        TopAppBar(
            modifier = modifier,
            title = titleContent,
            navigationIcon = navigationSlot,
            actions = actions,
            colors = colors,
            scrollBehavior = scrollBehavior,
            windowInsets = windowInsets
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgTopBar(
    title: String,
    modifier: Modifier = Modifier,
    centerTitle: Boolean = false,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(),
    scrollBehavior: TopAppBarScrollBehavior? = null,
    windowInsets: androidx.compose.foundation.layout.WindowInsets = topAppBarInsets
) {
    AgTopBar(
        title = {
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        modifier = modifier,
        centerTitle = centerTitle,
        navigationIcon = navigationIcon,
        actions = actions,
        colors = colors,
        scrollBehavior = scrollBehavior,
        windowInsets = windowInsets
    )
}

@Composable
fun AgBottomSheetTopBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    contentPadding: Dp = 16.dp,
    height: Dp = 56.dp
) {
    ProvideTextStyle(value = MaterialTheme.typography.titleLarge) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(height)
                .padding(horizontal = contentPadding),
            contentAlignment = Alignment.Center
        ) {
            navigationIcon?.let {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .size(height),
                    contentAlignment = Alignment.Center
                ) {
                    it()
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                title()
            }

            Row(
                modifier = Modifier.align(Alignment.CenterEnd),
                verticalAlignment = Alignment.CenterVertically,
                content = actions
            )
        }
    }
}

@Composable
fun AgBottomSheetTopBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    contentPadding: Dp = 16.dp,
    height: Dp = 56.dp
) {
    AgBottomSheetTopBar(
        title = {
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = actions,
        contentPadding = contentPadding,
        height = height
    )
}
