package com.swordfish.chimeroid.app.mobile.shared.compose.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.swordfish.chimeroid.app.shared.systems.MetaSystemInfo

// Expressive shape language via a stable API: an asymmetric "twist" — one diagonal
// pair of corners rounds almost to a circle, the other stays tight — instead of a
// uniform-radius rounded rect. (MaterialShapes.Cookie9Sided.toShape() would be the
// "real" M3 Expressive token, but it needs material3 1.5.0-alpha, whose transitive
// compose-animation/-foundation/-ui siblings require AGP 9.1.0+/compileSdk 37; this
// project is on AGP 8.7.2/compileSdk 35, so that pin broke
// :chimeroid-app:checkFreeReleaseAarMetadata in CI. Revisit once AGP/compileSdk are
// bumped.) Percent-based so it scales cleanly across every adaptive grid cell size
// rather than a fixed dp radius. Single shared instance: createOutline is a pure
// function of the incoming size, so every card in the grid reuses this one object —
// zero per-item allocation.
private val ExpressiveChipShape = RoundedCornerShape(
    topStartPercent = 44,
    topEndPercent = 16,
    bottomEndPercent = 44,
    bottomStartPercent = 16,
)

private const val ChipSizeFraction = 0.92f
private const val IconSizeFraction = 0.64f
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
