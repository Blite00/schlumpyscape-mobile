package rt4;

import com.jogamp.opengl.GL2;
import org.lwjgl.opengl.GL11;
import org.openrs2.deob.annotation.OriginalArg;
import org.openrs2.deob.annotation.OriginalMember;
import org.openrs2.deob.annotation.Pc;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL11.*;

public final class GlRaster {

	@OriginalMember(owner = "client!dj", name = "b", descriptor = "I")
	public static int clipTop = 0;

	@OriginalMember(owner = "client!dj", name = "c", descriptor = "I")
	public static int clipLeft = 0;

	@OriginalMember(owner = "client!dj", name = "d", descriptor = "I")
	private static int clipRight = 0;

	@OriginalMember(owner = "client!dj", name = "e", descriptor = "I")
	public static int clipBottom = 0;

	@OriginalMember(owner = "client!dj", name = "a", descriptor = "(IIII)V")
	public static void drawHorizontalLine(@OriginalArg(0) int x, @OriginalArg(1) int y, @OriginalArg(2) int length, @OriginalArg(3) int color) {
		GlRenderer.setupRenderingWithNoTexture();
		@Pc(5) float xCoordinateStart = (float) x + 0.3F;
		@Pc(10) float xCoordinateEnd = xCoordinateStart + (float) length;
		@Pc(18) float yCoordinate = (float) GlRenderer.canvasHeight - (float) y - 0.3F;
		glBegin(GL2.GL_LINES);
		glColor3f((color >> 16 & 0xFF) / 255.0F, (color >> 8 & 0xFF) / 255.0F, (color & 0xFF) / 255.0F);
		glVertex2f(xCoordinateStart, yCoordinate);
		glVertex2f(xCoordinateEnd, yCoordinate);
		glEnd();
	}

	@OriginalMember(owner = "client!dj", name = "b", descriptor = "(IIII)V")
	public static void drawVerticalLine(@OriginalArg(0) int x, @OriginalArg(1) int y, @OriginalArg(2) int height, @OriginalArg(3) int color) {
		GlRenderer.setupRenderingWithNoTexture();
		@Pc(5) float xCoordinate = (float) x + 0.3F;
		@Pc(13) float yCoordinateStart = (float) GlRenderer.canvasHeight - (float) y - 0.3F;
		@Pc(18) float yCoordinateEnd = yCoordinateStart - (float) height;
		glBegin(GL2.GL_LINES);
		glColor3f((color >> 16 & 0xFF) / 255.0F, (color >> 8 & 0xFF) / 255.0F, (color & 0xFF) / 255.0F);
		glVertex2f(xCoordinate, yCoordinateStart);
		glVertex2f(xCoordinate, yCoordinateEnd);
		glEnd();
	}

	@OriginalMember(owner = "client!dj", name = "c", descriptor = "()V")
	public static void method1177() {
		clipLeft = 0;
		clipTop = 0;
		clipRight = GlRenderer.canvasWidth;
		clipBottom = GlRenderer.canvasHeight;
		glDisable(GL2.GL_SCISSOR_TEST);
		GlFont.method1173();
	}

	/**
	 * mobile: the texture drawPixels uploads into. Not tied to GlCleaner.contextId: GlSprite.render
	 * bumps that on every sprite, and the GL context itself lives as long as the game.
	 */
	private static int pixelsTexture = -1, pixelsTextureW, pixelsTextureH;
	private static ByteBuffer pixelsBuffer;

	/**
	 * Draws a block of 0xRRGGBB pixels (the world map is drawn by the software rasteriser into one).
	 * Mobile: GLES behind gl4es has no glDrawPixels for BGRA, so it drew nothing and the map was
	 * black; upload the pixels into a texture and draw that as a quad instead.
	 */
	/** the size last uploaded by drawPixels */
	private static int lastPixelsW, lastPixelsH;

	/** Draws the pixels last uploaded by drawPixels again, if they are still there at this size. */
	public static boolean drawLastPixels(int x, int y, int width, int height) {
		if (pixelsTexture == -1 || lastPixelsW != width || lastPixelsH != height) {
			return false;
		}
		GlRenderer.setupRgbAlphaMode1Rendering();
		GlRenderer.setTextureId(pixelsTexture);
		pixelsQuad(x, y, width, height);
		return true;
	}


	public static void drawPixels(int[] pixels, int x, int y, int width, int height) {
		if (width <= 0 || height <= 0) {
			return;
		}
		lastPixelsW = width;
		lastPixelsH = height;
		@Pc(0) int tw = 1;
		while (tw < width) tw <<= 1;
		@Pc(0) int th = 1;
		while (th < height) th <<= 1;
		GlRenderer.setupRgbAlphaMode1Rendering();
		if (pixelsTexture == -1 || pixelsTextureW != tw || pixelsTextureH != th) {
			if (pixelsTexture != -1) {
				GL11.glDeleteTextures(pixelsTexture);
			}
			pixelsTexture = GL11.glGenTextures();
			pixelsTextureW = tw;
			pixelsTextureH = th;
			GlRenderer.setTextureId(pixelsTexture);
			// allocated zeroed so the unused edge past the map is defined
			GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, tw, th, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, ByteBuffer.allocateDirect(tw * th * 4).order(ByteOrder.nativeOrder()));
		}
		GlRenderer.setTextureId(pixelsTexture);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
		@Pc(0) int n = width * height;
		if (pixelsBuffer == null || pixelsBuffer.capacity() < n * 4) {
			pixelsBuffer = ByteBuffer.allocateDirect(n * 4).order(ByteOrder.nativeOrder());
		}
		pixelsBuffer.clear();
		for (@Pc(0) int i = 0; i < n; i++) {
			@Pc(0) int p = pixels[i];
			pixelsBuffer.put((byte) (p >> 16)).put((byte) (p >> 8)).put((byte) p).put((byte) 0xFF);
		}
		pixelsBuffer.flip();
		GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixelsBuffer);
		pixelsQuad(x, y, width, height);
	}

	private static void pixelsQuad(int x, int y, int width, int height) {
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
		@Pc(0) float u = (float) width / pixelsTextureW;
		@Pc(0) float v = (float) height / pixelsTextureH;
		GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
		GL11.glTranslatef((float) x, (float) (GlRenderer.canvasHeight - y), 0.0F);
		// counter-clockwise like every other quad here: back faces are culled
		GL11.glBegin(GL11.GL_TRIANGLE_FAN);
		GL11.glTexCoord2f(0.0F, 0.0F);
		GL11.glVertex2f(0.0F, 0.0F);
		GL11.glTexCoord2f(0.0F, v);
		GL11.glVertex2f(0.0F, (float) -height);
		GL11.glTexCoord2f(u, v);
		GL11.glVertex2f((float) width, (float) -height);
		GL11.glTexCoord2f(u, 0.0F);
		GL11.glVertex2f((float) width, 0.0F);
		GL11.glEnd();
		GL11.glLoadIdentity();
	}


	@OriginalMember(owner = "client!dj", name = "a", descriptor = "(IIIII)V")
	public static void drawRect(@OriginalArg(0) int x, @OriginalArg(1) int y, @OriginalArg(2) int width, @OriginalArg(3) int height, @OriginalArg(4) int color) {
		GlRenderer.setupRenderingWithNoTexture();
		@Pc(5) float local5 = (float) x + 0.3F;
		@Pc(12) float local12 = local5 + (float) (width - 1);
		@Pc(20) float local20 = (float) GlRenderer.canvasHeight - (float) y - 0.3F;
		@Pc(27) float local27 = local20 - (float) (height - 1);
		glBegin(GL2.GL_LINE_LOOP);
		glColor3f((color >> 16 & 0xFF) / 255.0F, (color >> 8 & 0xFF) / 255.0F, (color & 0xFF) / 255.0F);
		glVertex2f(local5, local20);
		glVertex2f(local5, local27);
		glVertex2f(local12, local27);
		glVertex2f(local12, local20);
		glEnd();
	}

	@OriginalMember(owner = "client!dj", name = "a", descriptor = "(IIIIII)V")
	public static void drawRectAlpha(@OriginalArg(0) int x, @OriginalArg(1) int y, @OriginalArg(2) int width, @OriginalArg(3) int height, @OriginalArg(4) int color, @OriginalArg(5) int alpha) {
		GlRenderer.setupRenderingWithNoTexture();
		@Pc(5) float local5 = (float) x + 0.3F;
		@Pc(12) float local12 = local5 + (float) (width - 1);
		@Pc(20) float local20 = (float) GlRenderer.canvasHeight - (float) y - 0.3F;
		@Pc(27) float local27 = local20 - (float) (height - 1);
		glBegin(GL2.GL_LINE_LOOP);
		glColor4f((color >> 16 & 0xFF) / 255.0F, (color >> 8 & 0xFF) / 255.0F, (color & 0xFF) / 255.0F, Math.min(alpha, 255) / 255.0F);
		glVertex2f(local5, local20);
		glVertex2f(local5, local27);
		glVertex2f(local12, local27);
		glVertex2f(local12, local20);
		glEnd();
	}

	@OriginalMember(owner = "client!dj", name = "b", descriptor = "(IIIIII)V")
	public static void method1181(@OriginalArg(0) int arg0, @OriginalArg(1) int arg1, @OriginalArg(2) int arg2, @OriginalArg(3) int arg3, @OriginalArg(4) int arg4, @OriginalArg(5) int arg5) {
		@Pc(3) int local3 = arg2 - arg0;
		@Pc(7) int local7 = arg3 - arg1;
		@Pc(14) int local14 = local3 >= 0 ? local3 : -local3;
		@Pc(21) int local21 = local7 >= 0 ? local7 : -local7;
		@Pc(23) int local23 = local14;
		if (local14 < local21) {
			local23 = local21;
		}
		if (local23 == 0) {
			return;
		}
		@Pc(37) int local37 = (local3 << 16) / local23;
		@Pc(43) int local43 = (local7 << 16) / local23;
		if (local43 <= local37) {
			local37 = -local37;
		} else {
			local43 = -local43;
		}
		@Pc(59) int local59 = arg5 * local43 >> 17;
		@Pc(67) int local67 = arg5 * local43 + 1 >> 17;
		@Pc(73) int local73 = arg5 * local37 >> 17;
		@Pc(81) int local81 = arg5 * local37 + 1 >> 17;
		@Pc(85) int local85 = arg0 + local59;
		@Pc(89) int local89 = arg0 - local67;
		@Pc(95) int local95 = arg0 + local3 - local67;
		@Pc(101) int local101 = arg0 + local3 + local59;
		@Pc(105) int local105 = arg1 + local73;
		@Pc(109) int local109 = arg1 - local81;
		@Pc(115) int local115 = arg1 + local7 - local81;
		@Pc(121) int local121 = arg1 + local7 + local73;
		GlRenderer.setupRenderingWithNoTexture();
		glColor3f((arg4 >> 16 & 0xFF) / 255.0F, (arg4 >> 8 & 0xFF) / 255.0F, (arg4 & 0xFF) / 255.0F);
		glBegin(GL2.GL_TRIANGLE_FAN);
		if (local43 <= local37) {
			glVertex2f((float) local101, (float) (GlRenderer.canvasHeight - local121));
			glVertex2f((float) local95, (float) (GlRenderer.canvasHeight - local115));
			glVertex2f((float) local89, (float) (GlRenderer.canvasHeight - local109));
			glVertex2f((float) local85, (float) (GlRenderer.canvasHeight - local105));
		} else {
			glVertex2f((float) local85, (float) (GlRenderer.canvasHeight - local105));
			glVertex2f((float) local89, (float) (GlRenderer.canvasHeight - local109));
			glVertex2f((float) local95, (float) (GlRenderer.canvasHeight - local115));
			glVertex2f((float) local101, (float) (GlRenderer.canvasHeight - local121));
		}
		glEnd();
	}

	@OriginalMember(owner = "client!dj", name = "c", descriptor = "(IIIIII)V")
	public static void fillRectAlpha(@OriginalArg(0) int x, @OriginalArg(1) int y, @OriginalArg(2) int width, @OriginalArg(3) int height, @OriginalArg(4) int color, @OriginalArg(5) int alpha) {
		GlRenderer.setupRenderingWithNoTexture();
		@Pc(3) float local3 = (float) x;
		@Pc(8) float local8 = local3 + (float) width;
		@Pc(13) float local13 = (float) (GlRenderer.canvasHeight - y);
		@Pc(18) float local18 = local13 - (float) height;
		glBegin(GL2.GL_TRIANGLE_FAN);
		glColor4f((color >> 16 & 0xFF) / 255.0F, (color >> 8 & 0xFF) / 255.0F, (color & 0xFF) / 255.0F, Math.min(alpha, 255) / 255.0F);
		glVertex2f(local3, local13);
		glVertex2f(local3, local18);
		glVertex2f(local8, local18);
		glVertex2f(local8, local13);
		glEnd();
	}

	@OriginalMember(owner = "client!dj", name = "c", descriptor = "(IIII)V")
	public static void method1183(@OriginalArg(0) int arg0, @OriginalArg(1) int arg1, @OriginalArg(2) int arg2, @OriginalArg(3) int arg3) {
		if (clipLeft < arg0) {
			clipLeft = arg0;
		}
		if (clipTop < arg1) {
			clipTop = arg1;
		}
		if (clipRight > arg2) {
			clipRight = arg2;
		}
		if (clipBottom > arg3) {
			clipBottom = arg3;
		}
		glEnable(GL2.GL_SCISSOR_TEST);
		if (clipLeft <= clipRight && clipTop <= clipBottom) {
			scissor();
		} else {
			glScissor(0, 0, 0, 0);
		}
		GlFont.method1173();
	}

	@OriginalMember(owner = "client!dj", name = "d", descriptor = "()V")
	public static void clear() {
		glClear(GL2.GL_DEPTH_BUFFER_BIT | GL2.GL_COLOR_BUFFER_BIT);
	}

	@OriginalMember(owner = "client!dj", name = "b", descriptor = "(IIIII)V")
	public static void method1185(@OriginalArg(0) int arg0, @OriginalArg(1) int arg1, @OriginalArg(2) int arg2, @OriginalArg(3) int arg3, @OriginalArg(4) int arg4) {
		GlRenderer.setupRenderingWithNoTexture();
		@Pc(5) float local5 = (float) arg0 + 0.3F;
		@Pc(10) float local10 = (float) arg2 + 0.3F;
		@Pc(18) float local18 = (float) GlRenderer.canvasHeight - (float) arg1 - 0.3F;
		@Pc(26) float local26 = (float) GlRenderer.canvasHeight - (float) arg3 - 0.3F;
		glBegin(GL2.GL_LINE_LOOP);
		glColor3f((arg4 >> 16 & 0xFF) / 255.0F, (arg4 >> 8 & 0xFF) / 255.0F, (arg4 & 0xFF) / 255.0F);
		glVertex2f(local5, local18);
		glVertex2f(local10, local26);
		glEnd();
	}

	@OriginalMember(owner = "client!dj", name = "c", descriptor = "(IIIII)V")
	public static void fillRect(@OriginalArg(0) int arg0, @OriginalArg(1) int arg1, @OriginalArg(2) int arg2, @OriginalArg(3) int arg3, @OriginalArg(4) int arg4) {
		GlRenderer.setupRenderingWithNoTexture();
		@Pc(3) float local3 = (float) arg0;
		@Pc(8) float local8 = local3 + (float) arg2;
		@Pc(13) float local13 = (float) (GlRenderer.canvasHeight - arg1);
		@Pc(18) float local18 = local13 - (float) arg3;
		glBegin(GL2.GL_TRIANGLE_FAN);
		glColor3f((arg4 >> 16 & 0xFF) / 255.0F, (arg4 >> 8 & 0xFF) / 255.0F, (arg4 & 0xFF) / 255.0F);
		glVertex2f(local3, local13);
		glVertex2f(local3, local18);
		glVertex2f(local8, local18);
		glVertex2f(local8, local13);
		glEnd();
	}

	@OriginalMember(owner = "client!dj", name = "d", descriptor = "(IIII)V")
	/** glScissor for the current clip; mobile: inside the zoomed panel the clip is in the panel's unzoomed space */
	private static void scissor() {
		@Pc(0) int left = MobileLayout.zoomX(clipLeft);
		@Pc(0) int top = MobileLayout.zoomY(clipTop);
		@Pc(0) int right = Math.min(GlRenderer.canvasWidth, MobileLayout.zoomX(clipRight));
		@Pc(0) int bottom = Math.min(GlRenderer.canvasHeight, MobileLayout.zoomY(clipBottom));
		glScissor((int) (left * GameShell.canvasScale + GameShell.subpixelX), (int) ((GlRenderer.canvasHeight - bottom) * GameShell.canvasScale + GameShell.subpixelY), (int) ((right - left) * GameShell.canvasScale + GameShell.subpixelX), (int) ((bottom - top) * GameShell.canvasScale + GameShell.subpixelY));
	}

	/** mobile: set the current clip again, e.g. after the panel zoom is switched off */
	public static void reapplyClip() {
		setClip(clipLeft, clipTop, clipRight, clipBottom);
	}

	public static void setClip(@OriginalArg(0) int arg0, @OriginalArg(1) int arg1, @OriginalArg(2) int arg2, @OriginalArg(3) int arg3) {
		if (arg0 < 0) {
			arg0 = 0;
		}
		if (arg1 < 0) {
			arg1 = 0;
		}
		if (arg2 > GlRenderer.canvasWidth) {
			arg2 = GlRenderer.canvasWidth;
		}
		if (arg3 > GlRenderer.canvasHeight) {
			arg3 = GlRenderer.canvasHeight;
		}
		clipLeft = arg0;
		clipTop = arg1;
		clipRight = arg2;
		clipBottom = arg3;
		glEnable(GL2.GL_SCISSOR_TEST);
		if (clipLeft <= clipRight && clipTop <= clipBottom) {
			scissor();
		} else {
			glScissor(0, 0, 0, 0);
		}
		GlFont.method1173();
	}

}
