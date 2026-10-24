package com.github.kargone.skyblockflips.feature;

import com.github.kargone.skyblockflips.flip.CraftCostModel;
import com.github.kargone.skyblockflips.flip.ServerData;
import com.github.kargone.skyblockflips.flip.TraderPositions;
import com.github.kargone.skyblockflips.util.ContainerUtils;
import com.github.kargone.skyblockflips.util.ScreenConstants;
import com.github.kargone.skyblockflips.util.SkyblockText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shows, as the stack size of each item being auctioned, where its BIN sits among
 * all BINs for that item: 1 is the cheapest, 2 the second cheapest and so on.
 *
 * An item counts as one of ours when an unsold auction in the trader data has its
 * name and exactly its "Buy it now" price, so this works in Manage Auctions and on
 * our own listings in the browser alike. The ladder comes from the server's last
 * Auction House scan, so it lags the game by up to a minute.
 */
public final class AuctionRankMarks {

    private static final Pattern BUY_IT_NOW = Pattern.compile("Buy it now: ([\\d,]+) coins");

    /** The only item the server keeps separate prices per rarity for. */
    private static final String TIERED_ITEM = "Beastmaster Crest";

    private AuctionRankMarks() {
    }

    /** Called from the slot hook after the item is drawn; slot coordinates are already local. */
    public static void drawOver(GuiGraphicsExtractor graphics, Screen screen, Slot slot) {
        if (screen.getTitle() == null || !ScreenConstants.isAuctionScreen(screen.getTitle().getString())) return;
        if (!ContainerUtils.isContainerSlot(slot)) return;

        ItemStack stack = slot.getItem();
        if (stack.isEmpty()) return;

        long price = binPrice(stack);
        if (price <= 0L) return;

        TraderPositions.Auction auction = ourAuction(SkyblockText.strip(stack.getHoverName().getString()).trim(), price);
        if (auction == null) return;

        int rank = ServerData.market().binRank(keyOf(auction.itemName()), price);
        if (rank == 0) return;

        String text = rank < 0 ? "§c99+" : (rank == 1 ? "§a" : rank <= 3 ? "§e" : "§c") + rank;
        graphics.itemDecorations(Minecraft.getInstance().font, stack, slot.x, slot.y, text);
    }

    /** The "Buy it now" price off the lore, or 0 for an auction with bids or no price. */
    private static long binPrice(ItemStack stack) {
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null) return 0L;

        for (Component line : lore.lines()) {
            Matcher matcher = BUY_IT_NOW.matcher(SkyblockText.strip(line.getString()));
            if (matcher.find()) {
                try {
                    return Long.parseLong(matcher.group(1).replace(",", ""));
                } catch (NumberFormatException e) {
                    return 0L;
                }
            }
        }
        return 0L;
    }

    /** The unsold auction of this name at this price, or null when it is not one of ours. */
    private static TraderPositions.Auction ourAuction(String name, long price) {
        for (TraderPositions.Auction auction : ServerData.positions().auctions()) {
            if (auction.sold() || Math.round(auction.priceSelling()) != price) continue;
            if (baseName(auction.itemName()).equalsIgnoreCase(name)) return auction;
        }
        return null;
    }

    /** The server's ladder key for a trader auction name such as "Beastmaster Crest - EPIC". */
    private static String keyOf(String itemName) {
        String base = baseName(itemName);
        String tier = base.equals(TIERED_ITEM) && base.length() < itemName.length()
                ? itemName.substring(base.length() + 3) : null;
        return CraftCostModel.normalizeName(base, tier);
    }

    private static String baseName(String itemName) {
        int tier = itemName.lastIndexOf(" - ");
        return tier > 0 ? itemName.substring(0, tier) : itemName;
    }
}
