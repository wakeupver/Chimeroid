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

#ifndef LIBRETRODROID_VIDEO_H
#define LIBRETRODROID_VIDEO_H

#include <GLES3/gl3.h>
#include <optional>
#include <array>

#include "renderers/renderer.h"
#include "shadermanager.h"
#include "utils/rect.h"
#include "immersivemode.h"
#include "videolayout.h"

namespace libretrodroid {

class Video {
public:

    struct RenderingOptions {
        bool hardwareAccelerated = false;
        unsigned int width;
        unsigned int height;
        bool useDepth;
        bool useStencil;
        int openglESVersion;
        int pixelFormat;
    };

    struct ShaderChainEntry {
        GLint gProgram = 0;
        GLint gvPositionHandle = 0;
        GLint gvCoordinateHandle = 0;
        GLint gTextureHandle = 0;
        GLint gPreviousPassTextureHandle = 0;
        GLint gScreenDensityHandle = 0;
        GLint gTextureSizeHandle = 0;
    };

    Video(
        RenderingOptions renderingOptions,
        ShaderManager::Config shaderConfig,
        bool bottomLeftOrigin,
        float rotation,
        bool skipDuplicateFrames,
        bool immersiveMode,
        Rect viewportRect,
        ImmersiveMode::Config immersiveModeConfig
    );

    ~Video();

    VideoLayout& getLayout() { return videoLayout; }

    void updateAspectRatio(float aspectRatio);
    void updateScreenSize(unsigned screenWidth, unsigned screenHeight);
    void updateViewportSize(Rect viewportRect);
    void updateRendererSize(unsigned width, unsigned height);
    void updateRotation(float rotation);
    void updateShaderType(ShaderManager::Config shaderConfig);

    // Force the renderer to immediately recreate its GL buffers if a resize
    // is pending. Must be called (and hw_context_reset signalled) whenever a
    // HW-accelerated core triggers SET_SYSTEM_AV_INFO, so that the next call
    // to getCurrentFramebuffer() returns a correctly-sized FBO.
    void recreateRenderer();

    void renderFrame();

    void onNewFrame(const void *data, unsigned width, unsigned height, size_t pitch);

    uintptr_t getCurrentFramebuffer() {
        return renderer->getFramebuffer();
    };

    bool rendersInVideoCallback() {
        return renderer->rendersInVideoCallback();
    }

    // ── Dual-screen support (NDS / 3DS) ─────────────────────────────────────
    // Enables a two-pass render: primary panel gets the top UV slice,
    // secondary panel gets the bottom UV slice.  Viewport coords are 0-1
    // fractions of the GL surface.  UV coords are 0-1 fractions of the
    // combined game texture (Y=0 is visual top for SW cores, Y=0 bottom for HW).
    struct DualScreenCfg {
        bool enabled                    = false;
        // Panel viewport fractions (x, y, w, h) relative to the GL surface
        float primaryVpX                = 0.f;
        float primaryVpY                = 0.f;
        float primaryVpW                = 1.f;
        float primaryVpH                = 0.5f;
        float secondaryVpX              = 0.f;
        float secondaryVpY              = 0.5f;
        float secondaryVpW              = 1.f;
        float secondaryVpH              = 0.5f;
        // UV crop rectangles for each panel
        float primaryUVxMin             = 0.f;
        float primaryUVyMin             = 0.f;
        float primaryUVxMax             = 1.f;
        float primaryUVyMax             = 0.5f;
        float secondaryUVxMin           = 0.f;
        float secondaryUVyMin           = 0.5f;
        float secondaryUVxMax           = 1.f;
        float secondaryUVyMax           = 1.f;
    };

    void setDualScreenConfig(DualScreenCfg cfg);

private:
    void updateProgram();

    float getScreenDensity();
    float getTextureWidth();
    float getTextureHeight();

    void initializeRenderer(RenderingOptions renderingOptions);

    // Helper: draw one quad using given vertex positions and UV coordinates.
    // Vertices are 12 floats (6 NDC xy pairs). UVs are 12 floats (6 st pairs).
    void drawQuadPass(const std::array<float, 12>& vertices,
                      const std::array<float, 12>& uvs,
                      const ShaderChainEntry&       shader);

    // Inner helper: upload vertices+uvs to quadVbo, set attrib pointers, draw 6
    // vertices, then disable attribs and unbind the VBO.  Caller owns program
    // binding, uniform setup, and texture bind/unbind.
    void uploadAndDraw(const std::array<float, 12>& vertices,
                       const std::array<float, 12>& uvs,
                       GLint posHandle,
                       GLint coordHandle);

    // Compute the UV quad (12 floats) for a crop rect [xMin,yMin,xMax,yMax].
    // Handles bottomLeftOrigin Y-flip.
    std::array<float, 12> buildUVQuad(float xMin, float yMin,
                                      float xMax, float yMax) const;

private:
    ShaderManager::Config requestedShaderConfig = ShaderManager::Config {
        ShaderManager::Type::SHADER_DEFAULT
    };
    std::optional<ShaderManager::Config> loadedShaderType = std::nullopt;

    bool isDirty = false;
    bool skipDuplicateFrames = false;

    std::vector<ShaderChainEntry> shadersChain;

    bool immersiveModeEnabled = false;
    ImmersiveMode immersiveMode;
    VideoLayout videoLayout;

    // Secondary layout used only in dual-screen mode.
    VideoLayout secondaryLayout;

    Renderer* renderer;

    // VBO used for rendering quads. Prevents GLES 3.0 issues where a raw
    // float* passed to glVertexAttribPointer is misinterpreted as a VBO
    // offset when a foreign VBO is still bound from a HW-accelerated core.
    GLuint quadVbo = 0;
    bool useES3    = false;

    bool      bottomLeftOrigin = false;
    DualScreenCfg dualCfg;
};

}

#endif //LIBRETRODROID_VIDEO_H
