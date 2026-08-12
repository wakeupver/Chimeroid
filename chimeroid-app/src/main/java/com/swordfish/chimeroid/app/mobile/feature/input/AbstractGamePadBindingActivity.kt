package com.swordfish.chimeroid.app.mobile.feature.input

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.compose.setContent
import androidx.compose.foundation.focusable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.onGloballyPositioned
import com.swordfish.chimeroid.app.mobile.shared.compose.ui.AppTheme
import com.swordfish.chimeroid.app.shared.input.KeyBindingUpdater
import com.swordfish.chimeroid.lib.android.RetrogradeActivity
import timber.log.Timber

abstract class AbstractGamePadBindingActivity : RetrogradeActivity() {
    protected lateinit var bindingUpdater: KeyBindingUpdater

    protected abstract fun createBindingUpdater(): KeyBindingUpdater

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        bindingUpdater = createBindingUpdater()

        setContent {
            AppTheme {
                val focusRequester = remember { FocusRequester() }

                AlertDialog(
                    modifier =
                        Modifier
                            .focusRequester(focusRequester)
                            .focusable()
                            .onKeyEvent { handleKeyEvent(it.nativeKeyEvent) }
                            .onGloballyPositioned { focusRequester.requestFocus() },
                    title = { Text(text = bindingUpdater.getTitle(applicationContext)) },
                    text = { Text(text = bindingUpdater.getMessage(applicationContext)) },
                    onDismissRequest = { finish() },
                    confirmButton = {},
                )
            }
        }
    }

    private fun handleKeyEvent(event: KeyEvent): Boolean {
        Timber.i("Received key event: $event")
        val result = bindingUpdater.handleKeyEvent(event)

        if (event.action == KeyEvent.ACTION_UP && result) {
            finish()
        }

        return result
    }
}
