package com.white.wirerider.content.item;

import com.white.wirerider.WireRiderMod;
import com.white.wirerider.client.WireZiplineClientManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ItemStackedOnOtherEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

@EventBusSubscriber(modid = "create_wire_rider")
public class SparkArrestorHandler {
    private static final String TAG_ARRESTOR = "WireRiderArrestor";

    public static boolean hasArrestor(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        return customData != null && customData.contains(TAG_ARRESTOR);
    }

    public static int getArrestorDurability(ItemStack stack) {
        if (!hasArrestor(stack)) return 0;
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) return 0;
        return customData.copyTag().getInt(TAG_ARRESTOR);
    }

    public static void setArrestorDurability(ItemStack stack, int durability) {
        if (stack == null || stack.isEmpty()) return;
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            if (durability > 0) {
                tag.putInt(TAG_ARRESTOR, durability);
            } else {
                tag.remove(TAG_ARRESTOR);
            }
        });
    }

    public static void removeArrestor(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.remove(TAG_ARRESTOR);
        });
    }

    public static void handleServerDischarge(ServerPlayer sp) {
        if (sp == null) return;
        ItemStack main = sp.getMainHandItem();
        ItemStack off = sp.getOffhandItem();

        if (hasArrestor(main)) {
            damageWrenchArrestor(main, sp, SparkArrestorItem.DISCHARGE_COST);
            return;
        }
        if (hasArrestor(off)) {
            damageWrenchArrestor(off, sp, SparkArrestorItem.DISCHARGE_COST);
            return;
        }
        if (off.getItem() instanceof SparkArrestorItem) {
            off.shrink(1);
            return;
        }
        if (main.getItem() instanceof SparkArrestorItem) {
            main.shrink(1);
            return;
        }
    }

    public static void damageWrenchArrestor(ItemStack wrench, ServerPlayer sp, int amount) {
        int cur = getArrestorDurability(wrench);
        cur -= amount;
        if (cur <= 0) {
            removeArrestor(wrench);
            sp.serverLevel().playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 1.0F, 0.9F);
        } else {
            setArrestorDurability(wrench, cur);
        }
    }

    @SubscribeEvent
    public static void onItemStacked(ItemStackedOnOtherEvent event) {
        ItemStack carried = event.getCarriedItem();
        ItemStack target = event.getStackedOnItem();

        if (target.isEmpty() || !WireZiplineClientManager.isWrench(target)) return;

        if (!carried.isEmpty() && carried.is(Items.SHEARS) && hasArrestor(target)) {
            int remaining = getArrestorDurability(target);
            removeArrestor(target);

            ItemStack returned = new ItemStack(WireRiderMod.SPARK_ARRESTOR.get());
            if (remaining < SparkArrestorItem.MAX_DURABILITY) {
                setArrestorDurability(returned, remaining);
            }

            Player player = event.getPlayer();
            if (player != null) {
                if (!player.getInventory().add(returned)) {
                    player.drop(returned, false);
                }
                player.playSound(SoundEvents.SHEEP_SHEAR, 0.8F, 1.2F);
                player.playSound(SoundEvents.ITEM_FRAME_REMOVE_ITEM, 0.8F, 1.4F);
            }
            event.setCanceled(true);
            return;
        }

        if (carried.isEmpty() || !(carried.getItem() instanceof SparkArrestorItem)) return;

        if (event.getClickAction() == ClickAction.SECONDARY) {
            int toInstall = hasArrestor(carried) ? getArrestorDurability(carried) : SparkArrestorItem.MAX_DURABILITY;
            setArrestorDurability(target, toInstall);
            carried.shrink(1);

            Player player = event.getPlayer();
            if (player != null) {
                player.playSound(SoundEvents.ARMOR_EQUIP_IRON.value(), 0.8F, 1.2F);
                player.playSound(SoundEvents.COPPER_PLACE, 0.8F, 1.4F);
            }
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (WireZiplineClientManager.isWrench(stack) && hasArrestor(stack)) {
            int dur = getArrestorDurability(stack);
            int max = SparkArrestorItem.MAX_DURABILITY;
            int percent = (dur * 10) / max;
            StringBuilder bar = new StringBuilder();
            for (int i = 0; i < 10; i++) {
                bar.append(i < percent ? "█" : "░");
            }
            event.getToolTip().add(Component.translatable("tooltip.create_wire_rider.spark_arrestor", bar.toString(), dur, max).withStyle(ChatFormatting.AQUA));
            event.getToolTip().add(Component.translatable("tooltip.create_wire_rider.spark_arrestor.remove").withStyle(ChatFormatting.GRAY));
        }
    }
}
