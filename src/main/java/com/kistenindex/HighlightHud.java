package com.kistenindex;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Zeichnet eine Markierung für den gewählten Container auf das HUD. Die Position wird selbst auf den
 * Bildschirm projiziert, deshalb ist die Markierung auch durch Wände sichtbar. Liegt der Container
 * außerhalb des Bildes, zeigt ein Marker am Bildschirmrand in seine Richtung.
 */
public final class HighlightHud {
	private static final int COLOR = 0xFFFFD700;
	private static final int FILL = 0x30FFD700;
	private static final int BLACK = 0xFF000000;
	private static final int MARGIN = 24;

	private HighlightHud() {
	}

	public static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		HighlightState.Target target = HighlightState.get();
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (target == null || player == null) {
			return;
		}
		if (!target.dimension().equals(player.level().dimension().identifier().toString())) {
			return;
		}

		float partial = deltaTracker.getGameTimeDeltaPartialTick(true);
		Vec3 eye = player.getEyePosition(partial);
		double yaw = Math.toRadians(player.getViewYRot(partial));
		double pitch = Math.toRadians(player.getViewXRot(partial));

		// Blickrichtung (f), rechts (r) und oben (u) der Kamera
		double fx = -Math.sin(yaw) * Math.cos(pitch);
		double fy = -Math.sin(pitch);
		double fz = Math.cos(yaw) * Math.cos(pitch);
		double rx = -Math.cos(yaw);
		double rz = -Math.sin(yaw);
		double ux = -rz * fy;
		double uy = rz * fx - rx * fz;
		double uz = rx * fy;

		int w = graphics.guiWidth();
		int h = graphics.guiHeight();
		double tanHalfFov = Math.tan(Math.toRadians(mc.options.fov().get()) / 2.0);
		double aspect = (double) w / (double) h;

		// Mittelpunkt des Containers
		double dx = target.x() + 0.5 - eye.x;
		double dy = target.y() + 0.5 - eye.y;
		double dz = target.z() + 0.5 - eye.z;
		double xc = dx * rx + dz * rz;
		double yc = dx * ux + dy * uy + dz * uz;
		double zc = dx * fx + dy * fy + dz * fz;
		long dist = Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz));
		String label = target.label() + " | " + dist + " m";

		if (zc > 0.05) {
			double sx = w / 2.0 + (xc / (zc * tanHalfFov * aspect)) * (w / 2.0);
			double sy = h / 2.0 - (yc / (zc * tanHalfFov)) * (h / 2.0);
			if (sx >= 0 && sx <= w && sy >= 0 && sy <= h) {
				// Rahmen um die acht Ecken des Blocks
				double minX = Double.MAX_VALUE;
				double minY = Double.MAX_VALUE;
				double maxX = -Double.MAX_VALUE;
				double maxY = -Double.MAX_VALUE;
				boolean allInFront = true;
				for (int i = 0; i < 8; i++) {
					double cx = target.x() + (i & 1) - eye.x;
					double cy = target.y() + ((i >> 1) & 1) - eye.y;
					double cz = target.z() + ((i >> 2) & 1) - eye.z;
					double ccx = cx * rx + cz * rz;
					double ccy = cx * ux + cy * uy + cz * uz;
					double ccz = cx * fx + cy * fy + cz * fz;
					if (ccz <= 0.05) {
						allInFront = false;
						break;
					}
					double px = w / 2.0 + (ccx / (ccz * tanHalfFov * aspect)) * (w / 2.0);
					double py = h / 2.0 - (ccy / (ccz * tanHalfFov)) * (h / 2.0);
					minX = Math.min(minX, px);
					minY = Math.min(minY, py);
					maxX = Math.max(maxX, px);
					maxY = Math.max(maxY, py);
				}
				int left;
				int top;
				int width;
				int height;
				if (allInFront) {
					left = (int) Math.floor(minX);
					top = (int) Math.floor(minY);
					width = Math.max(12, (int) Math.ceil(maxX - minX));
					height = Math.max(12, (int) Math.ceil(maxY - minY));
				} else {
					left = (int) sx - 12;
					top = (int) sy - 12;
					width = 24;
					height = 24;
				}
				graphics.fill(left, top, left + width, top + height, FILL);
				graphics.outline(left - 1, top - 1, width + 2, height + 2, BLACK);
				graphics.outline(left, top, width, height, COLOR);

				int textWidth = mc.font.width(label);
				int textX = clamp(left + width / 2 - textWidth / 2, 4, w - textWidth - 4);
				int textY = top >= 14 ? top - 12 : top + height + 4;
				graphics.text(mc.font, label, textX, textY, COLOR, true);
				return;
			}
		}

		// Außerhalb des Bildes oder hinter dir: Marker am Rand in Richtung des Containers
		double sdx = xc;
		double sdy = -yc;
		double len = Math.hypot(sdx, sdy);
		if (len < 1.0e-6) {
			sdx = 0;
			sdy = 1;
			len = 1;
		}
		sdx /= len;
		sdy /= len;
		double halfW = w / 2.0 - MARGIN;
		double halfH = h / 2.0 - MARGIN;
		double scaleX = Math.abs(sdx) < 1.0e-6 ? Double.MAX_VALUE : halfW / Math.abs(sdx);
		double scaleY = Math.abs(sdy) < 1.0e-6 ? Double.MAX_VALUE : halfH / Math.abs(sdy);
		double scale = Math.min(scaleX, scaleY);
		int ex = (int) Math.round(w / 2.0 + sdx * scale);
		int ey = (int) Math.round(h / 2.0 + sdy * scale);

		graphics.fill(ex - 6, ey - 6, ex + 6, ey + 6, BLACK);
		graphics.fill(ex - 5, ey - 5, ex + 5, ey + 5, COLOR);

		int textWidth = mc.font.width(label);
		int textX = clamp(ex - textWidth / 2, 4, w - textWidth - 4);
		int textY = ey > h / 2 ? ey - 20 : ey + 10;
		graphics.text(mc.font, label, textX, textY, COLOR, true);
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(value, Math.max(min, max)));
	}
}
