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

#include <GLES3/gl3.h>
#include <EGL/egl.h>
#include <cstdlib>
#include <string>
#include <cmath>
#include <utility>
#include <sstream>

#include "log.h"

#include "video.h"
#include "renderers/es3/framebufferrenderer.h"
#include "renderers/es3/imagerendereres3.h"
#include "renderers/es2/imagerendereres2.h"

namespace libretrodroid {

static void printGLString(const char *name, GLenum s) {
    const char *v = (const char *) glGetString(s);
    LOGI("GL %s = %s\n", name, v);
}

GLuint loadShader(GLenum shaderType, const char* pSource) {
    GLuint shader = glCreateShader(shaderType);
    if (shader) {
        glShaderSource(shader, 1, &pSource, nullptr);
        glCompileShader(shader);
        GLint compiled = 0;
        glGetShaderiv(shader, GL_COMPILE_STATUS, &compiled);
        if (!compiled) {
            GLint infoLen = 0;
            glGetShaderiv(shader, GL_INFO_LOG_LENGTH, &infoLen);
            if (infoLen) {
                char* buf = (char*) malloc(infoLen);
                if (buf) {
                    glGetShaderInfoLog(shader, infoLen, nullptr, buf);
                    LOGE("Could not compile shader %d:\n%s\n",
                         shaderType, buf);
                    free(buf);
                }
                glDeleteShader(shader);
                shader = 0;
            }
        }
    }
    return shader;
}

GLuint createProgram(const char* pVertexSource, const char* pFragmentSource) {
    GLuint vertexShader = loadShader(GL_VERTEX_SHADER, pVertexSource);
    if (!vertexShader) {
        return 0;
    }

    GLuint pixelShader = loadShader(GL_FRAGMENT_SHADER, pFragmentSource);
    if (!pixelShader) {
        return 0;
    }

    GLuint program = glCreateProgram();
    if (program) {
        glAttachShader(program, vertexShader);
        glAttachShader(program, pixelShader);
        glLinkProgram(program);
        GLint linkStatus = GL_FALSE;
        glGetProgramiv(program, GL_LINK_STATUS, &linkStatus);
        if (linkStatus != GL_TRUE) {
            GLint bufLength = 0;
            glGetProgramiv(program, GL_INFO_LOG_LENGTH, &bufLength);
            if (bufLength) {
                char* buf = (char*) malloc(bufLength);
                if (buf) {
                    glGetProgramInfoLog(program, bufLength, nullptr, buf);
                    LOGE("Could not link program:\n%s\n", buf);
                    free(buf);
                }
            }
            glDeleteProgram(program);
            program = 0;
        }
    }
    return program;
}

Video::~Video() {
    if (quadVbo) {
        glDeleteBuffers(1, &quadVbo);
        quadVbo = 0;
    }

}

void Video::compileShaderChain(const ShaderManager::Chain& shaders) {
    shadersChain.clear();
    shadersChain.reserve(shaders.passes.size());

    for (const auto& item : shaders.passes) {
        ShaderChainEntry shader{};

        shader.gProgram = createProgram(item.vertex.data(), item.fragment.data());
        if (!shader.gProgram) {
            LOGE("Could not create gl program.");
            throw std::runtime_error("Cannot create gl program");
        }

        shader.gvPositionHandle = glGetAttribLocation(shader.gProgram, "vPosition");
        shader.gvCoordinateHandle = glGetAttribLocation(shader.gProgram, "vCoordinate");
        shader.gTextureHandle = glGetUniformLocation(shader.gProgram, "texture");
        shader.gPreviousPassTextureHandle = glGetUniformLocation(shader.gProgram, "previousPass");
        shader.gTextureSizeHandle = glGetUniformLocation(shader.gProgram, "textureSize");
        shader.gScreenDensityHandle = glGetUniformLocation(shader.gProgram, "screenDensity");

        shadersChain.push_back(shader);
    }
}

void Video::updateProgram() {
    if (loadedShaderType.has_value() && loadedShaderType.value() == requestedShaderConfig) {
        return;
    }

    loadedShaderType = requestedShaderConfig;

    auto shaders = ShaderManager::getShader(requestedShaderConfig);
    compileShaderChain(shaders);
    renderer->setShaders(std::move(shaders));
}

void Video::renderFrame() {
    if (skipDuplicateFrames && !isDirty) return;
    isDirty = false;

    if (useES3) {
        glBindVertexArray(0);
    }
    glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
    glBindBuffer(GL_ARRAY_BUFFER, 0);

    glDisable(GL_DEPTH_TEST);
    glDisable(GL_BLEND);
    glDisable(GL_CULL_FACE);
    glDisable(GL_STENCIL_TEST);
    glDisable(GL_SCISSOR_TEST);

    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    glClearColor(0.0F, 0.0F, 0.0F, 1.0F);
    glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

    if (immersiveModeEnabled) {
        immersiveMode.renderBackground(
            videoLayout.getScreenWidth(),
            videoLayout.getScreenHeight(),
            videoLayout.getBackgroundVertices(),
            videoLayout.getRelativeForegroundBounds(),
            videoLayout.getFramebufferVertices().data(),
            renderer->getTexture()
        );
    }

    updateProgram();

    const float textureWidth = getTextureWidth();
    const float textureHeight = getTextureHeight();
    const float screenDensity = getScreenDensity();
    const auto& coordinates = videoLayout.getTextureCoordinates();
    const size_t passCount = shadersChain.size();

    for (size_t i = 0; i < passCount; ++i) {
        const auto& shader = shadersChain[i];
        const auto passData = renderer->getPassData(static_cast<unsigned int>(i));
        const bool isLastPass = (i == passCount - 1);

        glBindFramebuffer(GL_FRAMEBUFFER, passData.framebuffer.value_or(0));

        glViewport(
            0,
            0,
            passData.width.value_or(videoLayout.getScreenWidth()),
            passData.height.value_or(videoLayout.getScreenHeight())
        );

        glUseProgram(shader.gProgram);

        const auto& vertices = isLastPass
            ? videoLayout.getForegroundVertices()
            : videoLayout.getFramebufferVertices();

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, renderer->getTexture());
        glUniform1i(shader.gTextureHandle, 0);

        if (shader.gPreviousPassTextureHandle != -1 && passData.texture.has_value()) {
            glActiveTexture(GL_TEXTURE0 + 1);
            glBindTexture(GL_TEXTURE_2D, passData.texture.value());
            glUniform1i(shader.gPreviousPassTextureHandle, 1);
        }

        glUniform2f(shader.gTextureSizeHandle, textureWidth, textureHeight);
        glUniform1f(shader.gScreenDensityHandle, screenDensity);

        uploadAndDraw(vertices, coordinates, shader.gvPositionHandle, shader.gvCoordinateHandle);

        if (shader.gPreviousPassTextureHandle != -1 && passData.texture.has_value()) {
            glActiveTexture(GL_TEXTURE0 + 1);
            glBindTexture(GL_TEXTURE_2D, 0);
        }
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, 0);

        glUseProgram(0);
    }

    glBindFramebuffer(GL_FRAMEBUFFER, 0);
}

void Video::uploadAndDraw(
    const std::array<float, 12>& vertices,
    const std::array<float, 12>& uvs,
    GLint posHandle,
    GLint coordHandle
) {

    constexpr GLsizeiptr kQuadBytes = 12 * sizeof(float);

    glBindBuffer(GL_ARRAY_BUFFER, quadVbo);
    glBufferData(GL_ARRAY_BUFFER, kQuadBytes * 2, nullptr, GL_STREAM_DRAW);
    glBufferSubData(GL_ARRAY_BUFFER, 0,          kQuadBytes, vertices.data());
    glBufferSubData(GL_ARRAY_BUFFER, kQuadBytes, kQuadBytes, uvs.data());

    glVertexAttribPointer(posHandle,   2, GL_FLOAT, GL_FALSE, 0,
        reinterpret_cast<void*>(static_cast<uintptr_t>(0)));
    glEnableVertexAttribArray(posHandle);

    glVertexAttribPointer(coordHandle, 2, GL_FLOAT, GL_FALSE, 0,
        reinterpret_cast<void*>(static_cast<uintptr_t>(kQuadBytes)));
    glEnableVertexAttribArray(coordHandle);

    glDrawArrays(GL_TRIANGLES, 0, 6);

    glDisableVertexAttribArray(posHandle);
    glDisableVertexAttribArray(coordHandle);
    glBindBuffer(GL_ARRAY_BUFFER, 0);
}

float Video::getScreenDensity() {
    float textureWidth = getTextureWidth();
    float textureHeight = getTextureHeight();

    if (textureWidth <= 0.0f || textureHeight <= 0.0f) {
        return 1.0f;
    }
    return std::min(videoLayout.getScreenWidth() / textureWidth, videoLayout.getScreenHeight() / textureHeight);
}

float Video::getTextureWidth() {
    return renderer->lastFrameSize.first;
}

float Video::getTextureHeight() {
    return renderer->lastFrameSize.second;
}

void Video::onNewFrame(const void *data, unsigned width, unsigned height, size_t pitch) {
    if (data != nullptr) {
        renderer->onNewFrame(data, width, height, pitch);
        isDirty = true;
    }
}

void Video::updateScreenSize(unsigned width, unsigned height) {
    videoLayout.updateScreenSize(width, height);
}

void Video::updateViewportSize(Rect viewportRect) {
    videoLayout.updateViewportSize(viewportRect);
}

void Video::updateRendererSize(unsigned int width, unsigned int height) {
    LOGD("Updating renderer size: %d x %d", width, height);
    renderer->updateRenderedResolution(width, height);
}

void Video::recreateRenderer() {
    renderer->forceReinitialize();
}

void Video::updateRotation(float rotation) {
    videoLayout.updateRotation(rotation);
}

Video::Video(
    RenderingOptions renderingOptions,
    ShaderManager::Config shaderConfig,
    bool bottomLeftOrigin,
    float rotation,
    bool skipDuplicateFrames,
    bool immersiveModeEnabled,
    Rect viewportRect,
    ImmersiveMode::Config immersiveModeConfig
) :
    requestedShaderConfig(std::move(shaderConfig)),
    skipDuplicateFrames(skipDuplicateFrames),
    immersiveModeEnabled(immersiveModeEnabled),
    immersiveMode(immersiveModeConfig),
    videoLayout(bottomLeftOrigin, rotation, viewportRect),
    bottomLeftOrigin(bottomLeftOrigin) {

    printGLString("Version", GL_VERSION);
    printGLString("Vendor", GL_VENDOR);
    printGLString("Renderer", GL_RENDERER);
    printGLString("Extensions", GL_EXTENSIONS);
    initializeGLESLogCallbackIfNeeded();

    LOGI("Initializing graphics");

    glViewport(0, 0, videoLayout.getScreenWidth(), videoLayout.getScreenHeight());

    glUseProgram(0);

    useES3 = renderingOptions.openglESVersion >= 3;

    glGenBuffers(1, &quadVbo);

    initializeRenderer(renderingOptions);
}

void Video::updateShaderType(ShaderManager::Config shaderConfig) {
    requestedShaderConfig = std::move(shaderConfig);
}

void Video::initializeRenderer(RenderingOptions renderingOptions) {

    auto shaders = ShaderManager::getShader(requestedShaderConfig);

    if (renderingOptions.hardwareAccelerated) {
        renderer = std::make_unique<FramebufferRenderer>(
            renderingOptions.width,
            renderingOptions.height,
            renderingOptions.useDepth,
            renderingOptions.useStencil,
            shaders
        );
    } else if (renderingOptions.openglESVersion >= 3) {
        renderer = std::make_unique<ImageRendererES3>();
        renderer->setShaders(shaders);
    } else {
        renderer = std::make_unique<ImageRendererES2>();
        renderer->setShaders(shaders);
    }

    renderer->setPixelFormat(renderingOptions.pixelFormat);

    loadedShaderType = requestedShaderConfig;
    compileShaderChain(shaders);
}

void Video::updateAspectRatio(float aspectRatio) {
    videoLayout.updateAspectRatio(aspectRatio);
}

}
