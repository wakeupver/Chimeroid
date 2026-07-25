package com.swordfish.chimeroid.app.mobile.shared.compose.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.swordfish.chimeroid.R
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun FavoriteToggle(
    isToggled: Boolean,
    onFavoriteToggle: (Boolean) -> Unit,
) {
    IconButton(
        onClick = { onFavoriteToggle(!isToggled) },
        modifier = Modifier.fillMaxSize(),
    ) {
        val image =
            if (isToggled) {
                Icons.Default.Favorite
            } else {
                Icons.Default.FavoriteBorder
            }
        Icon(
            image,
            contentDescription = stringResource(R.string.favorites),
            tint = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.size(24.dp),
        )
    }
}
