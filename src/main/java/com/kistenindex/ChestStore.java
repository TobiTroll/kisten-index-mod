package com.kistenindex;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Speichert und durchsucht die erfassten Container, getrennt nach Welt bzw. Server. */
public final class ChestStore {
	public enum SortMode {
		DISTANCE("Entfernung"), AMOUNT("Menge"), AGE("Alter");

		public final String label;

		SortMode(String label) {
			this.label = label;
		}

		public SortMode next() {
			return values()[(ordinal() + 1) % values().length];
		}
	}

	public static final class ItemInfo {
		public String id;
		public String name;
		public int count;
		/** Verzauberungen als Text, z. B. "Schärfe V, Haltbarkeit III"; leer ohne Verzauberung. */
		public String enchants;
		public int damage;
		/** 0, wenn das Item keine Haltbarkeit hat. */
		public int maxDamage;
		/** Name der Shulkerbox, in der das Item liegt; null, wenn es direkt im Container liegt. */
		public String container;

		public ItemInfo() {
		}

		public ItemInfo(String id, String name, int count, String enchants, int damage, int maxDamage,
				String container) {
			this.id = id;
			this.name = name;
			this.count = count;
			this.enchants = enchants;
			this.damage = damage;
			this.maxDamage = maxDamage;
			this.container = container;
		}

		/** Restliche Haltbarkeit in Prozent oder -1, wenn das Item keine Haltbarkeit hat. */
		public int durabilityPercent() {
			if (maxDamage <= 0) {
				return -1;
			}
			return Math.max(0, (maxDamage - damage) * 100 / maxDamage);
		}

		public boolean sameAs(ItemInfo other) {
			return Objects.equals(id, other.id) && Objects.equals(name, other.name) && count == other.count
					&& Objects.equals(enchants, other.enchants) && damage == other.damage
					&& maxDamage == other.maxDamage && Objects.equals(container, other.container);
		}
	}

	public static final class Entry {
		public String dimension;
		public int x;
		public int y;
		public int z;
		public String type;
		public long updated;
		public boolean pinned;
		public List<ItemInfo> items = new ArrayList<>();
	}

	public record Hit(Entry entry, ItemInfo item) {
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Type LIST_TYPE = new TypeToken<List<Entry>>() {
	}.getType();
	private static final Pattern DURABILITY = Pattern.compile("^([<>])(\\d{1,3})%?$");

	private static List<Entry> entries = new ArrayList<>();
	private static String loadedKey = null;

	private ChestStore() {
	}

	private static String sanitize(String text) {
		return text.replaceAll("[^a-zA-Z0-9._-]", "_");
	}

	/** Eindeutiger Name der aktuellen Welt bzw. des aktuellen Servers, oder null. */
	private static String worldKey(Minecraft client) {
		if (client.getCurrentServer() != null) {
			return "server_" + sanitize(client.getCurrentServer().ip);
		}
		if (client.getSingleplayerServer() != null) {
			return "welt_" + sanitize(client.getSingleplayerServer().getWorldData().getLevelName());
		}
		return null;
	}

	private static Path file(String key) {
		return FabricLoader.getInstance().getConfigDir().resolve("kistenindex").resolve(key + ".json");
	}

	private static synchronized boolean ensureLoaded(Minecraft client) {
		String key = worldKey(client);
		if (key == null) {
			return false;
		}
		if (key.equals(loadedKey)) {
			return true;
		}
		entries = new ArrayList<>();
		Path path = file(key);
		if (Files.exists(path)) {
			try {
				List<Entry> loaded = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), LIST_TYPE);
				if (loaded != null) {
					entries = loaded;
				}
			} catch (IOException | RuntimeException e) {
				KistenIndexClient.LOGGER.warn("Kisten-Index konnte nicht geladen werden", e);
			}
		}
		loadedKey = key;
		return true;
	}

	private static void save() {
		if (loadedKey == null) {
			return;
		}
		try {
			Path path = file(loadedKey);
			Files.createDirectories(path.getParent());
			Files.writeString(path, GSON.toJson(entries, LIST_TYPE), StandardCharsets.UTF_8);
		} catch (IOException e) {
			KistenIndexClient.LOGGER.warn("Kisten-Index konnte nicht gespeichert werden", e);
		}
	}

	private static boolean sameItems(List<ItemInfo> a, List<ItemInfo> b) {
		if (a.size() != b.size()) {
			return false;
		}
		for (int i = 0; i < a.size(); i++) {
			if (!a.get(i).sameAs(b.get(i))) {
				return false;
			}
		}
		return true;
	}

	/** Legt einen Container neu an oder aktualisiert ihn. Speichert nur bei Änderungen. */
	public static synchronized void update(Minecraft client, String dimension, BlockPos pos, String type,
			List<ItemInfo> items) {
		if (!ensureLoaded(client)) {
			return;
		}
		Entry existing = null;
		for (Entry e : entries) {
			if (e.dimension.equals(dimension) && e.x == pos.getX() && e.y == pos.getY() && e.z == pos.getZ()) {
				existing = e;
				break;
			}
		}
		if (existing != null && sameItems(existing.items, items)) {
			return;
		}
		if (existing == null) {
			existing = new Entry();
			existing.dimension = dimension;
			existing.x = pos.getX();
			existing.y = pos.getY();
			existing.z = pos.getZ();
			entries.add(existing);
		}
		existing.type = type;
		existing.items = items;
		existing.updated = System.currentTimeMillis();
		save();
	}

	/** Schaltet die Markierung "angepinnt" für einen Container um. */
	public static synchronized void togglePinned(Minecraft client, Entry entry) {
		if (ensureLoaded(client)) {
			entry.pinned = !entry.pinned;
			save();
		}
	}

	/** Vergisst einen Container, er taucht erst nach erneutem Öffnen wieder im Index auf. */
	public static synchronized void remove(Minecraft client, Entry entry) {
		if (ensureLoaded(client) && entries.remove(entry)) {
			save();
		}
	}

	private static String nullToEmpty(String text) {
		return text == null ? "" : text;
	}

	private static String haystack(ItemInfo item) {
		return (nullToEmpty(item.name) + " " + nullToEmpty(item.id) + " " + nullToEmpty(item.enchants) + " "
				+ nullToEmpty(item.container)).toLowerCase(Locale.ROOT);
	}

	/**
	 * Jedes Wort der Suche muss passen: im Namen, in der ID, in den Verzauberungen oder im Shulker-Namen. Ein
	 * Wort wie "<30%" oder ">80%" filtert nach restlicher Haltbarkeit.
	 */
	private static boolean matches(ItemInfo item, List<String> tokens) {
		for (String token : tokens) {
			Matcher m = DURABILITY.matcher(token);
			if (m.matches()) {
				int pct = item.durabilityPercent();
				if (pct < 0) {
					return false;
				}
				int limit = Integer.parseInt(m.group(2));
				boolean ok = m.group(1).equals("<") ? pct < limit : pct > limit;
				if (!ok) {
					return false;
				}
				continue;
			}
			if (!haystack(item).contains(token)) {
				return false;
			}
		}
		return true;
	}

	/** Sucht Items und sortiert die Treffer. Angepinnte Container stehen immer oben. */
	public static synchronized List<Hit> search(Minecraft client, String query, String dimFilter, String typeFilter,
			boolean includeNested, SortMode sort) {
		List<Hit> hits = new ArrayList<>();
		if (!ensureLoaded(client)) {
			return hits;
		}
		List<String> tokens = new ArrayList<>();
		if (query != null) {
			for (String token : query.trim().toLowerCase(Locale.ROOT).split("\\s+")) {
				if (!token.isEmpty()) {
					tokens.add(token);
				}
			}
		}
		for (Entry e : entries) {
			if (!dimFilter.isEmpty() && !dimFilter.equals(e.dimension)) {
				continue;
			}
			if (!typeFilter.isEmpty() && !typeFilter.equals(typeLabel(e.type))) {
				continue;
			}
			for (ItemInfo item : e.items) {
				if (!includeNested && item.container != null) {
					continue;
				}
				if (matches(item, tokens)) {
					hits.add(new Hit(e, item));
				}
			}
		}

		final double px;
		final double py;
		final double pz;
		final String pdim;
		if (client.player != null) {
			px = client.player.getX();
			py = client.player.getY();
			pz = client.player.getZ();
			pdim = client.player.level().dimension().identifier().toString();
		} else {
			px = 0;
			py = 0;
			pz = 0;
			pdim = null;
		}

		Comparator<Hit> order = switch (sort) {
			case AMOUNT -> Comparator.comparingInt((Hit h) -> h.item().count).reversed();
			case AGE -> Comparator.comparingLong((Hit h) -> h.entry().updated).reversed();
			default -> Comparator.comparingDouble((Hit h) -> {
				Entry e = h.entry();
				if (pdim == null || !pdim.equals(e.dimension)) {
					return 1.0e18;
				}
				double dx = e.x - px;
				double dy = e.y - py;
				double dz = e.z - pz;
				return dx * dx + dy * dy + dz * dz;
			});
		};
		Comparator<Hit> pinnedFirst = Comparator.comparing((Hit h) -> !h.entry().pinned);
		hits.sort(pinnedFirst.thenComparing(order));
		return hits;
	}

	/** Alle Dimensionen, in denen bereits Container erfasst wurden. */
	public static synchronized List<String> dimensions(Minecraft client) {
		if (!ensureLoaded(client)) {
			return new ArrayList<>();
		}
		TreeSet<String> set = new TreeSet<>();
		for (Entry e : entries) {
			set.add(e.dimension);
		}
		return new ArrayList<>(set);
	}

	public static String typeLabel(String blockPath) {
		if (blockPath == null) {
			return "Container";
		}
		if (blockPath.contains("shulker_box")) {
			return "Shulkerbox";
		}
		return switch (blockPath) {
			case "chest" -> "Kiste";
			case "trapped_chest" -> "Redstone-Kiste";
			case "barrel" -> "Fass";
			case "ender_chest" -> "Endertruhe";
			case "dispenser" -> "Spender";
			case "dropper" -> "Werfer";
			case "furnace" -> "Ofen";
			case "blast_furnace" -> "Hochofen";
			case "smoker" -> "Räucherofen";
			default -> "Container";
		};
	}

	public static String dimensionLabel(String dimensionId) {
		if (dimensionId == null) {
			return "?";
		}
		return switch (dimensionId) {
			case "minecraft:overworld" -> "Oberwelt";
			case "minecraft:the_nether" -> "Nether";
			case "minecraft:the_end" -> "Ende";
			default -> dimensionId;
		};
	}

	/** Zeit seit dem letzten Scan als kurzer Text. */
	public static String ageText(long updated) {
		long seconds = Math.max(0, (System.currentTimeMillis() - updated) / 1000);
		if (seconds < 60) {
			return "gerade eben";
		}
		if (seconds < 3600) {
			return "vor " + (seconds / 60) + " Min.";
		}
		if (seconds < 86400) {
			return "vor " + (seconds / 3600) + " Std.";
		}
		return "vor " + (seconds / 86400) + " Tg.";
	}
}
