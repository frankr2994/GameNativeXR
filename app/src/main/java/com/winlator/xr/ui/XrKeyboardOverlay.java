package com.winlator.xr.ui;

import android.graphics.Color;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import app.gamenative.R;

import com.winlator.xr.XrActivity;
import com.winlator.xr.XrKeyEventSink;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Controller-operable keyboard rendered through XrContentDialog. It is a manual fallback for
 * guest text entry and intentionally keeps no typed-text buffer: characters are sent immediately
 * to the focused X-server window and never recorded by this view.
 */
public final class XrKeyboardOverlay extends XrContentDialog {
    private final XrActivity activity;
    private final boolean passwordMode;
    private final List<List<KeySpec>> rows = new ArrayList<>();
    private final List<List<Button>> keyViews = new ArrayList<>();

    private int selectedRow;
    private int selectedColumn;
    private boolean shift;
    private boolean capsLock;
    private TextView status;

    public XrKeyboardOverlay(XrActivity activity, boolean passwordMode) {
        super(activity, R.style.ContentDialog);
        this.activity = activity;
        this.passwordMode = passwordMode;
        createLayout();
    }

    @Override
    public void onKeyAction(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                moveSelection(0, -1);
                return;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                moveSelection(0, 1);
                return;
            case KeyEvent.KEYCODE_DPAD_UP:
                moveSelection(-1, 0);
                return;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                moveSelection(1, 0);
                return;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                activateSelectedKey();
                return;
            case KeyEvent.KEYCODE_ESCAPE:
            case KeyEvent.KEYCODE_BACK:
                dismiss();
                return;
            default:
                super.onKeyAction(keyCode);
        }
    }

    @Override
    public void onBackPressed() {
        dismiss();
    }

    private void createLayout() {
        contentView = new LinearLayout(getContext());
        LinearLayout root = (LinearLayout) contentView;
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(12), dp(12), dp(12));
        root.setBackgroundColor(Color.rgb(245, 245, 245));

        TextView title = new TextView(getContext());
        title.setText(R.string.keyboard);
        title.setTextColor(Color.BLACK);
        title.setTextSize(20);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        status = new TextView(getContext());
        status.setText(passwordMode ? "• • •" : "");
        status.setContentDescription(passwordMode ? "Password input" : "Text input");
        status.setTextColor(Color.DKGRAY);
        status.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(status, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        rows.add(characterRow("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"));
        rows.add(characterRow("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"));
        rows.add(characterRow("a", "s", "d", "f", "g", "h", "j", "k", "l"));
        rows.add(keyRow(
                KeySpec.action("Shift", KeyAction.SHIFT),
                KeySpec.character("z"), KeySpec.character("x"), KeySpec.character("c"),
                KeySpec.character("v"), KeySpec.character("b"), KeySpec.character("n"),
                KeySpec.character("m"), KeySpec.key("Backspace", KeyEvent.KEYCODE_DEL)));
        // Keep common sign-in characters and the rest of printable punctuation reachable without
        // a physical keyboard. Each character is sent immediately; no input text is retained.
        rows.add(characterRow("~", "`", "!", "@", "#", "$", "%", "^", "&", "*"));
        rows.add(characterRow("(", ")", "-", "_", "=", "+", "[", "]", "{", "}"));
        rows.add(characterRow("\\", "|", ";", ":", "'", "\"", ",", "<", ".", ">", "?", "/"));
        rows.add(keyRow(
                KeySpec.action("Caps", KeyAction.CAPS),
                KeySpec.key("Tab", KeyEvent.KEYCODE_TAB),
                KeySpec.key("Space", KeyEvent.KEYCODE_SPACE),
                KeySpec.key("Enter", KeyEvent.KEYCODE_ENTER),
                KeySpec.key("Esc", KeyEvent.KEYCODE_ESCAPE),
                KeySpec.action("Done", KeyAction.CLOSE)));
        rows.add(keyRow(
                KeySpec.key("←", KeyEvent.KEYCODE_DPAD_LEFT),
                KeySpec.key("↑", KeyEvent.KEYCODE_DPAD_UP),
                KeySpec.key("↓", KeyEvent.KEYCODE_DPAD_DOWN),
                KeySpec.key("→", KeyEvent.KEYCODE_DPAD_RIGHT),
                KeySpec.key("Home", KeyEvent.KEYCODE_MOVE_HOME),
                KeySpec.key("End", KeyEvent.KEYCODE_MOVE_END),
                KeySpec.key("Delete", KeyEvent.KEYCODE_FORWARD_DEL)));

        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            LinearLayout row = new LinearLayout(getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            root.addView(row, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));

            List<Button> buttons = new ArrayList<>();
            List<KeySpec> keys = rows.get(rowIndex);
            for (int columnIndex = 0; columnIndex < keys.size(); columnIndex++) {
                final int keyRow = rowIndex;
                final int keyColumn = columnIndex;
                Button button = new Button(getContext());
                button.setAllCaps(false);
                button.setTextSize(12);
                button.setGravity(Gravity.CENTER);
                button.setContentDescription(keys.get(columnIndex).label);
                button.setOnClickListener(view -> {
                    selectedRow = keyRow;
                    selectedColumn = keyColumn;
                    activateSelectedKey();
                });
                row.addView(button, new LinearLayout.LayoutParams(
                        0,
                        dp(42),
                        1f));
                buttons.add(button);
            }
            keyViews.add(buttons);
        }

        setContentView(contentView);
        refreshKeyboard();
    }

    private void moveSelection(int rowDelta, int columnDelta) {
        if (rows.isEmpty()) return;
        selectedRow = Math.floorMod(selectedRow + rowDelta, rows.size());
        int columns = rows.get(selectedRow).size();
        selectedColumn = Math.floorMod(Math.min(selectedColumn, columns - 1) + columnDelta, columns);
        refreshKeyboard();
    }

    private void activateSelectedKey() {
        if (selectedRow >= rows.size() || selectedColumn >= rows.get(selectedRow).size()) return;
        KeySpec key = rows.get(selectedRow).get(selectedColumn);
        switch (key.action) {
            case CHARACTER:
                char character = key.character;
                if (Character.isLetter(character) && (shift || capsLock)) {
                    character = Character.toUpperCase(character);
                }
                XrKeyEventSink.sendCharacter(activity, character);
                if (shift && !capsLock) shift = false;
                break;
            case KEYCODE:
                XrKeyEventSink.sendKey(activity, key.keyCode);
                break;
            case SHIFT:
                shift = !shift;
                break;
            case CAPS:
                capsLock = !capsLock;
                break;
            case CLOSE:
                dismiss();
                return;
        }
        refreshKeyboard();
    }

    private void refreshKeyboard() {
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            List<KeySpec> keys = rows.get(rowIndex);
            List<Button> buttons = keyViews.get(rowIndex);
            for (int columnIndex = 0; columnIndex < keys.size(); columnIndex++) {
                KeySpec key = keys.get(columnIndex);
                Button button = buttons.get(columnIndex);
                button.setText(displayLabel(key));
                boolean selected = rowIndex == selectedRow && columnIndex == selectedColumn;
                button.setSelected(selected);
                button.setBackgroundColor(selected ? Color.rgb(60, 120, 216) : Color.WHITE);
                button.setTextColor(selected ? Color.WHITE : Color.BLACK);
            }
        }
        redraw();
    }

    private String displayLabel(KeySpec key) {
        if (key.action != KeyAction.CHARACTER) return key.label;
        char character = key.character;
        return String.valueOf(Character.isLetter(character) && (shift || capsLock)
                ? Character.toUpperCase(character)
                : character);
    }

    private int dp(int value) {
        return (int) (value * getContext().getResources().getDisplayMetrics().density);
    }

    private static List<KeySpec> characterRow(String... characters) {
        List<KeySpec> keys = new ArrayList<>();
        for (String character : characters) keys.add(KeySpec.character(character));
        return keys;
    }

    private static List<KeySpec> keyRow(KeySpec... keys) {
        return new ArrayList<>(Arrays.asList(keys));
    }

    private enum KeyAction {
        CHARACTER,
        KEYCODE,
        SHIFT,
        CAPS,
        CLOSE,
    }

    private static final class KeySpec {
        final String label;
        final KeyAction action;
        final char character;
        final int keyCode;

        private KeySpec(String label, KeyAction action, char character, int keyCode) {
            this.label = label;
            this.action = action;
            this.character = character;
            this.keyCode = keyCode;
        }

        static KeySpec character(String value) {
            return new KeySpec(value, KeyAction.CHARACTER, value.charAt(0), KeyEvent.KEYCODE_UNKNOWN);
        }

        static KeySpec key(String label, int keyCode) {
            return new KeySpec(label, KeyAction.KEYCODE, '\0', keyCode);
        }

        static KeySpec action(String label, KeyAction action) {
            return new KeySpec(label, action, '\0', KeyEvent.KEYCODE_UNKNOWN);
        }
    }
}
