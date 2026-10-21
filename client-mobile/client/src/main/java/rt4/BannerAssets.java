package rt4;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;

/**
 * The schlumpy.net banner, drawn on the in-game welcome screen where the old
 * Discord / Credits / GitHub panels used to be.
 *
 * The server cannot push an image (it has no sprite packet), so the art is bundled in the
 * client jar and drawn over the empty box those panels sat in.
 * Every path fails soft: any problem returns null and the screen keeps its normal look.
 */
public class BannerAssets {

	/** Welcome / lobby interface the banner belongs on. */
	public static final int WELCOME_INTERFACE = 378;

	private static BufferedImage source = null;
	private static boolean tried = false;

	/** The component of the welcome interface we draw over, resolved once. */
	private static Component welcomeSlot = null;
	/** The interface's component array the slot was found in. Logging out unloads the
	 *  interface and the next login builds new components, so a new array means rescan. */
	private static Component[] scannedKids = null;

	/** Last scaled sprite, so repeated frames do not rescale. */
	private static int scaledW = -1;
	private static int scaledH = -1;
	private static Sprite scaled = null;

	/** Holiday the loaded banner belongs to, so a client left open past midnight swaps it. */
	private static int sourceTheme = -1;

	private static BufferedImage source() {
		int theme = HolidayTheme.active();
		if (tried && theme == sourceTheme) {
			return source;
		}
		tried = true;
		sourceTheme = theme;
		scaled = null;
		scaledW = -1;
		scaledH = -1;
		// holiday art follows the same calendar as the login theme and the website
		String holiday = theme == HolidayTheme.HALLOWEEN ? "/banner/welcome-halloween.jpg"
			: theme == HolidayTheme.CHRISTMAS ? "/banner/welcome-christmas.jpg"
			: theme == HolidayTheme.EASTER ? "/banner/welcome-easter.jpg" : null;
		source = holiday == null ? null : load(holiday);
		if (source == null) {
			source = load("/banner/welcome.png");
		}
		return source;
	}

	private static BufferedImage load(String path) {
		try {
			InputStream in = BannerAssets.class.getResourceAsStream(path);
			if (in == null) {
				System.out.println("welcome banner missing from jar: " + path);
				return null;
			}
			BufferedImage img = ImageIO.read(in);
			in.close();
			System.out.println("welcome banner loaded " + path + " (" + img.getWidth() + "x" + img.getHeight() + ")");
			return img;
		} catch (Throwable t) {
			System.out.println("welcome banner failed: " + path + " " + t);
			return null;
		}
	}

	/**
	 * True when this component is the box the removed Discord / Credits / GitHub panels sat in:
	 * the welcome interface's largest rectangle that is not the full-screen backdrop. The server
	 * hides the panels themselves, so the banner is drawn over the empty box.
	 */
	public static boolean isWelcomeSlot(Component c) {
		Component[][] all = InterfaceList.components;
		if (all == null || all.length <= WELCOME_INTERFACE) {
			return false;
		}
		Component[] kids = all[WELCOME_INTERFACE];
		if (kids == null) {
			return false;
		}
		if (kids != scannedKids) {
			scannedKids = kids;
			welcomeSlot = null;
			int best = -1;
			for (int i = 0; i < kids.length; i++) {
				Component k = kids[i];
				if (k == null || k.type != 3 || k.width >= 765) {
					continue;
				}
				int area = k.width * k.height;
				if (area > best) {
					best = area;
					welcomeSlot = k;
				}
			}
			System.out.println("welcome banner slot resolved: " + (welcomeSlot == null ? "none" : welcomeSlot.width + "x" + welcomeSlot.height));
		}
		return welcomeSlot != null && c == welcomeSlot;
	}

	/** Draws the banner in place of the welcome slot's box; false leaves the box to draw as normal. */
	public static boolean drawBannerArea(Component c, int x, int y) {
		if (!isWelcomeSlot(c)) {
			return false;
		}
		String[][] boxes = infoBoxes();
		if (boxes == null) {
			Sprite banner = bannerForSize(c.width, c.height);
			if (banner == null) {
				return false;
			}
			banner.render(x, y);
			return true;
		}
		// banner across the top, the server's three info boxes in a row underneath
		int bannerTop = 24;
		int bannerH = 104;
		Sprite banner = bannerForSize(c.width, bannerH);
		if (banner != null) {
			banner.render(x, y + bannerTop);
		}
		int gap = 8;
		int boxTop = bannerTop + bannerH + 8;
		int boxH = c.height - boxTop - 4;
		int boxW = (c.width - gap * 2) / 3;
		for (int i = 0; i < boxes.length; i++) {
			drawInfoBox(x + i * (boxW + gap), y + boxTop, boxW, boxH, boxes[i]);
		}
		return true;
	}

	/**
	 * Text components of the hidden message-centre panel that the server fills with
	 * "Title|line|line" for the event, character and who's-online boxes. Null when the
	 * server does not send them, so the banner keeps its plain centred layout.
	 */
	private static final int[] INFO_COMPONENTS = {37, 38, 39};

	private static String[][] infoBoxes() {
		Component[][] all = InterfaceList.components;
		if (all == null || all.length <= WELCOME_INTERFACE || all[WELCOME_INTERFACE] == null) {
			return null;
		}
		Component[] iface = all[WELCOME_INTERFACE];
		String[][] out = new String[INFO_COMPONENTS.length][];
		for (int i = 0; i < INFO_COMPONENTS.length; i++) {
			int id = INFO_COMPONENTS[i];
			if (iface.length <= id || iface[id] == null || iface[id].text == null) {
				return null;
			}
			String[] lines = iface[id].text.toString().split("\\|");
			if (lines.length < 2) {
				return null;
			}
			out[i] = lines;
		}
		return out;
	}

	private static void drawInfoBox(int x, int y, int w, int h, String[] lines) {
		if (GlRenderer.enabled) {
			GlRaster.fillRectAlpha(x, y, w, h, 0x000000, 110);
			GlRaster.drawRect(x, y, w, h, 0x3e3224);
			GlRaster.drawRect(x + 1, y + 1, w - 2, h - 2, 0x8c7a54);
		} else {
			SoftwareRaster.fillRectAlpha(x, y, w, h, 0x000000, 110);
			SoftwareRaster.drawRect(x, y, w, h, 0x3e3224);
			SoftwareRaster.drawRect(x + 1, y + 1, w - 2, h - 2, 0x8c7a54);
		}
		if (Fonts.b12Full == null || Fonts.p11Full == null) {
			return;
		}
		int cx = x + w / 2;
		Fonts.b12Full.renderCenter(JagString.parse(lines[0]), cx, y + 20, 0xff981f, 0);
		for (int i = 1; i < lines.length && i < 3; i++) {
			Fonts.p11Full.renderCenter(JagString.parse(lines[i]), cx, y + 24 + i * 22, 0xffffff, 0);
		}
	}

	/** The banner scaled to fit the slot, centred, transparent elsewhere. */
	public static Sprite bannerForSize(int w, int h) {
		if (w < 200 || h < 60) {
			return null;
		}
		BufferedImage img = source();
		if (img == null) {
			return null;
		}
		if (w == scaledW && h == scaledH && scaled != null) {
			return scaled;
		}
		try {
			// transparent around the banner, so the scroll shows above and below it
			BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
			java.awt.Graphics2D g = out.createGraphics();
			g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			// fit inside the slot preserving aspect
			double scale = Math.min((double) w / img.getWidth(), (double) h / img.getHeight());
			int dw = (int) (img.getWidth() * scale);
			int dh = (int) (img.getHeight() * scale);
			g.drawImage(img, (w - dw) / 2, (h - dh) / 2, dw, dh, null);
			g.dispose();
			scaled = HolidayAssets.toSprite(out);
			scaledW = w;
			scaledH = h;
			System.out.println("welcome banner applied at " + w + "x" + h);
			return scaled;
		} catch (Throwable t) {
			System.out.println("welcome banner resize failed: " + t);
			return null;
		}
	}
}
