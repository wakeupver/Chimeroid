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

#include "imagerendereres2.h"
#include "../../libretro-common/include/libretro.h"

namespace libretrodroid {

ImageRendererES2::ImageRendererES2() {
    glGenTextures(1, &currentTexture);
    glBindTexture(GL_TEXTURE_2D, currentTexture);

    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
}

void ImageRendererES2::onNewFrame(const void *data, unsigned width, unsigned height, size_t pitch) {
    glBindTexture(GL_TEXTURE_2D, currentTexture);

    if (alignmentDirty) {
        glPixelStorei(GL_UNPACK_ALIGNMENT, bytesPerPixel);
        alignmentDirty = false;
    }

    if (pixelFormat == RETRO_PIXEL_FORMAT_XRGB8888) {
        convertDataFromRGB8888(data, pitch * height);
    } else if (pixelFormat == RETRO_PIXEL_FORMAT_0RGB1555) {
        Renderer::unpackRGB1555InPlace(const_cast<void*>(data), (pitch * height) / bytesPerPixel);
    }

    if (filterDirty) {
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, linear ? GL_LINEAR : GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, linear ? GL_LINEAR : GL_NEAREST);
        filterDirty = false;
    }

    if (lastFrameSize.first != width || lastFrameSize.second != height) {
        glTexImage2D(GL_TEXTURE_2D, 0, glInternalFormat, width, height, 0, glFormat, glType, nullptr);
    }

    if (bytesPerPixel * width == pitch) {
        glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, width, height, glFormat, glType, data);
    } else {

        const auto* base = static_cast<const char*>(data);
        for (unsigned int i = 0; i < height; i++) {
            glTexSubImage2D(GL_TEXTURE_2D, 0, 0, i, width, 1, glFormat, glType, base + pitch * i);
        }
    }

    glBindTexture(GL_TEXTURE_2D, 0);

    Renderer::onNewFrame(data, width, height, pitch);
}

void ImageRendererES2::convertDataFromRGB8888(const void *data, size_t size) {

    auto *pixels = static_cast<uint32_t*>(const_cast<void*>(data));
    const size_t pixelCount = size / 4;
    for (size_t i = 0; i < pixelCount; ++i) {
        const uint32_t p = pixels[i];
        pixels[i] = (p & 0xFF00FF00u) | ((p & 0x00FF0000u) >> 16) | ((p & 0x000000FFu) << 16);
    }
}

uintptr_t ImageRendererES2::getTexture() {
    return currentTexture;
}

uintptr_t ImageRendererES2::getFramebuffer() {
    return 0;
}

void ImageRendererES2::setPixelFormat(int pixelFormat) {
    this->pixelFormat = pixelFormat;
    this->alignmentDirty = true;

    switch (pixelFormat) {
        case RETRO_PIXEL_FORMAT_XRGB8888:
            this->glInternalFormat = GL_RGBA;
            this->glFormat = GL_RGBA;
            this->glType = GL_UNSIGNED_BYTE;
            this->bytesPerPixel = 4;
            break;

        default:
        case RETRO_PIXEL_FORMAT_0RGB1555:
        case RETRO_PIXEL_FORMAT_RGB565:
            this->glInternalFormat = GL_RGB;
            this->glFormat = GL_RGB;
            this->glType = GL_UNSIGNED_SHORT_5_6_5;
            this->bytesPerPixel = 2;
            break;
    }
}

void ImageRendererES2::updateRenderedResolution(unsigned int width, unsigned int height) {}

bool ImageRendererES2::rendersInVideoCallback() {
    return false;
}

void ImageRendererES2::setShaders(ShaderManager::Chain shaders) {
    if (this->linear != shaders.linearTexture) {
        this->linear = shaders.linearTexture;
        this->filterDirty = true;
    }
}

Renderer::PassData ImageRendererES2::getPassData(unsigned int layer) {
    return { };
}

}
