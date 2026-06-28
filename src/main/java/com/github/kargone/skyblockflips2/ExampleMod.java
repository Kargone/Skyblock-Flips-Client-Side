package com.github.kargone.skyblockflips2;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.text.Text;

import java.io.IOException;

public class ExampleMod implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        System.out.println("Skyblock Flips Mod Initialized!");

        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            onChatMessage(message);
        });

        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) -> {
            onChatMessage(message);
        });
    }

    private void onChatMessage(Text message) {
        String content = message.getString();
        if (content.contains("[Bazaar]") || content.contains("You sold") ||
                content.contains("created a BIN auction") || content.contains("You collected") ||
                content.contains("You cancelled") || content.contains("You Supercrafted") ||
                content.contains("[Auction]") || content.contains("You purchased")) {
            
            // Network requests should be asynchronous
            new Thread(() -> {
                HttpClientExample myPostRequest = new HttpClientExample();
                try {
                    String result = myPostRequest.sendPOST("http://localhost:8000", content);
                    System.out.println("Server responded: " + result);
                } catch (IOException e) {
                    System.err.println("Failed to send chat message to server: " + e.getMessage());
                }
            }).start();
        }
    }
}
