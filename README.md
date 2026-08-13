# Chimeroid

A modern Android emulator built on LibretroDroid.

## Modules

| Module | Description |
|--------|-------------|
| `app` | Application module: Jetpack Compose UI, game loading, Room DB, DI, storage, touch controller UI, ROM metadata (libretro-db), and the `free` flavor source set (core updater, save sync) |
| `libretrodroid` | Native libretro bridge (C++/JNI) |

## Application ID

`com.swordfish.chimeroid`

## Build

```bash
./gradlew :app:assembleFreeDebug
```

## License

GPL v3 — see [COPYING](COPYING)
