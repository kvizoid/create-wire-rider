package com.white.wirerider.content;

import com.white.wirerider.client.WireZiplineClientManager;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ItemStackedOnOtherEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

@EventBusSubscriber(modid = "create_wire_rider")
public class WrenchLubricationHandler {
    private static final String TAG_LUBRICATION = "WireRiderLubrication";
    private static final int FULL_DURATION = 3600;

    public static boolean isLubricated(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        return customData != null && customData.contains(TAG_LUBRICATION);
    }

    public static int getLubricationTicks(ItemStack stack) {
        if (!isLubricated(stack)) return 0;
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) return 0;
        return customData.copyTag().getInt(TAG_LUBRICATION);
    }

    public static void setLubrication(ItemStack stack, int ticks) {
        if (stack == null || stack.isEmpty()) return;
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            if (ticks > 0) {
                tag.putInt(TAG_LUBRICATION, ticks);
            } else {
                tag.remove(TAG_LUBRICATION);
            }
        });
    }

    public static void tickLubrication(ItemStack stack, boolean inRain) {
        int current = getLubricationTicks(stack);
        if (current <= 0) return;
        int decrement = inRain ? 2 : 1;
        setLubrication(stack, Math.max(0, current - decrement));
    }

    @SubscribeEvent
    public static void onItemStacked(ItemStackedOnOtherEvent event) {
        ItemStack carried = event.getCarriedItem();
        ItemStack target = event.getStackedOnItem();

        if (target.isEmpty() || !WireZiplineClientManager.isWrench(target)) return;

        if (event.getClickAction() == ClickAction.SECONDARY) {
            boolean isHoneyBottle = carried.is(Items.HONEY_BOTTLE);
            boolean isHoneycomb = carried.is(Items.HONEYCOMB);

            if (isHoneyBottle || isHoneycomb) {
                setLubrication(target, FULL_DURATION);
                event.getPlayer().level().playSound(
                    null,
                    event.getPlayer().getX(),
                    event.getPlayer().getY(),
                    event.getPlayer().getZ(),
                    SoundEvents.HONEY_BLOCK_PLACE,
                    SoundSource.PLAYERS,
                    0.8f,
                    1.2f
                );

                if (!event.getPlayer().isCreative()) {
                    if (isHoneyBottle) {
                        event.getCarriedSlotAccess().set(new ItemStack(Items.GLASS_BOTTLE));
                    } else {
                        carried.shrink(1);
                    }
                }
                event.setCanceled(true);
            }
        } else if (event.getClickAction() == ClickAction.PRIMARY) {
            if (carried.getItem() instanceof SwordItem && isLubricated(target)) {
                setLubrication(target, 0);
                event.getPlayer().level().playSound(
                    null,
                    event.getPlayer().getX(),
                    event.getPlayer().getY(),
                    event.getPlayer().getZ(),
                    SoundEvents.GRINDSTONE_USE,
                    SoundSource.PLAYERS,
                    0.7f,
                    1.6f
                );
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty() || !WireZiplineClientManager.isWrench(stack)) return;

        int ticks = getLubricationTicks(stack);
        if (ticks > 0) {
            int totalSeconds = ticks / 20;
            int mins = totalSeconds / 60;
            int secs = totalSeconds % 60;
            event.getToolTip().add(Component.translatable("tooltip.create_wire_rider.honey_lubricated", mins, String.format("%02d", secs)));
        }
    }
}
