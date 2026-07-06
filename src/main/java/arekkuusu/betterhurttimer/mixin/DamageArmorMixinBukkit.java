package arekkuusu.betterhurttimer.mixin;

import arekkuusu.betterhurttimer.BHTConfig;
import arekkuusu.betterhurttimer.api.capability.Capabilities;
import arekkuusu.betterhurttimer.api.capability.HurtCapability;
import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(EntityLivingBase.class)
public abstract class DamageArmorMixinBukkit {

    //Bukkit Compliant
    @Redirect(method = "damageEntity_CB(Lnet/minecraft/util/DamageSource;F)Z", at = @At(target = "Lnet/minecraft/entity/EntityLivingBase;damageShield(F)V", value = "INVOKE"), require = 0)
    public void damageShieldS(EntityLivingBase entity, float damage) {
        HurtCapability capability = Capabilities.hurt(entity).orElse(null);
        if (capability != null) {
            long worldTime = entity.world.getTotalWorldTime();
            if (capability.shieldDamageCooldownUntil > worldTime) {
                return;
            } else {
                damageShield(damage);
                capability.shieldDamageCooldownUntil = worldTime + BHTConfig.CONFIG.damageFrames.shieldResistantTime;
            }
        } else {
            damageShield(damage);
        }
    }

    @Redirect(method = "damageEntity_CB(Lnet/minecraft/util/DamageSource;F)F", at = @At(target = "Lnet/minecraft/entity/EntityLivingBase;damageArmor(F)V", value = "INVOKE"), require = 0)
    public void damageArmorS(EntityLivingBase entity, float damage) {
        HurtCapability capability = Capabilities.hurt(entity).orElse(null);
        if (capability != null) {
            long worldTime = entity.world.getTotalWorldTime();
            if (capability.armorDamageCooldownUntil > worldTime) {
                return;
            } else {
                damageArmor(damage);
                capability.armorDamageCooldownUntil = worldTime + BHTConfig.CONFIG.damageFrames.armorResistantTime;
            }
        } else {
            damageArmor(damage);
        }
    }
    //Bukkit Compliant

    @Shadow
    protected abstract void damageArmor(float damage);

    @Shadow
    protected abstract void damageShield(float damage);
}
