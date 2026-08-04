package com.winlator.xr.input

import com.winlator.xr.api.XrInterface.ControllerButton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreGameInputRouterTest {

    @Test
    fun rightOnlyAndLeftOnlyFramesNavigateAndroidOverlay() {
        val rightRouter = PreGameInputRouterImpl()
        val rightResult = rightRouter.processControllerInput(
            frame(rightConnected = true, right = setOf(ControllerControl.TRIGGER)),
        )

        assertEquals(ControllerHand.RIGHT, rightResult.activeHand)
        assertTrue(rightResult.contains(RoutedInputAction.ANDROID_CONFIRM))

        val leftRouter = PreGameInputRouterImpl()
        val leftResult = leftRouter.processControllerInput(
            frame(leftConnected = true, left = setOf(ControllerControl.TRIGGER)),
        )

        assertEquals(ControllerHand.LEFT, leftResult.activeHand)
        assertTrue(leftResult.contains(RoutedInputAction.ANDROID_CONFIRM))
    }

    @Test
    fun pointerModeEmitsMatchedPointerPressAndRelease() {
        val router = PreGameInputRouterImpl()
        router.setMode(InputRouterMode.GUEST_POINTER)
        router.processControllerInput(frame(rightConnected = true))

        val down = router.processControllerInput(
            frame(rightConnected = true, right = setOf(ControllerControl.TRIGGER)),
        )
        val up = router.processControllerInput(frame(rightConnected = true))

        assertTrue(down.contains(RoutedInputAction.GUEST_POINTER_PRIMARY_DOWN))
        assertTrue(up.contains(RoutedInputAction.GUEST_POINTER_PRIMARY_UP))
    }

    @Test
    fun modeTransitionReleasesGuestInputAndSuppressesAStillHeldControl() {
        val router = PreGameInputRouterImpl()
        router.setMode(InputRouterMode.GUEST_POINTER)
        router.processControllerInput(frame(rightConnected = true))
        router.processControllerInput(
            frame(rightConnected = true, right = setOf(ControllerControl.TRIGGER)),
        )

        val transition = router.setMode(InputRouterMode.GUEST_NAVIGATION)
        val heldAfterTransition = router.processControllerInput(
            frame(rightConnected = true, right = setOf(ControllerControl.TRIGGER)),
        )
        router.processControllerInput(frame(rightConnected = true))
        val newPress = router.processControllerInput(
            frame(rightConnected = true, right = setOf(ControllerControl.TRIGGER)),
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
        router.processControllerInput(frame(rightConnected = true))

        val result = router.processControllerInput(
            frame(
                rightConnected = true,
                right = setOf(ControllerControl.GRIP, ControllerControl.THUMBSTICK_PRESS),
            ),
        )

        assertEquals(InputRouterMode.SYSTEM_MENU, result.mode)
        assertTrue(result.contains(RoutedInputAction.RELEASE_ALL_GUEST_INPUT))
        assertTrue(result.contains(RoutedInputAction.OPEN_SYSTEM_MENU))
    }

    @Test
    fun changingActiveHandReleasesGuestInputBeforeUsingTheFallbackHand() {
        val router = PreGameInputRouterImpl()
        router.setMode(InputRouterMode.GUEST_POINTER)
        router.processControllerInput(frame(rightConnected = true))
        router.processControllerInput(
            frame(rightConnected = true, right = setOf(ControllerControl.TRIGGER)),
        )

        val handChange = router.processControllerInput(frame(leftConnected = true))

        assertEquals(ControllerHand.LEFT, handChange.activeHand)
        assertTrue(handChange.contains(RoutedInputAction.RELEASE_ALL_GUEST_INPUT))
        assertTrue(handChange.requiresGuestInputRelease)
    }

    @Test
    fun adapterMapsExistingOpenXrButtonOrdinalsWithoutAssumingConnectionState() {
        val buttons = BooleanArray(ControllerButton.entries.size)
        buttons[ControllerButton.R_TRIGGER.ordinal] = true
        buttons[ControllerButton.L_MENU.ordinal] = true

        val frame = XrControllerInputAdapter.fromOpenXrButtons(
            buttons = buttons,
            leftConnected = false,
            rightConnected = true,
        )

        assertTrue(ControllerControl.TRIGGER in frame.rightControls)
        assertTrue(ControllerControl.MENU in frame.leftControls)
        assertFalse(frame.leftConnected)
        assertTrue(frame.rightConnected)
    }

    private fun frame(
        leftConnected: Boolean = false,
        rightConnected: Boolean = false,
        left: Set<ControllerControl> = emptySet(),
        right: Set<ControllerControl> = emptySet(),
    ) = ControllerInputFrame(
        leftConnected = leftConnected,
        rightConnected = rightConnected,
        leftControls = left,
        rightControls = right,
    )
}
