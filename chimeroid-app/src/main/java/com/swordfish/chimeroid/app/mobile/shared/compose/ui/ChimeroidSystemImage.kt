package com.swordfish.chimeroid.app.mobile.shared.compose.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.swordfish.chimeroid.app.shared.systems.MetaSystemInfo

@Composable
fun ChimeroidSystemImage(system: MetaSystemInfo) {
    val baseColor = Color(system.metaSystem.color())
    // Radial gradient: bright at centre, darker at edges → gives a lit-from-above feel
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.0f)
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        baseColor,
                        baseColor.copy(alpha = 0.80f),
                        Color.Black.copy(alpha = 0.25f).compositeOver(baseColor),
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            modifier = Modifier.fillMaxSize(0.60f),
            painter = painterResource(id = system.metaSystem.imageResId),
            contentDescription = stringResource(id = system.metaSystem.titleResId),
            contentScale = ContentScale.FillBounds,
        )
    }
}
