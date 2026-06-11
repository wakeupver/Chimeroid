package com.swordfish.chimeroid.app.shared.systems

import android.content.Context
import com.swordfish.chimeroid.lib.library.MetaSystemID

data class MetaSystemInfo(val metaSystem: MetaSystemID, val count: Int) {
    fun getName(context: Context) = context.resources.getString(metaSystem.titleResId)
}
