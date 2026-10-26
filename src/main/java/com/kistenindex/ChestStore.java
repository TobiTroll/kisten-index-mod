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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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

	/** Sortierung der Container-Ansicht. */
	public enum ContainerSort {
		DISTANCE, AGE, NAME, VARIETY, TOTAL
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

	/**
	 * Ein erfasster Container. Er wird eindeutig durch Dimension und Blockkoordinaten bestimmt; der eigene Name
	 * und die laufende Nummer hängen daran.
	 */
	public static final class Entry {
		public String dimension;
		public int x;
		public int y;
		public int z;
		public String type;
		public long updated;
		public boolean pinned;
		/** Eigener Name, nur in der Mod sichtbar; null oder leer, wenn keiner vergeben wurde. */
		public String name;
		/** Laufende Nummer je Containertyp für den automatischen Namen, z. B. "Kiste #12". */
		public int number;
		public List<ItemInfo> items = new ArrayList<>();
	}

	public record Hit(Entry entry, ItemInfo item) {
	}

	private record PlayerPos(double x, double y, double z, String dimension) {
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
		// Einträge aus älteren Versionen haben noch keine laufende Nummer.
		if (assignMissingNumbers()) {
			save();
		}
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

	/** Vergibt Nummern an Einträge ohne Nummer, getrennt nach Containertyp. */
	private static boolean assignMissingNumbers() {
		Map<String, Integer> highest = new HashMap<>();
		for (Entry e : entries) {
			if (e.number > 0) {
				highest.merge(typeLabel(e.type), e.number, Math::max);
			}
		}
		boolean changed = false;
		for (Entry e : entries) {
			if (e.number <= 0) {
				String label = typeLabel(e.type);
				int next = highest.getOrDefault(label, 0) + 1;
				highest.put(label, next);
				e.number = next;
				changed = true;
			}
		}
		return changed;
	}

	private static int nextNumber(String type) {
		String label = typeLabel(type);
		int highest = 0;
		for (Entry e : entries) {
			if (typeLabel(e.type).equals(label)) {
				highest = Math.max(highest, e.number);
			}
		}
		return highest + 1;
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
			existing.type = type;
			existing.number = nextNumber(type);
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

	/** Vergibt einen eigenen Namen. Ein leerer Name stellt den automatischen Namen wieder her. */
	public static synchronized void rename(Minecraft client, Entry entry, String name) {
		if (!ensureLoaded(client)) {
			return;
		}
		String trimmed = name == null ? "" : name.trim();
		entry.name = trimmed.isEmpty() ? null : trimmed;
		save();
	}

	// ---- Anzeige-Hilfen für Container ----

	/** Automatischer Name, z. B. "Kiste #12". */
	public static String defaultName(Entry e) {
		return typeLabel(e.type) + " #" + Math.max(1, e.number);
	}

	public static boolean hasCustomName(Entry e) {
		return e.name != null && !e.name.isBlank();
	}

	/** Eigener Name, sonst der automatische Name. */
	public static String displayName(Entry e) {
		return hasCustomName(e) ? e.name : defaultName(e);
	}

	/** Anzahl verschiedener Item-Arten im gespeicherten Inhalt. */
	public static int distinctItems(Entry e) {
		Set<String> ids = new HashSet<>();
		for (ItemInfo item : e.items) {
			ids.add(item.id);
		}
		return ids.size();
	}

	/** Gesamtmenge aller gespeicherten Items. */
	public static int totalCount(Entry e) {
		int total = 0;
		for (ItemInfo item : e.items) {
			total += item.count;
		}
		return total;
	}

	/** Item-ID, deren Icon den Container darstellt (der Blockname entspricht dem Item). */
	public static String iconId(Entry e) {
		return "minecraft:" + (e.type == null ? "chest" : e.type);
	}

	// ---- Suche ----

	private static String nullToEmpty(String text) {
		return text == null ? "" : text;
	}

	private static String haystack(ItemInfo item) {
		return (nullToEmpty(item.name) + " " + nullToEmpty(item.id) + " " + nullToEmpty(item.enchants) + " "
				+ nullToEmpty(item.container)).toLowerCase(Locale.ROOT);
	}

	private static List<String> tokens(String query) {
		List<String> tokens = new ArrayList<>();
		if (query != null) {
			for (String token : query.trim().toLowerCase(Locale.ROOT).split("\\s+")) {
				if (!token.isEmpty()) {
					tokens.add(token);
				}
			}
		}
		return tokens;
	}

	/** Prüft ein Haltbarkeits-Wort wie "<30%" oder ">80%" gegen ein Item. */
	private static boolean durabilityMatches(ItemInfo item, Matcher m) {
		int pct = item.durabilityPercent();
		if (pct < 0) {
			return false;
		}
		int limit = Integer.parseInt(m.group(2));
		return m.group(1).equals("<") ? pct < limit : pct > limit;
	}

	/**
	 * Jedes Wort der Suche muss passen: im Namen, in der ID, in den Verzauberungen oder im Shulker-Namen. Ein
	 * Wort wie "<30%" oder ">80%" filtert nach restlicher Haltbarkeit.
	 */
	private static boolean matches(ItemInfo item, List<String> tokens) {
		for (String token : tokens) {
			Matcher m = DURABILITY.matcher(token);
			if (m.matches()) {
				if (!durabilityMatches(item, m)) {
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

	/**
	 * Ein Container passt, wenn jedes Suchwort zu seinem Namen, Typ, seiner Dimension oder zu mindestens einem
	 * gespeicherten Item passt.
	 */
	private static boolean containerMatches(Entry e, List<String> tokens) {
		String base = (displayName(e) + " " + typeLabel(e.type) + " " + dimensionLabel(e.dimension))
				.toLowerCase(Locale.ROOT);
		for (String token : tokens) {
			Matcher m = DURABILITY.matcher(token);
			boolean found = false;
			if (m.matches()) {
				for (ItemInfo item : e.items) {
					if (durabilityMatches(item, m)) {
						found = true;
						break;
					}
				}
			} else if (base.contains(token)) {
				found = true;
			} else {
				for (ItemInfo item : e.items) {
					if (haystack(item).contains(token)) {
						found = true;
						break;
					}
				}
			}
			if (!found) {
				return false;
			}
		}
		return true;
	}

	private static PlayerPos playerPos(Minecraft client) {
		if (client.player == null) {
			return null;
		}
		return new PlayerPos(client.player.getX(), client.player.getY(), client.player.getZ(),
				client.player.level().dimension().identifier().toString());
	}

	/** Quadrat der Entfernung zum Spieler; Container in anderen Dimensionen kommen ans Ende. */
	private static double distanceSq(Entry e, PlayerPos p) {
		if (p == null || !p.dimension().equals(e.dimension)) {
			return 1.0e18;
		}
		double dx = e.x - p.x();
		double dy = e.y - p.y();
		double dz = e.z - p.z();
		return dx * dx + dy * dy + dz * dz;
	}

	/** Sucht Items und sortiert die Treffer. Angepinnte Container stehen immer oben. */
	public static synchronized List<Hit> search(Minecraft client, String query, String dimFilter, String typeFilter,
			boolean includeNested, SortMode sort) {
		List<Hit> hits = new ArrayList<>();
		if (!ensureLoaded(client)) {
			return hits;
		}
		List<String> tokens = tokens(query);
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

		final PlayerPos pos = playerPos(client);
		Comparator<Hit> order = switch (sort) {
			case AMOUNT -> Comparator.comparingInt((Hit h) -> h.item().count).reversed();
			case AGE -> Comparator.comparingLong((Hit h) -> h.entry().updated).reversed();
			default -> Comparator.comparingDouble((Hit h) -> distanceSq(h.entry(), pos));
		};
		Comparator<Hit> pinnedFirst = Comparator.comparing((Hit h) -> !h.entry().pinned);
		hits.sort(pinnedFirst.thenComparing(order));
		return hits;
	}

	/** Liefert jeden passenden Container genau einmal. Angepinnte Container stehen immer oben. */
	public static synchronized List<Entry> searchContainers(Minecraft client, String query, String dimFilter,
			String typeFilter, ContainerSort sort) {
		List<Entry> result = new ArrayList<>();
		if (!ensureLoaded(client)) {
			return result;
		}
		List<String> tokens = tokens(query);
		for (Entry e : entries) {
			if (!dimFilter.isEmpty() && !dimFilter.equals(e.dimension)) {
				continue;
			}
			if (!typeFilter.isEmpty() && !typeFilter.equals(typeLabel(e.type))) {
				continue;
			}
			if (containerMatches(e, tokens)) {
				result.add(e);
			}
		}

		final PlayerPos pos = playerPos(client);
		Comparator<Entry> order = switch (sort) {
			case AGE -> Comparator.comparingLong((Entry e) -> e.updated).reversed();
			case NAME -> Comparator.comparing((Entry e) -> displayName(e), String.CASE_INSENSITIVE_ORDER);
			case VARIETY -> Comparator.comparingInt((Entry e) -> distinctItems(e)).reversed();
			case TOTAL -> Comparator.comparingInt((Entry e) -> totalCount(e)).reversed();
			default -> Comparator.comparingDouble((Entry e) -> distanceSq(e, pos));
		};
		Comparator<Entry> pinnedFirst = Comparator.comparing((Entry e) -> !e.pinned);
		result.sort(pinnedFirst.thenComparing(order));
		return result;
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
