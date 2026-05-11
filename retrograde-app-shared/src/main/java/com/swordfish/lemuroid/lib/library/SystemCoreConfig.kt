package com.swordfish.lemuroid.lib.library

import com.swordfish.lemuroid.lib.controller.ControllerConfig
import com.swordfish.lemuroid.lib.core.CoreVariable
import java.io.Serializable

data class SystemCoreConfig(
    val coreID: CoreID,
    val controllerConfigs: HashMap<Int, ArrayList<ControllerConfig>>,
    val exposedSettings: List<ExposedSetting> = listOf(),
    val exposedAdvancedSettings: List<ExposedSetting> = listOf(),
    val defaultSettings: List<CoreVariable> = listOf(),
    val statesSupported: Boolean = true,
    val rumbleSupported: Boolean = false,
    val requiredBIOSFiles: List<String> = listOf(),
    /**
     * When non-empty, at least ONE file from this list must exist in the system directory.
     * This is distinct from [requiredBIOSFiles] (ALL must exist) and is used for systems
     * like PSX where any single regional BIOS (scph5500.bin / scph5501.bin / scph5502.bin)
     * or a fallback open-source BIOS (openbios.bin) is sufficient to boot games.
     */
    val requiredBIOSFilesAnyOf: List<String> = listOf(),
    val regionalBIOSFiles: Map<String, String> = mapOf(),
    val statesVersion: Int = 0,
    val skipDuplicateFrames: Boolean = true,
    val supportedOnlyArchitectures: Set<String>? = null,
    val supportsMicrophone: Boolean = false,
    /**
     * When true, all core variables reported by the libretro core are automatically shown
     * in the Core Options menu — similar to RetroArch's behaviour. Variables that are
     * already listed in [exposedSettings] or [exposedAdvancedSettings] keep their
     * localised title and filtered value list; every other variable is shown with its
     * native description string and the full value list reported by the core.
     *
     * Defaults to false so that every core's options screen only shows the curated
     * [exposedSettings] / [exposedAdvancedSettings] list. Set to true explicitly for
     * cores (e.g. SwanStation/PSX) where you want the full RetroArch-style variable dump.
     */
    val autoDetectSettings: Boolean = false,
    /**
     * Minimum OpenGL ES version the core requires.
     * Passed to [GLRetroViewData.requiredGLESVersion] so the EGL context is created
     * with the correct version. Defaults to 2 (standard GLES 2 context).
     * Set to 3 for cores that require GLES 3 (e.g. SwanStation).
     */
    val requiredGLESVersion: Int = 2,
) : Serializable
