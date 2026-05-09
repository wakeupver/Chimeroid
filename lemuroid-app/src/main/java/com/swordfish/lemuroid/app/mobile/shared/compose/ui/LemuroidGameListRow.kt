package com.swordfish.lemuroid.app.mobile.shared.compose.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.swordfish.lemuroid.app.utils.games.GameUtils
import com.swordfish.lemuroid.lib.library.db.entity.Game

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LemuroidGameListRow(
    modifier: Modifier = Modifier,
    game: Game,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFavoriteToggle: (Boolean) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(targetValue = if (isPressed) 0.985f else 1f, animationSpec = tween(100), label = "row_scale")
    val context = LocalContext.current
    val fallbackPainter = rememberDrawablePainter(remember(game) { CoverUtils.getFallbackDrawable(game) })
    val subtitle = remember(game.id) { GameUtils.getGameSubtitle(context, game) }

    Surface(
        modifier = modifier.fillMaxWidth().graphicsLayer(scaleX = scale, scaleY = scale)
            .combinedClickable(interactionSource = interactionSource, indication = null, onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp, shadowElevation = 0.dp,
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 9.dp), verticalAlignment = Alignment.Top) {
            Box(modifier = Modifier.width(52.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))) {
                AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(game.coverFrontUrl).build(),
                    contentDescription = game.title, modifier = Modifier.fillMaxSize(),
                    fallback = fallbackPainter, error = fallbackPainter, contentScale = ContentScale.FillHeight)
            }
            Column(modifier = Modifier.weight(1f).padding(start = 10.dp).heightIn(min = 78.dp)) {
                Text(game.title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (subtitle.isNotBlank())
                    Text(subtitle, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box(modifier = Modifier.width(40.dp).heightIn(min = 40.dp).align(Alignment.CenterVertically), contentAlignment = Alignment.Center) {
                FavoriteToggle(isToggled = game.isFavorite, onFavoriteToggle = onFavoriteToggle)
            }
        }
    }
}
