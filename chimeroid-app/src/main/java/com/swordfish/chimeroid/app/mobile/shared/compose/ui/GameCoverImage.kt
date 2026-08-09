package com.swordfish.chimeroid.app.mobile.shared.compose.ui

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
fun GameCoverImage(
    modifier: Modifier = Modifier,
    game: Game,
    contentDescription: String? = game.title,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val context = LocalContext.current
    val fallbackPainter = rememberDrawablePainter(remember(game) { CoverUtils.getFallbackDrawable(game) })
    val request = remember(game) { ImageRequest.Builder(context).data(CoverRequest(game)).build() }

    AsyncImage(
        model = request,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        placeholder = fallbackPainter,
        fallback = fallbackPainter,
        error = fallbackPainter,
    )
}
