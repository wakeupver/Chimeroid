/*
 *     Copyright (C) 2026  Chimeroid contributors
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

package com.swordfish.libretrodroid

/**
 * User-selectable rendering backend for a [GLRetroView] session.
 *
 * [OPENGL_ES] is the default and is exactly the pre-existing behaviour: a
 * GLSurfaceView-hosted EGL context, unchanged. [VULKAN] is opt-in and
 * experimental — the loaded core is free to ignore the preference entirely
 * (RETRO_ENVIRONMENT_GET_PREFERRED_HW_RENDER is advisory only), in which case
 * software-rendered cores still render correctly through Vulkan's own
 * pixel-upload path, but a core that forces GLES hardware rendering while
 * Vulkan is selected will show a blank surface for that session rather than
 * silently falling back (see LibretroDroid::onVulkanSurfaceCreated).
 */
enum class GraphicsApi(internal val nativeValue: Int) {
    OPENGL_ES(LibretroDroid.GRAPHICS_API_OPENGL_ES),
    VULKAN(LibretroDroid.GRAPHICS_API_VULKAN),
}
