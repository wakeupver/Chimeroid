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

#ifndef LIBRETRODROID_RENDERER_H
#define LIBRETRODROID_RENDERER_H

#include <cstdint>
#include <utility>
#include <vector>
#include <optional>

#include "../shadermanager.h"

namespace libretrodroid {

class Renderer {
public:
    struct PassData {
        std::optional<unsigned int> framebuffer = std::nullopt;
        std::optional<unsigned int> texture = std::nullopt;
        std::optional<unsigned int> width = std::nullopt;
        std::optional<unsigned int> height = std::nullopt;
    };

public:
    virtual uintptr_t getFramebuffer() = 0;
    virtual uintptr_t getTexture() = 0;
    virtual void updateRenderedResolution(unsigned width, unsigned height) = 0;
    virtual void setPixelFormat(int pixelFormat) = 0;
    virtual void onNewFrame(const void *data, unsigned width, unsigned height, size_t pitch);
    virtual bool rendersInVideoCallback() = 0;
    virtual void setShaders(ShaderManager::Chain shaders) = 0;
    virtual PassData getPassData(unsigned int layer) = 0;

    // Force immediate FBO/buffer recreation if a resize is pending.
    // Called when a HW-accelerated core signals SET_SYSTEM_AV_INFO so the new
    // framebuffer is ready before the next get_current_framebuffer() call.
    virtual void forceReinitialize() {}

    virtual ~Renderer() = default;

    // Shared by software-path image renderers (ES2/ES3) to re-pack a decoded
    // 0RGB1555 buffer in place so it can be uploaded as GL_UNSIGNED_SHORT_5_6_5.
    // Blue stays put; the 5-bit green/red fields are shifted one bit into the
    // wider 565 layout. In place, O(pixelCount), zero allocations. No-op on
    // null data so callers don't need their own guard.
    static void unpackRGB1555InPlace(void *data, size_t pixelCount);

public:
    // Unsigned to match the width/height parameters it's always derived from
    // and compared against (onNewFrame, updateRenderedResolution); avoids
    // signed/unsigned comparison warnings at every call site.
    std::pair<unsigned int, unsigned int> lastFrameSize;
};

}


#endif //LIBRETRODROID_RENDERER_H
