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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.swordfish.lemuroid.lib.library.SystemID
import com.swordfish.lemuroid.lib.library.db.entity.Game
import kotlinx.coroutines.launch

private val ScreenPadding = 16.dp

// ─── SystemID display names ───────────────────────────────────────────────────

private fun SystemID.displayName(): String = when (this) {
    SystemID.NES          -> "NES"
    SystemID.SNES         -> "SNES"
    SystemID.GENESIS      -> "Genesis"
    SystemID.GB           -> "Game Boy"
    SystemID.GBC          -> "GBC"
    SystemID.GBA          -> "GBA"
    SystemID.N64          -> "N64"
    SystemID.SMS          -> "Master System"
    SystemID.NDS          -> "NDS"
    SystemID.GG           -> "Game Gear"
    SystemID.ATARI2600    -> "Atari 2600"
    SystemID.FBNEO        -> "FBNeo"
    SystemID.MAME2003PLUS -> "MAME"
    SystemID.PC_ENGINE    -> "PC Engine"
    SystemID.LYNX         -> "Lynx"
    SystemID.ATARI7800    -> "Atari 7800"
    SystemID.SEGACD       -> "Sega CD"
    SystemID.NGP          -> "Neo Geo Pocket"
    SystemID.NGC          -> "Neo Geo Color"
    SystemID.WS           -> "WonderSwan"
    SystemID.WSC          -> "WonderSwan Color"
    SystemID.DOS          -> "DOS"
    SystemID.NINTENDO_3DS -> "3DS"
    SystemID.DREAMCAST    -> "Dreamcast"
    SystemID.PSX          -> "PlayStation"
    SystemID.PSP          -> "PSP"
}

// ─── Entry point ──────────────────────────────────────────────────────────────

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel,
    onGameClick: (Game) -> Unit,
    onGameLongClick: (Game) -> Unit,
    onOpenCoreSelection: () -> Unit,
) {
    val context = LocalContext.current

    ComposableLifecycle { _, event ->
        if (event == Lifecycle.Event.ON_RESUME) viewModel.updatePermissions(context.applicationContext)
    }

    val permissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (!granted) context.displayDetailsSettingsScreen() }

    val state            = viewModel.getViewStates().collectAsState(HomeViewModel.UIState())
    val selectedSystemId by viewModel.selectedSystemId.collectAsState()

    HomeScreenContent(
        modifier                       = modifier,
        state                          = state.value,
        selectedSystemId               = selectedSystemId,
        onSystemSelected               = { viewModel.selectSystem(it) },
        onShuffle                      = { viewModel.reshuffle() },
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

// ─── Content ──────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeScreenContent(
    modifier: Modifier = Modifier,
    state: HomeViewModel.UIState,
    selectedSystemId: String?,
    onSystemSelected: (String?) -> Unit,
    onShuffle: () -> Unit,
    onGameClicked: (Game) -> Unit,
    onGameLongClick: (Game) -> Unit,
    onOpenCoreSelection: () -> Unit,
    onEnableNotificationsClicked: () -> Unit,
    onEnableMicrophoneClicked: () -> Unit,
    onSetDirectoryClicked: () -> Unit,
    onSelectStorageLocationClicked: () -> Unit,
) {
    val listState     = rememberLazyListState()
    val scope         = rememberCoroutineScope()
    val showScrollTop = listState.firstVisibleItemIndex > 3

    // Label for filtered section header
    val selectedSystem = state.availableSystems.firstOrNull { it.dbname == selectedSystemId }
    val filteredLabel  = selectedSystem?.displayName() ?: "All Games"

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        LazyColumn(
            state          = listState,
            modifier       = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp),
        ) {

            // ── System chips ─────────────────────────────────────────────────
            item {
                SystemChipsRow(
                    systems          = state.availableSystems,
                    selectedSystemId = selectedSystemId,
                    onSelected       = onSystemSelected,
                )
            }

            // ── Notification banners ─────────────────────────────────────────
            item {
                Column(
                    modifier            = Modifier.padding(horizontal = ScreenPadding),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AnimatedVisibility(state.showStorageLocationCard) {
                        HomeNotification(R.string.home_storage_location_title,
                            R.string.home_storage_location_message,
                            R.string.home_storage_location_action,
                            onAction = onSelectStorageLocationClicked)
                    }
                    AnimatedVisibility(state.showNoNotificationPermissionCard) {
                        HomeNotification(R.string.home_notification_title,
                            R.string.home_notification_message,
                            R.string.home_notification_action,
                            onAction = onEnableNotificationsClicked)
                    }
                    AnimatedVisibility(state.showNoGamesCard) {
                        HomeNotification(R.string.home_empty_title, R.string.home_empty_message,
                            R.string.home_empty_action,
                            enabled = !state.indexInProgress,
                            onAction = onSetDirectoryClicked)
                    }
                    AnimatedVisibility(state.showNoMicrophonePermissionCard) {
                        HomeNotification(R.string.home_microphone_title,
                            R.string.home_microphone_message,
                            R.string.home_microphone_action,
                            onAction = onEnableMicrophoneClicked)
                    }
                    AnimatedVisibility(state.showDesmumeDeprecatedCard) {
                        HomeNotification(R.string.home_notification_desmume_deprecated_title,
                            R.string.home_notification_desmume_deprecated_message,
                            R.string.home_notification_desmume_deprecated_action,
                            onAction = onOpenCoreSelection)
                    }
                }
            }

            // ── Quick picks (Recents) ─────────────────────────────────────────
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

            // ── Speed dial (Favorites) ────────────────────────────────────────
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

            // ── Discover — horizontal random shuffle cards ─────────────────────
            if (state.discoveryGames.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(16.dp))
                    DiscoverSection(
                        games       = state.discoveryGames,
                        onGameClick = onGameClicked,
                        onLongClick = onGameLongClick,
                        onShuffle   = onShuffle,
                    )
                }
            }

            // ── Filtered game list — all games for selected chip ───────────────
            if (state.filteredGames.isNotEmpty()) {
                // Section header
                item {
                    Spacer(Modifier.height(20.dp))
                    Row(
                        modifier              = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = ScreenPadding, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically,
                    ) {
                        Text(
                            text  = filteredLabel,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            text  = "${state.filteredGames.size} games",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    HorizontalDivider(
                        modifier  = Modifier.padding(horizontal = ScreenPadding),
                        thickness = 1.dp,
                        color     = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    )
                }

                // Game rows
                items(state.filteredGames, key = { it.id }) { game ->
                    GameListRow(
                        game        = game,
                        onClick     = { onGameClicked(game) },
                        onMoreClick = { onGameLongClick(game) },
                    )
                }
            }

            // ── Empty state ────────────────────────────────────────────────────
            if (state.recentGames.isEmpty() && state.favoritesGames.isEmpty() &&
                state.discoveryGames.isEmpty() && !state.showNoGamesCard
            ) {
                item { HomeEmptyGames() }
            }
        }

        // Scroll-to-top FAB
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
                Box(
                    modifier         = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.KeyboardArrowUp, null,
                        tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

// ─── System chips ─────────────────────────────────────────────────────────────

@Composable
private fun SystemChipsRow(
    systems: List<SystemID>,
    selectedSystemId: String?,
    onSelected: (String?) -> Unit,
) {
    if (systems.isEmpty()) return

    LazyRow(
        modifier              = Modifier.fillMaxWidth(),
        contentPadding        = PaddingValues(horizontal = ScreenPadding, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            SystemChip(label = "All", selected = selectedSystemId == null) { onSelected(null) }
        }
        items(systems.size) { i ->
            val sys      = systems[i]
            val selected = selectedSystemId == sys.dbname
            SystemChip(label = sys.displayName(), selected = selected) { onSelected(sys.dbname) }
        }
    }
}

@Composable
private fun SystemChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick  = onClick,
        label    = {
            Text(label, style = MaterialTheme.typography.labelMedium.copy(
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

// ─── Quick picks ──────────────────────────────────────────────────────────────

@Composable
private fun QuickPicksSection(games: List<Game>, onGameClick: (Game) -> Unit, onLongClick: (Game) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier              = Modifier.fillMaxWidth()
                .padding(horizontal = ScreenPadding, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.recent),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground)
            SectionActionButton(icon = Icons.Rounded.PlayArrow, label = "Play all", onClick = {})
        }
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            games.take(6).forEach { game ->
                GameListRow(game = game, onClick = { onGameClick(game) }, onMoreClick = { onLongClick(game) })
            }
        }
    }
}

// ─── Speed dial ───────────────────────────────────────────────────────────────

@Composable
private fun SpeedDialSection(games: List<Game>, onGameClick: (Game) -> Unit, onLongClick: (Game) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenPadding, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(32.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.SportsEsports, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("YOUR LIBRARY", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.favorites),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground)
            }
            Icon(Icons.Rounded.ChevronRight, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }

        val cols  = 3
        val shown = games.take(6)
        val rows  = (shown.size + cols - 1) / cols
        Column(modifier = Modifier.padding(horizontal = ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (row in 0 until rows) {
                Row(modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

// ─── Discover — horizontal shuffled cards ─────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DiscoverSection(
    games: List<Game>,
    onGameClick: (Game) -> Unit,
    onLongClick: (Game) -> Unit,
    onShuffle: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier              = Modifier.fillMaxWidth()
                .padding(horizontal = ScreenPadding, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.discover),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground)
            SectionActionButton(icon = Icons.Rounded.Shuffle, label = "Shuffle", onClick = onShuffle)
        }

        LazyRow(
            modifier              = Modifier.fillMaxWidth(),
            contentPadding        = PaddingValues(horizontal = ScreenPadding),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(games, key = { it.id }) { game ->
                DiscoverCard(
                    game        = game,
                    onClick     = { onGameClick(game) },
                    onLongClick = { onLongClick(game) },
                )
            }
        }
    }
}

@Composable
private fun DiscoverCard(game: Game, onClick: () -> Unit, onLongClick: () -> Unit) {
    val context         = LocalContext.current
    val fallbackPainter = rememberDrawablePainter(remember(game) { CoverUtils.getFallbackDrawable(game) })

    Surface(
        modifier = Modifier.width(148.dp).clip(RoundedCornerShape(12.dp)),
        shape    = RoundedCornerShape(12.dp),
        color    = MaterialTheme.colorScheme.surfaceVariant,
        onClick  = onClick,
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                .background(MaterialTheme.colorScheme.surfaceVariant)) {
                AsyncImage(model = ImageRequest.Builder(context).data(game.coverFrontUrl).build(),
                    contentDescription = game.title, modifier = Modifier.fillMaxSize(),
                    fallback = fallbackPainter, error = fallbackPainter, contentScale = ContentScale.Crop)
            }
            Text(game.title,
                style    = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color    = MaterialTheme.colorScheme.onSurface,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp))
        }
    }
}

// ─── Shared: section action button (Play all / Shuffle) ───────────────────────

@Composable
private fun SectionActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp, onClick = onClick) {
        Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(15.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

// ─── Shared: game list row ────────────────────────────────────────────────────

@Composable
private fun GameListRow(game: Game, onClick: () -> Unit, onMoreClick: () -> Unit) {
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
            Text(game.title,
                style    = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color    = MaterialTheme.colorScheme.onSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank())
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onMoreClick, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Rounded.MoreVert, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    }
}

// ─── Notification ─────────────────────────────────────────────────────────────

@Composable
private fun HomeNotification(
    titleId: Int, messageId: Int, actionId: Int,
    enabled: Boolean = true, onAction: () -> Unit = {},
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(titleId), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(messageId), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(modifier = Modifier.align(Alignment.End),
                onClick = onAction, enabled = enabled) { Text(stringResource(actionId)) }
        }
    }
}

// ─── Empty state ──────────────────────────────────────────────────────────────

@Composable
private fun HomeEmptyGames() {
    Column(modifier = Modifier.fillMaxWidth().height(400.dp).padding(ScreenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Box(modifier = Modifier.size(96.dp).clip(RoundedCornerShape(28.dp)).background(
            Brush.linearGradient(colors = listOf(
                GradientStart.copy(alpha = 0.15f), GradientEnd.copy(alpha = 0.15f)))),
            contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.SportsEsports, null, modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary)
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
