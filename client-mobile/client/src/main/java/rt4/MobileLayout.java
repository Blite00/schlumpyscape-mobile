package rt4;

import org.openrs2.deob.annotation.Pc;

/**
 * Mobile-only: resizable in-game layout tweaks. The side-tab buttons are 33x36 pixels, a
 * fingertip on a phone, so in the resizable layout (interface 746) the two rows of seven tabs
 * become one row of fourteen big tabs centred along the bottom. The chat sits just above that row
 * and can be put away with a little x beside its top corner; a chat bubble at the bottom left
 * brings it back. The minimap group moves to the top left, mirrored (orbs on its right, the
 * log-out x in the corner), and the open panel (inventory, prayers...) stands at the right above
 * the tabs, drawn 1.2x larger. Positions change right after the game lays out each component, so
 * taps and drawing both use the new places; the panel zoom is applied while drawing it and
 * undone on the mouse position while the game hit-tests it.
 */
public class MobileLayout {

	private static final int RESIZABLE = 746;
	/** the tab bar: 24 holds the stones (25), the selected highlight (40) and the icons (55) */
	private static final int TABS = 24;
	private static final int PANEL = 73;
	/** the chat: 70 the message box, 23 the row of filter buttons under it, 71/72 the split private chat */
	private static final int CHAT = 70;
	private static final int CHAT_FILTERS = 23;
	private static final int TAB_W = 33;
	private static final int TAB_H = 36;
	private static final float MAX_SCALE = 1.8f;
	/** the minimap, compass, orbs and log-out button */
	private static final int MAP = 7;
	/** how much larger the open panel is drawn */
	private static final float PANEL_ZOOM = 1.2f;
	/** gap between the minimap group and the screen corner, so the log-out x is easy to reach */
	private static final int MAP_PAD = 18;
	private static int mapW;

	/** chat shown (true) or put away with the x (false); the bubble brings it back */
	public static boolean chatOpen = true;
	/** where the bubble and the x were drawn this frame, {x, y, w, h}; w == 0 when not shown */
	private static final int[] bubble = new int[4];
	private static final int[] closeX = new int[4];
	private static int chatX, chatY, chatW;
	/** the chat's components, so a tap can show or hide them at once instead of at the next layout */
	private static Component chatBox, chatFilters;

	private static int tabW() {
		return Math.round(TAB_W * scale());
	}

	private static int tabH() {
		return Math.round(TAB_H * scale());
	}

	private static float scale() {
		return Math.min(MAX_SCALE, (GameShell.canvasWidth - 4) / (14.0f * TAB_W));
	}

	private static boolean active() {
		return InterfaceList.topLevelInterface == RESIZABLE && client.gameState == 30 && scale() > 1.05f;
	}

	/** Right-click menu row pitch: taller rows on the phone so options are finger-sized. */
	public static int menuRow() {
		if (!active() || MiniMenu.size <= 0) return 15;
		return Math.max(15, Math.min(MENU_ROW, (GameShell.canvasHeight - 26) / MiniMenu.size));
	}

	/** Distance from a menu row's top edge to its text baseline. */
	public static int menuBase() {
		return (menuRow() + 11) / 2;
	}

	private static final int MENU_ROW = 32;

	/** Called right after the game has sized and positioned a component. */
	public static void adjust(Component c) {
		if (c != null && c.id >>> 16 == WORLD_MAP && InterfaceList.topLevelInterface == WORLD_MAP && client.gameState == 30) {
			adjustWorldMap(c);
			return;
		}
		if (c != null && c.id >>> 16 != RESIZABLE && active()) {
			spaceListRow(c);
			return;
		}
		if (c == null || InterfaceList.topLevelInterface != RESIZABLE || c.id >>> 16 != RESIZABLE) {
			return;
		}
		try {
			if (scale() <= 1.05f) {
				return;
			}
			@Pc(0) int child = c.id & 0xFFFF;
			@Pc(0) int parent = c.overlayer == -1 ? -1 : c.overlayer & 0xFFFF;
			@Pc(0) int barW = tabW() * 14;
			@Pc(0) int th = tabH();
			if (parent == -1) {
				if (child == TABS) {
					c.width = barW;
					c.height = th;
					c.x = (GameShell.canvasWidth - barW) / 2;
					c.y = GameShell.canvasHeight - th;
				} else if (child == PANEL) {
					// at the right, above the tabs; it is laid out at its normal size and drawn
					// zoomed from its top left corner, so leave room for the zoomed size
					@Pc(0) float z = zoom();
					@Pc(0) int zw = Math.round(c.width * z);
					@Pc(0) int zh = Math.round(c.height * z);
					c.x = Math.max(0, GameShell.canvasWidth - 4 - zw);
					c.y = Math.max(0, GameShell.canvasHeight - th - 2 - zh);
					panelX = c.x; panelY = c.y; panelW = c.hidden ? 0 : zw; panelH = zh;
				} else if (child == MAP) {
					c.x = MAP_PAD;
					c.y = MAP_PAD;
					mapW = c.width;
				} else if (child == 17 || child == 18 || child == 19) {
					// the bits that hang under the minimap follow it to the left
					c.x = GameShell.canvasWidth - c.x - c.width;
					c.y += MAP_PAD;
				} else if (child == CHAT_FILTERS) {
					c.y = GameShell.canvasHeight - th - c.height;
					showChat(c);
					chatFilters = c;
				} else if (child == CHAT) {
					c.y = GameShell.canvasHeight - th - 23 - c.height;
					showChat(c);
					chatBox = c;
					chatX = c.x;
					chatY = c.y;
					chatW = c.width;
				} else if (child == 71 || child == 72) {
					c.y -= th;
				}
			} else if (parent == MAP && mapW > 0) {
				// mirror the group: the orbs go to the map's right, the log-out x to the corner
				c.x = mapW - c.x - c.width;
			} else if (parent == TABS) {
				c.width = barW;
				c.height = th;
				c.x = 0;
				c.y = 0;
			} else if (c.overlayer >>> 16 == RESIZABLE && isTabLayer(parent)) {
				// where the game put it in the original 231x72 bar
				InterfaceList.method2291(c, TAB_H * 2, TAB_W * 7);
				@Pc(0) int col = Math.max(0, Math.min(6, Math.round(c.x / (float) TAB_W)));
				@Pc(0) int row = Math.max(0, Math.min(1, Math.round(c.y / (float) TAB_H)));
				@Pc(0) float s = scale();
				@Pc(0) int dx = c.x - col * TAB_W;
				@Pc(0) int dy = c.y - row * TAB_H;
				// the two rows of seven become one row of fourteen
				c.x = (row * 7 + col) * tabW() + Math.round(dx * s);
				c.y = Math.round(dy * s);
				c.width = Math.round(c.width * s);
				c.height = Math.round(c.height * s);
				// the red "selected" stone is a tiled sprite: stretched, it would repeat as a grid
				c.spriteTiling = false;
			}
		} catch (Throwable t) {
			// leave the game's own layout alone
		}
	}

	/** Row pitch multiplier for one-line text rows in scrolling lists (music, quests, friends). */
	private static final int LIST_SPACING = 2;
	private static final java.util.WeakHashMap<Component, int[]> listScroll = new java.util.WeakHashMap<>();

	/**
	 * Single-line text rows in a vertically scrolling list are ~15px apart: far too close to tap
	 * reliably. Spread them out and grow the list's scroll height to match. Layout recomputes
	 * y and height from the definition every pass, so scaling them here is idempotent.
	 */
	private static void spaceListRow(Component c) {
		if (c.overlayer == -1) return;
		Component p = InterfaceList.getComponent(c.overlayer);
		if (p == null || p.scrollMaxV <= p.height) return;
		// one-line text in a list of tappable rows (music tracks, quests, friends), headers included
		// so they stay in step; chat history and plain reading text keep their density
		if (c.type != 4 || c.height > 20) return;
		if (c.id >>> 16 == 137 || c.id >>> 16 == 752 || !hasTapRows(p, c)) return;
		c.y *= LIST_SPACING;
		c.height *= LIST_SPACING;
		c.valign = 1;
		int[] st = listScroll.get(p);
		if (st == null || p.scrollMaxV != st[1]) {
			// first sight, or a script has resized the list since
			st = new int[] {p.scrollMaxV, p.scrollMaxV * LIST_SPACING};
			listScroll.put(p, st);
		}
		p.scrollMaxV = st[1];
	}

	/** Lists known to hold tappable rows; a "no" is only trusted for a second (scripts add ops later). */
	private static final java.util.WeakHashMap<Component, Long> tapLists = new java.util.WeakHashMap<>();

	private static boolean hasTapRows(Component p, Component c) {
		Long known = tapLists.get(p);
		if (known != null && (known == 0L || System.currentTimeMillis() - known < 1000L)) return known == 0L;
		boolean found = c.ops != null || c.buttonType != 0;
		Component[] group = InterfaceList.components[p.id >>> 16];
		for (int i = 0; group != null && i < group.length && !found; i++) {
			Component k = group[i];
			found = k != null && k.overlayer == p.id && k.type == 4 && k.height <= 20 && (k.ops != null || k.buttonType != 0);
		}
		tapLists.put(p, found ? 0L : System.currentTimeMillis());
		return found;
	}

	private static int panelX, panelY, panelW, panelH;

	/** True when a tap at (x, y) cannot be meant for the chat: it is on the tab row or the open panel, or the chat is away. */
	public static boolean notChat(int x, int y) {
		if (!active()) {
			return false;
		}
		if (!chatOpen) {
			return true;
		}
		@Pc(0) int th = tabH();
		@Pc(0) int barX = (GameShell.canvasWidth - tabW() * 14) / 2;
		if (y >= GameShell.canvasHeight - th && x >= barX && x < barX + tabW() * 14) {
			return true;
		}
		return panelW > 0 && x >= panelX && x < panelX + panelW && y >= panelY && y < panelY + panelH;
	}

	/** components this class has hidden, so it only ever unhides its own */
	private static final java.util.Set<Component> hiddenByUs = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Component, Boolean>());

	private static void showChat(Component c) {
		if (!chatOpen) {
			if (!c.hidden) {
				c.hidden = true;
				hiddenByUs.add(c);
			}
		} else if (hiddenByUs.remove(c)) {
			c.hidden = false;
		}
	}

	/**
	 * Called each frame right after the mouse is read: a tap on the bubble or the x toggles the
	 * chat and is used up, so the game never sees it (no walking to where the button is).
	 */
	public static void handleTap() {
		try {
			if (!active() || Mouse.lastButton == 0) {
				return;
			}
			@Pc(0) int[] hit = chatOpen ? closeX : bubble;
			@Pc(0) int pad = 6;     // a little extra all round: fingers are not precise
			if (hit[2] > 0 && Mouse.clickX >= hit[0] - pad && Mouse.clickX < hit[0] + hit[2] + pad && Mouse.clickY >= hit[1] - pad && Mouse.clickY < hit[1] + hit[3] + pad) {
				chatOpen = !chatOpen;
				if (chatBox != null) showChat(chatBox);
				if (chatFilters != null) showChat(chatFilters);
				Mouse.lastButton = 0;
				Mouse.lastAction = 0;
				Mouse.clickX = -1000;
				Mouse.clickY = -1000;
				MobileInput.requestFocus(false);
			}
		} catch (Throwable t) {
			// never let the overlay break input
		}
	}

	/** Called after the interfaces are drawn: the chat bubble, or the x beside an open chat. */
	public static void drawOverlay() {
		bubble[2] = 0;
		closeX[2] = 0;
		if (!active()) {
			return;
		}
		try {
			@Pc(0) int w = GameShell.canvasWidth;
			@Pc(0) int h = GameShell.canvasHeight;
			clip(0, 0, w, h);
			@Pc(0) int th = tabH();
			if (chatOpen) {
				if (chatBox == null || chatBox.hidden || chatW <= 0) {
					return;  // no chat showing (a cutscene, a full-screen interface)
				}
				@Pc(0) int size = Math.max(26, th * 2 / 5);
				@Pc(0) int x = Math.min(w - size, chatX + chatW + 3);
				@Pc(0) int y = chatY + 2;
				drawClose(x, y, size);
				closeX[0] = x; closeX[1] = y; closeX[2] = size; closeX[3] = size;
			} else {
				@Pc(0) int bw = Math.max(40, th - 6);
				@Pc(0) int bh = Math.max(34, th - 14);
				@Pc(0) int x = 8;
				@Pc(0) int y = h - bh - 10;
				drawBubble(x, y, bw, bh);
				bubble[0] = x; bubble[1] = y; bubble[2] = bw; bubble[3] = bh + 8;
			}
		} catch (Throwable t) {
			// drawing is best effort
		}
	}

	private static void clip(int x0, int y0, int x1, int y1) {
		if (GlRenderer.enabled) {
			GlRaster.setClip(x0, y0, x1, y1);
		} else {
			SoftwareRaster.setClip(x0, y0, x1, y1);
		}
	}

	private static void fill(int x, int y, int w, int h, int rgb) {
		if (w <= 0 || h <= 0) {
			return;
		}
		if (GlRenderer.enabled) {
			GlRaster.fillRect(x, y, w, h, rgb);
		} else {
			SoftwareRaster.fillRect(x, y, w, h, rgb);
		}
	}

	/** A speech bubble in the game's stone-and-parchment colours, three dots inside, tail bottom left. */
	private static void drawBubble(int x, int y, int w, int h) {
		@Pc(0) int edge = 0x2b2117;
		@Pc(0) int body = 0xe8d8a8;
		@Pc(0) int r = Math.max(3, h / 6);
		// outline, then body: a rounded box out of three overlapping rectangles
		fill(x + r, y, w - 2 * r, h, edge);
		fill(x, y + r, w, h - 2 * r, edge);
		fill(x + r / 2, y + r / 2, w - r, h - r, edge);
		fill(x + r + 2, y + 2, w - 2 * r - 4, h - 4, body);
		fill(x + 2, y + r + 2, w - 4, h - 2 * r - 4, body);
		fill(x + r / 2 + 2, y + r / 2 + 2, w - r - 4, h - r - 4, body);
		// the tail
		@Pc(0) int tx = x + w / 4;
		for (@Pc(0) int i = 0; i < 8; i++) {
			fill(tx - i / 2, y + h - 2 + i, Math.max(2, 10 - i) + 2, 1, edge);
			if (i < 6) {
				fill(tx - i / 2 + 2, y + h - 2 + i, Math.max(1, 8 - i - 2), 1, body);
			}
		}
		// three dots
		@Pc(0) int d = Math.max(4, h / 6);
		@Pc(0) int cy = y + h / 2 - d / 2;
		for (@Pc(0) int i = -1; i <= 1; i++) {
			fill(x + w / 2 + i * d * 2 - d / 2, cy, d, d, 0x4a3a28);
		}
	}

	/** A small dark square with a red-brown border and an x across it. */
	private static void drawClose(int x, int y, int size) {
		fill(x, y, size, size, 0x2b2117);
		fill(x + 2, y + 2, size - 4, size - 4, 0x5a4a32);
		@Pc(0) int t = Math.max(3, size / 8);
		@Pc(0) int in = size / 4;
		for (@Pc(0) int i = in; i < size - in; i++) {
			fill(x + i - t / 2, y + i - t / 2, t, t, 0xf0e0b0);
			fill(x + size - 1 - i - t / 2, y + i - t / 2, t, t, 0xf0e0b0);
		}
	}

	// ---------- the world map ----------
	// Interface 755: the map (2), the bottom bar (15: background 16, Key 17, Overview 18/19, search
	// box 20), the column of zoom buttons (26: sprites and labels 33-42, 35x35 each) and the tiny
	// close x (3). The buttons are made finger-sized; the bar grows up over the bottom of the map.

	private static final int WORLD_MAP = 755;
	private static final int MAP_BTN = 56;
	private static final int MAP_BAR_GROW = MAP_BTN - 32;

	private static Component mapClose;
	/** Extra touch margin around the world map's close button, in render pixels. */
	private static final int MAP_CLOSE_SLOP = 50;

	/** True when (x, y) is on or near the world map's close button while the map is up. */
	public static boolean nearMapClose(int x, int y) {
		@Pc(0) Component c = mapClose;
		if (c == null || c.hidden || InterfaceList.topLevelInterface != WORLD_MAP || client.gameState != 30) {
			return false;
		}
		// it hugs the top right corner, so the zone runs to the edges there
		return x >= c.x - MAP_CLOSE_SLOP && y <= c.y + c.height + MAP_CLOSE_SLOP;
	}

	public static int mapCloseCx() {
		return mapClose.x + mapClose.width / 2;
	}

	public static int mapCloseCy() {
		return mapClose.y + mapClose.height / 2;
	}

	private static void adjustWorldMap(Component c) {
		try {
			@Pc(0) int child = c.id & 0xFFFF;
			@Pc(0) int parent = c.overlayer == -1 ? -1 : c.overlayer & 0xFFFF;
			if (child == 3) {
				// close: 16x16 in the top right corner
				@Pc(0) int right = c.x + c.width;
				c.width = 40;
				c.height = 40;
				c.x = right - 40;
				c.y = 4;
				mapClose = c;
			} else if (child == 15) {
				c.y -= MAP_BAR_GROW;
				c.height = MAP_BTN;
			} else if (parent == 15) {
				if (child == 16) {
					c.y = 0;
					c.height = MAP_BTN;
				} else if (child == 17 || child == 18 || child == 19) {
					@Pc(0) int mid = c.x + c.width / 2;
					c.width = MAP_BTN;
					c.height = MAP_BTN;
					c.x = mid - MAP_BTN / 2;
					c.y = 0;
				} else if (child == 20) {
					c.y = (MAP_BTN - c.height) / 2;
				}
			} else if (child == 26) {
				// the zoom column: right side, standing on the (taller) bar
				@Pc(0) int right = c.x + c.width;
				@Pc(0) int bottom = c.y + c.height;
				c.width = MAP_BTN;
				c.height = MAP_BTN * 5;
				c.x = right - MAP_BTN;
				c.y = Math.max(0, bottom - MAP_BAR_GROW - c.height);
			} else if (parent == 26) {
				@Pc(0) int row = Math.round(c.y / 35.0f);
				c.x = 0;
				c.y = row * MAP_BTN;
				c.width = MAP_BTN;
				c.height = MAP_BTN;
			}
		} catch (Throwable t) {
			// leave the game's own layout alone
		}
	}

	// ---------- the zoomed panel ----------

	private static float zoom() {
		return GlRenderer.enabled ? PANEL_ZOOM : 1.0f;
	}

	/** zoom origin (the panel's top left) and the mouse values swapped out while zoomed */
	private static int zx, zy;
	private static final int[] savedMouse = new int[8];
	public static boolean zoomed;

	/**
	 * Called by the draw and hit-test walkers for each component at the top level of 746: when it
	 * is the open panel, maps the mouse into the panel's unzoomed space (and, when drawing, zooms
	 * the GL projection). Returns true when the caller must call zoomExit afterwards.
	 */
	public static boolean zoomEnter(Component c, boolean draw) {
		if (zoomed || c == null || c.id != (RESIZABLE << 16 | PANEL) || !GlRenderer.enabled || !active()) {
			return false;
		}
		zx = c.x;
		zy = c.y;
		savedMouse[0] = Mouse.lastMouseX;
		savedMouse[1] = Mouse.lastMouseY;
		savedMouse[2] = Mouse.clickX;
		savedMouse[3] = Mouse.clickY;
		savedMouse[4] = Mouse.anInt5850;
		savedMouse[5] = Mouse.anInt5895;
		savedMouse[6] = ScriptRunner.anInt3751;
		savedMouse[7] = ScriptRunner.anInt1892;
		Mouse.lastMouseX = unzoomX(Mouse.lastMouseX);
		Mouse.lastMouseY = unzoomY(Mouse.lastMouseY);
		Mouse.clickX = unzoomX(Mouse.clickX);
		Mouse.clickY = unzoomY(Mouse.clickY);
		Mouse.anInt5850 = unzoomX(Mouse.anInt5850);
		Mouse.anInt5895 = unzoomY(Mouse.anInt5895);
		ScriptRunner.anInt3751 = unzoomX(ScriptRunner.anInt3751);
		ScriptRunner.anInt1892 = unzoomY(ScriptRunner.anInt1892);
		zoomed = true;
		if (draw) {
			project(PANEL_ZOOM);
		}
		return true;
	}

	public static void zoomExit(boolean draw) {
		if (!zoomed) {
			return;
		}
		Mouse.lastMouseX = savedMouse[0];
		Mouse.lastMouseY = savedMouse[1];
		Mouse.clickX = savedMouse[2];
		Mouse.clickY = savedMouse[3];
		Mouse.anInt5850 = savedMouse[4];
		Mouse.anInt5895 = savedMouse[5];
		ScriptRunner.anInt3751 = savedMouse[6];
		ScriptRunner.anInt1892 = savedMouse[7];
		zoomed = false;
		if (draw) {
			project(1.0f);
			// the walker restored its clip while still zoomed: set it again unzoomed
			GlRaster.reapplyClip();
		}
	}

	private static int unzoomX(int x) {
		return x < 0 ? x : zx + Math.round((x - zx) / PANEL_ZOOM);
	}

	private static int unzoomY(int y) {
		return y < 0 ? y : zy + Math.round((y - zy) / PANEL_ZOOM);
	}

	public static int zoomX(int x) {
		return zoomed ? zx + Math.round((x - zx) * PANEL_ZOOM) : x;
	}

	public static int zoomY(int y) {
		return zoomed ? zy + Math.round((y - zy) * PANEL_ZOOM) : y;
	}

	/** The 2D projection, scaled by z about the zoom origin (GL's y runs bottom up). */
	private static void project(float z) {
		@Pc(0) float w = GlRenderer.canvasWidth;
		@Pc(0) float h = GlRenderer.canvasHeight;
		org.lwjgl.opengl.GL11.glMatrixMode(org.lwjgl.opengl.GL11.GL_PROJECTION);
		org.lwjgl.opengl.GL11.glLoadIdentity();
		if (z == 1.0f) {
			org.lwjgl.opengl.GL11.glOrtho(0, w, 0, h, -1.0D, 1.0D);
		} else {
			@Pc(0) float ox = zx;
			@Pc(0) float oy = h - zy;
			@Pc(0) float l = ox - ox / z;
			@Pc(0) float b = oy - oy / z;
			org.lwjgl.opengl.GL11.glOrtho(l, l + w / z, b, b + h / z, -1.0D, 1.0D);
		}
		org.lwjgl.opengl.GL11.glMatrixMode(org.lwjgl.opengl.GL11.GL_MODELVIEW);
	}

	/**
	 * The red "selected" tab stone has see-through columns trimmed off its right side; stretched
	 * whole, they leave a dark sliver. Stretch just the visible part over the whole tab instead.
	 */
	public static boolean drawStretched(Component c, Sprite sprite, int x, int y) {
		if (!active() || c.id >>> 16 != RESIZABLE || c.overlayer == -1 || (c.overlayer & 0xFFFF) != 40) {
			return false;
		}
		if (sprite.width <= 0 || sprite.anInt1860 <= 0 || sprite.width >= sprite.anInt1860 && sprite.anInt1863 == 0) {
			return false;
		}
		@Pc(0) int fullW = c.width * sprite.anInt1860 / sprite.width;
		@Pc(0) int left = sprite.anInt1863 * fullW / sprite.anInt1860;
		sprite.renderResized(x - left, y, fullW, c.height);
		return true;
	}

	private static boolean isTabLayer(int child) {
		return child == 25 || child == 40 || child == 55;
	}
}
