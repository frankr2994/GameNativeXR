package com.winlator.xr.input

import com.winlator.xr.api.XrInterface.ControllerButton

/**
 * The only input target selected while a title is still in its launcher or sign-in flow.
 * GAME_INPUT intentionally remains a hand-off to the existing per-game mappings.
 */
enum class InputRouterMode {
    ANDROID_OVERLAY,
    GUEST_POINTER,
    GUEST_TEXT,
    GUEST_NAVIGATION,
    GAME_INPUT,
    SYSTEM_MENU,
}

enum class ControllerHand {
    LEFT,
    RIGHT;

    fun opposite(): ControllerHand = if (this == LEFT) RIGHT else LEFT
}

/**
 * Semantic controls are intentionally independent from OpenXR enum ordinals. This keeps mode
 * transitions testable and lets a future native controller-connection signal choose the hand.
 */
enum class ControllerControl {
    TRIGGER,
    GRIP,
    THUMBSTICK_PRESS,
    THUMBSTICK_UP,
    THUMBSTICK_DOWN,
    THUMBSTICK_LEFT,
    THUMBSTICK_RIGHT,
    FACE_PRIMARY,
    FACE_SECONDARY,
    MENU,
}

data class ControllerInputFrame(
    val leftConnected: Boolean,
    val rightConnected: Boolean,
    val leftControls: Set<ControllerControl> = emptySet(),
    val rightControls: Set<ControllerControl> = emptySet(),
) {
    fun controlsFor(hand: ControllerHand): Set<ControllerControl> = when (hand) {
        ControllerHand.LEFT -> leftControls
        ControllerHand.RIGHT -> rightControls
    }

    fun isConnected(hand: ControllerHand): Boolean = when (hand) {
        ControllerHand.LEFT -> leftConnected
        ControllerHand.RIGHT -> rightConnected
    }

    fun allControls(): Set<ControllerControl> = leftControls + rightControls
}

/**
 * Output is semantic rather than a raw key/text stream. Sinks translate these actions to Android
 * dialog events or X-server pointer/key events, and must never log typed characters.
 */
enum class RoutedInputAction {
    RELEASE_ALL_GUEST_INPUT,
    OPEN_SYSTEM_MENU,
    CLOSE_SYSTEM_MENU,
    REQUEST_VISIBLE_KEYBOARD,
    CLOSE_VISIBLE_KEYBOARD,
    ANDROID_CONFIRM,
    ANDROID_BACK,
    ANDROID_NAV_UP,
    ANDROID_NAV_DOWN,
    ANDROID_NAV_LEFT,
    ANDROID_NAV_RIGHT,
    GUEST_POINTER_PRIMARY_DOWN,
    GUEST_POINTER_PRIMARY_UP,
    GUEST_POINTER_SECONDARY_DOWN,
    GUEST_POINTER_SECONDARY_UP,
    GUEST_SCROLL_UP,
    GUEST_SCROLL_DOWN,
    GUEST_KEY_ENTER_DOWN,
    GUEST_KEY_ENTER_UP,
    GUEST_KEY_TAB_DOWN,
    GUEST_KEY_TAB_UP,
    GUEST_KEY_SHIFT_TAB_DOWN,
    GUEST_KEY_SHIFT_TAB_UP,
    GUEST_KEY_ESCAPE,
    GUEST_KEY_UP_DOWN,
    GUEST_KEY_UP_UP,
    GUEST_KEY_DOWN_DOWN,
    GUEST_KEY_DOWN_UP,
    GUEST_KEY_LEFT_DOWN,
    GUEST_KEY_LEFT_UP,
    GUEST_KEY_RIGHT_DOWN,
    GUEST_KEY_RIGHT_UP,
    FORWARD_TO_GAME_INPUT,
}

data class InputRoutingResult(
    val mode: InputRouterMode,
    val activeHand: ControllerHand?,
    val actions: List<RoutedInputAction> = emptyList(),
    val modeChanged: Boolean = false,
    val requiresGuestInputRelease: Boolean = false,
) {
    fun contains(action: RoutedInputAction): Boolean = action in actions
}

/** This observer receives only mode/action metadata; no text payload is part of this contract. */
fun interface InputRouterTelemetry {
    fun onRoute(result: InputRoutingResult)
}

interface PreGameInputRouter {
    val currentMode: InputRouterMode
    val activeHand: ControllerHand?

    /**
     * Every target change emits RELEASE_ALL_GUEST_INPUT so a pointer button or guest key cannot
     * survive into a dialog, keyboard, game, pause, or system-menu mode.
     */
    fun setMode(newMode: InputRouterMode): InputRoutingResult

    fun processControllerInput(frame: ControllerInputFrame): InputRoutingResult

    fun resetAllHeldInputs(): InputRoutingResult
}

/**
 * Adapter for the button array already supplied to XrActivity. Connection availability is kept as
 * an explicit argument because the current array does not reliably encode tracking/connection
 * state; callers must not infer a connected hand only from zero-valued controls.
 */
object XrControllerInputAdapter {
    fun fromOpenXrButtons(
        buttons: BooleanArray,
        leftConnected: Boolean,
        rightConnected: Boolean,
    ): ControllerInputFrame = ControllerInputFrame(
        leftConnected = leftConnected,
        rightConnected = rightConnected,
        leftControls = buildSet {
            addIfPressed(buttons, ControllerButton.L_TRIGGER, ControllerControl.TRIGGER)
            addIfPressed(buttons, ControllerButton.L_GRIP, ControllerControl.GRIP)
            addIfPressed(buttons, ControllerButton.L_THUMBSTICK_PRESS, ControllerControl.THUMBSTICK_PRESS)
            addIfPressed(buttons, ControllerButton.L_THUMBSTICK_UP, ControllerControl.THUMBSTICK_UP)
            addIfPressed(buttons, ControllerButton.L_THUMBSTICK_DOWN, ControllerControl.THUMBSTICK_DOWN)
            addIfPressed(buttons, ControllerButton.L_THUMBSTICK_LEFT, ControllerControl.THUMBSTICK_LEFT)
            addIfPressed(buttons, ControllerButton.L_THUMBSTICK_RIGHT, ControllerControl.THUMBSTICK_RIGHT)
            addIfPressed(buttons, ControllerButton.L_X, ControllerControl.FACE_PRIMARY)
            addIfPressed(buttons, ControllerButton.L_Y, ControllerControl.FACE_SECONDARY)
            addIfPressed(buttons, ControllerButton.L_MENU, ControllerControl.MENU)
        },
        rightControls = buildSet {
            addIfPressed(buttons, ControllerButton.R_TRIGGER, ControllerControl.TRIGGER)
            addIfPressed(buttons, ControllerButton.R_GRIP, ControllerControl.GRIP)
            addIfPressed(buttons, ControllerButton.R_THUMBSTICK_PRESS, ControllerControl.THUMBSTICK_PRESS)
            addIfPressed(buttons, ControllerButton.R_THUMBSTICK_UP, ControllerControl.THUMBSTICK_UP)
            addIfPressed(buttons, ControllerButton.R_THUMBSTICK_DOWN, ControllerControl.THUMBSTICK_DOWN)
            addIfPressed(buttons, ControllerButton.R_THUMBSTICK_LEFT, ControllerControl.THUMBSTICK_LEFT)
            addIfPressed(buttons, ControllerButton.R_THUMBSTICK_RIGHT, ControllerControl.THUMBSTICK_RIGHT)
            addIfPressed(buttons, ControllerButton.R_A, ControllerControl.FACE_PRIMARY)
            addIfPressed(buttons, ControllerButton.R_B, ControllerControl.FACE_SECONDARY)
        },
    )

    private fun MutableSet<ControllerControl>.addIfPressed(
        buttons: BooleanArray,
        button: ControllerButton,
        control: ControllerControl,
    ) {
        if (button.ordinal < buttons.size && buttons[button.ordinal]) add(control)
    }
}
