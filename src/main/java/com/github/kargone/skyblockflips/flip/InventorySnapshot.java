package com.github.kargone.skyblockflips.flip;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.Map;

/**
 * What the trader already holds, built from the short and long term investment
 * buckets of {@code GET /api/trader-data} - the Java side of the dashboard's
 * {@code buildInvestmentSnapshot}.
 *
 * A costing pass consumes materials out of a snapshot as it walks a recipe, so
 * every pass works on its own {@link #copy()} and the original stays intact.
 */
public final class InventorySnapshot {

    public static final InventorySnapshot EMPTY = new InventorySnapshot(Map.of());

    /** Units held of one item and what they cost in total. */
    public static final class Holding {
        private double amount;
        private double totalCost;

        Holding(double amount, double totalCost) {
            this.amount = amount;
            this.totalCost = totalCost;
        }

        public double amount() {
            return amount;
        }

        public double unitCost() {
            return amount == 0 ? 0 : totalCost / amount;
        }

        void consume(double units, double unitCost) {
            amount -= units;
            totalCost -= units * unitCost;
        }
    }

    private final Map<String, Holding> holdings;

    private InventorySnapshot(Map<String, Holding> holdings) {
        this.holdings = holdings;
    }

    public Holding holdingOf(String itemName) {
        return holdings.get(itemName);
    }

    public boolean isEmpty() {
        return holdings.isEmpty();
    }

    /** A mutable duplicate, so one costing pass cannot spend another's materials. */
    public InventorySnapshot copy() {
        Map<String, Holding> copied = new HashMap<>(holdings.size());
        for (Map.Entry<String, Holding> entry : holdings.entrySet()) {
            copied.put(entry.getKey(), new Holding(entry.getValue().amount, entry.getValue().totalCost));
        }
        return new InventorySnapshot(copied);
    }

    public static InventorySnapshot parse(JsonObject traderData) {
        Map<String, Holding> holdings = new HashMap<>();

        for (String bucketName : new String[]{"short-term-investments", "long-term-investments"}) {
            JsonElement bucketElement = traderData.get(bucketName);
            if (bucketElement == null || !bucketElement.isJsonObject()) continue;

            for (Map.Entry<String, JsonElement> investment : bucketElement.getAsJsonObject().entrySet()) {
                if (!investment.getValue().isJsonObject()) continue;

                double amount = 0D;
                double totalCost = 0D;
                JsonElement subOrders = investment.getValue().getAsJsonObject().get("sub-orders");
                if (subOrders != null && subOrders.isJsonArray()) {
                    for (JsonElement subOrderElement : subOrders.getAsJsonArray()) {
                        if (!subOrderElement.isJsonObject()) continue;
                        JsonObject subOrder = subOrderElement.getAsJsonObject();

                        double partialAmount = asDouble(subOrder.get("partial-amount"), 0D);
                        double unitCost = subOrder.has("price-bought")
                                ? asDouble(subOrder.get("price-bought"), 0D)
                                : asDouble(subOrder.get("price-crafted"), 0D);

                        amount += partialAmount;
                        totalCost += partialAmount * unitCost;
                    }
                }

                Holding existing = holdings.get(investment.getKey());
                if (existing == null) {
                    holdings.put(investment.getKey(), new Holding(amount, totalCost));
                } else {
                    existing.amount += amount;
                    existing.totalCost += totalCost;
                }
            }
        }

        return new InventorySnapshot(holdings);
    }

    private static double asDouble(JsonElement element, double fallback) {
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) return fallback;
        try {
            return element.getAsDouble();
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
