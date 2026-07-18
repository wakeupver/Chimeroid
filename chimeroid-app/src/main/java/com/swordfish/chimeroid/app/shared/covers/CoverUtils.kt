package com.swordfish.chimeroid.app.shared.covers

import android.content.Context
import coil.ImageLoader
import coil.memory.MemoryCache
import com.swordfish.chimeroid.common.drawable.TextDrawable
import com.swordfish.chimeroid.common.graphics.ColorUtils
import com.swordfish.chimeroid.lib.library.db.entity.Game
import java.util.Locale
import kotlinx.coroutines.Dispatchers

object CoverUtils {

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

    private fun computeTitle(game: Game): String {
        val sanitizedName =
            game.title
                .replace(Regex("\\(.*\\)"), "")

        return sanitizedName.asSequence()
            .filter { it.isDigit() or it.isUpperCase() or (it == '&') }
            .take(3)
            .joinToString("")
            .ifBlank { game.title.first().toString() }
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }

    private fun computeColor(game: Game): Int {
        return ColorUtils.randomColor(game.title)
    }
}
