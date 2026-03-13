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
package com.winlator;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.Display;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import com.winlator.xr.RuntimeMeta;
import com.winlator.xr.RuntimePFD;
import com.winlator.xr.RuntimePico;
import com.winlator.xr.XrController;
import com.winlator.xserver.XLock;
import com.winlator.xserver.XServer;

import static com.winlator.xr.XrInterface.ControllerButton;

public class XrActivity extends XServerDisplayActivity {
    private static XrActivity instance;

    // Configuration flags
    public static boolean isImmersive = false;
    public static boolean isSBS = false;
    private static boolean isEnabled = false;
    public static boolean mouseEmulation;

    // Rendering status
    private static long lastActive = 0;
    private static float lastDistance = 5;

    // XR input/output
    private XrController xrController = null;

    static {
        System.loadLibrary("xr");
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean usePassthrough = prefs.getBoolean("use_pt", true);
        nativeSetUsePT(usePassthrough);
        boolean curvedScreen = prefs.getBoolean("use_cs", false);
        nativeSetCurvedScreen(curvedScreen);
        mouseEmulation = prefs.getBoolean("use_xr_mouse", true);
    }

    @Override
    public synchronized void onResume() {
        super.onResume();
        instance = this;
        xrController = new XrController();
        sendManufacturer(Build.MANUFACTURER.toUpperCase());
    }

    @Override
    public synchronized void onDestroy() {
        super.onDestroy();
        closeSession();
    }

    public synchronized void closeSession() {
        Intent intent = getBaseContext().getPackageManager()
                .getLaunchIntentForPackage(getBaseContext().getPackageName());
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        }

        android.os.Process.killProcess(android.os.Process.myPid());
        System.exit(0);
    }

    public static XrActivity getInstance() {
        return instance;
    }

    public static float getDistance() {
        return lastDistance;
    }

    public static boolean isActive() {
        return Math.abs(System.currentTimeMillis() - lastActive) < 5000;
    }

    public static boolean isEnabled(Context context) {
        if (context != null) {
            isEnabled = PreferenceManager.getDefaultSharedPreferences(context).getBoolean("use_xr", true);
        }
        return isEnabled && isSupported();
    }

    public static boolean isSupported() {
        return getRuntime() != null;
    }

    public static void openIntent(Activity context, String containerId) {
        // Create the launch intent
        Intent intent = new Intent(context, getRuntime());
        intent.putExtra("container_id", containerId);

        // Set the flags
        final int mainDisplayId = Display.DEFAULT_DISPLAY;
        ActivityOptions options = ActivityOptions.makeBasic().setLaunchDisplayId(mainDisplayId);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK |
                Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        // Launch the activity
        context.getBaseContext().startActivity(intent, options.toBundle());
        context.finish();
    }

    public void updateFrame() {
        // Get OpenXR data
        float[] axes = instance.getAxes();
        boolean[] buttons = instance.getButtons();

        // Switch immersive/SBS mode
        updateShortcuts(buttons);

        // XServer input
        try (XLock lock = instance.getXServer().lock(XServer.Lockable.WINDOW_MANAGER, XServer.Lockable.INPUT_DEVICE)) {
            if (mouseEmulation) {
                xrController.updateMouseAxes(axes, false);
                xrController.updateMouseSnapturn(buttons, 25);
            }
            xrController.updateMouseState(buttons);
            xrController.updateKeyboardButtons(buttons);
            lastActive = System.currentTimeMillis();
        }
    }

    private void updateShortcuts(boolean[] buttons) {
        int primaryController = instance.container.getPrimaryController();
        ControllerButton primaryGrip = primaryController == 0 ? ControllerButton.L_GRIP : ControllerButton.R_GRIP;
        ControllerButton secondaryPress = primaryController == 1 ? ControllerButton.L_THUMBSTICK_PRESS : ControllerButton.R_THUMBSTICK_PRESS;
        if (xrController.getButtonClicked(buttons, secondaryPress)) {
            if (buttons[primaryGrip.ordinal()]) {
                isSBS = !isSBS;
            } else {
                isImmersive = !isImmersive;
            }
        }
    }

    private static Class getRuntime() {
        if (Build.MANUFACTURER.compareToIgnoreCase("PICO") == 0) {
            return RuntimePico.class;
        } else if (Build.MANUFACTURER.compareToIgnoreCase("PLAY FOR DREAM") == 0) {
            return RuntimePFD.class;
        } else if (Build.MANUFACTURER.compareToIgnoreCase("OCULUS") == 0) {
            return RuntimeMeta.class;
        } else if (Build.MANUFACTURER.compareToIgnoreCase("META") == 0) {
            return RuntimeMeta.class;
        } else {
            return null;
        }
    }

    // Rendering
    public native void init(int width, int height, int refresh, int cpu, int gpu);
    public native void bindFramebuffer();
    public native int getWidth();
    public native int getHeight();
    public native boolean initFrame(boolean immersive, boolean sbs, boolean aer, float distance);
    public native void bindFBO(int index);
    public native void endFrame();

    // Controllers
    public native float[] getAxes();
    public native boolean[] getButtons();
    public native void vibrateController(int duration, int chan, float intensity);

    // Settings
    public native void nativeSetFoV(float x, float y);
    public native void nativeSetCurvedScreen(boolean enabled);
    public native void nativeSetUsePT(boolean enabled);
    public native void nativeSetUseVR(boolean enabled);
    public native void nativeSetFramesync(int r, int g, int b, int a);
    public native void sendManufacturer(String manufacturer);
}
