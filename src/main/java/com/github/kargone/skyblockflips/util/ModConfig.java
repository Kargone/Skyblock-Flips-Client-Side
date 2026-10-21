package com.github.kargone.skyblockflips.util;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Settings from {@code config/skyblockflips.json} in the game directory, the same
 * file the panel positions are saved in.
 *
 * <p>{@code serverUrl} is where the Skyblock Flips server runs. It defaults to this
 * computer; to use a server elsewhere on the network, set it to that machine, e.g.
 * {@code "serverUrl": "http://192.168.1.50:8000"}, and restart the game.
 *
 * <p>{@code bigInvestmentWorth} is how many coins an investment has to be worth, at
 * its current sell price, to count as a big long term one: those are left out of
 * the Current Investments panel and listed in the Market Reminders window instead.
 *
 * <p>{@code minInvestmentProfit} is the least profit, over all its lots, a short term
 * investment needs to be listed in the Current Investments panel.
 *
 * <p>{@code minRelistProfit} is the least profit a cancelled sell offer has to make
 * relisted for the mod to say it is worth relisting.
 *
 * <p>Any key missing from the file is written in with its default the first time
 * the mod starts, so it is there to edit.
 */
public final class ModConfig {

    public static final String DEFAULT_SERVER_URL = "http://192.168.0.15:8000";
    public static final double DEFAULT_BIG_INVESTMENT_WORTH = 200_000_000D;
    public static final double DEFAULT_MIN_INVESTMENT_PROFIT = 1_500_000D;
    public static final double DEFAULT_MIN_RELIST_PROFIT = 2_000_000D;

    private static final String CONFIG_NAME = "skyblockflips.json";
    private static final String SERVER_URL_KEY = "serverUrl";
    private static final String BIG_INVESTMENT_WORTH_KEY = "bigInvestmentWorth";
    private static final String MIN_INVESTMENT_PROFIT_KEY = "minInvestmentProfit";
    private static final String MIN_RELIST_PROFIT_KEY = "minRelistProfit";

    private record Settings(String serverUrl, double bigInvestmentWorth, double minInvestmentProfit,
                            double minRelistProfit) {
    }

    private static volatile Settings settings = null;

    private ModConfig() {
    }

    /** The server's base address, without a trailing slash. Read once, on first use. */
    public static String serverUrl() {
        return settings().serverUrl();
    }

    /** Coins an investment must be worth to count as a big long term one. Read once, on first use. */
    public static double bigInvestmentWorth() {
        return settings().bigInvestmentWorth();
    }

    /** Least all-lots profit a short term investment needs to be listed. Read once, on first use. */
    public static double minInvestmentProfit() {
        return settings().minInvestmentProfit();
    }

    /** Least profit a relisted sell offer has to make to be worth relisting. Read once, on first use. */
    public static double minRelistProfit() {
        return settings().minRelistProfit();
    }

    private static Settings settings() {
        Settings loaded = settings;
        if (loaded == null) {
            synchronized (ModConfig.class) {
                if (settings == null) settings = load();
                loaded = settings;
            }
        }
        return loaded;
    }

    private static Settings load() {
        Path path = configPath();
        String serverUrl = DEFAULT_SERVER_URL;
        double bigInvestmentWorth = DEFAULT_BIG_INVESTMENT_WORTH;
        double minInvestmentProfit = DEFAULT_MIN_INVESTMENT_PROFIT;
        double minRelistProfit = DEFAULT_MIN_RELIST_PROFIT;

        try {
            JsonObject root = new JsonObject();
            if (Files.exists(path)) {
                var parsed = JsonParser.parseString(Files.readString(path));
                if (parsed.isJsonObject()) root = parsed.getAsJsonObject();
            }
            boolean missingKey = false;

            String configured = readString(root, SERVER_URL_KEY);
            while (configured.endsWith("/")) configured = configured.substring(0, configured.length() - 1);
            if (configured.isEmpty()) {
                root.addProperty(SERVER_URL_KEY, DEFAULT_SERVER_URL);
                missingKey = true;
            } else {
                serverUrl = configured;
                System.out.println("[SkyblockFlips] Using server " + serverUrl);
            }

            Double worth = readNumber(root, BIG_INVESTMENT_WORTH_KEY);
            if (worth == null) {
                root.addProperty(BIG_INVESTMENT_WORTH_KEY, (long) DEFAULT_BIG_INVESTMENT_WORTH);
                missingKey = true;
            } else {
                bigInvestmentWorth = worth;
            }

            Double minProfit = readNumber(root, MIN_INVESTMENT_PROFIT_KEY);
            if (minProfit == null) {
                root.addProperty(MIN_INVESTMENT_PROFIT_KEY, (long) DEFAULT_MIN_INVESTMENT_PROFIT);
                missingKey = true;
            } else {
                minInvestmentProfit = minProfit;
            }

            Double relistProfit = readNumber(root, MIN_RELIST_PROFIT_KEY);
            if (relistProfit == null) {
                root.addProperty(MIN_RELIST_PROFIT_KEY, (long) DEFAULT_MIN_RELIST_PROFIT);
                missingKey = true;
            } else {
                minRelistProfit = relistProfit;
            }

            // Write the defaults in, keeping everything else in the file, so the
            // settings are there to find and change.
            if (missingKey) {
                Files.createDirectories(path.getParent());
                Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(root));
            }
        } catch (Exception e) {
            System.out.println("[SkyblockFlips] Could not read " + CONFIG_NAME + ", using defaults where unset: " + e);
        }
        return new Settings(serverUrl, bigInvestmentWorth, minInvestmentProfit, minRelistProfit);
    }

    private static String readString(JsonObject root, String key) {
        if (!root.has(key) || !root.get(key).isJsonPrimitive()) return "";
        return root.get(key).getAsString().trim();
    }

    /** A number, or null when the key is missing or not a number. */
    private static Double readNumber(JsonObject root, String key) {
        if (!root.has(key) || !root.get(key).isJsonPrimitive()) return null;
        try {
            return root.get(key).getAsDouble();
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Path configPath() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve(CONFIG_NAME);
    }
}
