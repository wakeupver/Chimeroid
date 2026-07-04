/*
 * FileDescriptorKt.kt
 *
 * Copyright (C) 2017 Retrograde Project
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.swordfish.chimeroid.common.kotlin

import android.os.ParcelFileDescriptor
import timber.log.Timber

/**
 * Closes every raw Linux file-descriptor in this list that was previously detached via
 * [ParcelFileDescriptor.detachFd] (the PPSSPP-style direct-load / `/proc/self/fd/N` path).
 *
 * Each fd is adopted back into a [ParcelFileDescriptor] and closed independently: one
 * failure never prevents the rest from being released, so a single bad fd can't leak the
 * others. Safe to call on an empty list. Not idempotent by itself — callers own clearing
 * their backing list after this returns so the same fd is never closed twice.
 */
fun List<Int>.closeDetachedFds(tag: String) {
    forEach { rawFd ->
        runCatching {
            ParcelFileDescriptor.adoptFd(rawFd).close()
        }.onFailure {
            Timber.w(it, "$tag: failed to close detached fd=$rawFd")
        }
    }
}
