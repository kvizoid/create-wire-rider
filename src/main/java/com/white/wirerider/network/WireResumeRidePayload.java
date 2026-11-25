package com.white.wirerider.network;

import com.white.wirerider.WireRiderMod;
import com.white.wirerider.client.WireZiplineClientManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record WireResumeRidePayload(
    CompoundTag wireData,
    double x,
    double y,
    double z
) implements CustomPacketPayload {

    public static final Type<WireResumeRidePayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(WireRiderMod.MOD_ID, "wire_resume_ride"));

    public static final StreamCodec<ByteBuf, WireResumeRidePayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.COMPOUND_TAG, WireResumeRidePayload::wireData,
        ByteBufCodecs.DOUBLE, WireResumeRidePayload::x,
        ByteBufCodecs.DOUBLE, WireResumeRidePayload::y,
        ByteBufCodecs.DOUBLE, WireResumeRidePayload::z,
        WireResumeRidePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(WireResumeRidePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> WireZiplineClientManager.scheduleReconnectResume(
            payload.wireData(),
            payload.x(),
            payload.y(),
            payload.z()
        ));
    }
}
