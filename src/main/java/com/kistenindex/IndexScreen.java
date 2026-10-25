package com.kistenindex;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Die Such-GUI des Kisten-Index. */
public class IndexScreen extends Screen {
	private static final int ROWS = 8;
	private static final int WIDTH = 320;
	private static final int ICON_GUTTER = 24;
	private static final List<String> TYPE_FILTERS = List.of("", "Kiste", "Redstone-Kiste", "Fass", "Shulkerbox",
			"Endertruhe");
	private static final Map<String, ItemStack> STACKS = new HashMap<>();

	private String query = "";
	private String dimFilter = "";
	private String typeFilter = "";
	private int page = 0;
	private int left;

	private EditBox searchBox;
	private Button prevButton;
	private Button nextButton;
	private final List<Button> rowButtons = new ArrayList<>();
	private final List<ItemStack> rowStacks = new ArrayList<>();
	private List<ChestStore.Hit> hits = new ArrayList<>();

	public IndexScreen() {
		super(Component.literal("Kisten-Index"));
	}

	private static ItemStack stackFor(String id) {
		return STACKS.computeIfAbsent(id, key -> {
			Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(key));
			return new ItemStack(item);
		});
	}

	@Override
	protected void init() {
		left = this.width / 2 - WIDTH / 2;
		rowButtons.clear();
		rowStacks.clear();

		searchBox = new EditBox(this.font, left, 28, WIDTH, 20, Component.literal("Suche"));
		searchBox.setHint(Component.literal("Item suchen, z. B. Diamant"));
		searchBox.setMaxLength(64);
		searchBox.setValue(query);
		searchBox.setResponder(text -> {
			query = text;
			page = 0;
			refresh();
		});
		addRenderableWidget(searchBox);
		setInitialFocus(searchBox);

		Button dimButton = Button.builder(dimensionButtonText(), b -> {
			cycleDimension();
			b.setMessage(dimensionButtonText());
			page = 0;
			refresh();
		}).bounds(left, 52, WIDTH / 2 - 2, 20).build();
		addRenderableWidget(dimButton);

		Button typeButton = Button.builder(typeButtonText(), b -> {
			cycleType();
			b.setMessage(typeButtonText());
			page = 0;
			refresh();
		}).bounds(left + WIDTH / 2 + 2, 52, WIDTH / 2 - 2, 20).build();
		addRenderableWidget(typeButton);

		int bottom = 80 + ROWS * 22 + 6;
		prevButton = Button.builder(Component.literal("<"), b -> {
			page--;
			refresh();
		}).bounds(left, bottom, 40, 20).build();
		addRenderableWidget(prevButton);

		nextButton = Button.builder(Component.literal(">"), b -> {
			page++;
			refresh();
		}).bounds(left + WIDTH - 40, bottom, 40, 20).build();
		addRenderableWidget(nextButton);

		refresh();
	}

	private Component dimensionButtonText() {
		return Component.literal("Dimension: " + (dimFilter.isEmpty() ? "Alle" : ChestStore.dimensionLabel(dimFilter)));
	}

	private Component typeButtonText() {
		return Component.literal("Typ: " + (typeFilter.isEmpty() ? "Alle" : typeFilter));
	}

	private void cycleDimension() {
		List<String> dims = new ArrayList<>();
		dims.add("");
		dims.addAll(ChestStore.dimensions(Minecraft.getInstance()));
		int index = dims.indexOf(dimFilter);
		dimFilter = dims.get((index + 1) % dims.size());
	}

	private void cycleType() {
		int index = TYPE_FILTERS.indexOf(typeFilter);
		typeFilter = TYPE_FILTERS.get((index + 1) % TYPE_FILTERS.size());
	}

	/** Sucht neu und baut die Trefferliste der aktuellen Seite auf. */
	private void refresh() {
		for (Button b : rowButtons) {
			removeWidget(b);
		}
		rowButtons.clear();
		rowStacks.clear();

		hits = ChestStore.search(Minecraft.getInstance(), query, dimFilter, typeFilter);
		int maxPage = Math.max(0, (hits.size() - 1) / ROWS);
		if (page > maxPage) {
			page = maxPage;
		}
		if (page < 0) {
			page = 0;
		}

		int start = page * ROWS;
		for (int i = 0; i < ROWS && start + i < hits.size(); i++) {
			ChestStore.Hit hit = hits.get(start + i);
			Button row = Button.builder(Component.literal(describe(hit)), b -> select(hit))
					.bounds(left + ICON_GUTTER, 80 + i * 22, WIDTH - ICON_GUTTER, 20).build();
			rowButtons.add(row);
			rowStacks.add(stackFor(hit.item().id));
			addRenderableWidget(row);
		}
		prevButton.active = page > 0;
		nextButton.active = page < maxPage;
	}

	private static String describe(ChestStore.Hit hit) {
		ChestStore.Entry e = hit.entry();
		return hit.item().count + "x " + hit.item().name + " | " + ChestStore.typeLabel(e.type) + " | "
				+ ChestStore.dimensionLabel(e.dimension) + " " + e.x + " " + e.y + " " + e.z;
	}

	/** Klick auf einen Treffer: Container markieren, Koordinaten in den Chat schreiben, GUI schließen. */
	private void select(ChestStore.Hit hit) {
		Minecraft mc = Minecraft.getInstance();
		ChestStore.Entry e = hit.entry();
		HighlightState.set(new HighlightState.Target(e.dimension, e.x, e.y, e.z,
				hit.item().count + "x " + hit.item().name));
		if (mc.player != null) {
			mc.player.sendSystemMessage(Component.literal("Kisten-Index: " + hit.item().count + "x "
					+ hit.item().name + " in " + ChestStore.typeLabel(e.type) + " bei " + e.x + " / " + e.y + " / "
					+ e.z + " (" + ChestStore.dimensionLabel(e.dimension)
					+ "). Markiert, entfernen mit der Markierungs-Taste (Standard J)."));
		}
		this.onClose();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);

		// Item-Icons links neben den Treffern
		for (int i = 0; i < rowStacks.size(); i++) {
			graphics.fakeItem(rowStacks.get(i), left + 4, 80 + i * 22 + 2);
		}

		String title = "Kisten-Index";
		graphics.text(this.font, title, this.width / 2 - this.font.width(title) / 2, 12, 0xFFFFFFFF, true);

		int maxPage = Math.max(0, (hits.size() - 1) / ROWS);
		String info = hits.isEmpty()
				? "Keine Treffer. Öffne Kisten, damit sie erfasst werden."
				: hits.size() + " Treffer, Seite " + (page + 1) + " von " + (maxPage + 1);
		int bottom = 80 + ROWS * 22 + 6;
		graphics.text(this.font, info, this.width / 2 - this.font.width(info) / 2, bottom + 6, 0xFFAAAAAA, true);
	}
}
