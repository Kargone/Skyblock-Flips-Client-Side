package com.github.kargone.skyblockflips.flip;

import java.util.Map;

/**
 * One row of the server's {@code product-list.json}.
 *
 * @param apiName            Bazaar API id, empty for items that only trade on the Auction House
 * @param type               {@code auction-product}, {@code forge-product}, {@code crafting-material}, ...
 * @param tier               rarity suffix used when looking the item up in lowest-BIN data, may be empty
 * @param craftingMaterials  material name to quantity, empty when the item is not craftable
 * @param secondsToCraft     forge time, 0 when not applicable
 */
public record ProductEntry(
        String apiName,
        String type,
        String tier,
        Map<String, Integer> craftingMaterials,
        int secondsToCraft
) {

    public static final String AUCTION_PRODUCT = "auction-product";
    public static final String FORGE_PRODUCT = "forge-product";

    public boolean hasApiName() {
        // The JSON carries "" for auction-only items, and the dashboard treats that
        // as falsy, so an empty string has to mean "not on the Bazaar" here too.
        return apiName != null && !apiName.isEmpty();
    }

    public boolean isCraftable() {
        return craftingMaterials != null && !craftingMaterials.isEmpty();
    }

    public boolean isAuctionSold() {
        return AUCTION_PRODUCT.equals(type) || FORGE_PRODUCT.equals(type);
    }
}
