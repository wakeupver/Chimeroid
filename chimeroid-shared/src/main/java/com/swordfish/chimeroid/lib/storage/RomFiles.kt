package com.swordfish.chimeroid.lib.storage

import java.io.File

/**
 * Result of resolving a game's ROM (+ companion data files) to loadable [File] paths.
 *
 * [detachedFds] holds raw Linux file-descriptor integers detached from their
 * [android.os.ParcelFileDescriptor] wrappers via [android.os.ParcelFileDescriptor.detachFd]
 * for the PPSSPP-style direct-load path (see StorageAccessFrameworkProvider).
 *
 * This is the same technique used by PPSSPP (see ContentUri.java -> openContentUri):
 *   pfd.detachFd()  // Take ownership of the fd
 *
 * Detaching means Java GC can no longer accidentally close the fd.
 * The kernel fd stays open until someone explicitly closes it.
 * Paths in [files] that look like "/proc/self/fd/N" reference these detached fds.
 *
 * Caller MUST close every fd when the game session ends, e.g. via
 * `detachedFds.closeDetachedFds(tag)`.
 */
data class RomFiles(
    val files: List<File>,
    val detachedFds: List<Int> = emptyList(),
)
