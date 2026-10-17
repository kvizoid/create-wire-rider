package com.white.wirerider.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "com.george_vi.electroenergetics.content.wire.attachments.AttachmentRemovalWireInteractionBehaviour", remap = false)
public class AttachmentRemovalMixin {

    @Inject(method = "isActiveFor", at = @At("HEAD"), cancellable = true)
    private void wireRider$onlyOnShift(ItemStack stack, Player player, CallbackInfoReturnable<Boolean> cir) {
        if (player != null && !player.isShiftKeyDown()) {
            cir.setReturnValue(false);
        }
    }
}

