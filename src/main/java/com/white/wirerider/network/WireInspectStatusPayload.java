package com.white.wirerider.network;

import com.white.wirerider.WireRiderMod;
import com.white.wirerider.client.WireGogglesOverlayRenderer;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record WireInspectStatusPayload(
    CompoundTag wireTag,
    boolean energized,
    boolean insulated,
    boolean overvolted,
    float voltage,
    float current,
    float temperature
) implements CustomPacketPayload {

    public static final Type<WireInspectStatusPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(WireRiderMod.MOD_ID, "wire_inspect_status"));

    public static final StreamCodec<ByteBuf, WireInspectStatusPayload> STREAM_CODEC = StreamCodec.of(
        (buf, p) -> {
            ByteBufCodecs.COMPOUND_TAG.encode(buf, p.wireTag());
            ByteBufCodecs.BOOL.encode(buf, p.energized());
            ByteBufCodecs.BOOL.encode(buf, p.insulated());
            ByteBufCodecs.BOOL.encode(buf, p.overvolted());
            ByteBufCodecs.FLOAT.encode(buf, p.voltage());
            ByteBufCodecs.FLOAT.encode(buf, p.current());
            ByteBufCodecs.FLOAT.encode(buf, p.temperature());
        },
        buf -> new WireInspectStatusPayload(
            ByteBufCodecs.COMPOUND_TAG.decode(buf),
            ByteBufCodecs.BOOL.decode(buf),
            ByteBufCodecs.BOOL.decode(buf),
            ByteBufCodecs.BOOL.decode(buf),
            ByteBufCodecs.FLOAT.decode(buf),
            ByteBufCodecs.FLOAT.decode(buf),
            ByteBufCodecs.FLOAT.decode(buf)
        )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(WireInspectStatusPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> WireGogglesOverlayRenderer.updateInspectStatus(payload));
    }
}
