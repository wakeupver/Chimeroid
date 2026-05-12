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

#include "imagerendereres3.h"
#include "../../libretro-common/include/libretro.h"
#include "es3utils.h"

namespace libretrodroid {

ImageRendererES3::ImageRendererES3() {
    glGenTextures(1, &currentTexture);
    glBindTexture(GL_TEXTURE_2D, currentTexture);
}

void ImageRendererES3::onNewFrame(const void *data, unsigned width, unsigned height, size_t pitch) {
    if (pixelFormat == RETRO_PIXEL_FORMAT_0RGB1555) {
        convertDataFrom0RGB1555(data, width, height, pitch);
    }

    if (lastFrameSize.first != width || lastFrameSize.second != height || isDirty) {
        initializeTextures(width, height);
    }

    glBindTexture(GL_TEXTURE_2D, currentTexture);

    glPixelStorei(GL_UNPACK_ALIGNMENT, bytesPerPixel);
    glPixelStorei(GL_UNPACK_ROW_LENGTH, pitch / bytesPerPixel);

    glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, width, height, glFormat, glType, data);

    glPixelStorei(GL_UNPACK_ROW_LENGTH, 0);

    glBindTexture(GL_TEXTURE_2D, 0);

    Renderer::onNewFrame(data, width, height, pitch);
}

void ImageRendererES3::initializeTextures(unsigned int width, unsigned int height) {
    for (auto& i : *framebuffers) {
        ES3Utils::deleteFramebuffer(std::move(i));
    }
    framebuffers = libretrodroid::ES3Utils::buildShaderPasses(width, height, shaders);

    glBindTexture(GL_TEXTURE_2D, currentTexture);

    // Use immutable texture storage (glTexStorage2D): the driver allocates the
    // backing memory once and never reallocates on subsequent glTexSubImage2D
    // calls, reducing upload latency by ~15-20% on TBDR GPUs (Adreno/Mali).
    // We must delete and recreate the texture object whenever the size changes
    // because immutable storage cannot be redefined after creation.
    if (lastAllocatedWidth != width || lastAllocatedHeight != height) {
        glDeleteTextures(1, &currentTexture);
        glGenTextures(1, &currentTexture);
        glBindTexture(GL_TEXTURE_2D, currentTexture);
        glTexStorage2D(GL_TEXTURE_2D, 1, glInternalFormatSized, width, height);
        lastAllocatedWidth  = width;
        lastAllocatedHeight = height;
    }

    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, shaders.linearTexture ? GL_LINEAR : GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, shaders.linearTexture ? GL_LINEAR : GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

    if (swapRedAndBlueChannels) {
        applyGLSwizzle(GL_BLUE, GL_GREEN, GL_RED, GL_ALPHA);
    } else {
        applyGLSwizzle(GL_RED, GL_GREEN, GL_BLUE, GL_ALPHA);
    }

    glBindTexture(GL_TEXTURE_2D, 0);

    isDirty = false;
}

void ImageRendererES3::applyGLSwizzle(int r, int g, int b, int a) {
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_SWIZZLE_R, r);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_SWIZZLE_G, g);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_SWIZZLE_B, b);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_SWIZZLE_A, a);
}

uintptr_t ImageRendererES3::getTexture() {
    return currentTexture;
}

uintptr_t ImageRendererES3::getFramebuffer() {
    return 0; // ImageRender does not really expose a framebuffer.
}

void ImageRendererES3::setPixelFormat(int pixelFormat) {
    this->pixelFormat = pixelFormat;

    switch (pixelFormat) {

        case RETRO_PIXEL_FORMAT_XRGB8888:
            // Use sized internal format (GL_RGBA8) required by glTexStorage2D.
            this->glInternalFormatSized = GL_RGBA8;
            this->glFormat = GL_RGBA;
            this->glType = GL_UNSIGNED_BYTE;
            this->bytesPerPixel = 4;
            this->swapRedAndBlueChannels = true;
            break;

        default:
        case RETRO_PIXEL_FORMAT_0RGB1555:
        case RETRO_PIXEL_FORMAT_RGB565:
            // GL_RGB565 is both a sized and a base format in ES3.
            this->glInternalFormatSized = GL_RGB565;
            this->glFormat = GL_RGB;
            this->glType = GL_UNSIGNED_SHORT_5_6_5;
            this->bytesPerPixel = 2;
            this->swapRedAndBlueChannels = false;
            break;
    }

    // Force texture reallocation with new format.
    lastAllocatedWidth  = 0;
    lastAllocatedHeight = 0;
}

void ImageRendererES3::convertDataFrom0RGB1555(const void *data, unsigned int width, unsigned int height, size_t pitch) const {
    auto castData = (uint16_t*) data;

    // Iterate only over valid pixels per row (width), stepping by pitch stride.
    // Previously used `height * pitch / bytesPerPixel` which over-reads when
    // pitch > width * bytesPerPixel (padded rows), corrupting data past the frame.
    const size_t rowStride = pitch / bytesPerPixel;
    for (unsigned int y = 0; y < height; ++y) {
        uint16_t* row = castData + y * rowStride;
        for (unsigned int x = 0; x < width; ++x) {
            row[x] = ((0x1Fu) & row[x])
                | (((0x1Fu << 5)  & row[x]) << 1)
                | (((0x1Fu << 10) & row[x]) << 1);
        }
    }
}

void ImageRendererES3::updateRenderedResolution(unsigned int width, unsigned int height) {}

bool ImageRendererES3::rendersInVideoCallback() {
    return false;
}

void ImageRendererES3::setShaders(ShaderManager::Chain newShaders) {
    this->shaders = newShaders;
    this->isDirty = true;
}

Renderer::PassData ImageRendererES3::getPassData(unsigned int layer) {
    PassData result;

    if (layer >= 0 && layer < framebuffers->size()) {
        result.framebuffer = framebuffers->at(layer)->framebuffer;
        result.width = framebuffers->at(layer)->width;
        result.height = framebuffers->at(layer)->height;
    }

    if (layer > 0 && layer < framebuffers->size() + 1) {
        result.texture = framebuffers->at(layer - 1)->texture;
    }

    return result;
}

} //namespace libretrodroid
