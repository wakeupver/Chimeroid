package com.swordfish.chimeroid.app.mobile.shared.compose.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.swordfish.chimeroid.lib.library.db.entity.Game
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun ChimeroidSmallGameImage(
    modifier: Modifier = Modifier,
    game: Game,
) {
    GameCoverImage(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1.0f)
            .background(MiuixTheme.colorScheme.surfaceContainer),
        game = game,
    )
}
