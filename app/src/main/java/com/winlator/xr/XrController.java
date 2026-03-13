/*
 * Copyright (C) 2024-2026 WinlatorXR
 *
 * This file is part of WinlatorXR.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.winlator.xr;

import androidx.preference.PreferenceManager;

import com.winlator.XrActivity;
import com.winlator.container.Container;
import com.winlator.xserver.Keyboard;
import com.winlator.xserver.Pointer;
import com.winlator.xserver.XKeycode;

public class XrController {

    private final XrActivity instance;
    private boolean[] currentButtons = new boolean[XrInterface.ControllerButton.values().length];
    private final float[] lastAxes = new float[XrInterface.ControllerAxis.values().length];
    private final boolean[] lastButtons = new boolean[XrInterface.ControllerButton.values().length];
    private long lastMouseUpdate = 0;
    private short lastMouseX = 0;
    private short lastMouseY = 0;
    private float mouseSpeed = 1;
    private final float[] smoothedMouse = new float[2];

    public XrController() {
        instance = XrActivity.getInstance();
        mouseSpeed = PreferenceManager.getDefaultSharedPreferences(instance).getFloat("cursor_speed", 1.0f);
    }

    public void updateKeyboardButtons(boolean[] buttons) {
        // Get OpenXR input
        int primaryController = instance.container.getPrimaryController();
        XrInterface.ControllerButton secondaryGrip = primaryController == 1 ? XrInterface.ControllerButton.L_GRIP : XrInterface.ControllerButton.R_GRIP;
        XrInterface.ControllerButton secondaryTrigger = primaryController == 1 ? XrInterface.ControllerButton.L_TRIGGER : XrInterface.ControllerButton.R_TRIGGER;
        XrInterface.ControllerButton secondaryUp = primaryController == 1 ? XrInterface.ControllerButton.L_THUMBSTICK_UP : XrInterface.ControllerButton.R_THUMBSTICK_UP;
        XrInterface.ControllerButton secondaryDown = primaryController == 1 ? XrInterface.ControllerButton.L_THUMBSTICK_DOWN : XrInterface.ControllerButton.R_THUMBSTICK_DOWN;
        XrInterface.ControllerButton secondaryLeft = primaryController == 1 ? XrInterface.ControllerButton.L_THUMBSTICK_LEFT : XrInterface.ControllerButton.R_THUMBSTICK_LEFT;
        XrInterface.ControllerButton secondaryRight = primaryController == 1 ? XrInterface.ControllerButton.L_THUMBSTICK_RIGHT : XrInterface.ControllerButton.R_THUMBSTICK_RIGHT;

        // Pass the controller mapping into XServer
        currentButtons = buttons;
        mapKey(XrInterface.ControllerButton.L_MENU, XKeycode.KEY_ESC.getId());
        mapKey(XrInterface.ControllerButton.R_A, instance.container.getControllerMapping(Container.XrControllerMapping.BUTTON_A));
        mapKey(XrInterface.ControllerButton.R_B, instance.container.getControllerMapping(Container.XrControllerMapping.BUTTON_B));
        mapKey(XrInterface.ControllerButton.L_X, instance.container.getControllerMapping(Container.XrControllerMapping.BUTTON_X));
        mapKey(XrInterface.ControllerButton.L_Y, instance.container.getControllerMapping(Container.XrControllerMapping.BUTTON_Y));
        mapKey(secondaryGrip, instance.container.getControllerMapping(Container.XrControllerMapping.BUTTON_GRIP));
        mapKey(secondaryTrigger, instance.container.getControllerMapping(Container.XrControllerMapping.BUTTON_TRIGGER));
        mapKey(secondaryUp, instance.container.getControllerMapping(Container.XrControllerMapping.THUMBSTICK_UP));
        mapKey(secondaryDown, instance.container.getControllerMapping(Container.XrControllerMapping.THUMBSTICK_DOWN));
        mapKey(secondaryLeft, instance.container.getControllerMapping(Container.XrControllerMapping.THUMBSTICK_LEFT));
        mapKey(secondaryRight, instance.container.getControllerMapping(Container.XrControllerMapping.THUMBSTICK_RIGHT));
        System.arraycopy(buttons, 0, lastButtons, 0, buttons.length);
    }

    public void updateMouseAxes(float[] axes, boolean headMapping) {
        // Get OpenXR input
        int primaryController = instance.container.getPrimaryController();
        XrInterface.ControllerAxis mouseAxisX = primaryController == 0 ? XrInterface.ControllerAxis.L_X : XrInterface.ControllerAxis.R_X;
        XrInterface.ControllerAxis mouseAxisY = primaryController == 0 ? XrInterface.ControllerAxis.L_Y : XrInterface.ControllerAxis.R_Y;

        // Mouse control with hand
        float f = 0.75f;
        float meter2px = instance.getXServer().screenInfo.width * 10.0f;
        float dx = (axes[mouseAxisX.ordinal()] - lastAxes[mouseAxisX.ordinal()]) * meter2px;
        float dy = (axes[mouseAxisY.ordinal()] - lastAxes[mouseAxisY.ordinal()]) * meter2px;
        if ((Math.abs(dx) > 300) || (Math.abs(dy) > 300)) {
            dx = 0;
            dy = 0;
        }

        // Mouse control with head
        Pointer mouse = instance.getXServer().pointer;
        if (headMapping) {
            float angle2px = instance.getXServer().screenInfo.width * 0.05f / f;
            dx = getAngleDiff(lastAxes[XrInterface.ControllerAxis.HMD_YAW.ordinal()], axes[XrInterface.ControllerAxis.HMD_YAW.ordinal()]) * angle2px;
            dy = getAngleDiff(lastAxes[XrInterface.ControllerAxis.HMD_PITCH.ordinal()], axes[XrInterface.ControllerAxis.HMD_PITCH.ordinal()]) * angle2px;
            if (Float.isNaN(dy)) {
                dy = 0;
            }
            smoothedMouse[0] = mouse.getClampedX() + 0.5f;
            smoothedMouse[1] = mouse.getClampedY() + 0.5f;
        }

        // Mouse smoothing
        dx *= mouseSpeed;
        dy *= mouseSpeed;
        smoothedMouse[0] = smoothedMouse[0] * f + (mouse.getClampedX() + 0.5f + dx) * (1 - f);
        smoothedMouse[1] = smoothedMouse[1] * f + (mouse.getClampedY() + 0.5f - dy) * (1 - f);

        System.arraycopy(axes, 0, lastAxes, 0, axes.length);
    }

    public void updateMouseSnapturn(boolean[] buttons, int step) {
        // Get OpenXR input
        Pointer mouse = instance.getXServer().pointer;
        int primaryController = instance.container.getPrimaryController();
        XrInterface.ControllerButton primaryLeft = primaryController == 0 ? XrInterface.ControllerButton.L_THUMBSTICK_LEFT : XrInterface.ControllerButton.R_THUMBSTICK_LEFT;
        XrInterface.ControllerButton primaryRight = primaryController == 0 ? XrInterface.ControllerButton.L_THUMBSTICK_RIGHT : XrInterface.ControllerButton.R_THUMBSTICK_RIGHT;

        // Apply snapturn to the input
        if (getButtonClicked(buttons, primaryLeft)) {
            smoothedMouse[0] = mouse.getClampedX() - step;
        }
        if (getButtonClicked(buttons, primaryRight)) {
            smoothedMouse[0] = mouse.getClampedX() + step;
        }
    }

    public void updateMouseState(boolean[] buttons) {
        // Get OpenXR input
        Pointer mouse = instance.getXServer().pointer;
        int primaryController = instance.container.getPrimaryController();
        XrInterface.ControllerButton primaryGrip = primaryController == 0 ? XrInterface.ControllerButton.L_GRIP : XrInterface.ControllerButton.R_GRIP;
        XrInterface.ControllerButton primaryTrigger = primaryController == 0 ? XrInterface.ControllerButton.L_TRIGGER : XrInterface.ControllerButton.R_TRIGGER;
        XrInterface.ControllerButton primaryUp = primaryController == 0 ? XrInterface.ControllerButton.L_THUMBSTICK_UP : XrInterface.ControllerButton.R_THUMBSTICK_UP;
        XrInterface.ControllerButton primaryDown = primaryController == 0 ? XrInterface.ControllerButton.L_THUMBSTICK_DOWN : XrInterface.ControllerButton.R_THUMBSTICK_DOWN;

        // Apply values
        mouse.setX((int) smoothedMouse[0]);
        mouse.setY((int) smoothedMouse[1]);
        mouse.setButton(Pointer.Button.BUTTON_LEFT, buttons[primaryTrigger.ordinal()]);
        mouse.setButton(Pointer.Button.BUTTON_RIGHT, buttons[primaryGrip.ordinal()]);
        mouse.setButton(Pointer.Button.BUTTON_SCROLL_UP, buttons[primaryUp.ordinal()]);
        mouse.setButton(Pointer.Button.BUTTON_SCROLL_DOWN, buttons[primaryDown.ordinal()]);

        // Limit cursor updates to the FPS (this prevents freezing)
        long timestamp = System.currentTimeMillis();
        if (timestamp - lastMouseUpdate > 1000 / Math.max(instance.getLastFPS(), 1)) {
            if ((lastMouseX != mouse.getX()) || (lastMouseY != mouse.getY())) {
                lastMouseUpdate = timestamp;
                lastMouseX = mouse.getX();
                lastMouseY = mouse.getY();
                mouse.triggerOnPointerMove(lastMouseX, lastMouseY);
            }
        }
    }

    public boolean getButtonClicked(boolean[] buttons, XrInterface.ControllerButton button) {
        return buttons[button.ordinal()] && !lastButtons[button.ordinal()];
    }

    private float getAngleDiff(float oldAngle, float newAngle) {
        float diff = oldAngle - newAngle;
        while (diff > 180) {
            diff -= 360;
        }
        while (diff < -180) {
            diff += 360;
        }
        return diff;
    }

    private void mapKey(XrInterface.ControllerButton xrButton, byte xKeycode) {
        Keyboard keyboard = instance.getXServer().keyboard;
        if (currentButtons[xrButton.ordinal()] != lastButtons[xrButton.ordinal()]) {
            if (currentButtons[xrButton.ordinal()]) {
                keyboard.setKeyPress(xKeycode, 0);
            } else {
                keyboard.setKeyRelease(xKeycode);
            }
        }
    }
}
