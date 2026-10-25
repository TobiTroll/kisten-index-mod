package com.kistenindex;

/** Merkt sich den Container, der gerade markiert werden soll. */
public final class HighlightState {
	public record Target(String dimension, int x, int y, int z, String label) {
	}

	private static volatile Target target;

	private HighlightState() {
	}

	public static Target get() {
		return target;
	}

	public static void set(Target newTarget) {
		target = newTarget;
	}

	public static void clear() {
		target = null;
	}
}
