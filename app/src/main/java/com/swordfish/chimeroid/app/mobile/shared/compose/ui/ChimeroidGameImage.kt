package com.swordfish.chimeroid.app.mobile.shared.compose.ui

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.swordfish.chimeroid.lib.library.db.entity.Game

@Composable
fun ChimeroidGameImage(
    modifier: Modifier = Modifier,
    game: Game,
) {
    GameCoverImage(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1.0f),
        game = game,
    )
}
