package com.kistenindex;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

public class KistenIndexClient implements ClientModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger("kistenindex");

	private static KeyMapping openKey;
	private static KeyMapping clearKey;

	// Zuletzt angeklickter Block (wird beim Öffnen eines Containers gebraucht)
	private static BlockPos lastPos;
	private static String lastDim;
	private static String lastBlock;
	private static long lastClickTime;

	// Aktuell geöffneter, unterstützter Container
	private static AbstractContainerScreen<?> activeScreen;
	private static BlockPos activePos;
	private static String activeDim;
	private static String activeBlock;
	private static int tickCounter;

	@Override
	public void onInitializeClient() {
		KeyMapping.Category category = KeyMapping.Category.register(
				Identifier.fromNamespaceAndPath("kistenindex", "main"));

		// Seit 26.3 (SDL): Taste direkt als Konstante, ohne InputConstants.Type
		openKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.kistenindex.open",
				InputConstants.KEY_K,
				category));
		clearKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.kistenindex.clear",
				InputConstants.KEY_J,
				category));

		// Markierung des gewählten Containers auf dem HUD
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
				Identifier.fromNamespaceAndPath("kistenindex", "highlight"), HighlightHud::extract);

		// Merkt sich, welchen Block der Spieler zuletzt angeklickt hat.
		UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
			if (level.isClientSide()) {
				BlockPos pos = hitResult.getBlockPos();
				lastPos = pos;
				lastDim = level.dimension().identifier().toString();
				lastBlock = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).getPath();
				lastClickTime = System.currentTimeMillis();
			}
			return InteractionResult.PASS;
		});

		// Erkennt geöffnete Kisten, Fässer und Shulkerboxen.
		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (!(screen instanceof AbstractContainerScreen<?> containerScreen)) {
				return;
			}
			AbstractContainerMenu menu = containerScreen.getMenu();
			if (!(menu instanceof ChestMenu) && !(menu instanceof ShulkerBoxMenu)) {
				return;
			}
			if (lastPos == null || System.currentTimeMillis() - lastClickTime > 5000) {
				return;
			}
			activeScreen = containerScreen;
			activePos = lastPos;
			activeDim = lastDim;
			activeBlock = lastBlock;
			tickCounter = 0;

			// Markierung entfernen, sobald der markierte Container geöffnet wird (Doppelkisten: ±1 Block)
			HighlightState.Target marked = HighlightState.get();
			if (marked != null && marked.dimension().equals(activeDim)
					&& Math.abs(marked.x() - activePos.getX()) <= 1
					&& Math.abs(marked.z() - activePos.getZ()) <= 1
					&& marked.y() == activePos.getY()) {
				HighlightState.clear();
			}

			ScreenEvents.remove(screen).register(s -> {
				snapshot(client);
				if (activeScreen == containerScreen) {
					activeScreen = null;
				}
			});
		});

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openKey.consumeClick()) {
				if (client.player != null) {
					client.gui.setScreen(new IndexScreen());
				}
			}
			while (clearKey.consumeClick()) {
				if (HighlightState.get() != null) {
					HighlightState.clear();
					if (client.player != null) {
						client.player.sendSystemMessage(Component.literal("Kisten-Index: Markierung entfernt"));
					}
				}
			}
			if (activeScreen != null && ++tickCounter % 5 == 0) {
				snapshot(client);
			}
		});
	}

	/** Liest den Inhalt des geöffneten Containers und speichert ihn im Index. */
	private static void snapshot(Minecraft client) {
		if (activeScreen == null || activePos == null) {
			return;
		}
		Map<String, ChestStore.ItemInfo> merged = new LinkedHashMap<>();
		for (Slot slot : activeScreen.getMenu().slots) {
			if (slot.container instanceof Inventory) {
				continue; // Spielerinventar überspringen
			}
			ItemStack stack = slot.getItem();
			if (stack.isEmpty()) {
				continue;
			}
			String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
			String name = stack.getHoverName().getString();
			int count = stack.getCount();
			ChestStore.ItemInfo existing = merged.get(id);
			if (existing == null) {
				merged.put(id, new ChestStore.ItemInfo(id, name, count));
			} else {
				existing.count += count;
			}
		}
		ChestStore.update(client, activeDim, activePos, activeBlock, new ArrayList<>(merged.values()));
	}
}
