package com.swordfish.chimeroid.lib.core.assetsmanager

import android.net.Uri

class DolphinAssetsManager : ZipAssetsManager() {
    override val assetsFolderName = DOLPHIN_ASSETS_FOLDER_NAME
    override val assetsUrl: Uri = DOLPHIN_ASSETS_URL
    override val assetsVersion = DOLPHIN_ASSETS_VERSION
    override val assetsVersionKey = DOLPHIN_ASSETS_VERSION_KEY

    companion object {
        const val DOLPHIN_ASSETS_VERSION = "1.18.0"

        val DOLPHIN_ASSETS_URL: Uri =
            Uri.parse("https://github.com/wakeupver/Cores")
                .buildUpon()
                .appendEncodedPath("raw/$DOLPHIN_ASSETS_VERSION/assets/dolphin-emu.zip")
                .build()

        const val DOLPHIN_ASSETS_VERSION_KEY = "dolphin_assets_version_key"

        // Must be exactly "dolphin-emu": dolphin-libretro hardcodes
        // <system_dir>/dolphin-emu/Sys/... internally, so any other folder name means the
        // core silently runs without its compatibility DB, GC fonts, or DSP ROMs.
        const val DOLPHIN_ASSETS_FOLDER_NAME = "dolphin-emu"
    }
}
