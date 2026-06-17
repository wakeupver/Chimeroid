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
    // HW-accelerated cores (PPSSPP, SwanStation, etc.) can leave VAOs, VBOs,
    // and index buffers bound after their retro_run() frame.
    //
    // In GLES 3.0, when a VBO is bound to GL_ARRAY_BUFFER, the "pointer"
    // argument of glVertexAttribPointer() is treated as a byte offset into that
    // VBO — not as a CPU memory address.  With a stale foreign VBO still bound,
    // passing our raw float* would be misinterpreted as an offset, producing
    // blank or garbage output.
    //
    // Similarly, a bound VAO causes glVertexAttribPointer() to modify *that*
    // VAO's attribute state instead of the global default, so our vertex setup
    // has no effect on the draw call.
    //
    // Resetting all three bindings here (and using a VBO for our own draws
    // below) makes rendering correct and robust regardless of what the core left
    // behind — mirroring RetroArch's gl3_renderchain pattern.
    if (useES3) {
        glBindVertexArray(0);                 // unbind any core VAO
    }
    glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0); // unbind any core index buffer
    glBindBuffer(GL_ARRAY_BUFFER, 0);         // defensive; we'll rebind per-pass

    glDisable(GL_DEPTH_TEST);

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

        // ── VBO-backed vertex upload ──────────────────────────────────────────
        // Pack positions (12 floats) then UVs (12 floats) into a single stream
        // VBO.  glVertexAttribPointer() then receives byte offsets into that
        // VBO, which is the only correct usage in GLES 3.0.
        auto& vertices    = isLastPass
            ? videoLayout.getForegroundVertices()
            : videoLayout.getFramebufferVertices();
        auto& coordinates = videoLayout.getTextureCoordinates();

        constexpr GLsizeiptr kVerts    = 12;                        // floats
        constexpr GLsizeiptr kPosBytes = kVerts * sizeof(float);    // 48 B
        constexpr GLsizeiptr kUvBytes  = kVerts * sizeof(float);    // 48 B

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

        // ── Textures & uniforms ───────────────────────────────────────────────
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

        // ── Per-pass cleanup (matches RetroArch gl3 pattern) ─────────────────
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

    // Ensure default framebuffer is bound after all passes.
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
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
    videoLayout(bottomLeftOrigin, rotation, viewportRect) {

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
