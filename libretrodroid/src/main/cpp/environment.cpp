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

    retro_disk_control_callback_copy = {};
    retro_disk_control_available = false;

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

    gameTimingUpdated = false;
    gameTimingFps = 0.0;
    gameTimingSampleRate = 0.0;

    rumbleStates.fill(libretrodroid::RumbleState {});
}

void Environment::updateVariable(const std::string& key, const std::string& value) {
    auto current = variables[key];
    current.key = key;

    if (value != current.value) {
        current.value = value;
        variables[key] = current;
        dirtyVariables = true;
    }
}

bool Environment::environment_handle_set_variables(const struct retro_variable* received) {
    unsigned count = 0;
    while (received[count].key != nullptr) {
        LOGD("Received variable %s: %s", received[count].key, received[count].value);

        std::string key(received[count].key);
        std::string description(received[count].value);
        std::string value(received[count].value);

        auto firstValueStart = value.find(';') + 2;
        auto firstValueEnd = value.find('|', firstValueStart);
        value = value.substr(firstValueStart, firstValueEnd - firstValueStart);

        auto currentVariable = variables[key];
        currentVariable.key = key;
        currentVariable.description = description;

        if (currentVariable.value.empty()) {
            currentVariable.value = value;
        }

        variables[key] = currentVariable;
        LOGD("Assigning variable %s: %s", currentVariable.key.c_str(), currentVariable.value.c_str());

        count++;
    }

    return true;
}

bool Environment::environment_handle_get_variable(struct retro_variable* requested) {
    LOGD("Variable requested %s", requested->key);
    auto foundVariable = variables.find(std::string(requested->key));

    if (foundVariable == variables.end()) {
        return false;
    }

    requested->value = foundVariable->second.value.c_str();
    return true;
}

bool Environment::environment_handle_set_controller_info(const struct retro_controller_info* received) {
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
    // Android only supports OpenGL ES — desktop GL contexts do not exist here.
    // Cores such as SwanStation request RETRO_HW_CONTEXT_OPENGL_CORE (3) or
    // RETRO_HW_CONTEXT_OPENGL (1) when the user picks the "OpenGL" renderer,
    // ignoring our RETRO_ENVIRONMENT_GET_PREFERRED_HW_RENDER hint.  If we
    // accept those context types unchanged the core compiles shaders with
    // desktop-GL "#version NNN core" directives that Adreno GLES rejects with
    // "ERROR: Invalid #version", producing a permanent black screen.
    //
    // Fix: transparently remap any desktop-GL context type to OPENGLES3.
    // GLSurfaceView provides a real GLES 3.x context, so the mapping is valid.
    // SwanStation reads hw_render_callback->context_type back after SET_HW_RENDER
    // and will switch to its GLES3 shader compilation path.
    const auto requested = hw_render_callback->context_type;
    if (requested == RETRO_HW_CONTEXT_OPENGL || requested == RETRO_HW_CONTEXT_OPENGL_CORE) {
        LOGD("SET_HW_RENDER: remapping desktop GL context type %u -> OPENGLES3", (unsigned)requested);
        hw_render_callback->context_type = RETRO_HW_CONTEXT_OPENGLES3;
    }

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
            LOGD("Called RETRO_ENVIRONMENT_SET_DISK_CONTROL_INTERFACE");
            // Copy by value: the core may pass a pointer to a stack-allocated struct
            // (SwanStation does this).  Storing the raw pointer causes a dangling-pointer
            // crash the next time getRetroDiskControlCallback() is called (e.g. when the
            // in-game menu is opened).
            if (data != nullptr) {
                retro_disk_control_callback_copy =
                        *static_cast<struct retro_disk_control_callback *>(data);
                retro_disk_control_available = true;
            } else {
                retro_disk_control_available = false;
            }
            return true;
        }

        case RETRO_ENVIRONMENT_SET_DISK_CONTROL_EXT_INTERFACE: {
            LOGD("Called RETRO_ENVIRONMENT_SET_DISK_CONTROL_EXT_INTERFACE");
            // SwanStation (and other modern cores) use the extended interface.
            // Map the first seven fields — which are identical to the base
            // retro_disk_control_callback — so the rest of the code can call
            // them through the existing callback struct without modification.
            if (data != nullptr) {
                const auto *ext =
                        static_cast<const struct retro_disk_control_ext_callback *>(data);
                retro_disk_control_callback_copy.set_eject_state    = ext->set_eject_state;
                retro_disk_control_callback_copy.get_eject_state    = ext->get_eject_state;
                retro_disk_control_callback_copy.get_image_index    = ext->get_image_index;
                retro_disk_control_callback_copy.set_image_index    = ext->set_image_index;
                retro_disk_control_callback_copy.get_num_images     = ext->get_num_images;
                retro_disk_control_callback_copy.replace_image_index = ext->replace_image_index;
                retro_disk_control_callback_copy.add_image_index    = ext->add_image_index;
                retro_disk_control_available = true;
            } else {
                retro_disk_control_available = false;
            }
            return true;
        }

        case RETRO_ENVIRONMENT_GET_PERF_INTERFACE:
            LOGD("Called RETRO_ENVIRONMENT_GET_PERF_INTERFACE");
            return false;

            // TODO... RETRO_ENVIRONMENT_SET_SYSTEM_AV_INFO can also change frame-rate
        case RETRO_ENVIRONMENT_SET_SYSTEM_AV_INFO: {
            // SET_SYSTEM_AV_INFO carries a full retro_system_av_info (geometry + timing).
            // Previously this was merged with SET_GEOMETRY and the timing.fps was silently
            // discarded.  That caused games like Jet Grind Radio and Crazy Taxi (which run
            // at 30 fps internally) to play at 2× speed: Flycast initially reports 60 fps
            // from retro_get_system_av_info(), then calls SET_SYSTEM_AV_INFO with 30 fps
            // after loading the disc.  Without this fix, fpsSync was never updated, so
            // useVSync=true and retro_run() was called 60 times/sec instead of 30.
            struct retro_system_av_info *av_info = static_cast<struct retro_system_av_info *>(data);
            gameGeometryHeight      = av_info->geometry.base_height;
            gameGeometryWidth       = av_info->geometry.base_width;
            gameGeometryAspectRatio = av_info->geometry.aspect_ratio;
            gameGeometryUpdated     = true;
            if (av_info->timing.fps > 0.0) {
                gameTimingFps        = av_info->timing.fps;
                gameTimingSampleRate = av_info->timing.sample_rate;
                gameTimingUpdated    = true;
            }
            return true;
        }

        case RETRO_ENVIRONMENT_SET_GEOMETRY: {
            struct retro_game_geometry *geometry = static_cast<struct retro_game_geometry *>(data);
            gameGeometryHeight = geometry->base_height;
            gameGeometryWidth = geometry->base_width;
            gameGeometryAspectRatio = geometry->aspect_ratio;
            gameGeometryUpdated = true;
            return true;
        }

        case RETRO_ENVIRONMENT_SET_CONTROLLER_INFO:
            LOGD("Called RETRO_ENVIRONMENT_SET_CONTROLLER_INFO");
            return environment_handle_set_controller_info(static_cast<const struct retro_controller_info*>(data));

        // ── Messages ──────────────────────────────────────────────────────────
        case RETRO_ENVIRONMENT_SET_MESSAGE:
            LOGD("Called RETRO_ENVIRONMENT_SET_MESSAGE");
            return true;  // acknowledge; no on-screen display yet

        case RETRO_ENVIRONMENT_GET_MESSAGE_INTERFACE_VERSION: {
            LOGD("Called RETRO_ENVIRONMENT_GET_MESSAGE_INTERFACE_VERSION");
            *((unsigned*) data) = 1;
            return true;
        }

        case RETRO_ENVIRONMENT_SET_MESSAGE_EXT:
            LOGD("Called RETRO_ENVIRONMENT_SET_MESSAGE_EXT");
            return true;

        // ── Core options ──────────────────────────────────────────────────────
        // SwanStation queries GET_CORE_OPTIONS_VERSION first to decide which
        // API to use.  Returning version 2 lets it use SET_CORE_OPTIONS_V2,
        // which we now handle properly.
        case RETRO_ENVIRONMENT_GET_CORE_OPTIONS_VERSION: {
            LOGD("Called RETRO_ENVIRONMENT_GET_CORE_OPTIONS_VERSION");
            *((unsigned*) data) = 2;
            return true;
        }

        case RETRO_ENVIRONMENT_SET_CORE_OPTIONS:
            LOGD("Called RETRO_ENVIRONMENT_SET_CORE_OPTIONS");
            return environment_handle_set_core_options(
                static_cast<const struct retro_core_option_definition*>(data));

        case RETRO_ENVIRONMENT_SET_CORE_OPTIONS_INTL: {
            LOGD("Called RETRO_ENVIRONMENT_SET_CORE_OPTIONS_INTL");
            auto* intl = static_cast<const struct retro_core_options_intl*>(data);
            if (intl && intl->us)
                return environment_handle_set_core_options(intl->us);
            return false;
        }

        case RETRO_ENVIRONMENT_SET_CORE_OPTIONS_V2:
            LOGD("Called RETRO_ENVIRONMENT_SET_CORE_OPTIONS_V2");
            return environment_handle_set_core_options_v2(
                static_cast<const struct retro_core_options_v2*>(data));

        case RETRO_ENVIRONMENT_SET_CORE_OPTIONS_V2_INTL: {
            LOGD("Called RETRO_ENVIRONMENT_SET_CORE_OPTIONS_V2_INTL");
            auto* intl = static_cast<const struct retro_core_options_v2_intl*>(data);
            if (intl && intl->us)
                return environment_handle_set_core_options_v2(intl->us);
            return false;
        }

        // Visibility control — acknowledge without hiding anything.
        case RETRO_ENVIRONMENT_SET_CORE_OPTIONS_DISPLAY:
            LOGD("Called RETRO_ENVIRONMENT_SET_CORE_OPTIONS_DISPLAY");
            return true;

        case RETRO_ENVIRONMENT_SET_CORE_OPTIONS_UPDATE_DISPLAY_CALLBACK:
            LOGD("Called RETRO_ENVIRONMENT_SET_CORE_OPTIONS_UPDATE_DISPLAY_CALLBACK");
            return false;

        // ── Miscellaneous queries SwanStation makes ────────────────────────
        case RETRO_ENVIRONMENT_SET_SUPPORT_NO_GAME:
            LOGD("Called RETRO_ENVIRONMENT_SET_SUPPORT_NO_GAME");
            return true;

        case RETRO_ENVIRONMENT_GET_LIBRETRO_PATH:
            LOGD("Called RETRO_ENVIRONMENT_GET_LIBRETRO_PATH");
            return false;

        case RETRO_ENVIRONMENT_GET_CORE_ASSETS_DIRECTORY:
            LOGD("Called RETRO_ENVIRONMENT_GET_CORE_ASSETS_DIRECTORY");
            return false;

        // SwanStation sets quirks before retro_serialize(); just acknowledge.
        case RETRO_ENVIRONMENT_SET_SERIALIZATION_QUIRKS:
            LOGD("Called RETRO_ENVIRONMENT_SET_SERIALIZATION_QUIRKS");
            return true;

        case RETRO_ENVIRONMENT_SET_SUPPORT_ACHIEVEMENTS | RETRO_ENVIRONMENT_EXPERIMENTAL:
            LOGD("Called RETRO_ENVIRONMENT_SET_SUPPORT_ACHIEVEMENTS");
            return true;

        case RETRO_ENVIRONMENT_GET_FASTFORWARDING | RETRO_ENVIRONMENT_EXPERIMENTAL:
            LOGD("Called RETRO_ENVIRONMENT_GET_FASTFORWARDING");
            if (data) *((bool*) data) = false;
            return true;

        // Return RETRO_SAVESTATE_CONTEXT_NORMAL (0) so SwanStation does not
        // enable runahead-specific optimisations that require a shared context.
        case RETRO_ENVIRONMENT_GET_SAVESTATE_CONTEXT | RETRO_ENVIRONMENT_EXPERIMENTAL:
            LOGD("Called RETRO_ENVIRONMENT_GET_SAVESTATE_CONTEXT");
            if (data) *((int*) data) = 0;
            return true;

        // Shared GL context: we cannot provide one, SwanStation falls back to
        // single-threaded GL uploads which is safe.
        case RETRO_ENVIRONMENT_SET_HW_SHARED_CONTEXT | RETRO_ENVIRONMENT_EXPERIMENTAL:
            LOGD("Called RETRO_ENVIRONMENT_SET_HW_SHARED_CONTEXT");
            return false;

        case RETRO_ENVIRONMENT_SET_CONTENT_INFO_OVERRIDE:
            LOGD("Called RETRO_ENVIRONMENT_SET_CONTENT_INFO_OVERRIDE");
            return false;

        case RETRO_ENVIRONMENT_SET_HW_RENDER_CONTEXT_NEGOTIATION_INTERFACE | RETRO_ENVIRONMENT_EXPERIMENTAL:
            LOGD("Called RETRO_ENVIRONMENT_SET_HW_RENDER_CONTEXT_NEGOTIATION_INTERFACE");
            return false;

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
            { "jp", RETRO_LANGUAGE_JAPANESE },
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
    if (!retro_disk_control_available) return nullptr;
    // Cast away const: the struct is stored by value in this object and the
    // callers need a non-const pointer to invoke the function pointers inside.
    return const_cast<struct retro_disk_control_callback *>(&retro_disk_control_callback_copy);
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

bool Environment::isGameTimingUpdated() const {
    return gameTimingUpdated;
}

void Environment::clearGameTimingUpdated() {
    gameTimingUpdated = false;
}

double Environment::getGameTimingFps() const {
    return gameTimingFps;
}

double Environment::getGameTimingSampleRate() const {
    return gameTimingSampleRate;
}

const std::vector<struct Variable> Environment::getVariables() const {
    std::vector<struct Variable> result;

    std::for_each(
        variables.begin(),
        variables.end(),
        [&](std::pair<std::string, struct Variable> item) {
            result.push_back(item.second);
        }
    );

    std::sort(
        result.begin(),
        result.end(),
        [](struct Variable v1, struct Variable v2) {
            return v1.key < v2.key;
        }
    );

    return result;
}

const std::vector<std::vector<struct Controller>> &Environment::getControllers() const {
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

// ---------------------------------------------------------------------------
// Core options v1  (RETRO_ENVIRONMENT_SET_CORE_OPTIONS)
// ---------------------------------------------------------------------------
// Iterates the null-terminated array of retro_core_option_definition and
// populates our variables map.  Only the default value is stored initially;
// user overrides applied earlier via updateVariable() are preserved.
bool Environment::environment_handle_set_core_options(
        const struct retro_core_option_definition* defs) {
    if (!defs) return false;

    for (unsigned i = 0; defs[i].key != nullptr; i++) {
        std::string key(defs[i].key);

        auto& var = variables[key];
        var.key = key;
        if (defs[i].desc) var.description = std::string(defs[i].desc);

        // Preserve any value already set by the user.
        if (var.value.empty()) {
            if (defs[i].default_value && defs[i].default_value[0] != '\0') {
                var.value = std::string(defs[i].default_value);
            } else if (defs[i].values[0].value != nullptr) {
                // Fall back to the first valid value in the array.
                var.value = std::string(defs[i].values[0].value);
            }
        }

        LOGD("Core option (v1) %s = %s", var.key.c_str(), var.value.c_str());
    }
    return true;
}

// ---------------------------------------------------------------------------
// Core options v2  (RETRO_ENVIRONMENT_SET_CORE_OPTIONS_V2)
// ---------------------------------------------------------------------------
// SwanStation exclusively uses the v2 API.  The definitions list is
// null-terminated on key == nullptr, identical to v1 semantics but with an
// extra category field and an additional info_categories string.
bool Environment::environment_handle_set_core_options_v2(
        const struct retro_core_options_v2* opts) {
    if (!opts || !opts->definitions) return false;

    const struct retro_core_option_v2_definition* defs = opts->definitions;
    for (unsigned i = 0; defs[i].key != nullptr; i++) {
        std::string key(defs[i].key);

        auto& var = variables[key];
        var.key = key;
        if (defs[i].desc) var.description = std::string(defs[i].desc);

        if (var.value.empty()) {
            if (defs[i].default_value && defs[i].default_value[0] != '\0') {
                var.value = std::string(defs[i].default_value);
            } else if (defs[i].values[0].value != nullptr) {
                var.value = std::string(defs[i].values[0].value);
            }
        }

        LOGD("Core option (v2) %s = %s", var.key.c_str(), var.value.c_str());
    }
    return true;
}
