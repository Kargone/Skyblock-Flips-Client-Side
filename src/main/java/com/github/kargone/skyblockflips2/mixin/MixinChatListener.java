package com.github.kargone.skyblockflips2.mixin;

import com.github.kargone.skyblockflips2.HttpClientExample;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.RegistryOps;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.minecraft.client.multiplayer.chat.ChatListener", remap = false)
public class MixinChatListener {

    @Unique
    private static final Gson GSON = new Gson();
    @Unique
    private static final HttpClientExample HTTP_CLIENT = new HttpClientExample();

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

        new Thread(() -> {
            try {
                JsonObject payload = new JsonObject();
                payload.addProperty("plainText", content);
                payload.add("fullJson", fullJson);
                payload.addProperty("detectedRarity", rarity);

                String jsonPayload = GSON.toJson(payload);
                
                // Send to your local server
                String response = HTTP_CLIENT.sendPOST("http://localhost:8000", jsonPayload);
                
                if (response != null && !response.isEmpty()) {
                    System.out.println("[SkyblockFlips] Server response: " + response);
                }

            } catch (Exception e) {
                System.err.println("[SkyblockFlips] Error processing chat message: " + e.getMessage());
            }
        }).start();
    }

    @Unique
    private boolean isRelevant(String content) {
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
