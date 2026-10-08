package com.github.kargone.skyblockflips.flip;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Java port of the dashboard's crafting cost model - the functions behind the
 * "Auction Products" tab in {@code frontend/script.js}: {@code getRecipeCost},
 * {@code getProductUnitCost}, {@code getOwnedUnitCost}, {@code getCraftingMarketValue}
 * and {@code getCraftingCostBreakdown}.
 *
 * The point of this class is that the mod and the dashboard agree to the coin.
 * Where the JavaScript does something surprising, this follows it anyway and
 * says so in a comment rather than quietly improving on it.
 */
public final class CraftCostModel {

    public enum Mode {
        /** Materials bought off the top of the Bazaar's book. */
        INSTA_BUY,
        /** Materials bought with buy orders. */
        BUY_ORDER,
        /** Materials taken from what is already held. */
        OWNED
    }

    /**
     * Recipes are expected to be shallow. A cyclic one would recurse forever
     * because owned-cost lookups deliberately start a fresh visited set, exactly
     * as the dashboard does, so depth is capped instead.
     */
    private static final int MAX_DEPTH = 32;

    /** The section sign that starts a legacy colour code. */
    private static final char COLOUR_CODE = 0x00A7;

    private final ProductList products;
    private final MarketData market;

    public CraftCostModel(ProductList products, MarketData market) {
        this.products = products;
        this.market = market;
    }

    /** Port of the dashboard's {@code getCraftingCostBreakdown}. */
    public CostBreakdown breakdownOf(String name, InventorySnapshot investments) {
        double instaBuy = recipeCost(name, Mode.INSTA_BUY, null, new HashSet<>(), 0);
        double buyOrder = recipeCost(name, Mode.BUY_ORDER, null, new HashSet<>(), 0);
        double owned = recipeCost(name, Mode.OWNED, investments.copy(), new HashSet<>(), 0);
        double marketValue = marketValueOf(name);

        ProductEntry entry = products.get(name);
        // A "(forge)" product with a Bazaar listing is valued at its insta-sell
        // price in marketValueOf, so it pays Bazaar tax rather than the auction fee.
        boolean bazaarSale = name.contains("forge") && entry != null && entry.hasApiName()
                && market.bazaarQuote(entry.apiName()) != null;
        double fee = entry != null && entry.isAuctionSold() && !bazaarSale
                ? SkyblockFees.auctionFeeRate(marketValue)
                : SkyblockFees.bazaarTaxRate();
        double margin = marketValue * (1D - fee) - buyOrder;

        return new CostBreakdown(instaBuy, buyOrder, owned, marketValue, margin);
    }

    /**
     * The recipe tree under a product, flattened: every craftable material is
     * expanded into its own recipe until only uncraftable materials remain, and
     * those are summed across branches.
     *
     * This follows the same rule {@link #unitCost} does - a material with a recipe
     * is costed by its recipe, even when the Bazaar also sells it - so the material
     * totals add up to the dashboard's buy order cost.
     *
     * Each material is priced on its own, with a fresh copy of the investments for
     * the owned column, so one material cannot spend stock that another needs -
     * the same way the expanded sub-table costs them.
     */
    public RecipeTally tallyOf(String name, InventorySnapshot investments) {
        ProductEntry entry = products.get(name);
        if (entry == null || !entry.isCraftable()) return RecipeTally.EMPTY;

        Map<String, Long> rawQuantities = new LinkedHashMap<>();
        Map<String, Long> craftedQuantities = new LinkedHashMap<>();
        Map<String, Integer> craftedDepths = new LinkedHashMap<>();

        Set<String> path = new HashSet<>();
        path.add(normalizeName(name, entry.tier()));
        tally(entry, 1L, 1, path, rawQuantities, craftedQuantities, craftedDepths);

        List<RecipeTally.SubRecipe> subRecipes = new ArrayList<>(craftedQuantities.size());
        for (Map.Entry<String, Long> crafted : craftedQuantities.entrySet()) {
            ProductEntry craftedEntry = products.get(crafted.getKey());
            subRecipes.add(new RecipeTally.SubRecipe(crafted.getKey(),
                    itemId(crafted.getKey(), craftedEntry == null ? null : craftedEntry.tier()),
                    clampToInt(crafted.getValue()), craftedDepths.get(crafted.getKey())));
        }

        List<MaterialCost> materials = new ArrayList<>(rawQuantities.size());
        for (Map.Entry<String, Long> raw : rawQuantities.entrySet()) {
            String materialName = raw.getKey();
            int quantity = clampToInt(raw.getValue());

            double instaBuy = unitCostOf(materialName, Mode.INSTA_BUY, null, 0D);
            double buyOrder = unitCostOf(materialName, Mode.BUY_ORDER, null, 0D);
            double owned = unitCostOf(materialName, Mode.OWNED, investments.copy(), quantity);

            ProductEntry materialEntry = products.get(materialName);
            String apiName = materialEntry == null || !materialEntry.hasApiName() ? "" : materialEntry.apiName();
            // A non-empty api name is not proof of a Bazaar listing - a few entries
            // carry a display name there - so ask the market whether it has a book.
            boolean onBazaar = !apiName.isEmpty() && market.bazaarQuote(apiName) != null;

            boolean craftable = materialEntry != null && materialEntry.isCraftable();

            materials.add(new MaterialCost(materialName, quantity, apiName, onBazaar, craftable,
                    buyOrder * quantity, instaBuy * quantity, owned * quantity));
        }

        return new RecipeTally(List.copyOf(subRecipes), List.copyOf(materials));
    }

    /** Walks one recipe, adding {@code crafts} of it to the running totals. */
    private void tally(ProductEntry entry, long crafts, int depth, Set<String> path,
                       Map<String, Long> rawQuantities, Map<String, Long> craftedQuantities,
                       Map<String, Integer> craftedDepths) {
        for (Map.Entry<String, Integer> material : entry.craftingMaterials().entrySet()) {
            String materialName = material.getKey();
            long needed = crafts * material.getValue();

            ProductEntry materialEntry = products.get(materialName);
            String normalized = materialEntry == null ? null : normalizeName(materialName, materialEntry.tier());

            // A recipe that leads back to itself, or one nested past the cap, is
            // treated as bought rather than crafted so the walk always ends.
            boolean expand = materialEntry != null && materialEntry.isCraftable()
                    && depth <= MAX_DEPTH && !path.contains(normalized);
            if (!expand) {
                rawQuantities.merge(materialName, needed, Long::sum);
                continue;
            }

            craftedQuantities.merge(materialName, needed, Long::sum);
            craftedDepths.putIfAbsent(materialName, depth);

            path.add(normalized);
            tally(materialEntry, needed, depth + 1, path, rawQuantities, craftedQuantities, craftedDepths);
            path.remove(normalized);
        }
    }

    private static int clampToInt(long value) {
        return (int) Math.min(Integer.MAX_VALUE, value);
    }

    /** Port of the dashboard's {@code getProductUnitCost}, for callers outside the recursion. */
    public double unitCostOf(String name, Mode mode, InventorySnapshot snapshot, double amountNeeded) {
        return unitCost(name, mode, snapshot, amountNeeded, new HashSet<>(), 0);
    }

    /** Port of the dashboard's {@code getCraftingMarketValue}. */
    public double marketValueOf(String name) {
        ProductEntry entry = products.get(name);

        MarketData.BazaarQuote quote = bazaarQuoteOf(entry);
        if (quote != null) {
            if (name.contains("forge")) return quote.sellSummaryTop();
            if (quote.buySummaryTop() != 0D) return quote.buySummaryTop();
        }

        return lowestBinOf(name, entry == null ? null : entry.tier());
    }

    /**
     * The Bazaar book for a product, or null when it has none. An api name alone
     * does not mean the Bazaar carries it: auction items have Hypixel ids too.
     */
    private MarketData.BazaarQuote bazaarQuoteOf(ProductEntry entry) {
        return entry != null && entry.hasApiName() ? market.bazaarQuote(entry.apiName()) : null;
    }

    /**
     * Lowest BIN for a product, or 0 when there is none. Port of the dashboard's
     * {@code getLowestBin}: auction data is keyed by display name, never by api
     * name, and the " (forge)" on a recipe key is not part of the item's name.
     */
    private double lowestBinOf(String name, String tier) {
        Double lowestBin = market.lowestBin(normalizeName(CraftFlipRanking.displayNameOf(name), tier));
        return lowestBin == null ? 0D : lowestBin;
    }

    /** Port of the dashboard's {@code getRecipeCost}. */
    private double recipeCost(String name, Mode mode, InventorySnapshot snapshot, Set<String> path, int depth) {
        if (depth > MAX_DEPTH) return 0D;

        ProductEntry entry = products.get(name);
        if (entry == null || !entry.isCraftable()) {
            // The dashboard passes its visited set into the amount-needed slot here,
            // which lands as a fresh visited set and an amount of zero. Same effect.
            return unitCost(name, mode, snapshot, 0D, new HashSet<>(), depth + 1);
        }

        String normalized = normalizeName(name, entry.tier());
        if (path.contains(normalized)) return 0D;
        path.add(normalized);

        double totalCost = 0D;
        for (Map.Entry<String, Integer> material : entry.craftingMaterials().entrySet()) {
            String materialName = material.getKey();
            double remaining = material.getValue();

            Double ownedUnitCost = snapshot == null
                    ? null
                    : ownedUnitCost(materialName, snapshot, remaining, depth + 1);

            // A material that cannot be covered collapses the whole owned cost to
            // zero. The visited set is deliberately left dirty on this path, as in
            // the dashboard, so the caller sees the same state it would there.
            if (ownedUnitCost == null && mode == Mode.OWNED) return 0D;

            InventorySnapshot.Holding holding = snapshot == null ? null : snapshot.holdingOf(materialName);
            if (holding != null && holding.amount() > 0 && ownedUnitCost != null) {
                double used = Math.min(remaining, holding.amount());
                totalCost += used * ownedUnitCost;
                holding.consume(used, ownedUnitCost);
                remaining -= used;
            }

            if (remaining > 0) {
                totalCost += unitCost(materialName, mode, snapshot, 0D, path, depth + 1) * remaining;
            }
        }

        path.remove(normalized);
        return totalCost;
    }

    /** Port of the dashboard's {@code getProductUnitCost}. */
    private double unitCost(String name, Mode mode, InventorySnapshot snapshot, double amountNeeded, Set<String> path, int depth) {
        if (depth > MAX_DEPTH) return 0D;

        String normalized = normalizeName(name, null);
        if (path.contains(normalized)) return 0D;

        ProductEntry entry = products.get(name);

        Double ownedUnitCost = snapshot == null ? null : ownedUnitCost(name, snapshot, amountNeeded, depth + 1);
        if (ownedUnitCost != null) return ownedUnitCost;
        if (mode == Mode.OWNED) return 0D;

        if (entry != null && entry.isCraftable()) {
            return recipeCost(name, mode, snapshot, path, depth + 1);
        }

        MarketData.BazaarQuote quote = bazaarQuoteOf(entry);
        if (quote != null) {
            return mode == Mode.BUY_ORDER ? quote.sellSummaryTop() : quote.buySummaryTop();
        }

        Double lowestBin = market.lowestBin(normalized);
        return lowestBin == null ? 0D : lowestBin;
    }

    /**
     * Port of the dashboard's {@code getOwnedUnitCost}.
     *
     * @return the unit cost of covering this material from stock, or {@code null}
     *         when it cannot be covered and has to be bought.
     */
    private Double ownedUnitCost(String name, InventorySnapshot snapshot, double amountNeeded, int depth) {
        if (depth > MAX_DEPTH) return null;

        ProductEntry entry = products.get(name);
        if (entry != null && entry.isCraftable()) {
            // Something not in stock may still be craftable out of what is in stock.
            double craftCost = recipeCost(name, Mode.OWNED, snapshot, new HashSet<>(), depth + 1);
            if (craftCost > 0) return craftCost;
        }

        InventorySnapshot.Holding holding = snapshot.holdingOf(name);
        if (holding == null || holding.amount() == 0) return null;
        if (amountNeeded > 0 && holding.amount() < amountNeeded) return null;

        return holding.unitCost();
    }

    /**
     * The Skyblock item id for a product, as {@code /viewrecipe} wants it: the
     * display name upper cased with spaces and hyphens turned into underscores,
     * and the rarity appended when the product has one.
     *
     * <p>"Beastmaster Crest - EPIC" with tier EPIC becomes {@code BEASTMASTER_CREST_EPIC},
     * and "Razor-sharp Shark Tooth Necklace" becomes {@code RAZOR_SHARP_SHARK_TOOTH_NECKLACE}.
     * The rarity comes from the product's {@code tier} field rather than from the
     * suffix on the name, so the two can never disagree.
     */
    public static String itemId(String displayName, String tier) {
        String id = normalizeName(displayName, null)
                .toUpperCase()
                .replace(' ', '_')
                .replace('-', '_');

        return tier == null || tier.isEmpty() ? id : id + "_" + tier.toUpperCase();
    }

    /**
     * Port of the dashboard's {@code normalizeName}: strip colour codes, collapse
     * whitespace, drop an existing " - rarity" suffix, lowercase, then re-attach
     * the tier. Written without regular expressions so it reads the same in Java.
     */
    public static String normalizeName(String name, String tier) {
        if (name == null) name = "";

        StringBuilder cleaned = new StringBuilder(name.length());
        boolean lastWasSpace = false;
        for (int i = 0; i < name.length(); i++) {
            char current = name.charAt(i);

            if (current == COLOUR_CODE) {
                i++; // drop the colour code and the character it applies to
                continue;
            }
            if (Character.isWhitespace(current)) {
                if (!lastWasSpace) cleaned.append(' ');
                lastWasSpace = true;
                continue;
            }
            cleaned.append(current);
            lastWasSpace = false;
        }

        String result = cleaned.toString().trim();

        // Remove an existing rarity suffix: the leftmost " - " with something after it.
        int separator = result.indexOf(" - ");
        if (separator >= 0 && separator + 3 < result.length()) {
            result = result.substring(0, separator);
        }

        result = result.toLowerCase();
        return tier == null || tier.isEmpty() ? result : result + " - " + tier;
    }
}
