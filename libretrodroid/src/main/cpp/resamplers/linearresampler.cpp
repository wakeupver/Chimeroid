/*
 *     Copyright (C) 2020  Filippo Scognamiglio
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
#include <algorithm>
#include "linearresampler.h"

namespace libretrodroid {

void LinearResampler::reset() {
    lastL = 0;
    lastR = 0;
    savedPhase = 0.0;
}

void LinearResampler::resample(const int16_t *source, int32_t inputFrames, int16_t *sink, int32_t sinkFrames) {
    if (inputFrames <= 0 || sinkFrames <= 0) {
        return;
    }

    const double step = static_cast<double>(inputFrames) / sinkFrames;

    double phase = savedPhase;

    for (int32_t i = 0; i < sinkFrames; i++) {

        auto floorIdx = static_cast<int32_t>(std::floor(phase));
        const double frac = phase - floorIdx;
        const int32_t ceilIdx = floorIdx + 1;

        int16_t s0L, s0R;
        if (floorIdx < 0) {
            s0L = lastL;
            s0R = lastR;
        } else {
            s0L = source[floorIdx * 2];
            s0R = source[floorIdx * 2 + 1];
        }

        int16_t s1L, s1R;
        if (ceilIdx >= inputFrames) {
            s1L = source[(inputFrames - 1) * 2];
            s1R = source[(inputFrames - 1) * 2 + 1];
        } else {
            s1L = source[ceilIdx * 2];
            s1R = source[ceilIdx * 2 + 1];
        }

        *sink++ = static_cast<int16_t>(s0L * (1.0 - frac) + s1L * frac);
        *sink++ = static_cast<int16_t>(s0R * (1.0 - frac) + s1R * frac);

        phase += step;
    }

    lastL = source[(inputFrames - 1) * 2];
    lastR = source[(inputFrames - 1) * 2 + 1];

    savedPhase = phase - inputFrames;

    if (savedPhase > 0.0)  savedPhase = 0.0;
    if (savedPhase < -1.0) savedPhase = -1.0;
}

}
