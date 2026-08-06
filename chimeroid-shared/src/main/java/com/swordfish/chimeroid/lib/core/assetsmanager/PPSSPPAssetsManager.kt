package com.swordfish.chimeroid.lib.core.assetsmanager

import android.net.Uri

class PPSSPPAssetsManager : ZipAssetsManager() {
    override val assetsFolderName = PPSSPP_ASSETS_FOLDER_NAME
    override val assetsUrl: Uri = PPSSPP_ASSETS_URL
    override val assetsVersion = PPSSPP_ASSETS_VERSION
    override val assetsVersionKey = PPSSPP_ASSETS_VERSION_KEY

    companion object {
        const val PPSSPP_ASSETS_VERSION = "1.17.0"

        val PPSSPP_ASSETS_URL: Uri =
            Uri.parse("https://github.com/wakeupver/Cores")
                .buildUpon()
                .appendEncodedPath("raw/$PPSSPP_ASSETS_VERSION/assets/ppsspp.zip")
                .build()

        const val PPSSPP_ASSETS_VERSION_KEY = "ppsspp_assets_version_key"

        const val PPSSPP_ASSETS_FOLDER_NAME = "PPSSPP"
    }
}
