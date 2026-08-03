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

#include "framebufferrenderer.h"
#include "es3utils.h"
#include "../../log.h"

namespace libretrodroid {

FramebufferRenderer::FramebufferRenderer(
    unsigned width,
    unsigned height,
    bool depth,
    bool stencil,
    ShaderManager::Chain shaders
) {
    this->depth = depth;
    this->stencil = stencil;
    this->width = width;
    this->height = height;
    this->shaders = std::move(shaders);

    initializeBuffers();
}

void FramebufferRenderer::onNewFrame(const void *data, unsigned width, unsigned height, size_t pitch) {
    Renderer::onNewFrame(data, width, height, pitch);

    if (isDirty) {
        initializeBuffers();
        isDirty = false;
    }
}

void FramebufferRenderer::initializeBuffers() {
    framebuffers = ES3Utils::buildShaderPasses(width, height, shaders);

    ES3Utils::deleteFramebuffer(std::move(framebuffer));
    framebuffer = ES3Utils::createFramebuffer(
        width,
        height,
        shaders.linearTexture,
        false,
        depth,
        stencil
    );
}

uintptr_t FramebufferRenderer::getTexture() {
    return framebuffer->texture;
}

uintptr_t FramebufferRenderer::getFramebuffer() {
    return framebuffer->framebuffer;
}

void FramebufferRenderer::setPixelFormat(int pixelFormat) {
    // TODO... Here we should handle 32bit framebuffers.
}

void FramebufferRenderer::updateRenderedResolution(unsigned int width, unsigned int height) {
    if (this->width != width || this->height != height) {
        this->width = width;
        this->height = height;
        isDirty = true;

        // Whatever lastFrameSize was tracking is from the old allocation. Reset
        // it rather than let getValidContentFraction() compare a stale
        // pre-resize value against this new size for the one frame between
        // now and the next onNewFrame -- {0,0} already means "assume full"
        // there, which is the same safe behavior this renderer had before
        // getValidContentFraction() existed at all.
        lastFrameSize = std::make_pair(0u, 0u);
    }
}

bool FramebufferRenderer::rendersInVideoCallback() {
    return true;
}

std::pair<float, float> FramebufferRenderer::getValidContentFraction() {
    // lastFrameSize (set in Renderer::onNewFrame, called from ours above) is
    // the width/height the core actually passed to retro_video_refresh_t this
    // frame -- RETRO_HW_FRAME_BUFFER_VALID cores like flycast still pass real
    // width/height values even though `data` itself is just a sentinel, so
    // this is accurate for them too. width/height are what the buffer is
    // currently allocated at. Before the first frame (lastFrameSize still
    // {0,0}) or if a core ever reports something equal to or larger than the
    // allocation, this comes out to 1.0f: draw the whole thing, same as
    // before this method existed.
    if (lastFrameSize.first == 0 || lastFrameSize.second == 0 || width == 0 || height == 0) {
        return {1.0f, 1.0f};
    }

    float fractionX = static_cast<float>(lastFrameSize.first) / static_cast<float>(width);
    float fractionY = static_cast<float>(lastFrameSize.second) / static_cast<float>(height);

    return {
        std::min(fractionX, 1.0f),
        std::min(fractionY, 1.0f),
    };
}

void FramebufferRenderer::forceReinitialize() {
    // Called when the host needs the FBO to match a new geometry immediately
    // (e.g. after SET_SYSTEM_AV_INFO from a HW-accelerated core like PPSSPP).
    // Without this, get_current_framebuffer() would return the old-sized FBO
    // and the core would crash trying to render the new resolution into it.
    if (isDirty) {
        LOGD("FramebufferRenderer::forceReinitialize – rebuilding buffers (%dx%d)", width, height);
        initializeBuffers();
        isDirty = false;
    }
}

void FramebufferRenderer::setShaders(ShaderManager::Chain shaders) {
    if (shaders != this->shaders) {
        this->shaders = std::move(shaders);
        isDirty = true;
    }
}

Renderer::PassData FramebufferRenderer::getPassData(unsigned int layer) {
    PassData result;

    // Bounds are already proven by the guards below, so operator[] (unchecked)
    // is used instead of .at() (which would redo the same range check plus an
    // exception-path branch) in this per-pass, per-frame hot path.
    if (layer < framebuffers->size()) {
        const auto& fb = (*framebuffers)[layer];
        result.framebuffer = fb->framebuffer;
        result.width = fb->width;
        result.height = fb->height;
    }

    if (layer > 0 && layer < framebuffers->size() + 1) {
        result.texture = (*framebuffers)[layer - 1]->texture;
    }

    return result;
}

} //namespace libretrodroid
