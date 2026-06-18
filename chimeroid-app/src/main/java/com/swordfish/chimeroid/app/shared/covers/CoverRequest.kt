package com.swordfish.chimeroid.app.shared.covers

import com.swordfish.chimeroid.lib.library.db.entity.Game

/**
 * Typed request object for Coil. Wraps [Game] so we can route all cover
 * loads through [CoverArtFetcher] instead of the generic HTTP fetcher.
 */
data class CoverRequest(val game: Game)
