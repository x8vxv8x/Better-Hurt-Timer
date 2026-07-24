package arekkuusu.betterhurttimer.mixin;

import arekkuusu.betterhurttimer.BHTConfig;
import arekkuusu.betterhurttimer.api.capability.HurtCapability;
import arekkuusu.betterhurttimer.common.Events;
import arekkuusu.betterhurttimer.common.RuntimeData;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityLivingBase.class)
public abstract class HurtTimeMixin extends Entity {

    @Shadow
    public int hurtTime;
    public int preHurtTime;
    public DamageSource preDamageSource;

    public HurtTimeMixin(World p_i1582_1_) {
        super(p_i1582_1_);
    }

    @Inject(method = "attackEntityFrom(Lnet/minecraft/util/DamageSource;F)Z", at = @At("HEAD"))
    public void attackEntityFromBefore(DamageSource source, float amount, CallbackInfoReturnable<Boolean> info) {
        if (this.hurtTime > 0) {
            this.preHurtTime = this.hurtTime;
        } else {
            this.preHurtTime = 0;
        }
        this.preDamageSource = source;
    }

    @Redirect(method = "attackEntityFrom(Lnet/minecraft/util/DamageSource;F)Z", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/EntityLivingBase;hurtResistantTime:I", ordinal = 0))
    public int attackResistantOverride(EntityLivingBase target) {
        if (target.hurtResistantTime > 0 && Events.isDirectAttack(this.preDamageSource)) {
            HurtCapability capability = HurtCapability.get(target).orElse(null);
            Entity attacker = this.preDamageSource.getTrueSource();
            if (capability != null && attacker != null) {
                long serverTick = RuntimeData.serverTick();
                if (capability.canBypassDirectIFrames(serverTick, attacker.getEntityId())) {
                    return 0;
                }
            }
        }
        return target.hurtResistantTime;
    }

    @Inject(method = "attackEntityFrom(Lnet/minecraft/util/DamageSource;F)Z", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/EntityLivingBase;hurtTime:I", shift = At.Shift.AFTER))
    public void hurtResistantTime(DamageSource source, float amount, CallbackInfoReturnable<Boolean> info) {
        int hurtResistantTime = BHTConfig.CONFIG.damageFrames.hurtResistantTime;
        if ((Object) this instanceof EntityPlayer && BHTConfig.CONFIG.damageFrames.hurtResistantTimePlayer >= 0) {
            hurtResistantTime = BHTConfig.CONFIG.damageFrames.hurtResistantTimePlayer;
        }
        if (Events.isDirectAttack(source)) {
            HurtCapability capability = HurtCapability.get(this).orElse(null);
            Entity attacker = source.getTrueSource();
            if (capability != null && attacker != null) {
                long serverTick = RuntimeData.serverTick();
                capability.markDirectHit(serverTick, attacker.getEntityId());
            }
        }
        this.hurtResistantTime = hurtResistantTime;
        if (this.preHurtTime > 0) {
            this.hurtTime = this.preHurtTime;
        }
    }

    @Redirect(method = "attackEntityFrom(Lnet/minecraft/util/DamageSource;F)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;setEntityState(Lnet/minecraft/entity/Entity;B)V", ordinal = 2))
    public void turnOffSound(World world, Entity entity, byte b) {
        if (b == 2 || b == 33 || b == 36 || b == 37) {
            if (this.preHurtTime == 0) {
                world.setEntityState(entity, b);
            }
        }
    }

    @Redirect(method = "attackEntityFrom(Lnet/minecraft/util/DamageSource;F)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/EntityLivingBase;playHurtSound(Lnet/minecraft/util/DamageSource;)V"))
    public void playHurtSound(EntityLivingBase that, DamageSource source) {
        if (this.preHurtTime == 0) {
            this.playHurtSound(source);
        }
    }

    @Inject(method = "playHurtSound(Lnet/minecraft/util/DamageSource;)V", at = @At("HEAD"), cancellable = true)
    public void playHurtSound(DamageSource source, CallbackInfo info) {
        if (this.preHurtTime > 0) {
            info.cancel();
        }
    }

    @Shadow
    protected abstract void playHurtSound(DamageSource source);
}

