package com.github.kargone.skyblockflips.flip;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.Map;

/**
 * The payload of {@code GET /api/market-data}: current Bazaar order books plus
 * the lowest BIN seen for each auction item.
 */
public final class MarketData {

    public static final MarketData EMPTY = new MarketData(Map.of(), Map.of(), 0L);

    /**
     * Top of book for one Bazaar product.
     *
     * Named after the Hypixel API arrays rather than after what they mean, because
     * the dashboard reads them in the order Hypixel defines and any renaming here
     * would be a chance to silently swap the two.
     *
     * @param buySummaryTop  first {@code buy_summary} price level
     * @param sellSummaryTop first {@code sell_summary} price level
     */
    public record BazaarQuote(double buySummaryTop, double sellSummaryTop) {
    }

    private final Map<String, BazaarQuote> bazaarProducts;
    private final Map<String, Double> lowestBinPrices;
    private final long lastUpdated;

    private MarketData(Map<String, BazaarQuote> bazaarProducts, Map<String, Double> lowestBinPrices, long lastUpdated) {
        this.bazaarProducts = bazaarProducts;
        this.lowestBinPrices = lowestBinPrices;
        this.lastUpdated = lastUpdated;
    }

    public BazaarQuote bazaarQuote(String apiName) {
        return bazaarProducts.get(apiName);
    }

    /** @return the lowest BIN for a normalised item name, or {@code null} when unknown. */
    public Double lowestBin(String normalizedName) {
        return lowestBinPrices.get(normalizedName);
    }

    public long lastUpdated() {
        return lastUpdated;
    }

    public boolean isEmpty() {
        return bazaarProducts.isEmpty() && lowestBinPrices.isEmpty();
    }

    public static MarketData parse(JsonObject root) {
        Map<String, BazaarQuote> products = new HashMap<>();
        JsonObject bazaar = optionalObject(root, "bazaarProducts");
        if (bazaar != null) {
            for (Map.Entry<String, JsonElement> entry : bazaar.entrySet()) {
                if (!entry.getValue().isJsonObject()) continue;
                JsonObject product = entry.getValue().getAsJsonObject();
                products.put(entry.getKey(), new BazaarQuote(
                        topOfBook(product, "buy_summary"),
                        topOfBook(product, "sell_summary")
                ));
            }
        }

        Map<String, Double> lowestBins = new HashMap<>();
        JsonObject bins = optionalObject(root, "lowestBinPrices");
        if (bins != null) {
            for (Map.Entry<String, JsonElement> entry : bins.entrySet()) {
                Double price = asDouble(entry.getValue());
                if (price != null) lowestBins.put(entry.getKey(), price);
            }
        }

        long updated = 0L;
        if (root.has("lastUpdated")) {
            Double updatedValue = asDouble(root.get("lastUpdated"));
            if (updatedValue != null) updated = updatedValue.longValue();
        }

        return new MarketData(Map.copyOf(products), Map.copyOf(lowestBins), updated);
    }

    /** First price level of a Bazaar summary array, or 0 when the book is empty. */
    private static double topOfBook(JsonObject product, String summaryName) {
        if (!product.has(summaryName) || !product.get(summaryName).isJsonArray()) return 0D;

        var summary = product.getAsJsonArray(summaryName);
        if (summary.isEmpty() || !summary.get(0).isJsonObject()) return 0D;

        Double price = asDouble(summary.get(0).getAsJsonObject().get("pricePerUnit"));
        return price == null ? 0D : price;
    }

    private static JsonObject optionalObject(JsonObject root, String name) {
        JsonElement element = root.get(name);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private static Double asDouble(JsonElement element) {
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) return null;
        try {
            return element.getAsDouble();
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
