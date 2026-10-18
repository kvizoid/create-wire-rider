package com.white.wirerider.content.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public class SparkArrestorItem extends Item {
    public static final int MAX_DURABILITY = 10;
    public static final int DISCHARGE_COST = 1;

    public SparkArrestorItem(Properties properties) {
        super(properties.stacksTo(8));
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return SparkArrestorHandler.hasArrestor(stack) && SparkArrestorHandler.getArrestorDurability(stack) < MAX_DURABILITY;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        int dur = SparkArrestorHandler.hasArrestor(stack) ? SparkArrestorHandler.getArrestorDurability(stack) : MAX_DURABILITY;
        return Math.round(13.0f * dur / (float) MAX_DURABILITY);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0x00E5FF;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("item.create_wire_rider.spark_arrestor.desc").withStyle(ChatFormatting.GRAY));
        if (SparkArrestorHandler.hasArrestor(stack)) {
            int dur = SparkArrestorHandler.getArrestorDurability(stack);
            int percent = (dur * 10) / MAX_DURABILITY;
            StringBuilder bar = new StringBuilder();
            for (int i = 0; i < 10; i++) {
                bar.append(i < percent ? "█" : "░");
            }
            tooltipComponents.add(Component.translatable("tooltip.create_wire_rider.spark_arrestor", bar.toString(), dur, MAX_DURABILITY).withStyle(ChatFormatting.AQUA));
        }
    }
}
