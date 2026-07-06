package arekkuusu.betterhurttimer.common;

import arekkuusu.betterhurttimer.BHT;
import arekkuusu.betterhurttimer.BHTConfig;
import arekkuusu.betterhurttimer.api.BHTAPI;
import arekkuusu.betterhurttimer.api.capability.Capabilities;
import arekkuusu.betterhurttimer.api.capability.data.HurtSourceData;
import arekkuusu.betterhurttimer.api.event.PreLivingAttackEvent;
import arekkuusu.betterhurttimer.api.event.PreLivingKnockBackEvent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
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
import net.minecraftforge.common.util.FakePlayer;
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
    public static void onAttackEntityFromPre(PreLivingAttackEvent event) {
        if (isClientWorld(event.getEntityLiving())) return;

        DamageSource source = event.getSource();
        EntityLivingBase entity = event.getEntityLiving();
        Optional<HurtSourceData> optional = BHTAPI.get(entity, source);
        long worldTime = entity.world.getTotalWorldTime();

        if (Events.isAttack(source) && !(source instanceof EntityDamageSourceIndirect)) return;
        if (!optional.isPresent()) return;

        HurtSourceData data = optional.orElseThrow(UnsupportedOperationException::new);
        if (!data.hasTriggered()) {
            data.trigger(worldTime);
            return;
        }

        if (!data.canApply(worldTime)) {
            float lastAmount = event.getAmount();
            if (Double.compare(data.lastHurtAmount + BHTConfig.CONFIG.damageFrames.nextAttackDamageDifference, event.getAmount()) > 0) {
                if (BHTConfig.CONFIG.damageFrames.nextAttackDamageDifferenceApply) {
                    event.setAmount(lastAmount - Math.max(0, (float) data.lastHurtAmount));
                }
                data.lastHurtAmount = lastAmount;
            } else {
                event.setCanceled(true);
            }
        } else {
            data.trigger(worldTime);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onEntityAttack(LivingAttackEvent event) {
        if (isClientWorld(event.getEntity())) return;
        if (event.getAmount() <= 0) return;

        DamageSource source = event.getSource();

        if (!Events.isAttack(source) && !BHTAPI.isCustom(source.getImmediateSource())) return;
        if (source instanceof EntityDamageSourceIndirect && !BHTAPI.isCustom(source.getImmediateSource())) return;
        if (!(source.getImmediateSource() instanceof EntityLivingBase) && !BHTAPI.isCustom(source.getImmediateSource())) return;

        Entity target = event.getEntity();
        Entity attacker = source.getImmediateSource();
        int attackCooldown = Events.getAttackCooldown(attacker);
        if (attackCooldown <= 0) return;

        Capabilities.hurt(attacker).ifPresent(capability -> {
            long worldTime = event.getEntity().world.getTotalWorldTime();
            int attackAttemptMarker = Events.getAttackAttemptMarker(attacker);
            boolean sameAttempt = capability.currentAttackAttemptTick == worldTime &&
                    capability.currentAttackAttemptMarker == attackAttemptMarker;

            if (sameAttempt) {
                if (!capability.currentAttackAttemptAllowed) {
                    event.setCanceled(true);
                }
                return;
            }

            int ticksSinceLastAttack = capability.lastDirectAttackTick == Long.MIN_VALUE ?
                    Integer.MAX_VALUE :
                    (int) Math.min(Integer.MAX_VALUE, Math.max(0L, worldTime - capability.lastDirectAttackTick));
            boolean allowed = ticksSinceLastAttack >= attackCooldown;
            capability.currentAttackAttemptTick = worldTime;
            capability.currentAttackAttemptMarker = attackAttemptMarker;
            capability.currentAttackAttemptAllowed = allowed;
            if (allowed) {
                capability.lastDirectAttackTick = worldTime;
            } else {
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
                return BHTAPI.field.getInt(attacker);
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
            canSwing = BHTAPI.field.getInt(entity) >= 0 && item.getAttributeModifiers(
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
            if (BHTAPI.ATTACK_ITEM_THRESHOLD_MAP.containsKey(itemLocation)) {
                return BHTAPI.ATTACK_ITEM_THRESHOLD_MAP.get(itemLocation);
            }
        }
        ResourceLocation location = EntityList.getKey(entity.getClass());
        double threshold = BHTConfig.CONFIG.attackFrames.attackThresholdDefault;
        if (entity instanceof EntityPlayer)
            threshold = BHTConfig.CONFIG.attackFrames.attackThresholdPlayer;
        if (location != null && BHTAPI.ATTACK_THRESHOLD_MAP.containsKey(location))
            threshold = BHTAPI.ATTACK_THRESHOLD_MAP.get(location);
        return threshold;
    }

    public static boolean isAttack(DamageSource source) {
        return ATTACK_SOURCES.contains(source.getDamageType());
    }

    public static boolean isDirectAttack(DamageSource source) {
        if (source == null || source instanceof EntityDamageSourceIndirect) {
            return false;
        }
        return Events.isAttack(source) || BHTAPI.isCustom(source.getImmediateSource());
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
