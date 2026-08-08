package com.winlator.xr.input

import com.winlator.xr.api.XrInterface.ControllerButton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreGameInputRouterTest {

    @Test
    fun rightOnlyKnownConnectionSelectsRightHand() {
        val router = PreGameInputRouterImpl(preferredHand = ControllerHand.LEFT)
        val result = router.processControllerInput(
            frame(rightConnection = ControllerConnectionState.CONNECTED, leftConnection = ControllerConnectionState.DISCONNECTED, right = setOf(ControllerControl.TRIGGER))
        )
        assertEquals(ControllerHand.RIGHT, result.activeHand)
    }

    @Test
    fun leftOnlyKnownConnectionSelectsLeftHand() {
        val router = PreGameInputRouterImpl(preferredHand = ControllerHand.RIGHT)
        val result = router.processControllerInput(
            frame(leftConnection = ControllerConnectionState.CONNECTED, rightConnection = ControllerConnectionState.DISCONNECTED, left = setOf(ControllerControl.TRIGGER))
        )
        assertEquals(ControllerHand.LEFT, result.activeHand)
    }

    @Test
    fun unknownAvailabilityWithRightHandActivitySelectsRightHand() {
        val router = PreGameInputRouterImpl(preferredHand = ControllerHand.LEFT)
        val result = router.processControllerInput(
            frame(rightConnection = ControllerConnectionState.UNKNOWN, leftConnection = ControllerConnectionState.UNKNOWN, right = setOf(ControllerControl.TRIGGER))
        )
        assertEquals(ControllerHand.RIGHT, result.activeHand)
    }

    @Test
    fun unknownAvailabilityWithLeftHandActivitySelectsLeftHand() {
        val router = PreGameInputRouterImpl(preferredHand = ControllerHand.RIGHT)
        val result = router.processControllerInput(
            frame(rightConnection = ControllerConnectionState.UNKNOWN, leftConnection = ControllerConnectionState.UNKNOWN, left = setOf(ControllerControl.TRIGGER))
        )
        assertEquals(ControllerHand.LEFT, result.activeHand)
    }

    @Test
    fun unknownAvailabilityWithNoControlsAndRightPreferenceSelectsRightHand() {
        val router = PreGameInputRouterImpl(preferredHand = ControllerHand.RIGHT)
        val result = router.processControllerInput(
            frame(rightConnection = ControllerConnectionState.UNKNOWN, leftConnection = ControllerConnectionState.UNKNOWN)
        )
        assertEquals(ControllerHand.RIGHT, result.activeHand)
    }

    @Test
    fun unknownAvailabilityWithNoControlsAndLeftPreferenceSelectsLeftHand() {
        val router = PreGameInputRouterImpl(preferredHand = ControllerHand.LEFT)
        val result = router.processControllerInput(
            frame(rightConnection = ControllerConnectionState.UNKNOWN, leftConnection = ControllerConnectionState.UNKNOWN)
        )
        assertEquals(ControllerHand.LEFT, result.activeHand)
    }

    @Test
    fun pointerModeEmitsMatchedPointerPressAndRelease() {
        val router = PreGameInputRouterImpl()
        router.setMode(InputRouterMode.GUEST_POINTER)
        router.processControllerInput(frame(rightConnection = ControllerConnectionState.CONNECTED))

        val down = router.processControllerInput(
            frame(rightConnection = ControllerConnectionState.CONNECTED, right = setOf(ControllerControl.TRIGGER)),
        )
        val up = router.processControllerInput(frame(rightConnection = ControllerConnectionState.CONNECTED))

        assertTrue(down.contains(RoutedInputAction.GUEST_POINTER_PRIMARY_DOWN))
        assertTrue(up.contains(RoutedInputAction.GUEST_POINTER_PRIMARY_UP))
    }

    @Test
    fun modeTransitionReleasesGuestInputAndSuppressesAStillHeldControl() {
        val router = PreGameInputRouterImpl()
        router.setMode(InputRouterMode.GUEST_POINTER)
        router.processControllerInput(frame(rightConnection = ControllerConnectionState.CONNECTED))
        router.processControllerInput(
            frame(rightConnection = ControllerConnectionState.CONNECTED, right = setOf(ControllerControl.TRIGGER)),
        )

        val transition = router.setMode(InputRouterMode.GUEST_NAVIGATION)
        val heldAfterTransition = router.processControllerInput(
            frame(rightConnection = ControllerConnectionState.CONNECTED, right = setOf(ControllerControl.TRIGGER)),
        )
        router.processControllerInput(frame(rightConnection = ControllerConnectionState.CONNECTED))
        val newPress = router.processControllerInput(
            frame(rightConnection = ControllerConnectionState.CONNECTED, right = setOf(ControllerControl.TRIGGER)),
        )

        assertTrue(transition.contains(RoutedInputAction.RELEASE_ALL_GUEST_INPUT))
        assertTrue(transition.requiresGuestInputRelease)
        assertFalse(heldAfterTransition.contains(RoutedInputAction.GUEST_KEY_ENTER_DOWN))
        assertTrue(newPress.contains(RoutedInputAction.GUEST_KEY_ENTER_DOWN))
    }

    @Test
    fun rightHandChordOpensSystemMenuWithoutLeavingGuestInputHeld() {
        val router = PreGameInputRouterImpl()
        router.setMode(InputRouterMode.GUEST_POINTER)
        router.processControllerInput(frame(rightConnection = ControllerConnectionState.CONNECTED))

        val result = router.processControllerInput(
            frame(
                rightConnection = ControllerConnectionState.CONNECTED,
                right = setOf(ControllerControl.GRIP, ControllerControl.THUMBSTICK_PRESS),
            ),
        )

        assertEquals(InputRouterMode.SYSTEM_MENU, result.mode)
        assertTrue(result.contains(RoutedInputAction.RELEASE_ALL_GUEST_INPUT))
        assertTrue(result.contains(RoutedInputAction.OPEN_SYSTEM_MENU))
    }

    @Test
    fun systemMenuChordWorksWhenGripIsPressedBeforeTheThumbstick() {
        val router = PreGameInputRouterImpl()
        router.setMode(InputRouterMode.GUEST_POINTER)
        router.processControllerInput(frame(rightConnection = ControllerConnectionState.CONNECTED))

        router.processControllerInput(
            frame(
                rightConnection = ControllerConnectionState.CONNECTED,
                right = setOf(ControllerControl.GRIP),
            ),
        )
        val result = router.processControllerInput(
            frame(
                rightConnection = ControllerConnectionState.CONNECTED,
                right = setOf(ControllerControl.GRIP, ControllerControl.THUMBSTICK_PRESS),
            ),
        )

        assertEquals(InputRouterMode.SYSTEM_MENU, result.mode)
        assertTrue(result.contains(RoutedInputAction.OPEN_SYSTEM_MENU))
    }

    @Test
    fun changingActiveHandReleasesGuestInputBeforeUsingTheFallbackHand() {
        val router = PreGameInputRouterImpl(preferredHand = ControllerHand.RIGHT)
        router.setMode(InputRouterMode.GUEST_POINTER)
        // Establish right hand active
        router.processControllerInput(frame(rightConnection = ControllerConnectionState.CONNECTED, leftConnection = ControllerConnectionState.DISCONNECTED))
        // Hold trigger
        router.processControllerInput(
            frame(rightConnection = ControllerConnectionState.CONNECTED, leftConnection = ControllerConnectionState.DISCONNECTED, right = setOf(ControllerControl.TRIGGER)),
        )

        // Suddenly right hand disconnects and left connects
        val handChange = router.processControllerInput(frame(leftConnection = ControllerConnectionState.CONNECTED, rightConnection = ControllerConnectionState.DISCONNECTED))

        assertEquals(ControllerHand.LEFT, handChange.activeHand)
        assertTrue(handChange.contains(RoutedInputAction.RELEASE_ALL_GUEST_INPUT))
        assertTrue(handChange.requiresGuestInputRelease)
    }

    @Test
    fun adapterMapsExistingOpenXrButtonOrdinalsWithoutAssumingConnectionState() {
        val buttons = BooleanArray(ControllerButton.entries.size)
        buttons[ControllerButton.R_TRIGGER.ordinal] = true
        buttons[ControllerButton.L_MENU.ordinal] = true

        val adapterFrame = XrControllerInputAdapter.fromOpenXrButtons(
            buttons = buttons,
            leftConnection = ControllerConnectionState.UNKNOWN,
            rightConnection = ControllerConnectionState.CONNECTED,
        )

        assertTrue(ControllerControl.TRIGGER in adapterFrame.rightControls)
        assertTrue(ControllerControl.MENU in adapterFrame.leftControls)
        assertEquals(ControllerConnectionState.UNKNOWN, adapterFrame.leftConnection)
        assertEquals(ControllerConnectionState.CONNECTED, adapterFrame.rightConnection)
    }

    private fun frame(
        leftConnection: ControllerConnectionState = ControllerConnectionState.UNKNOWN,
        rightConnection: ControllerConnectionState = ControllerConnectionState.UNKNOWN,
        left: Set<ControllerControl> = emptySet(),
        right: Set<ControllerControl> = emptySet(),
    ) = ControllerInputFrame(
        leftConnection = leftConnection,
        rightConnection = rightConnection,
        leftControls = left,
        rightControls = right,
    )
}
