package com.swordfish.chimeroid.app.mobile.shared.compose.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.swordfish.chimeroid.app.shared.systems.MetaSystemInfo

// Real Material 3 Expressive shape token (androidx.compose.material3.MaterialShapes,
// material3 1.5.0-alpha) instead of a hand-rolled RoundedCornerShape approximation —
// the scalloped "cookie" silhouette is one of the signature Expressive shapes.
// toShape() wraps a normalized RoundedPolygon into a Compose Shape; both the polygon
// and the wrapper are stateless with respect to a given (size, layoutDirection, density)
// call, so one shared top-level instance is reused by every card in the grid — zero
// per-item allocation.
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val ExpressiveChipShape: Shape = MaterialShapes.Cookie9Sided.toShape()

private const val ChipSizeFraction = 0.92f
private const val IconSizeFraction = 0.60f
private const val TonalWashAlpha = 0.16f

@Composable
fun ChimeroidSystemImage(system: MetaSystemInfo) {
    val baseColor = Color(system.metaSystem.color())

    // Flat tonal wash instead of the old edge-darkening radial-gradient vignette:
    // a cheap solid-color fill (no per-pixel gradient shader) that still reads as
    // "this system's color" while leaving the chip below as the one saturated
    // accent — that contrast is what gives the layout its depth.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(baseColor.copy(alpha = TonalWashAlpha)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize(ChipSizeFraction)
                .clip(ExpressiveChipShape)
                .background(baseColor),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                modifier = Modifier.fillMaxSize(IconSizeFraction),
                painter = painterResource(id = system.metaSystem.imageResId),
                contentDescription = stringResource(id = system.metaSystem.titleResId),
                contentScale = ContentScale.FillBounds,
            )
        }
    }
}
