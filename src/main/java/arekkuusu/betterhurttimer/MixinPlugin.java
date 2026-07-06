package arekkuusu.betterhurttimer;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Collections;
import java.util.Set;

public class MixinPlugin implements IMixinConfigPlugin {

    private static final Set<String> CONFIG_DISABLED_MIXINS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "AttackEntityMixin",
            "DamageArmorMixin",
            "DamageArmorMixinBukkit",
            "DamageArmorMixinOverride",
            "DamageSpecialArmorMixin",
            "HurtAnimationMixin",
            "HurtCameraEffectMixin",
            "HurtTimeMixin",
            "KnockbackMixin"
    )));

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!mixinClassName.startsWith("arekkuusu.betterhurttimer.mixin.")) {
            return true;
        }

        String simpleName = mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1);

        if (BHTConfig.CONFIG.attackFrames.turnOffMixins && CONFIG_DISABLED_MIXINS.contains(simpleName)) {
            return false;
        }

        switch (simpleName) {
            case "DamageArmorMixin":
                return !hasClass("org.bukkit.plugin.Plugin");
            case "DamageArmorMixinBukkit":
                return hasClass("org.bukkit.plugin.Plugin");
            case "DamageArmorMixinOverride":
                return hasClass("com.robertx22.mine_and_slash.mixins.LivingEntityMixin");
            default:
                return true;
        }
    }

    private boolean hasClass(String name) {
        try {
            Class.forName(name, false, getClass().getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
