# Chimeroid

A modern Android emulator built on LibretroDroid.

## Modules

| Module | Description |
|--------|-------------|
| `chimeroid-app` | Main application (Jetpack Compose UI, mobile) |
| `chimeroid-shared` | Shared library: game loading, Room DB, DI, storage |
| `chimeroid-util` | Common Kotlin/Android utilities |
| `chimeroid-touchinput` | Touch controller UI components |
| `chimeroid-metadata-libretro-db` | ROM metadata from libretro-db |
| `chimeroid-app-ext-free` | Free variant extension (core updater, save sync) |
| `libretrodroid` | Native libretro bridge (C++/JNI) |

## Application ID

`com.swordfish.chimeroid`

## Build

```bash
./gradlew :chimeroid-app:assembleFreeDebug
```

## License

GPL v3 — see [COPYING](COPYING)
