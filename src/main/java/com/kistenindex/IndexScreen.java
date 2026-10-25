package com.kistenindex;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
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
	private static final int WIDTH = 320;
	private static final int ICON_GUTTER = 24;
	private static final int ROW_HEIGHT = 22;
	private static final int LIST_TOP = 100;
	private static final int MENU_WIDTH = 140;
	private static final int MENU_ITEM_HEIGHT = 14;
	// Seit 26.3 (SDL): 1 = linke, 3 = rechte Maustaste
	private static final int BUTTON_LEFT = 1;
	private static final int BUTTON_RIGHT = 3;
	private static final List<String> TYPE_FILTERS = List.of("", "Kiste", "Redstone-Kiste", "Fass", "Shulkerbox",
			"Endertruhe", "Spender", "Werfer", "Ofen", "Hochofen", "Räucherofen");
	private static final Map<String, ItemStack> STACKS = new HashMap<>();

	// Bleiben erhalten, wenn die GUI geschlossen und wieder geöffnet wird
	private static String query = "";
	private static String dimFilter = "";
	private static String typeFilter = "";
	private static ChestStore.SortMode sortMode = ChestStore.SortMode.DISTANCE;
	private static boolean includeNested = true;

	private int page = 0;
	private int left;
	private int rowsPerPage = 8;

	private EditBox searchBox;
	private Button prevButton;
	private Button nextButton;
	private final List<Button> rowButtons = new ArrayList<>();
	private final List<ItemStack> rowStacks = new ArrayList<>();
	private List<ChestStore.Hit> hits = new ArrayList<>();

	// Rechtsklick-Menü
	private ChestStore.Hit menuHit;
	private int menuX;
	private int menuY;

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
		rowsPerPage = Math.max(3, Math.min(8, (this.height - LIST_TOP - 50) / ROW_HEIGHT));
		rowButtons.clear();
		rowStacks.clear();
		menuHit = null;

		searchBox = new EditBox(this.font, left, 24, WIDTH, 20, Component.literal("Suche"));
		searchBox.setHint(Component.literal("Item, Verzauberung, <30% Haltbarkeit"));
		searchBox.setMaxLength(64);
		searchBox.setValue(query);
		searchBox.setResponder(text -> {
			query = text;
			page = 0;
			refresh();
		});
		addRenderableWidget(searchBox);
		setInitialFocus(searchBox);

		int half = WIDTH / 2 - 2;
		Button dimButton = Button.builder(dimensionButtonText(), b -> {
			cycleDimension();
			b.setMessage(dimensionButtonText());
			page = 0;
			refresh();
		}).bounds(left, 48, half, 20).build();
		addRenderableWidget(dimButton);

		Button typeButton = Button.builder(typeButtonText(), b -> {
			cycleType();
			b.setMessage(typeButtonText());
			page = 0;
			refresh();
		}).bounds(left + WIDTH / 2 + 2, 48, half, 20).build();
		addRenderableWidget(typeButton);

		Button sortButton = Button.builder(sortButtonText(), b -> {
			sortMode = sortMode.next();
			b.setMessage(sortButtonText());
			page = 0;
			refresh();
		}).bounds(left, 72, half, 20).build();
		addRenderableWidget(sortButton);

		Button nestedButton = Button.builder(nestedButtonText(), b -> {
			includeNested = !includeNested;
			b.setMessage(nestedButtonText());
			page = 0;
			refresh();
		}).bounds(left + WIDTH / 2 + 2, 72, half, 20).build();
		addRenderableWidget(nestedButton);

		int bottom = LIST_TOP + rowsPerPage * ROW_HEIGHT + 4;
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

	private Component sortButtonText() {
		return Component.literal("Sortierung: " + sortMode.label);
	}

	private Component nestedButtonText() {
		return Component.literal("Shulker-Inhalt: " + (includeNested ? "An" : "Aus"));
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

		hits = ChestStore.search(Minecraft.getInstance(), query, dimFilter, typeFilter, includeNested, sortMode);
		int maxPage = Math.max(0, (hits.size() - 1) / rowsPerPage);
		if (page > maxPage) {
			page = maxPage;
		}
		if (page < 0) {
			page = 0;
		}

		int start = page * rowsPerPage;
		for (int i = 0; i < rowsPerPage && start + i < hits.size(); i++) {
			ChestStore.Hit hit = hits.get(start + i);
			Button row = Button.builder(Component.literal(describe(hit)), b -> select(hit))
					.bounds(left + ICON_GUTTER, LIST_TOP + i * ROW_HEIGHT, WIDTH - ICON_GUTTER, 20).build();
			rowButtons.add(row);
			rowStacks.add(stackFor(hit.item().id));
			addRenderableWidget(row);
		}
		if (prevButton != null) {
			prevButton.active = page > 0;
		}
		if (nextButton != null) {
			nextButton.active = page < maxPage;
		}
	}

	private static String describe(ChestStore.Hit hit) {
		ChestStore.Entry e = hit.entry();
		ChestStore.ItemInfo item = hit.item();
		StringBuilder sb = new StringBuilder();
		if (e.pinned) {
			sb.append("[P] ");
		}
		sb.append(item.count).append("x ").append(item.name);
		int pct = item.durabilityPercent();
		if (pct >= 0) {
			sb.append(" (").append(pct).append("%)");
		}
		if (item.enchants != null && !item.enchants.isEmpty()) {
			sb.append(" [verzaubert]");
		}
		if (item.container != null) {
			sb.append(" in ").append(item.container);
		}
		sb.append(" | ").append(ChestStore.typeLabel(e.type)).append(" | ")
				.append(ChestStore.dimensionLabel(e.dimension)).append(' ').append(e.x).append(' ').append(e.y)
				.append(' ').append(e.z);
		return sb.toString();
	}

	private static List<Component> tooltipFor(ChestStore.Hit hit) {
		ChestStore.Entry e = hit.entry();
		ChestStore.ItemInfo item = hit.item();
		List<Component> lines = new ArrayList<>();
		lines.add(Component.literal(item.count + "x " + item.name));
		if (item.enchants != null && !item.enchants.isEmpty()) {
			lines.add(Component.literal("Verzauberungen: " + item.enchants));
		}
		int pct = item.durabilityPercent();
		if (pct >= 0) {
			lines.add(Component.literal("Haltbarkeit: " + (item.maxDamage - item.damage) + " / " + item.maxDamage
					+ " (" + pct + "%)"));
		}
		if (item.container != null) {
			lines.add(Component.literal("In: " + item.container));
		}
		lines.add(Component.literal(ChestStore.typeLabel(e.type) + ", " + ChestStore.dimensionLabel(e.dimension) + " "
				+ e.x + " " + e.y + " " + e.z));
		lines.add(Component.literal("Zuletzt gesehen: " + ChestStore.ageText(e.updated)));
		lines.add(Component.literal("Rechtsklick: Optionen"));
		return lines;
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

	// ---- Rechtsklick-Menü ----

	/** Zeile (0-basiert auf der aktuellen Seite) unter dem Mauszeiger oder -1. */
	private int rowAt(double mouseX, double mouseY) {
		if (mouseX < left + ICON_GUTTER || mouseX >= left + WIDTH || mouseY < LIST_TOP) {
			return -1;
		}
		int relative = (int) (mouseY - LIST_TOP);
		if (relative % ROW_HEIGHT >= 20) {
			return -1;
		}
		int row = relative / ROW_HEIGHT;
		return row < rowButtons.size() ? row : -1;
	}

	private ChestStore.Hit hitForRow(int row) {
		return hits.get(page * rowsPerPage + row);
	}

	private List<String> menuOptions() {
		List<String> options = new ArrayList<>();
		options.add("Markieren");
		options.add(menuHit.entry().pinned ? "Pin entfernen" : "Anpinnen");
		options.add("Container vergessen");
		options.add("Abbrechen");
		return options;
	}

	private void openMenu(int row, double mouseX, double mouseY) {
		menuHit = hitForRow(row);
		int menuHeight = menuOptions().size() * MENU_ITEM_HEIGHT + 4;
		menuX = (int) Math.max(2, Math.min(mouseX, this.width - MENU_WIDTH - 2));
		menuY = (int) Math.max(2, Math.min(mouseY, this.height - menuHeight - 2));
	}

	private void runMenuAction(int index) {
		Minecraft mc = Minecraft.getInstance();
		ChestStore.Hit hit = menuHit;
		menuHit = null;
		switch (index) {
			case 0 -> select(hit);
			case 1 -> {
				ChestStore.togglePinned(mc, hit.entry());
				refresh();
			}
			case 2 -> {
				ChestStore.Entry e = hit.entry();
				HighlightState.Target target = HighlightState.get();
				if (target != null && target.dimension().equals(e.dimension) && target.x() == e.x
						&& target.y() == e.y && target.z() == e.z) {
					HighlightState.clear();
				}
				ChestStore.remove(mc, e);
				refresh();
			}
			default -> {
			}
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mouseX = event.x();
		double mouseY = event.y();
		int button = event.button();

		if (menuHit != null) {
			if (button == BUTTON_LEFT) {
				int index = (int) Math.floor((mouseY - menuY - 2) / MENU_ITEM_HEIGHT);
				boolean inside = mouseX >= menuX && mouseX < menuX + MENU_WIDTH;
				if (inside && mouseY >= menuY && index >= 0 && index < menuOptions().size()) {
					runMenuAction(index);
				}
			}
			menuHit = null;
			return true;
		}

		if (button == BUTTON_RIGHT) {
			int row = rowAt(mouseX, mouseY);
			if (row >= 0) {
				openMenu(row, mouseX, mouseY);
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);

		// Item-Icons links neben den Treffern
		for (int i = 0; i < rowStacks.size(); i++) {
			graphics.fakeItem(rowStacks.get(i), left + 4, LIST_TOP + i * ROW_HEIGHT + 2);
		}

		String title = "Kisten-Index";
		graphics.text(this.font, title, this.width / 2 - this.font.width(title) / 2, 8, 0xFFFFFFFF, true);

		int maxPage = Math.max(0, (hits.size() - 1) / rowsPerPage);
		String info = hits.isEmpty()
				? "Keine Treffer. Öffne Kisten, damit sie erfasst werden."
				: hits.size() + " Treffer, Seite " + (page + 1) + " von " + (maxPage + 1);
		int bottom = LIST_TOP + rowsPerPage * ROW_HEIGHT + 4;
		graphics.text(this.font, info, this.width / 2 - this.font.width(info) / 2, bottom + 6, 0xFFAAAAAA, true);
		String hint = "Rechtsklick auf einen Treffer: Optionen";
		graphics.text(this.font, hint, this.width / 2 - this.font.width(hint) / 2, bottom + 22, 0xFF777777, true);

		// Tooltip mit Details, solange kein Menü offen ist
		if (menuHit == null) {
			int row = rowAt(mouseX, mouseY);
			if (row >= 0) {
				graphics.setComponentTooltipForNextFrame(this.font, tooltipFor(hitForRow(row)), mouseX, mouseY);
			}
		}

		// Rechtsklick-Menü ganz oben zeichnen
		if (menuHit != null) {
			graphics.nextStratum();
			List<String> options = menuOptions();
			int menuHeight = options.size() * MENU_ITEM_HEIGHT + 4;
			graphics.fill(menuX, menuY, menuX + MENU_WIDTH, menuY + menuHeight, 0xF0101018);
			graphics.outline(menuX, menuY, MENU_WIDTH, menuHeight, 0xFF7A7AB0);
			for (int i = 0; i < options.size(); i++) {
				int itemTop = menuY + 2 + i * MENU_ITEM_HEIGHT;
				boolean hovered = mouseX >= menuX && mouseX < menuX + MENU_WIDTH && mouseY >= itemTop
						&& mouseY < itemTop + MENU_ITEM_HEIGHT;
				if (hovered) {
					graphics.fill(menuX + 1, itemTop, menuX + MENU_WIDTH - 1, itemTop + MENU_ITEM_HEIGHT, 0xFF3A3A60);
				}
				graphics.text(this.font, options.get(i), menuX + 6, itemTop + 3, 0xFFFFFFFF, true);
			}
		}
	}
}
