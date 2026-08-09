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

#include "renderer.h"

#include <cstdint>

namespace libretrodroid {

void Renderer::onNewFrame(const void *data, unsigned width, unsigned height, size_t pitch) {
    lastFrameSize = std::make_pair(width, height);
}

void Renderer::unpackRGB1555InPlace(void *data, size_t pixelCount) {
    if (data == nullptr) {
        return;
    }

    auto *pixels = static_cast<uint16_t*>(data);
    for (size_t i = 0; i < pixelCount; ++i) {
        const uint16_t p = pixels[i];
        pixels[i] = (0x1Fu & p) | (((0x1Fu << 5) & p) << 1) | (((0x1Fu << 10) & p) << 1);
    }
}

}
