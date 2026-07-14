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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

    // gameScreen (MobileGameScreen: touch controls, the AndroidView hosting the GL surface, etc.)
    // is heavy on its first pass. It is intentionally a plain `if`, not an animated enter: animating
    // alpha/size over that first composition is what caused the loading -> gameplay hitch, since
    // Compose would interpolate a layer while simultaneously measuring/laying out the whole screen
    // for the first time. It now simply appears at full opacity the instant readyToShowGame flips
    // (zero animation cost of its own), while only the cheap splash below — drawn after it, so on
    // top in Box z-order — animates, fading out over what's already fully rendered underneath.
    // readyToShowGame is monotonic (false -> true once: isGameReady never regresses per
    // GameViewModelRetroGameView's state machine, and the duration latch is one-shot), so
    // gameScreen is composed exactly once and never torn down again for the session.
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
