package com.kistenindex;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Sprache der Mod (Deutsch oder Englisch). Die Auswahl wird in config/kistenindex/language.txt gespeichert
 * und bleibt nach einem Neustart erhalten. Ohne Datei ist Deutsch eingestellt.
 */
public final class Lang {
	public enum Language {
		DE, EN
	}

	private static Language current = load();

	private Lang() {
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("kistenindex").resolve("language.txt");
	}

	private static Language load() {
		try {
			Path path = file();
			if (Files.exists(path)) {
				String text = Files.readString(path, StandardCharsets.UTF_8).trim().toLowerCase(Locale.ROOT);
				if (text.equals("en")) {
					return Language.EN;
				}
			}
		} catch (IOException | RuntimeException e) {
			KistenIndexClient.LOGGER.warn("Sprache konnte nicht geladen werden", e);
		}
		return Language.DE;
	}

	private static void save() {
		try {
			Path path = file();
			Files.createDirectories(path.getParent());
			Files.writeString(path, current == Language.EN ? "en" : "de", StandardCharsets.UTF_8);
		} catch (IOException e) {
			KistenIndexClient.LOGGER.warn("Sprache konnte nicht gespeichert werden", e);
		}
	}

	public static Language get() {
		return current;
	}

	/** Name der aktuellen Sprache in dieser Sprache. */
	public static String languageName() {
		return current == Language.EN ? "English" : "Deutsch";
	}

	/** Wechselt zwischen Deutsch und Englisch und speichert die Auswahl. */
	public static void toggle() {
		current = current == Language.EN ? Language.DE : Language.EN;
		save();
	}

	/** Liefert je nach eingestellter Sprache den deutschen oder den englischen Text. */
	public static String t(String de, String en) {
		return current == Language.EN ? en : de;
	}
}
