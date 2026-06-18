package com.swordfish.chimeroid.app.shared.covers

import android.content.Context
import android.widget.ImageView
import coil.ImageLoader
import coil.imageLoader
import coil.load
import coil.memory.MemoryCache
import com.swordfish.chimeroid.common.drawable.TextDrawable
import com.swordfish.chimeroid.common.graphics.ColorUtils
import com.swordfish.chimeroid.lib.library.db.entity.Game
import kotlinx.coroutines.Dispatchers

object CoverUtils {

    /**
     * Loads a cover into [imageView] using the app-wide ImageLoader.
     * Data is a [CoverRequest] so our [CoverArtFetcher] handles it.
     */
    fun loadCover(
        game: Game,
        imageView: ImageView?,
    ) {
        if (imageView == null) return
        imageView.load(CoverRequest(game), imageView.context.imageLoader) {
            val fallback = getFallbackDrawable(game)
            fallback(fallback)
            error(fallback)
        }
    }

    /**
     * Builds the global Coil [ImageLoader].
     *
     * Cover art is handled entirely by [CoverArtFetcher]:
     *   - local JPEG file → served directly (no network round-trip)
     *   - missing         → download, compress to 512×512 JPEG, save, serve
     *
     * Coil's own DiskCache is disabled because we manage persistence ourselves
     * (individual .jpg files + a single covers.zip pack).
     */
    fun buildImageLoader(applicationContext: Context): ImageLoader {
        val coverFetcherFactory = CoverArtFetcher.Factory(applicationContext)
        return ImageLoader.Builder(applicationContext)
            .components {
                add(coverFetcherFactory)
            }
            .memoryCache {
                MemoryCache.Builder(applicationContext)
                    .maxSizePercent(0.15)
                    .build()
            }
            .crossfade(true)
            .interceptorDispatcher(Dispatchers.IO)
            .build()
    }

    fun getFallbackDrawable(game: Game) = TextDrawable(computeTitle(game), computeColor(game))

    fun getFallbackRemoteUrl(game: Game): String {
        val color = Integer.toHexString(computeColor(game)).substring(2)
        val title = computeTitle(game)
        return "https://fakeimg.pl/512x512/$color/fff/?font=bebas&text=$title"
    }

    private fun computeTitle(game: Game): String {
        val sanitizedName =
            game.title
                .replace(Regex("\\(.*\\)"), "")

        return sanitizedName.asSequence()
            .filter { it.isDigit() or it.isUpperCase() or (it == '&') }
            .take(3)
            .joinToString("")
            .ifBlank { game.title.first().toString() }
            .capitalize()
    }

    private fun computeColor(game: Game): Int {
        return ColorUtils.randomColor(game.title)
    }
}
