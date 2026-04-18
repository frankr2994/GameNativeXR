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

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.opengl.GLES20;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.Pair;

import com.winlator.XrActivity;
import com.winlator.math.XForm;
import com.winlator.renderer.GLRenderer;
import com.winlator.renderer.RenderableWindow;
import com.winlator.renderer.Texture;
import com.winlator.renderer.material.BGRMaterial;
import com.winlator.renderer.material.ShaderMaterial;
import com.winlator.widget.XServerView;
import com.winlator.xserver.Drawable;
import com.winlator.xserver.XLock;
import com.winlator.xserver.XServer;

import javax.microedition.khronos.opengles.GL10;

public class XrRenderer extends GLRenderer {
    private final BGRMaterial bgrMaterial = new BGRMaterial();

    private final Texture[] lastTexture = {new Texture(), new Texture()};
    private short lastTextureWidth = 0;
    private short lastTextureHeight = 0;

    private long timestampHadWindow = Long.MAX_VALUE;

    private boolean xrImmersive = false;
    private boolean xrFrameReady = false;
    private boolean xrFrameStarted = false;

    private int secUpdated;
    private int[] pixels;
    private Bitmap bitmap;
    private Canvas canvas;
    private Drawable drawable;
    private Paint paint;


    public XrRenderer(XServerView xServerView, XServer xServer) {
        super(xServerView, xServer);
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        if (XrActivity.shouldRebootInXR) {
            return;
        }

        width = xServer.screenInfo.width;
        height = xServer.screenInfo.height;
        if (width < 1280) {
            height = 1280 * height / width;
            width = 1280;
        }

        XrActivity.getInstance().init(width, height, 72, 4, 4);
        height = width; ////Use square resolution
        GLES20.glViewport(0, 0, width, height);
        magnifierEnabled = false;

        super.onSurfaceChanged(gl, width, height);
    }

    @Override
    protected boolean preDrawable(ShaderMaterial material, Drawable drawable) {
        if (XrActivity.isEnabled() && XrActivity.isVR && xrFrameReady) {
            Pair<Boolean, Integer> framesync = XrActivity.getInstance().processFramesync(drawable);
            xrFrameReady = false;
            if (XrActivity.isAER) {
                renderAER(drawable, material, framesync.first, framesync.second);
                return false;
            }
        }
        return super.preDrawable(material, drawable);
    }

    @Override
    protected void preFrame() {
        super.preFrame();
        if (XrActivity.shouldRebootInXR) {
            return;
        }

        xrImmersive = false;
        if (XrActivity.isEnabled()) {
            fullscreen = XrActivity.isVR;
            xrImmersive = (XrActivity.isImmersive || fullscreen) && (XrContentDialog.getFrontInstance() == null);
            xrFrameReady = xrFrameStarted = XrActivity.getInstance().initFrame(xrImmersive,
                    XrActivity.isSBS, XrActivity.isAER, XrActivity.getDistance());
            XrActivity.getInstance().updateFrame(getLastFPS(), xServer);
            if (!XrActivity.isAER) {
                XrActivity.getInstance().bindFBO(0);
            }
        } else {
            fullscreen = false;
        }
    }

    @Override
    protected void postFrame() {
        super.postFrame();
        if (XrActivity.shouldRebootInXR) {
            return;
        }

        if (xrFrameStarted) {
            renderFPSCounter();
            renderDialog();
            xrFrameReady = false;
            XrActivity.getInstance().endFrame();
            xServerView.requestRender();
        }
    }

    @Override
    protected Pair<Float, Float> preTransform() {
        if (!fullscreen && XrActivity.isSBS && !renderableWindows.isEmpty()) {
            RenderableWindow window = renderableWindows.get(renderableWindows.size() - 1);
            return new Pair<>((float)window.getRootX(), (float)window.getRootY());
        } else {
            return new Pair<>(0.0f, 0.0f);
        }
    }

    @Override
    protected void preWindows() {
        super.preWindows();

        if (!fullscreen && XrActivity.isSBS && !renderableWindows.isEmpty()) {
            RenderableWindow window = renderableWindows.get(renderableWindows.size() - 1);
            magnifierZoom = xServer.screenInfo.width / (float)window.getWidth();
            magnifierEnabled = true;
        } else {
            magnifierEnabled = false;
            magnifierZoom = 1;
        }
    }

    @Override
    protected void postWindows() {
        super.postWindows();
        if (!renderableWindows.isEmpty()) {
            timestampHadWindow = System.currentTimeMillis();
        }  else if ((System.currentTimeMillis() - timestampHadWindow > 1000)) {
            XrActivity.getInstance().runOnUiThread(() -> XrActivity.getInstance().closeSession());
        }
    }

    private float getUIScale() {
        float scale = xServer.screenInfo.height / 1200.0f;
        if (Build.MANUFACTURER.compareToIgnoreCase("PICO") == 0) {
            scale = 0.75f;
            DisplayMetrics displayMetrics = new DisplayMetrics();
            XrActivity.getInstance().getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
            scale *= (float)Math.min(xServer.screenInfo.width, xServer.screenInfo.height);
            scale /= (float)Math.min(displayMetrics.widthPixels, displayMetrics.heightPixels);
        }
        return scale;
    }

    private void renderAER(Drawable drawable, ShaderMaterial material, boolean shouldUpdate, int targetFBO) {
        if ((lastTextureWidth != drawable.getStride()) || (lastTextureHeight != drawable.height)) {
            for (int i = 0; i < lastTexture.length; i++) {
                lastTexture[i].destroy();
                lastTexture[i] = new Texture();
            }
            lastTextureWidth = drawable.getStride();
            lastTextureHeight = drawable.height;
        }

        if (shouldUpdate) {
            lastTexture[targetFBO].setNeedsUpdate(true);
            lastTexture[targetFBO].updateFromBuffer(drawable.getData(), drawable.getStride(), drawable.height);
        }

        for (int i = 0; i < lastTexture.length; i++) {
            XrActivity.getInstance().bindFBO(i);
            if (lastTexture[i].isAllocated()) {
                renderTexture(lastTexture[i], material);
            }
        }
        XrActivity.getInstance().bindFBO(-1);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
    }

    private void renderDialog() {
        bgrMaterial.use();
        GLES20.glUniform2f(bgrMaterial.getUniformLocation("viewSize"), xServer.screenInfo.width, xServer.screenInfo.height);
        quadVertices.bind(bgrMaterial.programId);

        XForm.identity(tmpXForm2);
        float aspect = xServer.screenInfo.width / (float)xServer.screenInfo.height;;
        try (XLock lock = xServer.lock(XServer.Lockable.DRAWABLE_MANAGER)) {
            float div = XrActivity.isSBS ? 2 : 1;
            XrContentDialog dialog = XrContentDialog.getFrontInstance();
            if (dialog != null) {
                Drawable drawable = dialog.getDrawable();
                if (drawable != null) {
                    float scale = getUIScale();
                    int offsetX = (int) ((xServer.screenInfo.width - drawable.width * aspect * scale) / 2 / div);
                    int offsetY = (int) ((xServer.screenInfo.height - drawable.height * scale) / 2);
                    renderDrawable(drawable, offsetX, offsetY, bgrMaterial, false, scale * aspect / div, scale);
                    if (div > 1) {
                        offsetX += (int) (xServer.screenInfo.width / div);
                        renderDrawable(drawable, offsetX, offsetY, bgrMaterial, false, scale * aspect / div, scale);
                    }
                }
            }
        }
        quadVertices.disable();
    }

    private void renderFPSCounter() {
        if (renderableWindows.isEmpty()) {
            return;
        }

        //Allocate render arrays
        int w = 128;
        int h = 32;
        if ((pixels == null) || (bitmap.getWidth() != w) || (bitmap.getHeight() != h)) {
            pixels = new int[w * h];
            bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            canvas = new Canvas(bitmap);
            drawable = Drawable.fromBitmap(bitmap);
            paint = new Paint();
            paint.setTextSize(20);
            paint.setTypeface(Typeface.DEFAULT_BOLD);
        }

        // Update FPS info
        int sec = (int) ((System.currentTimeMillis() / 1000) % 60);
        if (secUpdated != sec) {
            String fps = (int)getLastFPS() + " FPS";
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
            Paint.FontMetrics fm = paint.getFontMetrics();
            paint.setColor(Color.BLACK);
            canvas.drawText(fps, 2, 2 - fm.ascent, paint);
            paint.setColor(Color.RED);
            canvas.drawText(fps, 0, -fm.ascent, paint);
            drawable.drawBitmap(bitmap);
            secUpdated = sec;
        }

        // Render counter
        bgrMaterial.use();
        GLES20.glUniform2f(bgrMaterial.getUniformLocation("viewSize"), xServer.screenInfo.width, xServer.screenInfo.height);
        quadVertices.bind(bgrMaterial.programId);
        float aspect = xServer.screenInfo.width / (float)xServer.screenInfo.height;;
        try (XLock lock = xServer.lock(XServer.Lockable.DRAWABLE_MANAGER)) {
            float div = XrActivity.isSBS ? 2 : 1;
            if (drawable != null) {
                float scale = xServer.screenInfo.height / 1200.0f;
                renderDrawable(drawable, 16, 16, bgrMaterial, false, scale * aspect / div, scale);
            }
        }
        quadVertices.disable();
    }

    @Override
    protected void renderWindows(ShaderMaterial material, boolean forceFullscreen) {
        super.renderWindows(material, xrImmersive);
    }
}
