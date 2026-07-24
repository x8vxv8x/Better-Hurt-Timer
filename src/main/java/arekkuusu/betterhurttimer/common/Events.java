package arekkuusu.betterhurttimer.common;

import arekkuusu.betterhurttimer.BHT;
import arekkuusu.betterhurttimer.BHTConfig;
import arekkuusu.betterhurttimer.api.capability.Capabilities;
import arekkuusu.betterhurttimer.api.capability.data.HurtSourceData;
import arekkuusu.betterhurttimer.api.event.PreLivingKnockBackEvent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EntityDamageSourceIndirect;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

@Mod.EventBusSubscriber(modid = BHT.MOD_ID)
public class Events {

    public static int maxHurtResistantTime = 20;
    private static Set<String> ATTACK_SOURCES = Collections.emptySet();
    private static Set<String> KNOCKBACK_EXEMPT_SOURCES = Collections.emptySet();

    public static void refreshConfigCaches() {
        ATTACK_SOURCES = new HashSet<>();
        KNOCKBACK_EXEMPT_SOURCES = new HashSet<>();
        Collections.addAll(ATTACK_SOURCES, BHTConfig.CONFIG.attackFrames.attackSources);
        Collections.addAll(KNOCKBACK_EXEMPT_SOURCES, BHTConfig.CONFIG.knockbackFrames.knockbackExemptSource);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onNonDirectAttack(LivingAttackEvent event) {
        if (isClientWorld(event.getEntityLiving())) return;
        if (event.getAmount() <= 0) return;

        DamageSource source = event.getSource();
        EntityLivingBase entity = event.getEntityLiving();
        if (Events.isDirectAttack(source)) return;

        Optional<HurtSourceData> optional = BHTConfig.CONFIG.damageFrames.useVanillaNonDirectDamageFrames ?
                RuntimeData.getFixedSource(entity, source, BHTConfig.CONFIG.damageFrames.nonDirectDamageResistantTime) :
                RuntimeData.getConfiguredSource(entity, source);
        long serverTick = RuntimeData.serverTick();

        if (!optional.isPresent()) return;

        HurtSourceData data = optional.orElseThrow(UnsupportedOperationException::new);
        if (data.canApply(serverTick)) {
            data.trigger(serverTick);
            return;
        }
        event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onEntityAttack(LivingAttackEvent event) {
        if (isClientWorld(event.getEntity())) return;
        if (event.getAmount() <= 0) return;

        DamageSource source = event.getSource();

        if (!Events.isAttack(source) && !RuntimeData.hasCustomAttackThreshold(source.getImmediateSource())) return;
        if (source instanceof EntityDamageSourceIndirect && !RuntimeData.hasCustomAttackThreshold(source.getImmediateSource())) return;
        if (!(source.getImmediateSource() instanceof EntityLivingBase) && !RuntimeData.hasCustomAttackThreshold(source.getImmediateSource())) return;

        Entity target = event.getEntity();
        Entity attacker = source.getImmediateSource();
        int attackCooldown = Events.getAttackCooldown(attacker);
        if (attackCooldown <= 0) return;

        Capabilities.hurt(attacker).ifPresent(capability -> {
            long serverTick = RuntimeData.serverTick();
            int attackAttemptMarker = Events.getAttackAttemptMarker(attacker);
            if (!capability.allowDirectAttackAttempt(serverTick, attackAttemptMarker, attackCooldown)) {
                event.setCanceled(true);
            }
        });
    }

    public static int getAttackCooldown(Entity attacker) {
        double threshold = Events.getThreshold(attacker);
        if (threshold <= 0) {
            return 0;
        }

        if (attacker instanceof EntityLivingBase && Events.canSwing((EntityLivingBase) attacker)) {
            return (int) (Events.getCoolPeriod((EntityLivingBase) attacker) * threshold);
        } else {
            double attackerAttackSpeed = Events.getAttackSpeed(attacker);
            return (int) (Events.maxHurtResistantTime * (attackerAttackSpeed * threshold));
        }
    }

    public static int getAttackAttemptMarker(Entity attacker) {
        if (attacker instanceof EntityLivingBase && Events.canSwing((EntityLivingBase) attacker)) {
            try {
                return RuntimeData.TICKS_SINCE_LAST_SWING.getInt(attacker);
            } catch (Exception ignored) {
            }
        }
        return attacker.ticksExisted;
    }

    public static boolean canSwing(EntityLivingBase entity) {
        ItemStack stack = entity.getHeldItem(EnumHand.MAIN_HAND);
        Item item = stack.getItem();
        boolean canSwing = false;
        try {
            canSwing = RuntimeData.TICKS_SINCE_LAST_SWING.getInt(entity) >= 0 && item.getAttributeModifiers(
                    EntityEquipmentSlot.MAINHAND,
                    stack
            ).containsKey(SharedMonsterAttributes.ATTACK_SPEED.getName());
        } catch(Exception ignored) {
        }
        return canSwing;
    }

    public static double getCoolPeriod(EntityLivingBase entity) {
        return (1D / entity.getEntityAttribute(SharedMonsterAttributes.ATTACK_SPEED).getAttributeValue() * Events.maxHurtResistantTime);
    }

    public static double getHurtResistantTime(Entity entity) {
        if (entity instanceof EntityPlayer && BHTConfig.CONFIG.damageFrames.hurtResistantTimePlayer >= 0) {
            return BHTConfig.CONFIG.damageFrames.hurtResistantTimePlayer;
        }
        return entity instanceof EntityLivingBase ?
                ((EntityLivingBase) entity).maxHurtResistantTime
                : Events.maxHurtResistantTime;
    }

    public static double getAttackSpeed(Entity entity) {
        double attackSpeed = SharedMonsterAttributes.ATTACK_SPEED.getDefaultValue();
        IAttributeInstance attribute = null;
        if (entity instanceof EntityLivingBase) {
            attribute = ((EntityLivingBase) entity).getEntityAttribute(SharedMonsterAttributes.ATTACK_SPEED);
        }
        if (attribute != null) {
            attackSpeed = attribute.getAttributeValue();
        }
        return 1.2D - (1.2D / (1.2D / (attackSpeed * 1.2) * 20D));
    }

    public static double getThreshold(Entity entity) {
        if (entity instanceof EntityLivingBase) {
            ResourceLocation itemLocation = ((EntityLivingBase) entity).getHeldItemMainhand().getItem().getRegistryName();
            if (RuntimeData.ATTACK_ITEM_THRESHOLDS.containsKey(itemLocation)) {
                return RuntimeData.ATTACK_ITEM_THRESHOLDS.get(itemLocation);
            }
        }
        ResourceLocation location = RuntimeData.getEntityLocation(entity);
        double threshold = BHTConfig.CONFIG.attackFrames.attackThresholdDefault;
        if (entity instanceof EntityPlayer)
            threshold = BHTConfig.CONFIG.attackFrames.attackThresholdPlayer;
        if (location != null && RuntimeData.ATTACK_THRESHOLDS.containsKey(location))
            threshold = RuntimeData.ATTACK_THRESHOLDS.get(location);
        return threshold;
    }

    public static boolean isAttack(DamageSource source) {
        return ATTACK_SOURCES.contains(source.getDamageType());
    }

    public static boolean isDirectAttack(DamageSource source) {
        if (source == null || source instanceof EntityDamageSourceIndirect) {
            return false;
        }
        return Events.isAttack(source) || RuntimeData.hasCustomAttackThreshold(source.getImmediateSource());
    }

    @SubscribeEvent()
    public static void onKnockback(PreLivingKnockBackEvent event) {
        if (isClientWorld(event.getEntityLiving())) return;
        if (KNOCKBACK_EXEMPT_SOURCES.contains(event.getSource().getDamageType())) {
            event.setCanceled(true);
        }
    }

    public static boolean isClientWorld(Entity entity) {
        return entity.getEntityWorld().isRemote;
    }
}

