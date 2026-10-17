package com.white.wirerider.network;

import com.white.wirerider.WireRiderMod;
import com.white.wirerider.client.WireZiplineClientManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record WireSyncStatusPayload(
    boolean energized,
    float voltage,
    float current,
    float temperature,
    float dangerousDistance
) implements CustomPacketPayload {

    public static final Type<WireSyncStatusPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(WireRiderMod.MOD_ID, "wire_sync_status"));

    public static final StreamCodec<ByteBuf, WireSyncStatusPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.BOOL, WireSyncStatusPayload::energized,
        ByteBufCodecs.FLOAT, WireSyncStatusPayload::voltage,
        ByteBufCodecs.FLOAT, WireSyncStatusPayload::current,
        ByteBufCodecs.FLOAT, WireSyncStatusPayload::temperature,
        ByteBufCodecs.FLOAT, WireSyncStatusPayload::dangerousDistance,
        WireSyncStatusPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(WireSyncStatusPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            WireZiplineClientManager.updateElectricalStatus(
                payload.energized(),
                payload.voltage(),
                payload.current(),
                payload.temperature(),
                payload.dangerousDistance()
            );
        });
    }
}

