package com.github.kargone.skyblockflips.mixin;

import com.github.kargone.skyblockflips.overlay.OverlayManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.minecraft.client.gui.screens.Screen", remap = false)
public abstract class ScreenMixin {

    // REMOVED @Shadow protected Component title;
    // Field shadowing with remap=false causes an instant class verification failure on Lunar.
    @Inject(
            method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",
            at = @At("TAIL"),
            require = 0
    )
    private void onRender(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        Screen screen = (Screen) (Object) this;

        // The container and sign mixins draw their screens on a later pass; only
        // cover those here while their hooks have not proven themselves.
        if (!OverlayManager.screenHookOwns(screen)) {
            return;
        }

        if (screen.getTitle() != null) {
            OverlayManager.draw(graphics, screen, screen.getTitle().getString(), mouseX, mouseY);
        }
    }
}
