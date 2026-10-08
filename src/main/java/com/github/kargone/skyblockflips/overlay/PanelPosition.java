package com.github.kargone.skyblockflips.overlay;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;

/**
 * Where the player dragged each panel to, remembered across sessions in
 * {@code config/skyblockflips.json} inside the game directory.
 *
 * Until a panel is dragged for the first time nothing is stored for it and the
 * overlay places it automatically.
 */
public final class PanelPosition {

    /** The panels that can be moved, and the config key each one is saved under. */
    public enum Panel {
        MAIN("auctionPanel"),
        FORGE("forgePanel"),
        BREAKDOWN("breakdownPanel"),
        SIGN("signAmountButton"),
        REMINDERS("remindersPanel");

        private final String configKey;

        Panel(String configKey) {
            this.configKey = configKey;
        }
    }

    private record Spot(int x, int y) {
    }

    private static final String CONFIG_NAME = "skyblockflips.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Map<Panel, Spot> SPOTS = new EnumMap<>(Panel.class);
    private static boolean loaded = false;

    private PanelPosition() {
    }

    /** True once this panel has a position of its own; false means "place it automatically". */
    public static boolean isSet(Panel panel) {
        ensureLoaded();
        return SPOTS.containsKey(panel);
    }

    public static int x(Panel panel) {
        ensureLoaded();
        Spot spot = SPOTS.get(panel);
        return spot == null ? 0 : spot.x();
    }

    public static int y(Panel panel) {
        ensureLoaded();
        Spot spot = SPOTS.get(panel);
        return spot == null ? 0 : spot.y();
    }

    /** Moves a panel without touching disk - dragging calls this every frame. */
    public static void set(Panel panel, int newX, int newY) {
        ensureLoaded();
        SPOTS.put(panel, new Spot(newX, newY));
    }

    /** Writes the positions out. Called when a drag finishes, not while it runs. */
    public static void save() {
        ensureLoaded();
        if (SPOTS.isEmpty()) return;

        try {
            Files.createDirectories(configPath().getParent());

            JsonObject root = readExisting();
            for (Map.Entry<Panel, Spot> entry : SPOTS.entrySet()) {
                JsonObject spot = new JsonObject();
                spot.addProperty("x", entry.getValue().x());
                spot.addProperty("y", entry.getValue().y());
                root.add(entry.getKey().configKey, spot);
            }

            Files.writeString(configPath(), GSON.toJson(root));
        } catch (Exception e) {
            System.out.println("[SkyblockFlips] Could not save panel positions: " + e);
        }
    }

    private static void ensureLoaded() {
        if (loaded) return;
        loaded = true;

        try {
            JsonObject root = readExisting();
            for (Panel panel : Panel.values()) {
                if (!root.has(panel.configKey) || !root.get(panel.configKey).isJsonObject()) continue;

                JsonObject spot = root.getAsJsonObject(panel.configKey);
                if (!spot.has("x") || !spot.has("y")) continue;
                SPOTS.put(panel, new Spot(spot.get("x").getAsInt(), spot.get("y").getAsInt()));
            }
        } catch (Exception e) {
            System.out.println("[SkyblockFlips] Could not read panel positions: " + e);
        }
    }

    /** The whole config file, so saving a panel never drops other settings. */
    private static JsonObject readExisting() {
        try {
            Path path = configPath();
            if (!Files.exists(path)) return new JsonObject();

            var parsed = JsonParser.parseString(Files.readString(path));
            return parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject();
        } catch (Exception e) {
            return new JsonObject();
        }
    }

    private static Path configPath() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve(CONFIG_NAME);
    }
}
