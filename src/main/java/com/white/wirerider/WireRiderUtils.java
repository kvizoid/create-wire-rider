package com.white.wirerider;

import com.simibubi.create.AllTags.AllItemTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Common utility helpers shared across client and server.
 * Must NEVER reference client-only classes.
 */
public final class WireRiderUtils {
    private WireRiderUtils() {}

    public static float getLiveVoltageThreshold() {
        return WireRiderConfig.COMMON.liveVoltageThreshold.get().floatValue();
    }

    public static float getWireBurnTemp() {
        return WireRiderConfig.COMMON.wireBurnTemperature.get().floatValue();
    }

    public static float getWireCriticalTemp() {
        return WireRiderConfig.COMMON.wireCriticalTemperature.get().floatValue();
    }

    public static boolean isWrench(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return AllItemTags.CHAIN_RIDEABLE.matches(stack)
            || stack.is(WireRiderTags.WRENCHES)
            || stack.is(WireRiderTags.WRENCHES_PLURAL)
            || stack.is(WireRiderTags.ZIPLINE_TOOLS);
    }

    public static ItemStack getActiveWrench(Player player) {
        if (player == null) return ItemStack.EMPTY;
        if (isWrench(player.getMainHandItem())) return player.getMainHandItem();
        if (isWrench(player.getOffhandItem())) return player.getOffhandItem();
        return ItemStack.EMPTY;
    }

    public static boolean hasWrench(Player player) {
        return !getActiveWrench(player).isEmpty();
    }

    public static float computeShockDamage(double voltage) {
        if (voltage <= getLiveVoltageThreshold()) return 0.0f;
        double divisor = WireRiderConfig.COMMON.voltageDamageDivisor.get();
        double maxDmg = WireRiderConfig.COMMON.maxShockDamage.get();
        return (float) Math.max(4.0, Math.min(maxDmg, voltage / divisor));
    }
}
