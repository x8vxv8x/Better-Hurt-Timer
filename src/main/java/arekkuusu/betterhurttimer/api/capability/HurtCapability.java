package arekkuusu.betterhurttimer.api.capability;

import arekkuusu.betterhurttimer.BHT;
import arekkuusu.betterhurttimer.common.RuntimeData;
import arekkuusu.betterhurttimer.api.capability.data.HurtSourceData;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;

@SuppressWarnings("ConstantConditions")
public class HurtCapability implements ICapabilitySerializable<NBTTagCompound>, Capability.IStorage<HurtCapability> {

    private final Object2ObjectMap<String, HurtSourceData> hurtMap = new Object2ObjectArrayMap<>();
    private long armorDamageCooldownUntil = Long.MIN_VALUE;
    private long shieldDamageCooldownUntil = Long.MIN_VALUE;
    private long lastDirectAttackTick = Long.MIN_VALUE;
    private long currentAttackAttemptTick = Long.MIN_VALUE;
    private int currentAttackAttemptMarker = Integer.MIN_VALUE;
    private boolean currentAttackAttemptAllowed;
    private long currentDirectHitTick = Long.MIN_VALUE;
    private final Set<Integer> currentDirectAttackers = new HashSet<>();

    public HurtSourceData sourceData(String damageType, int waitTime) {
        return this.hurtMap.computeIfAbsent(damageType, ignored -> new HurtSourceData(waitTime));
    }

    public boolean canDamageArmor(long worldTime) {
        return this.armorDamageCooldownUntil <= worldTime;
    }

    public void markArmorDamaged(long worldTime, int cooldown) {
        this.armorDamageCooldownUntil = worldTime + cooldown;
    }

    public boolean canDamageShield(long worldTime) {
        return this.shieldDamageCooldownUntil <= worldTime;
    }

    public void markShieldDamaged(long worldTime, int cooldown) {
        this.shieldDamageCooldownUntil = worldTime + cooldown;
    }

    public boolean allowDirectAttackAttempt(long worldTime, int attackMarker, int cooldown) {
        if (this.currentAttackAttemptTick == worldTime && this.currentAttackAttemptMarker == attackMarker) {
            return this.currentAttackAttemptAllowed;
        }

        int ticksSinceLastAttack = this.lastDirectAttackTick == Long.MIN_VALUE ?
                Integer.MAX_VALUE :
                (int) Math.min(Integer.MAX_VALUE, Math.max(0L, worldTime - this.lastDirectAttackTick));
        boolean allowed = ticksSinceLastAttack >= cooldown;
        this.currentAttackAttemptTick = worldTime;
        this.currentAttackAttemptMarker = attackMarker;
        this.currentAttackAttemptAllowed = allowed;
        if (allowed) {
            this.lastDirectAttackTick = worldTime;
        }
        return allowed;
    }

    public boolean canBypassDirectIFrames(long worldTime, int attackerId) {
        return this.currentDirectHitTick == worldTime && !this.currentDirectAttackers.contains(attackerId);
    }

    public void markDirectHit(long worldTime, int attackerId) {
        if (this.currentDirectHitTick != worldTime) {
            this.currentDirectHitTick = worldTime;
            this.currentDirectAttackers.clear();
        }
        this.currentDirectAttackers.add(attackerId);
    }

    public static void init() {
        CapabilityManager.INSTANCE.register(HurtCapability.class, new HurtCapability(), HurtCapability::new);
        MinecraftForge.EVENT_BUS.register(new Handler());
    }

    @Override
    public boolean hasCapability(@Nonnull Capability<?> capability, @Nullable EnumFacing facing) {
        return getCapability(capability, facing) != null;
    }

    @Override
    @Nullable
    public <T> T getCapability(@Nonnull Capability<T> capability, @Nullable EnumFacing facing) {
        return capability == Capabilities.HURT_LIMITER ? Capabilities.HURT_LIMITER.cast(this) : null;
    }

    @Override
    public NBTTagCompound serializeNBT() {
        return (NBTTagCompound) Capabilities.HURT_LIMITER.getStorage().writeNBT(Capabilities.HURT_LIMITER, this, null);
    }

    @Override
    public void deserializeNBT(NBTTagCompound nbt) {
        Capabilities.HURT_LIMITER.getStorage().readNBT(Capabilities.HURT_LIMITER, this, null, nbt);
    }

    //** NBT **//
    public static final String LAST_ARMOR_TIMER_NBT = "armorDamageCooldownUntil";
    public static final String LAST_SHIELD_TIMER_NBT = "shieldDamageCooldownUntil";

    @Override
    @Nullable
    public NBTBase writeNBT(Capability<HurtCapability> capability, HurtCapability instance, EnumFacing side) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setLong(LAST_ARMOR_TIMER_NBT, instance.armorDamageCooldownUntil);
        tag.setLong(LAST_SHIELD_TIMER_NBT, instance.shieldDamageCooldownUntil);
        return tag;
    }

    @Override
    public void readNBT(Capability<HurtCapability> capability, HurtCapability instance, EnumFacing side, NBTBase nbt) {
        NBTTagCompound tag = (NBTTagCompound) nbt;
        instance.armorDamageCooldownUntil = tag.hasKey(LAST_ARMOR_TIMER_NBT) ? tag.getLong(LAST_ARMOR_TIMER_NBT) : tag.getInteger(LAST_ARMOR_TIMER_NBT);
        instance.shieldDamageCooldownUntil = tag.hasKey(LAST_SHIELD_TIMER_NBT) ? tag.getLong(LAST_SHIELD_TIMER_NBT) : tag.getInteger(LAST_SHIELD_TIMER_NBT);
    }
    //** NBT **//

    public static class Handler {
        private static final ResourceLocation KEY = new ResourceLocation(BHT.MOD_ID, "HURT");

        @SubscribeEvent
        public void attachCapabilities(AttachCapabilitiesEvent<Entity> event) {
            if (event.getObject() instanceof EntityLivingBase) {
                event.addCapability(KEY, Capabilities.HURT_LIMITER.getDefaultInstance());
                try {
                    RuntimeData.TICKS_SINCE_LAST_SWING.setInt(((EntityLivingBase) event.getObject()), -1);
                } catch(Exception ignored) {
                }
            }
        }

        @SubscribeEvent
        public void clonePlayer(PlayerEvent.Clone event) {
            event.getEntityPlayer().getCapability(Capabilities.HURT_LIMITER, null)
                    .deserializeNBT(event.getOriginal().getCapability(Capabilities.HURT_LIMITER, null).serializeNBT());
        }
    }
}
