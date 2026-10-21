package com.github.kargone.skyblockflips.flip;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The trader's open positions as the server recorded them: the auctions in
 * {@code auction-house-overview}, the Bazaar sell offers in
 * {@code sell-order-overview} and the lots in the short and long term
 * investment buckets of {@code GET /api/trader-data}. Nothing here is priced;
 * {@link PositionValues} does that once market data is in.
 *
 * @param loaded false until trader data has arrived at least once
 */
public record TraderPositions(List<Auction> auctions, List<SellOffer> sellOffers, List<Investment> investments,
                              boolean loaded) {

    public static final TraderPositions EMPTY = new TraderPositions(List.of(), List.of(), List.of(), false);

    /** One auction, sold or still up. */
    public record Auction(String itemName, double materialCost, double priceSelling, boolean sold) {
    }

    /**
     * One open Bazaar sell offer.
     *
     * @param investmentName the investment it was taken from, which differs from the
     *                       item name for forged stock ("Refined Diamond (forge)")
     * @param unsold         what the server counts as still for sale, which is what a
     *                       cancel refunds
     * @param unitCost       what its items cost, averaged over its lots
     */
    public record SellOffer(String itemName, String investmentName, double unsold, double unitCost) {
    }

    /** One lot of an investment, priced at what it was bought or crafted for. */
    public record Lot(double amount, double price, long timeBought) {
    }

    /** Everything held of one item in one bucket. */
    public record Investment(String name, boolean longTerm, List<Lot> lots) {
    }

    public static TraderPositions parse(JsonObject traderData) {
        List<Auction> auctions = new ArrayList<>();
        JsonElement overview = traderData.get("auction-house-overview");
        if (overview != null && overview.isJsonObject()) {
            JsonElement list = overview.getAsJsonObject().get("auctions");
            if (list != null && list.isJsonArray()) {
                for (JsonElement element : list.getAsJsonArray()) {
                    if (!element.isJsonObject()) continue;
                    JsonObject auction = element.getAsJsonObject();

                    String name = asString(auction.get("item-name"));
                    if (name.isEmpty()) continue;
                    auctions.add(new Auction(name,
                            asDouble(auction.get("material-cost")),
                            asDouble(auction.get("price-selling")),
                            auction.has("sold") && auction.get("sold").isJsonPrimitive() && auction.get("sold").getAsBoolean()));
                }
            }
        }

        List<Investment> investments = new ArrayList<>();
        addBucket(traderData, "short-term-investments", false, investments);
        addBucket(traderData, "long-term-investments", true, investments);

        return new TraderPositions(List.copyOf(auctions), parseSellOffers(traderData), List.copyOf(investments), true);
    }

    private static List<SellOffer> parseSellOffers(JsonObject traderData) {
        JsonElement overview = traderData.get("sell-order-overview");
        if (overview == null || !overview.isJsonObject()) return List.of();
        JsonElement list = overview.getAsJsonObject().get("sell-orders");
        if (list == null || !list.isJsonArray()) return List.of();

        List<SellOffer> offers = new ArrayList<>();
        for (JsonElement element : list.getAsJsonArray()) {
            if (!element.isJsonObject()) continue;
            JsonObject offer = element.getAsJsonObject();

            String name = asString(offer.get("item-name"));
            if (name.isEmpty()) continue;
            String investmentName = asString(offer.get("investment-name"));

            // The server's sellOrderCancel counts the unsold amount the same way.
            double unsold = 0D;
            double totalCost = 0D;
            JsonElement subOrders = offer.get("sub-orders");
            if (subOrders != null && subOrders.isJsonArray()) {
                for (JsonElement subElement : subOrders.getAsJsonArray()) {
                    if (!subElement.isJsonObject()) continue;
                    JsonObject subOrder = subElement.getAsJsonObject();
                    double amount = asDouble(subOrder.get("partial-selling-amount"));
                    unsold += amount;
                    totalCost += amount * asDouble(subOrder.get("price-bought"));
                }
            }
            offers.add(new SellOffer(name, investmentName.isEmpty() ? name : investmentName,
                    unsold, unsold == 0D ? 0D : totalCost / unsold));
        }
        return List.copyOf(offers);
    }

    private static void addBucket(JsonObject traderData, String bucketName, boolean longTerm, List<Investment> into) {
        JsonElement bucket = traderData.get(bucketName);
        if (bucket == null || !bucket.isJsonObject()) return;

        for (Map.Entry<String, JsonElement> investment : bucket.getAsJsonObject().entrySet()) {
            if (!investment.getValue().isJsonObject()) continue;

            List<Lot> lots = new ArrayList<>();
            JsonElement subOrders = investment.getValue().getAsJsonObject().get("sub-orders");
            if (subOrders != null && subOrders.isJsonArray()) {
                for (JsonElement element : subOrders.getAsJsonArray()) {
                    if (!element.isJsonObject()) continue;
                    JsonObject subOrder = element.getAsJsonObject();

                    // The dashboard's `price-bought || price-crafted`: a zero bought
                    // price falls through to the crafted one.
                    double price = asDouble(subOrder.get("price-bought"));
                    if (price == 0D) price = asDouble(subOrder.get("price-crafted"));

                    lots.add(new Lot(asDouble(subOrder.get("partial-amount")), price,
                            (long) asDouble(subOrder.get("time-bought"))));
                }
            }
            into.add(new Investment(investment.getKey(), longTerm, List.copyOf(lots)));
        }
    }

    private static String asString(JsonElement element) {
        return element == null || element.isJsonNull() || !element.isJsonPrimitive() ? "" : element.getAsString();
    }

    private static double asDouble(JsonElement element) {
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) return 0D;
        try {
            return element.getAsDouble();
        } catch (NumberFormatException e) {
            return 0D;
        }
    }
}
