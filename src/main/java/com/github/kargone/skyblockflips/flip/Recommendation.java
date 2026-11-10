package com.github.kargone.skyblockflips.flip;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * The payload of {@code GET /api/recommendation}: every buy order the server's
 * optimizer considered, grouped by order size bucket and ranked within each one,
 * and what it had to work with.
 *
 * The optimizer ({@code optimizer/main.py}) does the ranking; this only reads it,
 * keeping the server's order.
 *
 * @param available   false when the optimizer has not written a recommendation yet
 * @param generatedAt epoch millis the optimizer wrote this, 0 when unknown
 * @param qualified   how many orders pass the optimizer's margin and order book checks
 * @param loaded      false until the server has answered
 */
public record Recommendation(
        boolean available,
        long generatedAt,
        double purse,
        int availableSlots,
        double dailyCapRemaining,
        int qualified,
        List<Order> orders,
        boolean loaded
) {

    public static final Recommendation EMPTY = new Recommendation(false, 0L, 0D, 0, 0D, 0, List.of(), false);

    /**
     * One order to place.
     *
     * @param action         buy or sell
     * @param productName    product list name
     * @param quantity       units to list
     * @param orderPrice     coins per unit, the buy order's price
     * @param sellPrice      coins per unit it should sell for later (the current ask), 0 from an older server
     * @param unitProfit     coins per unit, after tax and expected price drift (the optimizer's margin)
     * @param bucket         the display bucket it is ranked in: 7k, 4k, 2k or 1k+500
     * @param qualified      false when it failed a check and is only ranked behind the ones that passed
     */
    public record Order(String action, String productName, long quantity, double orderPrice,
                        double sellPrice, double unitProfit, String bucket, boolean qualified) {
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
                        number(order, "order_price"),
                        number(order, "sell_price"),
                        number(order, "margin"),
                        string(order, "display_bucket"),
                        bool(order, "qualified")));
            }
        }

        return new Recommendation(
                bool(root, "available"),
                (long) number(root, "generated_at"),
                number(root, "purse"),
                (int) number(root, "available_slots"),
                number(root, "daily_cap_remaining"),
                (int) number(root, "n_qualified"),
                List.copyOf(orders),
                true);
    }

    private static boolean bool(JsonObject parent, String name) {
        JsonElement element = parent.get(name);
        return element != null && element.isJsonPrimitive() && element.getAsBoolean();
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
