package com.github.kargone.skyblockflips.mixin;

import com.github.kargone.skyblockflips.util.ContainerUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.minecraft.client.Minecraft", remap = false)
public class MinecraftMixin {

    @Inject(method = "setScreen(Lnet/minecraft/client/gui/screens/Screen;)V", at = @At("TAIL"), require = 0)
    private void onSetScreenTail(Screen screen, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (screen != null) {
            String screenClass = screen.getClass().getName();
            String guiClass = client.gui != null ? client.gui.getClass().getName() : "null";

            System.out.println("[SkyblockFlips] Debug Screen -> Class: " + screenClass + " | Title: \"" + screen.getTitle().getString() + "\"");
            System.out.println("[SkyblockFlips] Debug GUI Class: " + guiClass);

            new Thread(() -> {
                try { Thread.sleep(250); } catch (Exception ignored) {}
                ContainerUtils.logContainerItems(screen);
            }).start();
        }
    }
}
