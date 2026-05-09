package com.swordfish.lemuroid.app.mobile.shared.compose.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.swordfish.lemuroid.R

@Composable
fun FavoriteToggle(
    isToggled: Boolean,
    onFavoriteToggle: (Boolean) -> Unit,
) {
    val tint by animateColorAsState(
        targetValue   = if (isToggled) GradientStart else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label         = "fav_tint",
    )
    IconToggleButton(
        checked         = isToggled,
        onCheckedChange = onFavoriteToggle,
        modifier        = Modifier.fillMaxSize(),
    ) {
        Icon(
            imageVector        = if (isToggled) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
            contentDescription = stringResource(R.string.favorites),
            tint               = tint,
            modifier           = Modifier.size(22.dp),
        )
    }
}
