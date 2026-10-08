package com.github.kargone.skyblockflips.util;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;

/**
 * Small text helpers shared by the overlays. Hypixel sends everything as legacy
 * formatted strings, so most of this is about getting back to plain text and
 * fitting numbers/names into a narrow panel.
 */
public final class SkyblockText {

    private SkyblockText() {
    }

    /** Removes legacy colour codes and collapses runs of whitespace. */
    public static String strip(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        return ChatFormatting.stripFormatting(raw).replaceAll("\\s+", " ").trim();
    }

    /**
     * Formats a coin amount exactly like the dashboard's {@code formatCoin}, so a
     * number read off the overlay matches the one on the web page character for
     * character: 1.20B, 12.40M, 980.50K, 412.
     */
    public static String coins(double amount) {
        boolean negative = amount < 0;
        double value = Math.abs(amount);
        String sign = negative ? "-" : "";

        if (value >= 1_000_000_000D) return sign + String.format("%.2fB", value / 1_000_000_000D);
        if (value >= 1_000_000D) return sign + String.format("%.2fM", value / 1_000_000D);
        if (value >= 1_000D) return sign + String.format("%.2fK", value / 1_000D);
        return sign + String.format("%,d", Math.round(value));
    }

    /** Same as {@link #coins(double)} but always shows the sign, for profit deltas. */
    public static String signedCoins(double amount) {
        return (amount >= 0 ? "+" : "") + coins(amount);
    }

    /** Shortens {@code text} until it fits inside {@code maxWidth} pixels. */
    public static String fit(String text, int maxWidth) {
        Font font = Minecraft.getInstance().font;
        if (font.width(text) <= maxWidth) return text;

        String ellipsis = "..";
        int budget = Math.max(0, maxWidth - font.width(ellipsis));
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            if (font.width(builder.toString() + text.charAt(i)) > budget) break;
            builder.append(text.charAt(i));
        }
        return builder + ellipsis;
    }

    /** Legacy colour code Hypixel uses for each rarity tier. */
    public static String rarityColor(String rarity) {
        if (rarity == null) return "§f";
        return switch (rarity) {
            case "UNCOMMON" -> "§a";
            case "RARE" -> "§9";
            case "EPIC" -> "§5";
            case "LEGENDARY" -> "§6";
            case "MYTHIC" -> "§d";
            case "DIVINE" -> "§b";
            case "SPECIAL", "VERY SPECIAL" -> "§c";
            default -> "§f";
        };
    }
}
