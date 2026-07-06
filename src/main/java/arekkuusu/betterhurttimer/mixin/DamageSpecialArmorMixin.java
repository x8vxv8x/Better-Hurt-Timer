package arekkuusu.betterhurttimer.mixin;

import arekkuusu.betterhurttimer.BHTConfig;
import arekkuusu.betterhurttimer.api.capability.Capabilities;
import arekkuusu.betterhurttimer.api.capability.HurtCapability;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraftforge.common.ISpecialArmor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ISpecialArmor.ArmorProperties.class)
public abstract class DamageSpecialArmorMixin {

    @Redirect(method = "applyArmor(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/util/NonNullList;Lnet/minecraft/util/DamageSource;D)F", at = @At(value = "INVOKE", target = "Lnet/minecraftforge/common/ISpecialArmor;damageArmor(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/item/ItemStack;Lnet/minecraft/util/DamageSource;II)V"), remap = false)
    private static void damageSpecialArmor(ISpecialArmor armor, EntityLivingBase entity, ItemStack stack, DamageSource source, int damage, int slot) {
        HurtCapability capability = Capabilities.hurt(entity).orElse(null);
        if (capability != null) {
            long worldTime = entity.world.getTotalWorldTime();
            if (!capability.canDamageArmor(worldTime)) {
                return;
            }
            armor.damageArmor(entity, stack, source, damage, slot);
            capability.markArmorDamaged(worldTime, BHTConfig.CONFIG.damageFrames.armorResistantTime);
            return;
        }
        armor.damageArmor(entity, stack, source, damage, slot);
    }
}
