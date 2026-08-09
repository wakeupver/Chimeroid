package com.swordfish.chimeroid.app.shared.game

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.swordfish.chimeroid.app.shared.game.viewmodel.GameViewModelRetroGameView
import kotlinx.coroutines.delay

private const val SPLASH_EXIT_MS = 220

@Composable
fun BaseGameScreen(
    viewModel: BaseGameScreenViewModel,
    gameTitle: String,
    gameScreen: @Composable (BaseGameScreenViewModel) -> Unit,
) {
    val gameState =
        viewModel.getGameState()
            .collectAsState(GameViewModelRetroGameView.GameState.Uninitialized)
            .value

    val isGameReady =
        gameState is GameViewModelRetroGameView.GameState.Loaded ||
            gameState is GameViewModelRetroGameView.GameState.Ready

    var splashMinDurationElapsed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(SPLASH_MIN_VISIBLE_MS.toLong())
        splashMinDurationElapsed = true
    }

    val readyToShowGame = isGameReady && splashMinDurationElapsed

    Box(modifier = Modifier.fillMaxSize()) {
        if (readyToShowGame) {
            gameScreen(viewModel)
        }

        AnimatedVisibility(
            visible = !readyToShowGame,
            modifier = Modifier.fillMaxSize(),
            enter = EnterTransition.None,
            exit = fadeOut(tween(SPLASH_EXIT_MS)),
        ) {
            val loadingMessage = (gameState as? GameViewModelRetroGameView.GameState.Loading)?.message
            GameOpeningSplash(gameTitle = gameTitle, loadingMessage = loadingMessage)
        }
    }
}
