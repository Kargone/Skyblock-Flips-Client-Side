package com.github.kargone.skyblockflips.mixin;

import com.github.kargone.skyblockflips.SkyblockHttpClient;
import com.github.kargone.skyblockflips.flip.ServerData;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.RegistryOps;
import org.apache.http.conn.ConnectTimeoutException;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.net.ConnectException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Pseudo
@Mixin(targets = "net.minecraft.client.multiplayer.chat.ChatListener", remap = false)
public class ChatListenerMixin {

    @Unique
    private static final Gson GSON = new Gson();
    @Unique
    private static final SkyblockHttpClient HTTP_CLIENT = new SkyblockHttpClient();
    /**
     * One thread for every trade message, so the server gets them in the order they
     * appeared in chat. A sell offer cancel followed by a new sell offer has to arrive
     * in that order, or the new offer finds nothing in investments to sell.
     */
    @Unique
    private static final ExecutorService SENDER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "SkyblockFlips-TradeSender");
        thread.setDaemon(true);
        return thread;
    });
    @Unique
    private static final String OWN_PREFIX = "[SkyblockFlips]";
    /** Waits before each retry of a message the server could not be reached for, about two minutes in all. */
    @Unique
    private static final long[] RETRY_DELAYS_MS = {1_000L, 3_000L, 10_000L, 30_000L, 60_000L};

    /**
     * Injects into handleSystemMessage to capture server-sent messages (Bazaar, Auctions, etc.)
     */
    @Inject(method = "handleSystemMessage(Lnet/minecraft/network/chat/Component;Z)V", at = @At("HEAD"))
    private void onHandleSystemMessage(Component message, boolean overlay, CallbackInfo ci) {
        if (message == null) return;

        String content = message.getString();

        // Debug log to see every system message and its overlay status
        System.out.println("[SkyblockFlips] Raw Message: " + content + " | Overlay: " + overlay);

        // If it's relevant, process it even if it's an overlay (Action Bar)
        if (isRelevant(content)) {
            System.out.println("[SkyblockFlips] Relevant message detected: " + content + " | Overlay: " + overlay);
            processChatMessage(message);
        }
    }

    @Unique
    private void processChatMessage(Component message) {
        String content = message.getString();

        // Prepare JSON structure
        JsonElement fullJson = serializeToJson(message);
        String rarity = detectRarity(message);

        System.out.println("[SkyblockFlips] Detected Rarity: " + (rarity.isEmpty() ? "" : rarity));

        JsonObject payload = new JsonObject();
        payload.addProperty("plainText", content);
        payload.add("fullJson", fullJson);
        payload.addProperty("detectedRarity", rarity);
        String jsonPayload = GSON.toJson(payload);

        SENDER.execute(() -> sendTrade(jsonPayload, content));
    }

    /**
     * Sends one trade message, retrying while the server cannot be reached at all. A
     * failure after connecting is not retried, since the server may already have the
     * trade and would count it twice. A message that is given up on is shown in chat,
     * so the trade can be fixed by hand instead of going missing silently.
     */
    @Unique
    private static void sendTrade(String jsonPayload, String content) {
        for (int attempt = 0; ; attempt++) {
            try {
                // Send to the Skyblock Flips server (serverUrl in the mod's config)
                String response = HTTP_CLIENT.sendPOST(ServerData.baseUrl(), jsonPayload);
                if (response != null && !response.isEmpty()) {
                    System.out.println("[SkyblockFlips] Server response: " + response);
                }
                return;
            } catch (Exception e) {
                boolean notConnected = e instanceof ConnectException || e instanceof ConnectTimeoutException;
                if (notConnected && attempt < RETRY_DELAYS_MS.length) {
                    System.err.println("[SkyblockFlips] Server not reachable, retrying trade message: " + e.getMessage());
                    try {
                        Thread.sleep(RETRY_DELAYS_MS[attempt]);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    }
                    continue;
                }
                System.err.println("[SkyblockFlips] Error sending trade message: " + e.getMessage() + " | " + content);
                warnInChat(content, e);
                return;
            }
        }
    }

    @Unique
    private static void warnInChat(String content, Exception e) {
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            if (client.player == null) return;
            client.getChatListener().handleSystemMessage(Component.literal(
                    OWN_PREFIX + " Trade not sent to the server (" + e.getMessage() + "): " + content)
                    .withStyle(ChatFormatting.RED), false);
        });
    }

    @Unique
    private boolean isRelevant(String content) {
        // The mod's own warnings quote the trade, so they would look like one.
        if (content.startsWith(OWN_PREFIX)) return false;
        String lower = content.toLowerCase();
        return lower.contains("[bazaar]") ||
                lower.contains("you sold") ||
                lower.contains("bin auction") ||
                lower.contains("you collected") ||
                lower.contains("cancelled") ||
                lower.contains("supercrafted") ||
                lower.contains("[auction]") ||
                lower.contains("beastmaster") ||
                lower.contains("purchased") ||
                lower.contains("you claimed");
    }

    @Unique
    private JsonElement serializeToJson(Component message) {
        try {
            Minecraft client = Minecraft.getInstance();
            if (client.level != null) {
                return ComponentSerialization.CODEC.encodeStart(
                        RegistryOps.create(JsonOps.INSTANCE, client.level.registryAccess()),
                        message
                ).getOrThrow();
            }
            return ComponentSerialization.CODEC.encodeStart(JsonOps.INSTANCE, message).getOrThrow();
        } catch (Exception e) {
            JsonObject fallback = new JsonObject();
            fallback.addProperty("text", message.getString());
            fallback.addProperty("error", "Serialization failed: " + e.getMessage());
            return fallback;
        }
    }

    @Unique
    private String detectRarity(Component message) {

        for (Component part : message.toFlatList()) {
            String text = part.getString();
            TextColor color = part.getStyle().getColor();

            if (color != null) {
                String rarity = mapColorToRarity(color.serialize());

                if (rarity != null && text.contains("Beastmaster Crest")) {
                    return rarity;
                }
            }
        }

        return "";
    }

    @Unique
    private String mapColorToRarity(String colorName) {
        return switch (colorName) {
            case "white" -> "COMMON";
            case "green" -> "UNCOMMON";
            case "blue" -> "RARE";
            case "dark_purple" -> "EPIC";
            case "gold" -> "LEGENDARY";
            case "light_purple" -> "MYTHIC";
            case "aqua" -> "DIVINE";
            case "red" -> "SPECIAL";
            default -> null;
        };
    }
}
