package com.swordfish.lemuroid.app.mobile.feature.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController

@Composable
fun MainNavigationBar(
    currentRoute: MainRoute?,
    navController: NavHostController,
) {
    AnimatedVisibility(
        visible = currentRoute?.showBottomNavigation != false,
        enter   = expandVertically(),
        exit    = shrinkVertically(),
    ) {
        ChimeroidNavigationBar(currentRoute, navController)
    }
}

@Composable
private fun ChimeroidNavigationBar(
    currentRoute: MainRoute?,
    navController: NavHostController,
) {
    NavigationBar(
        modifier         = Modifier.fillMaxWidth(),
        containerColor   = MaterialTheme.colorScheme.surface,
        tonalElevation   = androidx.compose.ui.unit.Dp.Unspecified,
    ) {
        MainNavigationRoutes.values().forEach { destination ->
            val isSelected  = currentRoute?.root == destination.route
            val iconDrawable = if (isSelected) destination.selectedIcon else destination.unselectedIcon

            NavigationBarItem(
                icon = {
                    Icon(
                        imageVector        = iconDrawable,
                        contentDescription = stringResource(destination.titleId),
                    )
                },
                label = {
                    Text(
                        text       = stringResource(destination.titleId),
                        style      = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        ),
                    )
                },
                selected = isSelected,
                colors   = NavigationBarItemDefaults.colors(
                    selectedIconColor        = MaterialTheme.colorScheme.onPrimary,
                    unselectedIconColor      = MaterialTheme.colorScheme.onSurfaceVariant,
                    selectedTextColor        = MaterialTheme.colorScheme.primary,
                    unselectedTextColor      = MaterialTheme.colorScheme.onSurfaceVariant,
                    indicatorColor           = MaterialTheme.colorScheme.primary,
                ),
                onClick = {
                    navController.navigate(destination.route.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = false
                        }
                        launchSingleTop = true
                        restoreState    = false
                    }
                },
            )
        }
    }
}
