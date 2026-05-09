package com.swordfish.lemuroid.app.mobile.feature.home

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
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
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.GradientEnd
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.GradientMid
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.GradientStart
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.LemuroidGameCard
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.NeonCyan
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.NeonOrange
import com.swordfish.lemuroid.app.utils.android.ComposableLifecycle
import com.swordfish.lemuroid.common.displayDetailsSettingsScreen
import com.swordfish.lemuroid.lib.library.db.entity.Game
import kotlinx.coroutines.launch

private val Pad = 16.dp

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
        modifier                     = modifier,
        state                        = state.value,
        onGameClicked                = onGameClick,
        onGameLongClick              = onGameLongClick,
        onOpenCoreSelection          = onOpenCoreSelection,
        onEnableNotificationsClicked = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                permissionsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        },
        onEnableMicrophoneClicked    = { permissionsLauncher.launch(Manifest.permission.RECORD_AUDIO) },
        onSetDirectoryClicked        = { viewModel.changeLocalStorageFolder(context) },
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
    var isListView   by remember { mutableStateOf(false) }
    var searchQuery  by remember { mutableStateOf("") }
    var showSortMenu by remember { mutableStateOf(false) }
    val gridState    = rememberLazyGridState()
    val scope        = rememberCoroutineScope()
    val showScrollTop = gridState.firstVisibleItemIndex > 2

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        LazyVerticalGrid(
            columns               = if (isListView) GridCells.Fixed(1) else GridCells.Adaptive(104.dp),
            state                 = gridState,
            modifier              = Modifier.fillMaxSize(),
            contentPadding        = PaddingValues(top = 0.dp, bottom = 88.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement   = Arrangement.spacedBy(10.dp),
        ) {

            // ── Banner header ────────────────────────────────────────────────
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                HomeBanner(
                    isListView   = isListView,
                    isRefreshing = false,
                    onToggleView = { isListView = !isListView },
                    onRefresh    = { },
                )
            }

            // ── Search ───────────────────────────────────────────────────────
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                HomeSearchBar(
                    query         = searchQuery,
                    showSortMenu  = showSortMenu,
                    onQueryChange = { searchQuery = it },
                    onSortToggle  = { showSortMenu = it },
                )
            }

            // ── Notification cards ───────────────────────────────────────────
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AnimatedVisibility(state.showStorageLocationCard) {
                        HomeNotification(
                            titleId   = R.string.home_storage_location_title,
                            messageId = R.string.home_storage_location_message,
                            actionId  = R.string.home_storage_location_action,
                            onAction  = onSelectStorageLocationClicked,
                        )
                    }
                    AnimatedVisibility(state.showNoNotificationPermissionCard) {
                        HomeNotification(
                            titleId   = R.string.home_notification_title,
                            messageId = R.string.home_notification_message,
                            actionId  = R.string.home_notification_action,
                            onAction  = onEnableNotificationsClicked,
                        )
                    }
                    AnimatedVisibility(state.showNoGamesCard) {
                        HomeNotification(
                            titleId   = R.string.home_empty_title,
                            messageId = R.string.home_empty_message,
                            actionId  = R.string.home_empty_action,
                            onAction  = onSetDirectoryClicked,
                            enabled   = !state.indexInProgress,
                        )
                    }
                    AnimatedVisibility(state.showNoMicrophonePermissionCard) {
                        HomeNotification(
                            titleId   = R.string.home_microphone_title,
                            messageId = R.string.home_microphone_message,
                            actionId  = R.string.home_microphone_action,
                            onAction  = onEnableMicrophoneClicked,
                        )
                    }
                    AnimatedVisibility(state.showDesmumeDeprecatedCard) {
                        HomeNotification(
                            titleId   = R.string.home_notification_desmume_deprecated_title,
                            messageId = R.string.home_notification_desmume_deprecated_message,
                            actionId  = R.string.home_notification_desmume_deprecated_action,
                            onAction  = onOpenCoreSelection,
                        )
                    }
                }
            }

            // ── Recent games ─────────────────────────────────────────────────
            if (state.recentGames.isNotEmpty()) {
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    HomeGameRow(
                        title           = stringResource(R.string.recent),
                        games           = state.recentGames,
                        onGameClick     = onGameClicked,
                        onGameLongClick = onGameLongClick,
                    )
                }
            }

            // ── Favorites ────────────────────────────────────────────────────
            if (state.favoritesGames.isNotEmpty()) {
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    HomeGameRow(
                        title           = stringResource(R.string.favorites),
                        games           = state.favoritesGames,
                        onGameClick     = onGameClicked,
                        onGameLongClick = onGameLongClick,
                        accentColor     = GradientEnd,  // cyan for favorites
                    )
                }
            }

            // ── Discovery section ─────────────────────────────────────────────
            if (state.discoveryGames.isNotEmpty()) {
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    HomeSectionHeader(
                        text     = stringResource(R.string.discover),
                        modifier = Modifier.padding(start = Pad, end = Pad, top = 4.dp, bottom = 0.dp),
                    )
                }

                val filtered = state.discoveryGames.filter {
                    searchQuery.isBlank() || it.title.contains(searchQuery, ignoreCase = true)
                }
                items(filtered.size, key = { filtered[it].id }) { index ->
                    val game = filtered[index]
                    val cardModifier = if (isListView) {
                        Modifier.fillMaxWidth().padding(horizontal = Pad)
                    } else {
                        Modifier.widthIn(0.dp, 148.dp)
                    }
                    LemuroidGameCard(
                        modifier    = cardModifier,
                        game        = game,
                        onClick     = { onGameClicked(game) },
                        onLongClick = { onGameLongClick(game) },
                    )
                }
            }

            // ── Empty state ──────────────────────────────────────────────────
            if (state.recentGames.isEmpty() && state.favoritesGames.isEmpty() &&
                state.discoveryGames.isEmpty() && !state.showNoGamesCard
            ) {
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    HomeEmptyGames()
                }
            }
        }

        // ── Scroll-to-top FAB ─────────────────────────────────────────────────
        AnimatedVisibility(
            visible  = showScrollTop,
            enter    = fadeIn(tween(180)) + scaleIn(tween(180)),
            exit     = fadeOut(tween(140)) + scaleOut(tween(140)),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 28.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(listOf(GradientStart, GradientMid)),
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication        = null,
                    ) { scope.launch { gridState.animateScrollToItem(0) } },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector        = Icons.Rounded.KeyboardArrowUp,
                    contentDescription = stringResource(R.string.back),
                    tint               = Color.White,
                    modifier           = Modifier.size(22.dp),
                )
            }
        }
    }
}

// ─── Banner / Header ──────────────────────────────────────────────────────────

@Composable
private fun HomeBanner(
    isListView: Boolean,
    isRefreshing: Boolean,
    onToggleView: () -> Unit,
    onRefresh: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp)
            .background(
                Brush.verticalGradient(
                    colors        = listOf(
                        GradientStart.copy(alpha = 0.30f),
                        MaterialTheme.colorScheme.background,
                    ),
                ),
            )
            .padding(horizontal = Pad),
    ) {
        // Decorative orb top-right
        Box(
            modifier = Modifier
                .size(160.dp)
                .align(Alignment.TopEnd)
                .background(
                    Brush.radialGradient(
                        colors = listOf(GradientEnd.copy(alpha = 0.18f), Color.Transparent),
                    ),
                    CircleShape,
                ),
        )

        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(top = 24.dp),
        ) {
            Text(
                text  = "CHIMEROID",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight    = FontWeight.Black,
                    letterSpacing = 3.sp,
                ),
                color = GradientStart,
            )
            Text(
                text  = stringResource(R.string.title_home),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        // Icon row — top right
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            IconButton(onClick = onRefresh, enabled = !isRefreshing) {
                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier    = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color       = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Icon(
                        Icons.Rounded.Refresh, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
    val focused = query.isNotEmpty()
    val borderBrush = if (focused) {
        Brush.linearGradient(listOf(GradientStart, GradientEnd))
    } else {
        Brush.linearGradient(listOf(
            MaterialTheme.colorScheme.outline,
            MaterialTheme.colorScheme.outline,
        ))
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Pad, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(
                width = if (focused) 1.5.dp else 1.dp,
                brush = borderBrush,
                shape = RoundedCornerShape(14.dp),
            ),
    ) {
        TextField(
            value         = query,
            onValueChange = onQueryChange,
            modifier      = Modifier.fillMaxWidth(),
            placeholder   = {
                Text(
                    text  = stringResource(R.string.title_search),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                )
            },
            leadingIcon = {
                Icon(
                    Icons.Rounded.Search, null,
                    tint     = if (focused) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.size(20.dp),
                )
            },
            trailingIcon = {
                Row {
                    if (query.isNotBlank()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Rounded.Close, null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Box {
                        IconButton(onClick = { onSortToggle(true) }) {
                            Icon(Icons.AutoMirrored.Rounded.Sort, null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        DropdownMenu(expanded = showSortMenu, onDismissRequest = { onSortToggle(false) }) {
                            DropdownMenuItem(
                                text    = { Text(stringResource(R.string.title_home)) },
                                onClick = { onSortToggle(false) },
                            )
                        }
                    }
                }
            },
            singleLine = true,
            colors = TextFieldDefaults.colors(
                focusedContainerColor   = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedIndicatorColor   = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor  = Color.Transparent,
            ),
        )
    }
}

// ─── Section header with accent line ─────────────────────────────────────────

@Composable
private fun HomeSectionHeader(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier          = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(18.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Brush.verticalGradient(listOf(GradientStart, GradientEnd))),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text  = text.uppercase(),
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight    = FontWeight.ExtraBold,
                letterSpacing = 1.5.sp,
            ),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

// ─── Horizontal game row ──────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeGameRow(
    title: String,
    games: List<Game>,
    onGameClick: (Game) -> Unit,
    onGameLongClick: (Game) -> Unit,
    accentColor: Color = GradientStart,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier          = Modifier.padding(start = Pad, end = Pad, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(16.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accentColor),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text  = title.uppercase(),
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight    = FontWeight.ExtraBold,
                    letterSpacing = 1.5.sp,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        LazyRow(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding        = PaddingValues(horizontal = Pad),
        ) {
            items(games.size, key = { games[it].id }) { index ->
                val game = games[index]
                LemuroidGameCard(
                    modifier    = Modifier.width(112.dp).animateItem(),
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Pad)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(listOf(NeonOrange, NeonCyan)),
                shape = RoundedCornerShape(14.dp),
            )
            .padding(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text  = stringResource(titleId),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text  = stringResource(messageId),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (enabled) Brush.linearGradient(listOf(GradientStart, GradientMid))
                    else Brush.linearGradient(listOf(
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(0.3f),
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(0.3f),
                    )),
                )
                .clickable(enabled = enabled) { onAction() }
                .padding(horizontal = 14.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text  = stringResource(actionId),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
            )
        }
    }
}

// ─── Empty state ──────────────────────────────────────────────────────────────

@Composable
private fun HomeEmptyGames() {
    Column(
        modifier              = Modifier
            .fillMaxWidth()
            .height(380.dp)
            .padding(Pad),
        horizontalAlignment   = Alignment.CenterHorizontally,
        verticalArrangement   = Arrangement.Center,
    ) {
        // Gradient orb with icon
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(GradientStart.copy(0.20f), GradientEnd.copy(0.20f)),
                    ),
                )
                .border(
                    width = 1.5.dp,
                    brush = Brush.linearGradient(listOf(GradientStart, GradientEnd)),
                    shape = CircleShape,
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
        Spacer(Modifier.height(24.dp))
        Text(
            text      = stringResource(R.string.home_empty_title),
            style     = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
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
        Spacer(Modifier.height(28.dp))
        // Gradient CTA button
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(Brush.linearGradient(listOf(GradientStart, GradientMid, GradientEnd)))
                .clickable { }
                .padding(horizontal = 28.dp, vertical = 13.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment      = Alignment.CenterVertically,
                horizontalArrangement  = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Rounded.FolderOpen, null,
                    tint     = Color.White,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text  = stringResource(R.string.home_empty_action),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                )
            }
        }
    }
}


