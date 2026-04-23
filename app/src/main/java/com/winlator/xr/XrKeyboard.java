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

import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;

import com.winlator.core.AppUtils;
import com.winlator.xserver.XServer;

public class XrKeyboard implements TextWatcher {

    private static final KeyCharacterMap chars = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD);

    private final XrActivity instance;
    private String lastText = "";
    private EditText text;

    public XrKeyboard(EditText input) {
        instance = XrActivity.getInstance();
        text = input;
        text.addTextChangedListener(this);
    }

    public void sleep(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void show() {
        text.setVisibility(View.VISIBLE);
        resetText();
        AppUtils.showKeyboard(instance);
        text.requestFocus();
    }

    public void unload() {
        text.removeTextChangedListener(this);
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {}

    @Override
    public synchronized void afterTextChanged(Editable e) {
        XServer server = instance.getXServer();
        String s = text.getEditableText().toString();
        if (s.length() > lastText.length()) {
            lastText = s;
            char c = s.charAt(s.length() - 1);
            KeyEvent[] events = chars.getEvents(new char[]{c});
            if (events != null) {
                boolean first = true;
                for (KeyEvent keyEvent : events) {
                    if (!first) sleep(50);
                    server.keyboard.onKeyEvent(keyEvent);
                    first = false;
                }
            }
        } else {
            lastText = s;
            server.keyboard.onKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL));
            sleep(50);
            server.keyboard.onKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL));
        }
        if (s.isEmpty()) {
            resetText();
        }
    }

    private synchronized void resetText() {
        text.removeTextChangedListener(this);
        text.getEditableText().clear();
        text.getEditableText().append(" ");
        text.addTextChangedListener(this);
    }
}
