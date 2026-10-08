package com.github.kargone.skyblockflips.mixin;

import com.github.kargone.skyblockflips.overlay.OverlayManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws the amount-fill button after the sign itself has been drawn.
 *
 * {@code AbstractSignEditScreen.extractRenderState} calls the Screen version first
 * and only then draws its title and the sign, so anything submitted from
 * {@link ScreenMixin} ends up underneath the sign. Injecting at the tail here puts
 * the button on top instead.
 */
@Pseudo
@Mixin(targets = "net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen", remap = false)
public class SignEditScreenMixin {

    @Inject(
            method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",
            at = @At("TAIL"),
            require = 0
    )
    private void onSignRender(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        OverlayManager.markSignHookActive();

        Screen screen = (Screen) (Object) this;
        OverlayManager.draw(graphics, screen, "", mouseX, mouseY);
    }
}
