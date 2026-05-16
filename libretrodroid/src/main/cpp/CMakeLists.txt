cmake_minimum_required(VERSION 3.22.1)
project(libretrodroid)

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
# aaudio replaces oboe; GLESv3, EGL, android, log are NDK-provided
target_link_libraries(libretrodroid
    android
    log
    EGL
    GLESv3
    aaudio      # AAudio: Android 8.1+ (API 27). Replaces Oboe.
    OpenSLES    # Fallback path via AAudio's internal routing - optional safety
)
