package com.github.kargone.skyblockflips.flip;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The payload of {@code GET /api/product-list}, keyed by display name. */
public final class ProductList {

    public static final ProductList EMPTY = new ProductList(Map.of());

    private final Map<String, ProductEntry> products;

    private ProductList(Map<String, ProductEntry> products) {
        this.products = products;
    }

    public ProductEntry get(String displayName) {
        return products.get(displayName);
    }

    public boolean isEmpty() {
        return products.isEmpty();
    }

    /** Display names of every product of a type, sorted the way the dashboard sorts them. */
    public List<String> namesOfType(String type) {
        List<String> names = new ArrayList<>();
        for (Map.Entry<String, ProductEntry> entry : products.entrySet()) {
            if (type.equals(entry.getValue().type())) names.add(entry.getKey());
        }
        names.sort(Comparator.naturalOrder());
        return names;
    }

    public static ProductList parse(JsonObject root) {
        Map<String, ProductEntry> products = new LinkedHashMap<>();

        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            if (!entry.getValue().isJsonObject()) continue;
            JsonObject product = entry.getValue().getAsJsonObject();

            Map<String, Integer> materials = new LinkedHashMap<>();
            if (product.has("crafting-materials") && product.get("crafting-materials").isJsonObject()) {
                for (Map.Entry<String, JsonElement> material : product.getAsJsonObject("crafting-materials").entrySet()) {
                    try {
                        materials.put(material.getKey(), material.getValue().getAsInt());
                    } catch (RuntimeException ignored) {
                        // A material with an unreadable quantity is skipped rather than
                        // poisoning the whole recipe.
                    }
                }
            }

            // Materials must keep the order they appear in the JSON: a costing pass
            // spends held stock on the first material that wants it, so reordering
            // them changes the owned cost. Map.copyOf does not preserve order.
            products.put(entry.getKey(), new ProductEntry(
                    asString(product, "api-name"),
                    asString(product, "type"),
                    asString(product, "tier"),
                    Collections.unmodifiableMap(materials),
                    asInt(product, "seconds-to-craft")
            ));
        }

        return new ProductList(Collections.unmodifiableMap(products));
    }

    private static String asString(JsonObject object, String name) {
        JsonElement element = object.get(name);
        return element == null || element.isJsonNull() ? "" : element.getAsString();
    }

    private static int asInt(JsonObject object, String name) {
        JsonElement element = object.get(name);
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) return 0;
        try {
            return element.getAsInt();
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
