package com.white.wirerider;

import com.simibubi.create.content.kinetics.chainConveyor.ServerChainConveyorHandler;
import com.white.wirerider.compat.cee.CEECompat;
import com.white.wirerider.compat.powergrid.PowerGridCompat;
import com.white.wirerider.network.WireRideActionPayload;
import com.white.wirerider.network.WireSyncStatusPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(WireRiderMod.MOD_ID)
public class WireRiderMod {
    public static final String MOD_ID = "create_wire_rider";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public WireRiderMod(IEventBus modEventBus) {
        modEventBus.addListener(this::registerPayloads);

        if (ModList.get().isLoaded("electroenergetics")) {
            CEECompat.init(modEventBus);
        }

        if (ModList.get().isLoaded("powergrid")) {
            PowerGridCompat.init(modEventBus);
        }

        NeoForge.EVENT_BUS.register(this);
        LOGGER.info("Create: Wire Rider initialized successfully with multi-mod wire support (Power Grid & Electro Energetics)!");
    }

    private void registerPayloads(final RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        registrar.playToServer(
            WireRideActionPayload.TYPE,
            WireRideActionPayload.STREAM_CODEC,
            WireRideActionPayload::handle
        );

        registrar.playToClient(
            WireSyncStatusPayload.TYPE,
            WireSyncStatusPayload.STREAM_CODEC,
            WireSyncStatusPayload::handle
        );
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            ServerChainConveyorHandler.handleStopRidingPacket(sp);
        }
    }
}

