package com.swordfish.chimeroid.app.mobile.shared.compose.ui

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import com.swordfish.chimeroid.app.shared.covers.CoverRequest
import com.swordfish.chimeroid.app.shared.covers.CoverUtils
import com.swordfish.chimeroid.lib.library.db.entity.Game

@Composable
fun ChimeroidGameImage(
    modifier: Modifier = Modifier,
    game: Game,
) {
    val fallbackDrawable =
        remember(game) {
            CoverUtils.getFallbackDrawable(game)
        }

    val fallbackPainter = rememberDrawablePainter(drawable = fallbackDrawable)

    AsyncImage(
        model =
            ImageRequest.Builder(LocalContext.current)
                .data(CoverRequest(game))
                .build(),
        contentDescription = game.title,
        modifier =
            modifier
                .fillMaxWidth()
                .aspectRatio(1.0f),
        fallback = fallbackPainter,
        error = fallbackPainter,
        contentScale = ContentScale.Crop,
    )
}
