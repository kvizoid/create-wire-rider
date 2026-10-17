package com.white.wirerider;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

public class WireRiderTags {
    public static final TagKey<Item> WRENCHES = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "tools/wrench"));
    public static final TagKey<Item> ZIPLINE_TOOLS = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("create_wire_rider", "zipline_tools"));
}

