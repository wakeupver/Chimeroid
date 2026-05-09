package com.swordfish.lemuroid.app.mobile.shared.compose.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import com.swordfish.lemuroid.app.shared.covers.CoverUtils
import com.swordfish.lemuroid.lib.library.db.entity.Game

private val CardShape = RoundedCornerShape(10.dp)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LemuroidGameCard(
    modifier: Modifier = Modifier,
    game: Game,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue   = if (isPressed) 0.93f else 1f,
        animationSpec = tween(120),
        label         = "card_scale",
    )
    val borderAlpha by animateFloatAsState(
        targetValue   = if (isPressed) 0.85f else 0f,
        animationSpec = tween(120),
        label         = "border_alpha",
    )
    val fallbackPainter = rememberDrawablePainter(remember(game) { CoverUtils.getFallbackDrawable(game) })

    Box(
        modifier = modifier
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .clip(CardShape)
            .border(
                width = 1.5.dp,
                brush = Brush.linearGradient(listOf(
                    GradientStart.copy(alpha = borderAlpha),
                    GradientEnd.copy(alpha = borderAlpha),
                )),
                shape = CardShape,
            )
            .combinedClickable(
                interactionSource = interactionSource,
                indication        = null,
                onClick           = onClick,
                onLongClick       = onLongClick,
            ),
    ) {
        // Cover image
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            AsyncImage(
                model              = ImageRequest.Builder(LocalContext.current).data(game.coverFrontUrl).build(),
                contentDescription = game.title,
                modifier           = Modifier.fillMaxSize(),
                fallback           = fallbackPainter,
                error              = fallbackPainter,
                contentScale       = ContentScale.Crop,
            )

            // Bottom gradient + title overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxSize(0.55f)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(CoverGradientTop, CoverGradientBottom),
                        ),
                    ),
            )
            Text(
                text     = game.title,
                style    = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color    = androidx.compose.ui.graphics.Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 6.dp, vertical = 5.dp),
            )
        }
    }
}
