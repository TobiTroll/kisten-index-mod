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
import java.util.TreeSet;

/** Speichert und durchsucht die erfassten Container, getrennt nach Welt bzw. Server. */
public final class ChestStore {
	public static final class ItemInfo {
		public String id;
		public String name;
		public int count;

		public ItemInfo() {
		}

		public ItemInfo(String id, String name, int count) {
			this.id = id;
			this.name = name;
			this.count = count;
		}
	}

	public static final class Entry {
		public String dimension;
		public int x;
		public int y;
		public int z;
		public String type;
		public long updated;
		public List<ItemInfo> items = new ArrayList<>();
	}

	public record Hit(Entry entry, ItemInfo item) {
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Type LIST_TYPE = new TypeToken<List<Entry>>() {
	}.getType();

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
			ItemInfo x = a.get(i);
			ItemInfo y = b.get(i);
			if (!x.id.equals(y.id) || x.count != y.count) {
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

	/** Sucht Items (Name oder ID enthält den Suchtext), sortiert nach Entfernung. */
	public static synchronized List<Hit> search(Minecraft client, String query, String dimFilter, String typeFilter) {
		List<Hit> hits = new ArrayList<>();
		if (!ensureLoaded(client)) {
			return hits;
		}
		String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
		for (Entry e : entries) {
			if (!dimFilter.isEmpty() && !dimFilter.equals(e.dimension)) {
				continue;
			}
			if (!typeFilter.isEmpty() && !typeFilter.equals(typeLabel(e.type))) {
				continue;
			}
			for (ItemInfo item : e.items) {
				if (q.isEmpty() || item.name.toLowerCase(Locale.ROOT).contains(q)
						|| item.id.toLowerCase(Locale.ROOT).contains(q)) {
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
		hits.sort(Comparator.comparingDouble(h -> {
			Entry e = h.entry();
			if (pdim == null || !pdim.equals(e.dimension)) {
				return 1.0e18;
			}
			double dx = e.x - px;
			double dy = e.y - py;
			double dz = e.z - pz;
			return dx * dx + dy * dy + dz * dz;
		}));
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
}
