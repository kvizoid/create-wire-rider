package com.white.wirerider.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.white.wirerider.client.WrenchHeatRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemRenderer.class)
public class ItemRendererMixin {

    @Inject(
        method = "render",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V")
    )
    private void wireRider$renderWrenchHeat(
        ItemStack stack,
        ItemDisplayContext displayContext,
        boolean leftHand,
        PoseStack poseStack,
        MultiBufferSource buffer,
        int combinedLight,
        int combinedOverlay,
        BakedModel model,
        CallbackInfo ci
    ) {
        WrenchHeatRenderer.render(stack, displayContext, leftHand, poseStack, buffer, model);
    }
}
