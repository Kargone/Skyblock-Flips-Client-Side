package com.github.kargone.skyblockflips.feature;

import com.github.kargone.skyblockflips.flip.ServerData;
import com.github.kargone.skyblockflips.flip.TraderPositions;
import com.github.kargone.skyblockflips.util.ContainerUtils;
import com.github.kargone.skyblockflips.util.SkyblockText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Marks each undercut sell offer on the Bazaar orders screen with whether
 * cancelling and relisting it is worth it: green when it is, yellow when it is only
 * worth it after filling the inventory from storage, grey when it is not. An offer
 * that is still the lowest gets no mark. Hovering an offer shows the numbers.
 *
 * The sums are {@link RelistAdvisor}'s, worked out before the cancel: the unsold
 * part of the offer is relisted as far as it fits in the inventory, and storage
 * tops up whatever room is left.
 *
 * What each offer's tooltip said is also remembered, since the screen closes before
 * the cancel: {@link #seenOffer} hands it to the chat advice, so neither has to
 * trust the trader data for how much of an offer has sold.
 */
public final class SellOfferMarks {

    private static final String SELL_PREFIX = "SELL ";
    private static final Pattern OFFER_AMOUNT = Pattern.compile("Offer amount: ([\\d,]+)x");
    private static final Pattern FILLED = Pattern.compile("Filled: ([\\d,]+)/");
    private static final Pattern PRICE = Pattern.compile("Price per unit: ([\\d,.]+) coins");
    /** Prices are shown to 0.1 coins, so anything closer than this is the same price. */
    private static final double SAME_PRICE = 0.05D;
    private static final int SLOT_SIZE = 16;

    // Drawn behind the item, so they can be strong without hiding it.
    private static final int RELIST_COLOR = 0xC040D040;
    private static final int FILL_ONLY_COLOR = 0xC0E0C030;
    private static final int NOT_WORTH_COLOR = 0xC0404040;
    /** How long a remembered offer is trusted for a cancel. */
    private static final long SEEN_FOR_MS = 10L * 60L * 1000L;

    /**
     * A sell offer as the orders screen last showed it.
     *
     * @param unsold   what the tooltip said was still for sale
     * @param holdings storage and costs while the offer was still up
     */
    public record Seen(long unsold, RelistAdvisor.Holdings holdings, long seenAt) {
    }

    /** The offers last seen on the orders screen, by normalised item name. */
    private static volatile Map<String, List<Seen>> seen = Map.of();

    private SellOfferMarks() {
    }

    public static boolean isOrdersScreen(String title) {
        return SkyblockText.strip(title).toLowerCase(Locale.ROOT).contains("bazaar orders");
    }

    /** One sell offer read off its menu item. */
    private record Offer(String itemName, String productId, long amount, long filled, double price, int stackSize) {
        long unsold() {
            return Math.max(0L, amount - filled);
        }
    }

    /**
     * What relisting an offer would make.
     *
     * @param lowest   the lowest sell offer on the Bazaar, null when there is none
     * @param estimate null when the offer is still the lowest or has no price
     * @param fits     how much of the unsold part fits in the inventory
     */
    private record Mark(Offer offer, Double lowest, RelistAdvisor.Estimate estimate, long fits) {
    }

    /**
     * Remembers every sell offer the orders screen shows, for the chat advice on a
     * cancel. Called once per frame while the screen is open.
     */
    public static void remember(AbstractContainerScreen<?> screen) {
        Map<String, List<Seen>> seenNow = new HashMap<>();
        long now = System.currentTimeMillis();
        for (Slot slot : screen.getMenu().slots) {
            if (!ContainerUtils.isContainerSlot(slot)) continue;
            Offer offer = parse(slot.getItem());
            if (offer == null) continue;
            seenNow.computeIfAbsent(RelistAdvisor.normalize(offer.itemName()), name -> new ArrayList<>())
                    .add(new Seen(offer.unsold(), holdingsOf(offer), now));
        }
        // Slots fill in over a few ticks; an empty pass keeps what was seen before.
        if (!seenNow.isEmpty()) seen = seenNow;
    }

    /**
     * Colours the background of one slot when it holds an undercut sell offer.
     * Called as the menu draws that slot, before its item, so the item stays on top;
     * the drawing is already offset to the menu's corner.
     */
    public static void drawBehind(GuiGraphicsExtractor graphics, Screen screen, Slot slot) {
        if (!ContainerUtils.isContainerSlot(slot) || screen.getTitle() == null
                || !isOrdersScreen(screen.getTitle().getString())) return;

        Mark mark = markFor(slot.getItem());
        if (mark == null || mark.estimate() == null) return;

        int color = switch (mark.estimate().verdict()) {
            case RELIST -> RELIST_COLOR;
            case FILL_ONLY -> FILL_ONLY_COLOR;
            case NOT_WORTH -> NOT_WORTH_COLOR;
        };
        graphics.fill(slot.x, slot.y, slot.x + SLOT_SIZE, slot.y + SLOT_SIZE, color);
    }

    /**
     * The offer of this item last seen on the orders screen, or null when none was
     * seen lately. With several, the one whose unsold amount is closest to what the
     * cancel refunded wins; more can sell between looking and cancelling.
     */
    public static Seen seenOffer(String itemName, long refunded) {
        List<Seen> offers = seen.get(RelistAdvisor.normalize(itemName));
        if (offers == null) return null;

        long now = System.currentTimeMillis();
        Seen best = null;
        for (Seen offer : offers) {
            if (now - offer.seenAt() > SEEN_FOR_MS) continue;
            if (best == null || Math.abs(offer.unsold() - refunded) < Math.abs(best.unsold() - refunded)) best = offer;
        }
        return best;
    }

    private static RelistAdvisor.Holdings holdingsOf(Offer offer) {
        return RelistAdvisor.holdingsWhileListed(recordedOffer(offer), offer.itemName());
    }

    /** Adds the relist numbers to a sell offer's tooltip on the orders screen. */
    public static void appendTooltip(ItemStack stack, List<Component> lines) {
        Screen screen = Minecraft.getInstance().screen;
        if (!(screen instanceof AbstractContainerScreen<?>) || screen.getTitle() == null
                || !isOrdersScreen(screen.getTitle().getString())) return;

        Mark mark = markFor(stack);
        if (mark == null) return;

        lines.add(Component.literal(""));
        lines.add(Component.literal("§d§lSkyblockFlips"));
        if (mark.lowest() == null) {
            lines.add(Component.literal("§8No Bazaar price, can't tell whether to relist"));
            return;
        }
        if (mark.estimate() == null) {
            lines.add(Component.literal("§7Yours is the lowest offer §8(" + price(mark.lowest()) + ")"));
            return;
        }

        RelistAdvisor.Estimate estimate = mark.estimate();
        Offer offer = mark.offer();
        lines.add(Component.literal("§cUndercut: §7lowest is §f" + price(mark.lowest())
                + " §7(yours " + price(offer.price()) + ")"));
        lines.add(Component.literal("§7Relist " + RelistAdvisor.amount(estimate.relisted()) + " at "
                + price(estimate.relistPrice()) + ": " + RelistAdvisor.signed(estimate.profit())));
        if (mark.fits() < offer.unsold()) {
            lines.add(Component.literal("§8only " + RelistAdvisor.amount(mark.fits()) + " of "
                    + RelistAdvisor.amount(offer.unsold()) + " unsold fit in your inventory"));
        }
        if (estimate.canFill()) {
            lines.add(Component.literal("§7Fill inventory: " + RelistAdvisor.signed(estimate.filledProfit())
                    + " §7(+" + RelistAdvisor.amount(estimate.fromStorage()) + " from storage)"));
        }
        lines.add(Component.literal(switch (estimate.verdict()) {
            case RELIST -> "§aWorth relisting";
            case FILL_ONLY -> "§eWorth relisting only if you fill your inventory";
            case NOT_WORTH -> "§7Not worth relisting";
        }));
    }

    private static Mark markFor(ItemStack stack) {
        Offer offer = parse(stack);
        if (offer == null) return null;

        Double lowest = RelistAdvisor.lowestSellOfferById(offer.productId());
        if (lowest == null || lowest >= offer.price() - SAME_PRICE) return new Mark(offer, lowest, null, 0L);

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return null;

        // The cancel puts the unsold items in the inventory, as far as they fit.
        RelistAdvisor.InventorySpace space = RelistAdvisor.spaceFor(minecraft.player.getInventory(),
                offer.productId(), offer.itemName(), offer.stackSize());
        long fits = Math.min(offer.unsold(), space.room());

        RelistAdvisor.Holdings holdings = holdingsOf(offer);
        RelistAdvisor.Estimate estimate = RelistAdvisor.estimate(lowest, fits, holdings.refundedCost(),
                holdings.inStorage(), holdings.storageCost(), space.room() - fits);
        return new Mark(offer, lowest, estimate, fits);
    }

    /**
     * The server's record of this offer. Names are compared without symbols, since a
     * gemstone's symbol is missing from the menu; with several offers of one item,
     * the one whose unsold amount is closest wins.
     */
    private static TraderPositions.SellOffer recordedOffer(Offer offer) {
        String wanted = RelistAdvisor.normalize(offer.itemName());
        TraderPositions.SellOffer best = null;
        for (TraderPositions.SellOffer candidate : ServerData.positions().sellOffers()) {
            if (!RelistAdvisor.normalize(candidate.itemName()).equals(wanted)) continue;
            if (best == null || Math.abs(candidate.unsold() - offer.unsold()) < Math.abs(best.unsold() - offer.unsold())) {
                best = candidate;
            }
        }
        return best;
    }

    private static Offer parse(ItemStack stack) {
        if (stack.isEmpty()) return null;
        String name = SkyblockText.strip(stack.getHoverName().getString());
        if (!name.startsWith(SELL_PREFIX)) return null;
        String productId = RelistAdvisor.skyblockId(stack);
        if (productId.isEmpty()) return null;

        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null) return null;

        long amount = -1L;
        long filled = 0L;
        double price = -1D;
        for (Component line : lore.lines()) {
            String text = SkyblockText.strip(line.getString());
            Matcher matcher;
            if ((matcher = OFFER_AMOUNT.matcher(text)).find()) {
                amount = Long.parseLong(matcher.group(1).replace(",", ""));
            } else if ((matcher = FILLED.matcher(text)).find()) {
                filled = Long.parseLong(matcher.group(1).replace(",", ""));
            } else if ((matcher = PRICE.matcher(text)).find()) {
                price = Double.parseDouble(matcher.group(1).replace(",", ""));
            }
        }
        if (amount < 0 || price < 0) return null;

        return new Offer(name.substring(SELL_PREFIX.length()).trim(), productId, amount, filled, price,
                stack.getMaxStackSize());
    }

    private static String price(double coins) {
        return String.format(Locale.ROOT, "%,.1f", coins);
    }
}
