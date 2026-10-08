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
 * {@code "serverUrl": "http://192.168.1.50:8000"}, and restart the game. The key is
 * written into the file the first time the mod starts, so it is there to edit.
 */
public final class ModConfig {

    public static final String DEFAULT_SERVER_URL = "http://localhost:8000";

    private static final String CONFIG_NAME = "skyblockflips.json";
    private static final String SERVER_URL_KEY = "serverUrl";

    private static volatile String serverUrl = null;

    private ModConfig() {
    }

    /** The server's base address, without a trailing slash. Read once, on first use. */
    public static String serverUrl() {
        String url = serverUrl;
        if (url == null) {
            synchronized (ModConfig.class) {
                if (serverUrl == null) serverUrl = load();
                url = serverUrl;
            }
        }
        return url;
    }

    private static String load() {
        Path path = configPath();
        try {
            JsonObject root = new JsonObject();
            if (Files.exists(path)) {
                var parsed = JsonParser.parseString(Files.readString(path));
                if (parsed.isJsonObject()) root = parsed.getAsJsonObject();
            }

            if (root.has(SERVER_URL_KEY) && root.get(SERVER_URL_KEY).isJsonPrimitive()) {
                String configured = root.get(SERVER_URL_KEY).getAsString().trim();
                while (configured.endsWith("/")) configured = configured.substring(0, configured.length() - 1);
                if (!configured.isEmpty()) {
                    System.out.println("[SkyblockFlips] Using server " + configured);
                    return configured;
                }
            }

            // Not set yet: write the default in, keeping everything else in the file,
            // so the setting is there to find and change.
            root.addProperty(SERVER_URL_KEY, DEFAULT_SERVER_URL);
            Files.createDirectories(path.getParent());
            Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(root));
        } catch (Exception e) {
            System.out.println("[SkyblockFlips] Could not read " + CONFIG_NAME + ", using " + DEFAULT_SERVER_URL + ": " + e);
        }
        return DEFAULT_SERVER_URL;
    }

    private static Path configPath() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve(CONFIG_NAME);
    }
}
