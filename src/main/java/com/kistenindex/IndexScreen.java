package com.kistenindex;

import com.mojang.blaze3d.platform.ClipboardManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;

/** Die Such-GUI des Kisten-Index im modernen, dunklen Design. */
public class IndexScreen extends Screen {
	private static final int PANEL_W = 360;
	private static final int PAD = 12;
	private static final int ROW_H = 26;
	private static final int ROW_PITCH = 30;
	private static final int LIST_OFFSET = 98;
	private static final int POPUP_ITEM_H = 18;
	private static final int TOGGLE_W = 150;
	// Seit 26.3 (SDL): 1 = linke, 3 = rechte Maustaste
	private static final int BUTTON_LEFT = 1;
	private static final int BUTTON_RIGHT = 3;

	// Farben (ARGB)
	private static final int BG_PANEL = 0xF2101726;
	private static final int BORDER = 0xFF263149;
	private static final int FIELD = 0xFF0C1220;
	private static final int FIELD_BORDER = 0xFF222B40;
	private static final int ROW = 0xFF121A2B;
	private static final int ROW_BORDER = 0xFF18213A;
	private static final int ROW_HOVER = 0xFF16213D;
	private static final int TILE = 0xFF1B2538;
	private static final int PILL = 0xFF1B2538;
	private static final int TOGGLE_ACTIVE = 0xFF17294F;
	private static final int ACCENT = 0xFF3B82F6;
	private static final int TEXT = 0xFFE6EAF2;
	private static final int TEXT_SOFT = 0xFFB4BDD0;
	private static final int MUTED = 0xFF8A94A8;
	private static final int DIM = 0xFF4A546B;
	private static final int GOLD = 0xFFFFC857;
	private static final int PURPLE = 0xFFD9A7FF;
	private static final int GREEN = 0xFF7BD88F;
	private static final int YELLOW = 0xFFF2D06B;
	private static final int RED = 0xFFF47A7A;

	private static final List<String> TYPE_FILTERS = List.of("", "Kiste", "Redstone-Kiste", "Fass", "Shulkerbox",
			"Endertruhe", "Spender", "Werfer", "Ofen", "Hochofen", "Räucherofen");
	private static final List<String> SORT_LABELS = List.of("Entfernung", "Menge", "Alter", "Name A-Z");
	private static final List<String> CONTAINER_SORT_LABELS = List.of("Entfernung", "Alter", "Name A-Z",
			"Item-Arten", "Gesamtmenge");
	private static final Map<String, ItemStack> STACKS = new HashMap<>();

	// Bleiben erhalten, wenn die GUI geschlossen und wieder geöffnet wird
	private static String query = "";
	private static String dimFilter = "";
	private static String typeFilter = "";
	private static int sortIndex = 0;
	private static int containerSortIndex = 0;
	private static boolean includeNested = true;

	private enum View {
		ITEMS, CONTAINERS, DETAIL
	}

	private record Rect(int x, int y, int w, int h) {
		boolean contains(double px, double py) {
			return px >= x && px < x + w && py >= y && py < y + h;
		}
	}

	private record Seg(String text, int color) {
	}

	// Die Item-Ansicht ist der Standard.
	private View view = View.ITEMS;

	private Rect panel;
	private Rect searchRect;
	private Rect gearRect;
	private Rect viewItemsRect;
	private Rect viewContainersRect;
	private Rect backRect;
	private Rect dimRect;
	private Rect typeRect;
	private Rect sortRect;
	private Rect actionMarkRect;
	private Rect actionCopyRect;
	private Rect actionRenameRect;
	private Rect prevRect;
	private Rect nextRect;
	private int listTop;
	private int rowsPerPage = 8;

	// Seite der Item- bzw. Containerliste und Seite der Detailansicht (getrennt, damit die Liste erhalten bleibt)
	private int page = 0;
	private int detailPage = 0;

	private EditBox searchBox;
	private List<ChestStore.Hit> hits = new ArrayList<>();
	private List<ChestStore.Entry> containerHits = new ArrayList<>();
	private ChestStore.Entry detailEntry;
	private List<ChestStore.ItemInfo> detailItems = new ArrayList<>();

	// Kurze Bestätigung nach dem Kopieren der Koordinaten
	private long copyFeedbackUntil = 0;

	// Aufklappmenü (Dropdowns, Rechtsklick-Menüs, Zahnrad-Menü)
	private List<String> popupOptions;
	private int popupChecked = -1;
	private IntConsumer popupAction;
	private int popupX;
	private int popupY;
	private int popupW;

	// Eingabefenster zum Umbenennen
	private boolean renameOpen;
	private ChestStore.Entry renameEntry;
	private EditBox renameBox;
	private Rect dialogRect;
	private Rect renameFieldRect;
	private Rect dialogSaveRect;
	private Rect dialogCancelRect;

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
		rowsPerPage = Math.max(3, Math.min(8, (this.height - 136 - 16) / ROW_PITCH));
		int panelH = 136 + rowsPerPage * ROW_PITCH;
		int x0 = this.width / 2 - PANEL_W / 2;
		int y0 = Math.max(8, (this.height - panelH) / 2);

		panel = new Rect(x0, y0, PANEL_W, panelH);
		gearRect = new Rect(x0 + PANEL_W - PAD - 20, y0 + 10, 20, 20);
		int toggleX = x0 + PANEL_W - PAD - 20 - 6 - TOGGLE_W;
		viewItemsRect = new Rect(toggleX, y0 + 10, TOGGLE_W / 2, 20);
		viewContainersRect = new Rect(toggleX + TOGGLE_W / 2, y0 + 10, TOGGLE_W / 2, 20);
		backRect = new Rect(x0 + PANEL_W - PAD - 20 - 6 - 90, y0 + 10, 90, 20);
		searchRect = new Rect(x0 + PAD, y0 + 38, PANEL_W - 2 * PAD, 24);
		int controlsY = y0 + 68;
		dimRect = new Rect(x0 + PAD, controlsY, 100, 22);
		typeRect = new Rect(x0 + PAD + 106, controlsY, 100, 22);
		sortRect = new Rect(x0 + PANEL_W - PAD - 124, controlsY, 124, 22);
		actionMarkRect = new Rect(x0 + PAD, controlsY, 96, 22);
		actionCopyRect = new Rect(x0 + PAD + 102, controlsY, 136, 22);
		actionRenameRect = new Rect(x0 + PAD + 244, controlsY, 88, 22);
		listTop = y0 + LIST_OFFSET;
		int footerY = listTop + rowsPerPage * ROW_PITCH + 4;
		prevRect = new Rect(x0 + PAD, footerY, 56, 22);
		nextRect = new Rect(x0 + PANEL_W - PAD - 56, footerY, 56, 22);

		// Das Suchfeld wird ohne eigenen Rahmen in das gezeichnete Feld gesetzt.
		searchBox = new EditBox(this.font, searchRect.x() + 28, searchRect.y() + 8, searchRect.w() - 38, 10,
				Component.literal("Suche"));
		searchBox.setBordered(false);
		searchBox.setHint(Component.literal("Suchen ..."));
		searchBox.setMaxLength(64);
		searchBox.setValue(query);
		searchBox.setResponder(text -> {
			query = text;
			page = 0;
			refresh();
		});
		addWidget(searchBox);
		if (view == View.DETAIL) {
			setFocused(null);
		} else {
			setInitialFocus(searchBox);
		}

		popupOptions = null;
		renameOpen = false;
		renameBox = null;
		refresh();
	}

	/** Baut die Liste der aktuellen Ansicht neu auf und begrenzt die Seitenzahl. */
	private void refresh() {
		Minecraft mc = Minecraft.getInstance();
		switch (view) {
			case ITEMS -> {
				ChestStore.SortMode mode = ChestStore.SortMode.values()[Math.min(sortIndex, 2)];
				hits = ChestStore.search(mc, query, dimFilter, typeFilter, includeNested, mode);
				if (sortIndex == 3) {
					Comparator<ChestStore.Hit> byName = Comparator.comparing(
							(ChestStore.Hit h) -> h.item().name == null ? "" : h.item().name,
							String.CASE_INSENSITIVE_ORDER);
					Comparator<ChestStore.Hit> pinnedFirst = Comparator
							.comparing((ChestStore.Hit h) -> !h.entry().pinned);
					hits.sort(pinnedFirst.thenComparing(byName));
				}
			}
			case CONTAINERS -> containerHits = ChestStore.searchContainers(mc, query, dimFilter, typeFilter,
					ChestStore.ContainerSort.values()[containerSortIndex]);
			case DETAIL -> {
				detailItems = new ArrayList<>(detailEntry.items);
				detailItems.sort(Comparator.comparing(
						(ChestStore.ItemInfo i) -> i.name == null ? "" : i.name, String.CASE_INSENSITIVE_ORDER));
			}
		}
		setActivePage(Math.max(0, Math.min(activePage(), maxPage())));
	}

	private int rowCount() {
		return switch (view) {
			case ITEMS -> hits.size();
			case CONTAINERS -> containerHits.size();
			case DETAIL -> detailItems.size();
		};
	}

	private int activePage() {
		return view == View.DETAIL ? detailPage : page;
	}

	private void setActivePage(int value) {
		if (view == View.DETAIL) {
			detailPage = value;
		} else {
			page = value;
		}
	}

	private int maxPage() {
		return Math.max(0, (rowCount() - 1) / rowsPerPage);
	}

	private int visibleRows() {
		return Math.max(0, Math.min(rowsPerPage, rowCount() - activePage() * rowsPerPage));
	}

	private Rect rowRect(int row) {
		return new Rect(panel.x() + PAD, listTop + row * ROW_PITCH, PANEL_W - 2 * PAD, ROW_H);
	}

	private int rowAt(double mouseX, double mouseY) {
		for (int i = 0; i < visibleRows(); i++) {
			if (rowRect(i).contains(mouseX, mouseY)) {
				return i;
			}
		}
		return -1;
	}

	private ChestStore.Hit hitForRow(int row) {
		return hits.get(page * rowsPerPage + row);
	}

	private ChestStore.Entry containerForRow(int row) {
		return containerHits.get(page * rowsPerPage + row);
	}

	private ChestStore.ItemInfo detailItemForRow(int row) {
		return detailItems.get(detailPage * rowsPerPage + row);
	}

	// ---- Aktionen ----

	/** Schließt die ganze GUI (onClose ist für "Zurück" bei ESC überschrieben). */
	private void closeGui() {
		super.onClose();
	}

	/** ESC: erst Eingabefenster, dann Menü, dann Detailansicht schließen, zuletzt die GUI. */
	@Override
	public void onClose() {
		if (renameOpen) {
			closeRename();
		} else if (popupOptions != null) {
			closePopup();
		} else if (view == View.DETAIL) {
			backToContainers();
		} else {
			closeGui();
		}
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
		closeGui();
	}

	/** Markiert einen ganzen Container in der Welt und schließt die GUI. */
	private void markContainer(ChestStore.Entry e) {
		Minecraft mc = Minecraft.getInstance();
		String name = ChestStore.displayName(e);
		HighlightState.set(new HighlightState.Target(e.dimension, e.x, e.y, e.z, name));
		if (mc.player != null) {
			mc.player.sendSystemMessage(Component.literal("Kisten-Index: " + name + " bei " + e.x + " / " + e.y
					+ " / " + e.z + " (" + ChestStore.dimensionLabel(e.dimension)
					+ "). Markiert, entfernen mit der Markierungs-Taste (Standard J)."));
		}
		closeGui();
	}

	/** Vergisst einen Container und entfernt eine passende Markierung. */
	private void forgetContainer(ChestStore.Entry e) {
		HighlightState.Target target = HighlightState.get();
		if (target != null && target.dimension().equals(e.dimension) && target.x() == e.x && target.y() == e.y
				&& target.z() == e.z) {
			HighlightState.clear();
		}
		ChestStore.remove(Minecraft.getInstance(), e);
		refresh();
	}

	private void copyCoordinates(ChestStore.Entry e) {
		new ClipboardManager().setClipboard(e.x + " " + e.y + " " + e.z);
		copyFeedbackUntil = System.currentTimeMillis() + 1500;
	}

	private void switchView(View target) {
		if (view == target) {
			return;
		}
		view = target;
		page = 0;
		refresh();
	}

	private void openDetail(ChestStore.Entry e) {
		detailEntry = e;
		detailPage = 0;
		view = View.DETAIL;
		setFocused(null);
		refresh();
	}

	/** Zurück zur Containerliste. Suche, Filter, Sortierung und Seite bleiben erhalten. */
	private void backToContainers() {
		detailEntry = null;
		view = View.CONTAINERS;
		setFocused(searchBox);
		refresh();
	}

	// ---- Aufklappmenüs ----

	private void openPopup(int x, int y, int width, List<String> options, int checked, IntConsumer action) {
		int height = options.size() * POPUP_ITEM_H + 8;
		popupOptions = options;
		popupChecked = checked;
		popupAction = action;
		popupW = width;
		popupX = Math.max(2, Math.min(x, this.width - width - 2));
		popupY = Math.max(2, Math.min(y, this.height - height - 2));
	}

	private void closePopup() {
		popupOptions = null;
		popupAction = null;
	}

	private void openDimensionMenu() {
		Minecraft mc = Minecraft.getInstance();
		List<String> dims = new ArrayList<>();
		dims.add("");
		dims.addAll(ChestStore.dimensions(mc));
		List<String> labels = new ArrayList<>();
		for (String dim : dims) {
			labels.add(dim.isEmpty() ? "Alle Dimensionen" : ChestStore.dimensionLabel(dim));
		}
		openPopup(dimRect.x(), dimRect.y() + dimRect.h() + 2, 130, labels, Math.max(0, dims.indexOf(dimFilter)),
				index -> {
					dimFilter = dims.get(index);
					page = 0;
					refresh();
				});
	}

	private void openTypeMenu() {
		List<String> labels = new ArrayList<>();
		for (String type : TYPE_FILTERS) {
			labels.add(type.isEmpty() ? "Alle Typen" : type);
		}
		openPopup(typeRect.x(), typeRect.y() + typeRect.h() + 2, 130, labels,
				Math.max(0, TYPE_FILTERS.indexOf(typeFilter)), index -> {
					typeFilter = TYPE_FILTERS.get(index);
					page = 0;
					refresh();
				});
	}

	/** Die Sortieroptionen hängen von der Ansicht ab. */
	private void openSortMenu() {
		boolean containers = view == View.CONTAINERS;
		List<String> labels = containers ? CONTAINER_SORT_LABELS : SORT_LABELS;
		int checked = containers ? containerSortIndex : sortIndex;
		openPopup(sortRect.x() + sortRect.w() - 130, sortRect.y() + sortRect.h() + 2, 130, labels, checked,
				index -> {
					if (containers) {
						containerSortIndex = index;
					} else {
						sortIndex = index;
					}
					page = 0;
					refresh();
				});
	}

	private void openGearMenu() {
		List<String> options = List.of("Shulker-Inhalt: " + (includeNested ? "An" : "Aus"), "Filter zurücksetzen",
				"Schließen");
		openPopup(gearRect.x() + gearRect.w() - 150, gearRect.y() + gearRect.h() + 2, 150, options, -1, index -> {
			switch (index) {
				case 0 -> {
					includeNested = !includeNested;
					page = 0;
					refresh();
				}
				case 1 -> {
					dimFilter = "";
					typeFilter = "";
					page = 0;
					searchBox.setValue("");
					refresh();
				}
				default -> closeGui();
			}
		});
	}

	private void openContextMenu(int row, double mouseX, double mouseY) {
		ChestStore.Hit hit = hitForRow(row);
		List<String> options = List.of("Markieren", hit.entry().pinned ? "Pin entfernen" : "Anpinnen",
				"Container vergessen");
		openPopup((int) mouseX, (int) mouseY, 140, options, -1, index -> {
			Minecraft mc = Minecraft.getInstance();
			switch (index) {
				case 0 -> select(hit);
				case 1 -> {
					ChestStore.togglePinned(mc, hit.entry());
					refresh();
				}
				case 2 -> forgetContainer(hit.entry());
				default -> {
				}
			}
		});
	}

	private void openContainerMenu(int row, double mouseX, double mouseY) {
		ChestStore.Entry e = containerForRow(row);
		List<String> options = List.of("Details ansehen", "Markieren", "Umbenennen",
				e.pinned ? "Pin entfernen" : "Anpinnen", "Container vergessen");
		openPopup((int) mouseX, (int) mouseY, 150, options, -1, index -> {
			Minecraft mc = Minecraft.getInstance();
			switch (index) {
				case 0 -> openDetail(e);
				case 1 -> markContainer(e);
				case 2 -> openRename(e);
				case 3 -> {
					ChestStore.togglePinned(mc, e);
					refresh();
				}
				case 4 -> forgetContainer(e);
				default -> {
				}
			}
		});
	}

	// ---- Eingabefenster zum Umbenennen ----

	private void openRename(ChestStore.Entry e) {
		renameEntry = e;
		renameOpen = true;
		int dw = 240;
		int dh = 96;
		dialogRect = new Rect(this.width / 2 - dw / 2, this.height / 2 - dh / 2, dw, dh);
		renameFieldRect = new Rect(dialogRect.x() + 12, dialogRect.y() + 32, dw - 24, 24);
		int buttonY = dialogRect.y() + dh - 32;
		dialogSaveRect = new Rect(dialogRect.x() + 12, buttonY, 104, 22);
		dialogCancelRect = new Rect(dialogRect.x() + dw - 12 - 104, buttonY, 104, 22);

		renameBox = new EditBox(this.font, renameFieldRect.x() + 8, renameFieldRect.y() + 8,
				renameFieldRect.w() - 16, 10, Component.literal("Name"));
		renameBox.setBordered(false);
		renameBox.setMaxLength(32);
		renameBox.setHint(Component.literal(ChestStore.defaultName(e)));
		renameBox.setValue(e.name == null ? "" : e.name);
		addWidget(renameBox);
		setFocused(renameBox);
	}

	private void closeRename() {
		if (renameBox != null) {
			removeWidget(renameBox);
		}
		renameBox = null;
		renameOpen = false;
		setFocused(view == View.DETAIL ? null : searchBox);
	}

	private void saveRename() {
		ChestStore.rename(Minecraft.getInstance(), renameEntry, renameBox.getValue());
		closeRename();
		refresh();
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mouseX = event.x();
		double mouseY = event.y();
		int button = event.button();

		if (renameOpen) {
			if (button == BUTTON_LEFT) {
				if (dialogSaveRect.contains(mouseX, mouseY)) {
					saveRename();
				} else if (dialogCancelRect.contains(mouseX, mouseY)) {
					closeRename();
				} else if (renameFieldRect.contains(mouseX, mouseY)) {
					setFocused(renameBox);
					return renameBox.mouseClicked(event, doubleClick);
				}
			}
			return true;
		}

		if (popupOptions != null) {
			if (button == BUTTON_LEFT) {
				int index = (int) Math.floor((mouseY - popupY - 4) / POPUP_ITEM_H);
				boolean inside = mouseX >= popupX && mouseX < popupX + popupW;
				if (inside && mouseY >= popupY && index >= 0 && index < popupOptions.size()) {
					IntConsumer action = popupAction;
					closePopup();
					action.accept(index);
					return true;
				}
			}
			closePopup();
			return true;
		}

		if (button == BUTTON_LEFT) {
			if (gearRect.contains(mouseX, mouseY)) {
				openGearMenu();
				return true;
			}
			if (view == View.DETAIL) {
				if (backRect.contains(mouseX, mouseY)) {
					backToContainers();
					return true;
				}
				if (actionMarkRect.contains(mouseX, mouseY)) {
					markContainer(detailEntry);
					return true;
				}
				if (actionCopyRect.contains(mouseX, mouseY)) {
					copyCoordinates(detailEntry);
					return true;
				}
				if (actionRenameRect.contains(mouseX, mouseY)) {
					openRename(detailEntry);
					return true;
				}
			} else {
				if (viewItemsRect.contains(mouseX, mouseY)) {
					switchView(View.ITEMS);
					return true;
				}
				if (viewContainersRect.contains(mouseX, mouseY)) {
					switchView(View.CONTAINERS);
					return true;
				}
				if (dimRect.contains(mouseX, mouseY)) {
					openDimensionMenu();
					return true;
				}
				if (typeRect.contains(mouseX, mouseY)) {
					openTypeMenu();
					return true;
				}
				if (sortRect.contains(mouseX, mouseY)) {
					openSortMenu();
					return true;
				}
			}
			if (prevRect.contains(mouseX, mouseY)) {
				if (activePage() > 0) {
					setActivePage(activePage() - 1);
				}
				return true;
			}
			if (nextRect.contains(mouseX, mouseY)) {
				if (activePage() < maxPage()) {
					setActivePage(activePage() + 1);
				}
				return true;
			}
			int row = rowAt(mouseX, mouseY);
			if (row >= 0) {
				if (view == View.ITEMS) {
					select(hitForRow(row));
				} else if (view == View.CONTAINERS) {
					openDetail(containerForRow(row));
				}
				return true;
			}
		} else if (button == BUTTON_RIGHT) {
			int row = rowAt(mouseX, mouseY);
			if (row >= 0) {
				if (view == View.ITEMS) {
					openContextMenu(row, mouseX, mouseY);
				} else if (view == View.CONTAINERS) {
					openContainerMenu(row, mouseX, mouseY);
				}
				return true;
			}
		}

		// In der Detailansicht gibt es kein Suchfeld, Klicks daneben bleiben wirkungslos.
		if (view == View.DETAIL) {
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	// ---- Zeichnen ----

	/** Füllt ein Rechteck mit leicht abgerundeten Ecken. */
	private static void roundFill(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
		g.fill(x + 2, y, x + w - 2, y + h, color);
		g.fill(x + 1, y + 1, x + w - 1, y + h - 1, color);
		g.fill(x, y + 2, x + w, y + h - 2, color);
	}

	/** Abgerundete Box mit 1 Pixel Rand. */
	private static void roundBox(GuiGraphicsExtractor g, Rect r, int border, int fill) {
		roundFill(g, r.x(), r.y(), r.w(), r.h(), border);
		roundFill(g, r.x() + 1, r.y() + 1, r.w() - 2, r.h() - 2, fill);
	}

	private static void roundBox(GuiGraphicsExtractor g, int x, int y, int w, int h, int border, int fill) {
		roundBox(g, new Rect(x, y, w, h), border, fill);
	}

	/** Kleiner Pfeil nach unten. */
	private static void chevronDown(GuiGraphicsExtractor g, int x, int y, int color) {
		g.fill(x, y, x + 2, y + 1, color);
		g.fill(x + 6, y, x + 8, y + 1, color);
		g.fill(x + 1, y + 1, x + 3, y + 2, color);
		g.fill(x + 5, y + 1, x + 7, y + 2, color);
		g.fill(x + 2, y + 2, x + 6, y + 3, color);
		g.fill(x + 3, y + 3, x + 5, y + 4, color);
	}

	/** Kleiner Pfeil nach rechts. */
	private static void chevronRight(GuiGraphicsExtractor g, int x, int y, int color) {
		g.fill(x, y, x + 1, y + 2, color);
		g.fill(x + 1, y + 1, x + 2, y + 3, color);
		g.fill(x + 2, y + 2, x + 3, y + 4, color);
		g.fill(x + 3, y + 3, x + 4, y + 5, color);
		g.fill(x + 2, y + 4, x + 3, y + 6, color);
		g.fill(x + 1, y + 5, x + 2, y + 7, color);
		g.fill(x, y + 6, x + 1, y + 8, color);
	}

	private String fit(String text, int maxWidth) {
		if (maxWidth <= 0) {
			return "";
		}
		if (this.font.width(text) <= maxWidth) {
			return text;
		}
		String ellipsis = "...";
		int ellipsisWidth = this.font.width(ellipsis);
		String shortened = text;
		while (!shortened.isEmpty() && this.font.width(shortened) + ellipsisWidth > maxWidth) {
			shortened = shortened.substring(0, shortened.length() - 1);
		}
		return shortened + ellipsis;
	}

	private void drawSegments(GuiGraphicsExtractor g, List<Seg> segments, int x, int y, int maxX) {
		int cursor = x;
		for (Seg seg : segments) {
			int width = this.font.width(seg.text());
			if (cursor + width > maxX) {
				g.text(this.font, fit(seg.text(), maxX - cursor), cursor, y, seg.color(), true);
				return;
			}
			g.text(this.font, seg.text(), cursor, y, seg.color(), true);
			cursor += width;
		}
	}

	private void drawDropdown(GuiGraphicsExtractor g, Rect r, String text, boolean hovered) {
		roundBox(g, r, hovered ? ACCENT : FIELD_BORDER, FIELD);
		g.text(this.font, fit(text, r.w() - 30), r.x() + 8, r.y() + 7, TEXT_SOFT, true);
		chevronDown(g, r.x() + r.w() - 15, r.y() + 9, MUTED);
	}

	private void drawSmallButton(GuiGraphicsExtractor g, Rect r, String label, boolean enabled, boolean hovered) {
		roundBox(g, r, hovered && enabled ? ACCENT : FIELD_BORDER, FIELD);
		g.text(this.font, label, r.x() + r.w() / 2 - this.font.width(label) / 2, r.y() + 7,
				enabled ? TEXT : DIM, true);
	}

	/** Umschalter "Items | Container" in der Kopfzeile. */
	private void drawViewToggle(GuiGraphicsExtractor g, int mx, int my) {
		Rect whole = new Rect(viewItemsRect.x(), viewItemsRect.y(), TOGGLE_W, viewItemsRect.h());
		roundBox(g, whole, FIELD_BORDER, FIELD);
		for (int i = 0; i < 2; i++) {
			Rect r = i == 0 ? viewItemsRect : viewContainersRect;
			boolean active = (i == 0) == (view == View.ITEMS);
			boolean hovered = r.contains(mx, my);
			if (active) {
				roundBox(g, r.x() + 2, r.y() + 2, r.w() - 4, r.h() - 4, ACCENT, TOGGLE_ACTIVE);
			}
			String label = i == 0 ? "Items" : "Container";
			int color = active ? TEXT : hovered ? TEXT_SOFT : MUTED;
			g.text(this.font, label, r.x() + r.w() / 2 - this.font.width(label) / 2, r.y() + 6, color, true);
		}
	}

	private List<Seg> segmentsFor(ChestStore.Hit hit) {
		ChestStore.Entry e = hit.entry();
		ChestStore.ItemInfo item = hit.item();
		boolean enchanted = item.enchants != null && !item.enchants.isEmpty();
		List<Seg> segments = new ArrayList<>();
		if (e.pinned) {
			segments.add(new Seg("[P] ", GOLD));
		}
		segments.add(new Seg(item.count + "x ", TEXT_SOFT));
		segments.add(new Seg(String.valueOf(item.name), enchanted ? PURPLE : TEXT));
		int pct = item.durabilityPercent();
		if (pct >= 0) {
			int color = pct >= 50 ? GREEN : pct >= 20 ? YELLOW : RED;
			segments.add(new Seg(" (" + pct + "%)", color));
		}
		if (item.container != null) {
			segments.add(new Seg(" in " + item.container, MUTED));
		}
		segments.add(new Seg("  |  ", DIM));
		segments.add(new Seg(ChestStore.typeLabel(e.type), MUTED));
		segments.add(new Seg("  |  ", DIM));
		segments.add(new Seg(ChestStore.dimensionLabel(e.dimension), MUTED));
		return segments;
	}

	/** Name und Zusatzinfos eines Items in der Detailansicht (Menge steht rechts in der Kapsel). */
	private List<Seg> detailSegments(ChestStore.ItemInfo item) {
		boolean enchanted = item.enchants != null && !item.enchants.isEmpty();
		List<Seg> segments = new ArrayList<>();
		segments.add(new Seg(String.valueOf(item.name), enchanted ? PURPLE : TEXT));
		int pct = item.durabilityPercent();
		if (pct >= 0) {
			int color = pct >= 50 ? GREEN : pct >= 20 ? YELLOW : RED;
			segments.add(new Seg(" (" + pct + "%)", color));
		}
		if (item.container != null) {
			segments.add(new Seg(" in " + item.container, MUTED));
		}
		return segments;
	}

	private static String summaryFor(ChestStore.Entry e) {
		if (e.items.isEmpty()) {
			return "Keine Items gespeichert";
		}
		int distinct = ChestStore.distinctItems(e);
		return (distinct == 1 ? "1 Item" : distinct + " verschiedene Items") + "  ·  " + ChestStore.totalCount(e)
				+ " gesamt";
	}

	private static List<Component> tooltipFor(ChestStore.Hit hit, boolean withHint) {
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
		if (withHint) {
			lines.add(Component.literal("Linksklick: Markieren, Rechtsklick: Optionen"));
		}
		return lines;
	}

	private static List<Component> containerTooltip(ChestStore.Entry e) {
		List<Component> lines = new ArrayList<>();
		lines.add(Component.literal(ChestStore.displayName(e)));
		lines.add(Component.literal(ChestStore.typeLabel(e.type) + ", " + ChestStore.dimensionLabel(e.dimension) + " "
				+ e.x + " " + e.y + " " + e.z));
		lines.add(Component.literal(summaryFor(e)));
		lines.add(Component.literal("Zuletzt gesehen: " + ChestStore.ageText(e.updated)));
		lines.add(Component.literal("Linksklick: Details, Rechtsklick: Optionen"));
		return lines;
	}

	private void drawItemRow(GuiGraphicsExtractor g, Rect r, ChestStore.Hit hit, boolean hovered) {
		ChestStore.Entry e = hit.entry();
		roundBox(g, r, hovered ? ACCENT : ROW_BORDER, hovered ? ROW_HOVER : ROW);

		// Icon-Kachel
		roundBox(g, r.x() + 4, r.y() + 3, 20, 20, ROW_BORDER, TILE);
		g.fakeItem(stackFor(hit.item().id), r.x() + 6, r.y() + 5);

		// Koordinaten als Kapsel rechts
		String coords = e.x + " " + e.y + " " + e.z;
		int pillW = this.font.width(coords) + 14;
		int pillX = r.x() + r.w() - 22 - pillW;
		roundBox(g, pillX, r.y() + 4, pillW, 18, PILL, PILL);
		g.text(this.font, coords, pillX + 7, r.y() + 9, TEXT_SOFT, true);
		chevronRight(g, r.x() + r.w() - 13, r.y() + 9, hovered ? TEXT : MUTED);

		// Name, Typ und Dimension
		drawSegments(g, segmentsFor(hit), r.x() + 32, r.y() + 9, pillX - 8);
	}

	private void drawContainerRow(GuiGraphicsExtractor g, Rect r, ChestStore.Entry e, boolean hovered) {
		roundBox(g, r, hovered ? ACCENT : ROW_BORDER, hovered ? ROW_HOVER : ROW);

		// Icon-Kachel mit dem Container-Block
		roundBox(g, r.x() + 4, r.y() + 3, 20, 20, ROW_BORDER, TILE);
		g.fakeItem(stackFor(ChestStore.iconId(e)), r.x() + 6, r.y() + 5);

		// Koordinaten als Kapsel rechts, dahinter der Pfeil zur Detailansicht
		String coords = e.x + " " + e.y + " " + e.z;
		int pillW = this.font.width(coords) + 14;
		int pillX = r.x() + r.w() - 22 - pillW;
		roundBox(g, pillX, r.y() + 4, pillW, 18, PILL, PILL);
		g.text(this.font, coords, pillX + 7, r.y() + 9, TEXT_SOFT, true);
		chevronRight(g, r.x() + r.w() - 13, r.y() + 9, hovered ? TEXT : MUTED);

		// Zeile 1: Name, Typ und Dimension; Zeile 2: Inhaltsübersicht
		int textX = r.x() + 32;
		int maxX = pillX - 8;
		List<Seg> line = new ArrayList<>();
		if (e.pinned) {
			line.add(new Seg("[P] ", GOLD));
		}
		line.add(new Seg(ChestStore.displayName(e), ChestStore.hasCustomName(e) ? TEXT : TEXT_SOFT));
		line.add(new Seg("  |  ", DIM));
		line.add(new Seg(ChestStore.typeLabel(e.type), MUTED));
		line.add(new Seg("  |  ", DIM));
		line.add(new Seg(ChestStore.dimensionLabel(e.dimension), MUTED));
		drawSegments(g, line, textX, r.y() + 3, maxX);
		g.text(this.font, fit(summaryFor(e), maxX - textX), textX, r.y() + 14, MUTED, true);
	}

	private void drawDetailRow(GuiGraphicsExtractor g, Rect r, ChestStore.ItemInfo item, boolean hovered) {
		roundBox(g, r, hovered ? ACCENT : ROW_BORDER, hovered ? ROW_HOVER : ROW);

		roundBox(g, r.x() + 4, r.y() + 3, 20, 20, ROW_BORDER, TILE);
		g.fakeItem(stackFor(item.id), r.x() + 6, r.y() + 5);

		// Menge als Kapsel rechts
		String amount = item.count + "x";
		int pillW = this.font.width(amount) + 14;
		int pillX = r.x() + r.w() - 8 - pillW;
		roundBox(g, pillX, r.y() + 4, pillW, 18, PILL, PILL);
		g.text(this.font, amount, pillX + 7, r.y() + 9, TEXT_SOFT, true);

		drawSegments(g, detailSegments(item), r.x() + 32, r.y() + 9, pillX - 8);
	}

	/** Kopf der Detailansicht: Container-Icon, Name, Typ, Dimension, Koordinaten. */
	private void drawDetailHeader(GuiGraphicsExtractor g, int mx, int my) {
		ChestStore.Entry e = detailEntry;
		int x = panel.x() + PAD;
		int y = panel.y() + 38;
		g.fakeItem(stackFor(ChestStore.iconId(e)), x, y);
		String name = (e.pinned ? "[P] " : "") + ChestStore.displayName(e);
		int maxX = panel.x() + PANEL_W - PAD;
		g.text(this.font, fit("§l" + name, maxX - (x + 22)), x + 22, y + 1, TEXT, true);

		List<Seg> info = new ArrayList<>();
		info.add(new Seg(ChestStore.typeLabel(e.type), MUTED));
		info.add(new Seg("  |  ", DIM));
		info.add(new Seg(ChestStore.dimensionLabel(e.dimension), MUTED));
		info.add(new Seg("  |  ", DIM));
		info.add(new Seg(e.x + " " + e.y + " " + e.z, TEXT_SOFT));
		info.add(new Seg("  |  ", DIM));
		info.add(new Seg("gesehen " + ChestStore.ageText(e.updated), MUTED));
		drawSegments(g, info, x + 22, y + 13, maxX);

		drawSmallButton(g, actionMarkRect, "Hervorheben", true, actionMarkRect.contains(mx, my));
		String copyLabel = System.currentTimeMillis() < copyFeedbackUntil ? "Kopiert!" : "Koordinaten kopieren";
		drawSmallButton(g, actionCopyRect, copyLabel, true, actionCopyRect.contains(mx, my));
		drawSmallButton(g, actionRenameRect, "Umbenennen", true, actionRenameRect.contains(mx, my));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		super.extractRenderState(g, mouseX, mouseY, delta);

		// Solange ein Menü oder das Eingabefenster offen ist, reagiert der Rest der GUI nicht auf die Maus.
		boolean popupOpen = popupOptions != null;
		boolean blocked = popupOpen || renameOpen;
		int mx = blocked ? -1000 : mouseX;
		int my = blocked ? -1000 : mouseY;

		// Panel
		roundBox(g, panel, BORDER, BG_PANEL);

		// Kopfzeile
		g.fakeItem(stackFor("minecraft:chest"), panel.x() + PAD, panel.y() + 10);
		g.text(this.font, "§lKisten-Index", panel.x() + PAD + 24, panel.y() + 15, TEXT, true);
		if (view == View.DETAIL) {
			drawSmallButton(g, backRect, "< Zurück", true, backRect.contains(mx, my));
		} else {
			drawViewToggle(g, mx, my);
		}
		boolean gearHover = gearRect.contains(mx, my);
		roundBox(g, gearRect, gearHover ? ACCENT : FIELD_BORDER, FIELD);
		for (int i = 0; i < 3; i++) {
			int ly = gearRect.y() + 6 + i * 4;
			g.fill(gearRect.x() + 5, ly, gearRect.x() + 15, ly + 1, MUTED);
			int knob = gearRect.x() + 6 + ((i * 5) % 7);
			g.fill(knob, ly - 1, knob + 3, ly + 2, TEXT_SOFT);
		}

		if (view == View.DETAIL) {
			drawDetailHeader(g, mx, my);
		} else {
			// Suchfeld mit Lupe
			roundBox(g, searchRect, searchBox.isFocused() ? ACCENT : FIELD_BORDER, FIELD);
			int lupeX = searchRect.x() + 9;
			int lupeY = searchRect.y() + 6;
			roundBox(g, lupeX, lupeY, 9, 9, MUTED, FIELD);
			g.fill(lupeX + 8, lupeY + 8, lupeX + 10, lupeY + 10, MUTED);
			g.fill(lupeX + 10, lupeY + 10, lupeX + 12, lupeY + 12, MUTED);
			searchBox.extractRenderState(g, mx, my, delta);

			// Filter und Sortierung
			drawDropdown(g, dimRect, dimFilter.isEmpty() ? "Alle Dimensionen" : ChestStore.dimensionLabel(dimFilter),
					dimRect.contains(mx, my));
			drawDropdown(g, typeRect, typeFilter.isEmpty() ? "Alle Typen" : typeFilter, typeRect.contains(mx, my));
			String sortLabel = view == View.CONTAINERS ? CONTAINER_SORT_LABELS.get(containerSortIndex)
					: SORT_LABELS.get(sortIndex);
			drawDropdown(g, sortRect, "Sort: " + sortLabel, sortRect.contains(mx, my));
		}

		// Liste der aktuellen Ansicht
		int hoveredRow = rowAt(mx, my);
		for (int i = 0; i < visibleRows(); i++) {
			Rect r = rowRect(i);
			boolean hovered = i == hoveredRow;
			switch (view) {
				case ITEMS -> drawItemRow(g, r, hitForRow(i), hovered);
				case CONTAINERS -> drawContainerRow(g, r, containerForRow(i), hovered);
				case DETAIL -> drawDetailRow(g, r, detailItemForRow(i), hovered);
			}
		}

		// Fußzeile
		g.fill(panel.x() + PAD, prevRect.y() - 3, panel.x() + PANEL_W - PAD, prevRect.y() - 2, BORDER);
		drawSmallButton(g, prevRect, "<", activePage() > 0, prevRect.contains(mx, my));
		drawSmallButton(g, nextRect, ">", activePage() < maxPage(), nextRect.contains(mx, my));
		String pageText = "Seite " + (activePage() + 1) + " von " + (maxPage() + 1);
		String info = switch (view) {
			case ITEMS -> hits.isEmpty() ? "Keine Treffer. Öffne Kisten, damit sie erfasst werden."
					: hits.size() + " Treffer  ·  " + pageText;
			case CONTAINERS -> containerHits.isEmpty() ? "Keine Container. Öffne Kisten, damit sie erfasst werden."
					: containerHits.size() + " Container  ·  " + pageText;
			case DETAIL -> detailItems.isEmpty() ? "Keine Items gespeichert. Öffne den Container erneut."
					: ChestStore.distinctItems(detailEntry) + " Item-Arten  ·  "
							+ ChestStore.totalCount(detailEntry) + " gesamt  ·  " + pageText;
		};
		g.text(this.font, fit(info, nextRect.x() - prevRect.x() - prevRect.w() - 12),
				panel.x() + PANEL_W / 2 - Math.min(this.font.width(info), nextRect.x() - prevRect.x() - prevRect.w() - 12) / 2,
				prevRect.y() + 7, MUTED, true);

		// Tooltips
		if (!blocked) {
			if (hoveredRow >= 0) {
				List<Component> lines = switch (view) {
					case ITEMS -> tooltipFor(hitForRow(hoveredRow), true);
					case CONTAINERS -> containerTooltip(containerForRow(hoveredRow));
					case DETAIL -> tooltipFor(new ChestStore.Hit(detailEntry, detailItemForRow(hoveredRow)), false);
				};
				g.setComponentTooltipForNextFrame(this.font, lines, mouseX, mouseY);
			} else if (view != View.DETAIL && searchRect.contains(mouseX, mouseY)) {
				g.setComponentTooltipForNextFrame(this.font, List.of(
						Component.literal("Suche nach Item, Verzauberung oder Shulker-Name"),
						Component.literal("In der Container-Ansicht auch nach dem Container-Namen"),
						Component.literal("Mehrere Wörter: alle müssen passen"),
						Component.literal("<30% oder >80% filtert nach Haltbarkeit")), mouseX, mouseY);
			}
		}

		// Aufklappmenü ganz oben
		if (popupOpen) {
			g.nextStratum();
			int height = popupOptions.size() * POPUP_ITEM_H + 8;
			g.fill(popupX + 2, popupY + 3, popupX + popupW + 2, popupY + height + 3, 0x66000000);
			roundBox(g, popupX, popupY, popupW, height, ACCENT, 0xFF0F1626);
			for (int i = 0; i < popupOptions.size(); i++) {
				int itemTop = popupY + 4 + i * POPUP_ITEM_H;
				boolean hovered = mouseX >= popupX && mouseX < popupX + popupW && mouseY >= itemTop
						&& mouseY < itemTop + POPUP_ITEM_H;
				if (hovered) {
					g.fill(popupX + 3, itemTop, popupX + popupW - 3, itemTop + POPUP_ITEM_H, 0xFF1D2B4F);
				}
				boolean checked = i == popupChecked;
				g.text(this.font, fit(popupOptions.get(i), popupW - 22), popupX + 10, itemTop + 5,
						checked ? ACCENT : TEXT, true);
				if (checked) {
					g.fill(popupX + popupW - 14, itemTop + 8, popupX + popupW - 8, itemTop + 10, ACCENT);
				}
			}
		}

		// Eingabefenster zum Umbenennen
		if (renameOpen) {
			g.nextStratum();
			g.fill(0, 0, this.width, this.height, 0x99000000);
			roundBox(g, dialogRect, ACCENT, 0xFF0F1626);
			g.text(this.font, "§lContainer umbenennen", dialogRect.x() + 12, dialogRect.y() + 12, TEXT, true);
			roundBox(g, renameFieldRect, renameBox.isFocused() ? ACCENT : FIELD_BORDER, FIELD);
			renameBox.extractRenderState(g, mouseX, mouseY, delta);
			drawSmallButton(g, dialogSaveRect, "Speichern", true, dialogSaveRect.contains(mouseX, mouseY));
			drawSmallButton(g, dialogCancelRect, "Abbrechen", true, dialogCancelRect.contains(mouseX, mouseY));
		}
	}
}
