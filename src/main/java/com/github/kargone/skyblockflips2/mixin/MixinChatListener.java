package com.github.kargone.skyblockflips2.mixin;

import com.github.kargone.skyblockflips2.HttpClientExample;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Pseudo
@Mixin(targets = "net.minecraft.client.multiplayer.chat.ChatListener", remap = false)
public class MixinChatListener {

    @Unique
    private static final Gson GSON = new Gson();
    @Unique
    private static final HttpClientExample HTTP_CLIENT = new HttpClientExample();
    
    // Decouples the render thread from processing/networking
    @Unique
    private static final BlockingQueue<QueuedMessage> MESSAGE_QUEUE = new LinkedBlockingQueue<>(100);

    static {
        // Start a single background worker to batch and process messages
        Thread worker = new Thread(MixinChatListener::workerLoop, "SkyblockFlips-Worker");
        worker.setDaemon(true);
        worker.start();
    }

    @Inject(method = "handleSystemMessage(Lnet/minecraft/network/chat/Component;Z)V", at = @At("HEAD"))
    private void onHandleSystemMessage(Component message, boolean overlay, CallbackInfo ci) {
        if (message == null) return;
        
        String content = message.getString();
        
        // Fast path check on the render thread
        if (isRelevant(content)) {
            // Drop message if queue is full to prevent memory leaks/stalls
            MESSAGE_QUEUE.offer(new QueuedMessage(message, content, overlay));
        }
    }

    @Unique
    private static void workerLoop() {
        while (true) {
            try {
                // Wait for at least one message
                QueuedMessage first = MESSAGE_QUEUE.take();
                List<QueuedMessage> batch = new ArrayList<>();
                batch.add(first);
                
                // Collect any other messages that arrived in the last 100ms
                Thread.sleep(100);
                MESSAGE_QUEUE.drainTo(batch);

                processBatch(batch);
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                System.err.println("[SkyblockFlips] Background worker error: " + e.getMessage());
            }
        }
    }

    @Unique
    private static void processBatch(List<QueuedMessage> batch) {
        JsonArray array = new JsonArray();
        
        for (QueuedMessage msg : batch) {
            JsonObject payload = new JsonObject();
            payload.addProperty("plainText", msg.content);
            payload.addProperty("overlay", msg.overlay);
            
            // Serialization happens here, completely off the render thread
            payload.add("fullJson", serializeToJson(msg.component));
            payload.addProperty("detectedRarity", detectRarity(msg.component));

            array.add(payload);
        }

        try {
            // Sending as a batch reduces HTTP overhead and prevents driver resets from connection stalls
            HTTP_CLIENT.sendPOST("http://localhost:8000/batch", GSON.toJson(array));
        } catch (Exception e) {
            System.err.println("[SkyblockFlips] Failed to send batch: " + e.getMessage());
        }
    }

    @Unique
    private static boolean isRelevant(String content) {
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
                lower.contains("you claimed") ||
                lower.contains("you bought back");
    }

    @Unique
    private static JsonElement serializeToJson(Component message) {
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
            return fallback;
        }
    }

    @Unique
    private static String detectRarity(Component message) {
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
    private static String mapColorToRarity(String colorName) {
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

    private static class QueuedMessage {
        final Component component;
        final String content;
        final boolean overlay;

        QueuedMessage(Component component, String content, boolean overlay) {
            this.component = component;
            this.content = content;
            this.overlay = overlay;
        }
    }
}
