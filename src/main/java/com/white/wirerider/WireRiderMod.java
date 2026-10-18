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
import com.white.wirerider.content.toolbox.ZiplineToolboxEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(WireRiderMod.MOD_ID)
public class WireRiderMod {
    public static final String MOD_ID = "create_wire_rider";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
        DeferredRegister.create(Registries.ENTITY_TYPE, MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<ZiplineToolboxEntity>> ZIPLINE_TOOLBOX =
        ENTITY_TYPES.register("zipline_toolbox", () -> EntityType.Builder.<ZiplineToolboxEntity>of(ZiplineToolboxEntity::new, MobCategory.MISC)
            .sized(0.75F, 0.75F)
            .clientTrackingRange(10)
            .updateInterval(2)
            .build("zipline_toolbox"));

    public static final DeferredRegister<net.minecraft.world.item.Item> ITEMS =
        DeferredRegister.create(Registries.ITEM, MOD_ID);

    public static final DeferredHolder<net.minecraft.world.item.Item, com.white.wirerider.content.item.SparkArrestorItem> SPARK_ARRESTOR =
        ITEMS.register("spark_arrestor", () -> new com.white.wirerider.content.item.SparkArrestorItem(new net.minecraft.world.item.Item.Properties()));

    public WireRiderMod(IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);
        ITEMS.register(modEventBus);
        modEventBus.addListener(this::registerPayloads);
        modEventBus.addListener(this::addCreative);

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

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES
            || event.getTabKey() == CreativeModeTabs.REDSTONE_BLOCKS
            || (event.getTabKey().location() != null && event.getTabKey().location().getNamespace().equals("create"))) {
            event.accept(SPARK_ARRESTOR.get());
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            if (sp.getPersistentData().getBoolean("WireRiderActive")) {
                sp.fallDistance = 0.0f;
                sp.resetFallDistance();
                sp.getPersistentData().putInt("WireRiderLoginGrace", 160);
                ServerChainConveyorHandler.handleTTLPacket(sp);
            }
        }
    }

    @SubscribeEvent
    public void onPlayerTick(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            int grace = sp.getPersistentData().getInt("WireRiderLoginGrace");
            if (grace > 0) {
                sp.getPersistentData().putInt("WireRiderLoginGrace", grace - 1);
                sp.fallDistance = 0.0f;
                sp.resetFallDistance();
                sp.setDeltaMovement(0, 0, 0);
                ServerChainConveyorHandler.handleTTLPacket(sp);
            }
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            ServerChainConveyorHandler.handleStopRidingPacket(sp);
        }
    }
}

