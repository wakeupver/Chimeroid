package com.swordfish.lemuroid.app.mobile.shared.compose.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.shared.systems.MetaSystemInfo

private val SysCardShape = RoundedCornerShape(14.dp)

@Composable
fun LemuroidSystemCard(
    modifier: Modifier = Modifier,
    system: MetaSystemInfo,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue   = if (isPressed) 0.94f else 1f,
        animationSpec = tween(110),
        label         = "sys_scale",
    )
    val context = LocalContext.current
    val accentColor = Color(system.metaSystem.color())
    val isLight = accentColor.luminance() > 0.4f
    val textColor = if (isLight) Color(0xFF111111) else Color(0xFFFFFFFF)

    Surface(
        modifier       = modifier.graphicsLayer(scaleX = scale, scaleY = scale),
        onClick        = onClick,
        interactionSource = interactionSource,
        shape          = SysCardShape,
        color          = accentColor,
        tonalElevation = 0.dp,
        shadowElevation= 0.dp,
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
            // Subtle radial glow from center
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.12f),
                                Color.Transparent,
                            ),
                        ),
                    ),
            )

            // Console image — bottom-right anchored, large and proud
            Image(
                painter            = painterResource(system.metaSystem.imageResId),
                contentDescription = null,
                modifier           = Modifier
                    .fillMaxSize(0.7f)
                    .align(Alignment.BottomEnd)
                    .alpha(0.18f),
                contentScale       = ContentScale.FillBounds,
            )

            // Foreground content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                // Console icon pill (top-left)
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter            = painterResource(system.metaSystem.imageResId),
                        contentDescription = null,
                        modifier           = Modifier.fillMaxSize(0.65f),
                        contentScale       = ContentScale.Fit,
                    )
                }

                // Name + count
                Column {
                    Text(
                        text     = system.getName(context),
                        style    = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
                        color    = textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(1.dp))
                    Text(
                        text  = stringResource(R.string.system_grid_details, system.count.toString()),
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor.copy(alpha = 0.72f),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
