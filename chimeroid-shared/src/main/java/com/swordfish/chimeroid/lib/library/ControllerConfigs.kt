package com.swordfish.chimeroid.lib.library

import com.swordfish.chimeroid.lib.R
import com.swordfish.chimeroid.lib.controller.ControllerConfig
import com.swordfish.touchinput.radial.sensors.TILT_CONFIGURATION_ANALOG_LEFT
import com.swordfish.touchinput.radial.sensors.TILT_CONFIGURATION_ANALOG_RIGHT
import com.swordfish.touchinput.radial.sensors.TILT_CONFIGURATION_CROSS
import com.swordfish.touchinput.radial.sensors.TILT_CONFIGURATION_DISABLED
import com.swordfish.touchinput.radial.sensors.TILT_CONFIGURATION_L1_R1
import com.swordfish.touchinput.radial.sensors.TILT_CONFIGURATION_L2_R2
import com.swordfish.touchinput.radial.sensors.TILT_CONFIGURATION_L_R
import com.swordfish.touchinput.radial.settings.TouchControllerID

// TODO PADS... Make sure the ids are correct.
object ControllerConfigs {
    val ATARI_2600 =
        ControllerConfig(
            "default",
            R.string.controller_default,
            TouchControllerID.ATARI2600,
            mergeDPADAndLeftStickEvents = true,
            tiltConfigurations =
                listOf(
                    TILT_CONFIGURATION_DISABLED,
                    TILT_CONFIGURATION_CROSS,
                ),
        )

    val NES =
        ControllerConfig(
            "default",
            R.string.controller_default,
            TouchControllerID.NES,
            mergeDPADAndLeftStickEvents = true,
            tiltConfigurations =
                listOf(
                    TILT_CONFIGURATION_DISABLED,
                    TILT_CONFIGURATION_CROSS,
                ),
        )

    val SNES =
        ControllerConfig(
            "default",
            R.string.controller_default,
            TouchControllerID.SNES,
            mergeDPADAndLeftStickEvents = true,
            tiltConfigurations =
                listOf(
                    TILT_CONFIGURATION_DISABLED,
                    TILT_CONFIGURATION_CROSS,
                    TILT_CONFIGURATION_L_R,
                ),
        )

    val SMS =
        ControllerConfig(
            "default",
            R.string.controller_default,
            TouchControllerID.SMS,
            mergeDPADAndLeftStickEvents = true,
            tiltConfigurations =
                listOf(
                    TILT_CONFIGURATION_DISABLED,
                    TILT_CONFIGURATION_CROSS,
                ),
        )

    val GENESIS_6 =
        ControllerConfig(
            "default_6",
            R.string.controller_genesis_6,
            TouchControllerID.GENESIS_6,
            mergeDPADAndLeftStickEvents = true,
            libretroDescriptor = "MD Joypad 6 Button",
            tiltConfigurations =
                listOf(
                    TILT_CONFIGURATION_DISABLED,
                    TILT_CONFIGURATION_CROSS,
                ),
        )

    val GENESIS_3 =
        ControllerConfig(
            "default_3",
            R.string.controller_genesis_3,
            TouchControllerID.GENESIS_3,
            mergeDPADAndLeftStickEvents = true,
            libretroDescriptor = "MD Joypad 3 Button",
            tiltConfigurations =
                listOf(
                    TILT_CONFIGURATION_DISABLED,
                    TILT_CONFIGURATION_CROSS,
                ),
        )

    val GG =
        ControllerConfig(
            "default",
            R.string.controller_default,
            TouchControllerID.GG,
            mergeDPADAndLeftStickEvents = true,
            tiltConfigurations =
                listOf(
                    TILT_CONFIGURATION_DISABLED,
                    TILT_CONFIGURATION_CROSS,
                ),
        )

    val GB =
        ControllerConfig(
            "default",
            R.string.controller_default,
            TouchControllerID.GB,
            mergeDPADAndLeftStickEvents = true,
            tiltConfigurations =
                listOf(
                    TILT_CONFIGURATION_DISABLED,
                    TILT_CONFIGURATION_CROSS,
                ),
        )

    val GBA =
        ControllerConfig(
            "default",
            R.string.controller_default,
            TouchControllerID.GBA,
            mergeDPADAndLeftStickEvents = true,
            tiltConfigurations =
                listOf(
                    TILT_CONFIGURATION_DISABLED,
                    TILT_CONFIGURATION_CROSS,
                    TILT_CONFIGURATION_L_R,
                ),
        )

    val N64 =
        ControllerConfig(
            "default",
            R.string.controller_default,
            TouchControllerID.N64,
            allowTouchRotation = true,
            tiltConfigurations =
                listOf(
                    TILT_CONFIGURATION_DISABLED,
                    TILT_CONFIGURATION_CROSS,
                    TILT_CONFIGURATION_ANALOG_LEFT,
                    TILT_CONFIGURATION_L_R,
                ),
        )

    val PSX_STANDARD =
        ControllerConfig(
            "standard",
            R.string.controller_standard,
            TouchControllerID.PSX,
            mergeDPADAndLeftStickEvents = true,
            libretroDescriptor = "standard",
            tiltConfigurations =
                listOf(
                    TILT_CONFIGURATION_DISABLED,
                    TILT_CONFIGURATION_CROSS,
                    TILT_CONFIGURATION_L1_R1,
                    TILT_CONFIGURATION_L2_R2,
                ),
        )

    val PSX_DUALSHOCK =
        ControllerConfig(
            "dualshock",
            R.string.controller_dualshock,
            TouchControllerID.PSX_DUALSHOCK,
            allowTouchRotation = true,
            libretroDescriptor = "dualshock",
            tiltConfigurations =
                listOf(
                    TILT_CONFIGURATION_DISABLED,
                    TILT_CONFIGURATION_CROSS,
                    TILT_CONFIGURATION_ANALOG_LEFT,
                    TILT_CONFIGURATION_ANALOG_RIGHT,
                    TILT_CONFIGURATION_L1_R1,
                    TILT_CONFIGURATION_L2_R2,
                ),
        )

    val PSP =
        ControllerConfig(
            "default",
            R.string.controller_default,
            TouchControllerID.PSP,
            allowTouchRotation = true,
            tiltConfigurations =
                listOf(
                    TILT_CONFIGURATION_DISABLED,
                    TILT_CONFIGURATION_CROSS,
                    TILT_CONFIGURATION_ANALOG_LEFT,
                    TILT_CONFIGURATION_L_R,
                ),
        )

    val DREAMCAST =
        ControllerConfig(
            "default",
            R.string.controller_default,
            TouchControllerID.DREAMCAST,
            allowTouchRotation = true,
            // flycast's real controller_info (shell/libretro/libretro.cpp) has no
            // "RetroPad" entry; port 0's actual descriptor is {"Controller",
            // RETRO_DEVICE_JOYPAD}. Matching it is what makes findControllerId()
            // resolve and setControllerType() actually fire -- without it,
            // retro_set_controller_port_device() is never called at all (nothing
            // else in libretrodroid calls it), so flycast never assigns
            // MDT_SegaController to the port and neither the controller nor its
            // VMU expansion slot get attached to the maple bus.
            libretroId = 1, // RETRO_DEVICE_JOYPAD
            libretroDescriptor = "Controller",
            tiltConfigurations =
                listOf(
                    TILT_CONFIGURATION_DISABLED,
                    TILT_CONFIGURATION_CROSS,
                    TILT_CONFIGURATION_ANALOG_LEFT,
                    TILT_CONFIGURATION_L_R,
                ),
        )

    val FB_NEO_4 =
        ControllerConfig(
            "default_4",
            R.string.controller_arcade_4,
            TouchControllerID.ARCADE_4,
            mergeDPADAndLeftStickEvents = true,
            tiltConfigurations =
                listOf(
                    TILT_CONFIGURATION_DISABLED,
                    TILT_CONFIGURATION_CROSS,
                ),
        )

    val FB_NEO_6 =
        ControllerConfig(
            "default_6",
            R.string.controller_arcade_6,
            TouchControllerID.ARCADE_6,
            mergeDPADAndLeftStickEvents = true,
            tiltConfigurations =
                listOf(
                    TILT_CONFIGURATION_DISABLED,
                    TILT_CONFIGURATION_CROSS,
                ),
        )

    val MAME_2003_4 =
        ControllerConfig(
            "default_4",
            R.string.controller_arcade_4,
            TouchControllerID.ARCADE_4,
            mergeDPADAndLeftStickEvents = true,
            tiltConfigurations =
                listOf(
                    TILT_CONFIGURATION_DISABLED,
                    TILT_CONFIGURATION_CROSS,
                ),
        )

    val MAME_2003_6 =
        ControllerConfig(
            "default_6",
            R.string.controller_arcade_6,
            TouchControllerID.ARCADE_6,
            mergeDPADAndLeftStickEvents = true,
            tiltConfigurations =
                listOf(
                    TILT_CONFIGURATION_DISABLED,
                    TILT_CONFIGURATION_CROSS,
                ),
        )

    val MELONDS =
        ControllerConfig(
            "default",
            R.string.controller_default,
            TouchControllerID.MELONDS,
            mergeDPADAndLeftStickEvents = true,
            allowTouchOverlay = false,
            tiltConfigurations =
                listOf(
                    TILT_CONFIGURATION_DISABLED,
                    TILT_CONFIGURATION_CROSS,
                    TILT_CONFIGURATION_L_R,
                ),
        )

    val LYNX =
        ControllerConfig(
            "default",
            R.string.controller_default,
            TouchControllerID.LYNX,
            mergeDPADAndLeftStickEvents = true,
            tiltConfigurations =
                listOf(
                    TILT_CONFIGURATION_DISABLED,
                    TILT_CONFIGURATION_CROSS,
                ),
        )

    val ATARI7800 =
        ControllerConfig(
            "default",
            R.string.controller_default,
            TouchControllerID.ATARI7800,
            mergeDPADAndLeftStickEvents = true,
            tiltConfigurations =
                listOf(
                    TILT_CONFIGURATION_DISABLED,
                    TILT_CONFIGURATION_CROSS,
                ),
        )

    val PCE =
        ControllerConfig(
            "default",
            R.string.controller_default,
            TouchControllerID.PCE,
            mergeDPADAndLeftStickEvents = true,
            tiltConfigurations =
                listOf(
                    TILT_CONFIGURATION_DISABLED,
                    TILT_CONFIGURATION_CROSS,
                    TILT_CONFIGURATION_L_R,
                ),
        )

    val NGP =
        ControllerConfig(
            "default",
            R.string.controller_default,
            TouchControllerID.NGP,
            mergeDPADAndLeftStickEvents = true,
            tiltConfigurations =
                listOf(
                    TILT_CONFIGURATION_DISABLED,
                    TILT_CONFIGURATION_CROSS,
                ),
        )

    val DOS_AUTO =
        ControllerConfig(
            "auto",
            R.string.controller_dos_auto,
            TouchControllerID.DOS,
            allowTouchRotation = true,
            tiltConfigurations =
                listOf(
                    TILT_CONFIGURATION_DISABLED,
                    TILT_CONFIGURATION_CROSS,
                    TILT_CONFIGURATION_ANALOG_LEFT,
                    TILT_CONFIGURATION_ANALOG_RIGHT,
                    TILT_CONFIGURATION_L1_R1,
                    TILT_CONFIGURATION_L2_R2,
                ),
        )

    val WS_LANDSCAPE =
        ControllerConfig(
            "landscape",
            R.string.controller_landscape,
            TouchControllerID.WS_LANDSCAPE,
            mergeDPADAndLeftStickEvents = true,
            tiltConfigurations =
                listOf(
                    TILT_CONFIGURATION_DISABLED,
                    TILT_CONFIGURATION_CROSS,
                ),
        )

    val WS_PORTRAIT =
        ControllerConfig(
            "portrait",
            R.string.controller_portrait,
            TouchControllerID.WS_PORTRAIT,
            mergeDPADAndLeftStickEvents = true,
            tiltConfigurations =
                listOf(
                    TILT_CONFIGURATION_DISABLED,
                    TILT_CONFIGURATION_CROSS,
                ),
        )

    val NINTENDO_3DS =
        ControllerConfig(
            "default",
            R.string.controller_default,
            TouchControllerID.NINTENDO_3DS,
            allowTouchOverlay = false,
            tiltConfigurations =
                listOf(
                    TILT_CONFIGURATION_DISABLED,
                    TILT_CONFIGURATION_CROSS,
                    TILT_CONFIGURATION_ANALOG_LEFT,
                    TILT_CONFIGURATION_L_R,
                ),
        )

    val GAMECUBE =
        ControllerConfig(
            "default",
            R.string.controller_default,
            TouchControllerID.GAMECUBE,
            allowTouchRotation = true,
            // dolphin-libretro reports this port's controller subclass as "GameCube
            // Controller" (RETRO_DEVICE_JOYPAD, id 1) rather than a generic "RetroPad" --
            // matching it is what makes findControllerId()/setControllerType() actually
            // fire, same reasoning as DREAMCAST's "Controller" override above.
            libretroId = 1, // RETRO_DEVICE_JOYPAD
            libretroDescriptor = "GameCube Controller",
            tiltConfigurations =
                listOf(
                    TILT_CONFIGURATION_DISABLED,
                    TILT_CONFIGURATION_CROSS,
                    TILT_CONFIGURATION_ANALOG_LEFT,
                    TILT_CONFIGURATION_ANALOG_RIGHT,
                    // GameCube's single analog-capable L/R triggers read as JOYPAD_L2/R2
                    // (see SecondaryButtonAnalogL/R below), which is TILT_CONFIGURATION_
                    // L2_R2's binding -- not TILT_CONFIGURATION_L_R, which is L1/R1 and
                    // would silently do nothing here.
                    TILT_CONFIGURATION_L2_R2,
                ),
        )

}
