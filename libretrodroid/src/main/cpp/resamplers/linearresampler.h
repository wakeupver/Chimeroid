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

#ifndef LIBRETRODROID_LINEARRESAMPLER_H
#define LIBRETRODROID_LINEARRESAMPLER_H

#include "resampler.h"

namespace libretrodroid {

class LinearResampler : public Resampler {
public:
    void resample(const int16_t *source, int32_t inputFrames, int16_t *sink, int32_t sinkFrames) override;
    void reset() override;
    LinearResampler() = default;
    virtual ~LinearResampler() = default;

private:
    // Cross-callback interpolation state
    // lastL/lastR: last stereo sample from the previous resample() call, used to
    //              smooth the boundary when savedPhase < 0 (i.e. the next output
    //              should still partially come from the previous block's last frame).
    int16_t lastL = 0;
    int16_t lastR = 0;
    // savedPhase: fractional input-sample offset to carry into the next call.
    //             Typically very close to 0; lives in the range (-1, 0].
    double savedPhase = 0.0;
};

} //namespace libretrodroid

#endif //LIBRETRODROID_LINEARRESAMPLER_H
