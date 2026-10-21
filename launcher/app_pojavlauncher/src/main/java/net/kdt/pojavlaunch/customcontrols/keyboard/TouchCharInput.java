package net.kdt.pojavlaunch.customcontrols.keyboard;


import static android.content.Context.INPUT_METHOD_SERVICE;

import static org.lwjgl.glfw.CallbackBridge.sendKeyPress;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.utils.KeyEncoder;
import net.kdt.pojavlaunch.utils.LwjglGlfwKeycode;

import org.lwjgl.glfw.CallbackBridge;

/**
 * This class is intended for sending characters used in chat via the virtual keyboard
 */
public class TouchCharInput extends androidx.appcompat.widget.AppCompatEditText {
    public static final String TEXT_FILLER = "                              ";
    public static boolean softKeyboardIsActive = false;
    public TouchCharInput(@NonNull Context context) {
        this(context, null);
    }
    public TouchCharInput(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, R.attr.editTextStyle);
    }
    public TouchCharInput(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setup();
    }


    private boolean mIsDoingInternalChanges = false;
    private CharacterSenderStrategy mCharacterSender;

    /**
     * We take the new chars, and send them to the game.
     * If less chars are present, remove some.
     * The text is always cleaned up.
     */
    @Override
    protected void onTextChanged(CharSequence text, int start, int lengthBefore, int lengthAfter) {
        // Backspace must be handled FIRST: the old guard returned early when the buffer was back
        // to the filler length, which is exactly where a backspace to the start lands - so
        // deleting the first characters silently did nothing.
        boolean isBackSpace = (lengthBefore > lengthAfter);
        if(isBackSpace) {
            KeyEncoder.sendUnicodeBackspace();
            return;
        }
        if(lengthBefore == lengthAfter || lengthAfter == TEXT_FILLER.length()) return;
        Log.i("TouchCharInput","New Event (before/after)!: "+ lengthBefore + " : " + lengthAfter);
        char c = text.charAt(text.length()-1);
        Log.i("TouchCharInput","New Event!: "+c);
        if(mCharacterSender != null) {
            KeyEncoder.sendEncodedChar(c,c);
        }
    }


    /**
     * The system keyboard talks to the game through this connection instead of through the
     * EditText's text. Diffing the text was fragile: autocorrect and word composition rewrote
     * it, and backspace was lost once the buffer was empty, so a typo could not be fixed.
     *
     * The connection keeps no text of its own, so the keyboard sees an empty field and sends
     * every backspace as a DEL key - both that and deleteSurroundingText are handled. Words a
     * keyboard insists on composing are diffed against what was already sent.
     */
    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        // a plain keyboard with no suggestions or autocorrect: right for usernames and passwords,
        // and keyboards then commit each key as it is pressed
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
        outAttrs.imeOptions = EditorInfo.IME_ACTION_DONE
                | EditorInfo.IME_FLAG_NO_EXTRACT_UI
                | EditorInfo.IME_FLAG_NO_FULLSCREEN
                | EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING;
        outAttrs.initialSelStart = 0;
        outAttrs.initialSelEnd = 0;
        return new GameInputConnection();
    }

    private class GameInputConnection extends BaseInputConnection {
        /** text the keyboard is still composing, already sent to the game */
        private String mComposing = "";

        GameInputConnection() {
            super(TouchCharInput.this, false);
        }

        /** Replace what the game has from the composing text with the new text. */
        private void replaceComposing(String text) {
            int common = 0;
            int max = Math.min(mComposing.length(), text.length());
            while (common < max && mComposing.charAt(common) == text.charAt(common)) common++;
            for (int i = mComposing.length(); i > common; i--) sendBackspace();
            for (int i = common; i < text.length(); i++) sendCharacter(text.charAt(i));
            mComposing = text;
        }

        @Override
        public boolean commitText(CharSequence text, int newCursorPosition) {
            replaceComposing(text == null ? "" : text.toString());
            mComposing = "";
            return true;
        }

        @Override
        public boolean setComposingText(CharSequence text, int newCursorPosition) {
            replaceComposing(text == null ? "" : text.toString());
            return true;
        }

        @Override
        public boolean finishComposingText() {
            mComposing = "";
            return true;
        }

        @Override
        public boolean deleteSurroundingText(int beforeLength, int afterLength) {
            for (int i = 0; i < beforeLength; i++) {
                if (mComposing.length() > 0) {
                    mComposing = mComposing.substring(0, mComposing.length() - 1);
                }
                sendBackspace();
            }
            return true;
        }

        @Override
        public boolean deleteSurroundingTextInCodePoints(int beforeLength, int afterLength) {
            return deleteSurroundingText(beforeLength, afterLength);
        }

        @Override
        public boolean sendKeyEvent(KeyEvent event) {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return true;
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_DEL:
                    mComposing = "";
                    sendBackspace();
                    return true;
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_NUMPAD_ENTER:
                    mComposing = "";
                    sendEnter();
                    return true;
                default:
                    int unicode = event.getUnicodeChar();
                    if (unicode > 0) sendCharacter((char) unicode);
                    return true;
            }
        }

        @Override
        public boolean performEditorAction(int editorAction) {
            mComposing = "";
            sendEnter();
            return true;
        }
    }

    /*
     * Keys go to the game as GLFW key presses, the path its key handler is written for. The older
     * AWT route mangled backspace (it arrived as a stray character), which is why a typo could not
     * be fixed. Shifted symbols use the US layout, the same table the client decodes with.
     */
    private static final String SHIFTED_SYMBOLS = "!@#$%^&*()_+{}:\"<>?|~";
    private static final String UNSHIFTED_KEYS  = "1234567890-=[];',./\\`";

    private static void pressGlfwKey(int key, int mods) {
        CallbackBridge.sendKeycode(key, '\u0000', 0, mods, true);
        CallbackBridge.sendKeycode(key, '\u0000', 0, mods, false);
    }

    private void sendBackspace() {
        pressGlfwKey(LwjglGlfwKeycode.GLFW_KEY_BACKSPACE, 0);
    }

    private void sendCharacter(char c) {
        if (c == '\n') {
            sendEnter();
        } else if (c >= 'a' && c <= 'z') {
            pressGlfwKey(LwjglGlfwKeycode.GLFW_KEY_A + (c - 'a'), 0);
        } else if (c >= 'A' && c <= 'Z') {
            pressGlfwKey(LwjglGlfwKeycode.GLFW_KEY_A + (c - 'A'), LwjglGlfwKeycode.GLFW_MOD_SHIFT);
        } else if (c >= '0' && c <= '9') {
            pressGlfwKey(LwjglGlfwKeycode.GLFW_KEY_0 + (c - '0'), 0);
        } else if (c == ' ') {
            pressGlfwKey(LwjglGlfwKeycode.GLFW_KEY_SPACE, 0);
        } else if (SHIFTED_SYMBOLS.indexOf(c) >= 0) {
            // GLFW key codes for printable keys are their unshifted ASCII
            pressGlfwKey(UNSHIFTED_KEYS.charAt(SHIFTED_SYMBOLS.indexOf(c)), LwjglGlfwKeycode.GLFW_MOD_SHIFT);
        } else if (UNSHIFTED_KEYS.indexOf(c) >= 0) {
            pressGlfwKey(c, 0);
        } else {
            // anything outside US ASCII: fall back to the old route
            KeyEncoder.sendEncodedChar(c, c);
        }
    }

    /**
     * When we change from app to app, the keyboard gets disabled.
     * So, we disable the object
     */
    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        disable();
    }

    /**
     * Intercepts the back key to disable focus
     * Does not affect the rest of the activity.
     */
    @Override
    public boolean onKeyPreIme(final int keyCode, final KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
            disable();
        }
        return super.onKeyPreIme(keyCode, event);
    }


    /**
     * Toggle on and off the soft keyboard, depending of the state
     */
    public void switchKeyboardState(){
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(INPUT_METHOD_SERVICE);
        // Allow, regardless of whether or not a hardware keyboard is declared
        if(hasFocus()){
            clear();
            disable();
        }else{
            enable();
            imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT);
        }
    }


    /**
     * Clear the EditText from any leftover inputs
     * It does not affect the in-game input
     */
    @SuppressLint("SetTextI18n")
    public void clear(){
        mIsDoingInternalChanges = true;
        //Braille space, doesn't trigger keyboard auto-complete
        //replacing directly the text without though setText avoids notifying changes
        setText(TEXT_FILLER);
        setSelection(TEXT_FILLER.length());
        mIsDoingInternalChanges = false;
    }

    /** Regain ability to exist, take focus and have some text being input */
    public void enable(){
        softKeyboardIsActive = true;
        setEnabled(true);
        setFocusable(true);
        setVisibility(VISIBLE);
        requestFocus();
    }

    /** Lose ability to exist, take focus and have some text being input */
    public void disable(){
        softKeyboardIsActive = false;
        clear();
        setVisibility(GONE);
        clearFocus();
        setEnabled(false);
        //setFocusable(false);
    }

    /** Send the enter key. */
    private void sendEnter(){
        pressGlfwKey(LwjglGlfwKeycode.GLFW_KEY_ENTER, 0);
        clear();
    }

    /** Just sets the char sender that should be used. */
    public void setCharacterSender(CharacterSenderStrategy characterSender){
        mCharacterSender = characterSender;
    }

    /** This function deals with anything that has to be executed when the constructor is called */
    private void setup(){
        clear();
        disable();
    }

}
