package com.swordfish.chimeroid.lib.storage

import java.io.File

data class RomFiles(
    val files: List<File>,
    val detachedFds: List<Int> = emptyList(),
)
