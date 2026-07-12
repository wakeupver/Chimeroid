package com.swordfish.chimeroid.app.mobile.feature.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import com.swordfish.chimeroid.R
import com.swordfish.chimeroid.app.mobile.shared.compose.ui.ChimeroidTopBarActions
import com.swordfish.chimeroid.app.mobile.shared.compose.ui.ChimeroidTopBarChrome
import com.swordfish.chimeroid.app.mobile.shared.compose.ui.ChimeroidTopBarDefaults
import com.swordfish.chimeroid.app.shared.savesync.SaveSyncWork

// ─────────────────────────────────────────────────────────────────────────────
// Public entry-point — wrapped in a Surface so it carries the same
// background + drop-shadow as HomeCollapsingHeader.
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun MainTopBar(
    currentRoute: MainRoute,
    navController: NavHostController,
    onHelpPressed: () -> Unit,
    onUpdateQueryString: (String) -> Unit,
    mainUIState: MainViewModel.UiState,
) {
    // Surface provides background color + drop-shadow — matching HomeCollapsingHeader
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background,
        shadowElevation = ChimeroidTopBarDefaults.ShadowElevation,
    ) {
        Column(
            modifier = Modifier.windowInsetsPadding(
                WindowInsets.systemBars.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
            ),
        ) {
            ChimeroidTopAppBar(
                route = currentRoute,
                navController = navController,
                mainUIState = mainUIState,
                onHelpPressed = onHelpPressed,
                onUpdateQueryString = onUpdateQueryString,
            )
            AnimatedVisibility(mainUIState.operationInProgress) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TopAppBar styled to match HomeCollapsingHeader (collapsed state):
//   • Same colorScheme.background surface (Surface above is transparent here)
//   • Sub-routes   → back arrow
//   • Title        → titleLarge + FontWeight.Bold
//   • Actions      → Info | CloudSync? | Settings?
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun ChimeroidTopAppBar(
    route: MainRoute,
    navController: NavController,
    mainUIState: MainViewModel.UiState,
    onHelpPressed: () -> Unit,
    onUpdateQueryString: (String) -> Unit,
) {
    val context = LocalContext.current

    // Built on the same ChimeroidTopBarChrome HomeScreen pins inside its
    // collapsing header, so height/insets can never drift from HomeScreen again.
    ChimeroidTopBarChrome(
        // ── Nav icon: back arrow for sub-routes only ─────────────────────────
        navigationIcon = if (route.parent != null) {
            {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                    )
                }
            }
        } else {
            null
        },

        // ── Title: search view or bold page name ─────────────────────────────
        title = {
            if (route == MainRoute.SEARCH) {
                ChimeroidSearchView(
                    mainUIState = mainUIState,
                    onUpdateQueryString = onUpdateQueryString,
                )
            } else {
                Text(
                    text = stringResource(route.titleId),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },

        // ── Actions: same shared composable HomeScreen uses ───────────────────
        actions = {
            ChimeroidTopBarActions(
                onHelpPressed = onHelpPressed,
                onSyncClick = { SaveSyncWork.enqueueManualWork(context.applicationContext) },
                onSettingsClick = { navController.navigate(MainRoute.SETTINGS.route) },
                saveSyncEnabled = mainUIState.saveSyncEnabled,
                operationInProgress = mainUIState.operationInProgress,
                showSettingsAction = route.showBottomNavigation,
            )
        },
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Inline search field (Search route only)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun ChimeroidSearchView(
    mainUIState: MainViewModel.UiState,
    onUpdateQueryString: (String) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 8.dp, end = 8.dp),
        shape = RoundedCornerShape(100),
        tonalElevation = 16.dp,
    ) {
        TextField(
            value = mainUIState.searchQuery,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            textStyle = MaterialTheme.typography.bodyMedium,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            onValueChange = { onUpdateQueryString(it) },
            singleLine = true,
            keyboardActions = KeyboardActions(
                onDone = { focusManager.clearFocus(true) },
            ),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
        )
    }
}
