package com.swordfish.chimeroid.app.shared

object GameMenuContract {
    const val EXTRA_GAME = "EXTRA_GAME"
    const val EXTRA_SYSTEM_CORE_CONFIG = "EXTRA_SYSTEM_CORE_CONFIG"
    const val EXTRA_CURRENT_DISK = "EXTRA_CURRENT_DISK"
    const val EXTRA_DISKS = "EXTRA_DISKS"
    const val EXTRA_CORE_OPTIONS = "EXTRA_CORE_OPTIONS"
    const val EXTRA_ADVANCED_CORE_OPTIONS = "EXTRA_ADVANCED_CORE_OPTIONS"
    const val EXTRA_AUTO_DETECTED_CORE_OPTIONS = "EXTRA_AUTO_DETECTED_CORE_OPTIONS"
    const val EXTRA_AUDIO_ENABLED = "EXTRA_AUDIO_ENABLED"
    const val EXTRA_FAST_FORWARD_SUPPORTED = "EXTRA_FAST_FORWARD_SUPPORTED"
    const val EXTRA_FAST_FORWARD = "EXTRA_FAST_FORWARD"
    const val EXTRA_CURRENT_TILT_CONFIG = "EXTRA_CURRENT_TILT_CONFIG"
    const val EXTRA_TILT_ALL_CONFIGS = "EXTRA_TILT_ALL_CONFIGS"
    const val EXTRA_CURRENT_TOUCH_CONTROLLER_ID = "EXTRA_CURRENT_TOUCH_CONTROLLER_ID"

    const val RESULT_RESET = "RESULT_RESET"
    const val RESULT_SAVE = "RESULT_SAVE"
    const val RESULT_LOAD = "RESULT_LOAD"
    const val RESULT_QUIT = "RESULT_QUIT"
    const val RESULT_CHANGE_DISK = "RESULT_CHANGE_DISK"
    const val RESULT_EDIT_TOUCH_CONTROLS = "RESULT_EDIT_TOUCH_CONTROLS"

    /**
     * Returned when the user taps "Position Macros" on the Game Menu's Macros screen.
     * The host activity should dismiss the menu and enter macro drag-positioning mode
     * directly on the live game screen (see BaseGameScreenViewModel.enterMacroDragMode).
     */
    const val RESULT_POSITION_MACROS = "RESULT_POSITION_MACROS"
    const val RESULT_ENABLE_AUDIO = "RESULT_ENABLE_AUDIO"
    const val RESULT_ENABLE_FAST_FORWARD = "RESULT_ENABLE_FAST_FORWARD"
    const val RESULT_CHANGE_TILT_CONFIG = "RESULT_CHANGE_TILT_CONFIG"

    /**
     * Returned from the TV game menu when the user taps "Patch Codes".
     * The host activity should open the patch codes management UI.
     */
    const val RESULT_OPEN_PATCH_CODES = "RESULT_OPEN_PATCH_CODES"
}
