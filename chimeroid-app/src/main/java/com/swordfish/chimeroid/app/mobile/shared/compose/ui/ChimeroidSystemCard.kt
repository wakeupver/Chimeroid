package com.swordfish.chimeroid.app.mobile.shared.compose.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.swordfish.chimeroid.R
import com.swordfish.chimeroid.app.shared.systems.MetaSystemInfo

private const val PressedScale = 0.94f
private const val RestScale = 1f

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ChimeroidSystemCard(
    modifier: Modifier = Modifier,
    system: MetaSystemInfo,
    onClick: () -> Unit,
) {
    val context = LocalContext.current

    val title = remember(system.metaSystem.titleResId) {
        system.getName(context)
    }

    val countText = remember(system.count) {
        system.count.toString()
    }

    val countLabel = remember(system.metaSystem.titleResId, system.count) {
        context.getString(R.string.system_grid_details, system.count.toString())
    }

    // Expressive press feedback: MaterialTheme.motionScheme's spatial spec — the real
    // M3 Expressive spring token (MotionScheme.expressive(), wired up app-wide in
    // AppTheme) — instead of a hand-tuned spring(), so every bouncy interaction in the
    // app shares one physics tuning. Modifier.scale is a draw-only transform (no
    // relayout), so animating it is essentially free outside of the press itself.
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) PressedScale else RestScale,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "systemCardScale",
    )

    Card(
        modifier = modifier.scale(scale),
        shape = MaterialTheme.shapes.extraLarge,
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp, pressedElevation = 1.dp),
        interactionSource = interactionSource,
        onClick = onClick,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Image area with count badge overlay
            Box(modifier = Modifier.fillMaxWidth()) {
                ChimeroidSystemImage(system)

                // Game count — M3 Badge instead of a hand-rolled Surface+Text so
                // color/contrast comes from the theme (tertiaryContainer) rather than
                // a hardcoded black/white overlay that ignores light/dark and
                // dynamic-color theming.
                Badge(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                ) {
                    Text(
                        text = countText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            // Title + subtitle
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 9.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = countLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
