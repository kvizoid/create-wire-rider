package com.white.wirerider.network;

import com.simibubi.create.content.equipment.goggles.GogglesItem;
import com.white.wirerider.WireRiderMod;
import com.white.wirerider.compat.cee.CEECompat;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record WireInspectRequestPayload(CompoundTag wireTag) implements CustomPacketPayload {
    public static final Type<WireInspectRequestPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(WireRiderMod.MOD_ID, "wire_inspect_request"));

    public static final StreamCodec<ByteBuf, WireInspectRequestPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.COMPOUND_TAG, WireInspectRequestPayload::wireTag,
        WireInspectRequestPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(WireInspectRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) return;
            if (!GogglesItem.isWearingGoggles(sp)) return;

            CEECompat.InspectSnapshot snapshot = CEECompat.inspectWire(sp, payload.wireTag(), 16.0 * 16.0);
            if (snapshot == null) return;

            PacketDistributor.sendToPlayer(sp, new WireInspectStatusPayload(
                payload.wireTag(),
                snapshot.energized(),
                snapshot.insulated(),
                snapshot.overvolted(),
                snapshot.voltage(),
                snapshot.current(),
                snapshot.temperature()
            ));
        });
    }
}
