package rt4;

import java.lang.reflect.Method;
import java.util.IdentityHashMap;
import java.util.Map;

import org.openrs2.deob.annotation.Pc;

/**
 * Mobile-only glue between the login screen and the Android launcher. This class lives only in the
 * mobile client tree; the desktop client is untouched.
 *
 * - Login scale: the login scroll is drawn at a fixed pixel size, which is tiny on a phone. While
 *   the login screen is up we ask the launcher to render at a lower resolution (it upscales the
 *   surface and maps touches back), and return to the normal resolution once in game. The request
 *   is a marker line on stdout, which the launcher already watches.
 * - Soft keyboard: phone behaviour - hidden until the player taps the username or password field,
 *   gone when they tap elsewhere or leave the login screen. The launcher owns the keyboard; we call
 *   it through the JNI bridge. Reflection keeps every failure silent on builds without the bridge.
 */
public class MobileInput {

	private static Method setFocus = null;
	private static boolean resolved = false;
	private static boolean focused = false;
	private static int lastState = -1;
	private static long lastClick = 0L;

	/** 0 = nothing announced yet, 1 = login scale requested, 2 = game scale requested */
	private static int announcedScale = 0;

	/** where each login-screen component was last drawn, in canvas pixels: {x, y, time} */
	private static final Map<Component, long[]> drawn = new IdentityHashMap<Component, long[]>();

	private static void resolve() {
		if (resolved) {
			return;
		}
		resolved = true;
		try {
			Class<?> bridge = Class.forName("org.lwjgl.glfw.CallbackBridge");
			setFocus = bridge.getMethod("nativeSetTextInputFocus", boolean.class);
		} catch (Throwable t) {
			setFocus = null;
		}
	}

	/** Raise or drop the launcher's soft keyboard. */
	public static void requestFocus(boolean want) {
		if (want == focused) {
			return;
		}
		resolve();
		focused = want;
		if (setFocus == null) {
			return;
		}
		try {
			setFocus.invoke(null, want);
		} catch (Throwable t) {
			// launcher without the bridge: ignore
		}
	}

	/** The chatbox in this revision: 752 is the frame with the input line, 137 the message history. */
	private static boolean isChatbox(int interfaceId) {
		return interfaceId == 752 || interfaceId == 137;
	}

	/** Called by the interface renderer with each component's absolute on-screen position. */
	public static void recordDrawn(Component c, int x, int y) {
		if (c == null || client.gameState != 10 && client.gameState != 30) {
			return;
		}
		if (client.gameState == 30 && !isChatbox(c.id >>> 16) && !isScrollable(c)) {
			return; // in game only the chatbox and scrollable lists matter
		}
		if (drawn.size() > 600) {
			pruneDrawn();
		}
		@Pc(0) long[] at = drawn.get(c);
		if (at == null) {
			at = new long[5];
			drawn.put(c, at);
		}
		at[0] = MobileLayout.zoomX(x);   // where it shows on screen, for components in the zoomed panel
		at[1] = MobileLayout.zoomY(y);
		at[3] = MobileLayout.zoomX(x + c.width) - at[0];   // and its on-screen size
		at[4] = MobileLayout.zoomY(y + c.height) - at[1];
		at[2] = System.currentTimeMillis();
	}

	// ---------- taps ----------
	// A tap arrives as "move the cursor here" and "press" at the same moment. The game acts on a
	// click with the menu it built while drawing the last frame, for wherever the cursor was then,
	// so a quick tap ran the previous spot's option - usually "Walk here". A fresh tap is held back
	// until one frame has been drawn with the cursor on it, then let through.

	private static int heldButton, heldX, heldY, heldAt;
	private static long heldTime;
	private static int framesDrawn;

	/** Called after each in-game frame is drawn (the menu for the cursor is built while drawing). */
	public static void frameDrawn() {
		framesDrawn++;
	}

	/** Called each game tick right after the mouse is read. */
	public static void holdTap() {
		if (client.gameState != 30 && client.gameState != 10) {
			heldButton = 0;
			return;
		}
		@Pc(0) boolean fresh = Mouse.lastButton != 0;
		if (fresh) {
			tapButton = Mouse.lastButton;
		}
		if (fresh && MobileLayout.nearMapClose(Mouse.clickX, Mouse.clickY)) {
			// the world map's close button is small and tucked in the corner: a tap anywhere near it counts
			Mouse.clickX = MobileLayout.mapCloseCx();
			Mouse.clickY = MobileLayout.mapCloseCy();
		}
		if (heldButton != 0 && framesDrawn > heldAt) {
			// the held tap's menu is ready: let it through (a newer tap waits its turn)
			@Pc(0) int b = Mouse.lastButton, x = Mouse.clickX, y = Mouse.clickY;
			@Pc(0) long t = Mouse.clickTime;
			Mouse.lastButton = heldButton;
			Mouse.clickX = heldX;
			Mouse.clickY = heldY;
			Mouse.clickTime = heldTime;
			heldButton = 0;
			if (fresh) {
				hold(b, x, y, t);
			}
			return;
		}
		if (fresh && (heldButton != 0 || ScriptRunner.anInt3751 != Mouse.clickX || ScriptRunner.anInt1892 != Mouse.clickY)) {
			// the last frame's menu was built somewhere else (or another tap is waiting: the newest wins)
			hold(Mouse.lastButton, Mouse.clickX, Mouse.clickY, Mouse.clickTime);
			Mouse.lastButton = 0;
		}
		if (heldButton != 0) {
			// make sure the next frame builds its menu at the tap
			Mouse.lastMouseX = heldX;
			Mouse.lastMouseY = heldY;
		}
	}

	/** Button of the latest tap: 2 = long press (right click), which never moves the keyboard. */
	private static int tapButton;

	private static void hold(int button, int x, int y, long time) {
		heldButton = button;
		heldX = x;
		heldY = y;
		heldTime = time;
		heldAt = framesDrawn;
	}

	// ---------- one-finger drags ----------
	// The launcher sends one-finger drags through the GLFW scroll callback (it has no other
	// channel for movement), tagged by the x offset: TOUCH_START when a finger goes down, and
	// TOUCH_MOVE + dx with dy as the y offset for each movement, in render pixels. A drag that
	// starts on a scrollable list (chat, quests, bank, friends...) scrolls it; anywhere else it
	// turns the camera.

	public static final int TOUCH_START = 200000;
	public static final int TOUCH_MOVE = 100000;

	/** Render pixels of finger movement per wheel notch (a notch scrolls a list 45 pixels). */
	private static final int SCROLL_STEP = 24;

	private static boolean touchStarted = false;
	private static int touchDx = 0;
	private static int touchDy = 0;
	private static boolean dragScrolls = false;
	private static int scrollRemainder = 0;

	/** From the GLFW scroll callback (input thread): true when the event was a tagged touch drag. */
	public static synchronized boolean touchEvent(double xoffset, double yoffset) {
		if (xoffset >= TOUCH_START - 1000) {
			touchStarted = true;
			touchDx = 0;
			touchDy = 0;
			return true;
		}
		if (xoffset >= TOUCH_MOVE - 50000) {
			touchDx += (int) Math.round(xoffset - TOUCH_MOVE);
			touchDy += (int) Math.round(yoffset);
			return true;
		}
		return false;
	}

	/** Called each frame right after the mouse wheel is read, before the game uses it. */
	public static void applyTouchDrag() {
		@Pc(0) boolean start;
		@Pc(0) int dx;
		@Pc(0) int dy;
		synchronized (MobileInput.class) {
			start = touchStarted;
			dx = touchDx;
			dy = touchDy;
			touchStarted = false;
			touchDx = 0;
			touchDy = 0;
		}
		if (start) {
			// the world map takes the wheel for zoom, which made a drag there zoom out: there it pans
			dragScrolls = client.gameState == 30 && InterfaceList.topLevelInterface != WORLD_MAP && onScrollable(Mouse.lastMouseX, Mouse.lastMouseY);
			scrollRemainder = 0;
		}
		if (client.gameState != 30 || dx == 0 && dy == 0) {
			return;
		}
		if (InterfaceList.topLevelInterface == WORLD_MAP && WorldMap.loadPercentage >= 100 && !dragScrolls) {
			// the map follows the finger (the same sums as the game's own mouse drag)
			mapRemX -= dx * 2.0D / WorldMap.targetZoom;
			mapRemY -= dy * 2.0D / WorldMap.targetZoom;
			@Pc(0) int mx = (int) mapRemX;
			@Pc(0) int my = (int) mapRemY;
			mapRemX -= mx;
			mapRemY -= my;
			if (mx != 0) {
				WorldMap.method1964(WorldMap.anInt435 + mx);
			}
			if (my != 0) {
				WorldMap.method4641(WorldMap.anInt919 + my);
			}
			return;
		}
		if (dragScrolls) {
			// finger up = the list moves up with it = scroll down
			scrollRemainder -= dy;
			@Pc(0) int notches = scrollRemainder / SCROLL_STEP;
			scrollRemainder -= notches * SCROLL_STEP;
			MouseWheel.wheelRotation += notches;
		} else {
			Camera.yawTarget -= dx * 2.0D;
			Camera.pitchTarget += dy * 2.0D;
			Camera.clampCameraAngle();
		}
	}

	private static final int WORLD_MAP = 755;
	private static double mapRemX, mapRemY;
	private static long lastMapZoom;
	/** the world map's zoom steps, as the game names them */
	private static final int[] MAP_ZOOMS = {37, 50, 75, 100, 200};

	/**
	 * Called each tick after the key queue is read: on the world map a pinch (sent by the launcher
	 * as F3 = fingers together, F4 = apart) steps the map zoom instead of the camera's.
	 */
	public static void worldMapPinch() {
		if (InterfaceList.topLevelInterface != WORLD_MAP) {
			return;
		}
		@Pc(0) int step = 0;
		for (@Pc(0) int i = 0; i < InterfaceList.keyQueueSize; i++) {
			if (InterfaceList.keyCodes[i] == 3) step = -1;
			else if (InterfaceList.keyCodes[i] == 4) step = 1;
		}
		@Pc(0) long now = System.currentTimeMillis();
		if (step == 0 || now - lastMapZoom < 250L) {
			return;
		}
		lastMapZoom = now;
		@Pc(0) int cur = WorldMap.getTargetZoom();
		@Pc(0) int at = 0;
		for (@Pc(0) int i = 0; i < MAP_ZOOMS.length; i++) {
			if (MAP_ZOOMS[i] == cur) at = i;
		}
		at = Math.max(0, Math.min(MAP_ZOOMS.length - 1, at + step));
		WorldMap.setTargetZoom(MAP_ZOOMS[at]);
	}

	private static boolean isScrollable(Component c) {
		return c.scrollMaxV > c.height || c.onScroll != null;
	}

	/** True when (x, y) is on a scrollable list drawn in the last half second. */
	private static boolean onScrollable(@Pc(0) int x, @Pc(0) int y) {
		@Pc(0) long now = System.currentTimeMillis();
		try {
			for (Map.Entry<Component, long[]> e : drawn.entrySet()) {
				@Pc(0) Component c = e.getKey();
				@Pc(0) long[] at = e.getValue();
				if (now - at[2] > 500L || c.width <= 0 || c.height <= 0 || !isScrollable(c)) {
					continue;
				}
				// the scrollbar sits just right of the list
				if (x >= at[0] && x < at[0] + at[3] + 16 && y >= at[1] && y < at[1] + at[4]) {
					return true;
				}
			}
		} catch (Throwable t) {
			System.out.println("scroll test failed: " + t);
		}
		return false;
	}

	/** Interfaces are rebuilt now and then; forget components not drawn for a while. */
	private static void pruneDrawn() {
		@Pc(0) long now = System.currentTimeMillis();
		drawn.values().removeIf(at -> now - at[2] > 5000L);
	}

	/** Called each frame from the main loop. */
	public static void tickLoginScreen(int gameState) {
		if (gameState != lastState) {
			// arriving in game: whatever was being typed on the login screen is done
			if (gameState == 30 && focused) {
				requestFocus(false);
			}
			lastState = gameState;
		}
		updateScale(gameState);
		if (gameState == 10) {
			if (Mouse.clickTime != lastClick) {
				lastClick = Mouse.clickTime;
				@Pc(0) boolean onField = onLoginField(Mouse.clickX, Mouse.clickY);
				// re-grant on every field tap: the launcher clears its input state on a grant, so
				// switching from username to password starts clean
				focused = false;
				requestFocus(onField);
				announcedFieldBottom = -1;
			}
			if (focused) {
				announceActiveField();
			}
		} else if (gameState == 30) {
			// in game, the chatbox is the text field: tap it to type, tap the world to put the
			// keyboard away - no on-screen keyboard button needed
			if (Mouse.clickTime != lastClick) {
				lastClick = Mouse.clickTime;
				if (tapButton == 2) {
					// a long press opens the options menu for whatever is under the chat; it isn't a request to type
					return;
				}
				@Pc(0) boolean onChat = onChatbox(Mouse.clickX, Mouse.clickY);
				if (onChat) {
					focused = false;
					announcedFieldBottom = -1;
				}
				requestFocus(onChat);
			}
			if (focused) {
				announceChatBottom();
			}
		} else if (gameState != 25 && gameState != 28) {
			// 25 and 28 are map loads - on the login screen the 3D backdrop moves to a new region
			// every few seconds, so leave the keyboard alone; anything else (logging in,
			// disconnected) puts it away
			if (focused) {
				requestFocus(false);
			}
			if (!drawn.isEmpty()) {
				drawn.clear();
			}
		}
	}

	private static void updateScale(int gameState) {
		@Pc(0) int want;
		if (gameState == 10 && GlRenderer.enabled) {
			want = 1;
		} else if (gameState == 30 || announcedScale == 1 && !GlRenderer.enabled) {
			// only the real in-game state: the login screen's 3D backdrop passes through the map
			// loading state (25) whenever it moves to a new region, and must stay login-sized
			want = 2;
		} else {
			return; // loading, logging in, or a backdrop region load: keep whatever is showing
		}
		if (want != announcedScale) {
			announcedScale = want;
			System.out.println(want == 1 ? "SCHLUMPY_SCALE_LOGIN" : "SCHLUMPY_SCALE_GAME");
		}
	}

	/**
	 * True when the tap landed on the username or password box. The boxes are text components
	 * sitting inside the wide field graphic, so a hit counts when the tapped text component is
	 * an editable-looking one: empty, holding the typing cursor, or masked, or when the tap is
	 * inside a wide, short graphic (the field frame).
	 */
	private static boolean onLoginField(@Pc(0) int x, @Pc(0) int y) {
		@Pc(0) long now = System.currentTimeMillis();
		@Pc(0) boolean hit = false;
		try {
			for (Map.Entry<Component, long[]> e : drawn.entrySet()) {
				@Pc(0) Component c = e.getKey();
				@Pc(0) long[] at = e.getValue();
				if (now - at[2] > 500L || c.width <= 0 || c.height <= 0) {
					continue;
				}
				if (x < at[0] || x >= at[0] + c.width || y < at[1] || y >= at[1] + c.height) {
					continue;
				}
				@Pc(0) String text = c.text == null ? "" : c.text.toString();
				if (isFieldText(c, text) || isFieldFrame(c)) {
					hit = true;
				}
			}
		} catch (Throwable t) {
			System.out.println("login field test failed: " + t);
		}
		return hit;
	}

	private static int announcedFieldBottom = -1;

	/**
	 * Tells the launcher where the field with the typing cursor is, so it can keep it above the
	 * keyboard - Enter in the username box moves the cursor to the password box by itself.
	 */
	/**
	 * While typing in game, the launcher keeps the bottom of the chatbox (where the typed line
	 * is) above the keyboard, wherever in the chatbox the player tapped.
	 */
	private static void announceChatBottom() {
		@Pc(0) long now = System.currentTimeMillis();
		@Pc(0) int bottom = -1;
		for (Map.Entry<Component, long[]> e : drawn.entrySet()) {
			@Pc(0) Component c = e.getKey();
			@Pc(0) long[] at = e.getValue();
			if (now - at[2] > 500L || c.id >>> 16 != 752 || c.height <= 0 || c.height > 400) {
				continue;
			}
			bottom = Math.max(bottom, (int) at[1] + c.height);
		}
		if (bottom > 0 && bottom != announcedFieldBottom) {
			announcedFieldBottom = bottom;
			System.out.println("SCHLUMPY_FIELD_BOTTOM " + bottom);
		}
	}

	private static void announceActiveField() {
		@Pc(0) long now = System.currentTimeMillis();
		for (Map.Entry<Component, long[]> e : drawn.entrySet()) {
			@Pc(0) Component c = e.getKey();
			@Pc(0) long[] at = e.getValue();
			if (now - at[2] > 500L || c.type != 4 || c.text == null) {
				continue;
			}
			if (c.text.toString().endsWith("|")) {
				@Pc(0) int bottom = (int) at[1] + c.height;
				if (bottom != announcedFieldBottom) {
					announcedFieldBottom = bottom;
					System.out.println("SCHLUMPY_FIELD_BOTTOM " + bottom);
				}
				return;
			}
		}
	}

	private static boolean onChatbox(@Pc(0) int x, @Pc(0) int y) {
		if (MobileLayout.notChat(x, y)) {
			return false;     // a tap on the tabs or the open panel, or the chat is put away
		}
		if (InterfaceList.openInterfaces.get((752L << 16) | 12) != null) {
			return false;     // a dialogue is over the chat: the tap is "click here to continue"
		}
		@Pc(0) long now = System.currentTimeMillis();
		@Pc(0) boolean hit = false;
		try {
			for (Map.Entry<Component, long[]> e : drawn.entrySet()) {
				@Pc(0) Component c = e.getKey();
				@Pc(0) long[] at = e.getValue();
				if (now - at[2] > 500L || c.width <= 0 || c.height <= 0) {
					continue;
				}
				if (x >= at[0] && x < at[0] + c.width && y >= at[1] && y < at[1] + c.height) {
					if (isChatbox(c.id >>> 16)) {
						hit = true;
					}
				}
			}
		} catch (Throwable t) {
			System.out.println("chatbox test failed: " + t);
		}
		return hit;
	}

	private static boolean isFieldText(Component c, String text) {
		if (c.type != 4) {
			return false;
		}
		if (text.length() == 0 || text.endsWith("|")) {
			return true;
		}
		for (@Pc(0) int i = 0; i < text.length(); i++) {
			if (text.charAt(i) != '*') {
				return false;
			}
		}
		return true;
	}

	/** The ornate box behind a text field: a wide, short graphic (username/password are 142x22). */
	private static boolean isFieldFrame(Component c) {
		return c.type == 5 && c.width >= 100 && c.height <= 40 && c.width >= c.height * 4;
	}
}
