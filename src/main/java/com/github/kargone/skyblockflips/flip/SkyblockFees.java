package com.github.kargone.skyblockflips.flip;

/**
 * Transaction fees, kept in step with the dashboard's constants at the top of
 * {@code frontend/script.js}. While Derpy's QUAD TAXES perk is active (quadruples
 * bazaar tax, and here also scales auction fees) {@link #derpyTaxMultiplier} is 4;
 * {@link ServerData} sets it from the server's mayor data, so it no longer needs
 * changing by hand.
 */
public final class SkyblockFees {

    public static final double BAZAAR_TAX_RATE = 0.01D;
    public static final double AUCTION_FEE_RATE_LOW = 0.01D;   // auctions selling for <= 10M
    public static final double AUCTION_FEE_RATE_MID = 0.02D;   // auctions selling for > 10M
    public static final double AUCTION_FEE_RATE_HIGH = 0.025D; // auctions selling for > 100M

    public static volatile double derpyTaxMultiplier = 1D;

    private SkyblockFees() {
    }

    public static double bazaarTaxRate() {
        return BAZAAR_TAX_RATE * derpyTaxMultiplier;
    }

    public static double auctionFeeRate(double price) {
        double baseRate = price > 100_000_000D ? AUCTION_FEE_RATE_HIGH
                : price > 10_000_000D ? AUCTION_FEE_RATE_MID
                : AUCTION_FEE_RATE_LOW;
        return baseRate * derpyTaxMultiplier;
    }
}
