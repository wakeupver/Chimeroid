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
    delete renderer;
}

void Video::updateProgram() {
    if (loadedShaderType.has_value() && loadedShaderType.value() == requestedShaderConfig) {
        return;
    }

    loadedShaderType = requestedShaderConfig;

    auto shaders = ShaderManager::getShader(requestedShaderConfig);

    shadersChain = {};

    std::for_each(shaders.passes.begin(), shaders.passes.end(), [&](const auto& item){
        auto shader = ShaderChainEntry { };

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
    });

    renderer->setShaders(shaders);
}

void Video::renderFrame() {
    if (skipDuplicateFrames && !isDirty) return;
    isDirty = false;

    // ── GL state reset ────────────────────────────────────────────────────────
    if (useES3) {
        glBindVertexArray(0);
    }
    glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
    glBindBuffer(GL_ARRAY_BUFFER, 0);

    glDisable(GL_DEPTH_TEST);

    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    glClearColor(0.0F, 0.0F, 0.0F, 1.0F);
    glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

    // ── Dual-screen: two single-pass draws, one per panel ────────────────────
    if (dualCfg.enabled && !shadersChain.empty()) {
        auto& shader = shadersChain.back(); // single-pass only in dual mode

        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glViewport(
            0, 0,
            videoLayout.getScreenWidth(),
            videoLayout.getScreenHeight()
        );

        // Primary (top) screen
        auto primaryUV = buildUVQuad(
            dualCfg.primaryUVxMin, dualCfg.primaryUVyMin,
            dualCfg.primaryUVxMax, dualCfg.primaryUVyMax
        );
        drawQuadPass(videoLayout.getForegroundVertices(), primaryUV, shader);

        // Secondary (bottom) screen
        auto secondaryUV = buildUVQuad(
            dualCfg.secondaryUVxMin, dualCfg.secondaryUVyMin,
            dualCfg.secondaryUVxMax, dualCfg.secondaryUVyMax
        );
        drawQuadPass(secondaryLayout.getForegroundVertices(), secondaryUV, shader);

        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        return;
    }

    // ── Normal single-screen rendering ───────────────────────────────────────

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

    for (int i = 0; i < (int)shadersChain.size(); ++i) {
        auto  shader    = shadersChain[i];
        auto  passData  = renderer->getPassData(i);
        bool  isLastPass = (i == (int)shadersChain.size() - 1);

        glBindFramebuffer(GL_FRAMEBUFFER, passData.framebuffer.value_or(0));

        glViewport(
            0,
            0,
            passData.width.value_or(videoLayout.getScreenWidth()),
            passData.height.value_or(videoLayout.getScreenHeight())
        );

        glUseProgram(shader.gProgram);

        auto& vertices    = isLastPass
            ? videoLayout.getForegroundVertices()
            : videoLayout.getFramebufferVertices();
        auto& coordinates = videoLayout.getTextureCoordinates();

        constexpr GLsizeiptr kVerts    = 12;
        constexpr GLsizeiptr kPosBytes = kVerts * sizeof(float);
        constexpr GLsizeiptr kUvBytes  = kVerts * sizeof(float);

        glBindBuffer(GL_ARRAY_BUFFER, quadVbo);
        glBufferData(GL_ARRAY_BUFFER, kPosBytes + kUvBytes, nullptr, GL_STREAM_DRAW);
        glBufferSubData(GL_ARRAY_BUFFER, 0,        kPosBytes, vertices.data());
        glBufferSubData(GL_ARRAY_BUFFER, kPosBytes, kUvBytes,  coordinates.data());

        glVertexAttribPointer(shader.gvPositionHandle,   2, GL_FLOAT, GL_FALSE, 0,
            reinterpret_cast<void*>(static_cast<uintptr_t>(0)));
        glEnableVertexAttribArray(shader.gvPositionHandle);

        glVertexAttribPointer(shader.gvCoordinateHandle, 2, GL_FLOAT, GL_FALSE, 0,
            reinterpret_cast<void*>(static_cast<uintptr_t>(kPosBytes)));
        glEnableVertexAttribArray(shader.gvCoordinateHandle);

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, renderer->getTexture());
        glUniform1i(shader.gTextureHandle, 0);

        if (shader.gPreviousPassTextureHandle != -1 && passData.texture.has_value()) {
            glActiveTexture(GL_TEXTURE0 + 1);
            glBindTexture(GL_TEXTURE_2D, passData.texture.value());
            glUniform1i(shader.gPreviousPassTextureHandle, 1);
        }

        glUniform2f(shader.gTextureSizeHandle,    getTextureWidth(), getTextureHeight());
        glUniform1f(shader.gScreenDensityHandle,  getScreenDensity());

        glDrawArrays(GL_TRIANGLES, 0, 6);

        glDisableVertexAttribArray(shader.gvPositionHandle);
        glDisableVertexAttribArray(shader.gvCoordinateHandle);

        if (shader.gPreviousPassTextureHandle != -1 && passData.texture.has_value()) {
            glActiveTexture(GL_TEXTURE0 + 1);
            glBindTexture(GL_TEXTURE_2D, 0);
        }
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, 0);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glUseProgram(0);
    }

    glBindFramebuffer(GL_FRAMEBUFFER, 0);
}

// ── Dual-screen helpers ───────────────────────────────────────────────────────

std::array<float, 12> Video::buildUVQuad(
    float xMin, float yMin, float xMax, float yMax
) const {
    // When bottomLeftOrigin=true the texture's Y=0 is the bottom of the image.
    // In that case we flip the Y so "visual top" still maps to the top panel.
    float y0 = bottomLeftOrigin ? (1.0f - yMax) : yMin;
    float y1 = bottomLeftOrigin ? (1.0f - yMin) : yMax;

    return {
        xMin, y0,   // TL
        xMin, y1,   // BL
        xMax, y0,   // TR
        xMax, y0,   // TR (triangle 2)
        xMin, y1,   // BL (triangle 2)
        xMax, y1,   // BR
    };
}

void Video::drawQuadPass(
    const std::array<float, 12>& vertices,
    const std::array<float, 12>& uvs,
    const ShaderChainEntry&       shader
) {
    constexpr GLsizeiptr kVerts    = 12;
    constexpr GLsizeiptr kPosBytes = kVerts * sizeof(float);
    constexpr GLsizeiptr kUvBytes  = kVerts * sizeof(float);

    glUseProgram(shader.gProgram);

    glBindBuffer(GL_ARRAY_BUFFER, quadVbo);
    glBufferData(GL_ARRAY_BUFFER, kPosBytes + kUvBytes, nullptr, GL_STREAM_DRAW);
    glBufferSubData(GL_ARRAY_BUFFER, 0,        kPosBytes, vertices.data());
    glBufferSubData(GL_ARRAY_BUFFER, kPosBytes, kUvBytes,  uvs.data());

    glVertexAttribPointer(shader.gvPositionHandle,   2, GL_FLOAT, GL_FALSE, 0,
        reinterpret_cast<void*>(static_cast<uintptr_t>(0)));
    glEnableVertexAttribArray(shader.gvPositionHandle);

    glVertexAttribPointer(shader.gvCoordinateHandle, 2, GL_FLOAT, GL_FALSE, 0,
        reinterpret_cast<void*>(static_cast<uintptr_t>(kPosBytes)));
    glEnableVertexAttribArray(shader.gvCoordinateHandle);

    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, renderer->getTexture());
    glUniform1i(shader.gTextureHandle, 0);

    glUniform2f(shader.gTextureSizeHandle,   getTextureWidth(), getTextureHeight());
    glUniform1f(shader.gScreenDensityHandle, getScreenDensity());

    glDrawArrays(GL_TRIANGLES, 0, 6);

    glDisableVertexAttribArray(shader.gvPositionHandle);
    glDisableVertexAttribArray(shader.gvCoordinateHandle);
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, 0);
    glBindBuffer(GL_ARRAY_BUFFER, 0);
    glUseProgram(0);
}

void Video::setDualScreenConfig(DualScreenCfg cfg) {
    dualCfg = cfg;

    if (!cfg.enabled) return;

    // Compute per-panel aspect ratio from the actual game texture dimensions
    // (e.g. 256×384 for NDS, 400×480 for 3DS default-top-bottom layout).
    // Texture size may be 0 before the first frame — fall back to 4:3.
    float texW = getTextureWidth();
    float texH = getTextureHeight();

    float primaryAR, secondaryAR;
    if (texW > 0.f && texH > 0.f) {
        float pUVW = cfg.primaryUVxMax   - cfg.primaryUVxMin;
        float pUVH = cfg.primaryUVyMax   - cfg.primaryUVyMin;
        float sUVW = cfg.secondaryUVxMax - cfg.secondaryUVxMin;
        float sUVH = cfg.secondaryUVyMax - cfg.secondaryUVyMin;

        primaryAR   = (pUVH > 0.f) ? (pUVW * texW) / (pUVH * texH) : 4.f / 3.f;
        secondaryAR = (sUVH > 0.f) ? (sUVW * texW) / (sUVH * texH) : 4.f / 3.f;
    } else {
        primaryAR   = 4.f / 3.f;   // safe fallback (NDS-like)
        secondaryAR = 4.f / 3.f;
    }

    videoLayout.updateViewportSize(
        Rect(cfg.primaryVpX, cfg.primaryVpY, cfg.primaryVpW, cfg.primaryVpH)
    );
    videoLayout.updateAspectRatio(primaryAR);

    secondaryLayout.updateScreenSize(
        videoLayout.getScreenWidth(),
        videoLayout.getScreenHeight()
    );
    secondaryLayout.updateViewportSize(
        Rect(cfg.secondaryVpX, cfg.secondaryVpY, cfg.secondaryVpW, cfg.secondaryVpH)
    );
    secondaryLayout.updateAspectRatio(secondaryAR);
}

float Video::getScreenDensity() {
    return std::min(videoLayout.getScreenWidth() / getTextureWidth(), videoLayout.getScreenHeight() / getTextureHeight());
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
    secondaryLayout.updateScreenSize(width, height);
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
    secondaryLayout(bottomLeftOrigin, rotation, Rect(0.f, 0.5f, 1.f, 0.5f)),
    bottomLeftOrigin(bottomLeftOrigin) {

    printGLString("Version", GL_VERSION);
    printGLString("Vendor", GL_VENDOR);
    printGLString("Renderer", GL_RENDERER);
    printGLString("Extensions", GL_EXTENSIONS);
    initializeGLESLogCallbackIfNeeded();

    LOGI("Initializing graphics");

    glViewport(0, 0, videoLayout.getScreenWidth(), videoLayout.getScreenHeight());

    glUseProgram(0);

    // Store ES version so renderFrame() can issue the VAO reset guard.
    useES3 = renderingOptions.openglESVersion >= 3;

    // Allocate the persistent quad VBO used in renderFrame().
    // This ensures glVertexAttribPointer() always references VBO offsets,
    // which is the only valid usage in GLES 3.0 and avoids corruption when
    // a HW core leaves a foreign VBO bound after its retro_run() call.
    glGenBuffers(1, &quadVbo);

    initializeRenderer(renderingOptions);
}

void Video::updateShaderType(ShaderManager::Config shaderConfig) {
    requestedShaderConfig = std::move(shaderConfig);
}

void Video::initializeRenderer(RenderingOptions renderingOptions) {
    auto shaders = ShaderManager::getShader(requestedShaderConfig);

    if (renderingOptions.hardwareAccelerated) {
        renderer = new FramebufferRenderer(
            renderingOptions.width,
            renderingOptions.height,
            renderingOptions.useDepth,
            renderingOptions.useStencil,
            std::move(shaders)
        );
    } else {
        if (renderingOptions.openglESVersion >= 3) {
            renderer = new ImageRendererES3();
        } else {
            renderer = new ImageRendererES2();
        }
    }

    renderer->setPixelFormat(renderingOptions.pixelFormat);
    updateProgram();
}

void Video::updateAspectRatio(float aspectRatio) {
    videoLayout.updateAspectRatio(aspectRatio);
}

} //namespace libretrodroid
