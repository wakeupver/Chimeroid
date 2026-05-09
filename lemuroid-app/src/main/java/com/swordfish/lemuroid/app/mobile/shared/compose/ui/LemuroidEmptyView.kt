package com.swordfish.lemuroid.app.mobile.shared.compose.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.swordfish.lemuroid.R

@Composable
fun LemuroidEmptyView(
    modifier: Modifier = Modifier,
    text: String = stringResource(id = R.string.empty_view_default),
) {
    Column(
        modifier              = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment   = Alignment.CenterHorizontally,
        verticalArrangement   = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(listOf(NeonOrange, NeonCyan)),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector        = Icons.Rounded.SportsEsports,
                contentDescription = null,
                tint               = MaterialTheme.colorScheme.primary,
                modifier           = Modifier.size(36.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text      = text,
            style     = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
