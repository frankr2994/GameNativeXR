package com.winlator.xr.input

import com.winlator.xserver.Pointer
import com.winlator.xserver.XKeycode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XrLivePolicyTest {

    private class MockSink : XrInputSink {
        val pressedPointer = mutableListOf<Pointer.Button>()
        val releasedPointer = mutableListOf<Pointer.Button>()
        val pressedKeys = mutableListOf<XKeycode>()
        val releasedKeys = mutableListOf<XKeycode>()

        override fun injectPointerButtonPress(button: Pointer.Button) { pressedPointer.add(button) }
        override fun injectPointerButtonRelease(button: Pointer.Button) { releasedPointer.add(button) }
        override fun injectKeyPress(keycode: XKeycode) { pressedKeys.add(keycode) }
        override fun injectKeyRelease(keycode: XKeycode) { releasedKeys.add(keycode) }
    }

    @Test
    fun defaultFlatGameplayForwardsToLegacyInput() {
        val router = PreGameInputRouterImpl()
        val sink = MockSink()
        val policy = XrLivePolicy(router, sink)

        policy.updateMode(hasAndroidDialog = false)
        assertEquals(InputRouterMode.GAME_INPUT, router.currentMode)
    }

    @Test
    fun androidOverlayConsumesInputButPreservesRendererState() {
        val router = PreGameInputRouterImpl()
        val sink = MockSink()
        val policy = XrLivePolicy(router, sink)

        policy.updateMode(hasAndroidDialog = true)
        assertEquals(InputRouterMode.ANDROID_OVERLAY, router.currentMode)
    }

    @Test
    fun explicitPointerModeConsumesInput() {
        val router = PreGameInputRouterImpl()
        val sink = MockSink()
        val policy = XrLivePolicy(router, sink)

        policy.toggleGuestUI() // Turn on guest UI
        policy.updateMode(hasAndroidDialog = false)
        assertEquals(InputRouterMode.GUEST_POINTER, router.currentMode)
    }

    @Test
    fun exitFromGuestUIReturnsToLegacyGameInput() {
        val router = PreGameInputRouterImpl()
        val sink = MockSink()
        val policy = XrLivePolicy(router, sink)

        policy.toggleGuestUI()
        policy.updateMode(hasAndroidDialog = false)
        assertEquals(InputRouterMode.GUEST_POINTER, router.currentMode)

        policy.toggleGuestUI() // Turn off
        policy.updateMode(hasAndroidDialog = false)
        assertEquals(InputRouterMode.GAME_INPUT, router.currentMode)
    }

    @Test
    fun pointerKeyReleaseAllBehavior() {
        val router = PreGameInputRouterImpl()
        val sink = MockSink()
        val policy = XrLivePolicy(router, sink)

        policy.routeActions(InputRoutingResult(InputRouterMode.GUEST_POINTER, ControllerHand.RIGHT, listOf(RoutedInputAction.RELEASE_ALL_GUEST_INPUT)))

        assertTrue(sink.releasedPointer.contains(Pointer.Button.BUTTON_LEFT))
        assertTrue(sink.releasedPointer.contains(Pointer.Button.BUTTON_RIGHT))
        assertTrue(sink.releasedKeys.contains(XKeycode.KEY_ENTER))
        assertTrue(sink.releasedKeys.contains(XKeycode.KEY_TAB))
        assertTrue(sink.releasedKeys.contains(XKeycode.KEY_SHIFT_L))
        assertTrue(sink.releasedKeys.contains(XKeycode.KEY_ESC))
    }
}
