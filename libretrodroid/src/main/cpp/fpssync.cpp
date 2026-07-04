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

#include <algorithm>
#include <cmath>
#include "fpssync.h"
#include "log.h"

namespace libretrodroid {

unsigned FPSSync::advanceFrames() {
    if (lastFrame == MIN_TIME) {
        start();
        return 1;
    }

    auto now = std::chrono::steady_clock::now();
    auto elapsed = now - lastFrame;

    // Steady state: still within one frame interval of schedule (the overwhelming
    // majority of calls, whether vsync-locked or free-running). Kept as cheap as the
    // old unconditional "return 1" while still maintaining lastFrame, so a real stall
    // is detected below instead of being invisible to this function forever.
    if (elapsed <= sampleInterval) {
        lastFrame = lastFrame + sampleInterval;
        return 1;
    }

    // We've fallen behind (GPU/CPU/thermal load, a GC pause, a slow composited frame,
    // etc). Bound the observable backlog so a chronic slowdown or a core hang can't
    // accumulate unbounded catch-up debt that would fast-forward for a long time once
    // the bottleneck clears.
    auto maxBacklog = sampleInterval * MAX_BACKLOG_FRAMES;
    if (elapsed > maxBacklog) {
        elapsed = maxBacklog;
        lastFrame = now - maxBacklog;
    }

    long long framesOwed = elapsed / sampleInterval;
    long long framesToRun = std::clamp(framesOwed, (long long) 1, MAX_FRAMES_PER_STEP);

    // Only commit the interval-time for frames we actually commit to running. Any
    // remainder beyond framesToRun stays owed - it simply resurfaces as extra elapsed
    // time on the next call instead of being discarded here. This is what lets a
    // multi-frame stutter be fully repaid (audio production catches back up to
    // real-time) over the next few steps rather than permanently starving the audio
    // FIFO below real-time, which is what caused stutter to compound under load.
    lastFrame = lastFrame + sampleInterval * framesToRun;

    return static_cast<unsigned>(framesToRun);
}

FPSSync::FPSSync(double contentRefreshRate, double screenRefreshRate) {
    this->contentRefreshRate = contentRefreshRate;
    this->screenRefreshRate = screenRefreshRate;
    this->useVSync = std::abs(contentRefreshRate - screenRefreshRate) < FPS_TOLERANCE;
    this->sampleInterval = std::chrono::microseconds((long) ((1000000L / contentRefreshRate)));
    reset();
}

void FPSSync::start() {
    LOGI("Starting game with fps %f on a screen with refresh rate %f. Using vsync: %d", contentRefreshRate, screenRefreshRate, useVSync);
    lastFrame = std::chrono::steady_clock::now();
}

void FPSSync::reset() {
    lastFrame = MIN_TIME;
}

double FPSSync::getTimeStretchFactor() {
    return useVSync ? contentRefreshRate / screenRefreshRate : 1.0;
}

void FPSSync::wait() {
    if (useVSync) return;
    std::this_thread::sleep_until(lastFrame);
}

} //namespace libretrodroid
