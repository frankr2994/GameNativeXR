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

import android.opengl.GLES20;
import android.util.Pair;

import com.winlator.XrActivity;
import com.winlator.renderer.GLRenderer;
import com.winlator.renderer.RenderableWindow;
import com.winlator.renderer.material.ShaderMaterial;
import com.winlator.widget.XServerView;
import com.winlator.xserver.XServer;

import javax.microedition.khronos.opengles.GL10;

public class XrRenderer extends GLRenderer {
    private long timestampHadWindow = Long.MAX_VALUE;

    private boolean xrImmersive = false;
    private boolean xrFrameStarted = false;

    public XrRenderer(XServerView xServerView, XServer xServer) {
        super(xServerView, xServer);
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        if (XrActivity.isEnabled(null)) {
            XrActivity activity = XrActivity.getInstance();
            String res = activity.getScreenSize();
            String[] parts = res.split("x");
            width = Short.parseShort(parts[0]);
            height = Short.parseShort(parts[1]);
            if (width < 1280) {
                height = 1280 * height / width;
                width = 1280;
            }

            activity.init(width, height, 72, 4, 4);
            height = width; ////Use square resolution
            GLES20.glViewport(0, 0, width, height);
            magnifierEnabled = false;
        }

        super.onSurfaceChanged(gl, width, height);
    }

    @Override
    protected void preFrame() {
        super.preFrame();

        fullscreen = false;
        xrImmersive = false;
        if (XrActivity.isEnabled(null)) {
            xrImmersive = XrActivity.isImmersive;
            xrFrameStarted = XrActivity.getInstance().initFrame(xrImmersive,
                    XrActivity.isSBS, false, XrActivity.getDistance());
            XrActivity.getInstance().updateFrame();
            XrActivity.getInstance().bindFBO(0);
        }
    }

    @Override
    protected void postFrame() {
        super.postFrame();

        if (xrFrameStarted) {
            XrActivity.getInstance().endFrame();
            xServerView.requestRender();
        }
    }

    @Override
    protected Pair<Float, Float> preTransform() {
        if (!XrActivity.isEnabled(null)) {
            return super.preTransform();
        } else {
            return new Pair<>(0.0f, 0.0f);
        }
    }

    @Override
    protected void preWindows() {
        super.preWindows();

        if (XrActivity.isEnabled(null)) {
            if (!fullscreen && XrActivity.isSBS && !renderableWindows.isEmpty()) {
                RenderableWindow window = renderableWindows.get(renderableWindows.size() - 1);
                magnifierZoom = xServer.screenInfo.width / (float)window.content.width;
                magnifierEnabled = true;
            } else {
                magnifierEnabled = false;
                magnifierZoom = 1;
            }
        }
    }

    @Override
    protected void postWindows() {
        super.postWindows();
        if (!renderableWindows.isEmpty()) {
            timestampHadWindow = System.currentTimeMillis();
        }  else if ((System.currentTimeMillis() - timestampHadWindow > 1000)) {
            if (XrActivity.isEnabled(null)) {
                XrActivity.getInstance().runOnUiThread(() -> XrActivity.getInstance().closeSession());
            }
        }
    }

    @Override
    protected void renderWindows(ShaderMaterial material, boolean forceFullscreen) {
        super.renderWindows(material, xrImmersive);
    }
}
