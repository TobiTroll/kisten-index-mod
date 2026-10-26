package com.kistenindex;

import com.mojang.blaze3d.platform.InputConstants;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
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
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

public class KistenIndexClient implements ClientModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger("kistenindex");

	private static KeyMapping openKey;
	private static KeyMapping clearKey;

	// Zuletzt angeklickter Block (wird beim Öffnen eines Containers gebraucht). Bei Doppelkisten ist lastPos
	// immer dieselbe Hälfte, egal welche angeklickt wurde, lastMerge ist die andere Hälfte.
	private static BlockPos lastPos;
	private static BlockPos lastMerge;
	private static String lastDim;
	private static String lastBlock;
	private static long lastClickTime;

	// Aktuell geöffneter, unterstützter Container
	private static AbstractContainerScreen<?> activeScreen;
	private static BlockPos activePos;
	private static BlockPos activeMerge;
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
				BlockState state = level.getBlockState(pos);
				String blockPath = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
				BlockPos merge = null;

				// Doppelkiste: beide Hälften werden auf dieselbe Position (die kleinere) zusammengefasst.
				if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
					BlockPos other = ChestBlock.getConnectedBlockPos(pos, state);
					if (pos.compareTo(other) <= 0) {
						merge = other;
					} else {
						merge = pos;
						pos = other;
					}
					blockPath = "double_" + blockPath;
				}

				lastPos = pos;
				lastMerge = merge;
				lastDim = level.dimension().identifier().toString();
				lastBlock = blockPath;
				lastClickTime = System.currentTimeMillis();
			}
			return InteractionResult.PASS;
		});

		// Erkennt geöffnete Kisten, Fässer, Shulkerboxen, Spender, Werfer und Öfen.
		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (!(screen instanceof AbstractContainerScreen<?> containerScreen)) {
				return;
			}
			AbstractContainerMenu menu = containerScreen.getMenu();
			boolean supported = menu instanceof ChestMenu || menu instanceof ShulkerBoxMenu
					|| menu instanceof DispenserMenu || menu instanceof AbstractFurnaceMenu;
			if (!supported) {
				return;
			}
			if (lastPos == null || System.currentTimeMillis() - lastClickTime > 5000) {
				return;
			}
			activeScreen = containerScreen;
			activePos = lastPos;
			activeMerge = lastMerge;
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
						client.player.sendSystemMessage(Component.literal(
								Lang.t("Kisten-Index: Markierung entfernt", "Chest Index: marker removed")));
					}
				}
			}
			if (activeScreen != null && ++tickCounter % 5 == 0) {
				snapshot(client);
			}
		});
	}

	/** Liest den Inhalt des geöffneten Containers (inklusive Shulkerbox-Inhalten) und speichert ihn im Index. */
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
			addStack(merged, stack, null);

			// Liegt eine Shulkerbox im Container, werden ihre Inhalte mit erfasst.
			ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
			if (contents != null) {
				String shulkerName = stack.getHoverName().getString();
				contents.nonEmptyItemCopyStream().forEach(inner -> addStack(merged, inner, shulkerName));
			}
		}
		ChestStore.update(client, activeDim, activePos, activeMerge, activeBlock, new ArrayList<>(merged.values()));
	}

	private static void addStack(Map<String, ChestStore.ItemInfo> merged, ItemStack stack, String container) {
		String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
		String name = stack.getHoverName().getString();
		String enchants = enchantments(stack);
		int maxDamage = stack.isDamageableItem() ? stack.getMaxDamage() : 0;
		int damage = maxDamage > 0 ? stack.getDamageValue() : 0;
		String key = id + "|" + name + "|" + enchants + "|" + damage + "|" + (container == null ? "" : container);
		ChestStore.ItemInfo existing = merged.get(key);
		if (existing == null) {
			merged.put(key, new ChestStore.ItemInfo(id, name, stack.getCount(), enchants, damage, maxDamage, container));
		} else {
			existing.count += stack.getCount();
		}
	}

	/** Verzauberungen (auch von Zauberbüchern) als Text, z. B. "Schärfe V, Haltbarkeit III". */
	private static String enchantments(ItemStack stack) {
		StringBuilder sb = new StringBuilder();
		appendEnchantments(sb, stack.get(DataComponents.ENCHANTMENTS));
		appendEnchantments(sb, stack.get(DataComponents.STORED_ENCHANTMENTS));
		return sb.toString();
	}

	private static void appendEnchantments(StringBuilder sb, ItemEnchantments enchantments) {
		if (enchantments == null || enchantments.isEmpty()) {
			return;
		}
		for (Object2IntMap.Entry<Holder<Enchantment>> entry : enchantments.entrySet()) {
			if (sb.length() > 0) {
				sb.append(", ");
			}
			sb.append(Enchantment.getFullname(entry.getKey(), entry.getIntValue()).getString());
		}
	}
}
