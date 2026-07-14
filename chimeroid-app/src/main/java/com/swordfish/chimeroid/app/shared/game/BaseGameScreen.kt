package com.swordfish.chimeroid.app.shared.game

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import com.swordfish.chimeroid.app.shared.game.viewmodel.GameViewModelRetroGameView

private const val GAME_READY_TRANSITION_MS = 220

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

    // isGameReady only ever flips false -> true once per session (GameViewModelRetroGameView's
    // GameState machine never regresses Ready/Loaded back to Loading), so this AnimatedContent
    // never has to cope with a reverse transition: the AndroidView hosting the GL surface inside
    // gameScreen() is composed exactly once, the very first time isGameReady becomes true.
    AnimatedContent(
        targetState = isGameReady,
        transitionSpec = {
            fadeIn(tween(GAME_READY_TRANSITION_MS)) togetherWith fadeOut(tween(GAME_READY_TRANSITION_MS))
        },
        modifier = Modifier.fillMaxSize(),
        label = "game_screen_ready",
    ) { ready ->
        if (ready) {
            gameScreen(viewModel)
        } else {
            val loadingMessage = (gameState as? GameViewModelRetroGameView.GameState.Loading)?.message
            GameOpeningSplash(gameTitle = gameTitle, loadingMessage = loadingMessage)
        }
    }
}
