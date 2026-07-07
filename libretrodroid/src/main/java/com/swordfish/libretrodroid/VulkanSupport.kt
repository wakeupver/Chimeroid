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

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * Whether this device is a plausible candidate for [GraphicsApi.VULKAN].
 *
 * This is a coarse, cheap, side-effect-free check (a single hasSystemFeature
 * call, no native/JNI round-trip, no instance/device creation) meant for
 * gating the settings UI — it does NOT guarantee Vulkan will actually work:
 * the real, authoritative attempt happens in native VulkanContext::initialize()
 * once a game is loaded, which fails safe (falls back to a blank Vulkan
 * surface, never a crash) if this coarse check was a false positive.
 *
 * FEATURE_VULKAN_HARDWARE_LEVEL is a compile-time constant string inlined by
 * the compiler, so referencing it is safe even below API 24 despite the
 * constant itself being documented from API 24 onward; combined with the
 * explicit SDK_INT guard below, this never throws on any supported API level.
 */
object VulkanSupport {
    fun isSupported(context: Context): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL)
    }
}
