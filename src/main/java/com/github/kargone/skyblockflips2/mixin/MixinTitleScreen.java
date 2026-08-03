package com.github.kargone.skyblockflips2.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.minecraft.client.gui.screens.TitleScreen", remap = false)
public class MixinTitleScreen {
    @Inject(method = "init()V", at = @At("HEAD"))
    public void onInit(CallbackInfo ci) {
        System.out.println("[SkyblockFlips] Title Screen Loaded!");
    }}
