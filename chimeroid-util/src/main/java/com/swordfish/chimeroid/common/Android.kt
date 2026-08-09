package com.swordfish.chimeroid.common

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast

fun Bundle?.dump(): String {
    if (this == null) return "null"

    val builder = StringBuilder("Extras:\n")
    keySet()
        .toSet()
        .forEach { key ->
            @Suppress("DEPRECATION")
            builder.append(key).append(": ").append(get(key)).append("\n")
        }
    return builder.toString()
}

fun Activity.displayToast(
    string: String,
    length: Int = Toast.LENGTH_SHORT,
) {
    Toast.makeText(this, string, length).show()
}

fun Activity.displayToast(
    stringId: Int,
    length: Int = Toast.LENGTH_SHORT,
) {
    Toast.makeText(this, stringId, length).show()
}

fun Activity.overrideFadeTransition(open: Boolean) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        val type = if (open) Activity.OVERRIDE_TRANSITION_OPEN else Activity.OVERRIDE_TRANSITION_CLOSE
        overrideActivityTransition(type, android.R.anim.fade_in, android.R.anim.fade_out)
    } else {
        @Suppress("DEPRECATION")
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
}

fun Context.animationDuration(): Int {
    return resources.getInteger(android.R.integer.config_mediumAnimTime)
}

fun Context.shortAnimationDuration(): Int {
    return resources.getInteger(android.R.integer.config_shortAnimTime)
}

fun Context.longAnimationDuration(): Int {
    return resources.getInteger(android.R.integer.config_longAnimTime)
}

fun Context.displayDetailsSettingsScreen() {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
    intent.data = Uri.fromParts("package", packageName, null)
    startActivity(intent)
}
