package com.swordfish.lemuroid.app.mobile.feature.home

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.GradientEnd
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.GradientStart
import com.swordfish.lemuroid.app.shared.covers.CoverUtils
import com.swordfish.lemuroid.app.utils.android.ComposableLifecycle
import com.swordfish.lemuroid.app.utils.games.GameUtils
import com.swordfish.lemuroid.common.displayDetailsSettingsScreen
import com.swordfish.lemuroid.lib.library.db.entity.Game
import kotlinx.coroutines.launch

private val ScreenPadding = 16.dp
private val systemChips = listOf(
    "All", "PSP", "Dreamcast", "PSX", "GBA", "NDS", "N64",
    "SNES", "NES", "Genesis", "3DS", "PC Engine", "Atari",
)

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
        modifier                       = modifier,
        state                          = state.value,
        onGameClicked                  = onGameClick,
        onGameLongClick                = onGameLongClick,
        onOpenCoreSelection            = onOpenCoreSelection,
        onEnableNotificationsClicked   = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                permissionsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        },
        onEnableMicrophoneClicked      = { permissionsLauncher.launch(Manifest.permission.RECORD_AUDIO) },
        onSetDirectoryClicked          = { viewModel.changeLocalStorageFolder(context) },
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
    var selectedChip  by remember { mutableIntStateOf(0) }
    val listState     = rememberLazyListState()
    val scope         = rememberCoroutineScope()
    val showScrollTop = listState.firstVisibleItemIndex > 2

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        LazyColumn(
            state          = listState,
            modifier       = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            // Genre / mood chips
            item {
                GenreChipsRow(
                    chips          = systemChips,
                    selectedIndex  = selectedChip,
                    onChipSelected = { selectedChip = it },
                )
            }

            // Notification banners
            item {
                Column(
                    modifier            = Modifier.padding(horizontal = ScreenPadding),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AnimatedVisibility(state.showStorageLocationCard) {
                        HomeNotification(R.string.home_storage_location_title, R.string.home_storage_location_message,
                            R.string.home_storage_location_action, onAction = onSelectStorageLocationClicked)
                    }
                    AnimatedVisibility(state.showNoNotificationPermissionCard) {
                        HomeNotification(R.string.home_notification_title, R.string.home_notification_message,
                            R.string.home_notification_action, onAction = onEnableNotificationsClicked)
                    }
                    AnimatedVisibility(state.showNoGamesCard) {
                        HomeNotification(R.string.home_empty_title, R.string.home_empty_message,
                            R.string.home_empty_action, enabled = !state.indexInProgress, onAction = onSetDirectoryClicked)
                    }
                    AnimatedVisibility(state.showNoMicrophonePermissionCard) {
                        HomeNotification(R.string.home_microphone_title, R.string.home_microphone_message,
                            R.string.home_microphone_action, onAction = onEnableMicrophoneClicked)
                    }
                    AnimatedVisibility(state.showDesmumeDeprecatedCard) {
                        HomeNotification(R.string.home_notification_desmume_deprecated_title,
                            R.string.home_notification_desmume_deprecated_message,
                            R.string.home_notification_desmume_deprecated_action, onAction = onOpenCoreSelection)
                    }
                }
            }

            // Quick picks — recent games
            if (state.recentGames.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    QuickPicksSection(
                        games       = state.recentGames,
                        onGameClick = onGameClicked,
                        onLongClick = onGameLongClick,
                    )
                }
            }

            // Speed dial — favorites
            if (state.favoritesGames.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(16.dp))
                    SpeedDialSection(
                        games       = state.favoritesGames,
                        onGameClick = onGameClicked,
                        onLongClick = onGameLongClick,
                    )
                }
            }

            // Discover grid
            if (state.discoveryGames.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(16.dp))
                    DiscoverSection(
                        games       = state.discoveryGames,
                        onGameClick = onGameClicked,
                        onLongClick = onGameLongClick,
                    )
                }
            }

            // Empty state
            if (state.recentGames.isEmpty() && state.favoritesGames.isEmpty() &&
                state.discoveryGames.isEmpty() && !state.showNoGamesCard
            ) {
                item { HomeEmptyGames() }
            }
        }

        // Scroll-to-top button
        AnimatedVisibility(
            visible  = showScrollTop,
            enter    = fadeIn(tween(180)) + scaleIn(tween(180)),
            exit     = fadeOut(tween(140)) + scaleOut(tween(140)),
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 24.dp),
        ) {
            Surface(
                shape           = RoundedCornerShape(18.dp),
                color           = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                tonalElevation  = 4.dp,
                shadowElevation = 6.dp,
                modifier        = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication        = null,
                ) { scope.launch { listState.animateScrollToItem(0) } },
            ) {
                Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

// ─── Genre chips ──────────────────────────────────────────────────────────────

@Composable
private fun GenreChipsRow(chips: List<String>, selectedIndex: Int, onChipSelected: (Int) -> Unit) {
    LazyRow(
        modifier              = Modifier.fillMaxWidth(),
        contentPadding        = PaddingValues(horizontal = ScreenPadding, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(chips.size) { i ->
            val selected = i == selectedIndex
            FilterChip(
                selected = selected,
                onClick  = { onChipSelected(i) },
                label    = {
                    Text(chips[i], style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal))
                },
                shape  = RoundedCornerShape(50),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.onSurface,
                    selectedLabelColor     = MaterialTheme.colorScheme.surface,
                    containerColor         = MaterialTheme.colorScheme.surface,
                    labelColor             = MaterialTheme.colorScheme.onSurface,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled             = true,
                    selected            = selected,
                    borderColor         = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    selectedBorderColor = MaterialTheme.colorScheme.onSurface,
                    borderWidth         = 1.dp,
                    selectedBorderWidth = 1.dp,
                ),
            )
        }
    }
}

// ─── Quick picks ──────────────────────────────────────────────────────────────

@Composable
private fun QuickPicksSection(games: List<Game>, onGameClick: (Game) -> Unit, onLongClick: (Game) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier              = Modifier.fillMaxWidth().padding(horizontal = ScreenPadding, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.recent),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground)
            Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp, onClick = {}) {
                Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Rounded.PlayArrow, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(16.dp))
                    Text("Play all", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            games.take(6).forEach { game ->
                QuickPickRow(game = game, onClick = { onGameClick(game) }, onMoreClick = { onLongClick(game) })
            }
        }
    }
}

@Composable
private fun QuickPickRow(game: Game, onClick: () -> Unit, onMoreClick: () -> Unit) {
    val context         = LocalContext.current
    val fallbackPainter = rememberDrawablePainter(remember(game) { CoverUtils.getFallbackDrawable(game) })
    val subtitle        = remember(game.id) { GameUtils.getGameSubtitle(context, game) }

    Row(
        modifier          = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = ScreenPadding, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(52.dp).clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)) {
            AsyncImage(model = ImageRequest.Builder(context).data(game.coverFrontUrl).build(),
                contentDescription = game.title, modifier = Modifier.fillMaxSize(),
                fallback = fallbackPainter, error = fallbackPainter, contentScale = ContentScale.Crop)
        }
        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(game.title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank())
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onMoreClick, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Rounded.MoreVert, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    }
}

// ─── Speed dial ───────────────────────────────────────────────────────────────

@Composable
private fun SpeedDialSection(games: List<Game>, onGameClick: (Game) -> Unit, onLongClick: (Game) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenPadding, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.SportsEsports, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("YOUR LIBRARY", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.favorites), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground)
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }

        val cols  = 3
        val shown = games.take(6)
        val rows  = (shown.size + cols - 1) / cols
        Column(modifier = Modifier.padding(horizontal = ScreenPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (row in 0 until rows) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (col in 0 until cols) {
                        val game = shown.getOrNull(row * cols + col)
                        if (game != null) {
                            SpeedDialCard(game = game, modifier = Modifier.weight(1f),
                                onClick = { onGameClick(game) }, onLongClick = { onLongClick(game) })
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpeedDialCard(game: Game, modifier: Modifier, onClick: () -> Unit, onLongClick: () -> Unit) {
    val context         = LocalContext.current
    val fallbackPainter = rememberDrawablePainter(remember(game) { CoverUtils.getFallbackDrawable(game) })
    Surface(modifier = modifier.clip(RoundedCornerShape(10.dp)), shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant, onClick = onClick) {
        Box(modifier = Modifier.aspectRatio(1f)) {
            AsyncImage(model = ImageRequest.Builder(context).data(game.coverFrontUrl).build(),
                contentDescription = game.title, modifier = Modifier.fillMaxSize(),
                fallback = fallbackPainter, error = fallbackPainter, contentScale = ContentScale.Crop)
            Box(modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(colors = listOf(
                    MaterialTheme.colorScheme.background.copy(alpha = 0f),
                    MaterialTheme.colorScheme.background.copy(alpha = 0.75f)), startY = 80f)))
            Text(game.title, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).padding(6.dp))
        }
    }
}

// ─── Discover ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DiscoverSection(games: List<Game>, onGameClick: (Game) -> Unit, onLongClick: (Game) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.discover),
            style    = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color    = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = ScreenPadding, vertical = 4.dp),
        )
        LazyRow(
            modifier              = Modifier.fillMaxWidth(),
            contentPadding        = PaddingValues(horizontal = ScreenPadding),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(games.size, key = { games[it].id }) { index ->
                val game = games[index]
                DiscoverCoverCard(
                    game        = game,
                    onClick     = { onGameClick(game) },
                    onLongClick = { onLongClick(game) },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DiscoverCoverCard(game: Game, onClick: () -> Unit, onLongClick: () -> Unit) {
    val context         = LocalContext.current
    val fallbackPainter = rememberDrawablePainter(remember(game) { CoverUtils.getFallbackDrawable(game) })

    Surface(
        modifier        = Modifier
            .width(148.dp)
            .clip(RoundedCornerShape(12.dp)),
        shape           = RoundedCornerShape(12.dp),
        color           = MaterialTheme.colorScheme.surfaceVariant,
        onClick         = onClick,
    ) {
        Column {
            // 1:1 cover art
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                AsyncImage(
                    model              = ImageRequest.Builder(context).data(game.coverFrontUrl).build(),
                    contentDescription = game.title,
                    modifier           = Modifier.fillMaxSize(),
                    fallback           = fallbackPainter,
                    error              = fallbackPainter,
                    contentScale       = ContentScale.Crop,
                )
            }
            // Title below art
            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                Text(
                    text     = game.title,
                    style    = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color    = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ─── Notification ─────────────────────────────────────────────────────────────

@Composable
private fun HomeNotification(titleId: Int, messageId: Int, actionId: Int, enabled: Boolean = true, onAction: () -> Unit = {}) {
    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(titleId), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(messageId), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(modifier = Modifier.align(Alignment.End), onClick = onAction, enabled = enabled) {
                Text(stringResource(actionId))
            }
        }
    }
}

// ─── Empty state ──────────────────────────────────────────────────────────────

@Composable
private fun HomeEmptyGames() {
    Column(modifier = Modifier.fillMaxWidth().height(400.dp).padding(ScreenPadding),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(modifier = Modifier.size(96.dp).clip(RoundedCornerShape(28.dp)).background(
            Brush.linearGradient(colors = listOf(GradientStart.copy(alpha = 0.15f), GradientEnd.copy(alpha = 0.15f)))),
            contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.SportsEsports, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.home_empty_title),
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.home_empty_message), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        FilledTonalButton(onClick = {}) {
            Icon(Icons.Rounded.FolderOpen, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.home_empty_action))
        }
    }
}
