cmake_minimum_required(VERSION 3.22.1)
project(libretrodroid)

# ─── AAudio requires Android API 26+ ─────────────────────────────────────────
# Force the NDK to expose API-26 symbols regardless of the app's declared
# minSdkVersion. ANDROID_PLATFORM in Gradle can be overridden; setting
# CMAKE_SYSTEM_VERSION here is the authoritative per-module override.
if(ANDROID)
    if(NOT DEFINED CMAKE_SYSTEM_VERSION OR CMAKE_SYSTEM_VERSION LESS 26)
        set(CMAKE_SYSTEM_VERSION 26)
    endif()
    # Belt-and-suspenders: define the guard macro that AAudio.h checks
    add_definitions("-D__ANDROID_API__=26")
endif()

# ─── Compiler flags ───────────────────────────────────────────────────────────
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++17 -Wall")

# Required by libretro-common VFS frontend
add_definitions("-DVFS_FRONTEND -DHAVE_STRL")

# ─── libretro-common (from RetroArch) ─────────────────────────────────────────
include_directories("libretro/libretro-common/include")

set(LIBRETRO_COMMON
    libretro/libretro-common/vfs/vfs_implementation.c
    libretro/libretro-common/string/stdstring.c
    libretro/libretro-common/encodings/encoding_utf.c
    libretro/libretro-common/file/file_path.c
    libretro/libretro-common/time/rtime.c
)

# ─── Main libretrodroid shared library ────────────────────────────────────────
add_library(libretrodroid SHARED
    # JNI entry points
    libretrodroidjni.h
    libretrodroidjni.cpp

    # Core engine
    libretrodroid.h
    libretrodroid.cpp
    log.h

    # Core loader
    core.h
    core.cpp

    # Video / rendering
    video.h
    video.cpp
    videolayout.h
    videolayout.cpp
    immersivemode.h
    immersivemode.cpp
    shadermanager.h
    shadermanager.cpp

    # Renderers
    renderers/renderer.h
    renderers/renderer.cpp
    renderers/es2/imagerendereres2.h
    renderers/es2/imagerendereres2.cpp
    renderers/es3/es3utils.h
    renderers/es3/es3utils.cpp
    renderers/es3/framebufferrenderer.h
    renderers/es3/framebufferrenderer.cpp
    renderers/es3/imagerendereres3.h
    renderers/es3/imagerendereres3.cpp

    # Audio (AAudio-based, no Oboe)
    audio.h
    audio.cpp

    # Resamplers
    resamplers/resampler.h
    resamplers/linearresampler.h
    resamplers/linearresampler.cpp
    resamplers/sincresampler.h
    resamplers/sincresampler.cpp

    # Input / rumble
    input.h
    input.cpp
    rumble.h
    rumble.cpp
    rumblestate.h
    rumblestate.cpp

    # Emulation environment / callbacks
    environment.h
    environment.cpp
    fpssync.h
    fpssync.cpp
    errorcodes.h
    errorcodes.cpp

    # Utilities
    utils/javautils.h
    utils/javautils.cpp
    utils/utils.h
    utils/utils.cpp
    utils/jnistring.h
    utils/jnistring.cpp
    utils/libretrodroidexception.h
    utils/libretrodroidexception.cpp
    utils/rect.h
    utils/rect.cpp

    # Virtual file system
    vfs/vfs.h
    vfs/vfs.cpp
    vfs/vfsfile.h
    vfs/vfsfile.cpp
    vfs/fdwrapper.h
    vfs/fdwrapper.cpp

    # Microphone
    microphone/microphone.h
    microphone/microphone.cpp
    microphone/microphoneinterface.h
    microphone/microphoneinterface.cpp

    # libretro-common sources (RetroArch upstream)
    ${LIBRETRO_COMMON}
)

# ─── Android 15: support 16 KiB page size ─────────────────────────────────────
target_link_options(libretrodroid PRIVATE "-Wl,-z,max-page-size=16384")

# ─── Link libraries ───────────────────────────────────────────────────────────
target_link_libraries(libretrodroid
    android
    log
    EGL
    GLESv3
    aaudio
    OpenSLES
)
