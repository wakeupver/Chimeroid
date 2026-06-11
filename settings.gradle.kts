@file:Suppress("ktlint")

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
    }
}

include(
    ":libretrodroid",
    ":chimeroid-util",
    ":chimeroid-shared",
    ":chimeroid-touchinput",
    ":chimeroid-app",
    ":chimeroid-metadata-libretro-db",
    ":chimeroid-app-ext-free",
)
