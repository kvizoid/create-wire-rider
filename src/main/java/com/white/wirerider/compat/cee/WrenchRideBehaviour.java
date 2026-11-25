package com.white.wirerider.compat.cee;

import com.george_vi.electroenergetics.content.wire.interaction.WireInteractionBehaviour;
import com.george_vi.electroenergetics.foundation.nodes.NodeConnectionPoint;
import com.white.wirerider.WireRiderUtils;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class WrenchRideBehaviour extends WireInteractionBehaviour {

    @Override
    public boolean isActiveFor(ItemStack stack, Player player) {
        if (player != null && player.isShiftKeyDown()) return false;
        if (player != null && player.level().isClientSide() && CEEClientWireHelper.isRiding()) return false;

        if (WireRiderUtils.isWrench(stack)) return true;
        return player != null && WireRiderUtils.isWrench(player.getOffhandItem());
    }

    @Override
    public boolean tryUseOnWire(Level level, Player player, InteractionHand hand) {
        if (player.isShiftKeyDown()) return false;

        if (level.isClientSide()) {
            return CEEClientWireHelper.tryEmbarkTargeted(level, player, hand);
        }
        return true;
    }

    @Override
    public void interactWire(NodeConnectionPoint point, Level level, Player player, ItemStack stack) {
        // Server state is validated and synchronized via WireRideActionPayload
    }

    @Override
    public DisplayType getWireDisplayType(NodeConnectionPoint point, Level level, Player player, ItemStack stack) {
        // Rendered via WireTargetHighlightRenderer in Create brass style
        return DisplayType.NONE;
    }

    @Override
    public int getWireDisplayColor(NodeConnectionPoint point, Level level, Player player, ItemStack stack) {
        return 0xE5AA42;
    }
}
