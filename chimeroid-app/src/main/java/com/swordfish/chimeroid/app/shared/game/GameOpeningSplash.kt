package com.swordfish.chimeroid.app.shared.game

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.swordfish.chimeroid.R

private const val ICON_ANIM_MS = 420
private const val TITLE_ANIM_MS = 380
private const val TITLE_DELAY_MS = 160
private const val MIN_ICON_SCALE = 0.7f
private const val TITLE_SLIDE_PX = 24f
private val ICON_SIZE = 64.dp
private val SPINNER_SIZE = 28.dp
private val SPINNER_STROKE = 2.5.dp

/**
 * Branded loading splash shown by [BaseGameScreen] while the core boots: the Chimeroid mark
 * scales/fades in, the [gameTitle] follows after a short stagger, and the existing spinner plus
 * [loadingMessage] fade in beneath it.
 *
 * A single [LaunchedEffect] keyed on [Unit] drives every stage from one `revealed` flag, so the
 * sequence runs exactly once per composition (game launch) and never restarts when
 * [loadingMessage] changes as the core walks through its own loading steps — only [revealed]
 * feeds the animations, so unrelated recompositions are free.
 *
 * Deliberately takes plain data rather than the view-model/state-machine directly: keeps this
 * reusable, trivially previewable, and free of any coupling to [GameViewModelRetroGameView].
 */
@Composable
fun GameOpeningSplash(
    gameTitle: String,
    loadingMessage: String?,
    modifier: Modifier = Modifier,
) {
    var revealed by remember { mutableFloatStateOf(0f) }

    val iconProgress by animateFloatAsState(
        targetValue = revealed,
        animationSpec = tween(durationMillis = ICON_ANIM_MS, easing = LinearOutSlowInEasing),
        label = "splash_icon_reveal",
    )
    val titleProgress by animateFloatAsState(
        targetValue = revealed,
        animationSpec =
            tween(
                durationMillis = TITLE_ANIM_MS,
                delayMillis = TITLE_DELAY_MS,
                easing = LinearOutSlowInEasing,
            ),
        label = "splash_title_reveal",
    )

    // Triggers the whole sequence exactly once: LaunchedEffect(Unit) survives recomposition and
    // only reruns if GameOpeningSplash itself leaves and re-enters composition (a fresh launch).
    LaunchedEffect(Unit) { revealed = 1f }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier =
                Modifier
                    .wrapContentSize()
                    .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_chimeroid_tiny),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier =
                    Modifier
                        .size(ICON_SIZE)
                        .graphicsLayer {
                            val scale = MIN_ICON_SCALE + (1f - MIN_ICON_SCALE) * iconProgress
                            scaleX = scale
                            scaleY = scale
                            alpha = iconProgress
                        },
            )

            Text(
                text = gameTitle,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            alpha = titleProgress
                            translationY = (1f - titleProgress) * TITLE_SLIDE_PX
                        },
            )

            CircularProgressIndicator(
                modifier =
                    Modifier
                        .size(SPINNER_SIZE)
                        .graphicsLayer { alpha = titleProgress },
                strokeWidth = SPINNER_STROKE,
                color = MaterialTheme.colorScheme.primary,
            )

            AnimatedVisibility(visible = !loadingMessage.isNullOrBlank()) {
                Text(
                    text = loadingMessage.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
