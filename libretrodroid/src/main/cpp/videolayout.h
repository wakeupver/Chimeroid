/*
 *     Copyright (C) 2025  Filippo Scognamiglio
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

#ifndef LIBRETRODROID_VIDEOLAYOUT_H
#define LIBRETRODROID_VIDEOLAYOUT_H

#include <array>

#include "utils/rect.h"

namespace libretrodroid {

class VideoLayout {
public:
    VideoLayout(bool bottomLeftOrigin, float rotation, Rect viewportRect);

    void updateAspectRatio(float aspectRatio);

    // See Renderer::getValidContentFraction(): narrows textureCoordinates so
    // only the actually-painted fraction of an oversized buffer gets sampled,
    // instead of always assuming the whole texture is valid. No-op when both
    // fractions are 1 (every renderer/core except FramebufferRenderer with a
    // core that reports max_width/height bigger than what it currently
    // renders), so this doesn't change anything for a system that doesn't
    // need it.
    void updateValidContentFraction(float fractionX, float fractionY);

    void updateScreenSize(unsigned screenWidth, unsigned screenHeight);

    void updateViewportSize(Rect viewportRect);

    void updateRotation(float rotation);

    std::array<float, 12>& getForegroundVertices() { return foregroundVertices; }
    std::array<float, 12>& getBackgroundVertices() { return backgroundVertices; }
    std::array<float, 12>& getFramebufferVertices() { return framebufferVertices; }
    std::array<float, 12>& getTextureCoordinates() { return textureCoordinates; }
    std::array<float, 4>& getRelativeForegroundBounds() { return relativeForegroundBounds; }

    int getScreenWidth() { return screenWidth; }

    int getScreenHeight() { return screenHeight; }

    std::pair<float, float> getRelativePosition(float touchX, float touchY);

    /**
     * Like getRelativePosition but clamps to [0,1] when the touch is within the
     * panel's NDC bounds even if it falls in a letterbox/pillarbox dead-zone.
     * Returns (-10,-10) only when the touch is entirely outside this panel
     * (e.g., the user touched the primary/top screen).
     *
     * Used for dual-screen systems so the full bottom panel is touchable.
     */
    std::pair<float, float> getRelativePositionClamped(float touchX, float touchY);

private:
    void updateBuffers();

    void updateForegroundVertices();

    void updateTextureCoordinates();

    void updateBackgroundVertices();

    void updateRelativeForegroundBounds();

    // Shared by getRelativePosition/getRelativePositionClamped: the NDC
    // bounding box of foregroundVertices (x as-is, y sign-flipped so "down"
    // is positive), used to normalize a touch point to [0,1] game space.
    struct Bounds { float xMin, xMax, yMin, yMax; };
    Bounds computeForegroundBounds() const;

private:
    std::array<float, 12> foregroundVertices = {
        -1.0F,
        -1.0F,

        -1.0F,
        +1.0F,

        +1.0F,
        -1.0F,

        +1.0F,
        -1.0F,

        -1.0F,
        +1.0F,

        +1.0F,
        +1.0F,
    };

    std::array<float, 12> textureCoordinates {
        0.0F,
        0.0F,

        0.0F,
        1.0F,

        1.0F,
        0.0F,

        1.0F,
        0.0F,

        0.0F,
        1.0F,

        1.0F,
        1.0F,
    };

    std::array<float, 12> backgroundVertices = {
        -1.0F,
        -1.0F,

        -1.0F,
        +1.0F,

        +1.0F,
        -1.0F,

        +1.0F,
        -1.0F,

        -1.0F,
        +1.0F,

        +1.0F,
        +1.0F,
    };

    std::array<float, 12> framebufferVertices = {
        -1.0F,
        -1.0F,

        -1.0F,
        +1.0F,

        +1.0F,
        -1.0F,

        +1.0F,
        -1.0F,

        -1.0F,
        +1.0F,

        +1.0F,
        +1.0F,
    };

    std::array<float, 4> relativeForegroundBounds = {
        +0.0F,
        +0.0F,
        +1.0F,
        +1.0F,
    };

    bool bottomLeftOrigin = false;
    float rotation = 0.0F;
    float aspectRatio = 1;
    Rect viewportRect = Rect(0.0F, 0.0F, 1.0F, 1.0F);

    float validContentFractionX = 1.0F;
    float validContentFractionY = 1.0F;

    unsigned screenWidth = 0;
    unsigned screenHeight = 0;
};

} // namespace libretrodroid

#endif //LIBRETRODROID_VIDEOLAYOUT_H
