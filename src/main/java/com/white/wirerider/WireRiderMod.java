package com.white.wirerider;

import com.white.wirerider.compat.cee.CEECompat;
import com.white.wirerider.network.WireInspectRequestPayload;
import com.white.wirerider.network.WireInspectStatusPayload;
import com.white.wirerider.network.WireResumeRidePayload;
import com.white.wirerider.network.WireRideActionPayload;
import com.white.wirerider.network.WireSyncStatusPayload;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(WireRiderMod.MOD_ID)
public class WireRiderMod {
    public static final String MOD_ID = "create_wire_rider";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public WireRiderMod(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, WireRiderConfig.COMMON_SPEC);
        modContainer.registerConfig(ModConfig.Type.CLIENT, WireRiderConfig.CLIENT_SPEC);

        modEventBus.addListener(this::registerPayloads);

        if (ModList.get().isLoaded("electroenergetics")) {
            CEECompat.init(modEventBus);
        }

        NeoForge.EVENT_BUS.register(ServerWireRideManager.class);
        LOGGER.info("Create: Wire Rider initialized.");
    }

    private void registerPayloads(final RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("2");

        registrar.playToServer(
            WireRideActionPayload.TYPE,
            WireRideActionPayload.STREAM_CODEC,
            WireRideActionPayload::handle
        );

        registrar.playToServer(
            WireInspectRequestPayload.TYPE,
            WireInspectRequestPayload.STREAM_CODEC,
            WireInspectRequestPayload::handle
        );

        registrar.playToClient(
            WireSyncStatusPayload.TYPE,
            WireSyncStatusPayload.STREAM_CODEC,
            WireSyncStatusPayload::handle
        );

        registrar.playToClient(
            WireInspectStatusPayload.TYPE,
            WireInspectStatusPayload.STREAM_CODEC,
            WireInspectStatusPayload::handle
        );

        registrar.playToClient(
            WireResumeRidePayload.TYPE,
            WireResumeRidePayload.STREAM_CODEC,
            WireResumeRidePayload::handle
        );
    }
}
