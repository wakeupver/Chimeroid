/*
 *     Copyright (C) 2021  Filippo Scognamiglio
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

#include <jni.h>

#include <EGL/egl.h>

#include <memory>
#include <string>
#include <vector>
#include <unordered_set>
#include <mutex>
#include <optional>

#include "libretrodroid.h"
#include "log.h"
#include "core.h"
#include "audio.h"
#include "video.h"
#include "renderers/renderer.h"
#include "fpssync.h"
#include "input.h"
#include "rumble.h"
#include "shadermanager.h"
#include "utils/javautils.h"
#include "errorcodes.h"
#include "environment.h"
#include "renderers/es3/framebufferrenderer.h"
#include "renderers/es2/imagerendereres2.h"
#include "renderers/es3/imagerendereres3.h"
#include "utils/jnistring.h"

namespace libretrodroid {

extern "C" {
#include "utils/utils.h"
#include "../../libretro-common/include/libretro.h"
#include "utils/libretrodroidexception.h"
}

extern "C" {

JNIEXPORT jint JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_availableDisks(
    JNIEnv* env,
    jclass obj
) {
    return LibretroDroid::getInstance().availableDisks();
}

JNIEXPORT jint JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_currentDisk(
    JNIEnv* env,
    jclass obj
) {
    return LibretroDroid::getInstance().currentDisk();
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_changeDisk(
    JNIEnv* env,
    jclass obj,
    jint index
) {
    return LibretroDroid::getInstance().changeDisk(index);
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_updateVariable(
    JNIEnv* env,
    jclass obj,
    jobject variable
) {
    Variable v = JavaUtils::variableFromJava(env, variable);
    Environment::getInstance().updateVariable(v.key, v.value);
}

JNIEXPORT jobjectArray JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_getVariables(
    JNIEnv* env,
    jclass obj
) {
    auto variables = Environment::getInstance().getVariables();
    const auto count = static_cast<jsize>(variables.size());

    jclass variableClass = env->FindClass("com/swordfish/libretrodroid/Variable");
    if (variableClass == nullptr) {
        return nullptr;
    }

    // Reflection lookups are loop-invariant. Hoisting them turns O(n) redundant
    // FindClass/GetMethodID/GetFieldID calls into a single O(1) setup.
    jmethodID variableCtor = env->GetMethodID(variableClass, "<init>", "()V");
    jfieldID keyField = env->GetFieldID(variableClass, "key", "Ljava/lang/String;");
    jfieldID valueField = env->GetFieldID(variableClass, "value", "Ljava/lang/String;");
    jfieldID descriptionField = env->GetFieldID(variableClass, "description", "Ljava/lang/String;");
    if (variableCtor == nullptr || keyField == nullptr || valueField == nullptr || descriptionField == nullptr) {
        return nullptr;
    }

    jobjectArray result = env->NewObjectArray(count, variableClass, nullptr);
    if (result == nullptr) {
        return nullptr;
    }

    for (jsize i = 0; i < count; ++i) {
        const auto& variable = variables[i];

        jobject jVariable = env->NewObject(variableClass, variableCtor);
        jstring jKey = env->NewStringUTF(variable.key.c_str());
        jstring jValue = env->NewStringUTF(variable.value.c_str());
        jstring jDescription = env->NewStringUTF(variable.description.c_str());

        env->SetObjectField(jVariable, keyField, jKey);
        env->SetObjectField(jVariable, valueField, jValue);
        env->SetObjectField(jVariable, descriptionField, jDescription);
        env->SetObjectArrayElement(result, i, jVariable);

        // Cores with large option sets (e.g. snes9x, ~40+ variables) used to leak
        // 4 local refs/iteration with no bound, risking JNI local reference table
        // exhaustion — which silently truncates or corrupts the tail of the list.
        env->DeleteLocalRef(jDescription);
        env->DeleteLocalRef(jValue);
        env->DeleteLocalRef(jKey);
        env->DeleteLocalRef(jVariable);
    }

    return result;
}

JNIEXPORT jobjectArray JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_getControllers(
    JNIEnv* env,
    jclass obj
) {
    auto controllers = Environment::getInstance().getControllers();
    const auto playerCount = static_cast<jsize>(controllers.size());

    jclass controllerArrayClass = env->FindClass("[Lcom/swordfish/libretrodroid/Controller;");
    jclass controllerClass = env->FindClass("com/swordfish/libretrodroid/Controller");
    if (controllerArrayClass == nullptr || controllerClass == nullptr) {
        return nullptr;
    }

    // Loop-invariant reflection lookups, hoisted once instead of once per
    // player (ctor) and once per controller type (field ids).
    jmethodID controllerCtor = env->GetMethodID(controllerClass, "<init>", "()V");
    jfieldID idField = env->GetFieldID(controllerClass, "id", "I");
    jfieldID descriptionField = env->GetFieldID(controllerClass, "description", "Ljava/lang/String;");
    if (controllerCtor == nullptr || idField == nullptr || descriptionField == nullptr) {
        return nullptr;
    }

    jobjectArray result = env->NewObjectArray(playerCount, controllerArrayClass, nullptr);
    if (result == nullptr) {
        return nullptr;
    }

    for (jsize i = 0; i < playerCount; ++i) {
        const auto& playerControllers = controllers[i];
        const auto typeCount = static_cast<jsize>(playerControllers.size());

        jobjectArray controllerArray = env->NewObjectArray(typeCount, controllerClass, nullptr);
        if (controllerArray == nullptr) {
            return nullptr;
        }

        for (jsize j = 0; j < typeCount; ++j) {
            const auto& controller = playerControllers[j];

            jobject jController = env->NewObject(controllerClass, controllerCtor);
            jstring jDescription = env->NewStringUTF(controller.description.c_str());

            env->SetIntField(jController, idField, static_cast<jint>(controller.id));
            env->SetObjectField(jController, descriptionField, jDescription);
            env->SetObjectArrayElement(controllerArray, j, jController);

            env->DeleteLocalRef(jDescription);
            env->DeleteLocalRef(jController);
        }

        env->SetObjectArrayElement(result, i, controllerArray);
        env->DeleteLocalRef(controllerArray);
    }

    return result;
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_setControllerType(
    JNIEnv* env,
    jclass obj,
    jint port,
    jint type
) {
    LibretroDroid::getInstance().setControllerType(port, type);
}

JNIEXPORT jboolean JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_unserializeState(
    JNIEnv* env,
    jclass obj,
    jbyteArray state
) {
    try {
        jboolean isCopy = JNI_FALSE;
        jbyte* data = env->GetByteArrayElements(state, &isCopy);
        jsize size = env->GetArrayLength(state);

        bool result = LibretroDroid::getInstance().unserializeState(data, size);
        env->ReleaseByteArrayElements(state, data, JNI_ABORT);

        return result ? JNI_TRUE : JNI_FALSE;

    } catch (std::exception &exception) {
        LOGE("Error in unserializeState: %s", exception.what());
        JavaUtils::throwRetroException(env, ERROR_SERIALIZATION);
        return JNI_FALSE;
    }
}

JNIEXPORT jbyteArray JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_serializeState(
    JNIEnv* env,
    jclass obj
) {
    try {
        auto [data, size] = LibretroDroid::getInstance().serializeState();

        jbyteArray result = env->NewByteArray(size);
        env->SetByteArrayRegion(result, 0, size, data);

        return result;

    } catch (std::exception &exception) {
        LOGE("Error in serializeState: %s", exception.what());
        JavaUtils::throwRetroException(env, ERROR_SERIALIZATION);
    }

    return nullptr;
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_setCheat(
    JNIEnv* env,
    jclass obj,
    jint index,
    jboolean enabled,
    jstring code
) {
    try {
        auto codeString = JniString(env, code);
        LibretroDroid::getInstance().setCheat(index, enabled, codeString.stdString());
    } catch (std::exception &exception) {
        LOGE("Error in setCheat: %s", exception.what());
        JavaUtils::throwRetroException(env, ERROR_CHEAT);
    }
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_resetCheat(
    JNIEnv* env,
    jclass obj
) {
    try {
        LibretroDroid::getInstance().resetCheat();
    } catch (std::exception &exception) {
        LOGE("Error in resetCheat: %s", exception.what());
        JavaUtils::throwRetroException(env, ERROR_CHEAT);
    }
}

JNIEXPORT jboolean JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_unserializeSRAM(
    JNIEnv* env,
    jclass obj,
    jbyteArray sram
) {
    try {
        jboolean isCopy = JNI_FALSE;
        jbyte* data = env->GetByteArrayElements(sram, &isCopy);
        jsize size = env->GetArrayLength(sram);

        LibretroDroid::getInstance().unserializeSRAM(data, size);

        env->ReleaseByteArrayElements(sram, data, JNI_ABORT);

    } catch (std::exception &exception) {
        LOGE("Error in unserializeSRAM: %s", exception.what());
        JavaUtils::throwRetroException(env, ERROR_SERIALIZATION);
        return JNI_FALSE;
    }

    return JNI_TRUE;
}

JNIEXPORT jbyteArray JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_serializeSRAM(
    JNIEnv* env,
    jclass obj
) {
    try {
        auto [data, size] = LibretroDroid::getInstance().serializeSRAM();

        jbyteArray result = env->NewByteArray(size);
        env->SetByteArrayRegion(result, 0, size, (jbyte *) data);

        return result;

    } catch (std::exception &exception) {
        LOGE("Error in serializeSRAM: %s", exception.what());
        JavaUtils::throwRetroException(env, ERROR_SERIALIZATION);
    }

    return nullptr;
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_reset(
    JNIEnv* env,
    jclass obj
) {
    try {
        LibretroDroid::getInstance().reset();
    } catch (std::exception &exception) {
        LOGE("Error in clear: %s", exception.what());
        JavaUtils::throwRetroException(env, ERROR_GENERIC);
    }
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_onSurfaceChanged(
    JNIEnv* env,
    jclass obj,
    jint width,
    jint height
) {
    try {
        LibretroDroid::getInstance().onSurfaceChanged(width, height);
    } catch (std::exception& exception) {
        LOGE("Error in onSurfaceChanged: %s", exception.what());
        JavaUtils::throwRetroException(env, ERROR_GENERIC);
    } catch (...) {
        LOGE("Unknown exception in onSurfaceChanged");
        JavaUtils::throwRetroException(env, ERROR_GENERIC);
    }
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_onSurfaceCreated(
    JNIEnv* env,
    jclass obj
) {
    try {
        LibretroDroid::getInstance().onSurfaceCreated();
    } catch (std::exception& exception) {
        LOGE("Error in onSurfaceCreated: %s", exception.what());
        JavaUtils::throwRetroException(env, ERROR_GENERIC);
    } catch (...) {
        LOGE("Unknown exception in onSurfaceCreated");
        JavaUtils::throwRetroException(env, ERROR_GENERIC);
    }
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_onMotionEvent(
    JNIEnv* env,
    jclass obj,
    jint port,
    jint source,
    jfloat xAxis,
    jfloat yAxis
) {
    LibretroDroid::getInstance().onMotionEvent(port, source, xAxis, yAxis);
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_onTouchEvent(
    JNIEnv* env,
    jclass obj,
    jfloat xAxis,
    jfloat yAxis
) {
    LibretroDroid::getInstance().onTouchEvent(xAxis, yAxis);
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_releaseSecondaryTouch(
    JNIEnv* env,
    jclass obj
) {
    LibretroDroid::getInstance().releaseSecondaryTouch();
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_setSecondaryTouchDirect(
    JNIEnv* env,
    jclass obj,
    jfloat relX,
    jfloat relY
) {
    LibretroDroid::getInstance().setSecondaryTouchDirect(relX, relY);
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_onKeyEvent(
    JNIEnv* env,
    jclass obj,
    jint port,
    jint action,
    jint keyCode
) {
    LibretroDroid::getInstance().onKeyEvent(port, action, keyCode);
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_create(
    JNIEnv* env,
    jclass obj,
    jint GLESVersion,
    jstring soFilePath,
    jstring systemDir,
    jstring savesDir,
    jobjectArray jVariables,
    jobject shaderConfig,
    jfloat refreshRate,
    jboolean preferLowLatencyAudio,
    jboolean enableVirtualFileSystem,
    jboolean enableMicrophone,
    jboolean skipDuplicateFrames,
    jobject immersiveMode,
    jstring language
) {
    try {
        auto corePath = JniString(env, soFilePath);
        auto deviceLanguage = JniString(env, language);
        auto systemDirectory = JniString(env, systemDir);
        auto savesDirectory = JniString(env, savesDir);

        std::vector<Variable> variables;
        int size = env->GetArrayLength(jVariables);
        for (int i = 0; i < size; i++) {
            auto jVariable = (jobject) env->GetObjectArrayElement(jVariables, i);
            auto variable = JavaUtils::variableFromJava(env, jVariable);
            variables.push_back(variable);
        }

        std::optional<ImmersiveMode::Config> parsedConfig = std::nullopt;
        if (immersiveMode != nullptr) {
            jclass configClass = env->GetObjectClass(immersiveMode);
            jfieldID downscaledWidthField = env->GetFieldID(configClass, "downscaledWidth", "I");
            jfieldID downscaledHeightField = env->GetFieldID(configClass, "downscaledHeight", "I");
            jfieldID blurMaskSizeField = env->GetFieldID(configClass, "blurMaskSize", "I");
            jfieldID blurBrightnessField = env->GetFieldID(configClass, "blurBrightness", "F");
            jfieldID blurSkipUpdateField = env->GetFieldID(configClass, "blurSkipUpdate", "I");
            jfieldID blendFactorField = env->GetFieldID(configClass, "blendFactor", "F");

            ImmersiveMode::Config config {};
            config.downscaledWidth = env->GetIntField(immersiveMode, downscaledWidthField);
            config.downscaledHeight = env->GetIntField(immersiveMode, downscaledHeightField);
            config.blurMaskSize = env->GetIntField(immersiveMode, blurMaskSizeField);
            config.blurBrightness = env->GetFloatField(immersiveMode, blurBrightnessField);
            config.blurSkipUpdate = env->GetIntField(immersiveMode, blurSkipUpdateField);
            config.blendFactor = env->GetFloatField(immersiveMode, blendFactorField);
            parsedConfig = config;
        }

        LibretroDroid::getInstance().create(
            GLESVersion,
            corePath.stdString(),
            systemDirectory.stdString(),
            savesDirectory.stdString(),
            variables,
            JavaUtils::shaderFromJava(env, shaderConfig),
            refreshRate,
            preferLowLatencyAudio,
            enableVirtualFileSystem,
            enableMicrophone,
            skipDuplicateFrames,
            parsedConfig,
            deviceLanguage.stdString()
        );

    } catch (libretrodroid::LibretroDroidError& exception) {
        LOGE("Error in create: %s", exception.what());
        JavaUtils::throwRetroException(env, exception.getErrorCode());
    } catch (std::exception &exception) {
        LOGE("Error in create: %s", exception.what());
        JavaUtils::throwRetroException(env, ERROR_LOAD_LIBRARY);
    }
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_loadGameFromPath(
    JNIEnv* env,
    jclass obj,
    jstring gameFilePath
) {
    auto gamePath = JniString(env, gameFilePath);

    try {
        LibretroDroid::getInstance().loadGameFromPath(gamePath.stdString());
    } catch (std::exception &exception) {
        LOGE("Error in loadGameFromPath: %s", exception.what());
        JavaUtils::throwRetroException(env, ERROR_LOAD_GAME);
    }
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_loadGameFromBytes(
    JNIEnv* env,
    jclass obj,
    jbyteArray gameFileBytes
) {
    try {
        size_t size = env->GetArrayLength(gameFileBytes);
        auto* data = new int8_t[size];
        env->GetByteArrayRegion(
            gameFileBytes,
            0,
            size,
            reinterpret_cast<int8_t*>(data)
        );
        LibretroDroid::getInstance().loadGameFromBytes(data, size);
    } catch (std::exception &exception) {
        LOGE("Error in loadGameFromBytes: %s", exception.what());
        JavaUtils::throwRetroException(env, ERROR_LOAD_GAME);
    }
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_loadGameFromVirtualFiles(
        JNIEnv* env,
        jclass obj,
        jobject virtualFileList
) {

    try {
        jmethodID getVirtualFileMethodID = env->GetMethodID(
                env->FindClass("com/swordfish/libretrodroid/DetachedVirtualFile"),
                "getVirtualPath",
                "()Ljava/lang/String;"
        );
        jmethodID getFileDescriptorMethodID = env->GetMethodID(
                env->FindClass("com/swordfish/libretrodroid/DetachedVirtualFile"),
                "getFileDescriptor",
                "()I"
        );

        std::vector<VFSFile> virtualFiles;

        JavaUtils::forEachOnJavaIterable(env, virtualFileList, [&](jobject item) {
            JniString virtualFileName(env,(jstring) env->CallObjectMethod(
                item,
                getVirtualFileMethodID
            ));

            int fileDescriptor = env->CallIntMethod(item, getFileDescriptorMethodID);
            virtualFiles.emplace_back(VFSFile(virtualFileName.stdString(), fileDescriptor));
        });

        LibretroDroid::getInstance().loadGameFromVirtualFiles(std::move(virtualFiles));
    } catch (std::exception &exception) {
        LOGE("Error in loadGameFromDescriptors: %s", exception.what());
        JavaUtils::throwRetroException(env, ERROR_LOAD_GAME);
    }
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_destroy(
    JNIEnv* env,
    jclass obj
) {
    try {
        LibretroDroid::getInstance().destroy();
    } catch (std::exception &exception) {
        LOGE("Error in destroy: %s", exception.what());
        JavaUtils::throwRetroException(env, ERROR_GENERIC);
    }
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_resume(
    JNIEnv* env,
    jclass obj
) {
    try {
        LibretroDroid::getInstance().resume();
    } catch (std::exception &exception) {
        LOGE("Error in resume: %s", exception.what());
        JavaUtils::throwRetroException(env, ERROR_GENERIC);
    }
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_pause(
    JNIEnv* env,
    jclass obj
) {
    try {
        LibretroDroid::getInstance().pause();
    } catch (std::exception &exception) {
        LOGE("Error in pause: %s", exception.what());
        JavaUtils::throwRetroException(env, ERROR_GENERIC);
    }
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_step(
    JNIEnv* env,
    jclass obj,
    jobject glRetroView
) {
    // CRITICAL: step() calls core->retro_run() which may throw C++ exceptions (e.g. PPSSPP
    // internally calling std::terminate on unhandled errors). Without a try-catch here the
    // exception escapes the JNI boundary, the ART runtime calls std::terminate(), which calls
    // abort() → SIGABRT. Wrap the entire function body so any core exception is converted into
    // a Java RetroException that GLRetroView.catchExceptions() can handle gracefully.
    try {
        LibretroDroid::getInstance().step();

        if (LibretroDroid::getInstance().requiresVideoRefresh()) {
            LibretroDroid::getInstance().clearRequiresVideoRefresh();
            jclass cls = env->GetObjectClass(glRetroView);
            jmethodID requestAspectRatioUpdate = env->GetMethodID(cls, "refreshAspectRatio", "()V");
            env->CallVoidMethod(glRetroView, requestAspectRatioUpdate);
        }

        if (LibretroDroid::getInstance().isRumbleEnabled()) {
            LibretroDroid::getInstance().handleRumbleUpdates([&](int port, float weak, float strong) {
                jclass cls = env->GetObjectClass(glRetroView);
                jmethodID sendRumbleStrengthMethodID = env->GetMethodID(cls, "sendRumbleEvent", "(IFF)V");
                env->CallVoidMethod(glRetroView, sendRumbleStrengthMethodID, port, weak, strong);
            });
        }
    } catch (libretrodroid::LibretroDroidError& exception) {
        LOGE("LibretroDroidError in step: %s (code=%d)", exception.what(), exception.getErrorCode());
        JavaUtils::throwRetroException(env, exception.getErrorCode());
    } catch (std::exception& exception) {
        LOGE("std::exception in step: %s", exception.what());
        JavaUtils::throwRetroException(env, ERROR_GENERIC);
    } catch (...) {
        LOGE("Unknown exception in step");
        JavaUtils::throwRetroException(env, ERROR_GENERIC);
    }
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_setRumbleEnabled(
    JNIEnv* env,
    jclass obj,
    jboolean enabled
) {
    LibretroDroid::getInstance().setRumbleEnabled(enabled);
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_setFrameSpeed(
    JNIEnv* env,
    jclass obj,
    jint speed
) {
    LibretroDroid::getInstance().setFrameSpeed(speed);
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_setAudioEnabled(
    JNIEnv* env,
    jclass obj,
    jboolean enabled
) {
    LibretroDroid::getInstance().setAudioEnabled(enabled);
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_setShaderConfig(
    JNIEnv* env,
    jclass obj,
    jobject shaderConfig
) {
    LibretroDroid::getInstance().setShaderConfig(JavaUtils::shaderFromJava(env, shaderConfig));
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_setViewport(
    JNIEnv* env,
    jclass obj,
    jfloat x,
    jfloat y,
    jfloat width,
    jfloat height
) {
    LibretroDroid::getInstance().setViewport(Rect(x, y, width, height));
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_setDualScreenConfig(
    JNIEnv* env,
    jclass obj,
    jboolean enabled,
    jfloat primaryVpX,   jfloat primaryVpY,   jfloat primaryVpW,   jfloat primaryVpH,
    jfloat secondaryVpX, jfloat secondaryVpY, jfloat secondaryVpW, jfloat secondaryVpH,
    jfloat primaryUVxMin,   jfloat primaryUVyMin,   jfloat primaryUVxMax,   jfloat primaryUVyMax,
    jfloat secondaryUVxMin, jfloat secondaryUVyMin, jfloat secondaryUVxMax, jfloat secondaryUVyMax
) {
    LibretroDroid::getInstance().setDualScreenConfig(
        enabled,
        primaryVpX,   primaryVpY,   primaryVpW,   primaryVpH,
        secondaryVpX, secondaryVpY, secondaryVpW, secondaryVpH,
        primaryUVxMin,   primaryUVyMin,   primaryUVxMax,   primaryUVyMax,
        secondaryUVxMin, secondaryUVyMin, secondaryUVxMax, secondaryUVyMax
    );
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_refreshAspectRatio(
    JNIEnv* env,
    jclass obj
) {
    LibretroDroid::getInstance().refreshAspectRatio();
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_setAspectRatioOverride(
    JNIEnv* env,
    jclass obj,
    jfloat aspectRatio
) {
    LibretroDroid::getInstance().setAspectRatioOverride(aspectRatio);
}

}

}
