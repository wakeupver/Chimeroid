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

#ifndef LIBRETRODROID_GRAPHICSAPI_H
#define LIBRETRODROID_GRAPHICSAPI_H

// Single source of truth for the user-selectable rendering backend.
// Kept as a standalone header (rather than nested inside Environment or
// LibretroDroid) because it is shared verbatim by the JNI boundary, the
// environment negotiation layer and the orchestrator, without forcing any
// of them to include each other's full headers.
enum class GraphicsApi {
    OPENGL_ES = 0,
    VULKAN = 1,
};

#endif //LIBRETRODROID_GRAPHICSAPI_H
