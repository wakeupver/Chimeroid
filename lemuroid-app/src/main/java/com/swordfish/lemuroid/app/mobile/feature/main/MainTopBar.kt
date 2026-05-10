package com.swordfish.lemuroid.app.mobile.feature.main

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.GradientEnd
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.GradientStart
import com.swordfish.lemuroid.app.shared.savesync.SaveSyncWork

@Composable
fun MainTopBar(
    currentRoute: MainRoute,
    navController: NavHostController,
    onHelpPressed: () -> Unit,
    onUpdateQueryString: (String) -> Unit,
    mainUIState: MainViewModel.UiState,
) {
    Column {
        LemuroidTopAppBar(
            route               = currentRoute,
            navController       = navController,
            mainUIState         = mainUIState,
            onUpdateQueryString = onUpdateQueryString,
        )
        AnimatedVisibility(mainUIState.operationInProgress) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color    = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LemuroidTopAppBar(
    route: MainRoute,
    navController: NavController,
    mainUIState: MainViewModel.UiState,
    onUpdateQueryString: (String) -> Unit,
) {
    val context = LocalContext.current

    TopAppBar(
        title = {
            when {
                route == MainRoute.SEARCH -> LemuroidSearchView(mainUIState, onUpdateQueryString)
                route.parent == null      -> GradientTitle(stringResource(route.titleId))
                else                      -> Text(
                    text  = stringResource(route.titleId),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor         = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.surface,
        ),
        navigationIcon = {
            AnimatedVisibility(visible = route.parent != null, enter = fadeIn(), exit = fadeOut()) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(
                        imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint               = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
        },
        actions = {
            Row {
                // Save sync
                if (mainUIState.saveSyncEnabled) {
                    IconButton(
                        onClick  = { SaveSyncWork.enqueueManualWork(context.applicationContext) },
                        enabled  = !mainUIState.operationInProgress,
                    ) {
                        Icon(Icons.Outlined.CloudSync, null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                // Search — always visible on root screens (except SEARCH itself)
                if (route.showBottomNavigation && route != MainRoute.SEARCH) {
                    IconButton(onClick = { navController.navigate(MainRoute.SEARCH.route) }) {
                        Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.title_search),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                // Settings — always visible on root screens
                if (route.showBottomNavigation) {
                    IconButton(onClick = { navController.navigate(MainRoute.SETTINGS.route) }) {
                        Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.settings),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
    )
}

@Composable
fun GradientTitle(text: String) {
    val brush = Brush.linearGradient(colors = listOf(GradientStart, GradientEnd))
    Text(
        text  = text,
        style = MaterialTheme.typography.headlineMedium.copy(
            fontWeight = FontWeight.Bold,
            brush      = brush,
        ),
    )
}

@Composable
private fun LemuroidSearchView(mainUIState: MainViewModel.UiState, onUpdateQueryString: (String) -> Unit) {
    val focusRequester = remember { FocusRequester() }
    val focusManager   = LocalFocusManager.current

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Box(modifier = Modifier.fillMaxWidth().height(56.dp)) {
        Surface(
            modifier       = Modifier.fillMaxSize().padding(top = 8.dp, bottom = 8.dp, end = 8.dp),
            shape          = RoundedCornerShape(100),
            tonalElevation = 4.dp,
            color          = MaterialTheme.colorScheme.surface,
        ) {}
        TextField(
            value         = mainUIState.searchQuery,
            modifier      = Modifier.fillMaxSize().focusRequester(focusRequester),
            textStyle     = MaterialTheme.typography.bodyMedium,
            leadingIcon   = {
                Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            onValueChange = onUpdateQueryString,
            singleLine    = true,
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus(true) }),
            colors        = TextFieldDefaults.colors(
                focusedContainerColor   = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor   = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
        )
    }
}
