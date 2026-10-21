package rt4;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.File;

/**
 * Loads the holiday login art bundled inside the client jar and turns it into
 * a client Sprite. Every path here fails soft: if anything at all goes wrong
 * we return null and the client keeps its normal title art.
 */
public class HolidayAssets {

	private static String loadedResource = null;
	private static BufferedImage sourceImage = null;
	private static Sprite background = null;
	private static boolean tried = false;

	/** Last scaled sprite built, so repeated frames do not rescale. */
	private static int scaledW = -1;
	private static int scaledH = -1;
	private static Sprite scaledSprite = null;

	private static BufferedImage source() {
		String res = HolidayTheme.backgroundResource();
		if (res == null) {
			return null;
		}
		if (tried && res.equals(loadedResource)) {
			return sourceImage;
		}
		loadedResource = res;
		tried = true;
		sourceImage = null;
		background = null;
		scaledSprite = null;
		scaledW = -1;
		try {
			InputStream in = HolidayAssets.class.getResourceAsStream(res);
			if (in != null) {
				sourceImage = ImageIO.read(in);
				in.close();
				if (sourceImage != null) {
					background = toSprite(sourceImage);
					System.out.println("holiday art loaded: " + res + " (" + sourceImage.getWidth() + "x" + sourceImage.getHeight() + ")");
				}
			} else {
				System.out.println("holiday art missing from jar: " + res);
			}
		} catch (Throwable t) {
			System.out.println("holiday art failed: " + t);
			sourceImage = null;
			background = null;
		}
		return sourceImage;
	}

	/** Full-resolution themed art (used for the title screen). */
	public static Sprite background() {
		source();
		return background;
	}

	/**
	 * Themed art scaled to an interface component's size. Only responds to the big
	 * full-screen graphic so it cannot hijack small buttons or panels.
	 */
	public static Sprite backgroundForSize(int w, int h) {
		if (w < 600 || h < 400) {
			return null;
		}
		BufferedImage img = source();
		if (img == null) {
			return null;
		}
		if (w == scaledW && h == scaledH && scaledSprite != null) {
			return scaledSprite;
		}
		try {
			BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
			java.awt.Graphics2D g = out.createGraphics();
			g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			g.drawImage(img, 0, 0, w, h, null);
			g.dispose();
			scaledSprite = toSprite(out);
			scaledW = w;
			scaledH = h;
			System.out.println("holiday login background applied at " + w + "x" + h);
			return scaledSprite;
		} catch (Throwable t) {
			System.out.println("holiday background resize failed: " + t);
			return null;
		}
	}

	public static Sprite toSprite(BufferedImage img) {
		int w = img.getWidth();
		int h = img.getHeight();
		int[] argb = new int[w * h];
		img.getRGB(0, 0, w, h, argb, 0, w);
		int[] rgb = new int[w * h];
		for (int i = 0; i < argb.length; i++) {
			int p = argb[i];
			if ((p >>> 24) < 128) {
				rgb[i] = 0;
			} else {
				int c = p & 0xFFFFFF;
				// the software renderer treats 0 as transparent, so nudge pure black
				rgb[i] = c == 0 ? 0x010101 : c;
			}
		}
		SoftwareSprite soft = new SoftwareSprite(w, h, 0, 0, w, h, rgb);
		if (GlRenderer.enabled) {
			return new GlSprite(soft);
		}
		return soft;
	}

	/** Writes a sprite out as a PNG (used to lift the original art out of the cache). */
	public static void dump(Sprite sprite, String path) {
		try {
			if (!(sprite instanceof SoftwareSprite)) {
				return;
			}
			int[] px = ((SoftwareSprite) sprite).pixels;
			BufferedImage bi = new BufferedImage(sprite.width, sprite.height, BufferedImage.TYPE_INT_RGB);
			bi.setRGB(0, 0, sprite.width, sprite.height, px, 0, sprite.width);
			ImageIO.write(bi, "png", new File(path));
		} catch (Throwable t) {
			System.out.println("dump failed: " + t);
		}
	}
}
