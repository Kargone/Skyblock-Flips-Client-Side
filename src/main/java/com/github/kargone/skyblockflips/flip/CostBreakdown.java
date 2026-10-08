package com.github.kargone.skyblockflips.flip;

/**
 * The numbers behind one row of the dashboard's "Auction Products" table.
 *
 * @param instaBuy    recipe cost if every material is bought instantly
 * @param buyOrder    recipe cost if every material is bought with buy orders
 * @param owned       recipe cost covered by materials already held, 0 when the recipe is not fully covered
 * @param marketValue what the crafted item sells for
 * @param margin      {@code marketValue * (1 - fee) - buyOrder}
 */
public record CostBreakdown(
        double instaBuy,
        double buyOrder,
        double owned,
        double marketValue,
        double margin
) {

    /** Extra cost of skipping buy orders, shown as the "(+x)" on the cost column. */
    public double instaBuyPremium() {
        return instaBuy - buyOrder;
    }

    /** Margin when the materials are bought instantly, shown as the "(x)" on the margin column. */
    public double instaBuyMargin() {
        return margin - instaBuyPremium();
    }
}
