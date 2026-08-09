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
