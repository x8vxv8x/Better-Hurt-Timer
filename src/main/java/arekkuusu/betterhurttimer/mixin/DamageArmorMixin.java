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
public abstract class DamageArmorMixin {

    //Forge Compliant
    @Redirect(method = "attackEntityFrom(Lnet/minecraft/util/DamageSource;F)Z", at = @At(target = "Lnet/minecraft/entity/EntityLivingBase;damageShield(F)V", value = "INVOKE"), require = 0)
    public void damageShield(EntityLivingBase entity, float damage) {
        HurtCapability capability = Capabilities.hurt(entity).orElse(null);
        if (capability != null) {
            long worldTime = entity.world.getTotalWorldTime();
            if (capability.shieldDamageCooldownUntil > worldTime) {
                if (Double.compare(Math.max(0, capability.lastShieldDamage + BHTConfig.CONFIG.damageFrames.nextAttackDamageDifference), damage) < 0) {
                    damageShield((float) (damage - capability.lastShieldDamage));
                    capability.lastShieldDamage = damage;
                }
            } else {
                damageShield(damage);
                capability.lastShieldDamage = damage;
                capability.shieldDamageCooldownUntil = worldTime + BHTConfig.CONFIG.damageFrames.shieldResistantTime;
            }
        } else {
            damageShield(damage);
        }
    }

    @Redirect(method = "applyArmorCalculations(Lnet/minecraft/util/DamageSource;F)F", at = @At(target = "Lnet/minecraft/entity/EntityLivingBase;damageArmor(F)V", value = "INVOKE"), require = 0)
    public void damageArmor(EntityLivingBase entity, float damage) {
        HurtCapability capability = Capabilities.hurt(entity).orElse(null);
        if (capability != null) {
            long worldTime = entity.world.getTotalWorldTime();
            if (capability.armorDamageCooldownUntil > worldTime) {
                if (Double.compare(Math.max(0, capability.lastArmorDamage + BHTConfig.CONFIG.damageFrames.nextAttackDamageDifference), damage) < 0) {
                    damageArmor((float) (damage - capability.lastArmorDamage));
                    capability.lastArmorDamage = damage;
                }
            } else {
                damageArmor(damage);
                capability.lastArmorDamage = damage;
                capability.armorDamageCooldownUntil = worldTime + BHTConfig.CONFIG.damageFrames.armorResistantTime;
            }
        } else {
            damageArmor(damage);
        }
    }
    //Forge Compliant

    @Shadow
    protected abstract void damageArmor(float damage);

    @Shadow
    protected abstract void damageShield(float damage);
}
