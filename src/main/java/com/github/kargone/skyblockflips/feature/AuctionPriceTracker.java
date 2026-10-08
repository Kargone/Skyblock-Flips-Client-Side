package com.github.kargone.skyblockflips.feature;

import com.github.kargone.skyblockflips.flip.CraftCostModel;
import com.github.kargone.skyblockflips.flip.ServerData;
import com.github.kargone.skyblockflips.util.ContainerUtils;
import com.github.kargone.skyblockflips.util.SkyblockText;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.List;
import java.util.Set;

/**
 * Offers the lowest BIN as the price when listing an item.
 *
 * While the Create BIN Auction menu is open, the item placed in it is looked up in
 * the server's lowest-BIN prices and remembered as the {@link PendingAmount}. Clicking
 * the Item Price button then opens a sign, and the sign overlay offers that price
 * as its one-click fill.
 */
public final class AuctionPriceTracker {

    public static final String CREATE_BIN_TITLE = "create bin auction";

    /** The slot in the middle of the menu that holds the item being listed. */
    private static final int ITEM_SLOT = 13;

    /** What Hypixel renames the item to once it is placed in {@link #ITEM_SLOT}. */
    private static final String PLACED_ITEM_TITLE = "AUCTION FOR ITEM:";

    /** Coins taken off the lowest BIN so the new listing sits below it. */
    private static final long UNDERCUT = 10_000L;

    /**
     * Rarities as Hypixel prints them on an item's last lore line. The server only
     * keeps a separate lowest BIN per rarity for Beastmaster Crest, so that is the
     * only item this is read for.
     */
    private static final Set<String> RARITIES = Set.of(
            "COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC",
            "DIVINE", "SPECIAL", "VERY SPECIAL", "ULTIMATE", "ADMIN");
    private static final String TIERED_ITEM = "Beastmaster Crest";

    private AuctionPriceTracker() {
    }

    public static boolean isCreateBinScreen(String title) {
        return title != null && SkyblockText.strip(title).toLowerCase().contains(CREATE_BIN_TITLE);
    }

    /** Called every frame the Create BIN Auction menu is drawn. */
    public static void update(AbstractContainerMenu menu) {
        logMenuOnce(menu);
        if (menu.slots.size() <= ITEM_SLOT) {
            debug("menu has only " + menu.slots.size() + " slots");
            return;
        }

        Slot slot = menu.slots.get(ITEM_SLOT);
        if (!ContainerUtils.isContainerSlot(slot)) {
            debug("slot " + ITEM_SLOT + " is a player inventory slot");
            return;
        }

        ItemStack stack = slot.getItem();
        if (stack.isEmpty()) {
            debug("slot " + ITEM_SLOT + " is empty");
            return;
        }

        String name = SkyblockText.strip(stack.getHoverName().getString()).trim();
        if (name.equalsIgnoreCase(PLACED_ITEM_TITLE)) {
            // Hypixel renames the placed item and moves its real name to the
            // first line of the lore, above the item's own lore.
            name = firstLoreLine(stack);
        }
        String tier = name.equals(TIERED_ITEM) ? rarityOf(stack) : null;
        String key = CraftCostModel.normalizeName(name, tier);

        // The empty-slot placeholder has no lowest BIN, so it falls out here too.
        Double lowestBin = ServerData.market().lowestBin(key);
        debug("slot " + ITEM_SLOT + " = '" + name + "', key '" + key + "', lowest BIN " + lowestBin
                + ", market data loaded: " + !ServerData.market().isEmpty());
        if (lowestBin == null || lowestBin <= 0) return;

        // Undercut so the listing is the new cheapest; never offer less than 1 coin.
        long price = Math.max(1L, Math.round(lowestBin) - UNDERCUT);
        PendingAmount.remember("Lowest BIN -10k: " + name, price);
        debug("offering " + price + " for the price sign");
    }

    // --- Temporary diagnostics: every message is printed once, when it changes. ---

    private static String lastDebug = "";
    private static AbstractContainerMenu lastLoggedMenu = null;

    private static void debug(String message) {
        if (message.equals(lastDebug)) return;
        lastDebug = message;
        System.out.println("[SkyblockFlips] Create BIN: " + message);
    }

    /** Lists every filled menu slot once per menu opened, to find where the item sits. */
    private static void logMenuOnce(AbstractContainerMenu menu) {
        if (menu == lastLoggedMenu) return;

        StringBuilder filled = new StringBuilder();
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            if (!ContainerUtils.isContainerSlot(slot) || slot.getItem().isEmpty()) continue;
            String name = SkyblockText.strip(slot.getItem().getHoverName().getString()).trim();
            if (name.equals("Black Stained Glass Pane") || name.isEmpty() || name.equals(" ")) continue;
            filled.append("\n  slot ").append(i).append(": ").append(name);
        }
        // The contents arrive a few frames after the menu opens, so only lock in a
        // menu once it has something in it.
        if (filled.isEmpty()) return;

        lastLoggedMenu = menu;
        System.out.println("[SkyblockFlips] Create BIN menu, " + menu.slots.size() + " slots:" + filled);
    }

    /** The first non-blank lore line, which is where the placed item keeps its name. */
    private static String firstLoreLine(ItemStack stack) {
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null) return "";

        for (Component line : lore.lines()) {
            String text = SkyblockText.strip(line.getString()).trim();
            if (!text.isEmpty()) return text;
        }
        return "";
    }

    /** The rarity off the last lore line that carries one, e.g. "EPIC ACCESSORY". */
    private static String rarityOf(ItemStack stack) {
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null) return null;

        List<Component> lines = lore.lines();
        for (int i = lines.size() - 1; i >= 0; i--) {
            String line = SkyblockText.strip(lines.get(i).getString()).trim();
            if (line.startsWith("VERY SPECIAL")) return "VERY SPECIAL";

            for (String word : line.split(" ")) {
                if (RARITIES.contains(word)) return word;
            }
        }
        return null;
    }
}
