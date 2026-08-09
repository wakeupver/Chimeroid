package com.swordfish.chimeroid.app.mobile.feature.home

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.lifecycle.Lifecycle
import com.swordfish.chimeroid.R
import com.swordfish.chimeroid.app.mobile.shared.compose.ui.ChimeroidTopBarActions
import com.swordfish.chimeroid.app.mobile.shared.compose.ui.ChimeroidTopBarChrome
import com.swordfish.chimeroid.app.mobile.shared.compose.ui.ChimeroidTopBarDefaults
import com.swordfish.chimeroid.app.mobile.shared.compose.ui.GameCoverImage
import com.swordfish.chimeroid.app.utils.android.ComposableLifecycle
import com.swordfish.chimeroid.app.utils.games.GameUtils
import com.swordfish.chimeroid.common.displayDetailsSettingsScreen
import com.swordfish.chimeroid.lib.library.db.entity.Game

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel,
    onGameClick: (Game) -> Unit,
    onGameLongClick: (Game) -> Unit,
    onOpenSystems: () -> Unit,
    onOpenFavorites: () -> Unit,
    onHelpPressed: () -> Unit,
    onSettingsClick: () -> Unit,
    saveSyncEnabled: Boolean = false,
    operationInProgress: Boolean = false,
    onSyncClick: () -> Unit = {},
) {
    val context = LocalContext.current
    val applicationContext = context.applicationContext

    ComposableLifecycle { _, event ->
        when (event) {
            Lifecycle.Event.ON_RESUME -> viewModel.updatePermissions(applicationContext)
            else -> { }
        }
    }

    val permissionsLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (!isGranted) context.displayDetailsSettingsScreen()
        }

    val state = viewModel.uiState.collectAsState()
    HomeScreen(
        modifier = modifier,
        state = state.value,
        onGameClicked = onGameClick,
        onGameLongClick = onGameLongClick,
        onOpenSystems = onOpenSystems,
        onOpenFavorites = onOpenFavorites,
        onHelpPressed = onHelpPressed,
        onSettingsClick = onSettingsClick,
        saveSyncEnabled = saveSyncEnabled,
        operationInProgress = operationInProgress,
        onSyncClick = onSyncClick,
        onEnableNotificationsClicked = {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@HomeScreen
            permissionsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        },
        onSetDirectoryClicked = { viewModel.changeLocalStorageFolder(context) },
        onSelectStorageLocationClicked = { viewModel.selectStorageLocation(context) },
    )
}

@Composable
private fun HomeScreen(
    modifier: Modifier = Modifier,
    state: HomeViewModel.UIState,
    onGameClicked: (Game) -> Unit,
    onGameLongClick: (Game) -> Unit,
    onOpenSystems: () -> Unit,
    onOpenFavorites: () -> Unit,
    onHelpPressed: () -> Unit,
    onSettingsClick: () -> Unit,
    saveSyncEnabled: Boolean,
    operationInProgress: Boolean,
    onSyncClick: () -> Unit,
    onEnableNotificationsClicked: () -> Unit,
    onSetDirectoryClicked: () -> Unit,
    onSelectStorageLocationClicked: () -> Unit,
) {
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    val thresholdPx = remember(density) { with(density) { 100.dp.toPx() } }
    val fraction by remember {
        derivedStateOf { (scrollState.value.toFloat() / thresholdPx).coerceIn(0f, 1f) }
    }

    val expandedHeaderDp = ChimeroidTopBarDefaults.HomeExpandedHeight
    val collapsedHeaderDp = ChimeroidTopBarDefaults.Height
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(modifier = modifier.fillMaxSize()) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(
                    top = expandedHeaderDp + statusBarHeight,
                    start = 20.dp,
                    end = 20.dp,
                    bottom = 32.dp,
                ),
        ) {

            AnimatedVisibility(state.showNoNotificationPermissionCard) {
                HomeNotificationBanner(
                    message = stringResource(R.string.home_notification_title),
                    actionId = R.string.home_notification_action,
                    onAction = onEnableNotificationsClicked,
                )
            }
            AnimatedVisibility(state.showStorageLocationCard) {
                HomeNotificationBanner(
                    message = stringResource(R.string.home_storage_location_title),
                    actionId = R.string.home_storage_location_action,
                    onAction = onSelectStorageLocationClicked,
                )
            }
            AnimatedVisibility(state.showNoGamesCard) {
                HomeNotificationBanner(
                    message = stringResource(R.string.home_empty_title),
                    actionId = R.string.home_empty_action,
                    onAction = onSetDirectoryClicked,
                    enabled = !state.indexInProgress,
                )
            }

            val lastGame = state.recentGames.firstOrNull()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(230.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                BentoContinuePlayingCard(
                    game = lastGame,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    onClick = { lastGame?.let(onGameClicked) },
                    onLongClick = { lastGame?.let(onGameLongClick) },
                )
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    BentoActionCard(
                        icon = Icons.Default.VideogameAsset,
                        title = "Game\nSystems",
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        onClick = onOpenSystems,
                    )
                    BentoActionCard(
                        icon = Icons.Default.Favorite,
                        title = "My\nFavorites",
                        containerColor = MaterialTheme.colorScheme.inverseSurface,
                        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        onClick = onOpenFavorites,
                    )
                }
            }

            Spacer(Modifier.height(30.dp))

            if (state.recentGames.isNotEmpty()) {
                HomeSectionHeader(title = stringResource(R.string.recent))
                Spacer(Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.recentGames.take(5).forEach { game ->
                        key(game.id) {
                            HomeGameListItem(
                                game = game,
                                accentColor = MaterialTheme.colorScheme.primaryContainer,
                                onClick = { onGameClicked(game) },
                                onLongClick = { onGameLongClick(game) },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(28.dp))
            }

            if (state.favoritesGames.isNotEmpty()) {
                HomeSectionHeader(title = stringResource(R.string.favorites))
                Spacer(Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.favoritesGames.take(4).forEach { game ->
                        key(game.id) {
                            HomeGameListItem(
                                game = game,
                                accentColor = MaterialTheme.colorScheme.tertiaryContainer,
                                onClick = { onGameClicked(game) },
                                onLongClick = { onGameLongClick(game) },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(28.dp))
            }

            if (state.discoveryGames.isNotEmpty()) {
                HomeSectionHeader(title = stringResource(R.string.discover))
                Spacer(Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.discoveryGames.take(4).forEach { game ->
                        key(game.id) {
                            HomeGameListItem(
                                game = game,
                                accentColor = MaterialTheme.colorScheme.secondaryContainer,
                                onClick = { onGameClicked(game) },
                                onLongClick = { onGameLongClick(game) },
                            )
                        }
                    }
                }
            }
        }

        HomeCollapsingHeader(
            modifier = Modifier.fillMaxWidth(),
            fraction = fraction,
            expandedHeight = expandedHeaderDp,
            collapsedHeight = collapsedHeaderDp,
            onHelpPressed = onHelpPressed,
            onSettingsClick = onSettingsClick,
            saveSyncEnabled = saveSyncEnabled,
            operationInProgress = operationInProgress,
            onSyncClick = onSyncClick,
        )
    }
}

@Composable
private fun HomeCollapsingHeader(
    modifier: Modifier = Modifier,
    fraction: Float,
    expandedHeight: Dp,
    collapsedHeight: Dp,
    onHelpPressed: () -> Unit,
    onSettingsClick: () -> Unit,
    saveSyncEnabled: Boolean,
    operationInProgress: Boolean,
    onSyncClick: () -> Unit,
) {
    val headerHeight = lerp(expandedHeight, collapsedHeight, fraction)
    val appName = stringResource(R.string.chimeroid_name)

    val expandedAlpha = (1f - fraction / 0.6f).coerceIn(0f, 1f)

    val collapsedAlpha = ((fraction - 0.4f) / 0.6f).coerceIn(0f, 1f)

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.background,
        shadowElevation = lerp(0.dp, ChimeroidTopBarDefaults.ShadowElevation, fraction),
    ) {
        Column {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(headerHeight),
            ) {

                ChimeroidTopBarChrome(
                    title = {
                        Text(
                            text = appName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.alpha(collapsedAlpha),
                        )
                    },
                    actions = {
                        ChimeroidTopBarActions(
                            onHelpPressed = onHelpPressed,
                            onSyncClick = onSyncClick,
                            onSettingsClick = onSettingsClick,
                            saveSyncEnabled = saveSyncEnabled,
                            operationInProgress = operationInProgress,
                        )
                    },
                )

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = ChimeroidTopBarDefaults.TitleStartPadding, bottom = 14.dp)
                        .alpha(expandedAlpha),
                ) {
                    Text(
                        text = stringResource(R.string.home_greeting),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        text = stringResource(R.string.home_greeting_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BentoContinuePlayingCard(
    game: Game?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        ) {
            if (game != null) {
                GameCoverImage(
                    modifier = Modifier.fillMaxSize(),
                    game = game,
                    contentDescription = null,
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.00f to primaryContainer.copy(alpha = if (game != null) 0.08f else 1f),
                                0.40f to Color.Transparent,
                                0.62f to primaryContainer.copy(alpha = 0.55f),
                                1.00f to primaryContainer.copy(alpha = 0.97f),
                            ),
                        ),
                    ),
            )
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(14.dp)
                    .size(38.dp),
                shape = CircleShape,
                color = onPrimaryContainer.copy(alpha = 0.13f),
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = onPrimaryContainer,
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                )
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(14.dp),
            ) {
                Text(
                    text = if (game != null) "Continue\nPlaying" else "Start\nPlaying",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = onPrimaryContainer,
                    lineHeight = MaterialTheme.typography.titleMedium.fontSize * 1.15,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = game?.title ?: "No recent games",
                    style = MaterialTheme.typography.bodySmall,
                    color = onPrimaryContainer.copy(alpha = 0.68f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun BentoActionCard(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = CircleShape,
                color = contentColor.copy(alpha = 0.14f),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = contentColor,
                lineHeight = MaterialTheme.typography.labelLarge.fontSize * 1.25,
            )
        }
    }
}

@Composable
private fun HomeSectionHeader(title: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(R.string.show_all),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeGameListItem(
    game: Game,
    accentColor: Color,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val context = LocalContext.current
    val subtitle = remember(game.id) { GameUtils.getGameSubtitle(context, game) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(accentColor),
                contentAlignment = Alignment.Center,
            ) {
                GameCoverImage(
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                    game = game,
                    contentDescription = null,
                )
            }
            Column(
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            ) {
                Text(
                    text = game.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(18.dp)
                    .combinedClickable(onClick = onLongClick),
            )
        }
    }
}

@Composable
private fun HomeNotificationBanner(
    message: String,
    actionId: Int,
    enabled: Boolean = true,
    onAction: () -> Unit,
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            FilledTonalButton(
                onClick = onAction,
                enabled = enabled,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(stringResource(id = actionId), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
