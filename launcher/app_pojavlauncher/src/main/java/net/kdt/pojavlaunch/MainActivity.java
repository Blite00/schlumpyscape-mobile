package net.kdt.pojavlaunch;

import static net.kdt.pojavlaunch.Tools.currentDisplayMetrics;
import static net.kdt.pojavlaunch.prefs.LauncherPreferences.PREF_INSET_X;
import static net.kdt.pojavlaunch.prefs.LauncherPreferences.PREF_SUSTAINED_PERFORMANCE;
import static net.kdt.pojavlaunch.prefs.LauncherPreferences.PREF_USE_ALTERNATE_SURFACE;
import static net.kdt.pojavlaunch.prefs.LauncherPreferences.PREF_VIRTUAL_MOUSE_START;
import static org.lwjgl.glfw.CallbackBridge.sendKeyPress;
import static org.lwjgl.glfw.CallbackBridge.windowHeight;
import static org.lwjgl.glfw.CallbackBridge.windowWidth;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.os.SystemClock;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.drawerlayout.widget.DrawerLayout;

import com.kdt.LoggerView;

import android.animation.ValueAnimator;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import net.kdt.pojavlaunch.customcontrols.GyroControl;
import net.kdt.pojavlaunch.customcontrols.ControlButtonMenuListener;
import net.kdt.pojavlaunch.customcontrols.ControlData;
import net.kdt.pojavlaunch.customcontrols.ControlDrawerData;
import net.kdt.pojavlaunch.customcontrols.ControlLayout;
import net.kdt.pojavlaunch.customcontrols.CustomControls;
import net.kdt.pojavlaunch.customcontrols.EditorExitable;
import net.kdt.pojavlaunch.customcontrols.keyboard.LwjglCharSender;
import net.kdt.pojavlaunch.customcontrols.keyboard.TouchCharInput;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.utils.EfficientAndroidLWJGLKeycode;
import net.kdt.pojavlaunch.utils.LwjglGlfwKeycode;
import net.kdt.pojavlaunch.utils.MCOptionUtils;

import org.lwjgl.glfw.CallbackBridge;

import java.io.File;
import java.io.IOException;

public class MainActivity extends BaseActivity implements ControlButtonMenuListener, EditorExitable {
    public static volatile ClipboardManager GLOBAL_CLIPBOARD;
    public static final String INTENT_MINECRAFT_VERSION = "intent_version";

    volatile public static boolean isInputStackCall;

    public static TouchCharInput touchCharInput;
    private GLFWGLSurface minecraftGLView;
    private static Touchpad touchpad;
    private LoggerView loggerView;
    private DrawerLayout drawerLayout;
    private ListView navDrawer;
    private View mDrawerPullButton;
    private View contentFrame;
    private GyroControl mGyroControl = null;
    public static ControlLayout mControlLayout;

    private ArrayAdapter<String> gameActionArrayAdapter;
    private AdapterView.OnItemClickListener gameActionClickListener;
    public ArrayAdapter<String> ingameControlsEditorArrayAdapter;
    public AdapterView.OnItemClickListener ingameControlsEditorListener;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initLayout(R.layout.activity_basemain);
        CallbackBridge.addGrabListener(touchpad);
        CallbackBridge.addGrabListener(minecraftGLView);
        if(LauncherPreferences.PREF_ENABLE_GYRO) mGyroControl = new GyroControl(this);

        // Enabling this on TextureView results in a broken white result
        if(PREF_USE_ALTERNATE_SURFACE) getWindow().setBackgroundDrawable(null);
        else getWindow().setBackgroundDrawable(new ColorDrawable(Color.BLACK));

        // Set the sustained performance mode for available APIs
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            getWindow().setSustainedPerformanceMode(PREF_SUSTAINED_PERFORMANCE);

        ingameControlsEditorArrayAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, getResources().getStringArray(R.array.menu_customcontrol));
        ingameControlsEditorListener = (parent, view, position, id) -> {
            switch(position) {
                case 0: mControlLayout.addControlButton(new ControlData("New")); break;
                case 1: mControlLayout.addDrawer(new ControlDrawerData()); break;
                //case 2: mControlLayout.addJoystickButton(new ControlData()); break;
                case 2 : mControlLayout.openLoadDialog(); break;
                case 3: mControlLayout.openSaveDialog(this); break;
                case 4: mControlLayout.openSetDefaultDialog(); break;
                case 5: mControlLayout.openExitDialog(this);
            }
        };

        // Recompute the gui scale when options are changed
        MCOptionUtils.MCOptionListener optionListener = MCOptionUtils::getMcScale;
        MCOptionUtils.addMCOptionListener(optionListener);
        mControlLayout.setModifiable(false);
    }

    protected void initLayout(int resId) {
        setContentView(resId);
        bindValues();
        mControlLayout.setMenuListener(this);

        mDrawerPullButton.setOnClickListener(v -> onClickedMenu());
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);

        try {
            File latestLogFile = new File(Tools.DIR_GAME_HOME, "latestlog.txt");
            if(!latestLogFile.exists() && !latestLogFile.createNewFile())
                throw new IOException("Failed to create a new log file");
            Logger.begin(latestLogFile.getAbsolutePath());
            // FIXME: is it safe for multi thread?
            GLOBAL_CLIPBOARD = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            touchCharInput.setCharacterSender(new LwjglCharSender());
            CallbackBridge.nativeSetUseInputStackQueue(false);
            Tools.getDisplayMetrics(this);
            windowWidth = Tools.getDisplayFriendlyRes(currentDisplayMetrics.widthPixels, 1f);
            windowHeight = Tools.getDisplayFriendlyRes(currentDisplayMetrics.heightPixels, 1f);


            // Menu
            gameActionArrayAdapter = new ArrayAdapter<>(this,
                    android.R.layout.simple_list_item_1, getResources().getStringArray(R.array.menu_ingame));
            gameActionClickListener = (parent, view, position, id) -> {
                switch(position) {
                    case 0: dialogForceClose(MainActivity.this); break;
                    case 1: openLogOutput(); break;
                    case 2: dialogSendCustomKey(); break;
                    case 3: adjustMouseSpeedLive(); break;
                    case 5: openCustomControls(); break;
                }
                drawerLayout.closeDrawers();
            };
            navDrawer.setAdapter(gameActionArrayAdapter);
            navDrawer.setOnItemClickListener(gameActionClickListener);
            drawerLayout.closeDrawers();
            minecraftGLView.setSurfaceReadyListener(() -> {
                try {
                    // Setup virtual mouse right before launching
                    if (PREF_VIRTUAL_MOUSE_START) {
                        touchpad.post(() -> touchpad.switchState());
                    }
                    runCraft();
                }catch (Throwable e){
                    Tools.showError(getApplicationContext(), e, true);
                }
            });

            minecraftGLView.start();
        } catch (Throwable e) {
            Tools.showError(this, e, true);
        }
    }

    private void loadControls() {
        try {
            // Load keys
            mControlLayout.loadLayout(LauncherPreferences.PREF_DEFAULTCTRL_PATH);
        } catch(IOException e) {
            try {
                Log.w("MainActivity", "Unable to load the control file, loading the default now", e);
                mControlLayout.loadLayout(Tools.CTRLDEF_FILE);
            } catch (IOException ioException) {
                Tools.showError(this, ioException);
            }
        } catch (Throwable th) {
            Tools.showError(this, th);
        }
        mDrawerPullButton.setVisibility(mControlLayout.hasMenuButton() ? View.GONE : View.VISIBLE);
        mControlLayout.toggleControlVisible();
    }

    @Override
    public void onAttachedToWindow() {
        LauncherPreferences.computeNotchSize(this);
        loadControls();
    }

    /** Boilerplate binding */
    private void bindValues(){
        mControlLayout = findViewById(R.id.main_control_layout);
        watchKeyboardHeight();
        touchCharInput = findViewById(R.id.mainTouchCharInput);
        touchCharInput.setOnFocusChangeListener((v, hasFocus) -> {
            // keyboard closed with Back (or the game dropped it): put the game view back
            if (!hasFocus) {
                mFieldAnchorY = -1;
                mKeyboardHeight = readKeyboardHeight();
                updateKeyboardPan();
            }
        });
        minecraftGLView = findViewById(R.id.main_game_render_view);
        touchpad = findViewById(R.id.main_touchpad);
        drawerLayout = findViewById(R.id.main_drawer_options);
        navDrawer = findViewById(R.id.main_navigation_view);
        loggerView = findViewById(R.id.mainLoggerView);
        // hidden on start: the game shows its own loading screen until the login appears.
        // a real failure still surfaces via the error path below.
        loggerView.setVisibility(View.GONE);
        buildLoadingOverlay();
        // the game tells us when it starts/stops accepting typed text: raise the soft keyboard then
        org.lwjgl.glfw.CallbackBridge.setTextInputFocusListener(focused -> {
            if (touchCharInput == null) return;
            android.util.Log.i("SchlumpyKeyboard", "game asked for keyboard " + (focused ? "open" : "closed"));
            android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (focused) {
                // remember where the tapped field is, so it can be lifted above the keyboard
                float scale = minecraftGLView != null ? minecraftGLView.getRenderScale() : 1f;
                // until the client reports the field's real edge, assume it extends a little below the tap
                mFieldAnchorY = CallbackBridge.mouseY / scale + dp(24);
                touchCharInput.clear();
                touchCharInput.enable();
                updateKeyboardPan();
                scheduleKeyboardChecks();
                // the input view was GONE until enable(); the IME only accepts it once it is laid out
                // and served, so ask after a layout pass and once more as a fallback. Asking again
                // while the keyboard is already up is harmless.
                touchCharInput.post(() -> imm.showSoftInput(touchCharInput, 0));
                touchCharInput.postDelayed(() -> {
                    if (touchCharInput.hasFocus()) {
                        imm.showSoftInput(touchCharInput, 0);
                    }
                }, 250);
            } else if (touchCharInput.hasFocus() || mFieldAnchorY >= 0) {
                mFieldAnchorY = -1;
                updateKeyboardPan();
                touchCharInput.disable();
                imm.hideSoftInputFromWindow(touchCharInput.getWindowToken(), 0);
            }
        });
        mControlLayout = findViewById(R.id.main_control_layout);
        touchCharInput = findViewById(R.id.mainTouchCharInput);
        mDrawerPullButton = findViewById(R.id.drawer_button);
        contentFrame = findViewById(R.id.content_frame);
        int inset = (int) PREF_INSET_X;
        //touchpad.setPadding(inset,inset,inset,inset);
        contentFrame.setPadding(inset,inset,inset,inset);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private View mLoadingOverlay;
    private View mLoadingBarFill;
    private FrameLayout mLoadingBarBox;
    private TextView mLoadingStatus;
    private ValueAnimator mLoadAnimator;
    /** Client loading percent from SCHLUMPY_LOAD, -1 until the client first reports */
    private volatile int mClientLoadPct = -1;
    private long mClientLoadAtMs;
    private long mLoadStartMs;
    /** Fraction of the bar currently drawn (0-1) */
    private float mLoadShown = 0f;

    /**
     * Full-screen loading screen shown while the JVM boots and the cache loads, so the user never
     * sees the raw black surface or the flicker as the login UI assembles. It goes away only when
     * the client prints its UI-ready marker (or after a hard timeout, so it can never stick).
     */
    private void buildLoadingOverlay() {
        ViewGroup root = findViewById(android.R.id.content);
        FrameLayout overlay = new FrameLayout(this);
        overlay.setBackgroundColor(Color.BLACK);
        overlay.setClickable(true);

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.loading_logo);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        FrameLayout.LayoutParams logoParams =
                new FrameLayout.LayoutParams(dp(190), dp(190), Gravity.CENTER);
        logoParams.bottomMargin = dp(80);
        overlay.addView(logo, logoParams);

        FrameLayout barBox = new FrameLayout(this);
        GradientDrawable barBg = new GradientDrawable();
        barBg.setColor(0xFF101010);
        barBg.setStroke(dp(2), 0xFF555555);
        barBox.setBackground(barBg);
        FrameLayout.LayoutParams boxParams =
                new FrameLayout.LayoutParams(dp(300), dp(20), Gravity.CENTER);
        boxParams.topMargin = dp(70);
        overlay.addView(barBox, boxParams);

        View fill = new View(this);
        GradientDrawable fillBg = new GradientDrawable();
        fillBg.setColor(0xFFCC2222);
        fill.setBackground(fillBg);
        barBox.addView(fill, new FrameLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT));

        mLoadingOverlay = overlay;
        mLoadingBarFill = fill;
        mLoadingBarBox = barBox;
        root.addView(overlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView status = new TextView(this);
        status.setTextColor(0xFFBBBBBB);
        status.setTextSize(13);
        status.setGravity(Gravity.CENTER);
        status.setText("Starting the game\u2026");
        FrameLayout.LayoutParams statusParams = new FrameLayout.LayoutParams(
                dp(320), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        statusParams.topMargin = dp(112);
        overlay.addView(status, statusParams);
        mLoadingStatus = status;

        // The bar follows real progress: Java start-up eases towards 15%, then the client's own
        // loading percent (SCHLUMPY_LOAD lines) maps onto 15-95%, and the UI-ready marker fills
        // it. Between updates it creeps a little towards the next step so it never looks stuck,
        // but never past it.
        mLoadStartMs = SystemClock.uptimeMillis();
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(240000L);
        animator.addUpdateListener(animation -> {
            if (mLoadingBarFill == null || mLoadingBarBox == null) return;
            float target;
            if (mClientLoadPct < 0) {
                // no word from the client yet: Java is booting (about 8s on a mid-range phone)
                float t = (SystemClock.uptimeMillis() - mLoadStartMs) / 1000f;
                target = 0.15f * (1f - (float) Math.exp(-t / 5f));
            } else {
                float base = 0.15f + 0.80f * mClientLoadPct / 100f;
                float next = 0.15f + 0.80f * Math.min(100, mClientLoadPct + 5) / 100f;
                float since = (SystemClock.uptimeMillis() - mClientLoadAtMs) / 1000f;
                target = base + (next - base) * 0.8f * (1f - (float) Math.exp(-since / 4f));
            }
            // ease the drawn bar towards the target instead of jumping
            mLoadShown += (Math.max(target, mLoadShown) - mLoadShown) * 0.15f;
            // the box has no measured width on the first frames, and a zero here leaves the bar
            // permanently empty - fall back to its nominal width
            int full = mLoadingBarBox.getWidth();
            if (full <= 0) {
                full = dp(300);
            }
            int width = Math.round(full * Math.min(1f, mLoadShown));
            ViewGroup.LayoutParams lp = mLoadingBarFill.getLayoutParams();
            if (lp.width != width) {
                lp.width = width;
                mLoadingBarFill.setLayoutParams(lp);
            }
        });
        animator.start();
        mLoadAnimator = animator;

        Logger.setLogListener(text -> {
            if (text == null) return;
            int load = text.indexOf("SCHLUMPY_LOAD ");
            if (load >= 0) {
                try {
                    String rest = text.substring(load + 14).trim();
                    int sp = rest.indexOf(' ');
                    final int pct = Integer.parseInt(sp < 0 ? rest : rest.substring(0, sp));
                    final String label = sp < 0 ? "" : rest.substring(sp + 1).split("\n")[0].trim();
                    runOnUiThread(() -> {
                        if (pct > mClientLoadPct) {
                            mClientLoadPct = Math.min(100, pct);
                            mClientLoadAtMs = SystemClock.uptimeMillis();
                        }
                        if (mLoadingStatus != null && !label.isEmpty()) {
                            mLoadingStatus.setText(label);
                        }
                    });
                } catch (RuntimeException ignored) {
                    // a split log line; the next update corrects it
                }
            }
            if (text.contains("SCHLUMPY_UI_READY")) {
                runOnUiThread(this::hideLoadingOverlay);
            }
            // the client announces when the login screen comes and goes; the login scroll is drawn
            // at a fixed pixel size, so render it at a lower resolution to make it phone-sized
            if (text.contains("SCHLUMPY_SCALE_LOGIN")) {
                runOnUiThread(() -> applyRenderScale(true));
            } else if (text.contains("SCHLUMPY_SCALE_GAME")) {
                runOnUiThread(() -> applyRenderScale(false));
            }
            int at = text.indexOf("SCHLUMPY_FIELD_BOTTOM ");
            if (at >= 0) {
                try {
                    String num = text.substring(at + 22).trim().split("\\s+")[0];
                    final int bottom = Integer.parseInt(num);
                    runOnUiThread(() -> {
                        if (mFieldAnchorY < 0 || minecraftGLView == null) return;
                        mFieldAnchorY = bottom / minecraftGLView.getRenderScale();
                        updateKeyboardPan();
                    });
                } catch (RuntimeException ignored) {
                    // a split log line: the next frame announces again only on change, so skip
                }
            }
        });
        // safety net: never let the loading screen become a wall
        root.postDelayed(this::hideLoadingOverlay, 240000L);
    }

    /** Y of the tapped text field in game-view pixels while the keyboard is wanted, else -1 */
    private float mFieldAnchorY = -1;
    /** Current soft keyboard height in pixels, 0 when hidden */
    private int mKeyboardHeight = 0;

    /**
     * Tracks the soft keyboard's height. The game view is full screen and is not resized for the
     * keyboard, so without this the keyboard would sit on top of the field being typed into.
     * Fullscreen windows get no layout pass when the keyboard opens, so the height is read from the
     * window insets when they change and re-checked a few times after each keyboard request.
     */
    private void watchKeyboardHeight() {
        final View root = getWindow().getDecorView();
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            v.post(this::refreshKeyboardHeight);
            return v.onApplyWindowInsets(insets);
        });
        root.getViewTreeObserver().addOnGlobalLayoutListener(this::refreshKeyboardHeight);
    }

    private int readKeyboardHeight() {
        final View root = getWindow().getDecorView();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            android.view.WindowInsets insets = root.getRootWindowInsets();
            if (insets != null) {
                return insets.getInsets(android.view.WindowInsets.Type.ime()).bottom;
            }
            return 0;
        }
        android.graphics.Rect visible = new android.graphics.Rect();
        root.getWindowVisibleDisplayFrame(visible);
        int covered = root.getHeight() - visible.bottom;
        // small values are nav/status bars, not a keyboard
        return covered > root.getHeight() / 5 ? covered : 0;
    }

    private void refreshKeyboardHeight() {
        int height = readKeyboardHeight();
        if (height != mKeyboardHeight) {
            mKeyboardHeight = height;
            updateKeyboardPan();
        }
    }

    /** Re-reads the keyboard height while it animates in, then falls back to an estimate. */
    private void scheduleKeyboardChecks() {
        final View root = getWindow().getDecorView();
        root.postDelayed(this::refreshKeyboardHeight, 200);
        root.postDelayed(this::refreshKeyboardHeight, 500);
        root.postDelayed(() -> {
            refreshKeyboardHeight();
            if (mKeyboardHeight == 0 && touchCharInput != null && touchCharInput.hasFocus()) {
                // the platform would not report the keyboard: assume a typical landscape keyboard
                mKeyboardHeight = Math.round(root.getHeight() * 0.55f);
                updateKeyboardPan();
            }
        }, 900);
    }

    /**
     * Slides the game up just enough to keep the tapped field above the keyboard. The picture is a
     * TextureView that sits beside the touch view in the control layout, so the whole layout moves
     * and touches stay lined up with what is drawn.
     */
    private void updateKeyboardPan() {
        if (mControlLayout == null) return;
        float shift = 0f;
        if (mFieldAnchorY >= 0 && mKeyboardHeight > 0) {
            int[] loc = new int[2];
            mControlLayout.getLocationOnScreen(loc);
            float fieldBottom = loc[1] - mControlLayout.getTranslationY() + mFieldAnchorY + dp(16);
            float keyboardTop = getWindow().getDecorView().getHeight() - mKeyboardHeight;
            shift = Math.max(0f, fieldBottom - keyboardTop);
        }
        mControlLayout.animate().translationY(-shift).setDuration(150L).start();
    }

    /**
     * Login canvas height in render pixels. The login scroll and its logo need about 430 px, so this
     * makes the scroll fill the screen height on any phone while everything still fits.
     */
    private static final int LOGIN_RENDER_HEIGHT = 440;
    /** in-game render height: the resizable layout needs 503 rows (the fixed 765x503 frame) */
    private static final int GAME_RENDER_HEIGHT = 480;

    private void applyRenderScale(boolean loginScreen) {
        if (minecraftGLView == null) return;
        float preferred = minecraftGLView.getPreferredScale();
        float scale = preferred;
        if (loginScreen) {
            int screenHeight = Tools.currentDisplayMetrics.heightPixels;
            if (screenHeight > 0) {
                // never render the login above the user's own setting - only ever make it bigger
                scale = Math.min(preferred, LOGIN_RENDER_HEIGHT / (float) screenHeight);
            }
        } else {
            int screenHeight = Tools.currentDisplayMetrics.heightPixels;
            if (screenHeight > 0) {
                // in game too: OSRS mobile draws its interface finger-sized, so render at the
                // smallest height the resizable layout supports and let the surface upscale it
                scale = Math.min(preferred, GAME_RENDER_HEIGHT / (float) screenHeight);
            }
        }
        minecraftGLView.setRenderScale(scale);
    }

    private void hideLoadingOverlay() {
        if (mLoadingOverlay == null) return;
        // finish the bar before fading: the UI can be ready while the animation is still early,
        // and a bar that vanishes at 15% reads as broken.
        if (mLoadAnimator != null) {
            mLoadAnimator.cancel();
            mLoadAnimator = null;
        }
        if (mLoadingStatus != null) {
            mLoadingStatus.setText("Ready");
        }
        if (mLoadingBarFill != null && mLoadingBarBox != null) {
            ViewGroup.LayoutParams lp = mLoadingBarFill.getLayoutParams();
            lp.width = mLoadingBarBox.getWidth();
            mLoadingBarFill.setLayoutParams(lp);
        }
        mLoadingOverlay.postDelayed(() -> {
            if (mLoadingOverlay == null) return;
            mLoadingOverlay.animate().alpha(0f).setDuration(300L)
                    .withEndAction(() -> {
                        if (mLoadingOverlay != null) {
                            mLoadingOverlay.setVisibility(View.GONE);
                        }
                    }).start();
        }, 400L);
    }

    @Override
    public void onResume() {
        super.onResume();
        if(mGyroControl != null) mGyroControl.enable();
        CallbackBridge.nativeSetWindowAttrib(LwjglGlfwKeycode.GLFW_HOVERED, 1);
    }

    @Override
    protected void onPause() {
        if(mGyroControl != null) mGyroControl.disable();
        if (CallbackBridge.isGrabbing()){
            sendKeyPress(LwjglGlfwKeycode.GLFW_KEY_ESCAPE);
        }
        CallbackBridge.nativeSetWindowAttrib(LwjglGlfwKeycode.GLFW_HOVERED, 0);
        super.onPause();
    }

    @Override
    protected void onStart() {
        super.onStart();
        CallbackBridge.nativeSetWindowAttrib(LwjglGlfwKeycode.GLFW_VISIBLE, 1);
    }

    @Override
    protected void onStop() {
        CallbackBridge.nativeSetWindowAttrib(LwjglGlfwKeycode.GLFW_VISIBLE, 0);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        CallbackBridge.removeGrabListener(touchpad);
        CallbackBridge.removeGrabListener(minecraftGLView);
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        if(mGyroControl != null) mGyroControl.updateOrientation();
        Tools.updateWindowSize(this);
        minecraftGLView.refreshSize();
        runOnUiThread(() -> mControlLayout.refreshControlButtonPositions());
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();
        if(minecraftGLView != null)  // Useful when backing out of the app
            Tools.MAIN_HANDLER.postDelayed(() -> minecraftGLView.refreshSize(), 500);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 1 && resultCode == Activity.RESULT_OK) {
            // Reload PREF_DEFAULTCTRL_PATH
            LauncherPreferences.loadPreferences(getApplicationContext());
            try {
                mControlLayout.loadLayout(LauncherPreferences.PREF_DEFAULTCTRL_PATH);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void fullyExit() {
        android.os.Process.killProcess(android.os.Process.myPid());
    }

    public static boolean isAndroid8OrHigher() {
        return Build.VERSION.SDK_INT >= 26;
    }

    private void runCraft() throws Throwable {
        if(Tools.LOCAL_RENDERER == null) {
            Tools.LOCAL_RENDERER = LauncherPreferences.PREF_RENDERER;
        }
        Tools.launchGLJRE(this);
    }

    private void dialogSendCustomKey() {
        AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        dialog.setTitle(R.string.control_customkey);
        dialog.setItems(EfficientAndroidLWJGLKeycode.generateKeyName(), (dInterface, position) -> EfficientAndroidLWJGLKeycode.execKeyIndex(position));
        dialog.show();
    }

    boolean isInEditor;
    private void openCustomControls() {
        if(ingameControlsEditorListener == null || ingameControlsEditorArrayAdapter == null) return;

        mControlLayout.setModifiable(true);
        navDrawer.setAdapter(ingameControlsEditorArrayAdapter);
        navDrawer.setOnItemClickListener(ingameControlsEditorListener);
        mDrawerPullButton.setVisibility(View.VISIBLE);
        isInEditor = true;
    }

    private void openLogOutput() {
        loggerView.setVisibility(View.VISIBLE);
    }

    public static void toggleMouse(Context ctx) {
        if (CallbackBridge.isGrabbing()) return;

        Toast.makeText(ctx, touchpad.switchState()
                        ? R.string.control_mouseon : R.string.control_mouseoff,
                Toast.LENGTH_SHORT).show();
    }

    public static void dialogForceClose(Context ctx) {
        new AlertDialog.Builder(ctx)
                .setMessage(R.string.mcn_exit_confirm)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (p1, p2) -> {
                    try {
                        fullyExit();
                    } catch (Throwable th) {
                        Log.w(Tools.APP_NAME, "Could not enable System.exit() method!", th);
                    }
                }).show();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if(isInEditor) {
            if(event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                if(event.getAction() == KeyEvent.ACTION_DOWN) mControlLayout.askToExit(this);
                return true;
            }
            return super.dispatchKeyEvent(event);
        }
        boolean handleEvent;
        if(!(handleEvent = minecraftGLView.processKeyEvent(event))) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && !touchCharInput.isEnabled()) {
                if(event.getAction() != KeyEvent.ACTION_UP) return true; // We eat it anyway
                sendKeyPress(LwjglGlfwKeycode.GLFW_KEY_ESCAPE);
                return true;
            }
        }
        return handleEvent;
    }

    public static void switchKeyboardState() {
        if(touchCharInput != null) touchCharInput.switchKeyboardState();
    }

    int tmpMouseSpeed;
    public void adjustMouseSpeedLive() {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle(R.string.mcl_setting_title_mousespeed);
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_live_mouse_speed_editor,null);
        final SeekBar sb = v.findViewById(R.id.mouseSpeed);
        final TextView tv = v.findViewById(R.id.mouseSpeedTV);
        sb.setMax(275);
        tmpMouseSpeed = (int) ((LauncherPreferences.PREF_MOUSESPEED*100));
        sb.setProgress(tmpMouseSpeed-25);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                tmpMouseSpeed = i+25;
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        b.setView(v);
        b.setPositiveButton(android.R.string.ok, (dialogInterface, i) -> {
            LauncherPreferences.PREF_MOUSESPEED = ((float)tmpMouseSpeed)/100f;
            LauncherPreferences.DEFAULT_PREF.edit().putInt("mousespeed",tmpMouseSpeed).apply();
            dialogInterface.dismiss();
            System.gc();
        });
        b.setNegativeButton(android.R.string.cancel, (dialogInterface, i) -> {
            dialogInterface.dismiss();
            System.gc();
        });
        b.show();
    }
    private static void setUri(Context context, String input, Intent intent) {
        if(input.startsWith("file:")) {
            int truncLength = 5;
            if(input.startsWith("file://")) truncLength = 7;
            input = input.substring(truncLength);
            Log.i("MainActivity", input);
            boolean isDirectory = new File(input).isDirectory();
            if(isDirectory) {
                intent.setType(DocumentsContract.Document.MIME_TYPE_DIR);
            }else{
                String type = null;
                String extension = MimeTypeMap.getFileExtensionFromUrl(input);
                if(extension != null) type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
                if(type == null) type = "*/*";
                intent.setType(type);
            }
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            intent.setData(DocumentsContract.buildDocumentUri(
                    context.getString(R.string.storageProviderAuthorities), input
            ));
            return;
        }
        intent.setDataAndType(Uri.parse(input), "*/*");
    }

    public static void openLink(String link) {
        Context ctx = touchpad.getContext(); // no more better way to obtain a context statically
        ((Activity)ctx).runOnUiThread(() -> {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                setUri(ctx, link, intent);
                ctx.startActivity(intent);
            } catch (Throwable th) {
                Tools.showError(ctx, th);
            }
        });
    }
    @SuppressWarnings("unused") //TODO: actually use it
    public static void openPath(String path) {
        Context ctx = touchpad.getContext(); // no more better way to obtain a context statically
        ((Activity)ctx).runOnUiThread(() -> {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(DocumentsContract.buildDocumentUri(ctx.getString(R.string.storageProviderAuthorities), path), "*/*");
                ctx.startActivity(intent);
            } catch (Throwable th) {
                Tools.showError(ctx, th);
            }
        });
    }

    public static void querySystemClipboard() {
        Tools.runOnUiThread(()->{
            ClipData clipData = GLOBAL_CLIPBOARD.getPrimaryClip();
            if(clipData == null) {
                AWTInputBridge.nativeClipboardReceived(null, null);
                return;
            }
            ClipData.Item firstClipItem = clipData.getItemAt(0);
            //TODO: coerce to HTML if the clip item is styled
            CharSequence clipItemText = firstClipItem.getText();
            if(clipItemText == null) {
                AWTInputBridge.nativeClipboardReceived(null, null);
                return;
            }
            AWTInputBridge.nativeClipboardReceived(clipItemText.toString(), "plain");
        });
    }

    public static void putClipboardData(String data, String mimeType) {
        Tools.runOnUiThread(()-> {
            ClipData clipData = null;
            switch(mimeType) {
                case "text/plain":
                    clipData = ClipData.newPlainText("AWT Paste", data);
                    break;
                case "text/html":
                    clipData = ClipData.newHtmlText("AWT Paste", data, data);
            }
            if(clipData != null) GLOBAL_CLIPBOARD.setPrimaryClip(clipData);
        });
    }

    @Override
    public void onClickedMenu() {
        drawerLayout.openDrawer(navDrawer);
        navDrawer.requestLayout();
    }

    @Override
    public void exitEditor() {
        try {
            MainActivity.mControlLayout.loadLayout((CustomControls)null);
            MainActivity.mControlLayout.setModifiable(false);
            System.gc();
            MainActivity.mControlLayout.loadLayout(LauncherPreferences.PREF_DEFAULTCTRL_PATH);
            mDrawerPullButton.setVisibility(mControlLayout.hasMenuButton() ? View.GONE : View.VISIBLE);
        } catch (IOException e) {
            Tools.showError(this,e);
        }
        navDrawer.setAdapter(gameActionArrayAdapter);
        navDrawer.setOnItemClickListener(gameActionClickListener);
        isInEditor = false;
    }
}
