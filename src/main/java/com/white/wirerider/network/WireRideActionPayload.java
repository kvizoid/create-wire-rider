package com.white.wirerider.network;

import com.white.wirerider.ServerWireRideManager;
import com.white.wirerider.WireRiderMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record WireRideActionPayload(int action, CompoundTag extraData) implements CustomPacketPayload {
    public static final Type<WireRideActionPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(WireRiderMod.MOD_ID, "wire_ride_action"));

    public static final StreamCodec<ByteBuf, WireRideActionPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, WireRideActionPayload::action,
        ByteBufCodecs.COMPOUND_TAG, WireRideActionPayload::extraData,
        WireRideActionPayload::new
    );

    public static final int ACTION_START = 0;
    public static final int ACTION_HEARTBEAT = 1;
    public static final int ACTION_STOP = 2;
    public static final int ACTION_GROUND_SHOCK = 3;
    public static final int ACTION_THERMAL_BURN = 4;

    public static WireRideActionPayload stop() {
        return new WireRideActionPayload(ACTION_STOP, new CompoundTag());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(WireRideActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) return;

            switch (payload.action()) {
                case ACTION_START, ACTION_HEARTBEAT ->
                    ServerWireRideManager.handleStartOrHeartbeat(sp, payload.extraData());
                case ACTION_STOP ->
                    ServerWireRideManager.stopRiding(sp);
                case ACTION_GROUND_SHOCK ->
                    ServerWireRideManager.handleGroundShock(sp, payload.extraData());
                case ACTION_THERMAL_BURN ->
                    ServerWireRideManager.handleThermalBurn(sp, payload.extraData());
            }
        });
    }
}
