package arekkuusu.betterhurttimer.api.capability;

import arekkuusu.betterhurttimer.BHT;
import arekkuusu.betterhurttimer.common.RuntimeData;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.Object2LongArrayMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityInject;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Optional;

@SuppressWarnings("ConstantConditions")
public class HurtCapability implements ICapabilitySerializable<NBTTagCompound>, Capability.IStorage<HurtCapability> {

    @CapabilityInject(HurtCapability.class)
    public static final Capability<HurtCapability> CAPABILITY = null;

    private final Object2LongMap<String> hurtMap = new Object2LongArrayMap<>();
    private long shieldDamageCooldownUntil = Long.MIN_VALUE;
    private long lastDirectAttackTick = Long.MIN_VALUE;
    private long currentAttackAttemptTick = Long.MIN_VALUE;
    private int currentAttackAttemptMarker = Integer.MIN_VALUE;
    private boolean currentAttackAttemptAllowed;
    private long currentDirectHitTick = Long.MIN_VALUE;
    private final IntSet currentDirectAttackers = new IntOpenHashSet();

    public HurtCapability() {
        this.hurtMap.defaultReturnValue(Long.MIN_VALUE);
    }

    public boolean allowSourceDamage(String damageType, long serverTick, int waitTime) {
        long lastHurtTick = this.hurtMap.getLong(damageType);
        if (lastHurtTick == Long.MIN_VALUE || serverTick - lastHurtTick > waitTime) {
            this.hurtMap.put(damageType, serverTick);
            return true;
        }
        return false;
    }

    public boolean canDamageShield(long serverTick) {
        return this.shieldDamageCooldownUntil <= serverTick;
    }

    public void markShieldDamaged(long serverTick, int cooldown) {
        this.shieldDamageCooldownUntil = serverTick + cooldown;
    }

    public boolean allowDirectAttackAttempt(long serverTick, int attackMarker, int cooldown) {
        if (this.currentAttackAttemptTick == serverTick && this.currentAttackAttemptMarker == attackMarker) {
            return this.currentAttackAttemptAllowed;
        }

        int ticksSinceLastAttack = this.lastDirectAttackTick == Long.MIN_VALUE ?
                Integer.MAX_VALUE :
                (int) Math.min(Integer.MAX_VALUE, Math.max(0L, serverTick - this.lastDirectAttackTick));
        boolean allowed = ticksSinceLastAttack >= cooldown;
        this.currentAttackAttemptTick = serverTick;
        this.currentAttackAttemptMarker = attackMarker;
        this.currentAttackAttemptAllowed = allowed;
        if (allowed) {
            this.lastDirectAttackTick = serverTick;
        }
        return allowed;
    }

    public boolean canBypassDirectIFrames(long serverTick, int attackerId) {
        return this.currentDirectHitTick == serverTick && !this.currentDirectAttackers.contains(attackerId);
    }

    public void markDirectHit(long serverTick, int attackerId) {
        if (this.currentDirectHitTick != serverTick) {
            this.currentDirectHitTick = serverTick;
            this.currentDirectAttackers.clear();
        }
        this.currentDirectAttackers.add(attackerId);
    }

    public static Optional<HurtCapability> get(@Nullable Entity entity) {
        return entity != null ? Optional.ofNullable(entity.getCapability(CAPABILITY, null)) : Optional.empty();
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
        return capability == CAPABILITY ? CAPABILITY.cast(this) : null;
    }

    @Override
    public NBTTagCompound serializeNBT() {
        return (NBTTagCompound) CAPABILITY.getStorage().writeNBT(CAPABILITY, this, null);
    }

    @Override
    public void deserializeNBT(NBTTagCompound nbt) {
        CAPABILITY.getStorage().readNBT(CAPABILITY, this, null, nbt);
    }

    @Override
    @Nullable
    public NBTBase writeNBT(Capability<HurtCapability> capability, HurtCapability instance, EnumFacing side) {
        return new NBTTagCompound();
    }

    @Override
    public void readNBT(Capability<HurtCapability> capability, HurtCapability instance, EnumFacing side, NBTBase nbt) {
        instance.shieldDamageCooldownUntil = Long.MIN_VALUE;
    }

    public static class Handler {
        private static final ResourceLocation KEY = new ResourceLocation(BHT.MOD_ID, "HURT");

        @SubscribeEvent
        public void attachCapabilities(AttachCapabilitiesEvent<Entity> event) {
            if (event.getObject() instanceof EntityLivingBase) {
                event.addCapability(KEY, CAPABILITY.getDefaultInstance());
                try {
                    RuntimeData.TICKS_SINCE_LAST_SWING.setInt(((EntityLivingBase) event.getObject()), -1);
                } catch(Exception ignored) {
                }
            }
        }

        @SubscribeEvent
        public void clonePlayer(PlayerEvent.Clone event) {
            event.getEntityPlayer().getCapability(CAPABILITY, null)
                    .deserializeNBT(event.getOriginal().getCapability(CAPABILITY, null).serializeNBT());
        }
    }
}


