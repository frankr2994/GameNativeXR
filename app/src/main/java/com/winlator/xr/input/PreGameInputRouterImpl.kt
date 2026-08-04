package com.winlator.xr.input

class PreGameInputRouterImpl(
    private val preferredHand: ControllerHand = ControllerHand.RIGHT,
    private val telemetry: InputRouterTelemetry = InputRouterTelemetry { },
) : PreGameInputRouter {
    override var currentMode: InputRouterMode = InputRouterMode.ANDROID_OVERLAY
        private set

    override var activeHand: ControllerHand? = null
        private set

    private var returnModeFromSystemMenu = InputRouterMode.ANDROID_OVERLAY
    private var returnModeFromText = InputRouterMode.GUEST_POINTER
    private var previousHandControls: Set<ControllerControl> = emptySet()
    private var previousGlobalControls: Set<ControllerControl> = emptySet()
    private var suppressUntilControlsReleased = false

    override fun setMode(newMode: InputRouterMode): InputRoutingResult {
        if (newMode == currentMode) return publish(emptyList())

        if (newMode == InputRouterMode.SYSTEM_MENU) returnModeFromSystemMenu = currentMode
        if (newMode == InputRouterMode.GUEST_TEXT) returnModeFromText = currentMode
        currentMode = newMode
        resetFrameState()
        suppressUntilControlsReleased = true
        return publish(
            actions = listOf(RoutedInputAction.RELEASE_ALL_GUEST_INPUT),
            modeChanged = true,
            requiresGuestInputRelease = true,
        )
    }

    override fun resetAllHeldInputs(): InputRoutingResult {
        resetFrameState()
        suppressUntilControlsReleased = true
        return publish(
            actions = listOf(RoutedInputAction.RELEASE_ALL_GUEST_INPUT),
            requiresGuestInputRelease = true,
        )
    }

    override fun processControllerInput(frame: ControllerInputFrame): InputRoutingResult {
        val selectedHand = selectHand(frame)
        val controls = selectedHand?.let(frame::controlsFor).orEmpty()
        val globalControls = frame.allControls()
        val handChanged = selectedHand != activeHand

        if (handChanged) {
            val previousHand = activeHand
            activeHand = selectedHand
            previousHandControls = emptySet()
            previousGlobalControls = emptySet()
            if (previousHand != null) {
                suppressUntilControlsReleased = true
                previousHandControls = controls
                previousGlobalControls = globalControls
                return publish(
                    actions = listOf(RoutedInputAction.RELEASE_ALL_GUEST_INPUT),
                    requiresGuestInputRelease = true,
                )
            }
        }

        if (suppressUntilControlsReleased) {
            previousHandControls = controls
            previousGlobalControls = globalControls
            suppressUntilControlsReleased = controls.isNotEmpty() || globalControls.isNotEmpty()
            return publish(emptyList())
        }

        val pressed = controls - previousHandControls
        val released = previousHandControls - controls
        val globalPressed = globalControls - previousGlobalControls
        previousHandControls = controls
        previousGlobalControls = globalControls

        if (ControllerControl.MENU in globalPressed ||
            (ControllerControl.THUMBSTICK_PRESS in pressed && ControllerControl.GRIP in controls)
        ) {
            return toggleSystemMenu()
        }

        return when (currentMode) {
            InputRouterMode.ANDROID_OVERLAY,
            InputRouterMode.SYSTEM_MENU -> publish(mapAndroidOverlay(pressed))
            InputRouterMode.GUEST_POINTER -> processGuestPointer(pressed, released)
            InputRouterMode.GUEST_TEXT -> processGuestText(pressed)
            InputRouterMode.GUEST_NAVIGATION -> publish(mapGuestNavigation(pressed, released))
            InputRouterMode.GAME_INPUT -> publish(listOf(RoutedInputAction.FORWARD_TO_GAME_INPUT))
        }
    }

    private fun processGuestPointer(
        pressed: Set<ControllerControl>,
        released: Set<ControllerControl>,
    ): InputRoutingResult {
        if (ControllerControl.THUMBSTICK_PRESS in pressed) {
            returnModeFromText = InputRouterMode.GUEST_POINTER
            currentMode = InputRouterMode.GUEST_TEXT
            resetFrameState()
            suppressUntilControlsReleased = true
            return publish(
                actions = listOf(
                    RoutedInputAction.RELEASE_ALL_GUEST_INPUT,
                    RoutedInputAction.REQUEST_VISIBLE_KEYBOARD,
                ),
                modeChanged = true,
                requiresGuestInputRelease = true,
            )
        }

        return publish(mapGuestPointer(pressed, released))
    }

    private fun processGuestText(pressed: Set<ControllerControl>): InputRoutingResult {
        if (ControllerControl.THUMBSTICK_PRESS !in pressed) return publish(emptyList())

        currentMode = returnModeFromText
        resetFrameState()
        suppressUntilControlsReleased = true
        return publish(
            actions = listOf(
                RoutedInputAction.RELEASE_ALL_GUEST_INPUT,
                RoutedInputAction.CLOSE_VISIBLE_KEYBOARD,
            ),
            modeChanged = true,
            requiresGuestInputRelease = true,
        )
    }

    private fun toggleSystemMenu(): InputRoutingResult {
        val opening = currentMode != InputRouterMode.SYSTEM_MENU
        if (opening) {
            returnModeFromSystemMenu = currentMode
            currentMode = InputRouterMode.SYSTEM_MENU
        } else {
            currentMode = returnModeFromSystemMenu
        }
        resetFrameState()
        suppressUntilControlsReleased = true
        return publish(
            actions = listOf(
                RoutedInputAction.RELEASE_ALL_GUEST_INPUT,
                if (opening) RoutedInputAction.OPEN_SYSTEM_MENU else RoutedInputAction.CLOSE_SYSTEM_MENU,
            ),
            modeChanged = true,
            requiresGuestInputRelease = true,
        )
    }

    private fun mapAndroidOverlay(pressed: Set<ControllerControl>): List<RoutedInputAction> = buildList {
        if (ControllerControl.TRIGGER in pressed) add(RoutedInputAction.ANDROID_CONFIRM)
        if (ControllerControl.THUMBSTICK_PRESS in pressed) add(RoutedInputAction.ANDROID_BACK)
        if (ControllerControl.THUMBSTICK_UP in pressed) add(RoutedInputAction.ANDROID_NAV_UP)
        if (ControllerControl.THUMBSTICK_DOWN in pressed) add(RoutedInputAction.ANDROID_NAV_DOWN)
        if (ControllerControl.THUMBSTICK_LEFT in pressed) add(RoutedInputAction.ANDROID_NAV_LEFT)
        if (ControllerControl.THUMBSTICK_RIGHT in pressed) add(RoutedInputAction.ANDROID_NAV_RIGHT)
    }

    private fun mapGuestPointer(
        pressed: Set<ControllerControl>,
        released: Set<ControllerControl>,
    ): List<RoutedInputAction> = buildList {
        if (ControllerControl.TRIGGER in pressed) add(RoutedInputAction.GUEST_POINTER_PRIMARY_DOWN)
        if (ControllerControl.TRIGGER in released) add(RoutedInputAction.GUEST_POINTER_PRIMARY_UP)
        if (ControllerControl.GRIP in pressed) add(RoutedInputAction.GUEST_POINTER_SECONDARY_DOWN)
        if (ControllerControl.GRIP in released) add(RoutedInputAction.GUEST_POINTER_SECONDARY_UP)
        if (ControllerControl.THUMBSTICK_UP in pressed) add(RoutedInputAction.GUEST_SCROLL_UP)
        if (ControllerControl.THUMBSTICK_DOWN in pressed) add(RoutedInputAction.GUEST_SCROLL_DOWN)
    }

    private fun mapGuestNavigation(
        pressed: Set<ControllerControl>,
        released: Set<ControllerControl>,
    ): List<RoutedInputAction> = buildList {
        mapHeldControl(
            pressed,
            released,
            ControllerControl.TRIGGER,
            RoutedInputAction.GUEST_KEY_ENTER_DOWN,
            RoutedInputAction.GUEST_KEY_ENTER_UP,
        )
        mapHeldControl(
            pressed,
            released,
            ControllerControl.FACE_PRIMARY,
            RoutedInputAction.GUEST_KEY_TAB_DOWN,
            RoutedInputAction.GUEST_KEY_TAB_UP,
        )
        mapHeldControl(
            pressed,
            released,
            ControllerControl.FACE_SECONDARY,
            RoutedInputAction.GUEST_KEY_SHIFT_TAB_DOWN,
            RoutedInputAction.GUEST_KEY_SHIFT_TAB_UP,
        )
        mapHeldControl(
            pressed,
            released,
            ControllerControl.THUMBSTICK_UP,
            RoutedInputAction.GUEST_KEY_UP_DOWN,
            RoutedInputAction.GUEST_KEY_UP_UP,
        )
        mapHeldControl(
            pressed,
            released,
            ControllerControl.THUMBSTICK_DOWN,
            RoutedInputAction.GUEST_KEY_DOWN_DOWN,
            RoutedInputAction.GUEST_KEY_DOWN_UP,
        )
        mapHeldControl(
            pressed,
            released,
            ControllerControl.THUMBSTICK_LEFT,
            RoutedInputAction.GUEST_KEY_LEFT_DOWN,
            RoutedInputAction.GUEST_KEY_LEFT_UP,
        )
        mapHeldControl(
            pressed,
            released,
            ControllerControl.THUMBSTICK_RIGHT,
            RoutedInputAction.GUEST_KEY_RIGHT_DOWN,
            RoutedInputAction.GUEST_KEY_RIGHT_UP,
        )
        if (ControllerControl.THUMBSTICK_PRESS in pressed) add(RoutedInputAction.GUEST_KEY_ESCAPE)
    }

    private fun MutableList<RoutedInputAction>.mapHeldControl(
        pressed: Set<ControllerControl>,
        released: Set<ControllerControl>,
        control: ControllerControl,
        down: RoutedInputAction,
        up: RoutedInputAction,
    ) {
        if (control in pressed) add(down)
        if (control in released) add(up)
    }

    private fun selectHand(frame: ControllerInputFrame): ControllerHand? = when {
        frame.isConnected(preferredHand) -> preferredHand
        frame.isConnected(preferredHand.opposite()) -> preferredHand.opposite()
        else -> null
    }

    private fun resetFrameState() {
        activeHand = null
        previousHandControls = emptySet()
        previousGlobalControls = emptySet()
    }

    private fun publish(
        actions: List<RoutedInputAction>,
        modeChanged: Boolean = false,
        requiresGuestInputRelease: Boolean = false,
    ): InputRoutingResult = InputRoutingResult(
        mode = currentMode,
        activeHand = activeHand,
        actions = actions,
        modeChanged = modeChanged,
        requiresGuestInputRelease = requiresGuestInputRelease,
    ).also { result ->
        if (result.modeChanged || result.actions.isNotEmpty()) telemetry.onRoute(result)
    }
}
