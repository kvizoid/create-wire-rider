package com.white.wirerider.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public class WireRiderMixinPlugin implements IMixinConfigPlugin {
    private boolean ceePresent = false;

    @Override
    public void onLoad(String mixinPackage) {
        try {
            Class<?> loadingModListClass = Class.forName("net.neoforged.fml.loading.LoadingModList");
            Object loadingModList = loadingModListClass.getMethod("get").invoke(null);
            Object modFile = loadingModListClass.getMethod("getModFileById", String.class).invoke(loadingModList, "electroenergetics");
            ceePresent = (modFile != null);
        } catch (Throwable t) {
            ceePresent = false;
        }
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.contains("AttachmentRemovalMixin")) {
            return ceePresent;
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}

