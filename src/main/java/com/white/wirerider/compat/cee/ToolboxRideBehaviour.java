package com.white.wirerider.compat.cee;

import com.george_vi.electroenergetics.content.wire.interaction.WireInteractionBehaviour;
import com.george_vi.electroenergetics.content.wire.interaction.WireInteractionHandler;
import com.george_vi.electroenergetics.foundation.nodes.NodeConnectionPoint;
import com.simibubi.create.AllTags;
import com.white.wirerider.network.WireRideActionPayload;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

public class ToolboxRideBehaviour extends WireInteractionBehaviour {

    @Override
    public boolean isActiveFor(ItemStack stack, Player player) {
        if (player != null && player.isShiftKeyDown()) return false;
        if (stack.isEmpty()) {
            return player != null && AllTags.AllItemTags.TOOLBOXES.matches(player.getOffhandItem());
        }
        return AllTags.AllItemTags.TOOLBOXES.matches(stack) ||
            (player != null && AllTags.AllItemTags.TOOLBOXES.matches(player.getOffhandItem()));
    }

    @Override
    public boolean tryUseOnWire(Level level, Player player, InteractionHand hand) {
        if (player.isShiftKeyDown()) return false;
        if (level.isClientSide()) {
            NodeConnectionPoint targetedPoint = WireInteractionHandler.targetedPoint;
            if (targetedPoint == null) return false;

            ItemStack stack = player.getItemInHand(hand);
            if (!AllTags.AllItemTags.TOOLBOXES.matches(stack)) {
                if (hand == InteractionHand.MAIN_HAND && AllTags.AllItemTags.TOOLBOXES.matches(player.getOffhandItem())) {
                    stack = player.getOffhandItem();
                } else {
                    return false;
                }
            }

            PacketDistributor.sendToServer(CEECompat.createActionPayload(WireRideActionPayload.ACTION_LAUNCH_TOOLBOX, targetedPoint.connection()));
            player.swing(hand);
            return true;
        }
        return false;
    }

    @Override
    public void interactWire(NodeConnectionPoint point, Level level, Player player, ItemStack stack) {
    }

    @Override
    public DisplayType getWireDisplayType(NodeConnectionPoint point, Level level, Player player, ItemStack stack) {
        return DisplayType.LINE;
    }

    @Override
    public int getWireDisplayColor(NodeConnectionPoint point, Level level, Player player, ItemStack stack) {
        return 0xFFAA00;
    }
}
