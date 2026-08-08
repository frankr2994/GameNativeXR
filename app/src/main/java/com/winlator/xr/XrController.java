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

import android.content.Intent;
import android.util.Pair;
import android.view.KeyEvent;

import androidx.preference.PreferenceManager;

import com.drbeef.externalhapticsservice.HapticsConstants;
import com.drbeef.externalhapticsservice.HapticServiceClient;

import com.winlator.xr.api.XrAPI;
import com.winlator.xr.api.XrInterface;
import com.winlator.xr.ui.XrContentDialog;
import com.winlator.xr.ui.XrDialog;
import com.winlator.xserver.Keyboard;
import com.winlator.xserver.Pointer;
import com.winlator.xserver.XKeycode;

import com.winlator.xr.input.PreGameInputRouter;
import com.winlator.xr.input.PreGameInputRouterImpl;
import com.winlator.xr.input.InputRoutingResult;
import com.winlator.xr.input.RoutedInputAction;
import com.winlator.xr.input.ControllerHand;
import com.winlator.xr.input.ControllerInputFrame;
import com.winlator.xr.input.XrControllerInputAdapter;
import com.winlator.xr.input.InputRouterMode;
import com.winlator.xr.input.RayPointerMapper;
import com.winlator.xr.input.RayPointerMapperImpl;
import com.winlator.xr.input.ControllerPointerRay;
import com.winlator.xr.input.VirtualScreenSurface;
import com.winlator.xr.input.PointerVector3;
import com.winlator.xr.input.RayPointerHit;
import com.winlator.xr.input.XrLivePolicy;
import com.winlator.xr.input.XrInputSink;

import java.util.Vector;

public class XrController {

    private final XrActivity instance;
    private boolean[] currentButtons = new boolean[XrInterface.ControllerButton.values().length];
    private final float[] lastAxes = new float[XrInterface.ControllerAxis.values().length];
    private final boolean[] lastButtons = new boolean[XrInterface.ControllerButton.values().length];
    private long lastDialogShown = 0;
    private long lastMouseUpdate = 0;
    private short lastMouseX = 0;
    private short lastMouseY = 0;
    private float mouseSpeed = 1;
    private final float[] smoothedMouse = new float[2];

    // External haptics
    private boolean isExternalHapticsRunning = false;
    private final float[] lastVibration = new float[2];
    private final Vector<HapticServiceClient> externalHapticsServiceClients = new Vector<>();
    private final Vector<Pair<String, String>> externalHapticsServiceDetails = new Vector<>();

    private final PreGameInputRouter inputRouter;
    private final RayPointerMapper rayPointerMapper = new RayPointerMapperImpl();
    private long lastKeyboardToggleTime = 0;
    private final XrLivePolicy livePolicy;

    public XrController() {
        instance = XrActivity.getInstance();
        inputRouter = new PreGameInputRouterImpl(ControllerHand.RIGHT, this::onRoute);
        mouseSpeed = PreferenceManager.getDefaultSharedPreferences(instance).getFloat("cursor_speed", 1.0f);

        externalHapticsServiceDetails.add(Pair.create(HapticsConstants.BHAPTICS_PACKAGE, HapticsConstants.BHAPTICS_ACTION_FILTER));
        externalHapticsServiceDetails.add(Pair.create(HapticsConstants.FORCETUBE_PACKAGE, HapticsConstants.FORCETUBE_ACTION_FILTER));
        for (Pair<String, String> serviceDetail : externalHapticsServiceDetails) {
            Intent intent = new Intent(serviceDetail.second).setPackage(serviceDetail.first);
            HapticServiceClient client = new HapticServiceClient(instance, (state, desc) -> {}, intent);
            client.bindService();
            externalHapticsServiceClients.add(client);
        }

        livePolicy = new XrLivePolicy(inputRouter, new XrInputSink() {
            @Override
            public void injectPointerButtonPress(Pointer.Button button) {
                if (instance.getXServer() != null) instance.getXServer().injectPointerButtonPress(button);
            }
            @Override
            public void injectPointerButtonRelease(Pointer.Button button) {
                if (instance.getXServer() != null) instance.getXServer().injectPointerButtonRelease(button);
            }
            @Override
            public void injectKeyPress(XKeycode keycode) {
                if (instance.getXServer() != null) instance.getXServer().injectKeyPress(keycode);
            }
            @Override
            public void injectKeyRelease(XKeycode keycode) {
                if (instance.getXServer() != null) instance.getXServer().injectKeyRelease(keycode);
            }
        });
    }

    public void unload() {
        try {
            for (HapticServiceClient externalHapticsServiceClient : externalHapticsServiceClients) {
                externalHapticsServiceClient.stopBinding();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public boolean updateAndroidInput(boolean[] buttons, float[] axes) {
        boolean leftToggle = getButtonClicked(buttons, XrInterface.ControllerButton.L_THUMBSTICK_PRESS) && buttons[XrInterface.ControllerButton.L_X.ordinal()];
        boolean rightToggle = getButtonClicked(buttons, XrInterface.ControllerButton.R_THUMBSTICK_PRESS) && buttons[XrInterface.ControllerButton.R_A.ordinal()];
        if (leftToggle || rightToggle) {
            livePolicy.toggleGuestUI();
        }

        XrContentDialog dialog = XrContentDialog.getFrontInstance();
        livePolicy.updateMode(dialog != null);

        ControllerHand preferredHand = XrActivity.mouseLeftHanded ? ControllerHand.LEFT : ControllerHand.RIGHT;
        if (inputRouter instanceof PreGameInputRouterImpl) {
            ((PreGameInputRouterImpl) inputRouter).setPreferredHand(preferredHand);
        }

        ControllerInputFrame frame = XrControllerInputAdapter.INSTANCE.fromOpenXrButtons(
            buttons,
            com.winlator.xr.input.ControllerConnectionState.UNKNOWN,
            com.winlator.xr.input.ControllerConnectionState.UNKNOWN
        );
        InputRoutingResult result = inputRouter.processControllerInput(frame);

        // Ray mapping for visible reticle in pointer modes
        if (result.getMode() == InputRouterMode.GUEST_POINTER || result.getMode() == InputRouterMode.GUEST_TEXT) {
            float yaw = axes[XrActivity.mouseLeftHanded ? XrInterface.ControllerAxis.L_YAW.ordinal() : XrInterface.ControllerAxis.R_YAW.ordinal()];
            float pitch = axes[XrActivity.mouseLeftHanded ? XrInterface.ControllerAxis.L_PITCH.ordinal() : XrInterface.ControllerAxis.R_PITCH.ordinal()];
            PointerVector3 origin = new PointerVector3(0f, 0f, 0f);
            ControllerPointerRay ray = ControllerPointerRay.Companion.fromYawPitch(origin, yaw, pitch);

            float aspect = (float)instance.getXServer().screenInfo.width / (float)instance.getXServer().screenInfo.height;
            float distance = XrActivity.getDistance();
            VirtualScreenSurface surface = VirtualScreenSurface.Companion.facingViewer(
                distance,
                distance * 1.5f,
                (distance * 1.5f) / aspect,
                instance.getXServer().screenInfo.width,
                instance.getXServer().screenInfo.height
            );

            RayPointerHit hit = rayPointerMapper.map(ray, surface);
            if (hit != null) {
                smoothedMouse[0] = hit.getPixelX();
                smoothedMouse[1] = hit.getPixelY();
                // Ensure mouse pointer is updated immediately for the reticle
                instance.getXServer().injectPointerMove((int) smoothedMouse[0], (int) smoothedMouse[1]);
            }
        }

        System.arraycopy(buttons, 0, lastButtons, 0, buttons.length);
        System.arraycopy(axes, 0, lastAxes, 0, axes.length);

        if (!result.contains(RoutedInputAction.FORWARD_TO_GAME_INPUT)) {
            lastDialogShown = System.currentTimeMillis();
        }

        return result.contains(RoutedInputAction.FORWARD_TO_GAME_INPUT);
    }



    private void onRoute(InputRoutingResult result) {
        XrContentDialog dialog = XrContentDialog.getFrontInstance();
        for (RoutedInputAction action : result.getActions()) {
            switch (action) {
                case ANDROID_CONFIRM:
                    if (dialog != null) instance.runOnUiThread(() -> dialog.onKeyAction(KeyEvent.KEYCODE_ENTER));
                    break;
                case ANDROID_BACK:
                    if (dialog != null) instance.runOnUiThread(dialog::onBackPressed);
                    break;
                case ANDROID_NAV_UP:
                    if (dialog != null) instance.runOnUiThread(() -> dialog.onKeyAction(KeyEvent.KEYCODE_DPAD_UP));
                    break;
                case ANDROID_NAV_DOWN:
                    if (dialog != null) instance.runOnUiThread(() -> dialog.onKeyAction(KeyEvent.KEYCODE_DPAD_DOWN));
                    break;
                case ANDROID_NAV_LEFT:
                    if (dialog != null) instance.runOnUiThread(() -> dialog.onKeyAction(KeyEvent.KEYCODE_DPAD_LEFT));
                    break;
                case ANDROID_NAV_RIGHT:
                    if (dialog != null) instance.runOnUiThread(() -> dialog.onKeyAction(KeyEvent.KEYCODE_DPAD_RIGHT));
                    break;
                case REQUEST_VISIBLE_KEYBOARD:
                    lastKeyboardToggleTime = System.currentTimeMillis();
                    instance.runOnUiThread(() -> new com.winlator.xr.ui.XrKeyboardOverlay(instance, false).show());
                    break;
                case CLOSE_VISIBLE_KEYBOARD:
                    if (dialog instanceof com.winlator.xr.ui.XrKeyboardOverlay) {
                        instance.runOnUiThread(dialog::onBackPressed);
                    }
                    break;
                default:
                    // Handled by XrLivePolicy
                    break;
            }
        }
        livePolicy.routeActions(result);
    }

    public void updateHaptics(XrAPI xrAPI) {
        // Define haptics
        String[] sendEvent = {null, null};
        XrInterface.AppInput[] haptics = {XrInterface.AppInput.L_HAPTICS, XrInterface.AppInput.R_HAPTICS};
        for (int i = 0; i < haptics.length; i++) {
            XrInterface.AppInput haptic = haptics[i];
            float value = xrAPI.getValue(haptic);
            if (value > 0.0f) {
                // External haptics (scheme from Doom3Quest)
                if (lastVibration[i] < value) {
                    sendEvent[i] = value > 1 ? "shotgun_fire" : "pistol_fire";
                }
                // Controller haptics
                instance.vibrateController(1, i, value);
                xrAPI.setValue(haptic, value - 0.1f);
                lastVibration[i] = value;
            } else {
                xrAPI.setValue(haptic, 0.0f);
                lastVibration[i] = 0.0f;
            }
        }

        // Update external haptics
        for (HapticServiceClient externalHapticsServiceClient : externalHapticsServiceClients) {
            if (externalHapticsServiceClient.hasService()) {
                try {
                    if (isExternalHapticsRunning != XrActivity.isUDP) {
                        if (XrActivity.isUDP) {
                            externalHapticsServiceClient.getHapticsService().hapticEnable();
                        } else {
                            externalHapticsServiceClient.getHapticsService().hapticDisable();
                        }
                    } else if (isExternalHapticsRunning) {
                        for (int i = 0; i < haptics.length; i++) {
                            if (sendEvent[i] != null) {
                                externalHapticsServiceClient.getHapticsService().hapticEvent("Doom3Quest", sendEvent[i], i + 1, 0, 100, 0, 0);
                            }
                        }
                        externalHapticsServiceClient.getHapticsService().hapticFrameTick();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        isExternalHapticsRunning = XrActivity.isUDP;
    }

    public void updateKeyboardButtons(boolean[] buttons) {
        // Get OpenXR input
        XrInterface.ControllerButton secondaryGrip = !XrActivity.mouseLeftHanded ? XrInterface.ControllerButton.L_GRIP : XrInterface.ControllerButton.R_GRIP;
        XrInterface.ControllerButton secondaryTrigger = !XrActivity.mouseLeftHanded ? XrInterface.ControllerButton.L_TRIGGER : XrInterface.ControllerButton.R_TRIGGER;
        XrInterface.ControllerButton secondaryUp = !XrActivity.mouseLeftHanded ? XrInterface.ControllerButton.L_THUMBSTICK_UP : XrInterface.ControllerButton.R_THUMBSTICK_UP;
        XrInterface.ControllerButton secondaryDown = !XrActivity.mouseLeftHanded ? XrInterface.ControllerButton.L_THUMBSTICK_DOWN : XrInterface.ControllerButton.R_THUMBSTICK_DOWN;
        XrInterface.ControllerButton secondaryLeft = !XrActivity.mouseLeftHanded ? XrInterface.ControllerButton.L_THUMBSTICK_LEFT : XrInterface.ControllerButton.R_THUMBSTICK_LEFT;
        XrInterface.ControllerButton secondaryRight = !XrActivity.mouseLeftHanded ? XrInterface.ControllerButton.L_THUMBSTICK_RIGHT : XrInterface.ControllerButton.R_THUMBSTICK_RIGHT;

        // Pass the controller mapping into XServer
        currentButtons = buttons;
        mapKey(XrInterface.ControllerButton.L_MENU, XKeycode.KEY_ESC.getId());
        mapKey(XrInterface.ControllerButton.R_A, (byte)instance.container.getXrButtonA());
        mapKey(XrInterface.ControllerButton.R_B, (byte)instance.container.getXrButtonB());
        mapKey(XrInterface.ControllerButton.L_X, (byte)instance.container.getXrButtonX());
        mapKey(XrInterface.ControllerButton.L_Y, (byte)instance.container.getXrButtonY());
        mapKey(secondaryGrip, (byte)instance.container.getXrButtonGrip());
        mapKey(secondaryTrigger, (byte)instance.container.getXrButtonTrigger());
        mapKey(secondaryUp, (byte)instance.container.getXrThumbstickUp());
        mapKey(secondaryDown, (byte)instance.container.getXrThumbstickDown());
        mapKey(secondaryLeft, (byte)instance.container.getXrThumbstickLeft());
        mapKey(secondaryRight, (byte)instance.container.getXrThumbstickRight());
        System.arraycopy(buttons, 0, lastButtons, 0, buttons.length);
    }

    public void updateMouseAxes(float[] axes, boolean headMapping) {
        // Get OpenXR input
        XrInterface.ControllerAxis mouseAxisX = XrActivity.mouseLeftHanded ? XrInterface.ControllerAxis.L_X : XrInterface.ControllerAxis.R_X;
        XrInterface.ControllerAxis mouseAxisY = XrActivity.mouseLeftHanded ? XrInterface.ControllerAxis.L_Y : XrInterface.ControllerAxis.R_Y;

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

    public void updateMouseLightgun(float[] axes, float distance) {
        // Get values
        float x = axes[XrActivity.mouseLeftHanded ? XrInterface.ControllerAxis.L_X.ordinal() : XrInterface.ControllerAxis.R_X.ordinal()] - axes[XrInterface.ControllerAxis.HMD_X.ordinal()];;
        float y = axes[XrActivity.mouseLeftHanded ? XrInterface.ControllerAxis.L_Y.ordinal() : XrInterface.ControllerAxis.R_Y.ordinal()] - axes[XrInterface.ControllerAxis.HMD_Y.ordinal()];;
        float yaw = axes[XrActivity.mouseLeftHanded ? XrInterface.ControllerAxis.L_YAW.ordinal() : XrInterface.ControllerAxis.R_YAW.ordinal()];
        float pitch = axes[XrActivity.mouseLeftHanded ? XrInterface.ControllerAxis.L_PITCH.ordinal() : XrInterface.ControllerAxis.R_PITCH.ordinal()];
        float cx = (float) instance.getXServer().windowManager.rootWindow.getWidth() / 2;
        float cy = (float) instance.getXServer().windowManager.rootWindow.getHeight() / 2;
        float aspect = (float) Math.pow(cx / cy, 0.15);

        //Positional mapping
        float amount = (cx + cy) / 2.0f;
        smoothedMouse[0] = cx + x * amount / aspect;
        smoothedMouse[1] = cy - y * amount;

        //Angular mapping
        amount = distance / 4.0f * (cx + cy) / 2;
        smoothedMouse[0] -= (float) (Math.tan(Math.toRadians(yaw) / aspect) * amount);
        smoothedMouse[1] += (float) (Math.tan(Math.toRadians(pitch)) * amount);
    }

    public void updateMouseSnapturn(boolean[] buttons, int step) {
        // Get OpenXR input
        XrInterface.ControllerButton primaryLeft = XrActivity.mouseLeftHanded ? XrInterface.ControllerButton.L_THUMBSTICK_LEFT : XrInterface.ControllerButton.R_THUMBSTICK_LEFT;
        XrInterface.ControllerButton primaryRight = XrActivity.mouseLeftHanded ? XrInterface.ControllerButton.L_THUMBSTICK_RIGHT : XrInterface.ControllerButton.R_THUMBSTICK_RIGHT;

        // Apply snapturn to the input
        if (getButtonClicked(buttons, primaryLeft)) {
            smoothedMouse[0] -= step;
        }
        if (getButtonClicked(buttons, primaryRight)) {
            smoothedMouse[0] += step;
        }
    }

    public void updateMouseState(boolean[] buttons, float fps) {
        // Get OpenXR input
        Pointer mouse = instance.getXServer().pointer;
        XrInterface.ControllerButton primaryGrip = XrActivity.mouseLeftHanded ? XrInterface.ControllerButton.L_GRIP : XrInterface.ControllerButton.R_GRIP;
        XrInterface.ControllerButton primaryTrigger = XrActivity.mouseLeftHanded ? XrInterface.ControllerButton.L_TRIGGER : XrInterface.ControllerButton.R_TRIGGER;
        XrInterface.ControllerButton primaryUp = XrActivity.mouseLeftHanded ? XrInterface.ControllerButton.L_THUMBSTICK_UP : XrInterface.ControllerButton.R_THUMBSTICK_UP;
        XrInterface.ControllerButton primaryDown = XrActivity.mouseLeftHanded ? XrInterface.ControllerButton.L_THUMBSTICK_DOWN : XrInterface.ControllerButton.R_THUMBSTICK_DOWN;

        // Apply values
        currentButtons = buttons;
        mouse.setX((int) smoothedMouse[0]);
        mouse.setY((int) smoothedMouse[1]);
        mapButton(primaryTrigger, Pointer.Button.BUTTON_LEFT);
        mapButton(primaryGrip, Pointer.Button.BUTTON_RIGHT);
        mapButton(primaryUp, Pointer.Button.BUTTON_SCROLL_UP);
        mapButton(primaryDown, Pointer.Button.BUTTON_SCROLL_DOWN);

        // Limit cursor updates to the FPS (this prevents freezing)
        long timestamp = System.currentTimeMillis();
        if (timestamp - lastMouseUpdate > 1000 / Math.max(fps, 1)) {
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

    private void mapButton(XrInterface.ControllerButton xrButton, Pointer.Button button) {
        Pointer mouse = instance.getXServer().pointer;
        if (currentButtons[xrButton.ordinal()] != lastButtons[xrButton.ordinal()]) {
            mouse.setButton(button, currentButtons[xrButton.ordinal()]);
        }
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
