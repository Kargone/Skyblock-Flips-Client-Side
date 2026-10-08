package com.github.kargone.skyblockflips.flip;

import java.util.List;

/**
 * A product's recipe flattened all the way down.
 *
 * Every material that has a recipe of its own is expanded into that recipe,
 * recursively, until only things that cannot be crafted are left. Those are the
 * {@link #materials} - what actually has to be bought - with their quantities
 * summed across every branch that needs them. The craftable steps passed through
 * on the way down are kept in {@link #subRecipes}, so each can be looked up with
 * {@code /viewrecipe}.
 *
 * Quantities and costs are for one craft of the product.
 *
 * @param subRecipes craftable intermediates, in the order the walk first met them
 * @param materials  uncraftable materials with their total quantities
 */
public record RecipeTally(List<SubRecipe> subRecipes, List<MaterialCost> materials) {

    public static final RecipeTally EMPTY = new RecipeTally(List.of(), List.of());

    /**
     * One craftable step in the recipe tree.
     *
     * @param name     display name, as it appears in the product list
     * @param itemId   Skyblock item id, as {@code /viewrecipe} wants it
     * @param quantity how many of it one craft of the product needs in total
     * @param depth    how far down the tree it was first met, 1 for a direct material
     */
    public record SubRecipe(String name, String itemId, int quantity, int depth) {
    }

    /** Number of uncraftable items one craft needs, all materials together. */
    public long totalItems() {
        long total = 0;
        for (MaterialCost material : materials) total += material.quantity();
        return total;
    }

    public double totalBuyOrder() {
        double total = 0D;
        for (MaterialCost material : materials) total += material.buyOrder();
        return total;
    }

    public double totalInstaBuy() {
        double total = 0D;
        for (MaterialCost material : materials) total += material.instaBuy();
        return total;
    }
}
