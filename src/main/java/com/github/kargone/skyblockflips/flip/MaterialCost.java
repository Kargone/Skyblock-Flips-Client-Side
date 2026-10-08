package com.github.kargone.skyblockflips.flip;

/**
 * One row of the dashboard's "Crafting Materials" sub-table, the one that opens
 * when a product row is expanded.
 *
 * All three costs are for the whole {@link #quantity}, not per unit, which is how
 * the dashboard prints them.
 *
 * @param name        material display name, as it appears in the product list
 * @param quantity    how many the recipe needs
 * @param apiName     Bazaar API id, empty when the material is not a Bazaar item
 * @param onBazaar    whether the market data actually has a book for {@link #apiName}
 * @param craftable   whether this material has a recipe of its own, and so a breakdown to open
 * @param buyOrder    cost of the stack using buy orders
 * @param instaBuy    cost of the stack buying instantly
 * @param owned       cost already covered by held materials, 0 when none is held
 */
public record MaterialCost(
        String name,
        int quantity,
        String apiName,
        boolean onBazaar,
        boolean craftable,
        double buyOrder,
        double instaBuy,
        double owned
) {

    /** Extra cost of skipping buy orders, shown as the "(+x)" on the cost column. */
    public double instaBuyPremium() {
        return instaBuy - buyOrder;
    }
}
