package com.github.kargone.skyblockflips;

import net.fabricmc.api.ClientModInitializer;

public class SkyblockFlipsClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        System.out.println("[SkyblockFlips] Client Mod Initialized on Lunar Client (Recovery Mode)!");
    }
}