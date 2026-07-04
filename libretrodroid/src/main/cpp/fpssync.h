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

#ifndef LIBRETRODROID_FPSSYNC_H
#define LIBRETRODROID_FPSSYNC_H

#include <chrono>
#include <thread>

namespace libretrodroid {

typedef std::chrono::steady_clock::time_point TimePoint;
typedef std::chrono::duration<long, std::micro> Duration;

class FPSSync {
public:
    FPSSync(double contentRefreshRate, double screenRefreshRate);
    ~FPSSync() = default;

    void reset();
    unsigned advanceFrames();
    void wait();
    double getTimeStretchFactor();
private:
    // Upper bound on retro_run() calls a single advanceFrames() catch-up can request.
    // Bounds worst-case per-call CPU cost so a stalled render thread recovers lost
    // audio/game-time over a few steps instead of spiking into a spiral of death.
    static constexpr long long MAX_FRAMES_PER_STEP = 4;

    // Upper bound (in content frames) on how much "behind schedule" backlog is kept.
    // Anything beyond this is forgiven rather than queued, so a chronic slowdown or a
    // core hang can't build unbounded catch-up debt that fast-forwards once it clears.
    static constexpr long long MAX_BACKLOG_FRAMES = 8;

    double screenRefreshRate;
    double contentRefreshRate;
    bool useVSync;
    const double FPS_TOLERANCE = 5;

    const TimePoint MIN_TIME = TimePoint::min();
    void start();

    TimePoint lastFrame = MIN_TIME;
    Duration sampleInterval;
};

}


#endif //LIBRETRODROID_FPSSYNC_H
