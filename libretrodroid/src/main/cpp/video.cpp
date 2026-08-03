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
    // renderer is a unique_ptr: cleaned up automatically, including on the
    // exception path if construction fails partway (e.g. shader compilation
    // failure in initializeRenderer()), which the previous raw-pointer +
    // manual `delete` here could not guarantee.
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

    auto [validFractionX, validFractionY] = renderer->getValidContentFraction();
    videoLayout.updateValidContentFraction(validFractionX, validFractionY);

    // ── GL state reset ────────────────────────────────────────────────────────
    // HW-accelerated cores (PPSSPP, SwanStation, ...) render through this same
    // GL context and can leave blend/cull/stencil/scissor enabled with
    // arbitrary funcs. Our own quad passes never intend to use any of these,
    // so they're force-disabled every frame rather than assumed off; leaving
    // any one enabled can silently turn the composited frame transparent,
    // clipped, or culled away entirely.
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

    // ── Dual-screen: two single-pass draws, one per panel ────────────────────
    if (dualCfg.enabled && !shadersChain.empty()) {
        const auto& shader = shadersChain.back(); // single-pass only in dual mode

        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glViewport(
            0, 0,
            videoLayout.getScreenWidth(),
            videoLayout.getScreenHeight()
        );

        // primaryUV/secondaryUV are precomputed in setDualScreenConfig(): they
        // only depend on dualCfg + texture size, not on anything that changes
        // frame-to-frame.
        drawQuadPass(videoLayout.getForegroundVertices(), primaryUV, shader);
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

    // Loop-invariant across every pass of this frame: the source texture's
    // size and screen density don't change pass-to-pass, and the UV
    // coordinate array is the same reference every time. Resolving them once
    // avoids repeating the same trivial work once per shader pass.
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

void Video::uploadAndDraw(
    const std::array<float, 12>& vertices,
    const std::array<float, 12>& uvs,
    GLint posHandle,
    GLint coordHandle
) {
    // Positions and UVs are both 12 floats (6 xy/st pairs), so one constant
    // describes both halves of the interleaved buffer below.
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

void Video::drawQuadPass(
    const std::array<float, 12>& vertices,
    const std::array<float, 12>& uvs,
    const ShaderChainEntry&       shader
) {
    glUseProgram(shader.gProgram);

    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, renderer->getTexture());
    glUniform1i(shader.gTextureHandle, 0);

    glUniform2f(shader.gTextureSizeHandle,   getTextureWidth(), getTextureHeight());
    glUniform1f(shader.gScreenDensityHandle, getScreenDensity());

    uploadAndDraw(vertices, uvs, shader.gvPositionHandle, shader.gvCoordinateHandle);

    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, 0);
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

    constexpr float kFallbackAspectRatio = 4.f / 3.f;   // safe fallback (NDS-like)
    auto panelAspectRatio = [texW, texH](float uMin, float uMax, float vMin, float vMax) {
        float uvHeight = vMax - vMin;
        if (texW <= 0.f || texH <= 0.f || uvHeight <= 0.f) {
            return kFallbackAspectRatio;
        }
        return ((uMax - uMin) * texW) / (uvHeight * texH);
    };

    float primaryAR = panelAspectRatio(
        cfg.primaryUVxMin, cfg.primaryUVxMax, cfg.primaryUVyMin, cfg.primaryUVyMax
    );
    float secondaryAR = panelAspectRatio(
        cfg.secondaryUVxMin, cfg.secondaryUVxMax, cfg.secondaryUVyMin, cfg.secondaryUVyMax
    );

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

    // Cache the per-panel UV quads: they only depend on cfg (and
    // bottomLeftOrigin, which is fixed), so resolving them here means
    // renderFrame() can reuse them every frame instead of rebuilding two
    // std::array<float,12> from scratch on every single draw.
    primaryUV = buildUVQuad(
        cfg.primaryUVxMin, cfg.primaryUVyMin, cfg.primaryUVxMax, cfg.primaryUVyMax
    );
    secondaryUV = buildUVQuad(
        cfg.secondaryUVxMin, cfg.secondaryUVyMin, cfg.secondaryUVxMax, cfg.secondaryUVyMax
    );
}

float Video::getScreenDensity() {
    float textureWidth = getTextureWidth();
    float textureHeight = getTextureHeight();
    // Texture size is 0 until the first frame is decoded; without this guard the
    // division below produces Inf, which then feeds into the shader's screenDensity
    // uniform (used by CRT/LCD/sharpen passes for scanline/pixel-grid math).
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
    // Resolved once and reused below for both renderer construction (which,
    // for the HW-accelerated path, needs the Chain to size its FBOs) and the
    // initial GL program compilation, instead of calling
    // ShaderManager::getShader() a second time for the same config.
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

} //namespace libretrodroid
