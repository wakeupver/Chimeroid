package com.swordfish.lemuroid.app.mobile.feature.home

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material.icons.rounded.ViewModule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.GradientEnd
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.GradientStart
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.LemuroidGameCard
import com.swordfish.lemuroid.app.utils.android.ComposableLifecycle
import com.swordfish.lemuroid.common.displayDetailsSettingsScreen
import com.swordfish.lemuroid.lib.library.db.entity.Game
import kotlinx.coroutines.launch

private val ScreenPadding = 16.dp

// ─── Public entry point ───────────────────────────────────────────────────────

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel,
    onGameClick: (Game) -> Unit,
    onGameLongClick: (Game) -> Unit,
    onOpenCoreSelection: () -> Unit,
) {
    val context            = LocalContext.current
    val applicationContext = context.applicationContext

    ComposableLifecycle { _, event ->
        if (event == Lifecycle.Event.ON_RESUME) viewModel.updatePermissions(applicationContext)
    }

    val permissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (!granted) context.displayDetailsSettingsScreen() }

    val state = viewModel.getViewStates().collectAsState(HomeViewModel.UIState())

    HomeScreenContent(
        modifier                    = modifier,
        state                       = state.value,
        onGameClicked               = onGameClick,
        onGameLongClick             = onGameLongClick,
        onOpenCoreSelection         = onOpenCoreSelection,
        onEnableNotificationsClicked = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                permissionsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        },
        onEnableMicrophoneClicked   = { permissionsLauncher.launch(Manifest.permission.RECORD_AUDIO) },
        onSetDirectoryClicked       = { viewModel.changeLocalStorageFolder(context) },
        onSelectStorageLocationClicked = { viewModel.selectStorageLocation(context) },
    )
}

// ─── Private content ──────────────────────────────────────────────────────────

@Composable
private fun HomeScreenContent(
    modifier: Modifier = Modifier,
    state: HomeViewModel.UIState,
    onGameClicked: (Game) -> Unit,
    onGameLongClick: (Game) -> Unit,
    onOpenCoreSelection: () -> Unit,
    onEnableNotificationsClicked: () -> Unit,
    onEnableMicrophoneClicked: () -> Unit,
    onSetDirectoryClicked: () -> Unit,
    onSelectStorageLocationClicked: () -> Unit,
) {
    var isListView    by remember { mutableStateOf(false) }
    var searchQuery   by remember { mutableStateOf("") }
    var showSortMenu  by remember { mutableStateOf(false) }
    val gridState     = rememberLazyGridState()
    val scope         = rememberCoroutineScope()
    val showScrollTop = gridState.firstVisibleItemIndex > 2

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        LazyVerticalGrid(
            columns             = if (isListView) GridCells.Fixed(1) else GridCells.Adaptive(102.dp),
            state               = gridState,
            modifier            = Modifier.fillMaxSize(),
            contentPadding      = PaddingValues(top = 4.dp, bottom = 80.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement   = Arrangement.spacedBy(10.dp),
        ) {
            // ── Header ──────────────────────────────────────────────────────
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                HomeHeader(
                    isListView     = isListView,
                    isRefreshing   = false,
                    onToggleView   = { isListView = !isListView },
                    onRefresh      = { /* viewModel.refresh */ },
                )
            }

            // ── Search bar ──────────────────────────────────────────────────
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                HomeSearchBar(
                    query         = searchQuery,
                    showSortMenu  = showSortMenu,
                    onQueryChange = { searchQuery = it },
                    onSortToggle  = { showSortMenu = it },
                )
            }

            // ── Notification cards ──────────────────────────────────────────
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AnimatedVisibility(state.showStorageLocationCard) {
                        HomeNotification(
                            titleId  = R.string.home_storage_location_title,
                            messageId = R.string.home_storage_location_message,
                            actionId  = R.string.home_storage_location_action,
                            onAction  = onSelectStorageLocationClicked,
                        )
                    }
                    AnimatedVisibility(state.showNoNotificationPermissionCard) {
                        HomeNotification(
                            titleId  = R.string.home_notification_title,
                            messageId = R.string.home_notification_message,
                            actionId  = R.string.home_notification_action,
                            onAction  = onEnableNotificationsClicked,
                        )
                    }
                    AnimatedVisibility(state.showNoGamesCard) {
                        HomeNotification(
                            titleId  = R.string.home_empty_title,
                            messageId = R.string.home_empty_message,
                            actionId  = R.string.home_empty_action,
                            onAction  = onSetDirectoryClicked,
                            enabled   = !state.indexInProgress,
                        )
                    }
                    AnimatedVisibility(state.showNoMicrophonePermissionCard) {
                        HomeNotification(
                            titleId  = R.string.home_microphone_title,
                            messageId = R.string.home_microphone_message,
                            actionId  = R.string.home_microphone_action,
                            onAction  = onEnableMicrophoneClicked,
                        )
                    }
                    AnimatedVisibility(state.showDesmumeDeprecatedCard) {
                        HomeNotification(
                            titleId  = R.string.home_notification_desmume_deprecated_title,
                            messageId = R.string.home_notification_desmume_deprecated_message,
                            actionId  = R.string.home_notification_desmume_deprecated_action,
                            onAction  = onOpenCoreSelection,
                        )
                    }
                }
            }

            // ── Recent games row ────────────────────────────────────────────
            if (state.recentGames.isNotEmpty()) {
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    HomeGameRow(
                        title        = stringResource(R.string.recent),
                        games        = state.recentGames,
                        onGameClick  = onGameClicked,
                        onGameLongClick = onGameLongClick,
                    )
                }
            }

            // ── Favorites row ───────────────────────────────────────────────
            if (state.favoritesGames.isNotEmpty()) {
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    HomeGameRow(
                        title        = stringResource(R.string.favorites),
                        games        = state.favoritesGames,
                        onGameClick  = onGameClicked,
                        onGameLongClick = onGameLongClick,
                    )
                }
            }

            // ── Discovery section label ─────────────────────────────────────
            if (state.discoveryGames.isNotEmpty()) {
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    HomeSectionLabel(
                        text    = stringResource(R.string.discover),
                        modifier = Modifier.padding(horizontal = ScreenPadding, vertical = 4.dp),
                    )
                }

                // ── Discovery grid items ────────────────────────────────────
                val filtered = state.discoveryGames.filter {
                    searchQuery.isBlank() || it.title.contains(searchQuery, ignoreCase = true)
                }
                items(filtered.size, key = { filtered[it].id }) { index ->
                    val game = filtered[index]
                    val cardModifier = if (isListView) {
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = ScreenPadding)
                    } else {
                        Modifier.widthIn(0.dp, 144.dp)
                    }
                    LemuroidGameCard(
                        modifier     = cardModifier,
                        game         = game,
                        onClick      = { onGameClicked(game) },
                        onLongClick  = { onGameLongClick(game) },
                    )
                }
            }

            // ── Empty state ─────────────────────────────────────────────────
            if (state.recentGames.isEmpty() && state.favoritesGames.isEmpty() &&
                state.discoveryGames.isEmpty() && !state.showNoGamesCard
            ) {
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    HomeEmptyGames()
                }
            }
        }

        // ── Scroll-to-top button ────────────────────────────────────────────
        AnimatedVisibility(
            visible  = showScrollTop,
            enter    = fadeIn(tween(180)) + scaleIn(tween(180)),
            exit     = fadeOut(tween(140)) + scaleOut(tween(140)),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 24.dp),
        ) {
            val shape = RoundedCornerShape(18.dp)
            Box(
                modifier = Modifier
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication        = null,
                    ) { scope.launch { gridState.animateScrollToItem(0) } }
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector        = Icons.Rounded.KeyboardArrowUp,
                    contentDescription = stringResource(R.string.back),
                    tint               = MaterialTheme.colorScheme.onSurface,
                    modifier           = Modifier.size(22.dp),
                )
            }
        }
    }
}

// ─── Header ───────────────────────────────────────────────────────────────────

@Composable
private fun HomeHeader(
    isListView: Boolean,
    isRefreshing: Boolean,
    onToggleView: () -> Unit,
    onRefresh: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = ScreenPadding, end = ScreenPadding, top = 16.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text     = stringResource(R.string.title_home),
                style    = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color    = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            IconButton(onClick = onRefresh, enabled = !isRefreshing) {
                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier    = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color       = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Icon(
                        imageVector        = Icons.Rounded.Refresh,
                        contentDescription = null,
                        tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onToggleView) {
                Icon(
                    imageVector        = if (isListView) Icons.Rounded.ViewModule else Icons.Rounded.ViewAgenda,
                    contentDescription = null,
                    tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ─── Search bar ───────────────────────────────────────────────────────────────

@Composable
private fun HomeSearchBar(
    query: String,
    showSortMenu: Boolean,
    onQueryChange: (String) -> Unit,
    onSortToggle: (Boolean) -> Unit,
) {
    OutlinedTextField(
        value         = query,
        onValueChange = onQueryChange,
        modifier      = Modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenPadding, vertical = 4.dp),
        placeholder   = {
            Text(
                text  = stringResource(R.string.title_search),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
        },
        leadingIcon   = {
            Icon(
                imageVector        = Icons.Rounded.Search,
                contentDescription = null,
                tint               = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
        },
        trailingIcon  = {
            Row {
                if (query.isNotBlank()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            imageVector        = Icons.Rounded.Close,
                            contentDescription = null,
                            tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Box {
                    IconButton(onClick = { onSortToggle(true) }) {
                        Icon(
                            imageVector        = Icons.AutoMirrored.Rounded.Sort,
                            contentDescription = null,
                            tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DropdownMenu(
                        expanded          = showSortMenu,
                        onDismissRequest  = { onSortToggle(false) },
                    ) {
                        DropdownMenuItem(
                            text    = { Text(stringResource(R.string.title_home)) },
                            onClick = { onSortToggle(false) },
                        )
                    }
                }
            }
        },
        singleLine = true,
        shape      = RoundedCornerShape(20.dp),
        colors     = OutlinedTextFieldDefaults.colors(
            focusedBorderColor   = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
            focusedContainerColor   = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
        ),
    )
}

// ─── Section label ────────────────────────────────────────────────────────────

@Composable
private fun HomeSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text     = text,
        style    = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        color    = MaterialTheme.colorScheme.onSurface,
        modifier = modifier,
    )
}

// ─── Horizontal game row (Recent / Favorites) ─────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeGameRow(
    title: String,
    games: List<Game>,
    onGameClick: (Game) -> Unit,
    onGameLongClick: (Game) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        HomeSectionLabel(
            text     = title,
            modifier = Modifier.padding(start = ScreenPadding, end = ScreenPadding, bottom = 6.dp),
        )
        LazyRow(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding        = PaddingValues(horizontal = ScreenPadding),
        ) {
            items(games.size, key = { games[it].id }) { index ->
                val game = games[index]
                LemuroidGameCard(
                    modifier    = Modifier
                        .width(108.dp)
                        .animateItem(),
                    game        = game,
                    onClick     = { onGameClick(game) },
                    onLongClick = { onGameLongClick(game) },
                )
            }
        }
    }
}

// ─── Notification card ────────────────────────────────────────────────────────

@Composable
private fun HomeNotification(
    titleId: Int,
    messageId: Int,
    actionId: Int,
    enabled: Boolean = true,
    onAction: () -> Unit = {},
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenPadding),
        shape    = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier  = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = stringResource(titleId), style = MaterialTheme.typography.titleMedium)
            Text(text = stringResource(messageId), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(
                modifier = Modifier.align(Alignment.End),
                onClick  = onAction,
                enabled  = enabled,
            ) { Text(stringResource(actionId)) }
        }
    }
}

// ─── Empty state ──────────────────────────────────────────────────────────────

@Composable
private fun HomeEmptyGames() {
    Column(
        modifier              = Modifier
            .fillMaxWidth()
            .height(360.dp)
            .padding(ScreenPadding),
        horizontalAlignment   = Alignment.CenterHorizontally,
        verticalArrangement   = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            GradientStart.copy(alpha = 0.15f),
                            GradientEnd.copy(alpha = 0.15f),
                        ),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector        = Icons.Rounded.SportsEsports,
                contentDescription = null,
                modifier           = Modifier.size(48.dp),
                tint               = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text      = stringResource(R.string.home_empty_title),
            style     = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color     = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text      = stringResource(R.string.home_empty_message),
            style     = MaterialTheme.typography.bodyMedium,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FilledTonalButton(onClick = {}, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.home_empty_action))
            }
        }
    }
}
