package com.winlator.xr;

import android.view.KeyCharacterMap;
import android.view.KeyEvent;

import com.winlator.xserver.XServer;

/**
 * Sends controller-selected virtual-keyboard input directly to the focused guest X-server window.
 * The sink deliberately accepts individual key events only; it does not retain, log, or expose
 * typed text.
 */
public final class XrKeyEventSink {
    private static final KeyCharacterMap VIRTUAL_KEYBOARD =
            KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD);

    private XrKeyEventSink() {}

    public static void sendCharacter(XrActivity activity, char character) {
        XServer server = activity != null ? activity.getXServer() : null;
        if (server == null) return;

        KeyEvent[] events = VIRTUAL_KEYBOARD.getEvents(new char[]{character});
        if (events == null) return;
        for (KeyEvent event : events) {
            // KeyCharacterMap emits explicit Shift events for capitals and punctuation. The
            // virtual path translates the remaining event's Unicode/meta state into the guest
            // keysym; the ordinary path would lose that state and turn '@' into '2', etc.
            if (event.getKeyCode() != KeyEvent.KEYCODE_SHIFT_LEFT &&
                    event.getKeyCode() != KeyEvent.KEYCODE_SHIFT_RIGHT) {
                server.keyboard.onVirtualKeyEvent(event);
            }
        }
    }

    public static void sendKey(XrActivity activity, int keyCode) {
        XServer server = activity != null ? activity.getXServer() : null;
        if (server == null) return;

        server.keyboard.onKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
        server.keyboard.onKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, keyCode));
    }
}
