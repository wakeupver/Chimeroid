/*
 *     Copyright (C) 2020  Filippo Scognamiglio
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

#define MODULE_NAME_CORE "Libretro Core"

#include <utility>
#include <vector>
#include <string>
#include <cstring>
#include <cmath>
#include <algorithm>
#include <EGL/egl.h>
#include <unordered_map>

#include "../../libretro-common/include/libretro.h"
#include "log.h"
#include "environment.h"
#include "vfs/vfs.h"
#include "microphone/microphoneinterface.h"

void Environment::initialize(
    const std::string &requiredSystemDirectory,
    const std::string &requiredSavesDirectory,
    retro_hw_get_current_framebuffer_t required_callback_get_current_framebuffer
) {
    callback_get_current_framebuffer = required_callback_get_current_framebuffer;
    systemDirectory = requiredSystemDirectory;
    savesDirectory = requiredSavesDirectory;
}

void Environment::deinitialize() {
    callback_get_current_framebuffer = nullptr;
    hw_context_reset = nullptr;
    hw_context_destroy = nullptr;

    retro_disk_control_callback = nullptr;

    savesDirectory = std::string();
    systemDirectory = std::string();
    language = RETRO_LANGUAGE_ENGLISH;

    pixelFormat = RETRO_PIXEL_FORMAT_RGB565;
    useHWAcceleration = false;
    useDepth = false;
    useStencil = false;
    bottomLeftOrigin = false;
    screenRotation = 0;

    gameGeometryUpdated = false;
    gameGeometryWidth = 0;
    gameGeometryHeight = 0;
    gameGeometryAspectRatio = -1.0f;
    avInfoFullUpdate = false;
    avInfoChangedCallback = nullptr;

    rumbleStates.fill(libretrodroid::RumbleState {});

    // Environment is a process-lifetime singleton reused across game/core
    // reloads. Without this, a previous core's variables/controllers persist
    // and are served alongside (or instead of) the newly loaded core's own
    // data on the next session.
    {
        std::lock_guard<std::mutex> lock(variablesMutex);
        variables.clear();
        dirtyVariables = false;
    }
    {
        std::lock_guard<std::mutex> lock(controllersMutex);
        controllers.clear();
    }
}

void Environment::updateVariable(const std::string& key, const std::string& value) {
    std::lock_guard<std::mutex> lock(variablesMutex);

    auto it = variables.find(key);
    if (it != variables.end()) {
        // Key was registered by SET_VARIABLES — update value only if changed.
        if (it->second.value != value) {
            it->second.value = value;
            dirtyVariables = true;
        }
        return;
    }
    // Key not yet registered — insert a placeholder so GET_VARIABLE can serve it.
    // (Can happen when Kotlin pushes stored prefs before the core fires SET_VARIABLES.)
    Variable v;
    v.key   = key;
    v.value = value;
    variables.emplace(key, std::move(v));
    dirtyVariables = true;
}

bool Environment::environment_handle_set_variables(const struct retro_variable* received) {
    std::lock_guard<std::mutex> lock(variablesMutex);

    for (unsigned i = 0; received[i].key != nullptr; ++i) {
        const char* rawKey  = received[i].key;
        const char* rawDesc = received[i].value;   // "Human name; val1|val2|val3"

        LOGD("Received variable %s: %s", rawKey, rawDesc);

        std::string key(rawKey);
        std::string description(rawDesc);

        // Extract the first option value as the default.
        // Format: "Human name; val1|val2|…"  — semicolon + space separate name from values.
        std::string defaultValue;
        const char* semi = (rawDesc != nullptr) ? std::strchr(rawDesc, ';') : nullptr;
        if (semi != nullptr) {
            const char* valueStart = semi + 1;
            // Skip the single space after the semicolon if present.
            if (*valueStart == ' ') ++valueStart;
            // Default value ends at the first '|' or end-of-string.
            const char* pipe = std::strchr(valueStart, '|');
            defaultValue = (pipe != nullptr)
                ? std::string(valueStart, static_cast<size_t>(pipe - valueStart))
                : std::string(valueStart);
        }

        auto& var = variables[key];   // insert default-constructed entry if absent
        var.key         = key;
        var.description = std::move(description);
        // Preserve a user-set value (written by updateVariable before SET_VARIABLES fired).
        if (var.value.empty()) {
            var.value = std::move(defaultValue);
        }

        LOGD("Variable registered %s = %s", var.key.c_str(), var.value.c_str());
    }

    return true;
}

bool Environment::environment_handle_get_variable(struct retro_variable* requested) {
    LOGD("Variable requested %s", requested->key);
    std::lock_guard<std::mutex> lock(variablesMutex);

    auto foundVariable = variables.find(std::string(requested->key));

    if (foundVariable == variables.end()) {
        return false;
    }

    requested->value = foundVariable->second.value.c_str();
    return true;
}

bool Environment::environment_handle_set_controller_info(const struct retro_controller_info* received) {
    std::lock_guard<std::mutex> lock(controllersMutex);
    controllers.clear();

    unsigned player = 0;
    while (received[player].types != nullptr) {

        auto currentPlayer = received[player];

        controllers.emplace_back();

        unsigned controller = 0;
        while (controller < currentPlayer.num_types && currentPlayer.types[controller].desc != nullptr) {
            auto currentController = currentPlayer.types[controller];
            LOGD("Received controller for player %d: %d %s", player, currentController.id, currentController.desc);

            controllers[player].push_back(Controller { currentController.id, currentController.desc });
            controller++;
        }

        player++;
    }

    return true;
}

bool Environment::environment_handle_set_hw_render(struct retro_hw_render_callback* hw_render_callback) {
    useHWAcceleration = true;
    useDepth = hw_render_callback->depth;
    useStencil = hw_render_callback->stencil;
    bottomLeftOrigin = hw_render_callback->bottom_left_origin;

    hw_context_destroy = hw_render_callback->context_destroy;
    hw_context_reset = hw_render_callback->context_reset;
    hw_render_callback->get_current_framebuffer = callback_get_current_framebuffer;
    hw_render_callback->get_proc_address = &eglGetProcAddress;

    return true;
}

bool Environment::environment_handle_get_vfs_interface(struct retro_vfs_interface_info* vfsInterfaceInfo) {
    if (!useVirtualFileSystem) {
        return false;
    }

    vfsInterfaceInfo->required_interface_version = libretrodroid::VFS::SUPPORTED_VERSION;
    vfsInterfaceInfo->iface = libretrodroid::VFS::getInterface();
    return true;
}

bool Environment::environment_handle_get_microphone_interface(struct retro_microphone_interface* microphone_interface) {
    if (!enableMicrophone) {
        return false;
    }

    *microphone_interface = *libretrodroid::MicrophoneInterface::getInterface();
    return true;
}

void Environment::callback_retro_log(enum retro_log_level level, const char *fmt, ...) {
    va_list argptr;
    va_start(argptr, fmt);

    switch (level) {
#if VERBOSE_LOGGING
        case RETRO_LOG_DEBUG:
            __android_log_vprint(ANDROID_LOG_DEBUG, MODULE_NAME_CORE, fmt, argptr);
            break;
#endif
        case RETRO_LOG_INFO:
            __android_log_vprint(ANDROID_LOG_INFO, MODULE_NAME_CORE, fmt, argptr);
            break;
        case RETRO_LOG_WARN:
            __android_log_vprint(ANDROID_LOG_WARN, MODULE_NAME_CORE, fmt, argptr);
            break;
        case RETRO_LOG_ERROR:
            __android_log_vprint(ANDROID_LOG_ERROR, MODULE_NAME_CORE, fmt, argptr);
            break;
        default:
            // Log nothing in here.
            break;
    }
}

bool Environment::callback_set_rumble_state(unsigned port, enum retro_rumble_effect effect, uint16_t strength) {
    return Environment::getInstance().handle_callback_set_rumble_state(port, effect, strength);
}

bool Environment::handle_callback_set_rumble_state(unsigned port, enum retro_rumble_effect effect, uint16_t strength) {
    LOGV("Setting rumble strength for port %i to %i", port, strength);
    if (port < 0 || port > 3) return false;

    if (effect == RETRO_RUMBLE_STRONG) {
        rumbleStates[port].strengthStrong = strength;
    } else if (effect == RETRO_RUMBLE_WEAK) {
        rumbleStates[port].strengthWeak = strength;
    }

    return true;
}

bool Environment::callback_environment(unsigned cmd, void *data) {
    return Environment::getInstance().handle_callback_environment(cmd, data);
}

bool Environment::handle_callback_environment(unsigned cmd, void *data) {
    switch (cmd) {
        case RETRO_ENVIRONMENT_GET_CAN_DUPE:
            *((bool*) data) = true;
            return true;

        case RETRO_ENVIRONMENT_SET_PIXEL_FORMAT: {
            LOGD("Called SET_PIXEL_FORMAT");
            pixelFormat = *static_cast<enum retro_pixel_format *>(data);
            return true;
        }

        case RETRO_ENVIRONMENT_SET_INPUT_DESCRIPTORS:
            LOGD("Called SET_INPUT_DESCRIPTORS");
            return false;

        case RETRO_ENVIRONMENT_GET_VARIABLE:
            LOGD("Called RETRO_ENVIRONMENT_GET_VARIABLE");
            return environment_handle_get_variable(static_cast<struct retro_variable*>(data));

        case RETRO_ENVIRONMENT_SET_VARIABLES:
            LOGD("Called RETRO_ENVIRONMENT_SET_VARIABLES");
            return environment_handle_set_variables(static_cast<const struct retro_variable*>(data));

        case RETRO_ENVIRONMENT_GET_VARIABLE_UPDATE: {
            LOGD("Called RETRO_ENVIRONMENT_GET_VARIABLE_UPDATE. Is dirty?: %d", dirtyVariables);
            *((bool*) data) = dirtyVariables;
            dirtyVariables = false;
            return true;
        }

        case RETRO_ENVIRONMENT_GET_PREFERRED_HW_RENDER: {
            LOGD("Called RETRO_ENVIRONMENT_GET_PREFERRED_HW_RENDER");
            *((unsigned*) data) = retro_hw_context_type::RETRO_HW_CONTEXT_OPENGLES3;
            return true;
        }

        case RETRO_ENVIRONMENT_SET_HW_RENDER:
            LOGD("Called RETRO_ENVIRONMENT_SET_HW_RENDER");
            return environment_handle_set_hw_render(static_cast<struct retro_hw_render_callback*>(data));

        case RETRO_ENVIRONMENT_GET_RUMBLE_INTERFACE:
            LOGD("Called RETRO_ENVIRONMENT_GET_RUMBLE_INTERFACE");
            ((struct retro_rumble_interface*) data)->set_rumble_state = &callback_set_rumble_state;
            return true;

        case RETRO_ENVIRONMENT_GET_LOG_INTERFACE:
            LOGD("Called RETRO_ENVIRONMENT_GET_LOG_INTERFACE");
            ((struct retro_log_callback*) data)->log = &callback_retro_log;
            return true;

        case RETRO_ENVIRONMENT_GET_SAVE_DIRECTORY:
            LOGD("Called RETRO_ENVIRONMENT_GET_SAVE_DIRECTORY");
            *(const char**) data = savesDirectory.c_str();
            return !savesDirectory.empty();

        case RETRO_ENVIRONMENT_GET_SYSTEM_DIRECTORY:
            LOGD("Called RETRO_ENVIRONMENT_GET_SYSTEM_DIRECTORY");
            *(const char**) data = systemDirectory.c_str();
            return !systemDirectory.empty();

        case RETRO_ENVIRONMENT_SET_ROTATION: {
            LOGD("Called RETRO_ENVIRONMENT_SET_ROTATION");
            unsigned screenRotationIndex = (*static_cast<unsigned*>(data));
            screenRotation = screenRotationIndex * (float) (-M_PI / 2.0);
            screenRotationUpdated = true;
            return true;
        }

        case RETRO_ENVIRONMENT_SET_DISK_CONTROL_INTERFACE: {
            LOGD("Called RETRO_ENVIRONMENT_SET_ROTATION");
            retro_disk_control_callback = static_cast<struct retro_disk_control_callback*>(data);
            return true;
        }

        case RETRO_ENVIRONMENT_GET_PERF_INTERFACE:
            LOGD("Called RETRO_ENVIRONMENT_GET_PERF_INTERFACE");
            return false;

        case RETRO_ENVIRONMENT_SET_SYSTEM_AV_INFO: {
            // CRITICAL: this is called from WITHIN retro_run(). PPSSPP (and other HW cores)
            // will call get_current_framebuffer() immediately after this returns.  We MUST
            // resize the FBO and call hw_context_reset right here, before we return, so the
            // next get_current_framebuffer() sees the correctly-sized FBO.
            struct retro_system_av_info *avInfo = static_cast<struct retro_system_av_info *>(data);
            // Some HW-rendered cores (flycast, like PPSSPP -- see onSurfaceCreated's
            // fboWidth/fboHeight below) report a small nominal base_width/base_height
            // regardless of the actual render resolution (flycast hardcodes 640x480 here
            // specifically "to avoid a gigantic window size at startup"), and instead put
            // the true required buffer size in max_width/max_height. Sizing the FBO from
            // base_width/height alone meant that raising the internal-resolution core
            // option mid-session shrank the FBO back down to that nominal size while the
            // core kept rendering at the real (larger) resolution into it, clipping it.
            // max_width/height default to 0 for cores that never set them, so this falls
            // back to plain base_width/height unchanged for every other core.
            //
            // This only needs to be "big enough", not exact: flycast additionally sets
            // max_width == max_height on purpose ("Use same height for rotation
            // potential" -- one square-ish buffer serves both landscape and portrait
            // without reallocating), which would make gameGeometryWidth/Height a square
            // that doesn't match the real image shape. Renderer::getValidContentFraction()
            // (FramebufferRenderer's override) handles that by cropping the display to
            // whatever fraction of this allocation flycast is actually painting each
            // frame, using its own accurate per-frame reported size -- so this can stay a
            // simple upper bound instead of trying to derive the exact true dimensions.
            gameGeometryHeight     = std::max(avInfo->geometry.base_height, avInfo->geometry.max_height);
            gameGeometryWidth      = std::max(avInfo->geometry.base_width, avInfo->geometry.max_width);
            gameGeometryAspectRatio = avInfo->geometry.aspect_ratio;
            gameGeometryUpdated    = true;
            avInfoFullUpdate       = true;

            // A core reporting a new timing.fps here (e.g. flycast's "Detect Frame Rate
            // Changes" locking onto a game running its logic at 30fps instead of 60) used
            // to be silently dropped: FPSSync is built once from the original fps in
            // afterGameLoad() and nothing ever told it to rebuild, so retro_run() kept being
            // paced at the stale rate while the core itself now expects to be driven at
            // half that -- the frontend ended up calling it twice as often as the core's own
            // new timing implies, i.e. double speed. step() rebuilds FPSSync once it sees
            // this flag (same thread/lock as the geometry flags below, so no new locking
            // needed: both are written here inside retro_run(), read afterwards in the same
            // step() call).
            avInfoFps        = avInfo->timing.fps;
            avInfoFpsUpdated = true;

            LOGD("SET_SYSTEM_AV_INFO: new geometry %dx%d, fps %f", gameGeometryWidth, gameGeometryHeight, avInfoFps);

            if (avInfoChangedCallback) {
                avInfoChangedCallback(gameGeometryWidth, gameGeometryHeight);
            } else {
                LOGW("SET_SYSTEM_AV_INFO received but no avInfoChangedCallback registered – FBO not resized!");
            }
            return true;
        }

        case RETRO_ENVIRONMENT_SET_GEOMETRY: {
            // Geometry-only change (aspect ratio / base size). Does NOT require a GL context
            // reset – step() will pick this up after retro_run() returns and update the layout.
            struct retro_game_geometry *geometry = static_cast<struct retro_game_geometry *>(data);
            gameGeometryHeight     = geometry->base_height;
            gameGeometryWidth      = geometry->base_width;
            gameGeometryAspectRatio = geometry->aspect_ratio;
            // Note: unlike SET_SYSTEM_AV_INFO above, libretro.h documents max_width/
            // max_height as "ignored and cannot be changed" via this call, so they're
            // not read here -- a spec-compliant core has no reason to keep them valid
            // in a SET_GEOMETRY-only payload.
            gameGeometryUpdated    = true;
            // avInfoFullUpdate intentionally NOT set here
            LOGD("SET_GEOMETRY: new geometry %dx%d", gameGeometryWidth, gameGeometryHeight);
            return true;
        }

        case RETRO_ENVIRONMENT_SET_CONTROLLER_INFO:
            LOGD("Called RETRO_ENVIRONMENT_SET_CONTROLLER_INFO");
            return environment_handle_set_controller_info(static_cast<const struct retro_controller_info*>(data));

        case RETRO_ENVIRONMENT_GET_AUDIO_VIDEO_ENABLE:
            LOGD("Called RETRO_ENVIRONMENT_GET_AUDIO_VIDEO_ENABLE");
            return false;

        case RETRO_ENVIRONMENT_GET_LANGUAGE:
            LOGD("Called RETRO_ENVIRONMENT_GET_LANGUAGE");
            *((unsigned*) data) = language;
            return true;

        case RETRO_ENVIRONMENT_GET_VFS_INTERFACE:
            LOGD("Called RETRO_ENVIRONMENT_GET_VFS_INTERFACE");
            return environment_handle_get_vfs_interface(static_cast<struct retro_vfs_interface_info*>(data));

        case RETRO_ENVIRONMENT_GET_MICROPHONE_INTERFACE:
            LOGD("Called RETRO_ENVIRONMENT_GET_MICROPHONE_INTERFACE");
            return environment_handle_get_microphone_interface(static_cast<struct retro_microphone_interface*>(data));

        default:
            LOGD("callback environment has been called: %u", cmd);
            return false;
    }
}

void Environment::setLanguage(const std::string& androidLanguage) {
    std::unordered_map<std::string, unsigned> languages {
            { "en", RETRO_LANGUAGE_ENGLISH },
            { "ja", RETRO_LANGUAGE_JAPANESE },
            { "fr", RETRO_LANGUAGE_FRENCH },
            { "es", RETRO_LANGUAGE_SPANISH },
            { "de", RETRO_LANGUAGE_GERMAN },
            { "it", RETRO_LANGUAGE_ITALIAN },
            { "nl", RETRO_LANGUAGE_DUTCH },
            { "pt", RETRO_LANGUAGE_PORTUGUESE_PORTUGAL },
            { "ru", RETRO_LANGUAGE_RUSSIAN },
            { "ko", RETRO_LANGUAGE_KOREAN },
            { "zh", RETRO_LANGUAGE_CHINESE_TRADITIONAL },
            { "eo", RETRO_LANGUAGE_ESPERANTO },
            { "pl", RETRO_LANGUAGE_POLISH },
            { "vi", RETRO_LANGUAGE_VIETNAMESE },
            { "ar", RETRO_LANGUAGE_ARABIC },
            { "el", RETRO_LANGUAGE_GREEK },
            { "tr", RETRO_LANGUAGE_TURKISH }
    };

    if (languages.find(androidLanguage) != languages.end()) {
        language = languages[androidLanguage];
    }
}

retro_hw_context_reset_t Environment::getHwContextReset() const {
    return hw_context_reset;
}

retro_hw_context_reset_t Environment::getHwContextDestroy() const {
    return hw_context_destroy;
}

struct retro_disk_control_callback* Environment::getRetroDiskControlCallback() const {
    return retro_disk_control_callback;
}

int Environment::getPixelFormat() const {
    return pixelFormat;
}

bool Environment::isUseHwAcceleration() const {
    return useHWAcceleration;
}

bool Environment::isUseDepth() const {
    return useDepth;
}

bool Environment::isUseStencil() const {
    return useStencil;
}

bool Environment::isBottomLeftOrigin() const {
    return bottomLeftOrigin;
}

float Environment::getScreenRotation() const {
    return screenRotation;
}

bool Environment::isGameGeometryUpdated() const {
    return gameGeometryUpdated;
}

void Environment::clearGameGeometryUpdated() {
    gameGeometryUpdated = false;
}

unsigned int Environment::getGameGeometryWidth() const {
    return gameGeometryWidth;
}

unsigned int Environment::getGameGeometryHeight() const {
    return gameGeometryHeight;
}

float Environment::getGameGeometryAspectRatio() const {
    return gameGeometryAspectRatio;
}

const std::vector<struct Variable> Environment::getVariables() const {
    std::vector<struct Variable> result;
    {
        std::lock_guard<std::mutex> lock(variablesMutex);
        result.reserve(variables.size());
        for (const auto& [key, var] : variables) {
            result.push_back(var);
        }
    }

    std::sort(
        result.begin(),
        result.end(),
        [](const struct Variable& v1, const struct Variable& v2) {
            return v1.key < v2.key;
        }
    );

    return result;
}

std::vector<std::vector<struct Controller>> Environment::getControllers() const {
    std::lock_guard<std::mutex> lock(controllersMutex);
    return controllers;
}

float Environment::retrieveGameSpecificAspectRatio() {
    if (getGameGeometryAspectRatio() > 0) {
        return getGameGeometryAspectRatio();
    }

    if (getGameGeometryWidth() > 0 && getGameGeometryHeight() > 0) {
        return (float) getGameGeometryWidth() / (float) getGameGeometryHeight();
    }

    return -1.0f;
}

bool Environment::isScreenRotationUpdated() const {
    return screenRotationUpdated;
}

void Environment::clearScreenRotationUpdated() {
    screenRotationUpdated = false;
}

std::array<libretrodroid::RumbleState, 4>& Environment::getLastRumbleStates() {
    return rumbleStates;
}

void Environment::setEnableVirtualFileSystem(bool value) {
    this->useVirtualFileSystem = value;
}

void Environment::setEnableMicrophone(bool value) {
    this->enableMicrophone = value;
}

void Environment::setAVInfoChangedCallback(AVInfoChangedCallback callback) {
    avInfoChangedCallback = std::move(callback);
}

bool Environment::isAVInfoFullUpdate() const {
    return avInfoFullUpdate;
}

void Environment::clearAVInfoFullUpdate() {
    avInfoFullUpdate = false;
}

double Environment::getAVInfoFps() const {
    return avInfoFps;
}

bool Environment::isAVInfoFpsUpdated() const {
    return avInfoFpsUpdated;
}

void Environment::clearAVInfoFpsUpdated() {
    avInfoFpsUpdated = false;
}
