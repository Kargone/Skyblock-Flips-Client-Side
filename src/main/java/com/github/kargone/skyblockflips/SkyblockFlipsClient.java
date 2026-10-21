package com.github.kargone.skyblockflips;

import com.github.kargone.skyblockflips.feature.SellOfferMarks;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;

public class SkyblockFlipsClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        System.out.println("[SkyblockFlips] Client Mod Initialized on Lunar Client (Recovery Mode)!");
        ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> SellOfferMarks.appendTooltip(stack, lines));
    }
}