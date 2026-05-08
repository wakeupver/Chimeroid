/*
 *     Copyright (C) 2019  Filippo Scognamiglio
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

#include <cmath>
#include "fpssync.h"
#include "log.h"

namespace libretrodroid {

unsigned FPSSync::advanceFrames() {
    if (useVSync) return 1;

    if (lastFrame == MIN_TIME) {
        start();
        return 1;
    }

    auto now = std::chrono::steady_clock::now();

    // If we haven't reached the next frame deadline yet, tell the caller to skip
    // retro_run() this vsync.  wait() will then sleep until the deadline so the
    // next onDrawFrame() call arrives right on time.
    // This is the key fix for the "double speed" bug on 30 fps games:
    // Without this guard, std::max(..., 1) forced one retro_run() per vsync even
    // when the frame budget hadn't expired — doubling the effective step rate for
    // a 30 fps core on a 60 Hz display.
    if (now < lastFrame) return 0;

    auto elapsed  = now - lastFrame;
    auto frames   = elapsed / sampleInterval;   // integer division, >= 1 here
    if (frames < 1) frames = 1;                 // guard for rounding edge case
    if (frames > 2) frames = 2;                 // cap: prevent spiral-of-death

    lastFrame = lastFrame + sampleInterval * frames;
    return static_cast<unsigned>(frames);
}

FPSSync::FPSSync(double contentRefreshRate, double screenRefreshRate) {
    this->contentRefreshRate = contentRefreshRate;
    this->screenRefreshRate = screenRefreshRate;

    // Compute the best EGL swap interval: how many screen vsyncs to hold per core frame.
    //   120 Hz screen + 60 fps core → 2  (onDrawFrame fires at 60 Hz if driver honors it)
    //   90 Hz  screen + 30 fps core → 3
    //   60 Hz  screen + 60 fps core → 1
    //   60 Hz  screen + 30 fps core → 2
    this->swapInterval = std::max(1, (int)std::round(screenRefreshRate / contentRefreshRate));

    // Effective display cadence assuming the driver honors the swap interval.
    this->effectiveScreenRate = screenRefreshRate / swapInterval;

    // IMPORTANT: only trust vsync-based pacing (useVSync=true) for the exact 1:1 case
    // (swapInterval == 1).  Many Android GPU drivers (Adreno, Mali, PowerVR) silently
    // ignore eglSwapInterval(N) for N > 1 and continue delivering onDrawFrame at the
    // full screen refresh rate.  If useVSync=true while onDrawFrame still fires at e.g.
    // 60 Hz for a 30 fps core, advanceFrames() returns 1 every call → retro_run() is
    // called 60×/sec → game runs at 2× speed.
    //
    // For swapInterval > 1 we still call eglSwapInterval(N) (see applyEGLSwapInterval)
    // as a CPU/power optimization hint.  Software pacing (useVSync=false) then handles
    // both outcomes correctly:
    //   • Driver honors N: onDrawFrame arrives at effectiveScreenRate Hz, wait() deadline
    //     is already past → immediate return, zero extra spin.
    //   • Driver ignores N: onDrawFrame arrives at screenRefreshRate Hz, advanceFrames()
    //     returns 0 on "off" ticks and wait() sleeps to the deadline → correct fps.
    this->useVSync = (swapInterval == 1) &&
                     (std::abs(contentRefreshRate - screenRefreshRate) < FPS_TOLERANCE);

    this->sampleInterval = std::chrono::microseconds((long)((1000000L / contentRefreshRate)));
    reset();
}

void FPSSync::start() {
    LOGI(
        "FPSSync: core=%.3f Hz  screen=%.3f Hz  swapInterval=%d  effectiveScreen=%.3f Hz"
        "  useVSync=%d (swapInterval==1 && rate match)  sampleInterval=%ld µs",
        contentRefreshRate,
        screenRefreshRate,
        swapInterval,
        effectiveScreenRate,
        useVSync,
        (long)(1000000L / contentRefreshRate)
    );
    lastFrame = std::chrono::steady_clock::now();
}

void FPSSync::reset() {
    lastFrame = MIN_TIME;
}

double FPSSync::getTimeStretchFactor() {
    // useVSync=true implies swapInterval==1, so effectiveScreenRate == screenRefreshRate.
    // The stretch factor adjusts audio pitch/speed to compensate for any screen/core
    // rate mismatch (e.g. 59.94 Hz screen with a 60.0 fps core).
    return useVSync ? contentRefreshRate / screenRefreshRate : 1.0;
}

void FPSSync::wait() {
    if (useVSync) return;
    // Hybrid approach: sleep until ~1 ms before deadline, then busy-spin.
    // This eliminates the OS scheduler jitter (~1–3 ms) that causes frame drops
    // while avoiding a full busy-spin that would burn the CPU.
    constexpr auto SPIN_THRESHOLD = std::chrono::microseconds(800);
    const auto deadline = lastFrame;
    const auto sleepUntil = deadline - SPIN_THRESHOLD;
    std::this_thread::sleep_until(sleepUntil);
    // Busy-spin the last ~800 µs for precise wakeup
    while (std::chrono::steady_clock::now() < deadline) {
        // Yield hint: reduces power and memory-bus pressure in a tight spin loop.
        // Use the ARM YIELD hint on ARM/AArch64 (where it's a real instruction),
        // fall back to C++ yield on other architectures.
#if defined(__arm__) || defined(__aarch64__)
        __builtin_arm_yield();
#else
        std::this_thread::yield();
#endif
    }
}

} //namespace libretrodroid
