package com.white.wirerider.network;

import com.simibubi.create.content.kinetics.chainConveyor.ServerChainConveyorHandler;
import com.white.wirerider.WireRiderMod;
import com.white.wirerider.compat.IRidableWireSession;
import com.white.wirerider.compat.cee.CEECompat;
import com.white.wirerider.compat.powergrid.PowerGridCompat;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record WireRideActionPayload(int action, byte wireType, CompoundTag extraData) implements CustomPacketPayload {
    public static final Type<WireRideActionPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(WireRiderMod.MOD_ID, "wire_ride_action"));

    public static final StreamCodec<ByteBuf, WireRideActionPayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {
            ByteBufCodecs.VAR_INT.encode(buf, payload.action());
            ByteBufCodecs.BYTE.encode(buf, payload.wireType());
            ByteBufCodecs.COMPOUND_TAG.encode(buf, payload.extraData());
        },
        buf -> {
            int action = ByteBufCodecs.VAR_INT.decode(buf);
            byte wireType = ByteBufCodecs.BYTE.decode(buf);
            CompoundTag extraData = ByteBufCodecs.COMPOUND_TAG.decode(buf);
            return new WireRideActionPayload(action, wireType, extraData);
        }
    );

    public static final int ACTION_START = 0;
    public static final int ACTION_HEARTBEAT = 1;
    public static final int ACTION_STOP = 2;
    public static final int ACTION_SHOCK = 3;

    public static WireRideActionPayload stop() {
        return new WireRideActionPayload(ACTION_STOP, (byte) 0, new CompoundTag());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(WireRideActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer sp) {
                switch (payload.action()) {
                    case ACTION_START, ACTION_HEARTBEAT -> {
                        sp.fallDistance = 0.0f;
                        ServerChainConveyorHandler.handleTTLPacket(sp);

                        if (payload.wireType() == IRidableWireSession.TYPE_CEE && ModList.get().isLoaded("electroenergetics")) {
                            CEECompat.handleServerRideSync(sp, payload.extraData());
                        } else if (payload.wireType() == IRidableWireSession.TYPE_POWERGRID && ModList.get().isLoaded("powergrid")) {
                            PowerGridCompat.handleServerRideSync(sp, payload.extraData());
                        }
                    }
                    case ACTION_STOP -> {
                        sp.fallDistance = 0.0f;
                        ServerChainConveyorHandler.handleStopRidingPacket(sp);
                    }
                    case ACTION_SHOCK -> {
                        if (payload.wireType() == IRidableWireSession.TYPE_CEE && ModList.get().isLoaded("electroenergetics")) {
                            CEECompat.handleServerShock(sp, payload.extraData());
                        } else if (payload.wireType() == IRidableWireSession.TYPE_POWERGRID && ModList.get().isLoaded("powergrid")) {
                            PowerGridCompat.handleServerShock(sp, payload.extraData());
                        }
                    }
                }
            }
        });
    }
}

