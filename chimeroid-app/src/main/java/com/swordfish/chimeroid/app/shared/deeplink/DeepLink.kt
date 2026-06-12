package com.swordfish.chimeroid.app.shared.deeplink

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.swordfish.chimeroid.lib.library.db.entity.Game

object DeepLink {
    private fun uriForGame(
        appContext: Context,
        game: Game,
    ): Uri {
        return Uri.parse("chimeroid://${appContext.packageName}/play-game/id/${game.id}")
    }

    fun launchIntentForGame(
        appContext: Context,
        game: Game,
    ) = Intent(Intent.ACTION_VIEW, uriForGame(appContext, game))
}
