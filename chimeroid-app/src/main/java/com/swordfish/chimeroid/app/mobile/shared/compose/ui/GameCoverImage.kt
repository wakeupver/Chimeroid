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

/**
 * Single source of truth for rendering a [Game]'s cover art through Coil, including the
 * shared placeholder/fallback/error drawable lookup. Every cover-art surface in the app
 * (grid cards, list rows, small thumbnails, bento tiles) renders through this composable
 * so the request-building and fallback logic exists exactly once.
 *
 * [fallbackPainter] backs [placeholder], [fallback], and [error] alike, so a cover that is
 * still downloading shows the same title/color avatar as a missing or failed one instead
 * of a blank tile — one allocation per [game] rather than one per Coil state.
 *
 * Both the [ImageRequest] and [fallbackPainter] are keyed on [game], so recomposition
 * caused by unrelated state changes reuses the previous instances instead of re-allocating
 * them every frame.
 */
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
