package com.swordfish.chimeroid.app.mobile.feature.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController

@Composable
fun MainNavigationBar(
    currentRoute: MainRoute?,
    navController: NavHostController,
) {
    AnimatedVisibility(
        visible = currentRoute?.showBottomNavigation != false,
        enter = expandVertically(),
        exit = shrinkVertically(),
    ) {
        ChimeroidNavigationBar(currentRoute, navController)
    }
}

@Composable
private fun ChimeroidNavigationBar(
    currentRoute: MainRoute?,
    navController: NavHostController,
) {
    NavigationBar(modifier = Modifier.fillMaxWidth()) {
        MainNavigationRoutes.entries.forEach { destination ->
            val isSelected = currentRoute?.root == destination.route
            val iconDrawable = if (isSelected) destination.selectedIcon else destination.unselectedIcon

            NavigationBarItem(
                icon = {
                    Icon(
                        imageVector = iconDrawable,
                        contentDescription = stringResource(destination.titleId),
                    )
                },
                label = { Text(stringResource(destination.titleId)) },
                selected = isSelected,
                onClick = {
                    navController.navigate(destination.route.route) {

                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = false
                        }

                        launchSingleTop = true

                        restoreState = false
                    }
                },
            )
        }
    }
}
