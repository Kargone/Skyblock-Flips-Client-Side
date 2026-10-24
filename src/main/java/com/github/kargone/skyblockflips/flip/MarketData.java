package com.github.kargone.skyblockflips.flip;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * The payload of {@code GET /api/market-data}: current Bazaar order books, the
 * lowest BIN seen for each auction item, and every BIN price of the items being
 * auctioned.
 */
public final class MarketData {

    public static final MarketData EMPTY = new MarketData(Map.of(), Map.of(), Map.of(), 0L);

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
    /** BIN prices cheapest first, for the items the trader has up for auction, capped by the server. */
    private final Map<String, double[]> binLadders;
    private final long lastUpdated;

    private MarketData(Map<String, BazaarQuote> bazaarProducts, Map<String, Double> lowestBinPrices,
                       Map<String, double[]> binLadders, long lastUpdated) {
        this.bazaarProducts = bazaarProducts;
        this.lowestBinPrices = lowestBinPrices;
        this.binLadders = binLadders;
        this.lastUpdated = lastUpdated;
    }

    public BazaarQuote bazaarQuote(String apiName) {
        return bazaarProducts.get(apiName);
    }

    /** @return the lowest BIN for a normalised item name, or {@code null} when unknown. */
    public Double lowestBin(String normalizedName) {
        return lowestBinPrices.get(normalizedName);
    }

    /**
     * Where a BIN at this price sits among all BINs for the item, 1 being the
     * cheapest. The listing itself is in the scan, so only cheaper ones push it down.
     *
     * @return the position, -1 when it is past the end of the ladder the server
     *         sent, or 0 when there is no ladder for the item
     */
    public int binRank(String normalizedName, double price) {
        double[] ladder = binLadders.get(normalizedName);
        if (ladder == null || ladder.length == 0) return 0;

        int cheaper = 0;
        while (cheaper < ladder.length && ladder[cheaper] < price) cheaper++;
        return cheaper == ladder.length ? -1 : cheaper + 1;
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

        Map<String, double[]> ladders = new HashMap<>();
        JsonObject ladderObject = optionalObject(root, "binLadders");
        if (ladderObject != null) {
            for (Map.Entry<String, JsonElement> entry : ladderObject.entrySet()) {
                if (!entry.getValue().isJsonArray()) continue;
                JsonArray prices = entry.getValue().getAsJsonArray();
                double[] ladder = new double[prices.size()];
                int size = 0;
                for (JsonElement element : prices) {
                    Double price = asDouble(element);
                    if (price != null) ladder[size++] = price;
                }
                ladders.put(entry.getKey(), Arrays.copyOf(ladder, size));
            }
        }

        long updated = 0L;
        if (root.has("lastUpdated")) {
            Double updatedValue = asDouble(root.get("lastUpdated"));
            if (updatedValue != null) updated = updatedValue.longValue();
        }

        return new MarketData(Map.copyOf(products), Map.copyOf(lowestBins), Map.copyOf(ladders), updated);
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
