package com.github.kargone.skyblockflips.feature;

import com.github.kargone.skyblockflips.flip.InventorySnapshot;
import com.github.kargone.skyblockflips.flip.MarketData;
import com.github.kargone.skyblockflips.flip.ProductEntry;
import com.github.kargone.skyblockflips.flip.ServerData;
import com.github.kargone.skyblockflips.flip.SkyblockFees;
import com.github.kargone.skyblockflips.flip.TraderPositions;
import com.github.kargone.skyblockflips.util.ModConfig;
import com.github.kargone.skyblockflips.util.SkyblockText;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Says in chat whether a sell offer that was just cancelled is worth relisting.
 *
 * A relist is priced 0.1 under the lowest sell offer on the Bazaar, after tax,
 * against what the items cost. Two answers are given: relisting what the cancel put
 * in the inventory, and relisting after filling the rest of the inventory with the
 * same item from storage. Everything held in investments that is not in the
 * inventory counts as in storage. How much fits is read off the inventory itself,
 * so items that stack to 1 are counted right. It is worth relisting when the
 * profit reaches {@link ModConfig#minRelistProfit()}.
 *
 * The same sums back the marks on the Bazaar orders screen ({@link SellOfferMarks}).
 */
public final class RelistAdvisor {

    private static final Pattern CANCELLED = Pattern.compile(
            "\\[Bazaar] Cancelled! Refunded ([\\d,]+)x (.+?) from cancelling Sell Offer");
    private static final String PREFIX = "[SkyblockFlips] ";
    /** Undercut on a relist: 0.1 coins under the lowest sell offer. */
    private static final double UNDERCUT = 0.1D;
    /** The refunded items reach the inventory a moment after the chat message. */
    private static final long INVENTORY_DELAY_MS = 750L;
    private static final int MAIN_INVENTORY_SLOTS = 36;
    private static final int DEFAULT_STACK_SIZE = 64;

    private static final ScheduledExecutorService DELAY = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "SkyblockFlips-RelistAdvisor");
        thread.setDaemon(true);
        return thread;
    });

    /**
     * The last cost seen for each item, by normalised name. The trader data can lag
     * behind a new or relisted offer, and without this the item would look free.
     */
    private static final Map<String, Double> LAST_KNOWN_COST = new ConcurrentHashMap<>();

    private RelistAdvisor() {
    }

    public enum Verdict { RELIST, FILL_ONLY, NOT_WORTH }

    /**
     * What a relist would make.
     *
     * @param relisted     items relisted from the cancelled offer
     * @param fromStorage  items topped up from storage, 0 when that would lose money
     * @param profit       profit on the relisted items alone
     * @param filledProfit profit with the storage top-up added
     */
    public record Estimate(double relistPrice, long relisted, long fromStorage, double profit, double filledProfit) {

        public boolean canFill() {
            return fromStorage > 0;
        }

        public Verdict verdict() {
            double threshold = ModConfig.minRelistProfit();
            if (profit >= threshold) return Verdict.RELIST;
            if (canFill() && filledProfit >= threshold) return Verdict.FILL_ONLY;
            return Verdict.NOT_WORTH;
        }
    }

    /**
     * @param lowestOffer  the lowest sell offer on the Bazaar
     * @param relisted     items from the cancelled offer that would be relisted
     * @param relistedCost what each of those cost
     * @param inStorage    the same item held in storage
     * @param storageCost  what each stored item cost
     * @param room         how many more fit in the inventory besides the relisted ones
     */
    static Estimate estimate(double lowestOffer, long relisted, double relistedCost,
                             double inStorage, double storageCost, long room) {
        double relistPrice = Math.max(0D, Math.round((lowestOffer - UNDERCUT) * 10D) / 10D);
        double netPrice = relistPrice * (1 - SkyblockFees.bazaarTaxRate());

        double profit = (netPrice - relistedCost) * relisted;
        double storageUnitProfit = netPrice - storageCost;
        // Topping up only helps when the stored items would sell at a profit.
        long fromStorage = storageUnitProfit > 0
                ? Math.max(0L, Math.min((long) Math.floor(inStorage), room)) : 0L;
        return new Estimate(relistPrice, relisted, fromStorage, profit, profit + storageUnitProfit * fromStorage);
    }

    /** Called with every chat message; acts only on a sell offer cancel. */
    public static void onSystemMessage(String content) {
        Matcher matcher = CANCELLED.matcher(content);
        if (!matcher.find()) return;

        int refunded = Integer.parseInt(matcher.group(1).replace(",", ""));
        String itemName = matcher.group(2).trim();

        // What the orders screen showed before the cancel is trusted first, since the
        // trader data may not know about recent fills. Otherwise the trader data is
        // read now, before the server has recorded this cancel and the mod has
        // fetched it. The inventory is read once the items are in it.
        SellOfferMarks.Seen seen = SellOfferMarks.seenOffer(itemName, refunded);
        Holdings holdings = seen != null ? seen.holdings() : holdingsAtCancel(itemName, refunded);
        Minecraft minecraft = Minecraft.getInstance();
        DELAY.schedule(() -> minecraft.execute(() -> advise(itemName, refunded, holdings)),
                INVENTORY_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    /**
     * What is in storage, and what the refunded items and the stored ones cost.
     *
     * The server moves a cancelled offer's items back into investments. If the cached
     * trader data still has the offer, the cancel has not reached it yet and the
     * whole investment is in storage; otherwise the refunded items are already
     * counted in it and are taken back out.
     */
    record Holdings(double inStorage, double refundedCost, double storageCost) {

        /** False when nothing says what the items cost; a cost of 0 would show the full price as profit. */
        boolean costKnown() {
            return refundedCost > 0D;
        }
    }

    private static Holdings holdingsAtCancel(String itemName, int refunded) {
        String wanted = normalize(itemName);
        TraderPositions.SellOffer offer = null;
        for (TraderPositions.SellOffer candidate : ServerData.positions().sellOffers()) {
            if (normalize(candidate.itemName()).equals(wanted) && Math.round(candidate.unsold()) == refunded) {
                offer = candidate;
                break;
            }
        }

        if (offer != null) return holdingsWhileListed(offer, itemName);

        InventorySnapshot.Holding holding = holdingNamed(itemName);
        double held = holding == null ? 0D : holding.amount();
        double heldCost = holding == null ? 0D : holding.unitCost();
        double refundedCost = knownCost(itemName, heldCost);
        return new Holdings(Math.max(0D, held - refunded), refundedCost, heldCost > 0D ? heldCost : refundedCost);
    }

    /**
     * Holdings for an offer that is still up: its whole investment is in storage, and
     * its items cost what the server recorded for it. Without a recorded offer the
     * stored items' cost stands in for it, and failing that the last cost seen.
     */
    static Holdings holdingsWhileListed(TraderPositions.SellOffer offer, String itemName) {
        InventorySnapshot.Holding holding = holdingNamed(offer == null ? itemName : offer.investmentName());
        double held = holding == null ? 0D : holding.amount();
        double heldCost = holding == null ? 0D : holding.unitCost();
        double refundedCost = knownCost(itemName, offer != null && offer.unitCost() > 0D ? offer.unitCost() : heldCost);
        return new Holdings(held, refundedCost, heldCost > 0D ? heldCost : refundedCost);
    }

    /**
     * The investment of this name, compared without symbols when there is no exact
     * match, since a gemstone's symbol is missing from the orders menu.
     */
    private static InventorySnapshot.Holding holdingNamed(String name) {
        InventorySnapshot.Holding exact = ServerData.investments().holdingOf(name);
        if (exact != null) return exact;

        String wanted = normalize(name);
        for (TraderPositions.Investment investment : ServerData.positions().investments()) {
            if (!normalize(investment.name()).equals(wanted)) continue;
            InventorySnapshot.Holding holding = ServerData.investments().holdingOf(investment.name());
            if (holding != null) return holding;
        }
        return null;
    }

    /** A cost found now is remembered; with none, the last one remembered is used, or 0. */
    private static double knownCost(String itemName, double cost) {
        String key = normalize(itemName);
        if (cost > 0D) {
            LAST_KNOWN_COST.put(key, cost);
            return cost;
        }
        return LAST_KNOWN_COST.getOrDefault(key, 0D);
    }

    private static void advise(String itemName, int refunded, Holdings holdings) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;

        Double lowestOffer = lowestSellOffer(itemName);
        if (lowestOffer == null) {
            say("§7" + itemName + ": §8no Bazaar price, can't tell whether to relist");
            return;
        }
        if (!holdings.costKnown()) {
            say("§7" + itemName + ": §8no cost on record, can't tell whether to relist");
            return;
        }

        InventorySpace space = spaceFor(minecraft.player.getInventory(), "", itemName, DEFAULT_STACK_SIZE);
        // Hypixel's refund count is exact; the inventory may hold more of the item
        // from before, which is not part of this offer.
        int inHand = refunded;
        Estimate estimate = estimate(lowestOffer, inHand, holdings.refundedCost(),
                holdings.inStorage(), holdings.storageCost(), space.room());

        String price = String.format(Locale.ROOT, "%,.1f", estimate.relistPrice());
        String fill = "§7fill inventory: " + signed(estimate.filledProfit()) + " §7(+" + amount(estimate.fromStorage())
                + " from storage, " + amount(inHand + estimate.fromStorage()) + " total)";

        switch (estimate.verdict()) {
            case RELIST -> say("§aRelist §f" + itemName + " §aat " + price + ": " + signed(estimate.profit())
                    + " §7(" + amount(inHand) + ")" + (estimate.canFill() ? " §8| " + fill : ""));
            case FILL_ONLY -> say("§eRelist §f" + itemName + " §eonly if you fill your inventory: "
                    + signed(estimate.filledProfit()) + " §7at " + price + " (+" + amount(estimate.fromStorage())
                    + " from storage, " + signed(estimate.profit()) + " §7without)");
            case NOT_WORTH -> say("§7Not worth relisting §f" + itemName + "§7: " + signed(estimate.profit())
                    + " §7at " + price + (estimate.canFill() ? " §8| " + fill : ""));
        }
    }

    /** The lowest sell offer on the Bazaar for an item, or null when it has no listing. */
    private static Double lowestSellOffer(String itemName) {
        ProductEntry entry = ServerData.products().get(itemName);
        if (entry == null || !entry.hasApiName()) return null;
        return lowestSellOfferById(entry.apiName());
    }

    /** The same, looked up by Bazaar product id. */
    static Double lowestSellOfferById(String productId) {
        MarketData.BazaarQuote quote = ServerData.market().bazaarQuote(productId);
        // buy_summary is the book of sell offers, cheapest first.
        return quote == null || quote.buySummaryTop() <= 0D ? null : quote.buySummaryTop();
    }

    /**
     * @param held how many of the item the inventory holds
     * @param room how many more fit: the free part of its stacks plus every empty slot
     */
    record InventorySpace(int held, long room) {
    }

    /**
     * @param skyblockId   matches stacks by Skyblock id when not empty, otherwise by name
     * @param defaultStack the stack size used when the inventory holds none of the item
     */
    static InventorySpace spaceFor(Inventory inventory, String skyblockId, String itemName, int defaultStack) {
        String wanted = normalize(itemName);
        int held = 0;
        int partialRoom = 0;
        int emptySlots = 0;
        int stackSize = 0;

        for (int slot = 0; slot < MAIN_INVENTORY_SLOTS; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) {
                emptySlots++;
            } else if (skyblockId.isEmpty()
                    ? normalize(stack.getHoverName().getString()).equals(wanted)
                    : skyblockId.equals(skyblockId(stack))) {
                held += stack.getCount();
                partialRoom += stack.getMaxStackSize() - stack.getCount();
                stackSize = stack.getMaxStackSize();
            }
        }

        if (stackSize == 0) stackSize = defaultStack;
        return new InventorySpace(held, (long) emptySlots * stackSize + partialRoom);
    }

    static String skyblockId(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        return customData == null ? "" : customData.copyTag().getStringOr("id", "");
    }

    /**
     * Names compared without colours or symbols, since a gemstone's name starts with
     * a symbol that does not always survive being written to the trader data.
     */
    static String normalize(String name) {
        return SkyblockText.strip(name).replaceAll("[^\\p{L}\\p{N} '-]", "").trim().toLowerCase(Locale.ROOT);
    }

    static String signed(double coins) {
        return (coins >= 0 ? "§a" : "§c") + SkyblockText.signedCoins(coins);
    }

    static String amount(long amount) {
        return String.format(Locale.ROOT, "%,d", amount);
    }

    /** Shown like a server message. It starts with the mod's prefix, so the chat hook skips it. */
    private static void say(String text) {
        Minecraft.getInstance().getChatListener().handleSystemMessage(Component.literal(PREFIX + text), false);
    }
}
