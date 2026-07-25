package com.swordfish.chimeroid.app.mobile.feature.input

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.compose.setContent
import androidx.compose.foundation.focusable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.onGloballyPositioned
import com.swordfish.chimeroid.app.mobile.shared.compose.ui.AppTheme
import com.swordfish.chimeroid.app.shared.input.InputDeviceManager
import com.swordfish.chimeroid.app.shared.input.ShortcutBindingUpdater
import com.swordfish.chimeroid.lib.android.RetrogradeActivity
import timber.log.Timber
import top.yukonga.miuix.kmp.window.WindowDialog
import javax.inject.Inject

class GamePadShortcutBindingActivity : RetrogradeActivity() {
    @Inject
    lateinit var inputDeviceManager: InputDeviceManager

    private lateinit var shortcutBindingUpdater: ShortcutBindingUpdater

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        shortcutBindingUpdater = ShortcutBindingUpdater(inputDeviceManager, intent)

        setContent {
            AppTheme {
                val focusRequester = remember { FocusRequester() }

                WindowDialog(
                    show = true,
                    modifier =
                        Modifier
                            .focusRequester(focusRequester)
                            .focusable()
                            .onKeyEvent { handleKeyEvent(it.nativeKeyEvent) }
                            .onGloballyPositioned { focusRequester.requestFocus() },
                    title = shortcutBindingUpdater.getTitle(applicationContext),
                    summary = shortcutBindingUpdater.getMessage(applicationContext),
                    onDismissRequest = { finish() },
                ) {}
            }
        }
    }

    private fun handleKeyEvent(event: KeyEvent): Boolean {
        Timber.i("Received key event: $event")
        val result = shortcutBindingUpdater.handleKeyEvent(event)

        if (event.action == KeyEvent.ACTION_UP && result) {
            finish()
        }

        return result
    }

    @dagger.Module
    abstract class Module
}
