package com.github.kargone.skyblockflips.util;

public class ScreenConstants {
    public static final String BAZAAR = " ➜ ";
    public static final String AUCTION_HOUSE = "Auction";
    public static final String FORGE = "The Forge";
    public static final String FORGE_SUB_MENU = "Select Process";
    // You can add more titles here as you discover them

    /**
     * Menus the Auction House overlay should attach to. Hypixel uses a handful of
     * titles for what is really the same browser - "Auctions Browser" for the
     * category view, "Auctions: <query>" after a search, and so on.
     */
    private static final String[] AUCTION_TITLES = {
            "auction",
            "auctions browser",
            "bin auction view",
            "your bids",
            "ending soon"
    };

    public static boolean isAuctionScreen(String title) {
        if (title == null || title.isEmpty()) return false;

        String normalized = SkyblockText.strip(title).toLowerCase();
        for (String candidate : AUCTION_TITLES) {
            if (normalized.contains(candidate)) return true;
        }
        return false;
    }
}
