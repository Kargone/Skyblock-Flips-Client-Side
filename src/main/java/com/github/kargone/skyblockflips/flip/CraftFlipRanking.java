package com.github.kargone.skyblockflips.flip;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The dashboard's "Auction Products" and "Forge Products" tables, ranked and
 * ready to draw.
 *
 * The rankings are rebuilt on the background thread after every successful data
 * refresh and handed to the renderer as immutable lists, so drawing a frame
 * never walks a recipe tree.
 */
public final class CraftFlipRanking {

    /** Suffix the product list puts on a forge recipe whose output also trades on the Bazaar. */
    private static final String FORGE_SUFFIX = " (forge)";

    /**
     * One row: a product, what it costs to make versus what it sells for, and its
     * recipe tree flattened down to what has to be bought.
     *
     * @param name      the product list key, which is what every lookup wants
     * @param onBazaar  whether the market data has a Bazaar book for the product itself
     */
    public record RankedProduct(String name, ProductEntry entry, CostBreakdown breakdown,
                                RecipeTally tally, boolean onBazaar) {

        /** The name to show, without the " (forge)" the product list uses to tell recipes apart. */
        public String displayName() {
            return displayNameOf(name);
        }

        /**
         * The Skyblock item id for {@code /viewrecipe}. A forge product with an api
         * name uses it, since a forge key carries " (forge)" and some forge items
         * are named differently in game (Drill Motor is {@code DRILL_ENGINE}).
         */
        public String itemId() {
            if (ProductEntry.FORGE_PRODUCT.equals(entry.type()) && entry.hasApiName()) {
                return entry.apiName();
            }
            return CraftCostModel.itemId(displayName(), entry.tier());
        }
    }

    private static volatile List<RankedProduct> auctionProducts = List.of();
    private static volatile List<RankedProduct> forgeProducts = List.of();
    private static volatile List<RankedProduct> forgeAuctionProducts = List.of();
    private static volatile List<RankedProduct> forgeBazaarProducts = List.of();

    private CraftFlipRanking() {
    }

    /** Current auction ranking, best margin first. Empty until the first refresh lands. */
    public static List<RankedProduct> auctionProducts() {
        return auctionProducts;
    }

    /** Current forge ranking, best margin first. Empty until the first refresh lands. */
    public static List<RankedProduct> forgeProducts() {
        return forgeProducts;
    }

    /**
     * The forge ranking narrowed to products that sell on one market, best margin
     * first. Split once per refresh so the toggle costs nothing per frame.
     */
    public static List<RankedProduct> forgeProducts(boolean bazaar) {
        return bazaar ? forgeBazaarProducts : forgeAuctionProducts;
    }

    /** A product from either ranking by its product list key, or null when it is in neither. */
    public static RankedProduct find(String name) {
        for (RankedProduct product : auctionProducts) {
            if (product.name().equals(name)) return product;
        }
        for (RankedProduct product : forgeProducts) {
            if (product.name().equals(name)) return product;
        }
        return null;
    }

    public static void clear() {
        auctionProducts = List.of();
        forgeProducts = List.of();
        forgeAuctionProducts = List.of();
        forgeBazaarProducts = List.of();
    }

    /**
     * "Refined Diamond (forge)" becomes "Refined Diamond". The product list keeps
     * the forge recipe and the Bazaar listing as two entries, and this is the only
     * thing that tells them apart.
     */
    public static String displayNameOf(String name) {
        if (name.toLowerCase().endsWith(FORGE_SUFFIX)) {
            return name.substring(0, name.length() - FORGE_SUFFIX.length());
        }
        return name;
    }

    /**
     * Recomputes both rankings. Mirrors {@code renderCraftingSection}: one investment
     * snapshot is built for each table and each row costs against its own copy,
     * so two products cannot both spend the same materials.
     */
    public static void recompute(ProductList products, MarketData market, InventorySnapshot investments) {
        if (products.isEmpty()) {
            clear();
            return;
        }

        CraftCostModel model = new CraftCostModel(products, market);
        auctionProducts = rank(ProductEntry.AUCTION_PRODUCT, products, market, model, investments);
        List<RankedProduct> forge = rank(ProductEntry.FORGE_PRODUCT, products, market, model, investments);
        forgeProducts = forge;
        forgeAuctionProducts = forge.stream().filter(product -> !product.onBazaar()).toList();
        forgeBazaarProducts = forge.stream().filter(RankedProduct::onBazaar).toList();
    }

    private static List<RankedProduct> rank(String type, ProductList products, MarketData market,
                                            CraftCostModel model, InventorySnapshot investments) {
        List<RankedProduct> ranked = new ArrayList<>();

        // Only the entry of this type is picked up, so a forge item's plain-named
        // Bazaar entry never shows up as a second row.
        for (String name : products.namesOfType(type)) {
            ProductEntry entry = products.get(name);
            if (entry == null) continue;

            // As with materials, an api name alone is not proof of a Bazaar listing.
            boolean onBazaar = entry.hasApiName() && market.bazaarQuote(entry.apiName()) != null;

            ranked.add(new RankedProduct(name, entry,
                    model.breakdownOf(name, investments),
                    model.tallyOf(name, investments),
                    onBazaar));
        }

        ranked.sort(Comparator.comparingDouble((RankedProduct product) -> product.breakdown().margin()).reversed());
        return List.copyOf(ranked);
    }
}
