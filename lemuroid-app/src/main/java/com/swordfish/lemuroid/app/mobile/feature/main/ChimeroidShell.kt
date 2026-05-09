package com.swordfish.lemuroid.app.mobile.feature.main

import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VideogameAsset
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.swordfish.lemuroid.R
import kotlinx.coroutines.launch

// Maps EmuCoreX's PrimaryDestination to Chimeroid's MainRoute
enum class PrimaryDestination {
    Home, Favorites, Search, Systems, Settings
}

private enum class MobileLeadingAction { Drawer, Back }

@OptIn(ExperimentalLayoutApi::class)
@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
fun ChimeroidShell(
    selected: PrimaryDestination,
    drawerEnabled: Boolean = true,
    onNavigateHome: () -> Unit,
    onNavigateFavorites: () -> Unit,
    onNavigateSearch: () -> Unit,
    onNavigateSystems: () -> Unit,
    onNavigateSettings: () -> Unit,
    onBackClick: (() -> Unit)? = null,
    content: @Composable (openDrawer: (() -> Unit)?) -> Unit,
) {
    val navContent: @Composable () -> Unit = {
        SideNavigation(
            selected = selected,
            onNavigateHome = onNavigateHome,
            onNavigateFavorites = onNavigateFavorites,
            onNavigateSearch = onNavigateSearch,
            onNavigateSystems = onNavigateSystems,
            onNavigateSettings = onNavigateSettings,
            onCloseDrawer = { },
        )
    }

    val configuration = LocalConfiguration.current
    val isTabletClass = configuration.smallestScreenWidthDp >= 600
    val isWide = isTabletClass && configuration.screenWidthDp >= 900

    if (isWide) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(320.dp)
                    .padding(start = 12.dp, top = 12.dp, bottom = 12.dp),
            ) {
                navContent()
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) {
                content(null)
            }
        }
    } else {
        CompactShell(
            selected = selected,
            drawerEnabled = drawerEnabled,
            onNavigateHome = onNavigateHome,
            onNavigateFavorites = onNavigateFavorites,
            onNavigateSearch = onNavigateSearch,
            onNavigateSystems = onNavigateSystems,
            onNavigateSettings = onNavigateSettings,
            onBackClick = onBackClick,
            content = content,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
private fun CompactShell(
    selected: PrimaryDestination,
    drawerEnabled: Boolean,
    onNavigateHome: () -> Unit,
    onNavigateFavorites: () -> Unit,
    onNavigateSearch: () -> Unit,
    onNavigateSystems: () -> Unit,
    onNavigateSettings: () -> Unit,
    onBackClick: (() -> Unit)?,
    content: @Composable (openDrawer: (() -> Unit)?) -> Unit,
) {
    val configuration = LocalConfiguration.current
    val statusPadding = WindowInsets.statusBarsIgnoringVisibility.asPaddingValues().calculateTopPadding()
    val isTabletClass = configuration.smallestScreenWidthDp >= 600
    val isLandscapeCompact = configuration.screenWidthDp > configuration.screenHeightDp
    val drawerWidthFraction = when {
        isLandscapeCompact && isTabletClass -> 0.54f
        isLandscapeCompact -> 0.46f
        else -> 0.74f
    }
    val selectedDrawerItemFocusRequester = remember { FocusRequester() }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val mobileLeadingAction = if (
        !drawerEnabled || (selected != PrimaryDestination.Home && onBackClick != null)
    ) {
        MobileLeadingAction.Back
    } else {
        MobileLeadingAction.Drawer
    }

    val leadingActionClick = when (mobileLeadingAction) {
        MobileLeadingAction.Drawer -> rememberDebouncedClick {
            scope.launch {
                if (drawerState.isClosed) drawerState.open() else drawerState.close()
            }
        }
        MobileLeadingAction.Back -> {
            { onBackClick?.invoke(); Unit }
        }
    }

    LaunchedEffect(selected, mobileLeadingAction) {
        if (drawerState.isOpen) drawerState.close()
    }
    LaunchedEffect(drawerState.isOpen, mobileLeadingAction, selected) {
        if (drawerState.isOpen && mobileLeadingAction == MobileLeadingAction.Drawer) {
            selectedDrawerItemFocusRequester.requestFocus()
        }
    }

    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerEnabled && mobileLeadingAction == MobileLeadingAction.Drawer,
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.42f),
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(drawerWidthFraction)
                    .widthIn(min = 292.dp, max = if (isTabletClass) 360.dp else 320.dp),
                drawerShape = RoundedCornerShape(topEnd = 30.dp, bottomEnd = 30.dp),
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                drawerContentColor = MaterialTheme.colorScheme.onSurface,
                drawerTonalElevation = 6.dp,
                windowInsets = WindowInsets(0, 0, 0, 0),
            ) {
                SideNavigation(
                    selected = selected,
                    onNavigateHome = onNavigateHome,
                    onNavigateFavorites = onNavigateFavorites,
                    onNavigateSearch = onNavigateSearch,
                    onNavigateSystems = onNavigateSystems,
                    onNavigateSettings = onNavigateSettings,
                    selectedItemFocusRequester = selectedDrawerItemFocusRequester,
                    wrapInSurface = false,
                    topInset = statusPadding,
                    onCloseDrawer = { scope.launch { drawerState.close() } },
                )
            }
        },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            content(
                if (drawerEnabled && mobileLeadingAction == MobileLeadingAction.Drawer) {
                    leadingActionClick
                } else {
                    null
                }
            )
            // Floating hamburger button on non-home screens
            if (mobileLeadingAction == MobileLeadingAction.Drawer && selected != PrimaryDestination.Home) {
                Surface(
                    modifier = Modifier
                        .padding(top = statusPadding + 12.dp, start = 12.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    tonalElevation = 4.dp,
                    shadowElevation = 6.dp,
                    onClick = leadingActionClick,
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Menu,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SideNavigation(
    selected: PrimaryDestination,
    onNavigateHome: () -> Unit,
    onNavigateFavorites: () -> Unit,
    onNavigateSearch: () -> Unit,
    onNavigateSystems: () -> Unit,
    onNavigateSettings: () -> Unit,
    selectedItemFocusRequester: FocusRequester? = null,
    wrapInSurface: Boolean = true,
    topInset: androidx.compose.ui.unit.Dp = WindowInsets.statusBarsIgnoringVisibility
        .asPaddingValues().calculateTopPadding(),
    onCloseDrawer: () -> Unit,
) {
    val drawerInset = 18.dp
    val drawerSectionSpacing = 14.dp
    val drawerBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val navigateHome = rememberDebouncedClick { onCloseDrawer(); onNavigateHome() }
    val navigateFavorites = rememberDebouncedClick { onCloseDrawer(); onNavigateFavorites() }
    val navigateSearch = rememberDebouncedClick { onCloseDrawer(); onNavigateSearch() }
    val navigateSystems = rememberDebouncedClick { onCloseDrawer(); onNavigateSystems() }
    val navigateSettings = rememberDebouncedClick { onCloseDrawer(); onNavigateSettings() }

    val content: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = drawerInset,
                    end = drawerInset,
                    top = drawerInset,
                    bottom = drawerInset + drawerBottomInset,
                ),
            verticalArrangement = Arrangement.spacedBy(drawerSectionSpacing),
        ) {
            // App name header
            Text(
                text = stringResource(R.string.lemuroid_name),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = topInset + 4.dp, start = 6.dp, end = 6.dp),
            )

            // Section label: Quick Actions
            Text(
                text = stringResource(R.string.title_home),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )

            ShellItem(
                icon = Icons.Rounded.Home,
                label = stringResource(R.string.title_home),
                selected = selected == PrimaryDestination.Home,
                modifier = if (selected == PrimaryDestination.Home && selectedItemFocusRequester != null) {
                    Modifier.focusRequester(selectedItemFocusRequester)
                } else Modifier,
                onClick = navigateHome,
            )
            ShellItem(
                icon = Icons.Rounded.Favorite,
                label = stringResource(R.string.favorites),
                selected = selected == PrimaryDestination.Favorites,
                modifier = if (selected == PrimaryDestination.Favorites && selectedItemFocusRequester != null) {
                    Modifier.focusRequester(selectedItemFocusRequester)
                } else Modifier,
                onClick = navigateFavorites,
            )
            ShellItem(
                icon = Icons.Rounded.Search,
                label = stringResource(R.string.title_search),
                selected = selected == PrimaryDestination.Search,
                modifier = if (selected == PrimaryDestination.Search && selectedItemFocusRequester != null) {
                    Modifier.focusRequester(selectedItemFocusRequester)
                } else Modifier,
                onClick = navigateSearch,
            )

            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
            )

            // Section: Library
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.title_systems),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                ShellItem(
                    icon = Icons.Rounded.VideogameAsset,
                    label = stringResource(R.string.title_systems),
                    selected = selected == PrimaryDestination.Systems,
                    modifier = if (selected == PrimaryDestination.Systems && selectedItemFocusRequester != null) {
                        Modifier.focusRequester(selectedItemFocusRequester)
                    } else Modifier,
                    onClick = navigateSystems,
                )
            }

            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
            )

            // Section: App
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.title_settings),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                ShellItem(
                    icon = Icons.Rounded.Settings,
                    label = stringResource(R.string.title_settings),
                    selected = selected == PrimaryDestination.Settings,
                    modifier = if (selected == PrimaryDestination.Settings && selectedItemFocusRequester != null) {
                        Modifier.focusRequester(selectedItemFocusRequester)
                    } else Modifier,
                    onClick = navigateSettings,
                )
            }
        }
    }

    if (wrapInSurface) {
        Surface(
            modifier = Modifier.fillMaxHeight(),
            shape = RoundedCornerShape(30.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
        ) { content() }
    } else {
        content()
    }
}

@Composable
private fun ShellItem(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .focusable(),
        shape = RoundedCornerShape(18.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f)
        },
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

@Composable
private fun ShellAction(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .focusable(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}
