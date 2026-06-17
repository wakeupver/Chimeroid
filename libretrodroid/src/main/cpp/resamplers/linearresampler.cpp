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

// Reset inter-callback state.  Call after an underrun or silence gap so the
// next resample() doesn't try to interpolate from stale samples.
void LinearResampler::reset() {
    lastL = 0;
    lastR = 0;
    savedPhase = 0.0;
}

// Stateful linear-interpolation resampler.
//
// The original implementation restarted outputTime = 0 on every callback,
// meaning the very first output sample was always exactly source[0].  When
// the last sample of the previous block differed from source[0] of the new
// block, that produced a hard step discontinuity → audible crackle.
//
// Fix: maintain savedPhase (fractional phase carry-over, typically in (-1, 0])
// and lastL/lastR (last stereo frame of the previous block).  When savedPhase
// is negative the first few output frames interpolate between lastL/lastR and
// source[0], eliminating the boundary discontinuity.
void LinearResampler::resample(const int16_t *source, int32_t inputFrames, int16_t *sink, int32_t sinkFrames) {
    if (inputFrames <= 0 || sinkFrames <= 0) {
        return;
    }

    const double step = static_cast<double>(inputFrames) / sinkFrames;

    // Continue from where the previous call left off.
    // savedPhase is in (-1, 0]: negative means we "owe" the consumer a fraction
    // of the last input sample from the previous block before we start on source[0].
    double phase = savedPhase;

    for (int32_t i = 0; i < sinkFrames; i++) {
        // Use std::floor so we handle negative phase values correctly.
        auto floorIdx = static_cast<int32_t>(std::floor(phase));
        const double frac = phase - floorIdx;   // always in [0, 1)
        const int32_t ceilIdx = floorIdx + 1;

        // Floor sample: index -1 uses the last sample saved from the previous block.
        int16_t s0L, s0R;
        if (floorIdx < 0) {
            s0L = lastL;
            s0R = lastR;
        } else {
            s0L = source[floorIdx * 2];
            s0R = source[floorIdx * 2 + 1];
        }

        // Ceil sample: clamp to the last valid frame so we never over-read.
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

    // Save the last input frame so we can interpolate across the next boundary.
    lastL = source[(inputFrames - 1) * 2];
    lastR = source[(inputFrames - 1) * 2 + 1];

    // savedPhase for the next call = how far past the end of this block we are.
    // For an exact-ratio conversion this will be exactly 0.  For other ratios
    // it will be a small negative number in (-1, 0).
    savedPhase = phase - inputFrames;

    // Guard against accumulation drift from ratio changes across callbacks.
    if (savedPhase > 0.0)  savedPhase = 0.0;
    if (savedPhase < -1.0) savedPhase = -1.0;
}

} //namespace libretrodroid
