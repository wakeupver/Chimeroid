package com.swordfish.chimeroid.app.mobile.shared.compose.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * App-wide Material3 shape scale, threaded through [AppTheme] via `MaterialTheme(shapes = ...)`.
 *
 * [ChimeroidGameCard] and [ChimeroidSystemCard] previously each hardcoded their own
 * `RoundedCornerShape(14.dp)` — the same design decision typed out twice with no shared
 * source of truth. Both now read [Shapes.large] (16.dp, the standard M3 "large" step) from
 * here, so the corner radius is a single themeable token instead of a duplicated literal.
 */
val ChimeroidShapes =
    Shapes(
        extraSmall = RoundedCornerShape(4.dp),
        small = RoundedCornerShape(8.dp),
        medium = RoundedCornerShape(12.dp),
        large = RoundedCornerShape(16.dp),
        extraLarge = RoundedCornerShape(28.dp),
    )
