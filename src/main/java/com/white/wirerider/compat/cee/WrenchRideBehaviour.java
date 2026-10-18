package com.white.wirerider.compat.cee;

import com.george_vi.electroenergetics.content.wire.interaction.WireInteractionBehaviour;
import com.george_vi.electroenergetics.content.wire.interaction.WireInteractionHandler;
import com.george_vi.electroenergetics.foundation.nodes.NodeConnectionPoint;
import com.simibubi.create.content.kinetics.chainConveyor.ServerChainConveyorHandler;
import com.white.wirerider.client.WireZiplineClientManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class WrenchRideBehaviour extends WireInteractionBehaviour {

    @Override
    public boolean isActiveFor(ItemStack stack, Player player) {
        if (player != null && player.isShiftKeyDown()) return false;
        if (WireZiplineClientManager.isRiding()) return false;

        if (WireZiplineClientManager.isWrench(stack)) return true;
        if (player != null && WireZiplineClientManager.isWrench(player.getOffhandItem())) return true;
        return false;
    }

    @Override
    public boolean tryUseOnWire(Level level, Player player, InteractionHand hand) {
        if (WireZiplineClientManager.isRiding()) return false;
        if (player.isShiftKeyDown()) return false;

        if (level.isClientSide()) {
            NodeConnectionPoint targetedPoint = WireInteractionHandler.targetedPoint;
            if (targetedPoint == null) return false;

            ItemStack stack = player.getItemInHand(hand);
            if (!WireZiplineClientManager.isWrench(stack)) {
                if (hand == InteractionHand.MAIN_HAND && WireZiplineClientManager.isWrench(player.getOffhandItem())) {
                    stack = player.getOffhandItem();
                } else {
                    return false;
                }
            }

            CEEWireSession session = new CEEWireSession(targetedPoint.connection(), level, player.position());
            if (session.isValid()) {
                WireZiplineClientManager.embark(session);
                player.swing(hand);
                return true;
            }
            return false;
        }
        return true;
    }

    @Override
    public void interactWire(NodeConnectionPoint point, Level level, Player player, ItemStack stack) {
        if (level.isClientSide()) return;
        if (player.isShiftKeyDown()) return;

        if (player instanceof ServerPlayer sp) {
            sp.fallDistance = 0.0f;
            ServerChainConveyorHandler.handleTTLPacket(sp);
        }
    }

    @Override
    public DisplayType getWireDisplayType(NodeConnectionPoint point, Level level, Player player, ItemStack stack) {
        return DisplayType.LINE;
    }

    @Override
    public int getWireDisplayColor(NodeConnectionPoint point, Level level, Player player, ItemStack stack) {
        return 0x00E5FF;
    }
}
