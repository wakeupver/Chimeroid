package com.swordfish.chimeroid.app.mobile.feature.game

import androidx.compose.runtime.Composable
import com.swordfish.chimeroid.app.mobile.feature.gamemenu.GameMenuActivity
import com.swordfish.chimeroid.app.shared.game.BaseGameActivity
import com.swordfish.chimeroid.app.shared.game.BaseGameScreenViewModel

class GameActivity : BaseGameActivity() {
    @Composable
    override fun GameScreen(viewModel: BaseGameScreenViewModel) {
        MobileGameScreen(viewModel)
    }

    override fun getDialogClass() = GameMenuActivity::class.java
}
