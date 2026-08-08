package com.winlator.xr.input

import com.winlator.xserver.Pointer
import com.winlator.xserver.XKeycode

interface XrInputSink {
    fun injectPointerButtonPress(button: Pointer.Button)
    fun injectPointerButtonRelease(button: Pointer.Button)
    fun injectKeyPress(keycode: XKeycode)
    fun injectKeyRelease(keycode: XKeycode)
}

class XrLivePolicy(
    val router: PreGameInputRouter,
    private val sink: XrInputSink
) {
    var isGuestUIMode = false
        private set

    fun toggleGuestUI() {
        isGuestUIMode = !isGuestUIMode
    }

    fun updateMode(hasAndroidDialog: Boolean) {
        val newMode = when {
            hasAndroidDialog -> InputRouterMode.ANDROID_OVERLAY
            isGuestUIMode -> {
                if (router.currentMode == InputRouterMode.GUEST_TEXT || router.currentMode == InputRouterMode.GUEST_NAVIGATION) {
                    router.currentMode
                } else {
                    InputRouterMode.GUEST_POINTER
                }
            }
            else -> InputRouterMode.GAME_INPUT
        }
        val result = router.setMode(newMode)
        routeActions(result)
    }

    fun routeActions(result: InputRoutingResult) {
        for (action in result.actions) {
            when (action) {
                RoutedInputAction.GUEST_POINTER_PRIMARY_DOWN -> sink.injectPointerButtonPress(Pointer.Button.BUTTON_LEFT)
                RoutedInputAction.GUEST_POINTER_PRIMARY_UP -> sink.injectPointerButtonRelease(Pointer.Button.BUTTON_LEFT)
                RoutedInputAction.GUEST_POINTER_SECONDARY_DOWN -> sink.injectPointerButtonPress(Pointer.Button.BUTTON_RIGHT)
                RoutedInputAction.GUEST_POINTER_SECONDARY_UP -> sink.injectPointerButtonRelease(Pointer.Button.BUTTON_RIGHT)
                RoutedInputAction.GUEST_SCROLL_UP -> {
                    sink.injectPointerButtonPress(Pointer.Button.BUTTON_SCROLL_UP)
                    sink.injectPointerButtonRelease(Pointer.Button.BUTTON_SCROLL_UP)
                }
                RoutedInputAction.GUEST_SCROLL_DOWN -> {
                    sink.injectPointerButtonPress(Pointer.Button.BUTTON_SCROLL_DOWN)
                    sink.injectPointerButtonRelease(Pointer.Button.BUTTON_SCROLL_DOWN)
                }
                RoutedInputAction.GUEST_KEY_ENTER_DOWN -> sink.injectKeyPress(XKeycode.KEY_ENTER)
                RoutedInputAction.GUEST_KEY_ENTER_UP -> sink.injectKeyRelease(XKeycode.KEY_ENTER)
                RoutedInputAction.GUEST_KEY_TAB_DOWN -> sink.injectKeyPress(XKeycode.KEY_TAB)
                RoutedInputAction.GUEST_KEY_TAB_UP -> sink.injectKeyRelease(XKeycode.KEY_TAB)
                RoutedInputAction.GUEST_KEY_SHIFT_TAB_DOWN -> {
                    sink.injectKeyPress(XKeycode.KEY_SHIFT_L)
                    sink.injectKeyPress(XKeycode.KEY_TAB)
                }
                RoutedInputAction.GUEST_KEY_SHIFT_TAB_UP -> {
                    sink.injectKeyRelease(XKeycode.KEY_TAB)
                    sink.injectKeyRelease(XKeycode.KEY_SHIFT_L)
                }
                RoutedInputAction.GUEST_KEY_ESCAPE -> {
                    sink.injectKeyPress(XKeycode.KEY_ESC)
                    sink.injectKeyRelease(XKeycode.KEY_ESC)
                }
                RoutedInputAction.GUEST_KEY_UP_DOWN -> sink.injectKeyPress(XKeycode.KEY_UP)
                RoutedInputAction.GUEST_KEY_UP_UP -> sink.injectKeyRelease(XKeycode.KEY_UP)
                RoutedInputAction.GUEST_KEY_DOWN_DOWN -> sink.injectKeyPress(XKeycode.KEY_DOWN)
                RoutedInputAction.GUEST_KEY_DOWN_UP -> sink.injectKeyRelease(XKeycode.KEY_DOWN)
                RoutedInputAction.GUEST_KEY_LEFT_DOWN -> sink.injectKeyPress(XKeycode.KEY_LEFT)
                RoutedInputAction.GUEST_KEY_LEFT_UP -> sink.injectKeyRelease(XKeycode.KEY_LEFT)
                RoutedInputAction.GUEST_KEY_RIGHT_DOWN -> sink.injectKeyPress(XKeycode.KEY_RIGHT)
                RoutedInputAction.GUEST_KEY_RIGHT_UP -> sink.injectKeyRelease(XKeycode.KEY_RIGHT)
                RoutedInputAction.RELEASE_ALL_GUEST_INPUT -> {
                    sink.injectPointerButtonRelease(Pointer.Button.BUTTON_LEFT)
                    sink.injectPointerButtonRelease(Pointer.Button.BUTTON_RIGHT)
                    sink.injectKeyRelease(XKeycode.KEY_ENTER)
                    sink.injectKeyRelease(XKeycode.KEY_TAB)
                    sink.injectKeyRelease(XKeycode.KEY_SHIFT_L)
                    sink.injectKeyRelease(XKeycode.KEY_ESC)
                    sink.injectKeyRelease(XKeycode.KEY_UP)
                    sink.injectKeyRelease(XKeycode.KEY_DOWN)
                    sink.injectKeyRelease(XKeycode.KEY_LEFT)
                    sink.injectKeyRelease(XKeycode.KEY_RIGHT)
                }
                else -> {}
            }
        }
    }
}
