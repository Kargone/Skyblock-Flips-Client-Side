package com.github.kargone.skyblockflips.flip;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * The payload of {@code GET /api/recommendation}: the orders the server's optimizer
 * says to place next, most expected profit first, and what it had to work with.
 *
 * The optimizer ({@code optimizer/main.py}) works all of that out; this only reads it.
 *
 * @param available  false when the optimizer has not written a recommendation yet
 * @param generatedAt epoch millis the optimizer wrote this, 0 when unknown
 * @param loaded     false until the server has answered
 */
public record Recommendation(
        boolean available,
        long generatedAt,
        double purse,
        int availableSlots,
        double dailyCapRemaining,
        double totalExpectedProfit,
        List<Order> orders,
        boolean loaded
) {

    public static final Recommendation EMPTY = new Recommendation(false, 0L, 0D, 0, 0D, 0D, List.of(), false);

    /**
     * One order to place.
     *
     * @param action         buy or sell
     * @param productName    product list name
     * @param quantity       units to list: the full holding for a sell, the expected fill for a buy
     * @param expectedFill   units expected to fill before the next flip, which is what the profit is on
     * @param orderPrice     coins per unit
     * @param expectedProfit coins, after tax
     */
    public record Order(String action, String productName, long quantity, double expectedFill,
                        double orderPrice, double expectedProfit) {
    }

    public static Recommendation parse(JsonObject root) {
        List<Order> orders = new ArrayList<>();
        JsonElement orderArray = root.get("orders");
        if (orderArray != null && orderArray.isJsonArray()) {
            for (JsonElement element : orderArray.getAsJsonArray()) {
                if (!element.isJsonObject()) continue;
                JsonObject order = element.getAsJsonObject();
                String product = string(order, "product_name");
                if (product.isEmpty()) continue;

                orders.add(new Order(
                        string(order, "action"),
                        product,
                        (long) number(order, "quantity"),
                        number(order, "expected_fill"),
                        number(order, "order_price"),
                        number(order, "expected_profit")));
            }
        }

        JsonElement available = root.get("available");
        return new Recommendation(
                available != null && available.isJsonPrimitive() && available.getAsBoolean(),
                (long) number(root, "generated_at"),
                number(root, "purse"),
                (int) number(root, "available_slots"),
                number(root, "daily_cap_remaining"),
                number(root, "total_expected_profit"),
                List.copyOf(orders),
                true);
    }

    private static String string(JsonObject parent, String name) {
        JsonElement element = parent.get(name);
        return element == null || !element.isJsonPrimitive() ? "" : element.getAsString();
    }

    private static double number(JsonObject parent, String name) {
        JsonElement element = parent.get(name);
        if (element == null || !element.isJsonPrimitive()) return 0D;
        try {
            return element.getAsDouble();
        } catch (NumberFormatException e) {
            return 0D;
        }
    }
}
