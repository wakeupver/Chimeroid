package com.swordfish.chimeroid.app.shared.game

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.swordfish.chimeroid.app.shared.game.viewmodel.GameViewModelRetroGameView
import kotlinx.coroutines.delay

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

    // Latches true SPLASH_MIN_VISIBLE_MS after this composable first appears, i.e. once, for the
    // life of the game session (BaseGameActivity.onCreate/setContent runs exactly once; GameActivity
    // survives rotation via android:configChanges, so this never re-arms on a config change). Runs
    // independently of loadGame()'s own lifecycleScope coroutine in BaseGameActivity, so it only
    // ever holds the *display* of an already-ready game back by whatever time remains -- it never
    // slows down the load itself, and adds nothing once real loading has already taken longer.
    var splashMinDurationElapsed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(SPLASH_MIN_VISIBLE_MS.toLong())
        splashMinDurationElapsed = true
    }

    val readyToShowGame = isGameReady && splashMinDurationElapsed

    // readyToShowGame only ever flips false -> true once per session (isGameReady never regresses
    // per GameViewModelRetroGameView's state machine, and splashMinDurationElapsed is a one-shot
    // latch), so this AnimatedContent never has to cope with a reverse transition: the AndroidView
    // hosting the GL surface inside gameScreen() is composed exactly once.
    AnimatedContent(
        targetState = readyToShowGame,
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
